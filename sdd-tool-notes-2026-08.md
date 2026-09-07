# SDD Tool Notes: OpenSpec — 2026-08

Tool run: **OpenSpec** (`@fission-ai/openspec@latest`, resolved to `1.11.0`), against the real `POST /articles/{slug}/report` feature specified in homework 2.1 (`spec-2026-08.md`) and built in 2.3/2.5. Real CLI, real repo, real artifact — no hello-world.

## What I ran

```
npm install -g @fission-ai/openspec@latest   # after fixing a permission issue, see below
openspec init --tools claude .
openspec new change "surface-article-reports-to-admins"
openspec status --change surface-article-reports-to-admins --json
openspec instructions proposal --change surface-article-reports-to-admins --json
# ...wrote proposal.md, then repeated status/instructions for specs, design, tasks...
openspec validate surface-article-reports-to-admins --strict
```

The feature: a real, already-flagged gap, not invented for this homework. `peer-read-notes-2026-08.md` (from 2.1) noted that every row written to `article_report` is currently unreachable — no `GET`, no admin view, not even a report `id` to correlate later. That flagged-but-deferred gap is the actual motivating "why" for this change proposal.

## What worked out of the box

`openspec init` generated real, working scaffolding immediately: `openspec/config.yaml`, `openspec/specs/`, `openspec/changes/`, plus `.claude/commands/opsx/*.md` (6 slash commands) and `.claude/skills/openspec-*/SKILL.md` (6 skills) for Claude Code integration. `openspec new change`, `status --json`, `instructions <id> --json`, and `validate --strict` all worked first try with clean, exactly-documented JSON — no surprises, no undocumented fields.

## What required adjustment

1. **Environment, not the tool:** `npm install -g` failed with `EACCES` against the sandbox's default global `node_modules` (no write permission there). Fixed by pointing `npm config set prefix` at a writable directory — a sandbox quirk, not an OpenSpec problem.
2. **No interactive Claude Code session here.** The six `/opsx:*` slash commands are meant to be invoked inside an actual `claude` terminal session; this sandbox runs Cowork, not that. Since a slash command is really just a markdown prompt template pointing at the same CLI, I read `.claude/skills/openspec-propose/SKILL.md` directly and manually followed its documented procedure — `openspec new change` → `status --json` → `instructions <id> --json` → write the file per the returned `template` → repeat — by hand, using the real CLI the whole time. This is a genuine, disclosed substitution, not a shortcut around the tool: every file that exists was produced by following the tool's own documented instructions field-for-field, just without the slash-command convenience wrapper.
3. **The `init` self-test hit this sandbox's known FUSE limitation.** `openspec init` tried to write and then delete a probe file (`.openspec-test-...`) to check write permissions, and the delete failed (`EPERM: operation not permitted, unlink`) — the same "can create, can't delete" limitation logged elsewhere in this repo's own `training-progress.md` for git lock files. Non-fatal; `init` printed a warning and completed successfully anyway.

## What the tool forced me to think about that the Manual approach doesn't

`proposal.md`'s **Capabilities** section (New/Modified) is not optional prose — `openspec validate` genuinely rejects a change with zero declared capability deltas. Naming "Modified Capabilities: `article-report` — adding an `id` field" as its own explicit, structural section (not a buried sentence) is what made me stop and ask whether that modification was actually necessary. It wasn't: `article_report`'s existing composite key `(article_id, user_id)` already gives a reporter's own view all the identification it needs, so the field was dropped and the change became pure-addition, zero-modification. The Manual four-file pattern (`requirements.md`/`design.md`/`guidelines.md`/`tasks.md`) has nothing structurally equivalent — there's no artifact whose entire job is "declare every existing behavior contract you're touching, one by one, before you're allowed to write anything else." **This forcing function was genuinely useful, not noise** — it caught a real unnecessary scope addition in real time, the same class of catch this project's other homeworks (2.3's Risk 1, 2.4's review gate) found through manual self-discipline instead. OpenSpec makes that catch structurally harder to skip, which is worth something for anyone less inclined to self-audit.

## Where each handoff lands (if the whole team used this)

`openspec/specs/` is the durable "what's true about the system today" catalog; `openspec/changes/<name>/` is in-flight work with a machine-checkable completion state (`openspec status --json` reports `4/4 artifacts complete` for this change, right now). `openspec archive` (not run here — this change is deliberately left `in-progress`, since the homework's Verify step asks for the artifact, not for shipping it) is the one moment a change's spec deltas fold into the main `specs/` tree and the change folder is retired. The handoff for the next person (or the next fresh Claude Code session) is exactly these four files in a known location, queryable with the same `openspec instructions <id> --json` command I used — not prose in a chat message or a PR description that has to be re-read and re-interpreted from scratch every time.

## Three-sentence justification for keeping this tool for real work

(a) **Feature scope fit:** yes — this repo is a small, existing (brownfield) codebase where almost all future work is incremental additions to already-shipped capabilities, exactly OpenSpec's stated sweet spot, not a from-scratch greenfield buildout Spec-Kit is built for. (b) **Team/environment fit:** partial — the user works solo and this sandbox isn't an interactive `claude` terminal, so the slash-command convenience layer degrades to "read the skill file, run the same CLI by hand" (which still works, but a real interactive Claude Code user wouldn't hit that friction). (c) **Philosophy fit:** yes — delta-based, no phase gates, static sync all match how this project has actually operated across Week 2 (small additive changes, one commit at a time, docs updated by hand) — adopting OpenSpec formalizes the artifact shape without requiring a different way of working.

## One configuration choice I'd reject

The `spec-driven` schema's own `design.md` instruction text says "create only if any apply" (cross-cutting change, new dependency, security/perf/migration complexity, real ambiguity) — implying it's conditional. But the schema's actual dependency graph hard-requires it anyway: `openspec status --json` showed `tasks` with `"requires": ["specs", "design"]`, unconditionally. There's a `skip_specs: true` escape hatch for skipping specs entirely on a zero-behavior-change proposal, but no equivalent `skip_design` — so a genuinely simple change (arguably this one; the design decisions here were real but small — one route name, one repository method) is still structurally forced to produce a `design.md` or `tasks` stays permanently blocked. I'd reject that: the schema's own instruction text promises optionality that the dependency graph doesn't actually deliver, and I'd want a `skip_design` marker parallel to `skip_specs`.
