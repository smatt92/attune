# Attune — Technical Spec

*Companion to `PROJECT-PLAN.md`. The plan says **why/when**; this says **what/how**.*

---

## 1. What we're building (v1)

An Android app that lets you **describe** a desired phone state in plain language and have it **safely applied** to real system settings, after you approve a previewed plan. v1 ships **one** intent family done impeccably (recommended: **privacy lockdown**), not breadth.

Success for v1 = a stranger on XDA flashes/installs it, says *"set this up for travel,"* sees a clear list of changes, taps approve, and the phone is measurably more locked down — and they can undo it in one tap.

---

## 2. Goals / non-goals

**Goals**
- Natural-language intent → a **previewed, reversible** set of settings changes.
- An architecture where the LLM only *proposes* and deterministic code *applies*.
- One genuinely magical intent family, fully designed.
- Reliability and trust as first-class features (confirm-before-apply, full undo).

**Non-goals (v1, on purpose)**
- Not a task-automation agent ("book a cab," "reply to texts"). That's Google's platform lane + a crowded OSS field; we don't compete there.
- Not a ROM. App-first. ROM integration is a later, *earned* option.
- Not running large models on-device. Intent parsing is a cloud call (swappable later).
- Not broad settings coverage. One family, deep.

---

## 3. Architecture

Three layers, hard boundaries (see `ARCHITECTURE.md` for the diagram):

| Layer | Module | Determinism | Tested by |
|---|---|---|---|
| Intent → plan | `core` `IntentParser` (LLM/Claude) | Non-deterministic | **Eval harness** (golden intent → expected plan) |
| Plan → applied | `tools` `ConfigTool` + `core` `PlanExecutor` | Deterministic | **Unit tests** (mock settings) + **instrumented** (emulator) |
| Approve / undo | `app` (Compose UI) | Deterministic | UI/instrumented tests |

**Data flow:** `intent (text) → IntentParser → IntentPlan (proposal) → [user approves] → PlanExecutor → ConfigTools → Settings.{Secure|Global|System}` — with a `ToolSnapshot` captured per tool for one-tap revert.

**The key design idea:** isolate the unreliable part (the model) so it only emits *data*, and make the acting part (tools) deterministic and mockable. That's what makes the whole thing testable — and it's the opposite of screenshot-streaming "phone agents," where reasoning and acting are fused and every test needs a live device + live model (and which a developer documented this year as too slow, too costly, and weak on non-English UIs).

### Core contracts (already scaffolded)
- `IntentPlan` / `ConfigAction` — the proposal.
- `ConfigTool` — one capability; `preview()`, `snapshot()`, `apply()`, `revert()`; **never calls an LLM**.
- `SettingsContext` — abstraction over `android.provider.Settings`; in-memory fake in tests.
- `IntentParser` — text + available tools → `IntentPlan`.
- `PlanExecutor` — applies a plan all-or-nothing with rollback; can revert.

---

## 4. The first intent, fully specified — "privacy lockdown"

Why this one: legible (everyone gets "lock it down"), emotionally resonant, and it lands straight in the de-Google/privacy crowd already on XDA. The before/after is obvious, which is what a demo needs.

**Phrasings to support (intent family):** "lock this down for travel," "make this private," "stop apps tracking me," "I'm crossing a border," "hide my stuff."

**Candidate tool set (each a `ConfigTool`):**
| Tool id | Setting it touches | Effect |
|---|---|---|
| `privacy.ble_scan_always` | `Global ble_scan_always_enabled` | Stop BLE scanning while BT is off *(scaffolded as the example tool)* |
| `privacy.wifi_scan_always` | `Global wifi_scan_always_enabled` | Stop Wi-Fi scanning while Wi-Fi is off |
| `privacy.location_mode` | location providers | Drop to device-only / off |
| `privacy.lockscreen_notifications` | `Secure lock_screen_show_notifications` | Hide notification contents on lock screen |
| `privacy.adb_off` | `Global adb_enabled` | Disable USB debugging |
| `privacy.find_device` | per-OEM | Note: many are app-level, not Settings — flag as out-of-scope or app-action |

