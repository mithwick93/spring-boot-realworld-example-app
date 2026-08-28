# Plan: reject invalid pagination params on `GET /articles` and `GET /articles/feed`

**Task (the vague version, as given):** "Add input validation for an endpoint" — narrowed to: `ArticlesApi.getArticles` / `ArticlesApi.getFeed` accept `offset`/`limit` query params that are currently *silently coerced* rather than validated. `io.spring.application.Page`'s private setters clamp a negative `offset` back to `0` and clamp `limit` outside `(0, 100]` back into range, with no error and no signal to the caller. Decide whether out-of-range pagination input should instead be rejected with the repo's existing `422` validation-error shape, and implement it.

*(Originally written at `/effort medium` in a fresh session, before touching code. Revised twice after re-examination at `ultrathink` and `/effort high` — see `planning-depth-2026-08.md` for the full three-round transcript and reasoning. This version reflects the final, corrected conclusions.)*

## Files to modify

1. **`src/main/java/io/spring/api/ArticlesApi.java`**
   Both `getArticles` and `getFeed` currently take `@RequestParam(value = "offset", defaultValue = "0") int offset` and `@RequestParam(value = "limit", defaultValue = "20") int limit` directly. Add `@Min`/`@Max` constraints on these two params and mark the class `@Validated` (Spring's method-parameter validation, not `@Valid @RequestBody` — this repo doesn't yet use that flavor anywhere, so it's a new but consistent pattern).

2. **`src/main/java/io/spring/application/Page.java`**
   Delete the conditional clamp logic in `setOffset`/`setLimit` entirely — replace with unconditional field assignment. No throw, no re-validation here. *(Revised from the original draft, which proposed throwing `IllegalArgumentException` here as defense-in-depth. `ultrathink` caught that with `@Validated` + `@Min`/`@Max` on the controller, Spring's method-validation interceptor runs before `getArticles`/`getFeed` executes — so `new Page(...)` is never reached with invalid input on the only path that calls it. A `Page`-side check would be speculative code for a caller that doesn't exist — validation belongs at the boundary that already rejects it. Accepted tradeoff: a hypothetical future direct caller of `Page` would get silent bad-value pass-through, deferred until such a caller exists.)*

3. **`src/main/java/io/spring/api/exception/CustomizeExceptionHandler.java`** — **required change, not just a check.**
   `handleConstraintViolation`'s `getParam` helper assumes a `ConstraintViolationException` property path is either 1 segment (bare field) or ≥3 segments (`method.param.property`, the shape produced by cascaded `@Valid` on a bean, e.g. the existing `CurrentUserApi` duplicate-email path). A constraint on a primitive `@RequestParam` directly produces exactly **2** segments (`methodName.paramName`, e.g. `getArticles.offset`) — a case `getParam` doesn't handle, silently returning `""` as the field key instead of `offset`/`limit`. Two simultaneous violations would even collapse into one `""` key, dropping a message. Fix:
   ```java
   private String getParam(String s) {
     String[] splits = s.split("\\.");
     if (splits.length <= 2) {
       return splits[splits.length - 1];
     }
     return String.join(".", Arrays.copyOfRange(splits, 2, splits.length));
   }
   ```
   *(Found at `/effort high`, after an initial wrong pass concluded — via a grep that checked whether any test source **mentioned** `ConstraintViolationException`, rather than whether any test **exercised** that code path — that this handler was dead code. A targeted follow-up found `CurrentUserApiTest.should_get_error_if_email_exists_when_update_user_profile` does exercise it, just only at the 3-segment shape, which is why the 2-segment gap had gone unnoticed.)*

4. **`src/test/java/io/spring/api/ArticlesApiTest.java`**
   Add cases: negative `offset`, `limit = 0`, `limit > 100`, non-numeric `limit` (already 400s from Spring's type conversion, out of scope — confirm it's unaffected). Assert `422` + the `{"errors": {...}}` shape used elsewhere in this file's sibling tests (see `UsersApiTest` for the pattern), not the flat `{"message": ...}` shape used for auth failures.

## What's explicitly out of scope

- `ArticleReadService` (the only other consumer of `Page`) — behavior there is unchanged; it still receives a `Page` object, it just never receives one built from invalid input anymore.
- The GraphQL adapter — it doesn't use `Page`/offset-limit pagination at all (it's cursor-based, see `CursorPageParameter`/`CursorPager`), so this fix has no GraphQL-side equivalent and none is being added here.

## Risks

- **Risk 1 — resolved: validate only at the `@RequestParam` boundary, not in `Page` too.** Originally scoped as "both layers, for defense in depth." Rejected on reconsideration: `Page` is not itself a system boundary — it's one layer downstream of the controller, which already rejects bad input before `Page` is ever constructed on this path. Adding a second check there would be validating a scenario that can't happen given today's one caller — speculative code with no real caller to justify it. Revisit only if a second caller of `Page` is introduced.
- **Risk 2 — this is a behavior change, not just an addition.** Any existing client sending `limit=500` today gets `200` with 100 results back; after this change it gets `422`. That's a breaking API change for real callers, not just an internal cleanup. Confirm nothing in the RealWorld frontend fixtures (if any are exercised in this repo's tests) relies on the clamp-and-succeed behavior before shipping.
- **Risk 3 — resolved: the shape is `{"errors": {...}}`, but `getParam` has a bug that breaks it for this specific case.** `@Min`/`@Max` on a `@RequestParam` throws `javax.validation.ConstraintViolationException` (not Spring's `MethodArgumentNotValidException`, which only fires for `@Valid @RequestBody`), which `handleConstraintViolation` does catch and does return `422` + `{"errors": {...}}` for — same envelope as the rest of the API's field validation, good. But see the required `CustomizeExceptionHandler.java` fix above: without it, the field key comes back as `""` instead of `offset`/`limit`.

## How to verify success

1. `./gradlew test --tests "io.spring.api.ArticlesApiTest"` passes, including the new negative-offset / out-of-range-limit cases — and the assertions check the **exact field key** (`errors.offset[0]`, `errors.limit[0]`), not just `statusCode(422)`. Asserting only the status code would pass even with the unfixed `getParam` bug, since the envelope and status are already correct — only the key is wrong.
2. Manual check: `./gradlew bootRun`, then `curl "localhost:8080/articles?offset=-1"` and `curl "localhost:8080/articles?limit=500"` both return `422` with `{"errors": {"offset": [...]}}` / `{"errors": {"limit": [...]}}` — not an empty-string key, not the flat auth-failure shape.
3. `curl "localhost:8080/articles?limit=50"` (a valid value) still returns `200` with unchanged behavior — the fix rejects invalid input, it doesn't change valid-input handling.
4. Full suite (`./gradlew test`) still passes, including `CurrentUserApiTest.should_get_error_if_email_exists_when_update_user_profile` — confirms the `getParam` fix's `splits.length <= 2` branch doesn't disturb the existing 3-segment (`updateUser.command.email`) case that test already covers.

## Success criteria are testable, not aspirational

Every criterion above names an exact command and an exact expected status code / response shape — no "it works" checks.
