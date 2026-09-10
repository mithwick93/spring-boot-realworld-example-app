# Failure Patterns — Recognition

1. "Claude suggests the same fix repeatedly even though it doesn't work." — **Pattern mismatch** (Claude has anchored on a solution from a different context).
2. "Claude forgot what file we were working on." — **Context pollution** (the file information has been crowded out or summarized away).
3. "Claude implements the wrong feature despite clear instructions." — **Prompt ambiguity** (multiple interpretations were valid; Claude picked the wrong one).
4. "Claude confidently references an `Array.dedupe()` method that doesn't exist." — **Knowledge gap** (often manifests as hallucinated APIs or packages).
