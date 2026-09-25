# Worker Report: TASK-06

## Task
Scope: `src/main/java/io/spring/api/ArticleFavoriteApi.java`
Question: This endpoint takes only path variables, no request DTO. Is that an acceptable design per CLAUDE.md's conventions, and are the path variables safely handled?

## Findings

**Design convention — acceptable.**
`ArticleFavoriteApi.favoriteArticle` (`ArticleFavoriteApi.java:30-31`) and `unfavoriteArticle` (`ArticleFavoriteApi.java:40-41`) each take only `@PathVariable("slug") String slug` plus `@AuthenticationPrincipal User user` — no request body at all. CLAUDE.md's Conventions section explicitly documents this as a sanctioned pattern: "Simple path-variable actions (no request body) skip the DTO step entirely — see `ProfileApi.follow`/`unfollow`, which take only `@PathVariable` + `@AuthenticationPrincipal`." This endpoint follows the identical shape (favorite/unfavorite mirrors follow/unfollow), so no DTO is required and none is missing.

**Path variable handling — safe, with one gap.**
- The `slug` path variable is passed straight into `articleRepository.findBySlug(slug)` (`ArticleFavoriteApi.java:33`, `:43`), which resolves to `ArticleMapper.findBySlug` (`src/main/java/io/spring/infrastructure/mybatis/mapper/ArticleMapper.java:20`, using `@Param("slug")`) and the SQL in `src/main/resources/mapper/ArticleMapper.xml:61-64`: `where A.slug = #{slug}`. This uses MyBatis `#{}` parameter binding, not `${}` string substitution or manual concatenation — this satisfies CLAUDE.md's Database Access requirement ("Use MyBatis parameter binding (`#{param}`) only").
- If the slug does not resolve to an article, both handlers correctly reject with `ResourceNotFoundException` (`ArticleFavoriteApi.java:33`, `:43`) rather than proceeding with a null/invalid article — this satisfies the Security Requirements > Input Validation principle "Reject invalid input; do not attempt to sanitize and continue."
- Gap: unlike typical `@RequestBody` DTO fields, `slug` has no Bean Validation constraint (e.g. `@NotBlank`, `@Size`) at the controller boundary. Because `slug` is a URL path segment, an empty value isn't reachable through this route pattern (`articles/{slug}/favorite` requires a non-empty segment to match), so the missing `@NotBlank` doesn't create a distinct failure mode beyond the existing "slug not found → 404" path. There is no `@Size` upper bound, so an arbitrarily long path segment is passed to the `#{slug}` bound query — not an injection risk (still parameterized), but an unbounded-input path that CLAUDE.md's "add `@Size` bounds on any new user-supplied string field" guidance would otherwise flag if this were a body field.

## Recommendations

No functional or security gap requiring a code change was found — the endpoint conforms to the documented path-variable convention and the SQL is properly parameter-bound.

Optional, low-priority hardening (not required by any Test Plan or Security Requirement item, since the current behavior is already safe): if the team wants defense-in-depth consistency with body-field validation elsewhere, a `@Size(max = ...)` check on `slug` could be added before the repository lookup to reject abnormally long values early rather than letting them reach the DB query — but this is a robustness nicety, not a fix for an actual defect.
