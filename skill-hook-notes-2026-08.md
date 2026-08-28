# Homework 1.5 — Skill + Hook: notes

Live session evidence this is based on: `/exception-coverage DataIntegrityViolationException`
run, three real Write/Edit ops (`UserService.java`, `build.gradle`, plus two internal
plan-file writes), and the refused edit to `application.properties` (`hook-refusal-2026-08.txt`).
`.claude-tool-log.txt` (4 lines) and `application.properties` (unchanged, still holding
`jwt.secret`) were both re-read directly from disk to confirm the transcript matches reality.

## Would I notice if each artifact disappeared?
- **`exception-coverage` skill**: yes. It's what surfaced that `PUT /articles/{slug}`
  (`UpdateArticleParam`) has *no* duplicate-slug guard at all, worse than the read-check-then-race
  gap the other five entry points share. A generic grep wouldn't have organized that by
  adapter coverage.
- **PostToolUse log hook**: yes — it's the artifact that let me confirm, factually, that
  the matcher only fires on Write/Edit and not on Bash or Read (see below), rather than
  guessing.
- **PreToolUse refusal hook**: yes — `application.properties` still has no comment on
  `jwt.secret` because the hook stopped the edit before it happened. Without it the file
  would already be changed.

## Smallest viable version of each
- Skill: step 5 (checking each entry point separately, not just the exception type) could
  be cut for brevity, but it's what caught the unguarded `updateArticle` path — cutting it
  would have produced a report that missed the single worst case (no race needed, one
  request is enough).
- Hook: the timestamp in the log line could be dropped, but it's what made the ordering
  legible — the two plan-file writes visibly preceded the two real code edits.

## Matcher thought experiment
Real evidence, not hypothetical: the same session ran a Bash append (the README.md blank
line) and a `Read`/`cat` of the log file itself, and neither appears in
`.claude-tool-log.txt` — only the two `Edit` calls and two plan `Write` calls do. So
`Write|Edit` is doing real filtering, not decoration.

Widening it to `.*` would pull in `Read`, `Grep`, `Bash`, and the `Explore` subagent's
tool calls too. For the **log hook**, that might be desirable if the goal were a full
audit trail (e.g. compliance: "what did the agent look at"), but it would drown the
signal the homework actually asks for — "at least three entries from Write or Edit
only" stops meaning anything once every Read is in there too. For the **refusal hook**,
widening it would be actively wrong: the risk being guarded against is *mutation* of
`application.properties`, not *visibility* into it — a broadened matcher would start
refusing harmless reads/greps that happen to reference the file, which is a false
positive, not extra safety.

## Skill vs. hook — which surface for which behaviour
Lived difference, not just the definition: the skill ran exactly once, only because I
typed `/exception-coverage` — it's a playbook I reach for. Both hooks ran automatically,
every time their event matched, whether or not I asked — the log fired on every Write/Edit
in the whole session including the plan-file writes I didn't think about; the refusal
hook fired the one time it mattered without me having to remember it existed. That's the
actual dividing line: reach for a skill when the behaviour should run occasionally, on
your judgment; reach for a hook when it must run every time, whether you remembered to
ask or not.

## PreToolUse vs. PostToolUse for the refusal
The refusal fired on PreToolUse, before the Edit tool touched the file — confirmed by
re-reading `application.properties` afterward: still byte-for-byte the original, `jwt.secret`
line intact, no comment added. Had the same `exit 2` logic been wired to PostToolUse
instead, the Edit tool would already have written the new content to disk by the time the
hook ran; exit 2 at that point can't undo the write. It would only be able to log or flag
that the protected file had just been changed — a detector, not a guard. PreToolUse is
what makes it a guard: it runs before the tool result is applied, not after.

## Two-sentence answer: skill vs. hook, for the cohort
A skill is a playbook you reach for deliberately, on demand, when you judge the moment is
right. A hook is a guarantee — it fires on every matching event whether or not you
remembered to ask, which is the only way "always log this" or "never allow that" can
actually hold.
