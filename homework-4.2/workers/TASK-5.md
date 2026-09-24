# Worker Report: TASK-5

## Task

Scope: `src/main/java/io/spring/api/ArticleFavoriteApi.java`, `src/main/java/io/spring/api/ArticleReportApi.java`, `src/main/java/io/spring/api/ProfileApi.java`

Question: For these path-variable-only endpoints (favorite/unfavorite, report, follow/unfollow), is the absence of a request DTO an acceptable design (per the simple path-variable actions convention), and are the path variables safely handled?

## Findings

**DTO-less design is consistent with the documented convention, with one file that is only partially path-variable-only:**

- `ArticleFavoriteApi.favoriteArticle`/`unfavoriteArticle` (ArticleFavoriteApi.java:29-51) take only `@PathVariable("slug") String slug` plus `@AuthenticationPrincipal User user` — no request body, no DTO. This matches CLAUDE.md's "Simple path-variable actions (no request body) skip the DTO step entirely" convention, citing `ProfileApi.follow`/`unfollow` as the pattern.
- `ProfileApi.follow`/`unfollow` (ProfileApi.java:37-68) likewise take only `@PathVariable("username") String username` + the authenticated principal — this is the convention's canonical example, confirmed as-is.
- `ArticleReportApi.reportArticle` (ArticleReportApi.java:33-44) is **not** a pure path-variable action: alongside `@PathVariable("slug") String slug` it also takes `@Valid @RequestBody ReportArticleParam reportArticleParam` (ArticleReportApi.java:37, DTO defined at lines 63-77). This is correct and expected — the endpoint has a real payload (`reason`), so it follows the standard DTO+`@Valid` convention (`@NotBlank`, `@Size(max = 1000)` at lines 71-76), not the path-variable-only convention. It should not be confused with the favorite/follow style; it's the right convention for a different shape of endpoint.

**Path variable safety — SQL injection (CLAUDE.md > Security Requirements > Database Access):**

- `slug` in ArticleFavoriteApi.java:33/43 and ArticleReportApi.java:39 flows into `articleRepository.findBySlug(slug)`, which resolves to MyBatis mapper `ArticleMapper.xml:61-64`: `where A.slug = #{slug}` — parameter binding (`#{}`), not string concatenation. Safe per the "MyBatis parameter binding only" rule.
- `username` in ProfileApi.java:31/41/54 flows into `userRepository.findByUsername(username)` / `profileQueryService.findByUsername`, resolving to `UserMapper.xml:31-33`: `where username = #{username}` — also `#{}` binding. Safe.
- No manual concatenation or `${}` substitution found in either mapper.

**Path variable safety — input validation gap:**

- None of the three files impose any format/length constraint on the path variables themselves (`slug`, `username`). They are plain `String` path variables with no `@Pattern`/`@Size` and no bean-validation annotations (unsurprising, since `@PathVariable` isn't a `@RequestBody` DTO field and Bean Validation on path variables isn't wired up anywhere else in this codebase either).
- Practical exposure is low: all four affected repository calls (`findBySlug`, `findByUsername`) are read lookups guarded by `.orElseThrow(ResourceNotFoundException::new)` (ArticleFavoriteApi.java:33,43; ArticleReportApi.java:39; ProfileApi.java:34,48; ProfileApi.java:64/66 via the `Optional` branch). An oversized, malformed, or unexpected `slug`/`username` simply fails to match a row and produces a 404 — it cannot reach a state-changing operation (`save`, `saveRelation`, `removeRelation`, favorite `save`/`remove`) unless a genuine matching row exists first. There is no unbounded-length or resource-exhaustion concern beyond a single indexed-column equality lookup.
- This is arguably fine as *authorization/business logic* (CLAUDE.md's Input Validation section is worded around request bodies/query params/file contents, and 404-guarding is the existing, consistent pattern across the api package), but it is a literal gap relative to "treat ALL external input as untrusted" and "validate format... via Bean Validation" if read strictly — path variables receive no explicit format check anywhere in the api package, not just these three files.

## Recommendations

- No SQL-injection or state-mutation-without-authorization gap was found — all four repository lookups are parameter-bound and 404-guard before any write.
- No DTO gap: the favorite/unfollow endpoints correctly have no DTO (matching convention), and `ArticleReportApi` correctly does have one, because it carries an actual payload field (`reason`). Nothing here misapplies the convention.
- Optional hardening (low priority, repo-wide pattern rather than specific to these 3 files): add a `@Size(max = ...)` or `@Pattern` constraint on `slug`/`username` path variables (or a `@Validated` class-level annotation + method-parameter constraint) to reject obviously-malformed values before hitting the repository layer, rather than relying solely on the 404 fallback. This is a defense-in-depth suggestion, not a fix for an active vulnerability — flag for team discussion rather than treat as required before merge.
