# Worker Report: TASK-01

## Task
SCOPE: src/main/java/io/spring/api/UsersApi.java
QUESTION: Do the registration and login request DTOs validate required fields, email format, and length bounds on user-supplied strings, per CLAUDE.md's Security Requirements > Input Validation section?

## Findings

`UsersApi.java` defines the login endpoint and its DTO directly, and delegates registration to an external DTO:

- `LoginParam` (`src/main/java/io/spring/api/UsersApi.java:72-79`):
  - `email` (line 73-75): `@NotBlank(message = "can't be empty")` + `@Email(message = "should be an email")` — required-field and email-format validation both present.
  - `password` (line 77-78): `@NotBlank(message = "can't be empty")` — required-field validation present, but **no `@Size` bound** on the string.
  - Both endpoints correctly wire `@Valid @RequestBody` on the controller methods (`createUser` at line 40, `userLogin` at line 48), so annotations are actually enforced.

- `RegisterParam` is imported at `UsersApi.java:10` (`io.spring.application.user.RegisterParam`) and used at `UsersApi.java:40-41` for the `/users` POST endpoint. This class lives outside the assigned scope file, but since `createUser` depends entirely on it for input validation, it's included here for completeness:
  - `email` (`RegisterParam.java:15-18`): `@NotBlank` + `@Email` + `@DuplicatedEmailConstraint` — required + format + uniqueness, but **no `@Size` bound**.
  - `username` (`RegisterParam.java:20-22`): `@NotBlank` + `@DuplicatedUsernameConstraint` — required + uniqueness only, **no `@Size` bound**.
  - `password` (`RegisterParam.java:24-25`): `@NotBlank` only — **no `@Size` bound**.

**Gap vs. CLAUDE.md Security Requirements > Input Validation:** that section requires "`@Size` bounds on any new user-supplied string field." None of the four string fields across `LoginParam` and `RegisterParam` (`email`, `password` in `LoginParam`; `email`, `username`, `password` in `RegisterParam`) declare a `@Size` constraint. Required-field (`@NotBlank`) and email-format (`@Email`) validation are present and correctly applied everywhere they're semantically relevant, but length-bound validation is entirely absent. This means arbitrarily long strings (e.g. a multi-megabyte `username` or `password`) can reach the domain/persistence layer, which is both a validation gap and a potential resource-exhaustion/storage concern.

## Recommendations

Add `@Size` bounds to user-supplied string fields, consistent with the existing annotation style (hand-written messages, no i18n):

- `UsersApi.java` `LoginParam.password` (line 77-78): add e.g. `@Size(max = 100, message = "is too long")` (login password just needs an upper bound to prevent abuse; exact value should match whatever bound is enforced at registration).
- `RegisterParam.java`:
  - `email` (line 15-18): add a reasonable max (e.g. `@Size(max = 255, message = "is too long")`, matching typical email/DB column limits).
  - `username` (line 20-22): add both a sane min and max (e.g. `@Size(min = 1, max = 50, message = "must be between 1 and 50 characters")` — `@NotBlank` already covers empty/blank, so a max is the main gap).
  - `password` (line 24-25): add a min/max (e.g. `@Size(min = 8, max = 100, message = "must be between 8 and 100 characters")`) — this also has a security-hygiene benefit (minimum password length) beyond satisfying the CLAUDE.md rule.

No gap found in required-field or email-format validation — those are fully covered for both DTOs.
