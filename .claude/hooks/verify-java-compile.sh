#!/usr/bin/env bash
# .claude/hooks/verify-java-compile.sh — PostToolUse on Edit|Write
# Closes the loop on a real failure (4.6, 2026-09): an Edit extracted LoginParam out of
# UsersApi.java but left a duplicate leftover class body behind, and the resulting
# diagnostics got dismissed as a known pre-existing JDK/IDE issue instead of checked.
# Whenever a Java source file changes, actually compile the project instead of trusting
# any diagnostic-dismissal reasoning — a real compiler run is a computational sensor,
# not a guess.
set -euo pipefail

command -v jq >/dev/null || exit 0

input="$(cat)"
file_path="$(printf '%s' "$input" | jq -r '.tool_input.file_path // ""')"

# Only fire for real Java source under src/**, skip everything else (docs, config, etc).
case "$file_path" in
  */src/main/java/*.java|*/src/test/java/*.java) ;;
  *) exit 0 ;;
esac

project_dir="${CLAUDE_PROJECT_DIR:-.}"
cd "$project_dir"

# This repo needs Java 11 for Gradle regardless of the system default (see CLAUDE.md).
if command -v /usr/libexec/java_home >/dev/null 2>&1; then
  JAVA_HOME="$(/usr/libexec/java_home -v 11 2>/dev/null || true)"
fi
export JAVA_HOME

output="$(./gradlew compileJava compileTestJava --console=plain -q 2>&1)" && exit 0

echo "Blocked: $file_path was edited but the project no longer compiles." >&2
echo "Do not attribute this to a pre-existing/unrelated issue without reading the error below:" >&2
echo "$output" >&2
exit 2
