# Kaizen — Honest Self-Rating

_As of the Build-Bible pass (phases 0–7, 9 complete + CI-green; phase 8 partial; phase 10 = this doc + the human-QA loop you run on a device). Rated 1–10 with justification and the top gaps to reach 9.8._

## Per-pillar

| Area | Score | Why | Top gap to 9+ |
|---|---|---|---|
| **Diet (60%)** | 8.5 | 22-nutrient panel vs RDA with **honest no-data** (never a fake deficiency), budget/pantry-aware plans, cited DRI/ICMR-NIN values, guardrails intact. | Micronutrient intake is a *category estimate*, not per-food lab data; `dietStrictness` doesn't yet flex macro bands; pantry is a tag list, not a rich picker. |
| **Exercise (part of 25%)** | 8.5 | Scales Beginner→Beast with real volume/rest/split changes, **safety caps** for under-18/medical, auto level-up/ease-down, per-exercise form cues, tap-to-log + progressive overload. | Exercise library is template-based (no per-user movement swaps yet); form cues are short text (zero-cost constraint — no video). |
| **Mind (part of 25%)** | 8 | Logged + streaked yoga/meditation/breathing, time+mood suggestion, difficulty range (2-min → 20-min), guided breathing with a11y. | Yoga flows have "mark done" but not a per-pose guided timer like meditations. |
| **Discipline (15%)** | 8 | Habits with per-habit streaks, explainable adherence score, custom create/delete, non-punitive missed-day recovery, evening review. | `disciplineMode` (Gentle/Standard/Hardcore) not yet wired to expectations. |
| **Coach intelligence** | 8.5 | Deterministic 0–100 rating + A–F, renormalised on missing data, coach-voice narrative, honest next-month prediction, **intake-vs-scale disagreement flag**; nothing from an LLM. | A dedicated full-screen "Your Analysis" (beyond the Home card + HTML report) would sharpen it. |
| **Reminders** | 7 | Workout pre-alert at the user's set time (10-min lead), HIGH heads-up channel, quiet hours, hourly hydration, **survives reboot** (BOOT_COMPLETED). | **Step-aware walk nudge** (Health Connect reads in a worker) not built — device-critical, needs on-device verification. |
| **Security/privacy** | 8 | Encrypted `Profile.sensitiveEnc` + now `ChatMessage`, **key-versioning** for rotation, soft-delete + restore + scheduled purge, guardrails + disclaimers everywhere. | Redis rate-limit not wired (in-memory stands); `PeriodLog` dates left plaintext (the cycle engine queries them — encrypting breaks it). |
| **UX / accessibility** | 6.5 | Kaizen rebrand (real splash art + logo), pastel theme + accent/theme picker, a11y on every **new** screen, honest empty states. | **Phase 8 is the main gap**: onboarding is still one screen (not a multi-step wizard w/ plan-preview), older screens not a11y-retrofitted, no downloadable Google font, no shared design-token components. |

## Whole app: **8.0 / 10**

Rock-solid where it counts — deterministic, explainable, honest, kind, zero-cost, and now broad across all four pillars with real scaling and safety. It loses points on **visual polish/onboarding delight (Phase 8)** and **device-verified reminders (walk nudge)**, plus the **real-user QA loop (Phase 10)** which by definition needs 5 humans on real devices.

## The path to 9.8 (honest)
1. **Phase 8 design pass** — multi-step onboarding wizard with a plan-preview payoff; downloadable warm font + larger type scale; full TalkBack/48dp retrofit of older screens; shared components + dark-mode audit.
2. **Walk nudge** — Health Connect step-aware reminder, verified firing on a device.
3. **Redis rate-limit** + AI/report endpoint throttling.
4. **Phase 10 loop** — put it in front of 5 real people, watch them use it unaided, fix the top 10 trip-ups. This last loop is what turns 8.0 into 9.8; it cannot be self-declared.

## Verification state
- `cd server && npm run build` → 0 errors. `npx vitest run` → **331 tests / 35 files** green. (`npm test` is watch mode.)
- Android `assembleDebug` green on CI through phase 7; phase 9 is server-only.
