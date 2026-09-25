# Worker Report: TASK-06

## Task
SCOPE: `src/main/java/io/spring/api/ArticleFavoriteApi.java`
QUESTION: This endpoint takes only path variables, no request DTO. Is that an acceptable design per CLAUDE.md's conventions, and are the path variables safely handled?

## Findings

**Design is acceptable.** `ArticleFavoriteApi.java:29-37` (`favoriteArticle`) and `:39-51` (`unfavoriteArticle`) both take only `@PathVariable("slug") String slug` plus `@AuthenticationPrincipal User user` — no request body/DTO. CLAUDE.md's Conventions section explicitly documents this pattern: "**Simple path-variable actions** (no request body) skip the DTO step entirely — see `ProfileApi.follow`/`unfollow`, which take only `@PathVariable` + `@AuthenticationPrincipal`." `ArticleFavoriteApi` follows the exact same shape, so it's consistent with an established, sanctioned convention rather than a gap.

**Path variable handling is safe against injection**, per CLAUDE.md's Security Requirements > Database Access ("Use MyBatis parameter binding (`#{param}`) only — never `${param}` string substitution or manual concatenation"):
- `slug` flows from `ArticleFavoriteApi.java:33` / `:43` into `articleRepository.findBySlug(slug)` → `MyBatisArticleRepository.findBySlug` (`infrastructure/repository/MyBatisArticleRepository.java:49-50`) → `ArticleMapper.findBySlug(@Param("slug") String slug)` (`infrastructure/mybatis/mapper/ArticleMapper.java:20`) → SQL `where A.slug = #{slug}` (`src/main/resources/mapper/ArticleMapper.xml:63`).
- The read side (`articleQueryService.findBySlug(slug, user)` at `ArticleFavoriteApi.java:36`/`:50`) similarly binds via `#{slug}` in `src/main/resources/mapper/ArticleReadService.xml:45`.
- Both mapper XMLs use `#{slug}` (bound parameter), not `${slug}` (raw string substitution) — no SQL injection risk.

**Gap: no input validation / bounds on `slug` before it reaches the query.** CLAUDE.md's Security Requirements > Input Validation says: "Treat ALL external input as untrusted (request bodies, query params, file contents)" and "Validate format and required fields via Bean Validation... add `@Size` bounds on any new user-supplied string field." The `slug` path variable at `ArticleFavoriteApi.java:31` and `:41` has no `@Size`/`@Pattern` constraint and no format check — an attacker can submit an arbitrarily long or malformed string as `slug`. This isn't an injection risk (parameter binding protects against that), but it is a deviation from the stated Input Validation requirement, which calls for validating format/bounds on "ANY" external input, not just body fields.
  - Practical impact is low: `findBySlug` simply returns empty/`ResourceNotFoundException` for a non-matching slug (`ArticleFavoriteApi.java:33`, `:43`), so a bad slug fails closed rather than causing incorrect behavior. There's no unbounded-resource or crash risk visible in this file.

## Recommendations

- No change strictly required — the missing-DTO design matches the documented `ProfileApi.follow`/`unfollow` convention, and SQL parameter binding is correctly used throughout the slug lookup path (mapper/SQL injection is not a concern).
- Optional hardening (low priority, consistent with Input Validation section): since `@PathVariable` values bypass Bean Validation annotations unless the controller class is also annotated `@Validated` and the parameter carries a constraint (e.g. `@Size(max = ...)` or `@Pattern`), consider adding such a constraint to `slug` if there's ever concern about abuse via extremely long path segments. Given `findBySlug` already fails safely (404 via `ResourceNotFoundException`), this is a defense-in-depth suggestion, not a required fix.
