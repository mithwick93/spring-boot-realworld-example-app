# Scaling Notes — orchestration-recipe-input-validation-audit-2026-09.sh

Job: one worker per file, auditing REST controllers and their request DTOs/validators against
`CLAUDE.md`'s Security Requirements > Input Validation section. Two real runs: 5 items, then
the full 20-item set. Full run executed twice — once with a script defect in the audit-log
writer, once after the fix (see "What broke in the tooling itself" below). Numbers here are
from the corrected run unless noted.

## Did the script hold between the small run and the large run?

No, not cleanly. The 5-item run succeeded 5/5 on the first attempt, no retries. The 20-item run
succeeded 18/20, with 2 items (`TASK-12`, `TASK-16`) failing all 3 retry attempts and ending in
`FAILED` status. This was reproducible: both the discarded first full run and the corrected
second full run failed on exactly the same two items, for the same reason, every single time
(6 failed attempts total across the two runs for those two items alone).

## What broke, and where

Every one of the 9 failed attempts across both full runs carries the identical
`terminal_reason: "max_turns"` / `"Reached maximum number of turns (8)"` in its audit record —
not a rate limit, not the dollar cap, not a timeout. `--max-turns 8` was too tight specifically
for items whose question requires comparing two files against each other rather than reading
one file in isolation:

| Item | Question shape | Outcome |
|---|---|---|
| TASK-12 | `UpdateArticleParam` vs. `NewArticleParam` symmetry | Failed all 3 attempts, both runs |
| TASK-16 | `UpdateUserParam` vs. `RegisterParam` symmetry | Failed all 3 attempts, both runs |
| TASK-13 | `DuplicatedArticleUpdateValidator` cross-reference | Failed attempt 1, recovered on retry |
| TASK-17 | `DuplicatedEmailValidator` cross-reference | Failed attempt 1, recovered on retry |
| TASK-03 (2nd full run only) | `ArticlesApi` DTOs + query params, single file but broad scope | Failed attempt 1, recovered on retry |

Retrying with backoff fixed the transient cases (TASK-13, TASK-17, TASK-03) — the same question,
re-run, stayed under 8 turns the second time. It did not fix TASK-12 or TASK-16: 3 attempts,
identical failure reason, every time. That's the real distinction the small-then-full comparison
surfaces — retries paper over turn-budget variance, but they can't fix an item whose question is
systematically too broad for the cap. The first full run also showed a `permission_denials` entry
on some of the failing attempts (the worker tried a compound multi-command Bash one-liner the
script never granted permission for, via `--allowedTools`), but the second full run reproduced
the identical TASK-12/TASK-16 failures with zero permission denials — so the denied Bash calls
were an occasional contributing friction, not the root cause. The root cause is the 8-turn budget
being undersized for a cross-file comparison question.

## What broke in the tooling itself

The first full run's audit log was not valid NDJSON: the `jq -n` calls that built each entry
were missing the `-c` (compact) flag, so every record was pretty-printed across ~40 lines
instead of one line per call (1,077 lines for 27 records). This was caught by inspecting the
raw file directly before treating it as a deliverable, not by trusting the script's own
"complete" exit. Fixed by changing both `jq -n` calls that append to `$AUDIT_LOG` to `jq -nc`,
verified against a synthetic record, then re-run. The corrected run's audit.ndjson is 25 lines
for 25 records — one line per call, as the format requires.

## Rate limits, token budgets, timeouts

None hit. No `429`/rate-limit error appeared in any attempt across either full run. No attempt
hit the `--max-budget-usd 2` dollar cap — the highest single-attempt cost observed was $0.22,
well under the $2 ceiling; the constraint that actually bound was `--max-turns 8`, not the
dollar cap. No attempt hit the 300-second timeout.

## Could you tell from the audit record alone which item failed, at which step, and why?

Yes. Every failed attempt's audit line names the item (`TASK-12`, `TASK-16`, etc.), the attempt
number, and carries the full JSON the call printed, including `terminal_reason: "max_turns"` and
`errors: ["Reached maximum number of turns (8)"]`. No need to cross-reference the `.out` files to
know why something failed, though they're kept for full stdout/stderr detail.

One wrinkle worth naming honestly: `TASK-16`'s `.status` file and its audit-log entry both say
`FAILED` on its third and final attempt in both full runs, and correctly so — but in both runs a
complete, well-formed report file (`TASK-16.md`) was also left on disk, because the worker
finished writing it before the run was flagged as having hit the turn cap. The script's success
check (borrowed from the supplement's own caution that exit status alone can't be trusted)
requires both a clean exit and a non-empty report file, so it correctly recorded this as a
failure by its own rule — a run that hits its turn cap is not a clean success even if a usable
artifact happened to land first. The audit record doesn't paper over this: it says `FAILED`, and
a reader checking only the audit record would not assume the item completed. Whether the leftover
report is usable anyway is a separate, human judgment call the audit record correctly leaves open
rather than resolving on the item's behalf.

## Is the cost sustainable for a team, or only for one-off jobs?

| Run | Items | Attempts | Cost |
|---|---|---|---|
| Small | 5 | 5 | $1.0002 |
| Full (corrected) | 20 | 25 | $4.5872 |

Cost per item held steady (~$0.15-0.22 per attempt) from 5 items to 20 — no cost blowup at
scale, and per-item cost matches homework 4.2's own single-file worker costs closely. At this
rate, a recurring per-file audit like this one is affordable for occasional use (a pre-release
sweep, say) but not for running on every commit across a codebase this size without narrowing
the item list or raising the per-item turn budget for the specific question shapes that need it —
the marginal cost of hitting `max_turns` and retrying twice before giving up (TASK-12, TASK-16
each spent 3 full attempts for zero usable output in the corrected run) is real, avoidable waste:
raising `--max-turns` for cross-file comparison items specifically, rather than retrying the same
undersized budget three times, is the fix suggested by this data rather than more retries.
