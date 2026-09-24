# Worker Report: TASK-2

## Task
SCOPE: `src/main/java/io/spring/api/CurrentUserApi.java`
QUESTION: Does the update-current-user request DTO validate optional/partial fields (email format, size bounds) before they are persisted?

## Findings

- `CurrentUserApi.java:40-49` — `updateProfile()` is annotated `@Valid @RequestBody UpdateUserParam updateUserParam` (line 44), so Bean Validation *is* wired into the update path; a failing constraint would short-circuit before `userService.updateUser(...)` (line 46) is called and anything is persisted.
- The actual constraints live on the DTO, `src/main/java/io/spring/application/user/UpdateUserParam.java`, not in `CurrentUserApi.java` itself:
  - `email` (line 18) has `@Email(message = "should be an email")` — format is validated, but since the field defaults to `""` (line 19) and `@Email` treats an empty string as valid, an unset/omitted email in a partial update passes trivially. A malformed non-empty email is caught.
  - `password`, `username`, `bio`, `image` (lines 21-24) have **no validation annotations at all** — no `@NotBlank`, no `@Size`, no format constraint. Any length or content is accepted and flows straight into `userService.updateUser(new UpdateUserCommand(...))` at `CurrentUserApi.java:46` and on into persistence.
- Per this repo's `CLAUDE.md` → Security Requirements → Input Validation: "Validate format and required fields via Bean Validation (`@NotBlank`, `@Email`, etc.); add `@Size` bounds on any new user-supplied string field." The `email` field partially satisfies this (format only); `password`, `username`, `bio`, and `image` do not satisfy it at all — there are no `@Size` bounds on any of them, despite all being user-supplied strings persisted via the update flow.

## Recommendations

Gap confirmed, not a false alarm:
1. Add `@Size` bounds (e.g. `@Size(max = ...)`) to `username`, `bio`, `image`, and `password` in `UpdateUserParam.java` to satisfy the CLAUDE.md requirement and prevent unbounded strings from reaching persistence.
2. Consider whether `password` needs a minimum length constraint (currently unconstrained), consistent with how registration/login-adjacent flows should treat credentials.
3. `email`'s current behavior (empty string = valid, meant to signal "field not provided/no change") is intentional-looking but worth a comment or explicit "unchanged" sentinel if that's the real intent, since it's non-obvious from the annotation alone.
