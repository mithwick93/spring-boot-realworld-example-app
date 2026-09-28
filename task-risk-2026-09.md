# Task Risk — September 2026

Five real tasks from this week's work on the practice repo, categorized by risk and matched to an oversight framework, with two run for real at their assigned level.

## Risk table

| # | Task | Risk | Reasoning | Oversight framework | Why it matches |
|---|------|------|-----------|----------------------|-----------------|
| 1 | Editing `.github/workflows/claude-code-review.yml`'s job permissions (contents/pull-requests/issues scopes) | HIGH | A CI workflow permission grant runs unattended on every future PR; a wrong scope is hard to notice once merged, and it is security-sensitive by nature | HITL | Approving before execution catches a bad scope before it ever runs against a real PR |
| 2 | Running the orchestration-recipe script (20-worker input-validation audit, real API spend) | MEDIUM | Reversible — only writes report files, no repo mutation beyond docs — but real dollars and dozens of API calls across parallel workers, so it needs a record of what actually ran rather than a human watching each call | Auto mode / autonomous with audit | Retries, cost caps, and an audit trail already built into the recipe; the job is routine enough not to need per-call approval |
| 3 | Handling the `detect-secrets` block on a real `jwt.secret` value in `application.properties` | HIGH | Touches a real secret directly; a wrong call either ships a flagged secret silently or blocks a legitimate commit indefinitely — needs a human decision, not a classifier | HITL | Secret-handling calls for judgment about what's actually safe to allowlist, not something to auto-approve |
| 4 | Committing and pushing a homework deliverable commit to the shared `github/main` branch | MEDIUM | Reversible via `git revert`, but lands on the branch other work builds on top of | HOTL (ask rule on `git push`) | Free to work up to the push, but the push itself is a natural checkpoint to stop at |
| 5 | Running the small-scope 5-item validation audit (Read/Grep-only workers, no repo writes) | LOW | Read-only, no state change possible | Autonomous with audit | Safe to leave unwatched; a log of what was read is still worth keeping |

## The two live runs

**Task 1 — HITL.** Started a normal `claude` session (default permission mode) in the repo and asked it to tighten `issues: write` down to `issues: read` in the workflow's permissions block, since the workflow only ever posts PR comments and never touches issues. It came back with a full plan and stopped at the permission-mode prompt before touching anything. Approved "manually approve edits" rather than auto mode, then approved the actual edit when it asked. Read the file back afterward directly — `issues: read` at line 25, every other permission (`contents: write`, `pull-requests: write`, `id-token: write`, `actions: read`) untouched. Committed as `b55bd45`. The post-tool audit hook (registered separately, see below) also fired for real during this session without being asked to — one `Write` on the plan file it generated, one `Edit` on the workflow file — confirming the audit layer works independently of which oversight mode a session runs under.

Calibration: held. Nothing to revise here — the checkpoint asked exactly where it should have, and the approved change was correct.

**Task 2 — auto mode / autonomous with audit.** Re-ran the two items from a prior audit that had failed all three attempts on a turn-budget limit, this time with double the turn budget, inside an isolated Docker container (no `.git`/no remote at all in the copy used, non-root `node` user, credentials passed in as a single scoped long-lived token rather than any host credential file). Both items succeeded on the first attempt, confirming the turn budget — not something else — was the real cause of the earlier failures.

The audit trail (a post-tool hook writing one line per action to `audit-log-2026-09.txt`) worked exactly as intended and caught something the setup didn't: the run had been given `--allowedTools "Read,Write"` specifically so the workers could only read files and write their report — but the log showed the workers running `grep`, `find`, and `mkdir` via Bash throughout, which was never supposed to be available. Checking `claude -p --help` explained why: `--allowedTools` is a pre-approval list, not a hard restriction on which tools exist at all; the flag that actually restricts the available toolset is `--tools`. Adding `--tools "Read,Write"` alongside it and re-running produced the identical result — Bash calls still went through. Neither flag held, under `--permission-mode acceptEdits`, in this installed version.

Nothing destructive happened in either run (only read-only exploration plus the intended report write), so the MEDIUM risk assignment for this task held in substance — the audit trail did its job and gave full visibility into what actually ran. But this is the real calibration finding: not that the task was mis-rated, but that a flag I assumed would restrict the tool surface silently did not, and the only reason that was visible at all was the audit hook. A CLI flag turned out to be a habit, not a guarantee — exactly the distinction the guard hook (task 3, `hook-test-2026-09.md`) was built to test directly.

Tasks 3 and 5 were not re-run live this pass; their categorization above reflects how they already played out earlier this week and there's no new evidence to revise them on.

## Calibration miss

No task's risk *level* needs revising based on what actually happened. The real miss was at the mechanism level, not the categorization level: I treated `--allowedTools` as equivalent to a hard tool restriction, and it isn't — only `--tools` (and, more reliably than either, a PreToolUse hook) actually holds.
