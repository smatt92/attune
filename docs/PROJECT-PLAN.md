# Attune — Project Plan
*Working codename — rename freely (Cadence / Lumen / Phrase…). Naming is your strength; this is a placeholder.*

**Premise:** An AI-native configuration layer for Android. You *describe* what you want your phone to be; it configures itself. Intent in, configured device out.

---

## Verdict on the idea

Sound — in its sharpened form only. The version worth building is **AI as the designed interface to Android's own complexity** — *not* feature-aggregation (that's fork-merge engineering, the treadmill) and *not* task-automation (that's Google's platform land-grab plus a crowded open-source agent field). The open lane is the one the entire field is structurally bad at: making Android's *existing* power **usable** through intent. That's an information-architecture problem wearing an AI coat — your discipline, your thesis.

---

## The thesis (one paragraph)

Custom Android already exposes enormous power — hundreds of toggles across privacy, performance, display, gestures, networking. It's organized for the engineer who added each switch, not the human who has to find it. Attune replaces *configuring* with *describing*: you say "lock this down for travel," or "make it last two days on battery," or "set it up for my parent," and an AI reads the intent, proposes a concrete set of changes, shows you exactly what it will do, and — on approval — applies them through a deterministic tool layer. No screen-scraping, no silent changes, no guessing.

---

## Architecture (what makes it defensible *and* contributable)

Two clean layers — the same pattern you already ship in Crit/PrismKit (LLM for judgment, deterministic tools for action):

| Layer | Role | Why it matters |
|---|---|---|
| **Intent layer** (LLM) | Parse natural-language intent → a structured plan of named config actions | Where the "magic" lives; model is swappable (cloud or local) |
| **Tool layer** (deterministic) | Each setting is a typed, reversible "config tool" with known effects; applies only on approval | Reliability + trust; never wrong silently; this is the contributable surface |
| **Confirmation / consent** | Always shows the plan before applying; every change reversible | Keeps trust alive; the safety model platform agents conspicuously lack |

**Rails, not scraping.** Ride Android's declared-capability surfaces (Settings provider, AppFunctions, scoped accessibility within consent). Never stream screenshots to a vision model — too slow, too expensive, fails on non-English UIs.

---

## Operating principles (the load-bearing decisions)

1. **Inherit features, don't aggregate them.** Sit on one already-rich base (crDroid / LineageOS). You get the whole toggle menu for free; spend 100% of effort on the Attune layer.
2. **App / overlay first, ROM later (if ever).** Ship as an installable app on any supported ROM. ROM-integration is an *earned, optional* later step — never the starting point. This sidesteps the maintenance treadmill and the hardware/supply-chain wall entirely.
3. **Ride the platform's rails.** Build *on* AppFunctions/Intents, not against Google. Stay an app so you can pivot if the platform shifts under you.
4. **Community is downstream of a working artifact.** (Same shape as: prominence is downstream of clarity.) Build solo until there's something undeniable to walk into. You recruit to a working thing + a clear thesis — never to a pitch.
5. **Every gate ships a low-regret artifact.** Demo, Medium piece, portfolio proof, agentic-AI competence signal. Stop at any gate and you've still gained something standalone.
6. **Open source from Phase 1.** Public repo early; MIT/Apache for the Attune layer; comply with base ROM licenses (GPL) only if/when you integrate.

---

## Phase plan

| Phase | Who | ~Effort | Output | Gate to pass |
|---|---|---|---|---|
| **0 — Sharpen** | Solo | ~1 wk | Thesis + first intent slice chosen | Is the first slice *obviously* magical? |
| **1 — Spike** | Solo | ~2–3 wks | One end-to-end intent→config flow that works | Does it beat hunting toggles? |
| **2 — Designed v1** | Solo (core) | ~3–4 wks | Polished app + demo + repo | Demo + writeup ready to publish? |
| **3 — Go public** | Solo → open | launch | XDA thread + Medium + open repo | Real interest / first contributors? |
| **4 — Grow** | Core + contributors | ongoing | 3–5 engaged collaborators + intent library | Clear contributable surface + pull? |
| **5 — ROM (optional)** | Core + base maintainer | only if earned | crDroid/LineageOS-integrated build | Real demand + a willing maintainer? |

**Phase 0 — Sharpen (solo).** Lock the codename and the one-paragraph thesis. Pick the *single* first intent to own — one high-value domain where the before/after is undeniable (privacy lockdown, battery persona, "set up for my parent"). Don't build breadth; build one magical flow.

**Phase 1 — Technical spike (solo).** Smallest thing that proves intent→config is reliable: LLM parses one intent → 3–5 deterministic config actions → confirm → apply, on a spare/rooted device running crDroid/LineageOS. Make-or-break. If even one flow doesn't feel impeccable, rethink before going wider.

