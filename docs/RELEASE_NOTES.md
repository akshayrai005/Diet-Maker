# Kaizen — Release Notes

_Small Habits. Big Results._ Everything below is deterministic and runs at zero paid-API cost
(`AI_PROVIDER=rules`); no metric or prediction comes from an LLM.

## Diet-Data + Look + Exercise-Depth pass (latest)

- **Real micronutrient data** — the Food table now carries measured per-100g vitamins & minerals
  (ICMR-NIN / USDA) for staples, so the vitamins & minerals card shows real %s (unknowns stay "no
  data"); tap a nutrient for "why it matters + good sources".
- **"Look" / physique tracking** — log body measurements and get per-metric trends, a tape-measure
  **US-Navy body-fat% estimate**, waist-to-hip band, and a **private, on-device** progress-photo
  timeline (before/after, never uploaded). No body-fat numbers for under-18s.
- **Physique goals** — recomp / lean bulk / cut / maintain shift calories & protein *safely*, and
  priority muscle groups get extra training volume within safe caps.
- **Exercise depth** — every session now has a warm-up, a cardio element, and a cool-down; a rest
  timer with haptics; equipment-free exercise swaps; an estimated-1RM strength trend; and offline
  form diagrams.
- **Diet depth** — hydration target adapts to weight, activity and climate; more everyday foods.

## Health-Depth pass

- **Vitals & labs as a time-series** — log BP, resting HR, glucose, HbA1c, lipids, TSH, vitamin D,
  ferritin, hemoglobin, weight, waist; see per-metric **trend charts** and **guideline-cited
  educational bands** (ACC/AHA, ADA, NCEP ATP III, ATA…). Readings encrypted at rest; the risk
  engine now sharpens off your latest labs. Not a diagnosis — always defers to a doctor.
- **Medications & supplements** — track what you take, optional **dose reminders** (local, quiet-hours
  aware), adherence, and a standing "Kaizen doesn't check drug interactions — ask your pharmacist"
  note. Encrypted at rest.
- **Deficiency → food fixes** — a real low nutrient now suggests **specific foods** that fit your
  diet, allergies and budget (e.g. low iron → spinach, rajma, dates).
- **Mood / stress / sleep check-in** — a gentle Mind-pillar entry that trends over time and, only on
  *sustained* low mood, gently suggests talking to someone. Never a diagnosis.
- **Richer risk + a red-flag safety net** — family history + smoking sharpen risk; serious symptom
  text (chest pain, stroke signs, self-harm…) triggers a calm "seek urgent care" escalation that
  never reassures or diagnoses.
- **Mind is now a first-class 5th tab.**
- **Security** — Redis-backed rate limiting with automatic in-memory fallback + stricter AI/report
  throttling; PeriodLog symptom/flow/mood/notes encrypted (dates stay indexed for the cycle math).

## Build-Bible pass

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
