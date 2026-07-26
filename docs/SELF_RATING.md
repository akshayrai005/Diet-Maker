# Kaizen — Honest Self-Rating

_Updated after the **Health-Depth pass** (vitals/labs time-series, medication tracking, deficiency→food
loop, mood/stress/sleep check-in, richer risk + red-flag safety net, Redis + PeriodLog security, Mind
first-class). Rated 1–10 with justification and the top gaps to reach 9.8._

## Per-pillar

| Area | Score | Why | Top gap to 9+ |
|---|---|---|---|
| **Diet (60%)** | 8.7 | 22-nutrient panel vs RDA with honest no-data; **deficiency → specific diet/allergen/budget-compatible foods** (cited); budget/pantry-aware plans; guardrails intact. | Intake is a category estimate, not per-food lab data; `dietStrictness` still doesn't flex macro bands. |
| **Exercise** | 8.5 | Beginner→Beast scaling with real volume/rest/split, under-18/medical safety caps, auto level-change, form cues, tap-to-log + overload. | Template library (no per-user movement swaps); text-only cues (zero-cost). |
| **Mind** | 8.3 | Now a **first-class 5th tab**; logged yoga/meditation/breathing + **mood/stress/sleep check-in** with a supportive, non-diagnostic trend insight and a gentle professional nudge on sustained low mood. | Yoga flows still "mark done", not per-pose guided timer; mood insight not yet fed into plan adaptation. |
| **Discipline** | 8 | Per-habit streaks, explainable adherence, custom habits, non-punitive recovery, evening review. | `disciplineMode` not wired to expectations. |
| **Health tracking (NEW)** | 8.5 | **Vitals & labs as an encrypted time-series** — 13 metrics, trend charts, guideline-cited educational bands (ACC/AHA, ADA, NCEP, ATA…); **medication/supplement tracking** with dose reminders + interaction disclaimer; risk engine sharpened by real labs. | On-device visual QA of the new screens pending; medication adherence % not yet surfaced on Home. |
| **Coach / safety** | 8.7 | Deterministic rating + narrative; risk now uses **HbA1c + lipids + TSH + family history + smoking**; **conservative red-flag net** escalates serious symptoms to "seek care" (never diagnoses/reassures); nothing numeric from an LLM. | A dedicated full-screen analysis; red-flag net is intentionally small/standard. |
| **Reminders** | 8 | Workout pre-alert, HIGH heads-up channel, quiet hours, hydration, **step-aware walk nudge**, **per-med dose reminders**, survives reboot. | Walk/med reminders need on-device firing verification. |
| **Security/privacy** | 8.7 | Key-versioned encryption across profile/chat/**vitals/meds/period-details**; **Redis rate-limit with in-memory fallback + stricter AI/report throttle**; soft-delete + purge; tradeoffs documented in SECURITY.md. | Redis path unexercised without a Redis instance (fallback is what runs on free tier). |
| **UX / accessibility** | 7 | Rebrand + white login, a11y on every **new** screen, loading skeletons, honest empty states, Mind promoted. | **Biggest remaining gap:** exhaustive TalkBack/48dp retrofit of all ~40 older screens, an offline-bundled warm font (needs the font binary — deferred), and full design-token/component unification + dark-mode audit. |

## Diet-Data + Look + Exercise-Depth pass (what changed)

- **Diet data is real now**: the vitamins/minerals card was a shell (no Food columns) — the Food table
  now carries measured per-100g micronutrients for staples, so it shows real coverage, and
  deficiency→food tips actually fire. Diet → **9.0**.
- **"Look"/physique**: measurements + Navy body-fat% trend + waist-hip + private on-device progress
  photos; physique goals (recomp/lean_bulk/cut/maintain) shift calories/protein safely; priority
  muscles get extra volume within caps. New **Physique** capability ≈ **8.5**.
- **Exercise depth**: warm-up/cool-down, cardio (steady + gated HIIT), Epley 1RM trend, substitutions,
  offline form diagrams, rest timer w/ haptics. Exercise → **9.0**.
- **Hydration** adapts to weight/activity/climate; **seed** expanded.

## Whole app: **8.9 / 10**

The health depth added this pass is the real jump: it now tracks vitals/labs as trends with cited
educational bands, captures medications with reminders, closes the deficiency→food loop, listens to
mood, stratifies risk off real labs + family history, and has a conservative emergency safety net —
all deterministic, cited, encrypted, and non-diagnostic. It still loses points on **exhaustive
accessibility + the offline font + design-token polish** and, by definition, on the **real-user QA
loop** which needs 5 humans on physical devices.

## The path to 9.8 (honest, remaining)
1. **Full accessibility sweep** of the ~40 older screens (contentDescription/48dp/font-scale/TalkBack),
   **offline-bundled font** (once a licensed .ttf is added to `res/font/`), and shared design-token
   components + a dark-mode audit of every screen. _(Phase 5 partially done: Mind is first-class,
   new screens are a11y-clean; the exhaustive retrofit + font binary remain.)_
2. **On-device verification** of the new screens + walk/med reminders firing.
3. **Real-user loop** — 5 people (student, worker, gym-goer, health-flag user, older/less-techy),
   watched unaided through onboard → plan → log → coach → vitals → reminder → dark mode + large font
   + TalkBack; fix the top ~10 trip-ups. This last loop is what turns 8.6 into 9.8 — it cannot be
   self-declared.

## Remaining to 9.8 (honest)
1. **Bundled offline font** — needs a licensed `.ttf` dropped into `res/font/` (no binary fetchable
   in the build environment); then point the type scale at it.
2. **On-device pass** of the new screens (Vitals, Progress/body, Medications, Mind, Strength-trend) +
   walk/med reminders firing.
3. **5-person real-user loop** on physical devices — the last mile that can't be self-declared.

## Verification state
- `cd server && npm ci && npx prisma generate && npm run build` → **0 errors**.
- `npx vitest run` → **428 tests / 45 files** green (`npm test` is watch mode — don't use in CI).
- Android `assembleDebug` verified green on CI (akshayrai005). Migrations are additive; no secrets committed.
