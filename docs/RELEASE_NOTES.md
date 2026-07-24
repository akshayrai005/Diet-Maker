# Kaizen — Release Notes (Build-Bible pass)

_Small Habits. Big Results._ Everything below is deterministic and runs at zero paid-API cost
(`AI_PROVIDER=rules`); no metric or prediction comes from an LLM.

## Highlights this pass

- **Diet (60%)** — micronutrient panel expanded to **22 nutrients** vs sex/age RDA (ICMR-NIN/DRI,
  cited), with **honest "no data yet"** instead of fake deficiencies; **budget- and pantry-aware**
  meal plans.
- **Exercise** — workouts scale **Beginner → Beast** with real volume/rest/split changes, **safety
  caps** for under-18/medical, **auto level-up / ease-down**, per-exercise form cues, tap-to-log +
  progressive overload.
- **Mind** — logged & streaked yoga/meditation/breathing, time+mood suggestion, difficulty range
  (2-min quick → 20-min deep), guided breathing with accessibility.
- **Discipline** — habits with streaks, explainable adherence, **custom habit create/delete**,
  non-punitive missed-day recovery.
- **Coach** — 0–100 rating + A–F, coach-voice narrative, honest next-month prediction, and an
  **intake-vs-scale disagreement flag** (catches under-logging).
- **Reminders** — workout **pre-alert at your set time** (10-min lead), HIGH heads-up channel,
  quiet hours, hourly hydration, **survive reboot**.
- **Security** — `ChatMessage` encrypted at rest, **key-versioning** for rotation, account
  **soft-delete + restore + scheduled purge**.
- **Brand/UX** — real **Kaizen** splash art + launcher icon, pastel theme with **accent/theme
  picker**, backend warm-up on splash so Home opens straight to a ready dashboard.

## Known remaining (see SELF_RATING.md)
Onboarding wizard + downloadable font + full a11y retrofit (Phase 8), Health-Connect step-aware
walk nudge, Redis rate-limit, and the 5-real-user device-QA loop (Phase 10).

## Run
- Server: `cd server && npm run build` (0 errors) · `npx vitest run` (331 tests). Note: `npm test`
  is vitest **watch** mode.
- Android: built on CI (`assembleDebug`).
