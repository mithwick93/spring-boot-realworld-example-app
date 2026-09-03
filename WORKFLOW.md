# WORKFLOW.md — My SDD Playbook

Composed from what actually happened across homeworks 2.1–2.6 on this repo, not from theory. Each rule below earns its place by naming the failure mode it prevents; a rule that can't name one gets cut.

## 1. Default intensity: spec-anchored

I reach for **spec-anchored** first — write the spec, let it ride alongside the change, update it when implementation surfaces something it didn't anticipate. This prevents the failure mode 2.2's lesson content names directly: alignment loss after the first commit outranks bad task descriptions as a cause of AI-assisted work going wrong, and spec-anchored is the cheapest defense against that drift without paying spec-first's full overhead on every task.

**Step down to no-spec** only when both hold: the root cause is already fully understood (no design decision left to make), *and* the fix mirrors an existing pattern already used elsewhere in the codebase (2.2 task 1 — the signup-race fix — qualified on both counts). Prevents: writing a spec for something with no actual decision in it, pure overhead.

**Step up to spec-first** when the change touches something already flagged as sensitive by an existing guardrail (a `PreToolUse` hook, a security/compliance-adjacent config file) or where an unstated assumption would be expensive to discover mid-implementation (2.2 task 3 — the `jwt.secret`/session config — qualified). Prevents: the failure mode of discovering a coordination or rollback question *after* the change is already half-built.

## 2. Spec template: the five-component skeleton

Functional Requirements → Acceptance Criteria (GIVEN/WHEN/THEN, at least one happy path and one error case with concrete values) → I/O Examples → Constraints (real limits first, then design decisions) → Out of Scope. This is the shape the course's own Sample Specification Document uses, and matches what a real guideline check against it fixed in 2.1 (cut narrative bloat, led Constraints with testable limits instead of prose).

**One template rule earned from a real bug, not from the guideline doc:** every claim about a field's limit (length, format, range) must be checked against the actual schema/column definition before it's written down, never asserted from memory. Prevents the exact bug the 2.1 peer-read caught: the spec said `reason` had "no length cap" while the DB column was `varchar(1000)` — an assumption that would have shipped as an unhandled 500 instead of a clean 422 if it hadn't been checked.

## 3. Phase gates

One mandatory human (or simulated-peer, disclosed as such) self-critique gate, run **once, after Plan, before the first task is implemented** — not before some later task. This is a correction from what 2.3/2.4 actually did: the homework structure gated before task 2 specifically, but 2.4's own Evaluate found that task 1 — implemented with *no* gate — was where the real risks (an exception-translation gap only a live test run caught) actually lived. The gate's question is always the same: "what's the strongest objection a senior engineer would raise about this plan?" Prevents: shipping an unreviewed design decision that only gets questioned after it's already been built and committed.

When using a tool that has one, add its mechanical validation gate on top (OpenSpec's `openspec validate --strict`, or an equivalent lint/check script for Manual) — not a replacement for the human gate, a second, cheaper one that runs every time. This caught a real thing in 2.6: `validate` rejecting a change with zero declared capability deltas is what forced an explicit "Modified Capabilities" section to exist, which is what made an unnecessary scope addition (a report `id` field nobody needed) visible enough to question and cut.

## 4. Iteration discipline: one task, one commit, full suite before and after

Each task is small enough to verify independently; run the full suite before starting (to know the real baseline count) and after finishing (to confirm the delta is exactly what's expected — not "tests pass," but "82/82, up from 75, and I can explain every one of the 7 new tests"). Prevents two failure modes at once: a regression hiding inside a task too large to bisect, and a commit message that claims more confidence than the verification actually supports.

## 5. Tool choice: Manual by default, OpenSpec for brownfield deltas

**Manual** (the five-component skeleton above, no dependencies) is the default — it has no install surface to fail, no schema to learn, and survives tool churn, which matters more than any tool's forcing functions for most changes on a small repo.

**Switch to OpenSpec** specifically when the next real task is an incremental change to a capability that's *already built and shipped* in this repo — the "I'm about to write a spec for something that already exists and works" signal is what makes OpenSpec's brownfield, delta-based model fit, rather than Spec-Kit's greenfield "spec before you build" model (rejected in 2.6 for exactly this reason: the feature it would've specified was already implemented, making a from-scratch spec-first tool redundant). Prevents: reaching for a phase-gated, greenfield-shaped tool on a repo that's mostly incremental brownfield work, and getting overhead with no matching payoff.

## Composition — how these rules reference each other

- The spec-template's schema-check rule is *enforced* by the phase-gate rule — the peer-read gate is literally where that check happens, not a separate step.
- The tool-choice rule depends on the spec-template existing first: OpenSpec's delta model only makes sense once there's a reference spec (or a Manual `requirements.md`) to diff against.
- Iteration discipline is the safety net for the phase-gate rule: the one-time self-critique gate after Plan won't catch everything (it didn't, in 2.3 — the SQLite exception-translation gap surfaced at Verify, not at the gate), so per-task full-suite verification is what catches what the gate missed.

## One rule I considered and rejected

I considered requiring a phase gate before *every* task in an iteration, not just once after Plan — mirroring 2.4's gate structure applied per-task instead of once. I rejected it: for a task like 2.5's (one real design decision — a route name — already resolved during Plan), a gate would just re-check reasoning that had no new information since the last gate, and the overhead would dominate for changes this size. I kept the one-time post-Plan gate and rely on iteration discipline's per-task test-diff to catch anything task-specific instead.

## Hardest rule to follow under time pressure

The phase-gate's self-critique question ("what's the strongest objection a senior engineer would raise?") — it doesn't feel like real work in the moment, and it's the first thing that gets skipped when short on time. It's also the one that found the most in practice (2.4's AC3 test-coverage gap, the exception-duplication trade-off) — which is exactly why it's the one most worth keeping, and the one most in need of an actual enforcement mechanism (a checklist item in `tasks.md`, or a required file that has to exist before a second task's commit is allowed) rather than relying on remembering to do it.
