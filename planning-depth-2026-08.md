# Planning-depth comparison: Risk 1 reconsideration on the pagination-validation plan

Context: this is a log of a plan-mode conversation that revisited **Risk 1** of
`plan-article-pagination-validation-2026-08.md` (should offset/limit validation live at
the `@RequestParam` boundary, inside `Page` itself, or both?) at three different
reasoning depths, plus a follow-up investigation into whether
`CustomizeExceptionHandler.handleConstraintViolation` already covers the new
validation case. Kept because the *reversals* and *self-corrections* are the
interesting part — each depth increase caught something the previous pass missed
or got wrong.

---

## Round 1 — `/effort medium`, no `ultrathink`

**Prompt:** "Given plan-article-pagination-validation-2026-08.md, reconsider Risk 1 —
should offset/limit validation live at the `@RequestParam` boundary, inside `Page`
itself, or both?"

**Investigation:** Read `Page.java` and `ArticlesApi.java` directly. Spawned an
Explore agent to confirm `Page`'s two-arg constructor has exactly one production
caller (`ArticlesApi`, both endpoints via `new Page(offset, limit)`), and that no
existing test relies on the clamp behavior.

**Conclusion reached: validate at *both* layers, for different reasons —**
- `@RequestParam` + `@Min`/`@Max` + `@Validated` on `ArticlesApi` → owns the *wire
  contract* (produces the actual `422` HTTP response).
- Inside `Page`, replace the silent clamp with a thrown `IllegalArgumentException`
  → owns the *domain invariant*, protecting any future caller that isn't
  `ArticlesApi`.

Rationale at the time: `@RequestParam`-only leaves `Page` silently clamping forever
for any caller that isn't yet validated; `Page`-only loses the `422` shape since a
plain `IllegalArgumentException` isn't mapped by `CustomizeExceptionHandler`. "Both"
looked like the safe, complete answer.

Plan file was updated accordingly (`Page.java` changes from "leave alone" to
"actively remove the clamp and throw instead").

The user rejected `ExitPlanMode` without comment and re-issued the same question
with `ultrathink`.

---

## Round 2 — same question, `ultrathink`

**What deeper reasoning caught that medium effort missed:** the *mechanical* fact
that with `@Validated` + `@Min`/`@Max` on `@RequestParam`, Spring's method-validation
interceptor runs **before** the controller method body executes. If `offset`/`limit`
are invalid, `getArticles`/`getFeed` never runs — `new Page(offset, limit)` is never
reached. So a `Page`-side throw is not defense-in-depth for the *same* request path;
it is speculative code for a caller that does not exist. `Page` has exactly one
caller today (confirmed in round 1).

That fact collided with a standing instruction governing the whole session:
*"Don't add error handling, fallbacks, or validation for scenarios that can't
happen... Only validate at system boundaries (user input, external APIs)."* `Page`
is not a boundary — it's one layer downstream of the boundary that already rejects
bad input. Adding constructor validation there is exactly the pattern that
instruction rules out, however defensible it looks under generic "value objects
should enforce their own invariants" DDD reasoning. The general instinct wasn't
wrong in the abstract; it lost to more specific, standing guidance.

**Reversed conclusion: validate only at the `@RequestParam` boundary.** Inside
`Page`, delete the clamp and replace it with nothing — plain field assignment, no
conditional logic, no throw. This is simplification (removing now-redundant logic),
not addition, and it sidesteps the error-shape question entirely for that layer
since no new exception type needs mapping.

Accepted, explicitly stated tradeoff: a hypothetical future caller constructing
`Page` directly outside `ArticlesApi` would now get silent pass-through of bad
values instead of clamping or rejection. Deferred until such a caller actually
exists, per the same anti-speculative-code principle.

Plan file was rewritten again: `Files to modify` item 2 changed from "throw
`IllegalArgumentException`" to "delete the conditional logic, unconditional field
assignment"; Risk 1 write-up replaced; verification steps trimmed (no `Page`-level
unit test needed, since there's no conditional logic left to test).

The user again rejected `ExitPlanMode`, this time via `/effort` command output
(effort level changed to `high` as a saved default) plus a new question.

---

## Round 3 — `/effort high`, new question: does `handleConstraintViolation` already
cover the new case correctly?

**Prompt:** "Work out exactly how `ConstraintViolationException` surfaces when
`@Min`/`@Max` fail on a `@RequestParam` versus `@Valid @RequestBody`, and whether
`CustomizeExceptionHandler.handleConstraintViolation` already covers it correctly."

This was Risk 3 in the original plan, previously left as "confirm during
verification, don't assume." High effort meant actually resolving it before
implementation rather than deferring it to a test run.

### Mechanism, worked out precisely

- **`@Valid @RequestBody Dto`** (used by every REST controller with a body today —
  confirmed by grep across `UsersApi`, `ArticleApi`, `ArticlesApi.createArticle`,
  `CommentsApi`, `CurrentUserApi`): Spring MVC's own argument-resolution machinery
  validates the deserialized body via `WebDataBinder`/`BindingResult` *before* the
  controller method runs, throwing Spring's own `MethodArgumentNotValidException` —
  never `javax.validation.ConstraintViolationException`. Handled by the overridden
  `handleMethodArgumentNotValid`, which reads `BindingResult.getFieldErrors()`
  directly — clean field names, no parsing.
