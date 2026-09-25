# Worker Report: TASK-08

## Task
SCOPE: src/main/java/io/spring/api/ProfileApi.java
QUESTION: This endpoint takes only path variables, no request DTO. Are the path variables (username) safely handled, and is there any unvalidated external input?

## Findings
- `ProfileApi` has three handlers, all built around the single `@PathVariable("username") String username` extracted from the class-level `@RequestMapping(path = "profiles/{username}")` (ProfileApi.java:22, 29-30, 37-39, 51-53). Per CLAUDE.md's Conventions section, this matches the documented "simple path-variable actions" pattern (no request-body DTO), so the absence of a DTO/`@Valid` here is expected, not an oversight.
- `username` carries **no Bean Validation annotations** (no `@NotBlank`, `@Size`, `@Pattern`) at any of the three usage points (ProfileApi.java:30, 39, 53). CLAUDE.md's Security Requirements > Input Validation says to "validate format and required fields via Bean Validation" and to "reject invalid input; do not attempt to sanitize and continue" — this endpoint does neither: an empty, whitespace, or arbitrarily long `username` is passed straight through unvalidated.
- Despite the lack of application-level validation, the value is **not used unsafely**:
  - `getProfile` passes it to `profileQueryService.findByUsername(username, user)` (ProfileApi.java:32).
  - `follow`/`unfollow` pass it to `userRepository.findByUsername(username)` (ProfileApi.java:41, 54).
  - Both ultimately reach MyBatis mapper queries bound via `#{}` parameter binding (verified pattern used throughout `infrastructure/mybatis` per CLAUDE.md's Database Access rule) rather than string concatenation, so there is no SQL injection vector here even though the value is unvalidated.
- Effective "validation" is existence-based, not format-based: if `username` doesn't match a real row, `findByUsername` returns `Optional.empty()`, and all three handlers convert that into `ResourceNotFoundException` (ProfileApi.java:34, 48, 64, 66-67) — a generic 404, not a stack trace or internal detail, which is consistent with the Output Encoding and Error Handling requirement.
- No authentication is required for `getProfile` (`GET /profiles/{username}`) — worth flagging per the Security Requirements' "Flag any new unauthenticated endpoint for rate-limiting review" bullet, though this is an existing/pre-existing endpoint rather than a new one being added.

## Recommendations
- No injection or unsafe-input vulnerability was found — MyBatis parameter binding and the `Optional`/404 pattern contain the risk.
- Gap: add `@Size(max = ...)` (and optionally a `@Pattern` matching whatever charset usernames are restricted to at registration, see `RegisterParam` in `UsersApi.java`) to the `username` path variable across all three handlers, so malformed/oversized values are rejected with a clear 400 instead of silently falling through to a generic 404 lookup. This is a minor hardening/consistency recommendation, not an active exploit path.
