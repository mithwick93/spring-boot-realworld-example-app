# Worker Report: TASK-17

## Task
SCOPE: `src/main/java/io/spring/application/user/DuplicatedEmailValidator.java`
QUESTION: Does this validator have the same TOCTOU exposure (check-then-insert, no DB unique constraint backing it) as the article-title duplicate check, and is that a known, accepted risk per CLAUDE.md?

## Findings

- `DuplicatedEmailValidator.isValid` (`DuplicatedEmailValidator.java:14-16`) does a plain check-then-act: `!userRepository.findByEmail(value).isPresent()`. Between this check and the later `INSERT` during registration, two concurrent signups with the same email can both pass validation — this is a genuine TOCTOU window, structurally the same shape as the article-title duplicate check flagged in CLAUDE.md's Conventions section (`DuplicatedArticleConstraint`/`DuplicatedArticleUpdateValidator`).

- However, the second half of the question's premise — "no DB unique constraint backing it" — does **not** hold for email. `src/main/resources/db/migration/V1__create_tables.sql:5` declares `email varchar(255) UNIQUE` on the `users` table (and `username` at line 3 is likewise `UNIQUE`). By contrast, the article table's `title` column (`V1__create_tables.sql:14`) has **no** unique constraint — only `slug` does (line 13), which is a derived/normalized value, not the raw user-supplied title. So the article-title case genuinely lacks a DB-level backstop, while the email case does not.

- This is not an unaddressed race — it's a documented, deliberately handled one. `CustomizeExceptionHandler.handleDataIntegrityViolation` (`src/main/java/io/spring/api/exception/CustomizeExceptionHandler.java:83-94`) explicitly catches `DataIntegrityViolationException` and returns a clean `422 UNPROCESSABLE_ENTITY` response instead of leaking an unhandled DB exception. The inline comment at lines 86-91 names exactly this scenario: "Safety net for the race window between the application-layer duplicate-username/email check ... and the actual insert ... the DB-level UNIQUE constraint (users.username/users.email, V1__create_tables.sql) is the only thing that catches the second one."

- Per CLAUDE.md's Security Requirements > Output Encoding and Error Handling ("Never expose stack traces, internal class names, or file paths in API error responses"), this handler satisfies that requirement for the email/username race: the client gets a generic structured error, not a raw exception. Per Security Requirements > Database Access ("Apply least privilege... use MyBatis parameter binding"), the relevant control here is the schema-level UNIQUE constraint rather than parameter binding, and it is in place.

## Recommendations

No gap found for `DuplicatedEmailValidator`. The TOCTOU window at the application-validator level exists (as it structurally must for any check-then-insert pattern), but unlike the article-title case, it is:
1. backed by a DB-level `UNIQUE` constraint (`V1__create_tables.sql:5`), and
2. explicitly handled with a clean error response, with a comment documenting the exact race it exists to catch (`CustomizeExceptionHandler.java:83-94`).

This is a strictly better-covered case than the article-title duplicate check and should not be treated as an equivalent finding. No code change is recommended. If a stricter standard is desired, the only remaining theoretical gap is that the 422 response for the DB-level catch may not be worded identically to the application-layer validator's `{"errors": {...}}` shape (worth a quick manual check of the response body against the "errors are NOT uniformly shaped" pitfall in CLAUDE.md), but that is a consistency nit, not a security exposure.