- **`@Validated` class + `@Min`/`@Max` directly on a `@RequestParam` primitive**
  (the new pattern this plan adds): Spring Boot autoconfigures a
  `MethodValidationPostProcessor` because `spring-boot-starter-validation` is on the
  classpath. `@Validated` on the class makes Spring wrap it in an AOP proxy with a
  `MethodValidationInterceptor`; on every call it runs Hibernate Validator's
  `ExecutableValidator.validateParameters(...)`, throwing the raw
  `javax.validation.ConstraintViolationException` on failure (Spring Boot 2.6.3 /
  Spring Framework 5.3.15 does not wrap this — that only changes in Spring
  Framework 5.3.21+/6.x with `HandlerMethodValidationException`). Handled by
  `handleConstraintViolation`.

### Is it exercised today? — a genuine self-correction mid-investigation

**First check (wrong):** grepped test source for the literal string
`ConstraintViolationException` and `handleConstraintViolation`; got zero matches.
Concluded `handleConstraintViolation` was dead code for the REST API, since every
controller that calls into the two `@Validated` services (`ArticleCommandService`,
`UserService`) already validates the same object via `@Valid @RequestBody` first,
so the service-level `@Valid` should never actually throw.

**Correction (after a targeted subagent search):** that grep checked the wrong
thing — a test exercising this path doesn't need to *mention* the exception class,
it just asserts on the HTTP response. `CurrentUserApi.updateProfile`
(`src/main/java/io/spring/api/CurrentUserApi.java:40-49`) is a real counter-example:
it takes `@Valid @RequestBody UpdateUserParam updateUserParam`, then manually
constructs `new UpdateUserCommand(currentUser, updateUserParam)` — a *different*
object, never itself bound from the request body — and passes that into
`UserService.updateUser(@Valid UpdateUserCommand command)`. `UpdateUserCommand`'s
class-level `@UpdateUserConstraint` (a DB-backed duplicate-email/username check) is
therefore only ever validated via the service's AOP-triggered `@Valid` — a real,
currently-passing `ConstraintViolationException` path, covered by
`CurrentUserApiTest.should_get_error_if_email_exists_when_update_user_profile`
(asserts `errors.email[0]` == `"email already exist"`).

Net: `handleConstraintViolation` is *not* dead code — it's tested, but only against
the **3-segment** property-path shape (`updateUser.command.email` — method +
parameter + the property node the custom validator adds via
`addPropertyNode("email")`).

### The actual bug, once the right shape was checked

`getParam` (`CustomizeExceptionHandler.java`):
```java
private String getParam(String s) {
  String[] splits = s.split("\\.");
  if (splits.length == 1) {
    return s;
  } else {
    return String.join(".", Arrays.copyOfRange(splits, 2, splits.length));
  }
}
```
For the 3-segment shape this returns the correct trailing field name (`email`). But
a constraint declared *directly* on a primitive `@RequestParam` (no cascaded bean)
produces only a **2-segment** path — `methodName.paramName` (e.g.
`getArticles.offset` — one `MethodNode` + one `ParameterNode`, no further
`PropertyNode`). `splits.length == 1` doesn't match (length is 2), so it falls into
the `else` branch: `Arrays.copyOfRange(splits, 2, 2)` → empty array →
`String.join(".", [])` → `""`. Since `ErrorResourceSerializer` groups
`FieldErrorResource`s by `field` directly into the JSON key, the actual response for
`GET /articles?offset=-1` would be `{"errors": {"": ["must be greater than or equal
to 0"]}}` — correct status and envelope, but an **empty string as the field key**.
Two simultaneous violations (`offset` and `limit` both invalid) would even collapse
into that one `""` key, silently dropping a message.

**Fix required:**
```java
private String getParam(String s) {
  String[] splits = s.split("\\.");
  if (splits.length <= 2) {
    return splits[splits.length - 1];
  }
  return String.join(".", Arrays.copyOfRange(splits, 2, splits.length));
}
```
Verified this preserves both existing shapes (length 1 unchanged, length ≥3
unchanged) and fixes only the length-2 case, returning the parameter name
(`offset`/`limit`) instead of `""`.

This moved `CustomizeExceptionHandler.java` from "no change expected" to a required
file in the plan, and tightened the verification steps to assert the exact field key
(`errors.offset[0]`, not just `statusCode(422)`), since asserting only the status
code would have silently passed even with the broken `getParam`.

---

## Takeaways on reasoning depth itself

- **Medium effort** produced a plausible, defensible answer ("validate in both
  places") that was internally consistent but missed a mechanical fact (validation
  interceptor runs before the method body) that invalidated the premise for one of
  the two layers.
- **Ultrathink** caught that mechanical fact, and additionally caught that the
  "obviously correct" generic DDD instinct (value objects should self-validate)
  was in tension with a more specific, standing project/session instruction — and
  correctly deferred to the more specific one.
- **High effort on a fresh question** (not just re-asking the same one) surfaced a
  real, previously unknown bug (`getParam`'s empty-string field key) — but only
  after a wrong intermediate conclusion (grep-based "dead code" claim) was caught
  and corrected via a second, more targeted investigation. The self-correction
  itself is worth noting: the initial check answered "does any test *mention* this
  exception" rather than "does any test *exercise* this code path," and those are
  not the same question.
