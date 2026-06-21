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
Multi-module Gradle project (`:core`, `:tools`, `:app`), JDK 17.

- **JVM modules** (`:core`, `:tools`) build and test anywhere with a JDK — no Android SDK:
  `./gradlew testDebugUnitTest`
- **`:app`** is included only when an Android SDK is present (`ANDROID_HOME` or `local.properties`
  with `sdk.dir`). Build/install it from Android Studio or the CLI on a machine with the SDK.

### Phase 1: performance persona (Samsung S25 / One UI)
The first intent family changes the three animation scales so the phone feels faster. Those are
`Settings.Global` writes that need **`WRITE_SECURE_SETTINGS`** — a normal app can't hold it, so it
is granted once over adb (a permanent one-time step on the S25, which can't be a system app):

```bash
adb shell pm grant com.attune.app android.permission.WRITE_SECURE_SETTINGS
```

The in-app onboarding explains this in plain language and detects when it's been granted.

### Intent parser key
`ClaudeIntentParser` reads the Anthropic key from `BuildConfig.ANTHROPIC_API_KEY`, sourced from
`local.properties` (git-ignored) or the `ANTHROPIC_API_KEY` env var:

```
# local.properties (do NOT commit)
ANTHROPIC_API_KEY=sk-ant-...
```

The intent-eval harness uses the key as a CI secret: `./gradlew :core:intentEval`.

## ⚠️ Before you ship
**Do not ship the Anthropic API key inside the APK — it is extractable.** The `local.properties`→
`BuildConfig` path above is fine for this enthusiast spike only. Before any public/XDA release the
key MUST move behind a proxy/backend, or the app must let users supply their own key. (We do not
build the proxy in Phase 1 — this is the flag.)

## License
MIT (see `LICENSE`). If you later integrate into a GPL ROM base, you may prefer Apache-2.0 for the explicit patent grant — switch before that step.
