# Worker Report: TASK-01

## Task
SCOPE: `src/main/java/io/spring/api/UsersApi.java`
QUESTION: Do the registration and login request DTOs validate required fields, email format, and length bounds on user-supplied strings, per CLAUDE.md's Security Requirements > Input Validation section?

## Findings

CLAUDE.md's Input Validation section requires: "Validate format and required fields via Bean Validation (`@NotBlank`, `@Email`, etc.); add `@Size` bounds on any new user-supplied string field."

**`LoginParam`** (`UsersApi.java:72-79`, the login request DTO, used at `UsersApi.java:48`):
- `email` (line 73-75): `@NotBlank` (required ✓) and `@Email` (format ✓) present.
- `password` (line 77-78): `@NotBlank` (required ✓) present.
- No field has a `@Size` bound. Password in particular has no minimum length, so an empty-after-trim-check-passing single-character password is accepted, and there's no upper bound on either field.

**`RegisterParam`** (`src/main/java/io/spring/application/user/RegisterParam.java:14-26`, the registration request DTO — technically outside the literal scope file but imported and used directly at `UsersApi.java:10` and `UsersApi.java:40` as the `@Valid @RequestBody` type for `createUser`, so it's included here since the question explicitly asks about the registration DTO):
- `email` (line 15-18): `@NotBlank` + `@Email` present (required + format ✓).
- `username` (line 20-22): `@NotBlank` present (required ✓), but no format constraint at all (no `@Pattern`, no length bound).
- `password` (line 24-25): `@NotBlank` present (required ✓), no minimum-length or complexity constraint.
- No field on `RegisterParam` has a `@Size` bound either.

**Gap summary:** Both DTOs satisfy the "required fields" and "email format" parts of the Input Validation requirement, but neither satisfies the `@Size` bounds requirement — none of `email`, `username`, or `password` on either DTO has any length restriction (min or max). This means:
- Arbitrarily long strings can be submitted for any of these fields (no max), which is a direct gap against the documented rule.
- Password/username have no practical minimum length beyond non-blank (e.g., a 1-character password or username is currently accepted).

## Recommendations

Add `@Size` constraints per CLAUDE.md's explicit instruction:
- `RegisterParam.email` / `LoginParam.email`: `@Size(max = 255, message = "...")` (email format already partially bounds shape, but not length).
- `RegisterParam.username`: `@Size(min = 1, max = 50, message = "...")` or similar, matching whatever the DB column width is.
- `RegisterParam.password` / `LoginParam.password`: `@Size(min = 8, max = 100, message = "...")` to enforce a reasonable minimum password strength and cap payload size.

Confirm the chosen max values align with the corresponding column widths in `V1__create_tables.sql` to avoid silent truncation or DB-level errors surfacing as unhandled exceptions.
