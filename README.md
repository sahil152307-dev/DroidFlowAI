# DroidFlow AI — Hackathon Prototype (Final)

**Turn touch into tasks.** An Android agentic assistant that converts a natural-language
goal into state-aware UI actions — observing the screen through Android's accessibility
system, planning one action at a time, verifying every result, recovering from unexpected
UI states, and stopping whenever confidence or safety requirements are not met.

> Built on the same Android accessibility technology that empowers users with
> disabilities — turned into a **no-code automation layer for everyone**. No per-app
> integrations required from target apps.

---

## What this build is

The **complete prototype**: every phase of the TRD is implemented and wired together.

| Phase | Deliverable | Status |
|---|---|---|
| 0 | Gradle scaffold, manifests, theme, navigation, module structure | ✅ |
| 1 | 6 UI screens + on-device voice input (English, Hindi, Marathi, Gujarati, Bengali, Tamil, Telugu, Kannada) + localized strings | ✅ |
| 1b | 4 mock demo apps with a live perturbation system | ✅ |
| 2 | AccessibilityService perception: NodeExtractor, NodeMatcher, GestureDispatcher, ScreenStateDetector + UI Inspector | ✅ |
| 3 | ActionExecutor: all 10 standardized actions, label→field association, foreground-aware launch | ✅ |
| 4 | VerificationEngine (per-action expected-state checks with polling) + retry/replan + step budget | ✅ |
| 5 | Deterministic scripted planner driving the REAL loop (the no-AI demo backbone) | ✅ |
| 6 | Gemini intent engine + Gemini action planner with schema validation + auto-fallback | ✅ |
| 7 | SafetyGate (ALLOW/CONFIRM/DENY) + SensitiveDataFilter (redaction before logs/model) | ✅ |
| 8 | Live Agent View (real run), result metrics, JSONL session logs, demo kit docs | ✅ |

## The agent loop (what actually runs)

```
        ┌──────────────── user task (text or voice, Multi-language / Regional Indian) ──────┐
        │                                                                                   │
        ▼                                                                                   │
  IntentEngine (Gemini, fallback: keywords)  →  structured Task contract                   │
        │                                                                                   │
        ▼                                                                                   │
  ┌── AgentEngine loop (max 20 steps) ────────────────────────────────────────────────┐    │
  │  OBSERVE   fresh a11y snapshot → compact UIState (≤140 nodes, system windows     │    │
  │            filtered, secure screens yield no root)                                │    │
  │  PLAN      ScriptedPlanner (deterministic) or GeminiPlanner (LLM) → ONE action   │    │
  │  SAFETY    SafetyGate BEFORE resolution: ALLOW / CONFIRM (pause+ask) / DENY      │    │
  │  RESOLVE   NodeMatcher semantic scoring + ambiguity abstain + label→field        │    │
  │            association. No coordinates are ever stored.                          │    │
  │  ACT       ActionExecutor: node actions first, gesture fallback only for the     │    │
  │            foreground app                                                         │    │
  │  VERIFY    per-action expected-state polling (typed text present / screen        │    │
  │            changed / app foreground / price extracted)                           │    │
  │  RECOVER   failed action → replan with alternate wording; popup → dismiss;       │    │
  │            3 consecutive failures → honest stop                                  │    │
  └───────────────────────────────────────────────────────────────────────────────────┘    │
        │                                                                                   │
        ▼                                                                                   │
  TaskOutcome: fares, chosen app, savings, honest metrics + JSONL log per task             │
```

**Core engineering rule (TRD §45):** the LLM never touches Android. It only *proposes*
one of 10 structured actions; schema validation, target resolution, the safety gate and
the executor decide what actually happens.

## The 10 standardized actions

`CLICK · TYPE · SCROLL · SWIPE · BACK · OPEN_APP · WAIT · READ · ASK_USER · STOP`

## Safety layer

- **DENY (hard stop):** any Pay/Payment/Checkout click while the task carries a
  `before_payment` stop-condition; any TYPE into password/OTP/PIN/CVV fields.
- **CONFIRM (pause + ask):** Book / Submit / Register / Purchase / Sign-in — the agent
  pulls itself to the foreground and waits for a human tap.
- **Redaction:** OTP/PIN/card patterns are masked before anything is logged or sent to
  Gemini. Password fields never enter the model's context.
- **Budget:** step limit (default 20), per-action timeouts, 3-failure circuit breaker.
- **Foreground guard:** gesture fallback only fires when the node's own app is
  foreground — a stale node can never tap a different app.

## Demo apps (separate APK: `:demoapps`)

| App | Purpose | Safety boundary |
|---|---|---|
| **RideNow** (green) | cab booking, hint "Where to?", "Search rides" | Book Ride → Pay ₹482 |
| **SwiftRide** (purple) | different wording, "Enter drop location", "Find rides" | Book Ride → Pay ₹519 |
| **MockMart** | live-filtering product search | Add to cart / Buy Now → Pay |
| **QuickForm** | registration form with a marked password field | Submit Registration |

Each app has its own launcher icon and task affinity (real app-switching in Recents).
Long-press the app header to open the **perturbation sheet**: rename buttons, inject a
popup, slow the results, shift the layout — then watch the agent adapt (semantic
matching, popup dismissal, polling-based verify).

## Planner modes (Settings)

- **Hybrid (default):** deterministic script for the three known demo goals, Gemini for
  free-form tasks.
- **AI-first:** Gemini plans every step; on 2 invalid replies it degrades to the script.
- **Deterministic script:** zero network, the bulletproof fallback.

Without a Gemini key the app runs fully in deterministic mode — the demo can never die
because of the network.

## Project layout

```
app/       com.droidflow
             accessibility/  service, NodeExtractor, NodeMatcher, gestures, screen detector
             agent/          AgentEngine (orchestrator), ScriptedPlanner, GeminiPlanner,
                             IntentEngine, GeminiClient, Planner contract
             execution/      ActionExecutor, AppController
             safety/         SafetyGate, SensitiveDataFilter
             perception → see accessibility/ (TRD §6 compact UIState)
             logging/        AgentLogger (JSONL per task, redacted)
             models/         Task, AgentAction, UIState, ExecutionResult, TaskOutcome
             ui/             7 screens (Live Agent View = AgentExecutionScreen)
demoapps/  4 mock apps + perturbation system
```

## Docs

- **BUILD_GUIDE.md** — Android Studio setup, both APKs, first-run checklist,
  Android 13+ restricted-settings workaround, troubleshooting.
- **DEMO_SCRIPT.md** — the 4-minute judge run, line-by-line, plus Q&A ammo.

## Privacy stance 

- Screen context is processed on-device; only the compact, redacted element list is sent
  to Gemini — and only when a key is configured.
- Keys stay in app-private DataStore. Nothing is stored in the cloud.
- Every action is logged locally (JSONL) — auditable, shareable.
