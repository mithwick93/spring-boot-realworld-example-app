# Input Validation Audit — REST Controllers (`src/main/java/io/spring/api/`)

Scope: all 10 controllers in `src/main/java/io/spring/api/`, evaluated against this repo's
`CLAUDE.md` → Security Requirements → Input Validation section.

## 1. How input validation is implemented in this codebase

- **Request bodies**: DTOs annotated with `javax.validation` constraints (`@NotBlank`,
  `@Email`, `@Size`), triggered by `@Valid @RequestBody` on the controller method parameter.
- **Query parameters**: bounded directly on the controller method parameter with `@Min`/`@Max`,
  which requires the controller class to carry `@Validated` (only `ArticlesApi` needs this today).
- **Path variables**: never annotated with Bean Validation. Instead they're used as opaque
  lookup keys against a repository; a missing record throws `ResourceNotFoundException` → 404.
  This is a documented, intentional convention (see `CLAUDE.md` "Simple path-variable actions"),
  not a gap.
- **Business-rule / duplicate-key validation** (email exists, username exists, article slug
  exists): implemented as custom `ConstraintValidator`s (`DuplicatedEmailConstraint`,
  `DuplicatedUsernameConstraint`, `DuplicatedArticleConstraint`, `UpdateUserConstraint`), wired
  either onto DTO fields or, for cross-parameter checks, onto `@Validated` service methods
  (`ArticleCommandService.updateArticle`, `UserService.updateUser`) rather than the controller.
- **SQL injection defense-in-depth**: confirmed via grep that no MyBatis mapper XML under
  `src/main/resources/mapper/` uses `${}` string substitution — all binding is via `#{}`.
- **Error shape**: field-validation failures return `{"errors": {"field": ["msg"]}}`; auth-type
  exceptions return a flat `{"message": "..."}"` (per `CustomizeExceptionHandler`) — consistent
  with the documented pitfall, not re-litigated here.

## 2. Findings by controller

### `UsersApi.java` — clean (core validation), minor systemic gap
- `POST /users` (`UsersApi.java:40`): `@Valid @RequestBody RegisterParam` — email
  blank+format+duplicate (`RegisterParam.java:16-18`), username blank+duplicate (`:20-22`),
  password blank (`:24-25`). ✅ clean.
- `POST /users/login` (`UsersApi.java:48`): `@Valid @RequestBody LoginParam` — email
  blank+format (`UsersApi.java:73-75`), password blank (`:77-78`). ✅ clean.
- Gap: `RegisterParam` has no `@Size` upper bound on email/username/password
  (`RegisterParam.java:15-25`). See systemic finding below.

### `CurrentUserApi.java` (`/user`) — clean
- `GET /user` (`CurrentUserApi.java:31-38`): no validated external input; the `Authorization`
  header is only consumed after `JwtTokenFilter` has already required and parsed a well-formed
  `Scheme token` header for the request to reach an authenticated principal at all
  (`JwtTokenFilter.java:50-61`), so the controller's `authorization.split(" ")[1]` cannot see a
  malformed value in practice. Not a gap.
- `PUT /user` (`CurrentUserApi.java:40-44`): `@Valid @RequestBody UpdateUserParam` — email
  format checked (`UpdateUserParam.java:18`); duplicate email/username re-checked at the service
  layer via `UpdateUserConstraint` on `UpdateUserCommand` (`UserService.java:47`, `:60-107`).
  ✅ clean core validation.
- Gap: `UpdateUserParam` has no `@Size` on username/password/bio/image (`UpdateUserParam.java`).
  Systemic, see below.

### `ArticlesApi.java` (`/articles`) — clean, best example of query-param validation
- `POST /articles` (`ArticlesApi.java:33`): `@Valid @RequestBody NewArticleParam` — `@NotBlank`
  on title/description/body, `@DuplicatedArticleConstraint` on title
  (`NewArticleParam.java:17-25`). ✅ clean.
- `GET /articles/feed` and `GET /articles` (`ArticlesApi.java:46-47`, `:54-55`): `offset`/`limit`
  bounded with `@Min(0)`/`@Min(1)`/`@Max(100)`, class-level `@Validated` (`:27`) makes this
  effective. ✅ clean — prevents negative offset and unbounded page size.
- Minor gap: `tag`/`favorited`/`author` query params (`ArticlesApi.java:56-58`) are plain
  `String` with no `@Size` bound. No injection risk (parameterized MyBatis, confirmed no `${}`
  usage), low severity — see Recommendation 3.

### `ArticleApi.java` (`/articles/{slug}`) — real gap
- `GET`/`DELETE` (`ArticleApi.java:34-41`, `:64-78`): `slug` is an opaque path-variable lookup
  key; 404 on miss. ✅ clean, consistent with documented convention.
