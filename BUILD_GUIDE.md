# DroidFlow AI — Build & Run Guide

Follow this top to bottom once, and the demo will work every time after that.
Estimated setup time: **10 minutes**.

---

## 1. Requirements

| Tool | Version |
|---|---|
| Android Studio | Hedgehog (2023.1.1) or newer |
| JDK | 17 (bundled with Android Studio — no setup needed) |
| Android SDK | API 34 (Studio auto-installs on first sync) |
| Device | **Real phone, Android 8.0+ (API 26)**, USB debugging enabled |
| Internet | Only if you want the Gemini AI planner (optional) |

> Emulators work too, but a real phone is the stronger demo. Google's emulator
> sometimes has a broken on-device speech recognizer; if voice input fails there,
> type the task instead.

## 2. Open and build

1. Unzip the project. In Android Studio: **File → Open** → select the `DroidFlow`
   folder (the one containing `settings.gradle.kts`).
2. Let Gradle sync finish (first sync downloads ~300 MB of dependencies).
   - If Studio complains about a missing Gradle wrapper, click
     **"Fix Gradle wrapper and re-import"** (the wrapper jar is included, so this
     should not appear).
3. The project has **two runnable configurations** — both must be installed:
   - `app` → DroidFlow AI (the assistant)
   - `demoapps` → RideNow, SwiftRide, MockMart, QuickForm (the target apps)

   Install both:
   - In the configuration dropdown pick **app** → Run ▶ (installs + opens DroidFlow)
   - Then pick **demoapps** → Run ▶ (installs + opens RideNow)

   Or from a terminal in the project root:

   ```bash
   ./gradlew :app:installDebug :demoapps:installDebug
   ```

## 3. First-run checklist (DO THIS BEFORE THE DEMO)

Do these 6 steps once, then the phone is demo-ready forever:

1. **Enable the accessibility service**
   - Phone Settings → **Accessibility → Installed apps → DroidFlow → On**
   - Grant the permission dialog.
   - In DroidFlow, the Onboarding screen shows a live status — it must say ON.
2. **⚠ Android 13+ "Restricted settings"** (only if you installed via
   sideload/APK share instead of Android Studio/adb):
   - If the toggle is greyed out with "Restricted setting":
     Phone Settings → **Apps → DroidFlow → ⋮ (top-right) → Allow restricted
     settings**, then retry step 1.
   - Installing through Android Studio (Run ▶) or `adb install` **never triggers
     this restriction** — that is why we recommend it.
3. **Mic permission** — tap the 🎤 button once in DroidFlow and grant
   RECORD_AUDIO when asked.
4. **(Optional) Gemini API key** for the AI planner:
   - Get a free key at **aistudio.google.com** → "Get API key".
   - DroidFlow → Settings (⚙) → paste the key → Save.
   - Without a key everything still works in deterministic mode.
5. **Keep the screen on during the demo**: phone Settings → Developer options →
   **Stay awake while charging** ON (or just keep the phone unlocked in your hand).
6. **Dry run** (2 minutes): open DroidFlow → tap the first suggested task →
   Start → watch the full hero run (see DEMO_SCRIPT.md). If it completes, you
   are demo-ready.

## 4. What a successful hero run looks like

1. You tap **Start** on "Compare cab prices on RideNow and SwiftRide…".
2. The Live Agent View shows OBSERVE → PLAN → ACT → VERIFY cycling in the
   timeline, while the phone visibly:
   - opens RideNow, types "Airport", taps Search rides,
   - reads the Sedan fare (₹482),
   - opens SwiftRide, types the destination, taps Find rides,
   - reads ₹519,
   - reopens RideNow (the cheaper one), selects Sedan, opens booking.
3. A red **PAYMENT BOUNDARY** event fires — the agent stops before Pay.
4. The Result screen appears automatically: fare table, savings ₹37,
   "nothing was booked, nothing was charged".

## 5. Troubleshooting

| Symptom | Fix |
|---|---|
| Gradle sync fails: "SDK location not found" | Let Studio install SDK 34, or set `local.properties` → `sdk.dir=/path/to/Android/sdk` |
| Build error mentioning JDK | File → Settings → Build Tools → Gradle → Gradle JDK → **17 (Embedded)** |
| Agent says "Accessibility service is OFF" | Re-check step 3.1; after toggling, reopen DroidFlow and watch the Onboarding status |
| Agent opens an app but nothing happens | The service lost connection — toggle the accessibility service off/on |
| Voice button says unavailable | Install/update the Google app, or type the task — voice is a bonus, not a dependency |
| Gemini: "API key rejected (403)" | Re-copy the key (no spaces/newlines) from aistudio.google.com |
| Gemini: rate limit (429) | Switch Settings → Planner → Deterministic script; demo continues |
| Typing action verified but wrong text | Make sure only one keyboard (Gboard) is active; disable autofill for the demo apps |
| Popup not dismissed | The popup recovery looks for the "Later" button — use the standard popup, not a custom label |
| RideNow/SwiftRide open on top of each other | Both APKs must be installed; check Recents — each demo app gets its own card |

## 6. Reset between demo runs

- In each mock app: finish at the "Back to home" / "Fill another" screen (one tap),
  so the next run starts from a clean state.
- DroidFlow → Result screen → "New task".
- Sessions are logged in `filesDir/logs/*.jsonl` (per task) — share them from
  the Result screen if judges want evidence.

## 7. Optional: read-only real-app demo (bonus beat)

With the accessibility service on, open the **UI Inspector** (Home screen →
"Developer: UI Inspector"), then open any real app (e.g. Uber) — the inspector
live-captures its UI tree and can find + tap + verify a button. This proves the
stack is app-agnostic without touching a real transaction.
