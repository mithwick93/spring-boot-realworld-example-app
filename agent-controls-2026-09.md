## Layered controls: git force-push and history/working-tree destruction

**Goal:** an agent running unsupervised (auto-accept on) must not be able to rewrite shared git history or destroy uncommitted work.

### Layer 1 — Provider (git branch protection, GitHub ruleset on `main`)
Rules: ruleset `main`, enforcement Active, targets `main`; blocks force-push; blocks branch deletion; requires a pull request before merging.
**Owns:** the only non-bypassable boundary — server-side, applies to every client and every credential, cannot be disabled by editing a local config file.

### Layer 2 — Permissions (`.claude/settings.local.json`)
```json
"permissions": {
  "allow": ["Bash(git status)", "Bash(git diff:*)", "Bash(git log:*)"],
  "ask": ["Bash(git commit:*)", "Bash(git push:*)"],
  "deny": ["Bash(git push --force:*)", "Bash(git reset --hard:*)", "Bash(git clean:*)"]
}
```
**Owns:** cheap, declarative prefix-matching on the tool input — no code runs, but it only recognizes the literal shapes it's written for.

**Dangerous shape it misses:** `deny: Bash(git push --force:*)` only matches commands that *start* with `git push --force`. `git push origin main --force` (flag at the end) and `git push -f` (short flag) both slip through the same deny rule.

### Layer 3 — Hook (`.claude/hooks/guard-git.sh`, PreToolUse on Bash)
```bash
#!/usr/bin/env bash
set -euo pipefail
cmd="$(cat | jq -r '.tool_input.command // ""')"
printf '%s' "$cmd" | grep -Eq '(^|[;&|[:space:]])git[[:space:]]' || exit 0
if printf '%s' "$cmd" | grep -Eq 'git[[:space:]]+push\b.*(--force(-with-lease)?\b|(^|[[:space:]])-f\b)'; then
  echo "Blocked: force push rewrites shared history — open a PR instead." >&2
  exit 2
fi
if printf '%s' "$cmd" | grep -Eq 'git[[:space:]]+(reset[[:space:]]+--hard|clean[[:space:]]+(-[a-z]*f|--force)|push[[:space:]]+.*--mirror)'; then
  echo "Blocked: destructive git operation (hard reset / clean -f / mirror push)." >&2
  exit 2
fi
exit 0
```
**Owns:** the only layer that reads command *semantics* — it catches force-push regardless of argument position (verified: leading, trailing, and short-flag forms all blocked, plus `--force-with-lease`), which is exactly what the permission prefix rule above cannot do.

### Evaluate
- **Which control owns each layer:** provider = branch protection (non-bypassable, server-side); permissions = cheap prefix denial; hook = semantic/positional inspection. Nothing here was misplaced into the hook that a plain prefix rule could have caught — the deny rules in Layer 2 handle the literal-prefix cases, and the hook exists specifically to cover the reordered/short-flag cases prefix rules can't express.
- **Dangerous shape permissions miss:** argument reordering and short flags (`git push origin main --force`, `git push -f`) — confirmed the hook catches both, plus `--force-with-lease`, in live testing.
- **Which control survives a fresh clone with no hooks installed:** only branch protection. The local hook and the local permission rules are both bypassable the moment `.claude/` doesn't travel with the clone (or auto-accept skips permission prompts); branch protection lives on the remote and applies regardless of what's on the machine pushing to it. That's why the hook is a tripwire for this machine, not the actual control.
