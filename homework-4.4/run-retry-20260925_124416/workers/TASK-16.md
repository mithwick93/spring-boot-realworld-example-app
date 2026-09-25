# Worker Report: TASK-16

## Task
SCOPE: src/main/java/io/spring/application/user/UpdateUserParam.java
QUESTION: Does this DTO's Bean Validation annotations enforce the same bounds as RegisterParam where fields are equivalent, and is the blank-means-unchanged semantics for partial updates documented?

## Findings

**Bounds comparison (`UpdateUserParam.java:15-24` vs `RegisterParam.java:14-26`):**

| Field | RegisterParam | UpdateUserParam | Same? |
|---|---|---|---|
| email | `@NotBlank` + `@Email` + `@DuplicatedEmailConstraint` (`RegisterParam.java:15-18`) | `@Email` only, default `""` (`UpdateUserParam.java:17-19`) | No — `@NotBlank` and the duplicate-email check are missing |
| username | `@NotBlank` + `@DuplicatedUsernameConstraint` (`RegisterParam.java:20-22`) | no annotations, default `""` (`UpdateUserParam.java:22`) | No — no validation at all |
| password | `@NotBlank` (`RegisterParam.java:24-25`) | no annotations, default `""` (`UpdateUserParam.java:21`) | No — no validation at all |
| bio / image | n/a (not in RegisterParam) | no annotations (`UpdateUserParam.java:23-24`) | n/a |

Neither DTO has a `@Size` upper bound on any field (email, username, password, bio, image), so CLAUDE.md's "Security Requirements > Input Validation" instruction to "add `@Size` bounds on any new user-supplied string field" is currently unmet by both DTOs — this is a pre-existing gap, not a regression introduced by `UpdateUserParam`.

The `@NotBlank` asymmetry is intentional, not an oversight in isolation: `UpdateUserParam` fields default to `""` and are meant to represent "field not being updated," so a `@NotBlank` constraint would break the partial-update contract (see below). However, the duplicate-email/duplicate-username checks are still performed for updates — just via a different mechanism: `UserService.java:60-107` defines a package-private `@UpdateUserConstraint`/`UpdateUserValidator` that runs `userRepository.findByEmail`/`findByUsername` against the *raw* `updateUserParam.getEmail()`/`getUsername()` values (`UserService.java:77-87`) even when they are blank strings. This means an empty-string email/username is looked up in the repository on every update call, which is wasted work but not a security bug since `findByEmail("")`/`findByUsername("")` will almost always return empty and pass validation trivially.

**Blank-means-unchanged semantics — documentation status:**

The actual "blank field means don't change this field" behavior is implemented in `User.update()` (`io.spring.core.user.User.java:29-49`), which wraps every field assignment in `if (!Util.isEmpty(...))`. **This semantics is not documented anywhere near `UpdateUserParam.java`** — the DTO itself (`UpdateUserParam.java:1-25`) has no comment explaining why every field defaults to `""` instead of `null`, and there's no Javadoc pointing a reader from the DTO to `User.update()` where the actual skip-if-blank logic lives. A reader of `UpdateUserParam.java` in isolation would not know that leaving a field blank is a deliberate "no-op for this field" signal rather than "clear this field to empty" — that intent is only discoverable by tracing `UpdateUserCommand` → `UserService.updateUser` → `User.update`.

This also means Bean Validation on `UpdateUserParam` cannot use `@NotBlank`/`@Size(min=...)` the way `RegisterParam` does, because blank is a valid, meaningful "unchanged" value for update — but nothing on the DTO enforces an upper bound either, so an attacker could still submit an excessively long email/username/bio/image on update with no `@Size` guard, same gap as `RegisterParam`.

## Recommendations

1. Add a doc comment on `UpdateUserParam` (or on `User.update()`, cross-referenced) stating explicitly that blank string fields mean "leave unchanged," since this is a non-obvious contract split across three files (`UpdateUserParam` → `UpdateUserCommand`/`UserService` → `User.update()`) and CLAUDE.md's own "Pitfall" conventions section sets a precedent for documenting this kind of cross-file behavior.
2. Add `@Size` upper bounds to `email`, `username`, `bio`, and `image` on `UpdateUserParam` (and to `email`/`username`/`password` on `RegisterParam`), per CLAUDE.md's Input Validation requirement — currently neither DTO bounds these fields, so an unbounded string can reach persistence via either the register or update path.
3. Optional cleanup: short-circuit the `UpdateUserValidator` duplicate-check in `UserService.java:76-87` when `inputEmail`/`inputUsername` is blank, to avoid a pointless repository lookup on every partial update that doesn't touch those fields.
