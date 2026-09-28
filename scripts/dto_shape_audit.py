#!/usr/bin/env python3
"""Headless batch agent for homework 4.7 (Stand up a headless agent).

Reviews every REST controller under src/main/java/io/spring/api/ against the
DTO-shape convention documented in .claude/rules/rest-api-request-dtos.md
(written for homework 4.5), using the Claude Agent SDK in headless mode --
no interactive session. This code discovers the files, loops over them,
enforces a turn cap and a dollar cap per item, and writes structured
per-item output; the model only ever reviews one file's content at a time,
embedded directly in its prompt.

Usage:
    python3 scripts/dto_shape_audit.py
        Reviews every *.java file under the API directory. Writes one JSON
        file per item plus a summary.json into headless-runs-YYYY-MM/.

    python3 scripts/dto_shape_audit.py --trigger-denial
        Also runs one extra item that asks the agent to fix violations by
        editing the file directly. Edit is not in this script's allowed
        tools, and permission_mode="dontAsk" denies it outright instead of
        prompting -- the deliberate failure case this homework asks for.
        The run can still finish looking like a normal success; the
        permission_denials field on the result is the only record of the
        denial, which is exactly the failure mode being practiced against.

Every item gets its own query() call with max_turns and max_budget_usd set.
A run that hits either cap is recorded as a failed item (via the result's
is_error/subtype/terminal_reason), never silently treated as an empty or
successful result.
"""
from __future__ import annotations

import argparse
import asyncio
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

from claude_agent_sdk import ClaudeAgentOptions, ResultMessage, query

REPO_ROOT = Path(__file__).resolve().parent.parent
RULE_PATH = REPO_ROOT / ".claude/rules/rest-api-request-dtos.md"
API_DIR = REPO_ROOT / "src/main/java/io/spring/api"

MAX_TURNS = 3
MAX_BUDGET_USD = 0.50


def rule_text() -> str:
    raw = RULE_PATH.read_text()
    # Strip the YAML frontmatter (--- ... ---) so only the rule body goes
    # into the prompt.
    parts = raw.split("---", 2)
    return parts[2].strip() if len(parts) == 3 else raw.strip()


def build_prompt(java_file: Path) -> str:
    body = java_file.read_text()
    task = (
        'Reply with exactly "COMPLIANT" if the file has no request DTO '
        "or fully follows the convention. Otherwise list each violation "
        "as one line, naming the exact rule it breaks. Do not edit "
        "anything -- report only."
    )
    return (
        "Here is a REST-API request-DTO convention this repo enforces:\n\n"
        f"{rule_text()}\n\n"
        f"Here is {java_file.relative_to(REPO_ROOT)}:\n\n"
        f"```java\n{body}\n```\n\n"
        f"Check this file's request DTO(s) against the convention above. {task}"
    )


def build_denial_trigger_prompt(java_file: Path) -> str:
    # Deliberately short and directive (no embedded file content, no review
    # task) so the agent's first move is the tool call itself, not several
    # turns of reasoning about a review task first. An earlier version of
    # this prompt asked the agent to "review, then fix" -- live runs showed
    # it burned its entire turn budget on the review/reasoning half and
    # never reached an actual Edit attempt, even at 2x the normal turn cap.
    rel = java_file.relative_to(REPO_ROOT)
    return (
        f"Add the line `// audit: reviewed` as the first line of {rel}. "
        "Edit the file directly -- do not just describe the change."
    )