- `PUT /articles/{slug}` (`ArticleApi.java:43-47`): the `@RequestBody UpdateArticleParam`
  parameter is **missing `@Valid`** — every other `@RequestBody` DTO in this codebase
  (`RegisterParam`, `LoginParam`, `NewArticleParam`, `NewCommentParam`, `UpdateUserParam`,
  `ReportArticleParam`) is annotated `@Valid`; this one is not. Compounding this,
  `UpdateArticleParam` itself carries zero field-level Bean Validation constraints —
  title/description/body have no `@Size` (`UpdateArticleParam.java:13-16`). Net effect today:
  no functional difference (there's nothing to trigger), but the missing `@Valid` means any
  future constraint added to `UpdateArticleParam` (e.g. the `@Size` fix in Recommendation 2)
  would silently do nothing unless this is also fixed.
  - Note: the duplicate-slug-on-rename check is **not** affected by this gap — it's enforced
    independently via the method-level `@DuplicatedArticleConstraint` on the `@Validated`
    `ArticleCommandService.updateArticle` (`ArticleCommandService.java:31-32`).

### `CommentsApi.java` (`/articles/{slug}/comments`) — clean, minor systemic gap
- `POST` (`CommentsApi.java:40-44`): `@Valid @RequestBody NewCommentParam` — `@NotBlank` on
  body (`CommentsApi.java:100-101`). ✅ clean core validation.
- `GET`/`DELETE` (`CommentsApi.java:53-55`, `:67-71`): `slug`/`id` are opaque path-variable
  lookup keys, 404 on miss. ✅ clean.
- Gap: `NewCommentParam.body` has no `@Size` cap. Systemic, see below.

### `ArticleFavoriteApi.java` (`/articles/{slug}/favorite`) — clean
- `POST`/`DELETE` (`ArticleFavoriteApi.java:29-51`): only a path-variable `slug`, opaque lookup
  key, 404 on miss. No request body or query params. No gap.

### `ProfileApi.java` (`/profiles/{username}`) — clean
- `GET`/`POST follow`/`DELETE unfollow` (`ProfileApi.java:28-68`): only a path-variable
  `username`, opaque lookup key, 404 on miss. No gap.

### `TagsApi.java` (`/tags`) — clean
- `GET /tags` (`TagsApi.java:17-18`): no external input at all. No gap.

### `ArticleReportApi.java` (`/articles/{slug}/report`) — clean, best example in the codebase
- `POST` (`ArticleReportApi.java:33-37`): `@Valid @RequestBody ReportArticleParam` —
  `@NotBlank` **and** `@Size(max = 1000)` on `reason` (`ArticleReportApi.java:71-76`). This is
  the only DTO in the codebase with an explicit upper bound and is exactly the pattern
  `CLAUDE.md`'s Security Requirements ask for elsewhere. `slug` path variable is an opaque
  lookup key, 404 on miss. ✅ clean.

### `CurrentUserReportsApi.java` (`/user/reports`) — clean
- `GET` (`CurrentUserReportsApi.java:28-29`): no external input besides
  `@AuthenticationPrincipal`. No gap.

### Systemic finding: missing `@Size` upper bounds
Across the codebase, only `ArticleReportApi`'s `ReportArticleParam.reason` has an upper bound.
Every other persisted, user-supplied string field has none:
`NewArticleParam`/`UpdateArticleParam` title/description/body, `NewCommentParam.body`,
`RegisterParam`/`UpdateUserParam` username/password/bio/image/email. This matters concretely
here because the schema declares `varchar(255)`/`varchar(511)` columns
(`db/migration/V1__create_tables.sql:2-16`) but **SQLite ignores `varchar(n)` length
declarations entirely** (type affinity only) — so there is currently no enforcement point,
DB or application, stopping an authenticated user from persisting an arbitrarily large string
into any of these fields.

## 3. Priority recommendations

1. Add `@Valid` to `ArticleApi.updateArticle`'s `UpdateArticleParam` parameter
   (`ArticleApi.java:47`) for consistency with every other write endpoint in the codebase, so
   any constraint added to `UpdateArticleParam` is actually enforced by Spring.
2. Add `@Size` upper bounds to the currently-unbounded, persisted, user-supplied string fields:
   `NewArticleParam`/`UpdateArticleParam` title/description/body, `NewCommentParam.body`,
   `RegisterParam` username/password, `UpdateUserParam` username/password/bio/image. Copy the
   existing in-repo pattern from `ReportArticleParam.reason` (`@Size(max = 1000)`, relying on
   Bean Validation's default message). This is the one concrete, actionable gap from this audit.
3. (Low priority, optional) Add a reasonable `@Size` bound to the free-text `tag`/`author`/
   `favorited` query parameters on `GET /articles` (`ArticlesApi.java:56-58`). Low risk today
   since MyBatis binds them as parameters (confirmed no `${}` substitution anywhere in
   `src/main/resources/mapper/`) — this is cosmetic hardening, not a fix for an exploitable gap.

## 4. Risk assessment: **LOW–MEDIUM**

- No SQL injection, XSS, or authentication/authorization-bypass gaps were found. Every mapper
  uses `#{}` parameter binding; every state-changing endpoint validates required fields for
  blank/format; duplicate-key business invariants (email, username, article slug) are enforced
  through dedicated validators wired at the correct layer (including the cross-parameter
  update-time slug check, which uses the exact `addParameterNode`/`addPropertyNode` pattern
  `CLAUDE.md` documents to avoid the `<cross-parameter>` field-name pitfall).
- The two concrete issues found — missing `@Valid` on `ArticleApi.updateArticle`, and the
  absence of `@Size` bounds on several string fields — are real gaps but low-impact on their
  own: they let an already-authenticated user write oversized (not wrong-type, not
  injection-bearing) data into their own or a shared resource's fields. There is no path from
  either gap to privilege escalation, data exfiltration, account takeover, or code execution.
  I'd rate the combined impact **MEDIUM** at most (storage bloat / large-payload-per-request
  resource use), not HIGH — and only if left unaddressed at scale, since these are all
  authenticated write paths, not the public registration/login endpoints (which are already the
  most rigorously validated DTOs in the codebase).