**Phase 2 — Designed v1 (solo).** Wrap the spike in real design — your edge. Onboarding, the intent input, the "here's what I'll change, approve?" pattern, coherent IA for a small but real set of intents. Ship an installable build. Record a tight demo. *This is the thing you show.*

**Phase 3 — Go public + seed community (the kickoff).** Publish simultaneously: an XDA thread (Development / AI section), a Medium piece dead-center on your thesis ("I made my phone configure itself by talking to it"), and the open GitHub repo. The artifact + writeup *are* the recruiting magnet. Community starts **here**, not earlier.

**Phase 4 — Engage contributors.** Structured below.

**Phase 5 — ROM integration (optional, earned).** Only if there's pull *and* a willing base maintainer. Staying an app may well be the better permanent answer — broader reach, no per-device treadmill.

---

## Community strategy

### When (staging)
- **Phases 0–2: doors closed.** Solo, or you + one trusted collaborator. "Alone first" is a *feature*: it keeps the vision coherent and the artifact sharp before other hands shape it.
- **Phase 3: doors open at launch.** The working app + writeup + repo is the invitation.
- **Phase 4: grow deliberately.** Don't chase headcount; chase *fit*.

### Who (the three tribes — where like-minded people already are)
| Tribe | Where to find them | What pulls them in |
|---|---|---|
| ROM & Android-modding devs | XDA Development subforums, LineageOS/crDroid Matrix & Telegram, r/androiddev, r/LineageOS, GitHub topic tags | A genuinely new layer on a base they already run |
| Mobile AI-agent builders | mobile-use / Droidrun / Open-AutoGLM Discords & GitHub, the AppFunctions dev space | The intent→deterministic-tool architecture (cleaner than scraping) |
| AI-curious designers | Your Medium / Bluesky / Mastodon following, design-systems circles, IxDF Hyderabad | The "IA of AI" framing — a design problem most engineers can't see |

### How (engagement mechanics)
Match contribution surface to who shows up — the architecture makes this clean:

| Contributor type | Owns |
|---|---|
| Designers | Intent vocabularies, flows, confirm/consent patterns, onboarding |
| ROM / platform devs | New base integrations, new deterministic config *tools* (one per capability), accessibility/AppFunctions plumbing |
| AI / ML folks | Intent parsing, prompt/model layer, local-model option, an eval harness ("did intent map correctly?") |
| Writers / community | Docs, an "intent recipe" library, guides |

**Infra to stand up at Phase 3 (not before):**
- GitHub repo with `good first issue` labels + a public roadmap (Projects board)
- `CONTRIBUTING.md` — how to add an intent / a config tool / a base
- A sharp `ARCHITECTURE.md` — doubles as your recruiting doc *and* a thesis artifact
- One chat home: **Matrix** (aligned with the FOSS/privacy ROM crowd) or **Discord** (bigger reach) — pick one, accept the tradeoff
- A one-screen scope statement (what Attune is and isn't) so PRs stay coherent

### Honest community reality
- Most FOSS projects get *users* + occasional drive-by PRs, not co-maintainers. **Early target: 3–5 genuinely engaged people, not "a community."** Quality over headcount.
- Community carries its own cost: triage, review, governance. Opening up trades solo velocity for reach + durability. Do it when there's a clear contributable surface *and* real pull — not to feel less alone.
- Govern lightly at first: you're the decision-maker, scope is explicit, PRs get friendly-but-decisive responses.

---

## Risks & honest failure modes

| Risk | Mitigation |
|---|---|
| Google absorbs this at the platform layer (the "intelligence system") | Differentiate on *design/experience* they won't do; stay an app so you can pivot; build on their rails, not against them |
| One wrong or silent change kills trust | Confirm-before-apply, deterministic reversible tools, never a silent write |
| Your bandwidth (job search, certs, Crit, Medium constellation) | Gated phases; each gate ships a standalone low-regret artifact; stop cleanly anytime |
| Community never materializes | Fine — it's a strong *solo* portfolio + writing piece regardless; don't build community infra before demand |

---

## This week (concrete first moves)
- [ ] Lock codename + one-paragraph thesis
- [ ] Choose the **first intent slice** — one obviously-magical domain (recommend: privacy lockdown *or* battery persona)
- [ ] Pick base + test device (crDroid/LineageOS on a spare/rooted device, or a GSI)
- [ ] Define the spike boundary: LLM intent parse → 3–5 deterministic config actions → confirm → apply
- [ ] Create private repo + skeleton: `ARCHITECTURE.md` stub + the intent→tool interface
- [ ] Draft the Medium angle now — writing the thesis clarifies the build

---

*Low-regret by design: even if Attune never becomes a community project, every phase outputs a demo, an essay, a portfolio piece, and concrete agentic-AI proof-of-competence. The downside is bounded; the upside compounds.*
