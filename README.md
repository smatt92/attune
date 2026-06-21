# Attune

**Describe your phone instead of configuring it.**

Attune is an AI-native configuration layer for Android. You say what you want your phone to *be* — "lock this down for travel," "make it last two days," "set it up for my parent" — and Attune reads the intent, proposes a concrete set of changes, shows you exactly what it will do, and applies them only after you approve.

> Working codename. Rename freely.

## Status
Pre-alpha / scaffolding. Phase 0–1 (see `docs/PROJECT-PLAN.md` and `docs/SPEC.md`).

## Why this exists
Custom Android already exposes enormous power — hundreds of toggles. It's organized for the engineer who added each switch, not the human who has to find it. Attune is not "more features." It makes the features that already exist *usable* through intent. That's an information-architecture problem wearing an AI coat.

## The one rule
**Nothing is ever applied silently.** The AI only ever *proposes* a plan. A deterministic, reversible tool layer does the applying — and only after you say yes.

## Architecture in one breath
`intent (text) → [LLM] → IntentPlan → [you approve] → [deterministic tools] → applied + revertible`

See `docs/SPEC.md` for the full design and `ARCHITECTURE.md` for the layer contracts.

## Repo layout
- `core/`  — pure-Kotlin contracts: intent model, tool interface, plan executor, parser interface. No Android deps; unit-testable on the JVM.
- `tools/` — concrete `ConfigTool` implementations (one per capability). Mockable settings backend.
- `app/`   — Android app (Jetpack Compose UI, the confirm/consent flow, the intent input).
- `docs/`  — spec, project plan.

## Build
This repo ships the **contracts and CI**, not a full Gradle/Android project yet — that gets generated against your local Android SDK (recommended: Claude Code, see `CLAUDE.md`). The Kotlin files here define the interfaces the implementation must satisfy.

## License
MIT (see `LICENSE`). If you later integrate into a GPL ROM base, you may prefer Apache-2.0 for the explicit patent grant — switch before that step.
