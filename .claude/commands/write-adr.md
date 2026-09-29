---
name: "Write ADR"
description: "Extract an Architecture Decision Record from the current branch's diff, before opening a PR"
allowed-tools: Bash(git diff:*), Bash(git log:*), Bash(git status), Bash(git branch:*), Bash(ls:*), Read, Write, Bash(git add:*), Bash(git commit:*)
category: "Workflow"
tags: ["adr", "documentation", "pr-flow"]
---

Run this after committing your code change on a feature branch, but **before** running `gh pr create`. The goal: if this branch's diff represents a real architectural decision, draft an ADR and commit it into the same branch, so it ships as part of the PR diff rather than as an afterthought.

**Steps**

1. **Find the diff for this PR-to-be.**
   ```bash
   git branch --show-current
   git diff origin/main...HEAD
   ```
   If `origin/main` isn't available locally, fall back to `git diff main...HEAD`. If the current branch IS `main`, stop and tell the user this command only makes sense on a feature branch.

2. **Decide whether this diff is architecturally significant.** Read the actual diff, not just file names. Architecturally significant means things like: a new external dependency, a new or changed cross-cutting pattern (e.g. how errors are handled, how auth works, how a layer talks to another layer), a data model or schema change, a chosen approach where a real alternative existed, or a new convention future code is expected to follow. NOT significant: a test-only change, a typo/formatting fix, a routine bug fix that doesn't change any pattern, a dependency version bump with no behavior change.

   State your reasoning briefly either way.

3. **If not significant:** say so in one or two sentences and stop. Do not create a file, do not ask further questions.

4. **If significant:**
   - Run `ls docs/adr 2>/dev/null` to find existing ADRs (create the `docs/adr/` directory when writing the file if it doesn't exist yet). Determine the next number by taking the highest existing `NNNN-*.md` prefix and incrementing (zero-padded to 4 digits, starting at `0001` if none exist).
   - Derive a short kebab-case title from the decision itself (not the branch name).
   - Draft `docs/adr/NNNN-<kebab-title>.md` using this structure:
     ```markdown
     # NNNN. <Title>

     Date: <today's date>

     ## Status

     Accepted

     ## Context

     <What situation/problem made a decision necessary. Cite the actual files/classes/behavior involved - not generic language.>

     ## Decision

     <What was decided, stated plainly. If there were real alternatives, name them and say why they were not chosen.>

     ## Consequences

     <What this makes easier, what it makes harder, what future code should now follow, any follow-up work this implies.>
     ```
   - Base every section on the real diff you read in step 1 - do not write generic boilerplate that could apply to any change.

5. **Show the drafted ADR content to the user and ask before writing the file or committing it.** This is a new document being added to the repo - confirm before creating it, per this project's standing rule of asking before writing documents.

6. **If approved:**
   ```bash
   git add docs/adr/NNNN-<kebab-title>.md
   git commit -m "docs: add ADR NNNN - <title>"
   ```
   This lands the ADR as a commit on the current branch, before the PR is opened, so `gh pr create` will include it automatically.

**Guardrails**
- Never invent decisions that aren't actually in the diff - the ADR must describe what this specific change does, not a hypothetical.
- Never fabricate "alternatives considered" if none are evident from the diff or conversation - it's fine for a Decision section to state the choice without a false alternatives narrative.
- One ADR per invocation. If the diff contains multiple unrelated architectural decisions, say so and ask the user which one to document first (or whether to write more than one).
