## Why

`spec-2026-08.md`'s peer review (see `peer-read-notes-2026-08.md`) flagged a real operational gap that shipped as an explicit non-goal: every row written to `article_report` is currently unreachable — no `GET`, no list, no admin view, not even a report `id` in the create response to correlate later. Reports exist only as a black hole a report-abuse workflow could never actually use. This proposal is the first real slice of closing that gap.

## What Changes

- New capability: a user can list the reports **they themselves** have filed, across all articles, via a new `GET /user/reports` endpoint (route decided in design.md — mirrors the existing `GET /user` "current user" convention rather than nesting under a single article's slug), reusing the existing JWT auth with no new permission model.
- **Scope correction from the originally-flagged gap, made explicitly rather than silently:** the peer review's language was "an admin-facing read path." This repo has **zero** admin/role/authorization concept anywhere in `src/main/java` (confirmed by search — no `Role`, no `admin`, nothing beyond per-user JWT identity and article-authorship checks in `AuthorizationService`). Building real admin visibility would mean designing and shipping a whole authorization layer this codebase doesn't have — that is a much larger, separately-scoped change, not a natural extension of the report feature. This proposal deliberately narrows to **self-visibility** (a user seeing their own filed reports) as the honest, shippable slice; admin-facing visibility stays an explicit non-goal here, named below, not quietly dropped.
- **A second scope-down, caught while drafting this proposal, not assumed upfront:** the peer review also suggested adding a report `id` for correlation. Not needed here — `article_report`'s existing composite primary key `(article_id, user_id)` already uniquely identifies a row, and a user listing *their own* reports already knows their own `user_id` implicitly (it's their JWT). So this proposal makes **no change to the existing `article-report` capability at all** — no new field, no modified response shape. It's a pure new-capability addition, reusing `article-report`'s existing data as-is.

## Capabilities

### New Capabilities
- `article-report-visibility`: a reporter can list the reports they have personally filed, reusing existing JWT auth, no new authorization model, no change to `article-report`'s existing data or response shapes.

### Modified Capabilities
(none — see the second scope-down above)

## Impact

- Affected code: `ArticleReportRepository`/`MyBatisArticleReportRepository`/mapper (add a find-by-reporter query, no schema change), `ArticleReportApi` (new GET route only — existing `POST` route and response are untouched).
- Not affected: no schema change to `articles`, `users`, or `article_report`; no new runtime dependency; no change to the existing duplicate-report enforcement (AC2/AC3), validation (AC6/AC7), or the `POST` response shape (AC1).
- Explicitly out of scope for this change (unchanged from `spec-2026-08.md` §5, still true after this proposal): any admin/moderator dashboard, resolution workflow, notifications, or rate limiting. Admin-facing visibility specifically is now a **named** future increment blocked on a real authorization layer, not an unnoticed gap.
