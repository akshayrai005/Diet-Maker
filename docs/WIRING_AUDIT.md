# Kaizen — Wiring Audit (Phase 0)

_Generated at the start of the "Build Bible" pass. Reflects the repo after the Kaizen transformation
work (commits through `40eb1da` + the boot-receiver commit). Legend:_

- **Built** — the deterministic engine / pure logic exists.
- **Wired** — reachable from a real HTTP endpoint.
- **Surfaced** — rendered on an Android screen the user can reach.
- **Tested** — has vitest coverage (pure logic).

> **Reality check on the prompt's premise:** the prompt states `npm run build` fails with ~49 `TS7006`
> implicit-any errors. That is **stale**. `npm run build` (`tsc -p tsconfig.json`, `strict: true`) exits
> **0 errors**, and `npx vitest run` passes **287 tests / 32 files**. The named files
> (`rating/context.ts`, `reports/reports.service.ts`, `wellness/wellnessLog.service.ts`) were authored
> this session with explicit types. No Phase-0 compile fix was required; Phase 0's remaining value is
> this audit + the boot receiver + confirming Android builds (CI green on `40eb1da`).

## Core engines

| Engine | Endpoint | Screen | Built | Wired | Surfaced | Tested |
|---|---|---|:-:|:-:|:-:|:-:|
| BMR/TDEE/macros (`calc/*`) | `/calc*`, `/dashboard` | Dashboard | ✅ | ✅ | ✅ | ✅ |
| Risk / guardrails (`risk`, `guidance`) | `/risk`, `/guidance` | Dashboard flags | ✅ | ✅ | ✅ | ✅ |
| Meal-plan generator (`food`, `nutrition`) | `/plan*` | Diet › Plan | ✅ | ✅ | ✅ | ✅ |
| Overall rating (`rating/rating.ts`) | `/rating` | Home "Your Analysis" | ✅ | ✅ | ✅ | ✅ |
| Coach brief (`coach/coach.brief.ts`) | `/coach/today` | Home card | ✅ | ✅ | ✅ | ✅ |
| Analysis narrative (`reports/analysis.ts`) | `/report/analysis` | Report HTML | ✅ | ✅ | ✅ | ✅ |
| Progressive overload (`exercise/overload.ts`) | `/exercise-plan` | Move › Exercise | ✅ | ✅ | ✅ | ✅ |
| Activity calories (`calc/activityCalories.ts`) | logExercise / wellness | Move › Log | ✅ | ✅ | ✅ | ✅ |
| Micronutrient RDA + estimate | `/dashboard` | Home Micronutrients | ✅ | ✅ | ✅ | ✅ |
| Discipline adherence (`discipline/adherence.ts`) | `/discipline/today` | Me › Discipline | ✅ | ✅ | ✅ | ✅ |
| Habits + streaks (`discipline/habits.ts`) | `/discipline/today`, `/habits/:id/toggle` | Me › Discipline | ✅ | ✅ | ✅ | ✅ |
| Wellness content + logging | `/wellness*`, `/wellness/session` | Move › Meditation, Log | ✅ | ✅ | ✅ | ✅ |
| Wellness "now" suggestion (`suggestNow`) | `/wellness/suggest` | Move › Meditation | ✅ | ✅ | ✅ | ✅ |
| Next-month prediction (`predictNextMonth`) | `/report/*` | Report | ✅ | ✅ | ✅ | ✅ |
| Cycle phase (`cycle`) | `/cycle*` | Diet (female) | ✅ | ✅ | ✅ | ✅ |
| Reminder prefs (`reminders`) | `/reminders/prefs` | Settings | ✅ | ✅ | ✅ | n/a |

## Nav surfacing (Android)

- **Home** = coach brief + rating ring + pillar bars (AnalysisCard) + dashboard (calories/macros/micros/water). ✅
- **Diet** = `[Plan | Log | Grocery]`. ✅
- **Move** = `[Exercise | Meditation | Log]` with tap-to-log + streaks. ✅
- **Me** = hub incl. **Discipline** (adherence ring + habits), Coach, Reports, Badges, Settings, etc. ✅
- Mind folded into Move › Meditation (no duplicate tab). ✅

## Known gaps (drive later phases)

| Gap | Phase |
|---|---|
| Micronutrient panel is **7 nutrients**; prompt wants ~22 (add A,C,E,K,B1,B2,B3,B6, Zn,Se,I,P,Cu,Mn). Food seed lacks most micros. | 2 |
| No `fitnessLevel` / `intensityPreference`; workout generator doesn't scale Beginner→Hard. | 3 |
| Budget/pantry/strictness/IF signals stored but **not yet driving plan generation**. | 2 |
| Reminders: **workout pre-alert now fires** at the user's set time; **boot re-arm added** this phase. Walk nudge + hourly hydration are **prefs-only, not firing yet**; no HIGH-importance channel / quiet hours. | 7 |
| Onboarding is a **single screen**, not a wizard; no plan-preview-before-signup. | 8 |
| Accessibility: new screens have semantics; **older screens not retrofitted**; no downloadable font. | 8 |
| Security: `ChatMessage` encryption to verify; **`PeriodLog` not encrypted**; no key-versioning; rate-limit is in-memory (no Redis). Account soft-delete + grace **done**; scheduled purge is a manual `purgeExpiredAccounts()` (no cron). | 9 |

## Dead code / test-only exports

- None material found: the four previously-orphaned engines (`rating`, `adherence`, `overload`,
  `micronutrients`) are now all wired + surfaced. `purgeExpiredAccounts()` is exported but not yet called
  by a scheduler (intended for a Phase-9 cron).

## How to run

- Build: `cd server && npm run build` → 0 errors.
- Tests: `cd server && npx vitest run` (note: `npm test` is vitest **watch** mode) → 287 pass.
- Android: compiled on GitHub Actions CI (`assembleDebug`); latest green on `40eb1da`.
