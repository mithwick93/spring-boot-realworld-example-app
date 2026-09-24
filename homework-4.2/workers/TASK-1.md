# Worker Report: TASK-1

## Task
SCOPE: `src/main/java/io/spring/api/UsersApi.java`
QUESTION: Do the registration and login request DTOs (`RegistrationParam`, `LoginParam`) validate required fields, email format, and enforce length bounds on user-supplied strings?

## Findings

### `LoginParam` (defined in this file, `UsersApi.java:72-79`)
- `email` field (`UsersApi.java:73-75`): `@NotBlank(message = "can't be empty")` + `@Email(message = "should be an email")` — required-field and email-format validation are both present.
- `password` field (`UsersApi.java:77-78`): `@NotBlank(message = "can't be empty")` only — required-field validation present, but **no `@Size` bound** on this user-supplied string.
- The controller method `userLogin` (`UsersApi.java:47-58`) is annotated `@Valid @RequestBody LoginParam loginParam`, so these constraints are actually enforced at the boundary.

### `RegisterParam` (used, but NOT defined in this file)
- `UsersApi.java` only imports `RegisterParam` (`UsersApi.java:10`, `import io.spring.application.user.RegisterParam;`) and uses it as the `@Valid @RequestBody` type for `createUser` (`UsersApi.java:39-40`). Note the question refers to it as `RegistrationParam`, but the actual class name in the codebase is `RegisterParam`.
- The class body itself lives in `io.spring.application.user.RegisterParam`, which is **outside the declared SCOPE** (`src/main/java/io/spring/api/UsersApi.java`). Its field-level `@NotBlank`/`@Email`/`@Size` annotations cannot be verified from this file alone — only that `@Valid` is applied at the controller boundary (`UsersApi.java:40`), so whatever constraints exist on `RegisterParam` will be enforced.

## Recommendations

1. **Gap — `LoginParam.password` has no length bound.** Per this repo's CLAUDE.md, Security Requirements > Input Validation: "add `@Size` bounds on any new user-supplied string field." `password` at `UsersApi.java:77-78` currently has only `@NotBlank`, allowing an unbounded-length string to reach `passwordEncoder.matches(...)` (`UsersApi.java:51`). Recommend adding an upper bound, e.g. `@Size(max = 100, message = "is too long")`, consistent with the convention documented for new string fields.
2. **Scope gap — cannot confirm `RegisterParam`'s own validation.** To fully answer the question for the registration DTO, `io.spring.application.user.RegisterParam` must be read directly; it is out of this task's file scope. Recommend a follow-up check on that file specifically for `@NotBlank`/`@Email`/`@Size` on its username/email/password fields before concluding registration input validation is complete.
3. No other gaps found within `UsersApi.java` itself: both endpoints (`createUser`, `userLogin`) correctly use `@Valid @RequestBody`, so whatever constraints exist on the DTOs are enforced at the API boundary.
