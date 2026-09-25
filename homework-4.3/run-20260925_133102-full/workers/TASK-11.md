# Worker Report: TASK-11

## Task
SCOPE: `src/main/java/io/spring/application/article/NewArticleParam.java`
QUESTION: Does this DTO's Bean Validation annotations enforce required fields and length bounds consistent with the underlying database column widths, per CLAUDE.md's Input Validation section?

## Findings

**Required-field validation is present but incomplete for one field:**
- `title` (`NewArticleParam.java:19`): `@NotBlank(message = "can't be empty")` — present.
- `description` (`NewArticleParam.java:22`): `@NotBlank(message = "can't be empty")` — present.
- `body` (`NewArticleParam.java:25`): `@NotBlank(message = "can't be empty")` — present.
- `tagList` (`NewArticleParam.java:27`): `private List<String> tagList;` — **no validation annotation at all**, not even `@NotNull`, and no constraint on individual list elements (e.g. blank/oversized tag strings).

**Length-bound validation is entirely missing:**
- None of the four fields (`title`, `description`, `body`, `tagList`) carry a `@Size` (or `@Length`) annotation anywhere in the file.
- Underlying schema (`src/main/resources/db/migration/V1__create_tables.sql`):
  - `articles.title` → `varchar(255)` (line: `title varchar(255),`)
  - `articles.description` → `text` (unbounded)
  - `articles.body` → `text` (unbounded)
  - There is no dedicated tag-length column definition visible in this migration for `tags.name` beyond `varchar(255)`, which is also unbounded from the DTO's perspective since `tagList` has no size constraint.

The one field with a finite, meaningful column width — `title` at `varchar(255)` — has **no corresponding `@Size(max = 255)`** on `NewArticleParam.java:19`. This is the exact gap CLAUDE.md's Input Validation section calls out: *"Validate format and required fields via Bean Validation (`@NotBlank`, `@Email`, etc.); add `@Size` bounds on any new user-supplied string field."*

Because SQLite uses type affinity rather than strictly enforcing `VARCHAR(n)` length at the storage layer, an oversized title will not fail at the database level either — it will simply be silently stored beyond the declared 255-character intent, and later truncated/mismatched behavior could occur if the app is ever migrated to a database that does enforce `VARCHAR(255)` (e.g. MySQL/Postgres), or if any downstream consumer assumes the 255 bound holds.

## Recommendations

1. Add `@Size(max = 255, message = "is too long")` to `title` (`NewArticleParam.java:19`) to match the `articles.title varchar(255)` column and satisfy CLAUDE.md's Input Validation requirement.
2. Consider adding a reasonable `@Size` upper bound to `description` and `body` even though the columns are `text` (unbounded) — CLAUDE.md's directive to bound "any new user-supplied string field" is a defense-in-depth measure against unbounded payloads (e.g. abuse/DoS via huge request bodies), independent of what the column type technically permits.
3. Add `@Size` validation to `tagList` (e.g. max number of tags, and per-element length/blank checks via a custom validator or `@Size` on a wrapped element type) — currently it has zero validation, meaning an attacker-supplied list of arbitrarily many or arbitrarily long tag strings is accepted unchecked.
