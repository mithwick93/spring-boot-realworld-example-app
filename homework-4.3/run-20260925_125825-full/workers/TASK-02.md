# Worker Report: TASK-02

## Task
SCOPE: `src/main/java/io/spring/api/CurrentUserApi.java`
QUESTION: Does the update-current-user request DTO validate optional/partial fields (email format, size bounds) before persisting, per CLAUDE.md's Input Validation section?

## Findings

- `CurrentUserApi.java:44` — `updateProfile` annotates the request body `@Valid @RequestBody UpdateUserParam updateUserParam`, so whatever Bean Validation constraints exist on `UpdateUserParam` do get triggered before `userService.updateUser(...)` is called at `CurrentUserApi.java:46`. The controller itself performs no additional validation of its own.
- The actual field-level rules live in the DTO, which is outside this file's scope but is the direct target of the `@Valid` annotation referenced above — `src/main/java/io/spring/application/user/UpdateUserParam.java`:
  - `email` (line 18): `@Email(message = "should be an email")` — format is validated, but only when non-blank. Per Bean Validation semantics, `@Email` treats `null`/empty string as valid, and the field defaults to `""` (line 19), so a partial update that omits `email` passes validation trivially.
  - `password`, `username`, `bio`, `image` (lines 21–24): no validation annotations at all — no `@Size`, no `@NotBlank`, no format checks. Any length, including pathologically large strings, will pass `@Valid` and flow into `userService.updateUser(new UpdateUserCommand(currentUser, updateUserParam))` (`CurrentUserApi.java:46`) and on to persistence.
- CLAUDE.md's Security Requirements → Input Validation states: "Validate format and required fields via Bean Validation (`@NotBlank`, `@Email`, etc.); add `@Size` bounds on any new user-supplied string field." `UpdateUserParam` only satisfies the `@Email` half of that guidance for the `email` field; `username`, `password`, `bio`, and `image` have no `@Size` bounds despite being user-supplied strings persisted to the user record.

## Recommendations

- Add `@Size` bounds to `username`, `bio`, `image`, and `password` on `UpdateUserParam` (e.g. `@Size(max = 255)` or values matching the column widths in `V1__create_tables.sql`) so oversized partial-update values are rejected by Bean Validation instead of reaching persistence.
- If there is a minimum password length policy elsewhere in registration, consider mirroring it here (`@Size(min = ...)`) so update and create paths enforce the same rule — currently an empty-string password default combined with no `@Size`/`@NotBlank` means this DTO alone cannot guarantee a non-trivial password on update; verify how `UserService`/`UpdateUserCommand` treats blank vs. non-blank values before tightening, since that logic sits outside this file's scope.
- No gap was found in the *wiring* of validation (the `@Valid` annotation is correctly present on the controller method) — the gap is specifically in the DTO's field-level constraint coverage for `username`/`password`/`bio`/`image`.
