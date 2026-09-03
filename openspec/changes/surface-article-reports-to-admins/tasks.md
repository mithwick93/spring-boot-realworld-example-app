## 1. Persistence

- [ ] 1.1 Add `findByReporterId(String userId)` to `ArticleReportRepository` and implement it in `MyBatisArticleReportRepository` / `ArticleReportMapper` (+ `.xml`); verify with a new repository test asserting it returns all reports for a given user across multiple articles, and an empty list for a user with none.

## 2. REST controller

- [ ] 2.1 Create `CurrentUserReportsApi` at `@RequestMapping(path = "/user/reports")`, `GET` only, `@AuthenticationPrincipal User user`, calling the new repository method; verify with a `@WebMvcTest` asserting the three spec scenarios (multiple reports, empty list, 401 unauthenticated).
- [ ] 2.2 Verify no `WebSecurityConfig` change is needed by confirming `/user/reports` is not matched by any existing `permitAll`/method-specific rule (already checked in design.md; re-confirm against the actual file at implementation time in case it has changed since).

## 3. Verify

- [ ] 3.1 Run the full suite (`./gradlew test`) and confirm no regressions against the 82/82 baseline from homework 2.5.
- [ ] 3.2 Confirm the existing `POST /articles/{slug}/report` response shape is byte-for-byte unchanged (no `id` field added) — the concrete check for the proposal's "no change to `article-report`" claim.
