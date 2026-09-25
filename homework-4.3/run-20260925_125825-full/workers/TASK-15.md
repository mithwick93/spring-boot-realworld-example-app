# Worker Report: TASK-15

## Task
SCOPE: `src/main/java/io/spring/application/user/RegisterParam.java`
QUESTION: Does this DTO's Bean Validation annotations enforce required fields, email format, and length bounds on username/email/password consistent with the underlying database column widths?

## Findings

- `RegisterParam.java:15-18` (email) — `@NotBlank` (required) + `@Email` (format) are present. **No `@Size` bound.**
- `RegisterParam.java:20-22` (username) — `@NotBlank` (required) only. No format constraint (arguably fine — usernames aren't emails), and **no `@Size` bound.**
- `RegisterParam.java:24-25` (password) — `@NotBlank` (required) only. **No `@Size` bound.**

Underlying schema (`src/main/resources/db/migration/V1__create_tables.sql`): `users.username`, `users.password`, `users.email` are all `varchar(255)` (lines 2-4 of that file).

So: required-field enforcement — yes, for all three fields (`@NotBlank`). Email format enforcement — yes (`@Email`). Length-bound enforcement against the DB column widths — **no**, for all three fields. Nothing in this DTO rejects a value longer than 255 chars before it reaches the `varchar(255)` columns.

This is a direct gap against CLAUDE.md's Security Requirements > Input Validation:
> "Validate format and required fields via Bean Validation (`@NotBlank`, `@Email`, etc.); add `@Size` bounds on any new user-supplied string field"

Concrete consequences:
- SQLite does not enforce `VARCHAR(n)` length at the storage layer (it's advisory only in SQLite's type system), so an oversized `username`/`email`/`password` will be silently persisted rather than rejected or truncated — the app-level intent of a 255-char column is not actually enforced anywhere.
- `password` has no upper bound at all before it's handed to `BCryptPasswordEncoder`. BCrypt silently ignores/truncates input beyond 72 bytes, so a client could submit an arbitrarily long password (memory/CPU cost per request) with no rejection, and two different long passwords sharing the same first 72 bytes would hash identically.
- No `@Size` also means no defense against oversized-payload style abuse on an unauthenticated endpoint (`POST /users` register is public — see `WebSecurityConfig`), which is exactly the kind of new unauthenticated input path CLAUDE.md flags for validation scrutiny.

## Recommendations

Add explicit `@Size` bounds matching (or intentionally tighter than) the `varchar(255)` columns, consistent with the pattern CLAUDE.md prescribes:

- `email`: `@Size(max = 255, message = "is too long")` alongside the existing `@NotBlank`/`@Email`.
- `username`: `@Size(min = 1, max = 255, message = "is too long")` (min redundant with `@NotBlank` but harmless; keeps the pair self-documenting).
- `password`: `@Size(max = 72, message = "is too long")` — cap at BCrypt's effective input limit (72 bytes) rather than 255, since anything beyond 72 is silently ignored by the encoder today; alternatively cap at 255 for DB consistency and additionally reject/warn above 72 if stricter password-hash correctness is desired. At minimum, some upper bound should be added — currently there is none.

These are the same three fields exercised by `UsersApiTest`-style tests per CLAUDE.md's Test Plan; boundary cases (exact 255, 256, empty string) should be added there once `@Size` is introduced.
