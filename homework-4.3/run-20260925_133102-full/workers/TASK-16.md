# Worker Report: TASK-16

## Task
SCOPE: `src/main/java/io/spring/application/user/UpdateUserParam.java`
QUESTION: Does this DTO's Bean Validation annotations enforce the same bounds as `RegisterParam` where fields are equivalent, and is the blank-means-unchanged semantics for partial updates documented?

## Findings

**Bounds comparison against `RegisterParam`:**

| Field | `RegisterParam` (`RegisterParam.java`) | `UpdateUserParam` (`UpdateUserParam.java`) |
|---|---|---|
| email | `@NotBlank` (L15) + `@Email` (L16) + `@DuplicatedEmailConstraint` (L17) | `@Email` only (`UpdateUserParam.java:18`) — no `@NotBlank`, no duplicate-check constraint on the field itself |
| username | `@NotBlank` (L20) + `@DuplicatedUsernameConstraint` (L21) | no annotation at all (`UpdateUserParam.java:22`) |
| password | `@NotBlank` (L24) | no annotation at all (`UpdateUserParam.java:21`) |
| bio / image | n/a (not in `RegisterParam`) | no annotation (`UpdateUserParam.java:23-24`) |

None of the fields in `UpdateUserParam` carry a `@Size` upper bound, and `RegisterParam` doesn't either — so there's no size-bound *regression*, but per CLAUDE.md's Security Requirements > Input Validation ("add `@Size` bounds on any new user-supplied string field"), neither DTO currently satisfies that bar. This report only asked about parity with `RegisterParam`, and on that narrower question the DTOs are **not** at parity: `UpdateUserParam` deliberately omits `@NotBlank` on `email`/`username`/`password` (default value `""` via `@Builder.Default`, `UpdateUserParam.java:17,21-24`), because blank is the intentional "field not being changed" sentinel (see below) — `@NotBlank` would break every partial update that omits a field. So the missing `@NotBlank` is by design, not an oversight.

However, the duplicate-value check *is* missing from the DTO layer for updates: `RegisterParam.email`/`username` carry `@DuplicatedEmailConstraint`/`@DuplicatedUsernameConstraint` directly on the fields (`RegisterParam.java:17,21`), enforcing uniqueness at the same validation pass as format checks. `UpdateUserParam` has no such per-field constraint — the equivalent uniqueness check is instead implemented separately as a class-level constraint (`UpdateUserConstraint`/`UpdateUserValidator`) on `UpdateUserCommand`, applied via `@Valid` in `UserService.updateUser` (`UserService.java:47`, validator at `UserService.java:60-107`). This is a different mechanism (compares against `command.getTargetUser()` to allow a user to "re-submit" their own current email/username) — functionally reasonable, but it means the uniqueness bound is not visible by reading `UpdateUserParam.java` alone; it's asserted in a different file (`UserService.java`) against a different type (`UpdateUserCommand`), which could confuse someone auditing DTO-level validation in isolation.

**Blank-means-unchanged semantics — documentation:**

`UpdateUserParam.java` itself has **no comment or Javadoc** anywhere in the file explaining that blank string fields mean "leave unchanged." The defaults (`= ""` via `@Builder.Default`, lines 17/21-24) hint that blank is a deliberate sentinel rather than an omission, but nothing states what blank *does*. The actual behavior is implemented three files away in `io.spring.core.user.User#update` (`User.java:29-49`), which checks `!Util.isEmpty(field)` before assigning each of email/username/password/bio/image (e.g. `User.java:30-32` for email). That method also has no comment explaining the semantics — it's discoverable only by reading the conditional logic itself.

## Recommendations

1. Add a short comment on `UpdateUserParam` (or on `User#update`, `User.java:29`) stating explicitly that blank/empty string values are treated as "no change requested" for that field — this is non-obvious behavior spread across three files (`UpdateUserParam.java`, `UpdateUserCommand.java`, `User.java`) and isn't derivable from any single one of them.
2. Consider whether `@Size` upper bounds should be added to `email`/`username`/`bio`/`image` on both `RegisterParam` and `UpdateUserParam` per CLAUDE.md's Input Validation requirement ("add `@Size` bounds on any new user-supplied string field") — this is a pre-existing gap on both DTOs, not something introduced by `UpdateUserParam`, so it's a candidate for a follow-up rather than a blocking finding here.
3. No functional gap found in the missing `@NotBlank`/duplicate-constraint annotations on `UpdateUserParam` itself — they are intentionally absent to support partial updates, and the uniqueness check is (correctly, if less discoverably) enforced one layer up via `UpdateUserConstraint` on `UpdateUserCommand` (`UserService.java:60-107`). If discoverability is a concern, a comment cross-referencing `UpdateUserConstraint` from `UpdateUserParam.java` would help future readers connect the two.
