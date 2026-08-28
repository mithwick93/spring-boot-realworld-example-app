---
name: review-changes
description: Review staged changes against this repo's conventions before committing. Delegates the actual review to the read-only "reviewer" subagent rather than reviewing in the main thread. Use before every commit.
allowed-tools: Bash(git diff:*), Bash(git status:*), Task
---

Staged diff:

```
!`git diff --cached`
```

Changed files:

```
!`git diff --cached --name-only`
```

If both are empty, tell the user nothing is staged (`git add` first) and stop — do not fall
back to reviewing unstaged or already-committed changes silently.

Otherwise, do not review the diff yourself. Invoke the `reviewer` subagent (it is defined in
`.claude/agents/reviewer.md` — Read/Grep/Glob only, no write access) and hand it the diff and
file list above, asking it to check the change against this repo's `CLAUDE.md` conventions
and known pitfalls and return its findings plus a verdict. The whole point of this skill is
that review always happens through an agent that structurally cannot edit anything — relay
its findings and verdict back to the user verbatim, without softening or re-summarizing the
verdict line.
