# Workflow Breaks — 2026-08 (revised 2026-09-03)

**Supersedes an earlier version of this file.** The original draft applied `WORKFLOW.md` to
homework 2.6's `surface-article-reports-to-admins` OpenSpec change (`GET /user/reports`) —
real, tested code, but built autonomously in a single session before the collaboration model
below was agreed on. That feature shipped separately (`ff89c1f`) and is fine as-is, but it
doesn't demonstrate the rule set honestly, because there was no second party checking the work.
This version replaces it with a real run under the actual intended model: implementation
happens in the user's own Claude Code terminal; a separate session (this one) gives exact
prompts and independently verifies every claim against the real repo state — reading actual
`git diff`/`git log`/`git show` output, not trusting pasted summaries.

**The real task:** reject invalid `offset`/`limit` query params on `GET /articles` and
`GET /articles/feed`, per the pre-existing `plan-article-pagination-validation-2026-08.md`
(spec-anchored intensity, Manual tool — both chosen back in 2.2's `intensity-choices-2026-08.md`
and correctly *not* re-litigated here, per `WORKFLOW.md` rule 5's default).

## Where the rules held

**Tool choice (§5) — Manual/spec-anchored was correctly kept, not upgraded.** This is a
brownfield, 3-file, already-planned delta. Reaching for OpenSpec ceremony here (as 2.6 did for
a genuinely new capability) would have been overhead with no payoff — the existing plan doc was
already a sufficient anchor. Confirms the tool-choice rule works both ways: it's not just about
picking a heavier tool when warranted, but staying with Manual when a heavier tool wouldn't add
anything.

**Spec-template schema-check rule — caught a real stale claim, not a hypothetical one.** The
plan's item 3 claimed `CustomizeExceptionHandler.getParam` needed a fix for 2-segment property
paths. That fix had already shipped in `582c3fa`, before this plan was even finalized. Caught by
actually re-reading the current file and `git log` (not trusting the earlier grep-based pass that
missed it), confirmed independently in this session via `git merge-base --is-ancestor 582c3fa
HEAD` and a direct `grep` of the live exception handler. The plan was amended — item 3 dropped,
items 1 and 4 rescoped from "fix a bug" to "guard against a regression" — rather than the stale
assumption being carried into implementation.

**Iteration discipline (§4) — held properly this time, unlike the superseded draft.** The three
files that needed changing (`ArticlesApi.java`, `Page.java`, `ArticlesApiTest.java`) aren't
independently verifiable — annotating the controller without removing `Page`'s clamp leaves dead
code active; removing the clamp without the annotations breaks pagination outright. Recognizing
this as **one** atomic task (not artificially splitting it to hit a two-commits-per-plan-item
quota) was the correct call, and it was verified as a whole: baseline 87/87 before, 91/91 after
(90/90 mid-flight before the symmetry test was added), one commit (`56555b8`). Contrast with the
superseded draft, which broke this same rule by implementing two real tasks together without
verifying the first in isolation.

## Where a rule needed patching mid-flight

**`WORKFLOW.md` rule 3 says one self-critique gate, once, after Plan, before the first task —
this run needed two gates, not one.** Gate 1 (design-time, before code existed) caught the stale
`getParam` claim — a "does this plan still describe reality" question. Gate 2 (diff-time, after
the code and tests already existed, before commit) caught something gate 1 structurally couldn't:
the new `@Min`/`@Max` annotations were added symmetrically to both `getArticles` and `getFeed`,
but the first round of tests only exercised `getArticles` — nothing proved `getFeed`'s validation
actually fired the same way. That gap couldn't be seen before the tests were written, so a single
pre-implementation gate would have missed it. One test was added to `ListArticleApiTest.java`
(`should_get_error_message_with_limit_over_max_on_feed`), confirmed by independently reading the
diff, and the full suite re-run (91/91) before the commit. The rule as literally worded doesn't
have a slot for this second, pre-commit checkpoint — worth revising `WORKFLOW.md` to name both
gate points explicitly rather than "once."

## Something the rule set never anticipated

`WORKFLOW.md`'s five rules were all written assuming one agent plans, implements, and verifies in
a single continuous session. This run instead split those roles across two separate tools: real
implementation and test-running happened in the user's own Claude Code terminal; independent
verification (reading the actual `git diff`, `git status`, `git show --stat`, `git log` rather
than accepting a pasted transcript at face value) happened in a separate session that never ran
the code itself. None of the five existing rules address this division of labor — not a failure
surfaced by running the workflow, but a structural assumption the ruleset doesn't state anywhere.
Not turning this into a sixth rule yet on one occurrence (per `WORKFLOW.md`'s own composition
section, a rule that can't point to a repeat failure is decorative) — naming it here as the
honest gap, to revisit if this collaboration pattern becomes the default rather than a one-off.
