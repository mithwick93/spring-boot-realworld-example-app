# Plan a Production Agentic Workflow — DTO-shape audit (homework 4.8)

**Workflow designed (not built):** turning `scripts/dto_shape_audit.py` — the homework 4.7
headless batch agent that reviews every REST controller in `src/main/java/io/spring/api/`
against `.claude/rules/rest-api-request-dtos.md` — from a script one person runs by hand into
an unattended PR check the whole team relies on.

**Grounded in real artifacts**, not hypothetical: `scripts/dto_shape_audit.py`'s actual
`ClaudeAgentOptions` (`allowed_tools=["Read"]`, `permission_mode="dontAsk"`, `max_turns=3`,
`max_budget_usd=0.50`), the real batch run in `headless-run-2026-09.md` (10/10 files, $0.2705
total, $0.02–$0.053 per item), the real three-attempt denial-trigger finding from that same run,
and the production PR-review workflow already running in this repo
(`.github/workflows/claude-code-review.yml` — `pull_request` webhook, `claude-code-action`,
structured JSON output, a separate plain step posts the PR comment with `GITHUB_TOKEN` rather
than letting Claude post directly, per `anthropics/claude-code-action#647`).

---

## 1. Where does it run, and why there?

**Decision:** a CI runner — GitHub Actions, the same platform `claude-code-review.yml` already
runs on in this repo.

**Reason:** the job is bounded (10 independent `query()` calls, each finishing in 1–3 turns) and
produces a discrete artifact (a per-file verdict), which is exactly the CI-runner profile from
the lesson. Reusing GitHub Actions costs nothing new to operate — the repo already has a working
Claude-driven Action, a secrets store (`CLAUDE_CODE_OAUTH_TOKEN`), and a comment-posting pattern
to copy.

**Failure modes of this place, and why the workflow can live with them:**
- *CI quota exhaustion* (shared Actions minutes) — livable: the whole job is ~10 short,
  Read-only calls, not a long-running process; it's a small fraction of what the rest of the
  pipeline (build, test, the existing review Action) already spends.
- *Runner-config drift* (e.g. the runner image dropping the Python version this needs) — livable
  as long as the job pins `claude-agent-sdk==0.2.160` (the real installed version) in its own
  `requirements.txt` and fails the install step loudly rather than silently running against
  whatever happens to be on the image.

**Rejected, with reasons:** local CLI (doesn't scale past the one person who remembers to run
it — this is the exact gap 4.7 found: 3 real violations sitting undetected because nobody ran
the audit against those files); serverless function (no natural single triggering event beyond
what a webhook already gives a CI runner, and cold starts don't matter here — nobody's waiting
on the response); long-lived service (this doesn't need to run continuously, only on the events
that touch the audited files); hosted Claude Managed Agents (beta, not currently ZDR- or
HIPAA-BAA-eligible, and no benefit for a job this small and already Read-only).

## 2. How is it triggered, and why that trigger?

**Decision:** a webhook — GitHub's `pull_request` event (`opened`, `synchronize`, `reopened`),
path-filtered to `src/main/java/io/spring/api/**` and the DTO classes under
`src/main/java/io/spring/application/**`. `claude-code-review.yml` already has this `paths:`
filter written but commented out; this workflow turns it on.

**Reason:** the whole point of the check is catching a DTO-placement violation *before* merge —
feedback has to land on the PR that introduces it. The task tolerates GitHub Actions' own
free job-level retry but not hours of delay: a scheduled nightly sweep would let a real
violation sit on `main` for up to a day before anyone reads the report, which is exactly the
kind of drift 4.7 already found (3 of 10 controllers violating a rule that's supposed to cover
all of them).

**Rejected, with reasons:** schedule (lets drift sit, per above — this is not a hypothetical,
it already happened once); manual (nothing stops a solo maintainer from simply forgetting);
upstream pipeline step (there's no upstream step that naturally produces this trigger — PR-open
is the first real event); queue message (no bursty multi-tenant load — one repo, one audience).

## 3. What do you monitor, and what would tell you a run went wrong?

**Decision:** one structured JSON event per file (the script's real output shape — `file`,
`status`, `subtype`, `terminal_reason`, `num_turns`, `total_cost_usd`, `permission_denials`,
`review`), posted as PR comments the same way `claude-code-review.yml` already posts its
security-review comment; a run-level `summary.json` (`items`, `ok`, `failed_or_errored`,
`total_cost_usd`, `items_with_permission_denials` — already produced by the script's own
`main()`); and a rolling pass rate across the last N runs, since one green run alone doesn't
reveal drift.

