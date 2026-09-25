# Worker Report: TASK-16

## Task
SCOPE: `src/main/java/io/spring/application/user/UpdateUserParam.java`
QUESTION: Does this DTO's Bean Validation annotations enforce the same bounds as `RegisterParam` where fields are equivalent, and is the blank-means-unchanged semantics for partial updates documented?

## Findings

**Bound comparison, field by field:**

- `email`: `UpdateUserParam.java:18` has `@Email(message = "should be an email")` — matches `RegisterParam.java:16`. But `RegisterParam.java:15` also has `@NotBlank(message = "can't be empty")`, which `UpdateUserParam` deliberately omits (see semantics below, this is intentional not a gap). Neither DTO has a `@Size` bound on email.
- `username`: `RegisterParam.java:20` has `@NotBlank`; `UpdateUserParam.java:22` has no annotation at all (just `@Builder.Default private String username = "";`). No `@Size` on either.
- `password`: `RegisterParam.java:24` has `@NotBlank`; `UpdateUserParam.java:21` has no annotation. No `@Size` bound on either — this is notable because password has no minimum-length enforcement anywhere, on registration or update. <!-- pragma: allowlist secret -->
- `bio`, `image`: present only on `UpdateUserParam.java:23-24`; `RegisterParam` has no equivalents, so there's nothing to compare against.

So for the two fields both DTOs share validation intent on (`email` format), the annotations do match. But no field in `UpdateUserParam` carries a `@Size` bound, and per CLAUDE.md's Security Requirements > Input Validation ("add `@Size` bounds on any new user-supplied string field"), this is a pre-existing gap that also exists in `RegisterParam` — not something `UpdateUserParam` introduces uniquely, but it also doesn't fix it. `password` and `username`/`bio`/`image` on `UpdateUserParam` have zero Bean Validation (`@Email` is the only annotation present on the whole class, `UpdateUserParam.java:18`).

**Blank-means-unchanged semantics — not documented at the DTO level:**

The actual "empty string = leave field unchanged" behavior is NOT implemented or explained anywhere in `UpdateUserParam.java`. Tracing the call path:
- `UpdateUserParam.java:17,21-24` — every field defaults to `""` via `@Builder.Default`, with no comment explaining why an empty string is the chosen sentinel for "no change requested."
- `CurrentUserApi.java:44-46` — the controller passes the raw `UpdateUserParam` through to `UserService.updateUser` unchanged.
- `UserService.java:47-57` — `updateUser` forwards the four scalar fields straight into `user.update(...)` with no transformation or comment.
- `core/user/User.java:29-49` — this is where the actual semantics live: `if (!Util.isEmpty(email)) { this.email = email; }` (and identically for username, password, bio, image). This is the only place in the codebase that implements "blank means unchanged," and it has no Javadoc/comment stating that contract.

So a reader who only opens `UpdateUserParam.java` has no way to know that leaving a field blank means "don't change this field" — that behavior is an implicit contract enforced three files away, undocumented at every hop.

## Recommendations

1. Add a class- or field-level comment on `UpdateUserParam.java` (e.g. above line 15 or above each `@Builder.Default` field) stating that an empty string is treated as "no change" and the actual merge logic lives in `User.update()` — this is exactly the kind of non-obvious, hidden-invariant case the project's comment policy calls for documenting.
2. Add a short comment on `User.update()` (`core/user/User.java:29`) cross-referencing `UpdateUserParam` so the contract is discoverable from either side.
3. Per CLAUDE.md's Input Validation requirement to "add `@Size` bounds on any new user-supplied string field," consider adding `@Size` constraints to `username`, `password`, `bio`, `image` on `UpdateUserParam` (and correspondingly on `RegisterParam`'s `username`/`password`) — currently neither DTO bounds string length, which is a gap in both, not one introduced by `UpdateUserParam` alone.
4. No mismatch was found between the two DTOs' *existing* annotations for the fields they share (`email`) — the gap is one of missing bounds and missing documentation, not inconsistency between the two files.
