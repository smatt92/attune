# CLAUDE.md — build context for Claude Code

You are helping build **Attune**: an AI layer that turns a natural-language intent into a
reviewed, reversible set of Android settings changes. Read `docs/SPEC.md` before large changes.

## The architecture is non-negotiable
Three layers, hard boundaries:
1. **Intent layer** (`core` `IntentParser`) — LLM turns text → `IntentPlan`. Side-effect free.
2. **Tool layer** (`tools` `ConfigTool`) — deterministic, reversible, one capability each.
   **Tools never call an LLM.**
3. **Executor + confirm** (`core` `PlanExecutor`, app UI) — applies only after user approval;
   all-or-nothing with rollback.

## Invariants you must preserve
- Nothing is ever applied silently. The plan is shown and approved first.
- Every `ConfigTool.apply()` is reversible via its returned `ToolSnapshot`.
- No screen-scraping / vision agents. Use Settings providers / AppFunctions / scoped accessibility.
- The tool layer must stay unit-testable against the in-memory `FakeSettings` (no device needed).

## Division of labor (important)
**You (Claude Code) own the plumbing:**
- Generate the Gradle/Compose project against the local Android SDK.
- Implement `ConfigTool`s (repetitive, well-specified) and their unit tests.
- Wire the `IntentParser` to the Claude API; implement the eval harness `:core:intentEval`.
- Keep CI green.

**The human owns judgment (do NOT auto-generate these without review):**
- The **intent vocabulary** — which phrasings map to which tools (this is the product).
- The **UX / IA** — the confirm/consent sheet, onboarding, the intent input.
- Which **settings are safe** to expose and their defaults.

## Testing expectations
- New `ConfigTool` ⇒ a unit test proving apply + revert against `FakeSettings`.
- New intent family ⇒ golden cases in `core/src/test/resources/intents/`.
- Anything that writes a real setting ⇒ an instrumented test under `androidTest`.

## Conventions
- Kotlin, JDK 17, Jetpack Compose for UI.
- Keep `core` free of Android framework imports (pure Kotlin → fast JVM tests).
