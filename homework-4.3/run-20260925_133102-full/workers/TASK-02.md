# Worker Report: TASK-02

## Task
SCOPE: `src/main/java/io/spring/api/CurrentUserApi.java`
QUESTION: Does the update-current-user request DTO validate optional/partial fields (email format, size bounds) before persisting, per CLAUDE.md's Input Validation section?

## Findings

- `CurrentUserApi.updateProfile` (`CurrentUserApi.java:40-49`) binds the request body to `UpdateUserParam` with `@Valid @RequestBody` (`CurrentUserApi.java:44`), so any Bean Validation constraints declared on `UpdateUserParam` *are* enforced before `userService.updateUser(...)` is called at `CurrentUserApi.java:46`.
- The DTO, `io.spring.application.user.UpdateUserParam` (`UpdateUserParam.java:15-25`), declares five optional/partial-update fields: `email`, `password`, `username`, `bio`, `image`, each defaulted to `""` via `@Builder.Default`.
- Only `email` carries a validation annotation: `@Email(message = "should be an email")` at `UpdateUserParam.java:18`. Per javax.validation semantics, `@Email` treats `null`/empty `CharSequence` as valid, which is consistent with this field being optional for partial updates — but it also means a caller can send `"email": ""` and it passes trivially (matches existing default, not itself a gap).
- **No `@Size` (or any other) bounds exist on `password`, `username`, `bio`, `image`, or even `email` itself** (`UpdateUserParam.java:21-24`). CLAUDE.md's Input Validation section explicitly states: *"add `@Size` bounds on any new user-supplied string field"* — none of these four fields have any upper bound, so a caller can submit e.g. a multi-megabyte `bio`, `image` URL, or `username` and it will pass `@Valid` unchanged.
- Downstream, `UserService.updateUser` (`UserService.java:47-55`) is itself annotated `@Valid` on `UpdateUserCommand`, but it only forwards the same unbounded `UpdateUserParam` values into the update call — there is no additional length/format check before persistence.
- This is consistent with the repo's own documented pitfall list, which calls out missing `@Size` bounds as a gap to check for on any new *or existing* user-supplied string field, not just newly added ones.

## Recommendations

1. Add `@Size(max = <n>)` bounds to `username`, `password`, `bio`, `image`, and `email` in `UpdateUserParam.java` (lines 18-24), matching whatever column/display limits are appropriate (check the `users` table schema in `V1__create_tables.sql` for existing column widths to pick consistent bounds).
2. For `password` specifically, also consider a minimum `@Size` (e.g. `@Size(min = 8, max = ...)`) since it's currently unbounded in both directions — an empty-string password update is currently indistinguishable from "not changing password" only because `UserService` presumably blank-checks before persisting (not visible in the scoped file; verify in `UserService`/`User` entity).
3. No other gap found in `CurrentUserApi.java` itself — the `@Valid @RequestBody` wiring at line 44 is correct and does route through Bean Validation; the gap is entirely in the DTO's missing constraints, not in how the controller invokes validation.
