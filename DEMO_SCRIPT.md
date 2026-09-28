# DroidFlow AI — 4-Minute Judge Demo Script

**Setup (before judges arrive):** run the BUILD_GUIDE first-run checklist, keep the
phone unlocked, DroidFlow on the Home screen, brightness up. Planner = Hybrid.
Gemini key = optional (mention which mode you're in).

---

## Beat 1 — The hook (20 sec)

> "Everyone here can automate their phone with Tasker or ADB scripts. But my
> mother can't. DroidFlow is an on-device agent that drives ANY app the way a
> person does — through the interface — using Android's accessibility layer,
> the same system built for users with disabilities. We turned it into a
> no-code automation layer for everyone."

Show the Home screen: one text box, one mic button. Nothing else.

## Beat 2 — Voice + Hindi (30 sec)

Tap 🎤 and speak (Hinglish works great):

> "RideNow aur SwiftRide ka airport ka cab fare compare karo, sasta wala kholo"

The recognized text fills the box. Tap **Run task**.

> "Intent parsing runs on Gemini — it returns a structured contract, not free
> text: which apps, which destination, and two hard stop-conditions:
> before_booking and before_payment. Even if the model hallucinates, the
> engine will not let it cross those lines."

(Task Confirmation screen shows the structured goal + risk badge + stop
conditions — point at them.)

Tap **Start task**.

## Beat 3 — THE HERO RUN (90–120 sec) — put the phone in their hands

The Live Agent View starts cycling **OBSERVE → PLAN → ACT → VERIFY** while the
phone visibly drives the two mock apps. Narrate only the key beats:

1. *RideNow opens, "Airport" types itself, Search rides taps itself.*

   > "Every action is semantic — it matched 'Where to?' in this app…"

2. *SwiftRide opens — different wording, different colors, same flow works.*

   > "…and 'Enter drop location' in this one. No coordinates, no recorded
   macros. If a developer renames a button tomorrow, the agent still finds it."

3. *Fare table fills live: RideNow Sedan ₹482, SwiftRide ₹519.*

   > "It read both fares, computed the ₹37 difference, and reopened the
   cheaper app on its own."

4. *RideNow booking screen opens → red SAFETY event → the payment boundary.*

   > "Now the part I care about most. The planner proposed clicking Pay.
   The safety gate — a deterministic rule layer, not the LLM — said NO.
   Nothing was booked, nothing was charged. The AI proposes; rules dispose."

The Result screen auto-appears: comparison table, savings, honest metrics
(events, actions, seconds).

> "Metrics are honest — if it failed, it says so. No fake success."

## Beat 4 — Chaos engineering (45 sec) — THE differentiator

> "Agents break on messy UIs. Watch this."

Open RideNow manually, **long-press its header** → perturbation sheet →
enable **Rename button** and **Popup on results**. Home. Run the task again
(this time from the suggestion chip, or say the English equivalent).

- The renamed search button ("Find your ride") is still found — semantic
  matching + alternate-wording retries.
- When the rating popup interrupts, the timeline shows the agent detecting it,
  tapping **Later**, and continuing.

> "Popup, slow loading, renamed labels, reordered lists — it re-observes,
  re-plans and recovers. That's the verify loop doing its job."

## Beat 5 — The other use cases (45 sec)

1. **Price lookup:** "Find the price of wireless headphones on MockMart" →
   searches, reads ₹1299, reports and STOPS — no cart actions.
2. **Form fill:** "Open the QuickForm registration form and fill my name and
   email" → fills fields, then deliberately pauses at Submit for confirmation.
   Point at the password field:

   > "It detected the password field and never touched it — sensitive fields
   are excluded from context and never typed into."

## Beat 6 — Architecture close (30 sec)

Show the Live Agent View / README architecture diagram:

> "Ten standardized actions, one orchestrator, a deterministic safety gate,
   and a planner that's swappable — Gemini when online, a scripted fallback
   when not. The demo literally cannot die from a network failure."

Final line:

> "Accessibility was designed to give everyone access to their phone.
   DroidFlow gives everyone *automation* of their phone. Same tech, new
   superpower."

---

## Q&A ammo (memorize 3–4)

| Question | Answer |
|---|---|
| "How is this different from Tasker/MacroDroid?" | Those need per-device rules you build by hand. DroidFlow takes a natural-language goal, generalizes across apps semantically, and verifies each step — no rules to write. |
| "What if the LLM hallucinates?" | The LLM only *proposes* one of 10 structured actions. Schema validation, target resolution (with ambiguity abstention) and the safety gate all run deterministically before anything executes. |
| "Privacy?" | Screen data is processed on-device; only a compact, redacted element list goes to Gemini, and only if you configure a key. OTP/PIN/card values are masked before any log or network call. |
| "Why mock apps?" | Deterministic, offline-safe demos + a controlled chaos lab (perturbations) to prove recovery. The UI Inspector shows the same stack reading real apps read-only. |
| "Where does it fail today?" | Heavy custom-drawn canvases expose little semantics; CAPTCHA/secure screens are invisible by design (FLAG_SECURE → null root — a feature, not a bug); latency on very long tasks. |
| "Business case?" | Cross-app price comparison, form automation for low-literacy users, QA regression on real devices, accessibility auditing — one engine, many front doors. |
| "Why not use the app's API?" | Users don't have APIs to their apps. UI-level automation works on anything with a screen — that's the whole point. |

## Recovery playbook (if something goes wrong live)

| Situation | Move |
|---|---|
| Gemini slow/failed | "Watch the fallback" — deterministic planner completes the task. Then: Settings → Planner → Deterministic script for the rest of the pitch. |
| Service disconnected mid-run | Cancel → toggle accessibility off/on → Run again. Say: "system-bound services, same as any a11y tool." |
| Voice fails | Type the task — "typed or spoken, same contract". |
| Wrong app state | Finish the mock app screen ("Back to home"), run again — the agent re-observes from scratch every cycle. |
