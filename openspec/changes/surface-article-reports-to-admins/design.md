## Context

See `proposal.md` - Why/What Changes for motivation and the two scope-downs (self-visibility only, no `id` field). The one thing not yet decided is the exact route shape, since `article-report-visibility`'s spec deliberately left it unspecified (a spec describes behavior, not routes).

## Goals / Non-Goals

**Goals:**
- Decide the concrete route and controller placement for "list my own filed reports."
- Confirm no `WebSecurityConfig` change is needed (reuse the existing catch-all `anyRequest().authenticated()`).

**Non-Goals:**
- Anything already ruled out in the proposal (admin visibility, an `id` field, pagination/filtering).

## Decisions

**Route: `GET /user/reports`, not `/articles/{slug}/reports/mine`.** This repo already has a precedent for "the current authenticated user's own X" as a top-level resource: `CurrentUserApi` at `@RequestMapping(path = "/user")` (`GET /user` = my own profile, no path parameter, identity comes entirely from the JWT). A per-article route (`/articles/{slug}/reports/mine`) would be wrong here anyway — the spec's first scenario is explicitly "reports across all articles, one list," not one article at a time, so nesting under `/articles/{slug}` would force an awkward N-requests-per-article shape for something that's supposed to be one call. `/user/reports` matches the existing convention and the actual required shape.

Alternative considered: extend `CurrentUserApi` itself with a new `getReports()` method. Rejected — `CurrentUserApi` is scoped to the `User` aggregate (profile read/update); reports are a different aggregate (`ArticleReport`). A new, small `CurrentUserReportsApi` (mirroring how `ArticleReportApi` is already its own controller rather than a method bolted onto `ArticlesApi`) keeps that separation consistent with how this codebase already splits controllers by aggregate, not by URL prefix.

**No `WebSecurityConfig` change.** `/user/reports` falls under the existing `.anyRequest().authenticated()` catch-all (the same rule that already covers `POST /articles/{slug}/report` — see `spec-2026-08.md` Constraints). Confirmed by reading the config directly rather than assumed: the only path-specific rules are `GET /articles/feed`, `POST /users`/`/users/login`, and `GET /articles/**`/`/profiles/**`/`/tags` — none match `/user/reports`.

**Repository method: `findByReporterId(String userId)`, not a filtered version of the existing `find(articleId, userId)`.** The existing `ArticleReportRepository.find` is keyed by `(articleId, userId)` — it answers "did this user report this specific article," not "list every report this user has filed." A new method is a small, additive interface change, not a modification to existing behavior (consistent with the proposal's "no change to `article-report`" claim — this is a new read path over the same table, not a changed one).

## Risks / Trade-offs

- **Trade-off named, not hidden:** because there's no admin visibility, if a report needs moderator attention, the current answer is still "nobody sees it" for anyone other than the reporter themselves. This proposal makes that gap smaller (the reporter isn't shouting into a void) but does not close the original moderation gap — that's explicitly deferred to a future authorization-layer change, per the proposal.
- **Low risk on the query itself:** `findByReporterId` is a straightforward indexed lookup on the existing composite-PK table (`user_id` is half of the primary key, so it's already indexed) — no new index, no migration.

## Open Questions

None — the route, controller placement, and security-config impact were all resolved above rather than left open, since each would have changed the task breakdown if deferred.
