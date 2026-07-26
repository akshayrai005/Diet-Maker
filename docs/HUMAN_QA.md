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
