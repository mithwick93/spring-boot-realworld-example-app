# Worker Report: TASK-08

## Task
SCOPE: src/main/java/io/spring/api/ProfileApi.java
QUESTION: This endpoint takes only path variables, no request DTO. Are the path variables (username) safely handled, and is there any unvalidated external input?

## Findings

- `ProfileApi.java:22` declares the controller at `@RequestMapping(path = "profiles/{username}")`, and all three handlers (`getProfile` at `:28-35`, `follow` at `:37-49`, `unfollow` at `:51-68`) bind `username` via plain `@PathVariable("username") String username` — this matches the documented convention ("Simple path-variable actions ... skip the DTO step entirely — see `ProfileApi.follow`/`unfollow`") in CLAUDE.md's Conventions section, so the *absence* of a DTO is expected, not a gap.
- There is **no Bean Validation** on `username` (no `@Size`, `@Pattern`, `@NotBlank`) at the point it enters the controller (`ProfileApi.java:30`, `:39`, `:53`). Per CLAUDE.md's Security Requirements > Input Validation: "Treat ALL external input as untrusted (request bodies, query params, file contents)" and "add `@Size` bounds on any new user-supplied string field" — `username` here is external, untrusted input (a URL path segment) but currently unbounded/unvalidated in length or character set.
- Downstream, `username` flows to `UserRepository.findByUsername(username)` (`ProfileApi.java:41`, `:54`) and `ProfileQueryService.findByUsername(username, user)` (`ProfileApi.java:32`, `:46`, `:62`), which resolve to MyBatis mapper queries:
  - `UserReadService.xml:4-6` — `select * from users where username = #{username}`
  - `UserMapper.xml` `findByUsername` (interface `UserMapper.java:12`, `@Param("username")`)
  Both use `#{username}` MyBatis parameter binding (prepared-statement placeholder), **not** `${username}` string substitution — so this is not vulnerable to SQL injection, satisfying CLAUDE.md's Database Access rule ("Use MyBatis parameter binding (`#{param}`) only").
- No unvalidated input reaches a sink where it would cause injection, path traversal, or similar. The only functional consequence of an arbitrary/malformed username (e.g. very long string, empty string via a trailing slash edge case, or special characters) is a normal "no such user" `ResourceNotFoundException` (`:34`, `:48`, `:64`, `:66`) — a 404, not an error leaking internals.
- `unfollow` (`:51-68`) does have slightly more complex branching (nested `Optional`/`if` instead of `.orElseThrow` chaining like `follow`), but this is a style inconsistency, not a security issue — both paths correctly 404 on missing user/relation without exposing stack traces or internal state.

## Recommendations

- No exploitable input-validation or injection gap was found — MyBatis binding is safe, and unmatched usernames correctly 404 rather than leaking errors.
- Minor hardening (optional, not a security requirement given there's no data-integrity risk): consider a `@Size(max = ...)` or `@Pattern` constraint on the `username` path variable (via `@Validated` on the controller class + `@PathVariable @Size(...)`) to reject obviously-invalid values (e.g. empty, absurdly long) before hitting the repository layer, consistent with the CLAUDE.md guidance to bound user-supplied strings. This is a defense-in-depth suggestion, not a fix for a demonstrated vulnerability.
