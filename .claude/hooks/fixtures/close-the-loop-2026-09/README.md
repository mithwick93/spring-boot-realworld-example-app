# Fixture: duplicate leftover class after DTO extraction (4.6)

Reconstructs the real failure from the `move-login-param-dto` session (2026-09-28,
`feat/rest-api-dto-rule`): extracting `LoginParam` out of `UsersApi.java` into its own
file left the trailing `class LoginParam { ... }` body behind in `UsersApi.java` even
after its imports were stripped, so `UsersApi.java` and the new `LoginParam.java` both
declared `io.spring.api.LoginParam` — a duplicate top-level class across two files in
the same package, which javac rejects at compile time. The agent's own diagnostics
correctly flagged this, but it was dismissed as a known pre-existing JDK/IDE issue
instead of investigated.

- `broken/` — the bad intermediate state: imports removed from `UsersApi.java`, but the
  trailing `LoginParam` class body still present, alongside the new `LoginParam.java`.
  Does not compile (`duplicate class: io.spring.api.LoginParam`).
- `fixed/` — the corrected state: `UsersApi.java` fully stripped of the class and its
  now-unused imports, `LoginParam.java` holds the extracted class. Compiles cleanly.

Used by `verify-java-compile.sh`'s self-test (see `close-the-loop-2026-09.md`) to prove
the compile check actually catches this failure, not just in theory.
