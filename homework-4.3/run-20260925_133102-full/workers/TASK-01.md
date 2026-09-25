# Worker Report: TASK-01

## Task
SCOPE: src/main/java/io/spring/api/UsersApi.java
QUESTION: Do the registration and login request DTOs validate required fields, email format, and length bounds on user-supplied strings, per CLAUDE.md's Security Requirements > Input Validation section?

## Findings

**Login DTO (`LoginParam`, defined in-file at UsersApi.java:72-79):**
- `email` (UsersApi.java:73-75): `@NotBlank(message = "can't be empty")` + `@Email(message = "should be an email")` — required-field and email-format checks are present.
- `password` (UsersApi.java:77-78): `@NotBlank(message = "can't be empty")` — required-field check present.
- **Gap:** Neither `email` nor `password` has a `@Size` (or similar) upper-bound constraint. CLAUDE.md's Security Requirements > Input Validation explicitly says to "add `@Size` bounds on any new user-supplied string field" — `LoginParam` has none. An attacker can submit an arbitrarily long string in either field.
- Both endpoints route validation via `@Valid @RequestBody` (UsersApi.java:40, 48), consistent with the project's documented Bean Validation convention.

**Registration DTO (`RegisterParam`):**
- `RegisterParam` is only *referenced* in UsersApi.java — imported at UsersApi.java:10 (`io.spring.application.user.RegisterParam`) and used as the `@Valid @RequestBody` parameter of `createUser` (UsersApi.java:40). Its field declarations and annotations live in `io.spring.application.user.RegisterParam`, a separate file that is **outside this task's SCOPE** (`src/main/java/io/spring/api/UsersApi.java` only).
- Consequently, within the bounds of this scoped review, I cannot confirm from UsersApi.java alone whether `RegisterParam`'s fields (presumably username/email/password) satisfy required-field, email-format, or `@Size` length-bound validation — that would require reading `src/main/java/io/spring/application/user/RegisterParam.java`, which is a different worker's scope or a follow-up task.
- What UsersApi.java does confirm: `createUser` correctly gates the request through `@Valid`, so whatever constraints exist on `RegisterParam` will be enforced by Spring's validation pipeline (UsersApi.java:40).

## Recommendations

1. **Add `@Size` bounds to `LoginParam.email` and `LoginParam.password`** (UsersApi.java:72-79), per CLAUDE.md's Input Validation requirement ("add `@Size` bounds on any new user-supplied string field"). E.g., `@Size(max = 255)` on `email` and a reasonable `@Size(max = 255)` (or a project-consistent bound) on `password`. Currently both fields only enforce non-blank/format, not length, so oversized payloads are accepted and passed to `passwordEncoder.matches(...)` and a `findByEmail` lookup unbounded in size.
2. **Escalate a follow-up check on `RegisterParam`** (`src/main/java/io/spring/application/user/RegisterParam.java`) to verify it has required-field, `@Email`, and `@Size` constraints on its username/email/password fields — this file is out of the current SCOPE and was not read as part of this task.
3. No gap found regarding required-field or email-format validation for `LoginParam` — those are correctly implemented (UsersApi.java:73-75, 77-78).
