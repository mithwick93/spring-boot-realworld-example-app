# session-recovery-2026-08

Task: fix the `DuplicatedArticleValidator` self-exclusion bug (tracked in `task-checklist.md`),
using a compact-matcher `SessionStart` hook (`.claude/settings.json`) plus a deliberate handoff
to a fresh session.

## Part 1 — `/compact` mid-task (Session A)

Ran `/compact` with the core fix implemented but the test-isolation bug still unfixed and the
full suite not yet re-verified. Right after compacting, the session re-read five files on its
own, including `task-checklist.md`:

```
❯ /compact
  ⎿  Compacted (ctrl+o to see full summary)
Read src/test/java/io/spring/application/article/ArticleCommandServiceTest.java (72 lines)
Read task-checklist.md (66 lines)
Read src/main/java/io/spring/application/article/DuplicatedArticleUpdateValidator.java (47 lines)
Read src/main/java/io/spring/api/ArticleApi.java (88 lines)
Read src/main/java/io/spring/application/article/ArticleCommandService.java (40 lines)
```

Honest caveat: I can't cleanly attribute this to the hook versus incidental continuity. A test
subagent I'd launched before compacting reported its result at almost the same moment, and the
very next thing the session did was handle that result — not explicitly restate "next checklist
step is X." Re-reading `task-checklist.md` is real, visible evidence of checklist-based recovery
either way, but it doesn't prove the hook specifically (rather than the session's own judgment)
is what caused it. I didn't redo this with a distinctive hook marker to disambiguate, since the
rest of the run produced cleaner, unambiguous evidence for the harder Verify criteria (below).

## Part 2 — fresh session continuing from the handoff alone (Session B)

Closed Session A after it wrote a full handoff into `task-checklist.md` (what's done, what's
next, files touched, decisions made). Started a brand-new `claude` session — no `--resume` —
and sent exactly one message:

```
❯ Read task-checklist.md — that's your only context. Continue.
```

No other explanation was given. The fresh session read the checklist plus the files it named,
launched an Explore agent to confirm this repo's UUID-based test-uniqueness convention (there
wasn't one — it correctly proceeded with the diagnosed fix anyway), and picked up exactly where
Session A left off: finished the test-isolation fix in `ArticleCommandServiceTest.java`,
delegated the full suite to a subagent (71/71 passing), ran `spotlessJavaApply`, ran
`/review-changes` (real delegation to the `reviewer` subagent, verdict `approve`, no
violations), documented the cross-parameter validation pitfall in `CLAUDE.md`, filled in the
checklist's "Where each fact belongs" section, and — once I explicitly said "commit this" —
committed as `954c511`. It never asked me to clarify anything the handoff hadn't already
covered.

## Part 3 — context isolation, measured not estimated

Read `/context` immediately before delegating one more real operation (a post-commit
re-verification of the full suite), then again immediately after:

**Before:**
```
120.6k/1m tokens (12%)
```

**Delegated:**
```
❯ Delegate to a subagent: re-run the full test suite (./gradlew test) one more time now that
the fix is committed, and report back only the pass/fail totals — nothing else.

⏺ Agent "Final gradle test verification post-commit" finished · 20s
Final verification: BUILD SUCCESSFUL, 71 tests, 0 failures.
```

**After:**
```
134.5k/1m tokens (13%)
```

A full Gradle test run — build output, test report, everything — added roughly 14k tokens
(1 percentage point) to the main thread instead of the full raw output, because only the
subagent's one-line summary crossed back into context. That's the number this exercise is
built around: not an estimate of what isolation saves, the actual delta.

## Evaluate

- **Did it identify the next step unprompted, or need pointing at the checklist?** Ambiguous
  for the `/compact` case (Part 1) — a coincidental subagent result muddies it. Unambiguous for
  the handoff case (Part 2) — a completely fresh session, given only "read the checklist," went
  end-to-end to a real commit with zero clarifying questions.
- **What did context isolation save?** ~14k tokens for one delegated test run, measured
  directly (Part 3) — not an estimate.
- **Degradation signals to watch for going forward:** none appeared here severely, but the
  ambiguity in Part 1 is itself a signal worth naming — when a background subagent's result
  lands right at a compaction boundary, it can look like checklist-driven recovery when it might
  just be residual short-term continuity. Worth testing with a distinctive hook marker
  (e.g. an `echo` sentinel ahead of `cat task-checklist.md`) next time this matters for real,
  rather than inferring it from tool-call ordering.
- **What did the fresh session ask that the handoff should have answered?** Nothing — that's
  the actual finding. The handoff named what was done, what remained, which files mattered, and
  which decisions were already made, and that was sufficient.
