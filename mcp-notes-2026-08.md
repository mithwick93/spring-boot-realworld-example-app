# Homework 1.7 — MCP: JetBrains IDE server

Server: IntelliJ IDEA's bundled MCP Server (Settings → Tools → MCP Server), connected to
Claude Code for this project.

## `claude mcp list` output

Full real output below — everything above the last line is my pre-existing personal
`claude.ai` connector list (Slack, Notion, monday.com, etc.), unrelated to this exercise.
The line that matters for 1.7 is the last one:

```
claude.ai Google Drive: https://drivemcp.googleapis.com/mcp/v1 – ✓ Connected
claude.ai Slack: https://mcp.slack.com/mcp – ! Needs authentication
claude.ai Google Calendar: https://calendarmcp.googleapis.com/mcp/v1 – ✓ Connected
claude.ai Gmail: https://gmailmcp.googleapis.com/mcp/v1 – ✓ Connected
claude.ai Notion: https://mcp.notion.com/mcp – ! Needs authentication
claude.ai monday.com: https://mcp.monday.com/mcp – ! Needs authentication
claude.ai Linear: https://mcp.linear.app/mcp – ! Needs authentication
claude.ai Intercom: https://mcp.intercom.com/mcp – ! Needs authentication
claude.ai HubSpot: https://mcp.hubspot.com/anthropic – ! Needs authentication
claude.ai Figma: https://mcp.figma.com/mcp – ✓ Connected
claude.ai Canva: https://mcp.canva.com/mcp – ! Needs authentication
claude.ai Box: https://mcp.box.com – ! Needs authentication
claude.ai Atlassian: https://mcp.atlassian.com/v1/mcp – ✓ Connected
claude.ai Asana: https://mcp.asana.com/sse – ! Needs authentication
idea: http://127.0.0.1:64342/stream (HTTP) – ✓ Connected
```

## Real task

Asked Claude Code to use the `idea` MCP server's code-navigation tools (not grep/Read) to
find every real call site of `DuplicatedArticleValidator`, `DuplicatedUsernameValidator`,
`DuplicatedEmailValidator`, and `CustomizeExceptionHandler.handleConstraintViolation`, then
compare against `planning-depth-2026-08.md`'s Round 3 finding — where a plain grep for
`ConstraintViolationException` wrongly concluded that handler was dead code.

**What it found:** the three `Duplicated*Validator.isValid` methods each have exactly one
incoming call chain, entirely inside Hibernate Validator's engine
(`ConstraintTree.validateSingleConstraint` → ... → `MetaConstraint.doValidateConstraint`) —
none are ever called directly by application code, since Bean Validation dispatches them via
the `ConstraintValidator` interface based on the `@...Constraint` annotations on DTO fields.
`CustomizeExceptionHandler.handleConstraintViolation` came back as a bare leaf with zero
incoming calls in the call-hierarchy tool — the same blind spot grep had, for the same
underlying reason: `@ExceptionHandler` methods are dispatched by Spring's exception resolver
via reflection/annotation matching, not a compile-time call, so no call-graph tool (IDE or
otherwise) can show a "caller" here.

The question that actually resolves is "what throws `ConstraintViolationException` and does
it reach a controller" — answered by finding the `@Validated` service classes with `@Valid`
parameters (`UserService`, `ArticleCommandService`) and running the IDE's call-hierarchy tool
on their methods. That confirmed the doc's corrected Round 3 finding exactly:
`CurrentUserApi.updateProfile` builds a *new* `UpdateUserCommand` and passes it to
`UserService.updateUser(@Valid UpdateUserCommand command)`, making the AOP method-validation
interceptor the only place `@UpdateUserConstraint` is checked — a live path, tested by
`CurrentUserApiTest.should_get_error_if_email_exists_when_update_user_profile`
(verified this exists at `CurrentUserApiTest.java:121-145` — checked it myself, matches).

**What the IDE surfaced that the doc didn't:** `UserMutation.updateUser` is a *second* live
caller of `UserService.updateUser` that Round 3's write-up never mentions — it only discusses
the REST path. Unlike `UserMutation.createUser` (same file), which explicitly catches
`ConstraintViolationException` and routes it through
`GraphQLCustomizeExceptionHandler.getErrorsAsData(cve)`, `updateUser` has no try/catch at all
(confirmed directly in `UserMutation.java` — `createUser` has the catch block, `updateUser`
doesn't). Checking `GraphQLCustomizeExceptionHandler` shows it's also registered as a global
DGS `DataFetcherExceptionHandler` (`@Component`, `onException` handles
`ConstraintViolationException` at line 41), so the uncaught exception on the `updateUser`
path still gets handled — just at the engine level, producing a different response shape
(top-level GraphQL error) than `createUser`'s explicit catch (error embedded in `data`). Not
a bug, but a real asymmetry that grep would never surface, since neither the class name nor
the exception type is mentioned in `updateUser` at all.

**Bottom line:** IDE call-hierarchy has the *same* blind spot as grep for framework-dispatched
methods — neither shows "callers" for `@ExceptionHandler` methods or `ConstraintValidator.isValid`.
Its advantage is one level removed: precisely resolving the `@Validated`/`@Valid` chains that
*cause* the exception, across both REST and GraphQL adapters, which needs semantic resolution
rather than text-matching on the exception's class name — exactly the class of miss the doc's
own Round 3 self-correction had already identified once, on the REST side only.

## Evaluate

- **What changed about my workflow with the MCP connection in place?** I stopped
  grep-first for "who calls this" questions. The IDE's call hierarchy resolves overloads,
  interface dispatch, and cross-file references I'd otherwise have to chase by hand — but it
  has the exact same annotation-dispatch blind spot grep has, so it's not a strictly better
  tool, just a different set of blind spots.
- **What new failure modes appeared?** It made 14 separate MCP tool calls and took a few
  minutes — noticeably slower than a grep pass would have been for the same question. Framework-
  dispatched methods (`@ExceptionHandler`, `ConstraintValidator.isValid`) come back as
  false-negative "zero callers," which reads like "dead code" if you don't already know to
  distrust that specific shape of result — the exact trap Round 3 documented for grep.
- **Was the setup friction worth the capability gained? Would I keep it on for the team?**
  Yes. IntelliJ IDEA is used company-wide here, so every engineer already has the same
  indexed project open for other reasons — connecting Claude Code to it costs one checkbox
  in Settings → Tools → MCP Server, not a new tool anyone has to adopt. That changes the
  calculus versus a one-off personal MCP server: the setup cost is shared across the whole
  team for free, while the capability (IDE-accurate call hierarchy instead of grep) scales
  with how much of the day is already spent in IntelliJ. I'd keep it on.

