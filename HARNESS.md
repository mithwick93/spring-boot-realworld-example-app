# HARNESS.md — spring-boot-realworld-example-app

My `.claude/` here is built around one workflow: review before commit, with a real guardrail
around real secrets. Six artifacts serve all seven components, but two components (quality
gates, effectiveness levers) have no dedicated artifact of their own — they're carried by
pieces I built for tool surface and control flow instead. That's defensible for a one-person
repo; on a bigger team I'd expect quality gates to earn a real artifact (see the rejection
below).

## Component audit

1. **Context engineering** — `CLAUDE.md`. Carries the conventions `/init` missed: test
   naming, DTO placement, and the one that actually bites — error responses aren't uniformly
   shaped. *Rationale (quality): a reviewer that doesn't know this convention flags the wrong
   things.*

2. **Tool surface** — `.mcp.json` (IntelliJ's bundled MCP server, `idea`),
   `.claude/skills/review-changes/SKILL.md`, `.claude/skills/exception-coverage/SKILL.md`,
   `.claude/agents/reviewer.md`. The most crowded component — four of six artifacts live
   here. *Rationale (effectiveness): review, exception-path checking, and IDE-accurate call
   hierarchy are the three things I did by hand most often; each is now a command instead of
   a habit I have to remember.*

3. **Control flow** — the `SessionStart` reminder hook, plus the tool restrictions on
   `reviewer` (Read/Grep/Glob only) and `review-changes` (`Bash(git diff:*)`,
   `Bash(git status:*)`, `Task` only). *Rationale (reliability): the reviewer can't act on its
   own findings even if I ask it to — the tool list, not a written instruction, is what
   actually stops it.*

4. **Quality gates** — the same `review-changes` → `reviewer` pair, again, not a dedicated
   artifact. *Rationale (quality): the gate is human-triggered review against documented
   conventions, not automated test or lint enforcement — see the rejection below for why I
   didn't automate it further.*

5. **Reliability & safety** — the `PreToolUse` hook refusing writes to
   `application.properties` (exit 2), which holds `jwt.secret` and the datasource
   credentials. *Rationale (reliability): this is the one artifact I'd notice immediately if
   it disappeared — it's the only thing standing between a careless edit and a leaked
   secret.*

6. **Observability** — the `PostToolUse` hook logging every `Write`/`Edit` to
   `.claude-tool-log.txt` (gitignored — five real entries as of today, matching my actual
   edits). *Rationale (observability): the matcher is `Write|Edit` only, so Bash and Read
   calls from the same sessions don't show up — it's already scoped to the two tools I
   actually need audited, not everything.*

7. **Effectiveness levers** — `reviewer.md` pinned to `model: haiku` with a 6-tool-call turn
   budget. *Rationale (effectiveness): checking a diff against six fixed conventions doesn't
   need Sonnet-level reasoning or an open-ended tool budget — haiku plus a hard cap keeps it
   cheap and stops it from wandering into a full repo audit.*

## The rejection

I considered a `PreToolUse` hook that ran `./gradlew test` (or `spotlessJavaApply`) on every
`Write`/`Edit`, as a real automated quality gate instead of the review-only one I have.
Rejected it: hooks fire per tool call, not per commit, so a hook matched on `Write|Edit`
would re-run the full test suite on every single file edit in a session — dozens of times
for one commit's worth of work. The gate I actually want is per-commit; Claude Code's hook
matchers are per-tool-call. The tool doesn't have the shape this gate needs.
`review-changes` running against the staged diff is the version of a quality gate that
actually fits the trigger point I have available.

## Cross-artifact reference

`review-changes` doesn't review anything itself — its own instructions say not to, and hand
the staged diff to the `reviewer` subagent via `Task` instead. That's components 2 (tool
surface) and 3 (control flow) depending on each other: the skill is only as safe as the agent
it delegates to, and that agent's tool list — Read/Grep/Glob, no `Write` — is a control-flow
decision made in a separate file. Delete `reviewer.md` and `review-changes` still runs, but
it has nothing safe left to hand the diff to.

## What's empty

No `.claude/rules/`, no reliance on auto memory, no statusline, no OTEL. Deliberate — one
repo, one person, one review workflow. Onboarding a teammate to this harness is five files,
not a settings dump to reverse-engineer.
