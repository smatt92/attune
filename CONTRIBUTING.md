# Contributing to Attune

Attune is small on purpose. The architecture gives everyone a clean surface to own.

## Pick your surface
| You are a… | You can own |
|---|---|
| **Designer** | Intent vocabularies & flows, the confirm/consent sheet, onboarding, the "intent recipe" library |
| **ROM / platform dev** | New `ConfigTool`s (one per capability), new base integrations, AppFunctions/accessibility plumbing |
| **AI / ML** | The `IntentParser`, prompt/model layer, a local-model option, the eval harness |
| **Writer** | Docs, guides, the recipe catalog |

## Add a ConfigTool (most common first PR)
1. Implement `ConfigTool` in `tools/` — one capability, reversible, **no LLM calls**.
2. Add a unit test proving `apply()` then `revert()` against `FakeSettings`.
3. If it writes a real setting, add an instrumented test under `androidTest`.
4. Register it and add a `ToolDescriptor` so the parser knows it exists.

## Add an intent family
1. Add golden cases to `core/src/test/resources/intents/<family>.json`.
2. Make sure the listed tools exist.
3. Run `./gradlew :core:intentEval` and check the pass-rate.

## Non-negotiables (PRs that break these will be asked to change)
- Nothing applies silently — the plan is always shown first.
- Every change is reversible.
- No screen-scraping / vision agents.
- `core` stays free of Android framework imports.

## Scope — what Attune is NOT
- Not a task-automation agent ("book me a cab"). That's the platform's lane.
- Not a ROM. (May become a ROM-integrated build later; not the starting point.)
- Not "AI that does things for you" — it's "AI that helps you *configure* your device, with consent."

Look for issues labelled `good first issue`. Be friendly; scope is enforced but kindly.
