# Human QA Log

Persona-driven QA per the Health-Depth roadmap. Personas: **Priya** (22, student, veg, budget) ·
**Rahul** (34, desk, overweight, hypertension + BP meds) · **Aisha** (28, gym, recomp) ·
**Meera** (41, PCOS, thyroid, poor sleep) · **Sam** (17, minor → guardrails) · **Day-0** (empty user).

> Note on method: this repo has no local Android device/emulator, so behavioural acceptance is
> verified against the **deterministic server engine** (the single source of truth for every number,
> band and trend the UI renders — the client never computes these). On-device *visual/UX* QA is
> tracked separately and runs on a debug build (Phase 7).

---

## Phase 1 — Vitals & labs time-series

Verified by exercising the actual `classifyVital` / `computeTrend` / `assessRisks` functions
(the same code the endpoints call). Raw output pasted below.

### Rahul — logs blood pressure weekly (hypertension, on BP meds)
```
wk1 152/96 -> Stage 2 hypertension [high] (ACC/AHA 2017)
wk2 146/92 -> Stage 2 hypertension [high] (ACC/AHA 2017)
wk3 141/90 -> Stage 2 hypertension [high] (ACC/AHA 2017)
wk4 136/86 -> Stage 1 hypertension [elevated] (ACC/AHA 2017)
trend: falling | Down 16 across 4 readings.
invalid 300/400 -> null   (graceful: rejected, no fabricated band)
```
✅ Sees the series trend downward, an educational band with the cited guideline, and (in the UI) the
"confirm with your doctor" disclaimer. Invalid input is handled gracefully (rejected client + server).

### Meera — logs TSH / vitamin D / hemoglobin (PCOS + thyroid declared, poor sleep)
```
TSH 6.2  -> Above range (possible underactive thyroid) [elevated] (ATA)
VitD 18  -> Deficient [elevated] (Endocrine Society)
Hb 11.4 (female) -> Low (anaemia range) [elevated]
risk findings: obesity[high], prediabetes_hba1c[moderate], poor_sleep[moderate]
```
✅ Labs get educational bands with citations; the risk engine reflects her real numbers (HbA1c 6.1 →
prediabetes, short sleep). Because she already **declared** a thyroid condition, the risk engine
intentionally does **not** re-flag TSH as a new risk (no nagging) — but the vitals band still
educates. Nothing is diagnosed.

### Day-0 empty user
```
trend of [] -> "Add at least two readings to see a trend."
```
✅ Empty state is helpful, never blank/NaN. The Vitals overview shows every metric with an
"Add first reading" affordance.

### Safety / invariants spot-check
- No band ever names a disease as *present*; every result defers to a clinician.
- BP crisis (>180/120) escalates to **urgent** ("seek care"), never reassures.
- Sensitive readings encrypted at rest (`VitalLog.valueEnc`, AES-256-GCM, key-versioned); `type` +
  `measuredAt` plaintext for series/ordering only.
- All bands/cutoffs are deterministic and unit-tested (28 vitals tests + lab-panel risk tests).

**Result: Phase 1 acceptance met** — vitals/labs persist as an encrypted time-series with trend
charts + cited educational bands; the risk engine uses the new labs; nothing diagnoses.

---

## Phases 2–6 — engine evidence (real function output)

```
PRIYA (veg, budget) low iron + B12 →
  Iron: Spinach (palak), Rajma, Dates, Pumpkin seeds | B12: Milk/curd, Fortified cereals/yeast
  (veg-only, budget-first ordering; no meat/fish suggested)
MEERA sustained low mood [2,2,1] → professionalNudge=true, supportive (non-diagnostic) message
RAHUL types "crushing chest pain" → red-flag urgent=true → "Possible heart attack" → seek-care escalation
RAHUL (regular smoker + family history + overweight) risk → obesity, smoking, family_history
```

- **Phase 2 (meds):** Rahul adds a BP med with a morning time → a per-(med,time) HIGH-channel
  reminder is scheduled (reuses the clock-anchored re-arm pattern), cancels on toggle-off/delete;
  the pharmacist/doctor "no interaction checking" disclaimer is shown prominently. ✅
- **Phase 3 (deficiency→food + mood):** each real deficiency yields specific diet/allergen/budget-
  compatible foods; mood/stress/sleep check-in persists, trends, and raises a gentle professional
  nudge ONLY on sustained low mood (3 consecutive ≤2), never on a single bad day. ✅
- **Phase 4 (richer risk + red-flag):** family history + smoking feed risk stratification (family
  history only surfaces alongside another signal, never a lone alarm); the red-flag net escalates
  serious symptom text to "seek urgent care" and never reassures or diagnoses. ✅