**Signals and the number that makes someone act:**
- **Cost per PR run over ~$1.00** — the real full 10-file batch cost $0.2705 total; an
  incremental, PR-scoped run only re-checks changed files, so a run costing several times the
  full-repo batch signals a cost or behavior regression, not normal variance.
- **Any item with `status != "ok"`** — blocks the check rather than posting a silent advisory
  comment, because 4.7's own denial-trigger run proved a run can look like an ordinary pass
  unless `permission_denials` specifically is read.
- **`items_with_permission_denials > 0`** on any run that isn't the deliberate test case —
  pages the workflow owner directly rather than waiting for a periodic review.

**Who reads them:** the PR author sees the per-file comment inline; the repo owner (me, solo
maintainer on this project) reviews the run-level summary weekly, the same cadence as the other
Claude-driven checks already on this repo.

## 4. How does it fail?

**Infrastructure failure modes:**

| Failure | Response |
| --- | --- |
| API rate limit (429) | **Real gap, named honestly:** the script has no retry-with-backoff today. Production version needs it — retry with exponential backoff, and drop to a `--fallback-model` for that one item, not the whole run. |
| Token/budget exhaustion | Already handled: `max_budget_usd=0.50` per item hard-stops that item, and the script's `except` block reads the partial `ResultMessage` (cost/denials/review) instead of discarding it — a real bug caught and fixed during 4.7 — so a capped item is recorded `failed`, never silently `ok`. |
| Tool denial | Already handled and empirically proven: 4.7's real denial-trigger run captured both an `Edit` and a `Write` denial verbatim in `permission_denials`, and `items_with_permission_denials` is counted at the run level so a denial can't hide inside an otherwise-green summary. |
| Stale auth | The Action's `CLAUDE_CODE_OAUTH_TOKEN` (or `ANTHROPIC_API_KEY` for the SDK path) is refreshed via the platform's secrets store before retry, never read from a token file the script itself manages. |
| Partial output | A `review: null` result (exactly what the real denial case produced) is treated as a failed item, not a pass with an empty comment — already true of `status: "failed" if result_message.is_error else "ok"`. |
| Downstream dependency failure | **Real gap, named honestly:** the script doesn't check that `.claude/rules/rest-api-request-dtos.md` exists and parses before spending anything on a batch run. It should — fail loudly before the first `query()` call, not partway through. |

**AI-specific failure modes:**

| Failure | Response |
| --- | --- |
| Hallucinated references | The prompt only ever embeds one real file's actual on-disk content — the model can't invent a `Read` result. But the review *text* could still claim a violation that isn't really there. **Weakest part of this answer:** the only check today is what 4.7 actually did — manually re-reading the flagged files against `git diff`/real source before trusting a verdict. Not yet automated. |
| Prompt drift | Re-run two known fixtures (a real compliant file, `CommentsApi.java`, and a real violating file, `ArticleApi.java`) on every CI invocation, diff the verdict against the saved-good `headless-runs-2026-09/*.json`, alert on any flip. |
| Model regression | Pin the exact model string, not a `-latest` alias. Treat the 11 real per-item JSON results already in `headless-runs-2026-09/` as the regression baseline; a new model version runs against this workflow first and is compared to that baseline before rolling out anywhere else. |
| Silent context degradation | Already structurally prevented: every file gets its own independent `query()` call, no shared session across items, `max_turns=3` — context never accumulates across files long enough to degrade. The only within-call risk (one very large controller) shows up as a cost anomaly under the $0.50 cap, not a silent quality drop. |

## 5. What can it reach and spend?

**Tool surface:** `Read` only — no `Bash`, `Edit`, or `Write`. Already proven to hold under
pressure: 4.7's denial trigger asked the agent directly to edit a file, and both the `Edit` and
the `Write` fallback were denied.

