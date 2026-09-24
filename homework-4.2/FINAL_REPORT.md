# Input Validation Audit — Final Report

Synthesis of 6 worker reports (TASK-1 through TASK-6) covering every REST controller in `src/main/java/io/spring/api/`, against the Input Validation rules in `CLAUDE.md`.

## 1. Executive Summary

- **No active vulnerabilities found.** No SQL injection, no unauthenticated write paths, no missing authorization checks. Every mapper uses MyBatis `#{}` binding, and every path-variable lookup either 404s on a miss or is additionally authorization-checked before a write.
- **The one real functional gap**: `ArticleApi.updateArticle` (`PUT /articles/{slug}`) has **no `@Valid` and no annotations at all** on `UpdateArticleParam` — it will silently accept an empty-string title/body/description or an arbitrarily large one. This is the only place where "invalid" input isn't rejected at all, as opposed to just being unbounded in length.
- **A recurring pattern, not a one-off**: missing `@Size` upper bounds on user-supplied strings appears in 5 of 6 tasks (`LoginParam.password`, `RegisterParam.email/username/password`, `UpdateUserParam`'s four fields, `NewArticleParam.title/description/body`, `tag`/`author`/`favorited` query params, comment/article path variables). This is consistent enough to treat as one repo-wide hardening item rather than six separate bugs.
- **Path-variable-only endpoints are correctly designed.** `ArticleFavoriteApi`, `ProfileApi` follow/unfollow, and the DTO on `ArticleReportApi` all match the documented convention with no misapplication.
- **Two files have zero external input and need no changes**: `TagsApi` and `CurrentUserReportsApi` (workers confirmed no query params, path variables, or body — only the trusted, already-authenticated principal).

## 2. Key Findings

### 2a. Real gaps found

| # | Location | Gap | Severity |
|---|---|---|---|
| G1 | `ArticleApi.java:47`, `UpdateArticleParam.java:12-17` | `updateArticle` request has no `@Valid` on the controller and zero Bean Validation annotations on the DTO — empty or arbitrarily large title/body/description are accepted and persisted. | **MEDIUM** |
| G2 | `UpdateUserParam.java:21-24` | `password`, `username`, `bio`, `image` have **no validation annotations at all** (not even `@Size`); `email` only rejects malformed non-empty values (empty string passes by design, for partial-update semantics). | **LOW-MEDIUM** |
| G3 | `UsersApi.java:77-78` (`LoginParam.password`) and `RegisterParam.java:20-25` (`email`, `username`, `password`) | `@NotBlank`/`@Email` present, but **no `@Size` upper bound** on any of these — unbounded-length strings reach `passwordEncoder`/persistence. | LOW |
| G4 | `NewArticleParam.java:17,21,24,27` | `title`/`description`/`body` have `@NotBlank` but no `@Size` upper bound; `tagList` has no validation at all (no size/element-length constraint). | LOW |
| G5 | `ArticlesApi.java:56-58` | `tag`, `favoritedBy`, `author` query params have no `@Size` bound (unlike `offset`/`limit`, which are correctly bounded with `@Min`/`@Max`). | LOW |
| G6 | `CommentsApi.java` (slug at 42/55/69, `commentId` at 70), `ArticleFavoriteApi`/`ArticleReportApi`/`ProfileApi` path variables | `slug`, `commentId`, `username` path variables have no `@Size`/`@Pattern` constraint anywhere in the `api` package. | LOW (defense-in-depth only) |

### 2b. Checked and clean

- **SQL injection**: confirmed absent. Every repository call examined (`findBySlug`, `findByUsername`, `findByReporterId`, `findById`) resolves to MyBatis `#{param}` binding in mapper XML — no `${}` or string concatenation anywhere in scope (TASK-5).
- **Authorization before mutation**: `deleteComment` checks `AuthorizationService.canWriteComment` before removal (TASK-4); favorite/unfavorite and follow/unfollow require `@AuthenticationPrincipal` (TASK-5).
- **Comment body validation**: `NewCommentParam.body` correctly has `@NotBlank` + `@Valid` wired at the controller (TASK-4).
- **Article creation required-field validation**: `NewArticleParam.title/description/body` all have `@NotBlank`, and `title` additionally has `@DuplicatedArticleConstraint` (TASK-3).
- **List/feed pagination bounds**: `offset` (`@Min(0)`) and `limit` (`@Min(1) @Max(100)`) on `ArticlesApi` are correctly bounded and enforced via class-level `@Validated` (TASK-3).
- **DTO-vs-path-variable convention**: `ArticleFavoriteApi`, `ProfileApi.follow/unfollow` correctly skip a DTO (no body); `ArticleReportApi.reportArticle` correctly *does* have one (`ReportArticleParam` with `@NotBlank` + `@Size(max = 1000)` on `reason`) because it carries a real payload. No misapplication of the convention found (TASK-5).
- **No external input surface**: `TagsApi.getTags()` takes zero parameters; `CurrentUserReportsApi.myReports()` takes only the authenticated principal, not attacker-controlled data (TASK-6).

### Where workers agreed
All six reports independently converged on the same root cause for most findings: **`@Size` upper bounds are inconsistently applied** — present on newer/report-specific fields (`ReportArticleParam.reason`) but absent almost everywhere else. All workers also agreed that unvalidated path variables are low-risk specifically *because* every lookup 404s (or is additionally auth-checked) before any state change — none found a path variable reaching a write without a prior existence/ownership check.

### Conflict resolution
No direct contradictions between reports. One item needed resolution: TASK-1 flagged `RegisterParam`'s own field-level validation as unverifiable because the class lives outside `UsersApi.java` (its assigned scope). I read `RegisterParam.java` directly to close this: it has `@NotBlank`/`@Email`/`@DuplicatedEmailConstraint`/`@DuplicatedUsernameConstraint` on `email`/`username` and `@NotBlank` on `password` — required-field validation is present, but (consistent with G3 above) **no `@Size` bound exists on any of the three fields**.

## 3. Priority Recommendations

1. **Add `@Valid` to `ArticleApi.updateArticle`'s `@RequestBody UpdateArticleParam` parameter** (`ArticleApi.java:47`), and add `@NotBlank` (or explicit "blank means unchanged" semantics matching the partial-update pattern) plus `@Size` bounds to `title`/`body`/`description` in `UpdateArticleParam.java:12-17`. This is the only finding that lets genuinely invalid input (empty strings) through untouched — fix first.
2. **Add `@Size` bounds to `UpdateUserParam.java:21-24`** (`password`, `username`, `bio`, `image`) and document/confirm the "empty string = unchanged" convention already used by `email` (line 18-19) so partial-update semantics are consistent and intentional across all five fields.
3. **Add `@Size(max = ...)` to `LoginParam.password`** (`UsersApi.java:77-78`) and to `RegisterParam.email/username/password` (`RegisterParam.java:15-25`) — same unbounded-string pattern as #2, applied to the auth entry points.
4. **Add `@Size(max = ...)` to `NewArticleParam.title/description/body`** (`NewArticleParam.java:17,21,24`) and a size/element-length constraint to `tagList` (line 27).
5. **Add `@Size(max = ...)` to the `tag`, `favorited`, `author` query params** in `ArticlesApi.java:56-58`, matching the pattern already used for `offset`/`limit`.
6. **Optional, lowest priority**: add `@Size`/`@Pattern` constraints to `slug`, `commentId`, and `username` path variables across `CommentsApi.java`, `ArticleFavoriteApi.java`, `ArticleReportApi.java`, and `ProfileApi.java`. Treat as a defense-in-depth discussion item, not a merge blocker — every current usage already 404s or auth-checks before any write.

## 4. Risk Assessment

- **HIGH:** None found.
- **MEDIUM:**
  - `ArticleApi.updateArticle` accepting fully unvalidated body content (Recommendation #1) — the one case where the app deviates from its own "reject invalid input" rule outright, rather than just under-bounding valid input.
- **LOW:**
  - `UpdateUserParam` missing `@Size` on four fields (Recommendation #2).
  - Missing `@Size` on `LoginParam`/`RegisterParam` string fields (Recommendation #3).
  - Missing `@Size` on `NewArticleParam` fields and `tagList` (Recommendation #4).
  - Missing `@Size` on `tag`/`author`/`favorited` query params (Recommendation #5).
  - Unvalidated path variables (Recommendation #6) — mitigated by existing 404/authorization guards; effectively cosmetic hardening.
- **Not a risk (confirmed clean):** SQL injection surface, DTO-vs-path-variable convention adherence, comment/article required-field validation, pagination bounds, `TagsApi`/`CurrentUserReportsApi` input surface.

## 5. Suggested Next Steps

1. Fix Recommendation #1 first (functional gap) and write a regression test asserting `PUT /articles/{slug}` with an empty `title` returns a 422/400, per this repo's Test Plan convention (`should_<outcome>_<condition>` naming, positive/negative/boundary cases).
2. Batch Recommendations #2–#5 into a single "add `@Size` bounds to user-supplied strings" pass, since they're the same fix pattern repeated across files — pick one consistent max length policy (e.g. username/email ~100-255 chars, article title ~255, body/description larger, matching whatever the DB column widths already imply) rather than picking arbitrary numbers per field.
3. Discuss Recommendation #6 with the team as a style/defense-in-depth decision rather than implementing unilaterally — it changes behavior on malformed input from "404" to "400," which may or may not be desired by API consumers.
4. Re-run the full suite (`./gradlew test`) after each batch of changes per CLAUDE.md's Test Plan exit criteria, and confirm `CustomizeExceptionHandler`/`GraphQLCustomizeExceptionHandler` field-name mapping still produces correct `propertyPath` segments for any newly added constraints (per the documented cross-parameter-validator pitfall).
