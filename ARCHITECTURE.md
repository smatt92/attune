# Architecture

```
            ┌─────────────┐   text intent
   user ───▶│ Intent input│──────────────┐
            └─────────────┘               ▼
                                 ┌───────────────────┐  available tools
                                 │  IntentParser     │◀───────────────── ToolRegistry
                                 │  (LLM / Claude)   │
                                 └─────────┬─────────┘
                                           │ IntentPlan (proposal)
                                           ▼
                                 ┌───────────────────┐
                                 │ Confirmation sheet│  ← user approves / edits / cancels
                                 └─────────┬─────────┘
                                           │ approved plan
                                           ▼
                                 ┌───────────────────┐   ┌──────────────┐
                                 │   PlanExecutor    │──▶│ ConfigTool(s)│──▶ Settings.{Secure|Global|System}
                                 │ (all-or-nothing,  │   │ deterministic│
                                 │  rollback)        │◀──│  reversible  │◀── ToolSnapshot (for revert)
                                 └───────────────────┘   └──────────────┘
```

## Modules
- **core** — `IntentPlan`, `ConfigAction`, `ConfigTool`, `ToolSnapshot`, `SettingsContext`,
  `IntentParser`, `PlanExecutor`. Pure Kotlin. Unit-tested on the JVM.
- **tools** — concrete `ConfigTool` implementations. Tested against the in-memory `FakeSettings`.
- **app** — Android app: Compose UI, the confirm/consent flow, the real `SettingsContext` impl
  (writes via `android.provider.Settings`), wiring to the Claude API.

## Why this is testable (and most "AI phone agents" aren't)
The reasoning (non-deterministic LLM) is isolated in one layer and *only produces data*. The
acting (deterministic tools) is isolated in another and is trivially mockable. So:
- the LLM layer is checked by an **eval harness** (golden intent → expected plan),
- the action layer is checked by **fast unit tests** with no device,
- only the thin "does it really write the setting" seam needs an **emulator/device**.

Contrast with screenshot-streaming agents, where reasoning and acting are fused and every test
needs a live device + a live model. We deliberately avoid that.