**The two cap numbers, empirically sized:** `max_turns=3` per item (every real "ok" item in the
4.7 batch finished in 1 turn; 3 gives headroom without letting one file's review spiral) and
`max_budget_usd=0.50` per item (10–25x the real observed $0.02–$0.053 per item, generous
headroom for a larger controller file without capping so tight it flags normal variance as a
failure).

**One control left out, with reason:** a per-run sandboxed container beyond the GitHub Actions
runner's own ephemeral VM. Left out because the tool surface is Read-only with no write or
execute path — the ephemeral runner already can't leak state between runs or be used to reach
anything outside the checkout, so a bespoke container adds operational cost with no matching
risk reduction.

**Real gap named honestly, not papered over:** there is currently no *run-level* total budget
cap — nothing stops N files from each spending up to $0.50 if something goes systematically
wrong. This was already flagged in 4.7's own Evaluate section and is still unresolved here.

## 6. How do you roll back?

**Decision — two of the four mechanisms:**
- **A feature-flag kill switch:** a repository variable (e.g. `DTO_AUDIT_ENABLED`) checked at
  the top of the job step — the same pattern this course's own composite illustration names —
  so the check can be disabled with no redeploy if it starts blocking merges incorrectly.
- **Idempotent by design:** the job only reads and comments; rerunning it on the same PR/commit
  twice produces the same verdict with no side effect to undo.

**Not chosen, with reasons:** queue replay doesn't apply — there's no queue, and GitHub Actions
itself re-runs a failed job on request for free. Audit-log replay isn't needed either — nothing
destructive happens to replay; the structured per-file JSON already gives full traceability of
what a run said, without anything having been mutated.

---

## The open question I could not answer

Should a violation this audit finds **block the merge** (a required status check) or stay
**advisory** (a comment only, like `claude-code-review.yml`'s current security check)?

The real tension: the rule this audit enforces already has 3 known real violations sitting in
`main` right now (`ArticleApi.java`/`UpdateArticleParam`, `ArticlesApi.java`/`NewArticleParam`,
`CurrentUserApi.java`/`UpdateUserParam` — found in homework 4.7, predating this design). Turning
the check into a required, blocking status today would immediately fail any PR that so much as
touches those three files, for a violation nobody introduced in that PR. Advisory avoids that,
but is exactly the failure mode lesson 4.1 names for advisory checks generally — it informs but
never blocks, so there's no guarantee anyone acts on a real finding.

I don't have a clean answer for how to turn this on as a blocking check without either
grandfathering the 3 existing violations explicitly (a specific exception list someone has to
maintain) or fixing them first (out of scope for a workflow-*design* homework, not a coding
one).

---

## Evaluate

**Weakest of the six answers:** #4 (how does it fail) and #5 (spend) share the same honest gap
— no retry-with-backoff on rate limits, and no run-level total budget cap. Answering it properly
would mean actually building and load-testing the retry logic against a real rate-limited run,
not just designing it on paper.

**Concrete response vs. acknowledgment, per failure mode:** four of six infrastructure modes
have a concrete, already-proven response (budget exhaustion, tool denial, partial output, stale
auth); two are named gaps rather than acknowledgments dressed as answers (rate limit, downstream
dependency). All four AI-specific modes have a concrete mechanism except hallucinated
references, which is honestly still a manual check, not an automated one.

**Cost of getting it wrong:** a false "compliant" verdict lets a real DTO-placement violation
merge unnoticed — low cost per incident (a style/convention drift, not a security or data issue)
but compounding, the same way the 3 real violations from 4.7 already compounded silently for
multiple homeworks before anyone ran an audit. Monitoring above (the pass-rate trend, not just
per-run status) is what would catch that compounding before it grows, not the per-run check
alone.

**Could this ship Monday?** Not as designed. Missing before it could: the retry-with-backoff
logic (§4), a run-level budget cap (§5), and a resolved answer to the open question above —
without that last one, "ship" doesn't even have a defined merge behavior yet.

---

## Verify

The peer saw only this document's finished text and was asked to say back the deployment
topology and the trigger, each with its reason, and to name anything that felt underexplained on
a first read.

**Topology, played back correctly:** "A CI runner — specifically GitHub Actions, the same
platform the repo's existing `claude-code-review.yml` already runs on... the job is bounded (10
independent, short `query()` calls) and produces a discrete per-file artifact." Matches §1.

**Trigger, played back correctly:** "GitHub's `pull_request` webhook... the check's purpose is
to catch a violation before merge, so feedback must land on the PR that introduces it; a
scheduled sweep would let a violation sit on `main` for up to a day, which the doc says already
happened in practice." Matches §2.

Both answers came back correct with their reasons intact — per the homework's own pass
criterion, no rewrite is required. **One genuine critique surfaced anyway, kept rather than
dropped for being inconvenient:** the peer noted that §1's topology justification leans heavily
on "we already have this in the repo" (reuse/cost) rather than independently defending GitHub
Actions on the task's own properties, and that "produces a discrete artifact" reads as abstract
in §1 until the path-filter mechanics become concrete in §2. Neither breaks the pass — the
reasons still played back accurately — but it's a real weak spot in how self-contained §1 is on
its own, not manufactured for this section.