> Exact keys/behaviour vary by base ROM and Android version — verify per base on the test device. The instrumented test layer is precisely where these get pinned down.

**Flow:**
1. User types/says intent.
2. `IntentParser` returns an `IntentPlan` of `ConfigAction`s, each with a `humanLabel` from the tool's `preview()`.
3. **Confirmation sheet** lists every change in plain language; user can toggle individual actions off, then approve.
4. `PlanExecutor.apply()` runs them; on any failure, auto-rollback.
5. A persistent "Undo this change set" affordance reverts via the stored snapshots.

**Edge cases to design for:** missing permission (see §6), a setting already at target (no-op, show as "already set"), partial OEM support (gracefully skip + warn), conflicting actions (parser must not emit contradictions — covered by eval cases).

---

## 5. Tech stack

| Concern | Choice | Why |
|---|---|---|
| Language | Kotlin, JDK 17 | Android standard |
| UI | Jetpack Compose | Fast to build the confirm/onboarding flows; your design edge lives here |
| Intent model | **Claude API** (Messages) | Strong structured-output; ties into your ecosystem; model swappable behind `IntentParser` |
| Settings access | `android.provider.Settings` via `SettingsContext` | The real write path; abstracted for testing |
| Build | Gradle (multi-module: core / tools / app) | Keeps `core` Android-free for fast JVM tests |
| Unit tests | JUnit + (Robolectric where Android types leak) | Fast, no device |
| Instrumented | Espresso / UI Automator | Real settings on an emulator |
| CI | GitHub Actions | Workflows already scaffolded |

Structured output: prompt the model to return **only** JSON matching the `IntentPlan` shape (tool ids constrained to the provided `ToolDescriptor` list), parse defensively, reject any action whose `toolId` isn't registered.

---

## 6. The permission constraint (read this — it shapes who can use v1)

Writing most of these settings needs **`WRITE_SECURE_SETTINGS`**, which a normal app **cannot** hold. Three ways to get it:

| Path | How | Audience |
|---|---|---|
| **adb grant** (recommended for v1) | One-time `adb shell pm grant com.attune.app android.permission.WRITE_SECURE_SETTINGS` | XDA / enthusiasts comfortable with adb — **exactly your launch crowd** |
| **root** | Granted directly on rooted devices | Power users |
| **system/privileged app** | Bundled into a ROM (Phase 5) | No friction — but requires ROM integration |

This is not a bug; it's a feature for sequencing. The adb-grant requirement naturally scopes v1 to the enthusiast audience that XDA *is*, which is the right audience to launch to. The frictionless path (system app) is one more concrete reason the ROM-integrated version exists later. Be upfront about the one-time adb step in onboarding.

---

## 7. Testing strategy ("how will it test the builds?")

A four-tier pyramid, fast → slow:

**Tier 1 — Unit tests (JVM, every push).** Each `ConfigTool` against the in-memory `FakeSettings`: assert `apply()` writes the right key/value, `revert()` restores the prior value, no-ops are no-ops. The `PlanExecutor`: ordering, and **rollback on partial failure**. `core` has zero Android deps, so these run in seconds with `./gradlew testDebugUnitTest`.

**Tier 2 — Intent eval harness (scheduled / pre-release).** The LLM layer is non-deterministic, so test it like a model, not like code: a **golden corpus** of `intent → expected actions` (see `core/src/test/resources/intents/`). The harness runs the parser and asserts each plan *includes* the required actions; report a **pass-rate** and gate releases on a threshold. Costs API calls ⇒ runs nightly / on dispatch, not every commit (needs `ANTHROPIC_API_KEY` secret). This is how you keep "describe your phone" honest as you add intents.

