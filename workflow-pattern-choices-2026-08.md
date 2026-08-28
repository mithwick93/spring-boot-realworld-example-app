# Homework 1.4 — Choose the Workflow Pattern

Reference read: *Supplement: RPI and the Workflow-Pattern Family* ("The variant family"
section) — bands are Manual/human-gated (RPI, RPIR, QRSPI), Autonomous loop (Ralph),
Multi-agent harness (planner → generator → evaluator).

## The six tasks

| # | Task | Pattern | Justification |
|---|------|---------|----------------|
| 1 | Rename a variable across a single file | **Direct prompting** | Single-file, sub-30-minutes, zero ambiguity — any RPI overhead exceeds the task itself. |
| 2 | Add a feature that touches six files in a service you've never seen | **RPI** | Multi-file (5+) *and* brownfield/unfamiliar — exactly the case RPI's fresh-context-per-phase rule is for; no ambiguity about the goal, so no QRSPI Question/Design step is needed. |
| 3 | Change payment-rounding logic, where a wrong result costs real money | **RPIR** | High-stakes with an expensive cascading-error risk — the extra review gate and cross-model (uncorrelated-error) review are worth paying for here specifically because the cost of a missed error is asymmetric. |
| 4 | "Make onboarding feel faster" — no clear spec, unclear where to start | **QRSPI** | The ambiguity is in *what to build*, not how — QRSPI's explicit Question + Design steps exist precisely to de-risk that before any research budget is spent. Plain RPI would jump straight to Research on an undefined target. |
| 5 | Build a brand-new CLI tool from a one-paragraph spec | **Ralph** | Greenfield with a clear spec — "nothing to research." An autonomous generate-test-react loop fits; RPI's Research phase would have nothing to find. |
| 6 | *(real task, this repo)* Signup's username/email UNIQUE constraint (`V1__create_tables.sql`) has no exception handling for the race it exists to catch — two concurrent signups with the same username both pass the read-based `DuplicatedUsernameValidator`/`DuplicatedEmailValidator` check (`isValid` does `!userRepository.findByUsername(value).isPresent()`), then the second insert hits the DB constraint and throws an unhandled `DataIntegrityViolationException`. Neither `CustomizeExceptionHandler` (REST) nor `GraphQLCustomizeExceptionHandler` (GraphQL) catches it — it surfaces as an unmapped 500 instead of the same "username already exist" 422 the read-check produces. Fix: catch it in both handlers and re-shape it into the existing error format. | **RPI** | Multi-file (two exception handlers + a repository-layer wrap + tests for both REST and GraphQL paths), brownfield, and codebase is no longer unfamiliar — it was explored in Homework 1.2/1.3. Stakes are real (auth-path correctness) but the fix is mechanical once traced, not "wrong number costs money" territory, so RPIR's extra review-gate overhead isn't earned; the bug is already precisely located, so QRSPI's Question/Design de-risking has nothing to de-risk. |

## Evaluate

**Justify each choice on three axes (brownfield/greenfield, stakes, ambiguity).**
Task 1 is low on all three axes — that's what makes it a non-pattern case. Task 2 is
brownfield + low ambiguity + moderate stakes, which is RPI's exact target. Task 3 keeps
task 2's brownfield/low-ambiguity profile but stakes go up, which is the one axis that
moves it from RPI to RPIR. Task 4 keeps moderate stakes but ambiguity is now the high
axis — that's what moves it to QRSPI rather than RPI. Task 5 is greenfield with low
ambiguity (a spec exists) — greenfield is what rules out RPI entirely regardless of the
other two axes. Task 6 mirrors task 2's profile almost exactly (brownfield, low
ambiguity, moderate-real stakes) because the investigation itself already did the
"research" — I know the two exact files and the exact race.

**Where two patterns both seem to fit, what tips the call?**
Task 3 vs. task 2: both are brownfield multi-file changes, so RPI would "work" for task 3
too. What tips it to RPIR specifically is that the cost of a missed error is not
symmetric with the cost of the extra review pass — rounding logic errors compound
silently in production. If the payment task were "add a rounding *display* label" instead
of the rounding logic itself, I'd drop back to plain RPI — the stakes axis is about
blast radius, not the subject-matter label "payment."

**Which tasks need no pattern at all, and how do you know?**
Task 1. The tell isn't "it's small" in the abstract — it's the "competent contractor with
your README and one hour" test from the reference doc: a single rename needs zero
onboarding, so any structure added is pure overhead with no reliability payoff to buy back.

## Reflect

Task 4 was the hardest to place. "Make onboarding feel faster" has no file list, no
reproduction, and no fixed definition of "faster" — my first instinct was RPI, treating
"figure out what onboarding even means" as part of Research. Naming the ambiguity axis
explicitly is what moved it to QRSPI instead: RPI's Research phase assumes the *target*
is known and only the *path* to it is unknown, but here the target itself is undefined.
That's a genuine distinction, not a semantic one — QRSPI's Question/Design steps exist to
settle "what are we even building" with a human before any research effort is spent on a
possibly-wrong target.

## Verify

Task 6, one tier heavier (RPIR instead of RPI): the extra review gate and cross-model
review would cost real turnaround time for a bug whose fix is a five-line catch block in
two files — I already know exactly what's wrong and where, so a second model reviewing
the plan has nothing uncorrelated to catch. One tier lighter (direct prompting instead of
RPI): I'd lose the forced "write research to a file first" step, and REST-only fixes are
an easy trap here — prompting directly risks fixing `CustomizeExceptionHandler` and
declaring done, missing that `GraphQLCustomizeExceptionHandler` needs the identical fix on
the mutation path. Both answers are concrete failure modes, not hand-waves, so the RPI
placement holds.
