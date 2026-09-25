# Worker Report: TASK-15

## Task
SCOPE: `src/main/java/io/spring/application/user/RegisterParam.java`
QUESTION: Does this DTO's Bean Validation annotations enforce required fields, email format, and length bounds on username/email/password consistent with the underlying database column widths?

## Findings

Relevant DTO fields (`RegisterParam.java`):
- `email`: `@NotBlank` (line 15) + `@Email` (line 16) + `@DuplicatedEmailConstraint` (line 17)
- `username`: `@NotBlank` (line 20) + `@DuplicatedUsernameConstraint` (line 21)
- `password`: `@NotBlank` (line 24)

Underlying schema (`src/main/resources/db/migration/V1__create_tables.sql:1-8`):
```
create table users (
  id varchar(255) primary key,
  username varchar(255) UNIQUE,
  password varchar(255),
  email varchar(255) UNIQUE,
  bio text,
  image varchar(511)
);
```
`username`, `password`, and `email` are all `varchar(255)`.

**Required fields:** Enforced correctly. All three fields have `@NotBlank` (`RegisterParam.java:15,20,24`), satisfying the "required" part of the question.

**Email format:** Enforced correctly via `@Email` (`RegisterParam.java:16`).

**Length bounds — gap found.** None of the three fields carries a `@Size` annotation:
- `email` (line 15-18): no `@Size(max = 255)` to match the `varchar(255)` column.
- `username` (line 20-22): no `@Size(max = 255)`.
- `password` (line 24-25): no `@Size` at all — no upper bound, and no minimum length either (a 1-character password passes `@NotBlank`).

This is a direct gap against CLAUDE.md's Security Requirements > Input Validation section, which states: *"Validate format and required fields via Bean Validation (`@NotBlank`, `@Email`, etc.); add `@Size` bounds on any new user-supplied string field."* `email` and `username` are user-supplied strings backed by fixed-width `varchar(255)` UNIQUE columns, so oversized input isn't caught by validation and is instead deferred to the database layer (behavior there depends on the underlying JDBC/SQLite driver's leniency, and SQLite in particular does not strictly enforce `VARCHAR` length limits, so oversized values may simply persist silently rather than error).

`password` is BCrypt-hashed before storage (per CLAUDE.md's Security Requirements > Authentication and Secrets), so the raw password length is not stored directly in the `varchar(255)` column — but the complete absence of any `@Size` bound means arbitrarily long input is passed into the password encoder, which is a potential resource-consumption concern (BCrypt cost scales with input length), and there is no minimum-length policy for password strength.

## Recommendations

1. Add `@Size(max = 255, message = "...")` to `email` and `username` in `RegisterParam.java` to match the `varchar(255)` column widths in `V1__create_tables.sql` and fail fast with a clean `{"errors": {...}}` response instead of relying on undefined DB-layer behavior.
2. Add a bounded `@Size(min = 8, max = 72, message = "...")` (or similar policy) to `password` — an upper bound guards against oversized input reaching `PasswordEncoder`, and a minimum enforces basic password strength. (Note: BCrypt has a well-known 72-byte input limit, so a max around 72 is worth considering.)
3. No gap found in required-field enforcement or email-format validation — both are already correctly handled.
