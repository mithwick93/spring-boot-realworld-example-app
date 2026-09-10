Acceptance criterion, set before any 3.5 code changes:

`CursorPageParameter`'s non-positive-limit path has an explicit, tested, intentional behavior — not a silent implicit fallback to the default — enforced in `LimitClamp.resolve`, with the decision and rationale recorded in `src/main/java/io/spring/application/CLAUDE.md`. Full test suite green with no regression from the 107/107 baseline (as of 3.4's `774e7cb`).

Status: met, as of iteration 1 (2026-09-09). Non-positive limit values now floor-clamp to 1 in `LimitClamp.resolve`; the two characterization tests that pinned the old fallback-to-default behavior were re-purposed into specification tests; 107/107 suite green, same total as the 3.4 baseline.
