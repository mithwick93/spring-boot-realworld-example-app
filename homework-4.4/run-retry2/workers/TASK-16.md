# Worker Report: TASK-16

## Task
SCOPE: src/main/java/io/spring/application/user/UpdateUserParam.java
QUESTION: Does this DTO's Bean Validation annotations enforce the same bounds as RegisterParam where fields are equivalent, and is the blank-means-unchanged semantics for partial updates documented?

## Findings

**Field-by-field comparison, `UpdateUserParam.java` vs `RegisterParam.java`:**

- `email`: Both classes apply `@Email` (`UpdateUserParam.java:18`, `RegisterParam.java:16`). `RegisterParam.java:15` additionally applies `@NotBlank`; `UpdateUserParam` has no `@NotBlank` on `email` and instead defaults the field to `""` via `@Builder.Default` (`UpdateUserParam.java:17-19`). This is not a missed bound — it's intentional, since `UpdateUserParam` is a partial-update DTO (see below) and requiring non-blank would break "leave unchanged" semantics.
- `username`: `RegisterParam.java:20-21` requires `@NotBlank`; `UpdateUserParam.java:22` has no constraint at all (not even `@Email`-equivalent format checks, which is correct since username has no format constraint in `RegisterParam` either). Consistent absence of a bound here, same reasoning as email.
- `password`: `RegisterParam.java:24-25` requires `@NotBlank`; `UpdateUserParam.java:21` has no constraint. Same "blank = unchanged" reasoning applies. <!-- pragma: allowlist secret -->
- `bio`, `image`: Present only in `UpdateUserParam.java:23-24`; `RegisterParam` has no equivalent fields, so there is nothing to compare against.
- **Duplicate-value checks**: `RegisterParam.java:17,21` carry `@DuplicatedEmailConstraint` / `@DuplicatedUsernameConstraint` directly on the fields. `UpdateUserParam` carries no such per-field annotation; instead, the equivalent duplicate check is done at the command level via `@UpdateUserConstraint` on `UpdateUserCommand`, implemented by `UpdateUserValidator` (`UserService.java:60-107`), which reads `value.getParam().getEmail()`/`getUsername()` and excludes the current user from the collision check. So duplicate-checking bound *is* preserved for update, just relocated to a cross-field validator (a similar pattern to the `<cross-parameter>` convention documented in CLAUDE.md, though this one is single-object, not multi-parameter, so it doesn't hit the `<cross-parameter>` pitfall).
- **No `@Size` bounds on any string field in either class** (`email`, `username`, `password`, `bio`, `image` in `UpdateUserParam.java:17-24`; `email`, `username`, `password` in `RegisterParam.java:15-25`). Per CLAAUDE.md "Input Validation": *"add `@Size` bounds on any new user-supplied string field"* — this is a pre-existing gap shared by both DTOs, not something `UpdateUserParam` introduces or fails to match; flagging since `password` in particular has no upper bound in either class, and BCrypt (used via `PasswordEncoder`, see `WebSecurityConfig`) silently truncates input beyond 72 bytes, so overlong passwords are accepted but only the first 72 bytes are effectively hashed. <!-- pragma: allowlist secret -->

**Blank-means-unchanged semantics:**

- The actual behavior lives in `core/user/User.java:29-49` (`User.update(...)`): each field is only overwritten `if (!Util.isEmpty(...))`. This is what makes blank fields in `UpdateUserParam` act as "leave unchanged" rather than "clear the field."
- `UpdateUserParam.java` itself (the DTO in scope) has **zero comments or Javadoc** documenting this contract — nothing on the class (`UpdateUserParam.java:15`) or on any of the five fields (`UpdateUserParam.java:17-24`) states that an empty string means "don't change this field."
- The consuming endpoint, `CurrentUserApi.updateProfile` (`api/CurrentUserApi.java:40-49`), also has no comment explaining this to a reader relying only on the API layer.
- `UserService.updateUser` (`UserService.java:47-57`), which bridges `UpdateUserParam` to `User.update(...)`, likewise has no comment — contrast with `UserService.createUser` at `UserService.java:34`, which does have a one-line Javadoc (`/** Creates and persists a new user from registration input. */`) but says nothing about update semantics.
- Net effect: the "blank means unchanged" contract is discoverable only by reading `core/user/User.java`, three layers away from the DTO named in scope. Nothing in `UpdateUserParam.java` signals to a caller (REST or GraphQL, see `graphql/UserMutation.java:78-79`, which builds `UpdateUserParam` directly) that submitting `""` for a field is different from submitting the current value.

## Recommendations

1. No change needed to bring `UpdateUserParam`'s bounds in line with `RegisterParam` — the differences (`@NotBlank`/`@DuplicatedXConstraint` present on `RegisterParam` but absent on `UpdateUserParam`) are intentional consequences of partial-update semantics, not a validation gap.
2. Document the blank-means-unchanged contract directly on `UpdateUserParam.java` (e.g., a short Javadoc on the class or on each field: "empty string leaves this field unchanged on update") so the semantics are visible at the DTO itself rather than only in `core/user/User.java:29-49`. This is a documentation gap, not a security bug, but it's exactly the kind of non-obvious cross-layer behavior CLAUDE.md's Conventions section calls out as worth writing down.
3. Consider adding `@Size` upper bounds to `password` (and ideally `username`/`bio`/`image`) on both `RegisterParam` and `UpdateUserParam`, per CLAUDE.md's Input Validation requirement, to avoid accepting arbitrarily long input that BCrypt will silently truncate for `password` and that has no persistence-layer size guarantee for the others (schema bounds should be checked separately in the Flyway migration if this is pursued).