async def review_one(java_file: Path, trigger_denial: bool = False) -> dict:
    options = ClaudeAgentOptions(
        allowed_tools=["Read"],
        permission_mode="dontAsk",
        max_turns=MAX_TURNS,
        max_budget_usd=MAX_BUDGET_USD,
    )
    prompt = (
        build_denial_trigger_prompt(java_file) if trigger_denial else build_prompt(java_file)
    )
    result_message: ResultMessage | None = None
    try:
        async for message in query(prompt=prompt, options=options):
            if isinstance(message, ResultMessage):
                result_message = message
    except Exception as exc:  # noqa: BLE001 -- a run that raises after hitting
        # its turn/budget cap (or any other SDK-level failure) must be
        # recorded as a failed item, never dropped or read as empty. The SDK
        # yields the ResultMessage *before* raising, so if one was captured,
        # read its real cost/denial/review data instead of discarding it --
        # an earlier version of this script threw that data away here.
        partial = {}
        if result_message is not None:
            denials = result_message.permission_denials or []
            partial = {
                "subtype": result_message.subtype,
                "terminal_reason": result_message.terminal_reason,
                "num_turns": result_message.num_turns,
                "total_cost_usd": result_message.total_cost_usd,
                "permission_denials": [repr(d) for d in denials],
                "review": result_message.result,
            }
        return {
            "file": str(java_file.relative_to(REPO_ROOT)),
            "status": "error",
            "error": repr(exc),
            "result_message_captured_before_raise": result_message is not None,
            **partial,
        }

    if result_message is None:
        return {
            "file": str(java_file.relative_to(REPO_ROOT)),
            "status": "error",
            "error": "no ResultMessage received from query()",
        }

    denials = result_message.permission_denials or []
    return {
        "file": str(java_file.relative_to(REPO_ROOT)),
        "status": "failed" if result_message.is_error else "ok",
        "subtype": result_message.subtype,
        "terminal_reason": result_message.terminal_reason,
        "num_turns": result_message.num_turns,
        "total_cost_usd": result_message.total_cost_usd,
        "permission_denials": [repr(d) for d in denials],
        "review": result_message.result,
    }


async def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--trigger-denial",
        action="store_true",
        help="Also run one deliberate denied-tool failure case.",
    )
    parser.add_argument(
        "--denial-only",
        action="store_true",
        help="Skip the normal batch and only run the deliberate failure case "
             "(for re-running the failure trigger alone after a script fix, "
             "without re-spending on the full batch).",
    )
    args = parser.parse_args()

    stamp = datetime.now(timezone.utc).strftime("%Y-%m")
    output_dir = REPO_ROOT / f"headless-runs-{stamp}"
    output_dir.mkdir(parents=True, exist_ok=True)

    java_files = sorted(API_DIR.glob("*.java"))
    if not java_files:
        print(f"no .java files found under {API_DIR}", file=sys.stderr)
        sys.exit(1)

    results = []
    if not args.denial_only:
        for java_file in java_files:
            print(f"reviewing {java_file.name} ...", flush=True)
            result = await review_one(java_file)
            results.append(result)
            (output_dir / f"{java_file.stem}.json").write_text(json.dumps(result, indent=2))
            cost = result.get("total_cost_usd")
            cost_str = f"${cost:.4f}" if isinstance(cost, (int, float)) else "n/a"
            print(f"  -> {result['status']} (cost={cost_str})", flush=True)

    if args.trigger_denial or args.denial_only:
        target = java_files[0]
        print(
            f"\ndeliberately triggering a denied-tool failure on {target.name} ...",
            flush=True,
        )
        denial_result = await review_one(target, trigger_denial=True)
        denial_result["file"] = f"{denial_result['file']} (deliberate-denial trigger)"
        results.append(denial_result)
        (output_dir / f"{target.stem}-denial-trigger.json").write_text(
            json.dumps(denial_result, indent=2)
        )
        denials = denial_result.get("permission_denials") or []
        print(f"  -> {denial_result['status']}, permission_denials={denials}", flush=True)

    ok = sum(1 for r in results if r["status"] == "ok")
    failed_or_errored = sum(1 for r in results if r["status"] in ("failed", "error"))
    total_cost = sum(r.get("total_cost_usd") or 0 for r in results)
    items_with_denials = sum(1 for r in results if r.get("permission_denials"))
    summary = {
        "run_at": datetime.now(timezone.utc).isoformat(),
        "items": len(results),
        "ok": ok,
        "failed_or_errored": failed_or_errored,
        "total_cost_usd": round(total_cost, 4),
        "items_with_permission_denials": items_with_denials,
    }
    (output_dir / "summary.json").write_text(json.dumps(summary, indent=2))
    print("\n--- summary ---")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    asyncio.run(main())
