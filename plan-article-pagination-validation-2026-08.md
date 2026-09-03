# Plan: reject invalid pagination params on `GET /articles` and `GET /articles/feed`

**Task (the vague version, as given):** "Add input validation for an endpoint" — narrowed to: `ArticlesApi.getArticles` / `ArticlesApi.getFeed` accept `offset`/`limit` query params that are currently *silently coerced* rather than validated. `io.spring.application.Page`'s private setters clamp a negative `offset` back to `0` and clamp `limit` outside `(0, 100]` back into range, with no error and no signal to the caller. Decide whether out-of-range pagination input should instead be rejected with the repo's existing `422` validation-error shape, and implement it.

*(Originally written at `/effort medium` in a fresh session, before touching code. Revised twice after re-examination at `ultrathink` and `/effort high` — see `planning-depth-2026-08.md` for the full three-round transcript and reasoning. This version reflects the final, corrected conclusions.)*

## Files to modify

1. **`src/main/java/io/spring/api/ArticlesApi.java`**
   Both `getArticles` and `getFeed` currently take `@RequestParam(value = "offset", defaultValue = "0") int offset` and `@RequestParam(value = "limit", defaultValue = "20") int limit` directly. Add `@Min`/`@Max` constraints on these two params and mark the class `@Validated` (Spring's method-parameter validation, not `@Valid @RequestBody` — this repo doesn't yet use that flavor anywhere, so it's a new but consistent pattern).

2. **`src/main/java/io/spring/application/Page.java`**
   Delete the conditional clamp logic in `setOffset`/`setLimit` entirely — replace with unconditional field assignment. No throw, no re-validation here. *(Revised from the original draft, which proposed throwing `IllegalArgumentException` here as defense-in-depth. `ultrathink` caught that with `@Validated` + `@Min`/`@Max` on the controller, Spring's method-validation interceptor runs before `getArticles`/`getFeed` executes — so `new Page(...)` is never reached with invalid input on the only path that calls it. A `Page`-side check would be speculative code for a caller that doesn't exist — validation belongs at the boundary that already rejects it. Accepted tradeoff: a hypothetical future direct caller of `Page` would get silent bad-value pass-through, deferred until such a caller exists.)*

3. **`src/main/java/io/spring/api/exception/CustomizeExceptionHandler.java`** — no change needed. The 2-segment property-path case (`methodName.paramName`, e.g. `getArticles.offset`) that this plan originally flagged as unhandled was already fixed in `582c3fa` ("fix: getParam mishandles 2-segment property paths (offset/limit)"), which predates this plan and is already on `HEAD`. Confirmed by reading the current file and `git log`, not the earlier grep-based pass that missed it.

4. **`src/test/java/io/spring/api/ArticlesApiTest.java`**
   Add cases: negative `offset`, `limit = 0`, `limit > 100`, non-numeric `limit` (already 400s from Spring's type conversion, out of scope — confirm it's unaffected). Assert `422` + the `{"errors": {...}}` shape used elsewhere in this file's sibling tests (see `UsersApiTest` for the pattern), not the flat `{"message": ...}` shape used for auth failures.

## What's explicitly out of scope

- `ArticleReadService` (the only other consumer of `Page`) — behavior there is unchanged; it still receives a `Page` object, it just never receives one built from invalid input anymore.
- The GraphQL adapter — it doesn't use `Page`/offset-limit pagination at all (it's cursor-based, see `CursorPageParameter`/`CursorPager`), so this fix has no GraphQL-side equivalent and none is being added here.

## Risks

- **Risk 1 — resolved: validate only at the `@RequestParam` boundary, not in `Page` too.** Originally scoped as "both layers, for defense in depth." Rejected on reconsideration: `Page` is not itself a system boundary — it's one layer downstream of the controller, which already rejects bad input before `Page` is ever constructed on this path. Adding a second check there would be validating a scenario that can't happen given today's one caller — speculative code with no real caller to justify it. Revisit only if a second caller of `Page` is introduced.
- **Risk 2 — this is a behavior change, not just an addition.** Any existing client sending `limit=500` today gets `200` with 100 results back; after this change it gets `422`. That's a breaking API change for real callers, not just an internal cleanup. Confirm nothing in the RealWorld frontend fixtures (if any are exercised in this repo's tests) relies on the clamp-and-succeed behavior before shipping.
- **Risk 3 — resolved, and already fixed upstream.** `@Min`/`@Max` on a `@RequestParam` throws `javax.validation.ConstraintViolationException` (not Spring's `MethodArgumentNotValidException`, which only fires for `@Valid @RequestBody`), which `handleConstraintViolation` does catch and does return `422` + `{"errors": {...}}` for — same envelope as the rest of the API's field validation. The 2-segment property path this produces (`getArticles.offset`) is already mapped to the correct field key by `getParam`, fixed in `582c3fa` prior to this plan. No `CustomizeExceptionHandler.java` change is needed — confirmed by reading the current file and `git log`, not the earlier grep-based pass that missed the fix.

## How to verify success

1. `./gradlew test --tests "io.spring.api.ArticlesApiTest"` passes, including the new negative-offset / out-of-range-limit cases — and the assertions check the **exact field key** (`errors.offset[0]`, `errors.limit[0]`), not just `statusCode(422)`. This guards against a regression in `getParam`'s 2-segment handling (fixed in `582c3fa`), even though no change to it is part of this task.
2. Manual check: `./gradlew bootRun`, then `curl "localhost:8080/articles?offset=-1"` and `curl "localhost:8080/articles?limit=500"` both return `422` with `{"errors": {"offset": [...]}}` / `{"errors": {"limit": [...]}}` — not an empty-string key, not the flat auth-failure shape.
3. `curl "localhost:8080/articles?limit=50"` (a valid value) still returns `200` with unchanged behavior — the fix rejects invalid input, it doesn't change valid-input handling.
4. Full suite (`./gradlew test`) still passes, including `CurrentUserApiTest.should_get_error_if_email_exists_when_update_user_profile` — confirms adding `@Validated` + `@Min`/`@Max` to `ArticlesApi` doesn't disturb other controllers' existing 3-segment (`updateUser.command.email`) `ConstraintViolationException` handling. `getParam` itself is untouched by this plan, so this step only verifies for regressions, not a fix.

## Success criteria are testable, not aspirational

Every criterion above names an exact command and an exact expected status code / response shape — no "it works" checks.
