# Close the Loop — 2026-09

Homework 4.6 [E] Close the Loop (lesson 4.5, Loop Engineering).

Claude Code v2.1.283 (Sonnet 5 default, Haiku 4.5 for the checker) for all sessions below.

---

## Failure

Reopened the most recent substantial session (`move-login-param-dto`, `feat/rest-api-dto-rule`,
2026-09-28) via `claude --resume`.

**One-sentence failure:** the edit that extracted `LoginParam` out of `UsersApi.java` left a
duplicate leftover class body behind, and the resulting diagnostics were dismissed as a known
pre-existing JDK/IDE issue instead of investigated — the background verification subagent's own
silent fix is the only reason the session's final "both files confirmed correct" report came back
accurate. Real, observed, not invented: the subagent's report in that session states directly that
its "caught fix (removing a duplicate leftover class body)" is what produced the intended end
state.

---

## The check

`.claude/hooks/verify-java-compile.sh` — a `PostToolUse` hook on `Edit|Write` matching
`src/main/java/**/*.java` and `src/test/java/**/*.java`. Compiles the project (`compileJava
compileTestJava`, correct `JAVA_HOME`) after every such edit and blocks (exit 2, real compiler
output) instead of letting any diagnostic get dismissed as unrelated.

Fixture kept at `.claude/hooks/fixtures/close-the-loop-2026-09/` — `broken/` reconstructs the
actual defect (imports stripped, trailing `LoginParam` class body left in `UsersApi.java`,
alongside the new `LoginParam.java`); `fixed/` is the corrected state. Verified directly (not
simulated): compiling `broken/` fails with 2 real compiler errors; compiling `fixed/` succeeds;
running the hook script itself against each state returns exit 2 (broken) and exit 0 (fixed).

Committed `030cc74` (`feat: PostToolUse compile-check hook + duplicate-class fixture (4.6)`) on
branch `feat/close-the-loop-hook`, opened as PR #16.

---

## Re-run with the checker

**Maker re-run** (fresh session, same prompt as the original task — "Move LoginParam out of
UsersApi.java into its own file..."): the same dismissal happened live — the diagnostic was again
called "the known pre-existing JDT/Gradle Java-version mismatch... unrelated to this change —
ignoring it" — but this time the new hook fired on the `Edit` and blocked with the real compiler
output (`duplicate class: io.spring.api.LoginParam` plus 8 cascading errors). That forced an actual
fix: the leftover class body was removed, and the hook passed silently on the next edit. Verified
independently against the real files afterward: `UsersApi.java` clean, `LoginParam.java` holds the
extracted class, no duplicate.

**Checker** (fresh session, `/model haiku` — confirmed by the session switching to "Haiku 4.5" in
its own header): read `.claude/hooks/fixtures/close-the-loop-2026-09/README.md` plus the real diff
and file contents, with an explicit instruction not to trust any prior claim of success. Verdict:
**PASS** — "`UsersApi.java` no longer contains any `LoginParam` class body or its now-unused
imports... `LoginParam.java` is the sole holder of the extracted class."

What the checker did *not* catch: nothing, because there was nothing left to catch — the hook from
step 2 had already forced the real fix mid-task, before the maker could report a false success.
The checker's PASS is confirmation that the hook worked, not a new finding of its own. That is a
meaningfully different outcome from the original failure, where nothing forced the fix and the
diagnostic simply got explained away.

---

## Cost per closed task

| | Maker re-run | Checker |
|---|---|---|
| Session | `move-loginparam-to-file` | `LoginParam duplicate check` |
| Active/API duration | 1m 11s | 1m 32s (session total — see caveat) |
| Cost | $0.3020 | $0.3292 (session total — see caveat) |
| Model | claude-sonnet-5 (dominant) | claude-sonnet-5 (dominant) |

**Caveat, stated plainly:** the checker session's `$0.3292`/`1m 32s` figures are for the whole
session, not just the review turn — the same session was reused afterward to run the step-4
`/loop` demo (two autonomous ticks before it was pointed at something real), and `/usage` reports
per-session totals, not per-turn. The review turn itself is the one marked "Sautéed for 42s · done
2:46 PM" in the transcript; the rest of that session's cost belongs to the loop demo below, not to
closing this task.

**Wall-clock, start to done:** the maker's own transcript reports "Baked for 1m 23s · done 2:44
PM" (start ≈ 2:42:37 PM); the checker's reports "Sautéed for 42s · done 2:46 PM." Start of the
re-run to the checker's recorded verdict: **≈ 3m 23s**.

One more real finding here: `/model haiku` printed "Set model to Haiku 4.5 and saved as your
default for new sessions" and the session header confirmed it, but the session's own `/usage`
breakdown shows the bulk of tokens and cost under `claude-sonnet-5`, not `claude-haiku-4-5`. The
most likely explanation is that the `/loop` cron ticks run afterward in the same session executed
on Sonnet regardless of the interactive `/model` setting — `/usage` does not break this down by
turn, so this is an observation, not a confirmed root cause.

---

## Recurring loop

**Bound, decided up front:** 1-minute cadence, stop once the target check reports a final status
(success or failure) or after 15 minutes, whichever comes first.

**First attempt (discarded):** `/loop 1m` was started in the checker session, whose only context
was the already-finished PASS review. Two ticks (2:53 PM, 2:54 PM) both correctly reported nothing
actionable — no PR existed yet to check. Real behavior, but not a real chore: the loop was honest
about having nothing to do, but there was nothing meaningful to observe either. Cancelled
(`CronDelete 56cf1509`) once that was clear.

**Second attempt (the real one):** committed the step-2 hook for real (`030cc74`) and opened PR
#16, giving `claude-review.yml` (from homework 4.1) a real, asynchronous check to run. `/loop 1m`
in a fresh session with the prompt to run `gh pr checks 16` and keep watching until every check
reported a final status. First lap already showed the check complete: `claude-review — pass
(1m21s)`. Notably, the session explicitly said "All checks are complete... ready to merge" and
still went ahead and scheduled the recurring cron anyway — exactly the lesson's point that a loop
does not stop itself just because the work is done. Cancelled manually (`CronDelete 87bcfa8e`)
once that was read.

**"It ran" vs. "it worked":** both loops ran on schedule and both laps were read directly, not
assumed from a green status. But only the second lap actually watched something real change state
(a CI check moving from pending to pass) — the first loop "ran" correctly every time without ever
having anything to "work" on.

---

## Evaluate

**What did the checker catch that the maker had declared done?** Nothing — see above. The work
was clean by the time the checker looked at it, because the durable check from step 2 had already
forced the real fix mid-task. The checker was not too weak to disagree; there was genuinely nothing
left to disagree with.

**Observed or imagined?** Observed. The failure came directly from a real, resumed session's
transcript, not from a hypothetical.

**Failure mode this loop is most exposed to:** over-automation — a passing `claude-review: pass`
badge on a PR is easy to treat as "reviewed" without anyone actually opening and reading the
comment the workflow posts. What would catch it: requiring the review comment's own text (not just
the check's pass/fail status) to be read before merge, the same way the checker session here was
told to read the actual diff rather than trust a status line.

**Up front or from real runs?** From a real run — the failure used here came from re-reading an
actual session's transcript, not from imagining what could go wrong with an untested capability.
