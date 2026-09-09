# Modernization state — CursorPageParameter limit validation

**Pattern:** Branch-by-Abstraction (nearest analog — flagged as a stretch; none of the five macro-patterns are a clean fit at this scale, see rationale below)
**Started:** 2026-09-08 (Homework 3.4, structural refactor)
**Acceptance criterion (closed 2026-09-09):** `CursorPageParameter`'s non-positive-limit path has an explicit, tested, intentional behavior — not a silent implicit fallback to the default — enforced in `LimitClamp.resolve`, with the decision and rationale recorded in this file. Full test suite green with no regression from the 107/107 baseline (as of 3.4's `774e7cb`).
**Owner / on-call:** Mithila Wickramarathne

## Macro-pattern rationale (setup step 2)
Picked: **Branch-by-Abstraction**, explicitly flagged as a stretch. `LimitClamp` (introduced in 3.4) is the pattern's Phase 1 (Abstract): an abstraction point with the old, unchanged behavior sitting behind it, all callers already migrated to go through it. This capstone's iterations map to the pattern's later phase — deciding what happens to that preserved behavior now that the abstraction exists.

Rejected alternatives:
- **Strangler Fig** — no live-traffic routing boundary; this is a method call, not a request/route.
- **Anti-Corruption Layer** — no legacy/new semantic translation happening; old and new code speak the same model.
- **Expand/Contract** — no schema/API shape crossing independently-deployed clients; one JVM, one deploy.
- **Parallel Run** — deterministic pure function; unit tests fully characterize it, no need for statistical production-scale divergence detection.

## Engagement options (setup step 1)
- **Refactor (chosen)** — small, self-contained, callers stable, extraction already proven safe in PR #1.
- **Wrap** — rejected; no external system to shield against, just an internal validation gap.
- **Rewrite** — rejected; refactor cost (one class, ~10 lines) is nowhere near rebuild cost.
- **Delete** — rejected; the function is load-bearing across 8 call sites.
- **Tolerate** — rejected; 3.3's PAID grid already scored this Prioritize-quadrant (high severity) — deliberate non-engagement was ruled out at assessment time.

## Pathology inventory
- Asymmetric validation (originally a large-static-method-adjacent smell, now extracted): `CursorPageParameter.setLimit` — over-limit values are clamped to `MAX_LIMIT`, but zero/negative values were silently replaced by the default limit instead of being rejected or floor-clamped. Pre-3.4 location: `CursorPageParameter.java:33-39`. **Resolved in 3.5 iteration 1** (see below) — non-positive values now floor-clamp to 1.

## Technique catalogue applied so far
| Date | Technique | Target | Commit |
|---|---|---|---|
| 2026-09-08 | Characterization tests | `CursorPageParameterTest` (7 cases, incl. the known bug) | `00a1e34` |
| 2026-09-08 | Add new code in a new method (Extract Method) | `LimitClamp.resolve` (intermediate name `resolveLimit`, superseded) | `5059392` |
| 2026-09-08 | Isolate the dependency | `MAX_LIMIT` parameterized | `d72e767` |
| 2026-09-08 | Add new code in a new class (Extract Class) | `LimitClamp.resolve` | `774e7cb` |
| 2026-09-09 | Bug fix with existing test coverage (flip pinning test, then fix) | `LimitClamp.resolve` non-positive path, floor-clamped to 1 | *(pending commit)* |

## Standing prompt invariants for this codebase
- Lombok's `@Data` on `CursorPageParameter` will silently regenerate a public, unclamped `setLimit(int)` if the private method's exact name/signature disappears — any move touching this class must keep `setLimit`'s name, signature, and visibility unchanged throughout.
- MyBatis's OGNL expressions in `ArticleReadService.xml`/`CommentReadService.xml` reflect on getters by name — never rename a getter without grepping both XML files first.
- Boundary semantics (strict `>`, check order, the bug itself) must survive verbatim across any structural move, until an iteration explicitly targets changing them.

## Claude failure modes specific to this codebase
(none yet — populate as iterations catch a reviewer-check violation)

## Migration state
- **3.4 (structural):** closed. Clamp logic extracted to `LimitClamp`, callers migrated, bug preserved and flagged (not fixed) in a code comment. 107/107 suite green.
- **3.5 iteration 1:** closed. Non-positive limit values now floor-clamp to 1 in `LimitClamp.resolve`, symmetric with the existing over-limit clamp to `MAX_LIMIT` and matching REST's own `@Min(1)` floor convention on the sibling `ArticlesApi.java` path. The two characterization tests that pinned the old fallback-to-default behavior were deliberately re-purposed into specification tests (renamed, re-asserted) rather than left as stale regressions. 107/107 suite green — same total as the 3.4 baseline (2 tests renamed, none added/removed). Acceptance criterion **met**.
