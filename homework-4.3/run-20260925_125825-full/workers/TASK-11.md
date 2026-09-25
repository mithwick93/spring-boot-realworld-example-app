# Worker Report: TASK-11

## Task
SCOPE: `src/main/java/io/spring/application/article/NewArticleParam.java`
QUESTION: Does this DTO's Bean Validation annotations enforce required fields and length bounds consistent with the underlying database column widths, per CLAUDE.md's Input Validation section?

## Findings

CLAUDE.md > Security Requirements > Input Validation states: "Validate format and required fields via Bean Validation (`@NotBlank`, `@Email`, etc.); add `@Size` bounds on any new user-supplied string field."

Required-field enforcement is present but length-bound enforcement is missing entirely:

- `NewArticleParam.java:17-19` — `title`: `@NotBlank(message = "can't be empty")` is present (required-field check satisfied), but there is **no `@Size` upper bound**. The backing column is `articles.title varchar(255)` (`V1__create_tables.sql:14`). Since the project runs on SQLite, and SQLite does not enforce `VARCHAR(n)` length at the storage layer (length modifiers are advisory only), an over-255-character title is not rejected by the DB either — the only place this could be caught is app-level `@Size(max = 255)`, which is absent.
- `NewArticleParam.java:21-22` — `description`: `@NotBlank` present, no `@Size` bound. Backing column is `articles.description text` (`V1__create_tables.sql:15`), which has no fixed width, so there's no specific DB width to be "consistent with" — but per the CLAUDE.md rule this is still a user-supplied string field lacking any upper bound, leaving it open to unbounded-size input.
- `NewArticleParam.java:24-25` — `body`: same pattern as `description` — `@NotBlank` present, no `@Size` bound; backing column `articles.body text` (`V1__create_tables.sql:16`), unbounded.
- `NewArticleParam.java:27` — `tagList`: **no validation annotations at all** — not `@NotNull`, no `@Size` on the list, and no per-element constraint (e.g. `@NotBlank`/`@Size` via a cascaded validator) on the tag strings themselves. Each tag is ultimately persisted into `tags.name varchar(255) not null` (`V1__create_tables.sql:34`), which is `NOT NULL` at the DB level — a blank or null-containing list element is not rejected at the DTO layer before reaching persistence.

Summary: required-field validation (`@NotBlank`) is consistently applied to the three string fields, satisfying that half of the Input Validation section. Length-bound validation (`@Size`) is absent for every field, including `title`, whose backing column has an explicit, finite width (`varchar(255)`) that the DTO does nothing to mirror.

## Recommendations

1. Add `@Size(max = 255, message = "can't be longer than 255 characters")` to `title` (`NewArticleParam.java:19`) to match `articles.title varchar(255)` (`V1__create_tables.sql:14`). This is the clearest gap: a finite DB column width exists with no corresponding app-level bound, and SQLite will not enforce it for you.
2. Add reasonable `@Size(max = ...)` bounds to `description` and `body` even though their columns are `text` (unbounded) — the CLAUDE.md rule ("add `@Size` bounds on any user-supplied string field") is about limiting arbitrary/malicious input size, not just mirroring a fixed column width.
3. Add validation to `tagList`: at minimum a `@Size(max = ...)` on the list itself, plus a way to validate each element (e.g. switch to `List<@NotBlank @Size(max = 255) String>`, which requires enabling method/field cascading — Bean Validation supports container element constraints since 2.0) so blank or overlong tag names are rejected before hitting `tags.name varchar(255) not null`.
