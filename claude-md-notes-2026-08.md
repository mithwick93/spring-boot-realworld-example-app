# CLAUDE.md notes — 2026-08

**Method note:** the conventions below (test naming, DTO placement, validation style,
error-shape split) were found by directly reading the source tree (`UsersApi.java`,
`ProfileApi.java`, `CustomizeExceptionHandler.java`, and the matching test files) and
appended to `CLAUDE.md`. The exchange below applies those documented conventions to a
new, unimplemented feature to check the conventions actually hold up. Recommended:
re-ask the same question inside your own `claude` session for a live confirmation —
this file captures the reasoning either way.

## Conventions test — "report a user" feature (not implemented in this repo)

**Question posed:**
> I want to add an endpoint for a user to report another user's profile with a reason.
> Where does the DTO go, what validation would it need, and what should I name the
> test method for "report succeeds"?

**Answer, applying CLAUDE.md's documented conventions:**

- **DTO placement:** a `ReportProfileParam` class, package-private, declared at the
  bottom of `ProfileApi.java` (alongside the `ProfileApi` class itself) — following the
  `LoginParam`-in-`UsersApi.java` pattern, not a separate file.
- **Envelope:** annotate it `@JsonRootName("report")` so the request body matches the
  project's `{"report": {...}}` style envelope, consistent with `{"user": {...}}` and
  `{"profile": {...}}`.
- **Validation:** a `reason` field annotated `@NotBlank(message = "can't be empty")`,
  bound via `@Valid @RequestBody` on the controller method — matching `LoginParam`'s
  style exactly (hand-written message, no i18n).
- **Endpoint shape:** `@PostMapping(path = "report")` inside `ProfileApi`, alongside
  `follow`/`unfollow`, taking `@PathVariable("username")`, `@AuthenticationPrincipal User`,
  and `@Valid @RequestBody ReportProfileParam` — unlike `follow`/`unfollow`, this one
  needs a body since it carries a reason.
- **Test method name:** `should_report_profile_success` for the happy path, and
  `should_show_error_message_for_blank_reason` for the validation failure — matching
  the `should_<outcome>_<condition>` pattern exactly.
- **Error shape to expect:** a blank `reason` would trigger `MethodArgumentNotValidException`,
  so the error response is the `{"errors": {"reason": ["can't be empty"]}}` shape, NOT
  the flat `{"message": ...}` shape used for auth failures. This is exactly the pitfall
  CLAUDE.md now flags — worth confirming in a real test rather than assuming.

## What `/init` missed

`/init` generated a strong, specific CLAUDE.md (commands, architecture, persistence,
security were all concrete and correct) but it could not see two things that only show
up by reading actual controller/exception code: (1) the test method naming convention
(`should_<outcome>_<condition>`), and (2) that error responses are NOT uniformly shaped —
field-validation errors use `{"errors": {...}}` while auth failures use a flat
`{"message": ...}`. The second one is the sharper miss: it's the kind of inconsistency
that silently breaks a new endpoint's tests if you assume one shape everywhere.

## Auto-memory check

`/memory` → "Open auto-memory folder" showed 0 items for this project. No overlap with
CLAUDE.md yet — auto-memory only accumulates facts as sessions progress; on a first pass,
everything durable has to come from what's deliberately written into CLAUDE.md.