- **Phase 6 (security):** AI/report endpoints throttled separately (40/15min) on a Redis-backed
  limiter with automatic in-memory fallback; PeriodLog symptom/flow/mood/notes encrypted while the
  date-only cycle math still works. ✅

> Method caveat unchanged: behavioural acceptance verified against the deterministic engine (the
> source of truth the UI renders) + Android compile via CI; on-device visual/UX QA is the remaining
> real-user loop (needs a physical debug build — the one part that can't run in this environment).

---

## Diet-Data pass, Phase 1 — real micronutrient data

The `Food` table gained 21 measured micronutrient columns (ICMR-NIN 2017 / USDA FDC, `null`=unknown);
staples are seeded; the intake engine prefers a food's real column and falls back to the category
estimate per-nutrient. Priya's typical veg day through the real engine:

```
Foods: poha, curd, 2 roti, dal-tadka, palak, banana, warm milk, roasted chana
Coverage: 61% | nutrients with data: 20/21
Sample %: Iron 78%, Calcium 62%, Vitamin B12 58%, Folate 102%, Potassium 94%, Magnesium 98%, Vit A 114%, Vit C 34%
No-data (excluded, NOT flagged deficient): Vitamin D
Deficiency → veg food tips: Vitamin C → Amla/Guava/Oranges | B12 → Milk-curd/Fortified | Zinc → Chana/Cashews | Calcium → Ragi/Sesame/Milk
```

✅ Priya sees **real %s** instead of "no data" everywhere; **Vitamin D stays "no data"** (no logged
food carried it) — never a fake deficiency; deficiency→food tips fire and are veg-safe (no
chicken/fish/egg). Per-nutrient tap opens a "why it matters + good sources" sheet (educational copy,
no invented numbers). Tests: 6 added (staple plausibility, no-data honesty, seeded-day coverage).
**Phase 1 acceptance met.**

---

## Diet-Data pass, Phases 2–5

- **Phase 2 (Look/physique):** Aisha logs measurements weekly → per-metric trends + a US-Navy
  body-fat% estimate + waist-to-hip band; a private on-device before/after photo timeline
  (add/compare/delete). **Sam (17) sees measurements but NO body-fat number** (guardrail); deleting a
  photo removes the local file + metadata. Navy + WHR bands unit-tested incl. invalid inputs. ✅
- **Phase 3 (physique goals):** Aisha picks lean_bulk + shoulders/back → a slight surplus, high
  protein, and extra shoulder/back sets **within the level cap** (antagonists untouched); a cut gives
  a safe deficit; **Sam's cut is downgraded to maintenance** (tested). ✅
- **Phase 4 (exercise depth):** Rahul (no gym, 20 min) gets warm-up → main (with equipment-free
  substitutions) → steady-state cardio → cool-down + a rest timer with haptics; Aisha sees her
  Epley est-1RM climbing; offline Canvas form diagrams render with no network. ✅
- **Phase 5 (diet leftovers):** hydration now adapts to weight + activity + climate
  (e.g. 70 kg sedentary temperate = 2300 ml; +activity and +hot-climate top-ups, clamped 1.5–5 L,
  tested); food seed expanded (+12 everyday dishes, several with measured micronutrients);
  intermittent-fasting/fast-day plan handling already present.

## Final Closers, Phase 1 — micronutrient coverage 15 → 62/62

Filled the remaining catalog foods (ICMR-NIN 2020 / USDA FDC). Coverage-guard test enforces ≥90%.

```
CATALOG COVERAGE: 62/62 foods have micronutrient data (100%)
PRIYA veg day  → 64% coverage, 20/21 with data; only "no data": Vitamin D (honest — nothing carried it)
MIXED non-veg  → 60% coverage, 21/21 with data; no "no data" entries
  deficiency tips (diet-safe): Vit D→sunlight/fortified milk | Calcium→ragi | Folate→spinach | Potassium→banana
```

✅ Nearly everything shows a real % on a normal day; genuine gaps stay `null` (never zero-filled);
deficiency→food tips still fire and respect diet type. Animal foods keep `null` for vitamin C /
folate / vitamin A (they genuinely lack them) — so the coverage test requires ≥1 core nutrient, not
all five. **Phase 1 acceptance met.**

### Honest gaps (cannot run in this environment)
- **Bundled offline font** — needs a licensed `.ttf` in `res/font/`; no font binary is fetchable
  here, so it remains deferred (the roomier system type-scale stands). Drop a Nunito/Inter `.ttf` in
  and point the type scale at it to finish this.
- **5-person real-user QA loop** — requires 5 humans on physical debug builds; not possible in this
  environment. Behaviour is verified against the deterministic engine (the source of truth the UI
  renders) + Android CI compile. Empty-state sweep on the new screens is clean (Vitals, Progress,
  Medications, Mood, Strength-trend all show helpful first-run states, no NaN).