**Tier 3 — Instrumented tests (emulator, on PR/main).** Prove the setting *actually* changes on a real Android system. Espresso/UI Automator + the real `SettingsContext` on an emulator. **Current best practice (verified 2026):** run on **`ubuntu-latest`** (now 2–3× faster and cheaper than macOS for this), **enable KVM** in the job, use `reactivecircus/android-emulator-runner@v2`, and **grant `WRITE_SECURE_SETTINGS` via adb in the script** before `./gradlew connectedCheck`. All of this is wired in `.github/workflows/ci.yml`. For a broader device/API matrix later, add **Firebase Test Lab** or **emulator.wtf**.

**Tier 4 — Manual smoke matrix (real hardware, per release).** The honest reality: settings keys and behaviours differ across base ROMs (LineageOS vs crDroid vs OEM). Keep a short manual checklist per supported base on real devices. Document which bases are "supported." This tier is where ROM-specific quirks surface that no emulator catches — and it's why "supported bases" is an explicit, small list, not "all of Android."

**The honest summary:** the architecture is what makes testing tractable. Because the model only emits data and the tools are deterministic, ~90% of correctness is covered by fast, device-free tests; only the thin "did it really write" seam needs an emulator, and only ROM-quirk coverage needs real hardware.

---

## 8. Build & dev workflow (and: should we use Claude Code?)

**Yes — this is close to an ideal Claude Code job.** It's greenfield, tightly specified, and front-loaded with exactly the boilerplate agents are good at (Gradle/Compose scaffolding, the repetitive `ConfigTool` + test pairs, wiring the Claude API, the eval harness). You already work in `CLAUDE.md`-driven repos, so the context handoff is natural. `CLAUDE.md` in this repo encodes the invariants and — importantly — the **division of labor**:

- **Claude Code owns the plumbing:** generate the actual Gradle/Compose project against your local Android SDK, implement tools + unit tests, wire the parser, keep CI green.
- **You own the judgment:** the intent vocabulary (this *is* the product), the confirm/onboarding UX (your edge), and which settings are safe to expose. Don't let these get auto-generated unreviewed.

Use **Android Studio** alongside for the emulator, device debugging, and Compose previews. (Its own agent mode exists, but Claude Code + `CLAUDE.md` is the through-line here.) For setup specifics, see the Claude Code docs: https://docs.claude.com/en/docs/claude-code/overview

**First build step on your machine:** open the repo in Claude Code and ask it to generate the multi-module Gradle/Compose project that satisfies the existing `core` contracts and makes `./gradlew testDebugUnitTest` pass. The contracts in this repo are the spec it builds against.

---

## 9. Repo layout
```
attune/
├── README.md            overview
├── ARCHITECTURE.md      layer diagram + invariants
├── CONTRIBUTING.md      contribution surfaces by skill
├── CLAUDE.md            Claude Code context + division of labor
├── LICENSE              MIT (consider Apache-2.0 before ROM integration)
├── core/                pure-Kotlin contracts (Android-free) + intent corpus + eval
├── tools/               ConfigTool implementations + unit tests
├── app/                 Compose app, real SettingsContext, confirm flow (Claude Code generates)
├── docs/                SPEC.md, PROJECT-PLAN.md
└── .github/workflows/   ci.yml (unit + emulator + eval), eval-schedule.yml
```

---

## 10. Open decisions (lock these in Phase 0)
1. **First intent family** — privacy lockdown (recommended) vs battery persona.
2. **Codename** — Attune is a placeholder.
3. **Base + test device** — which ROM on which spare/rooted device (or a GSI).
4. **Model routing** — cloud Claude only for now; design `IntentParser` so a local model can slot in later.
5. **Repo visibility** — public from the start (recruiting magnet) vs private until Phase 2 demo. Plan recommends solo/closed through Phase 2, public at Phase 3 launch.
