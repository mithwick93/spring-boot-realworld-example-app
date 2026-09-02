# Review Gate: before Task 2 (POST /articles/{slug}/report)

Run before executing task 2 (REST controller + error wiring) from `tasks-2026-08.md`, per 2.4's process.

## Step 1 — Spec and plan, side by side

Checked every acceptance criterion in `spec-2026-08.md` §2 against what task 2's own success criteria in `tasks-2026-08.md` actually named.

**Real gap found:** task 2's description said it covers "all seven acceptance criteria," but its "Done when" line only listed six — AC1, AC2, AC4, AC5, AC6, AC7. **AC3 (different users can each report the same article once) was missing.** Nothing about the implementation needs to change for this — `save()` already inserts one row per `(articleId, userId)` pair, so two different users each reporting the same article is the existing behavior, not a new code path — but an untested acceptance criterion is exactly the kind of thing that quietly falls off a checklist between planning and implementation, and there'd have been nothing catching it if `ArticleReportApiTest` had shipped without a test for it. Fixed: `tasks-2026-08.md`'s task 2 now explicitly lists AC3.

**Does the plan introduce anything the spec doesn't require?** No — the plan's file list and the SQLite-specific exception handling in task 1 are implementation detail that elaborates on the spec without contradicting or exceeding it.

## Step 2 — Self-critique: strongest objection a senior engineer would raise

Asked directly, and took the answer seriously rather than picking the easiest one to dismiss.

**The objection:** `MyBatisArticleReportRepository.save()` catches a DB-level constraint violation and rethrows a feature-specific exception (`DuplicateReportException`). Homework 2.2's signup fix did the same thing independently — its own bespoke `DataIntegrityViolationException` handler, in a different file, for a different feature. That's two hand-rolled instances of "catch a duplicate-key violation and translate it" already, after only two features. A senior engineer would ask: why isn't this a shared, reusable mechanism (a helper method, or a single place that knows how to recognize a SQLite constraint violation) instead of every future uniqueness constraint in this codebase growing its own copy of the same try/catch?

**This is a real, valid objection, not a strawman.** The pattern really has been duplicated twice now, and this repo already has a documented history (per `HARNESS.md` and multiple Week 1 homeworks) of the same class of bug — uniqueness races — showing up repeatedly across different features. A generalized mechanism would plausibly pay for itself.

**Response — accepted explicitly, not fixed:** not doing the generalization now. Refactoring 2.2's and 2.3's already-committed, already-tested code into a shared abstraction is more scope than task 2 ("wire the report endpoint to HTTP") calls for, and it would touch working code for a generalization neither current feature strictly needs yet. This is written down explicitly in `spec-2026-08.md`'s Constraints section as a deliberate deferral — the difference between this and silently letting it slide is that it's now named, in writing, as a real trade-off someone could pick up later, rather than an unnoticed gap.

## Step 3 — One assumption the spec doesn't justify

The plan decided *where* the duplicate-key translation happens (inside the repository implementation, not the application/command layer, not the controller). The spec never said this — it explicitly left "catch the DB constraint violation directly, or check-then-insert" as an **open question for implementation** (`spec-2026-08.md`, pre-2.4 wording). That's the assumption: the plan resolved a question the spec deliberately left open, without the spec ever being updated to reflect the resolution.

**Decision: amend the spec.** `spec-2026-08.md`'s Constraints section now documents what was actually decided and why (repository-layer translation, two catch blocks needed because of SQLite's exception-translation gap, confirmed by a real test failure — not assumed), plus the reusability trade-off from Step 2, explicitly accepted rather than fixed. The spec no longer has an open question sitting unresolved after the decision that answers it has already shipped.

## Evaluate

**What did the gate catch that wouldn't have been caught mid-implementation?** The AC3 test-coverage gap, most concretely — if task 2 had been implemented straight from the "Done when" line as written, `ArticleReportApiTest` would have shipped with six passing tests and nobody would have noticed the seventh was never written, since "all tests green" doesn't distinguish between "every acceptance criterion has a test" and "every test that exists happens to pass."

**Was this genuinely low-risk, or performative?** Not performative — both findings are real and neither was known before this pass. That said, the AC3 gap is genuinely low-severity (the behavior was already correct; only the test was missing), while the exception-translation-reusability question is a real but non-blocking architectural note. Calling both of these out honestly, rather than manufacturing a bigger issue to justify the gate, is itself the point.

**Where does this workflow not have gates today?** Task 1 itself had no equivalent gate before it was implemented and committed in 2.3 — the plan went straight from Plan to Implement with no deliberate pause. That happened to be fine (Risk 1 and Risk 2 were both named and both handled correctly), but that's partly luck of a well-specified task, not evidence gates aren't needed there. The highest-leverage place to add one: right after Plan, before *any* task is implemented, not just before task 2 — this homework's structure (gate before task 2 specifically) meant task 1 never got the same scrutiny, and task 1 was where the two real risks actually lived.
