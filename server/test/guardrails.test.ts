import { describe, it, expect } from 'vitest';
import {
  applyGuardrails,
  CALORIE_FLOOR,
  MAX_WEEKLY_LOSS_FRACTION,
  type GuardrailInput,
} from '../src/guardrails';

function base(overrides: Partial<GuardrailInput> = {}): GuardrailInput {
  return {
    sex: 'male',
    ageYears: 30,
    currentWeightKg: 90,
    targetWeightKg: 75,
    tdee: 2600,
    bmi: 29,
    goal: 'lose',
    conditions: [],
    ...overrides,
  };
}

describe('guardrails — always includes the disclaimer', () => {
  it('every result carries the educational disclaimer', () => {
    const r = applyGuardrails(base());
    expect(r.flags.some((f) => f.code === 'DISCLAIMER')).toBe(true);
  });
});

describe('guardrails — weekly-loss cap', () => {
  it('caps an aggressive 2 kg/week request and extends the timeline', () => {
    const r = applyGuardrails(base({ desiredWeeklyLossKg: 2 }));
    const cap = MAX_WEEKLY_LOSS_FRACTION * 90; // 0.675
    // Planned loss must not exceed the cap.
    expect(Math.abs(r.safeWeeklyDeltaKg)).toBeLessThanOrEqual(cap + 0.01);
    expect(r.flags.some((f) => f.code === 'WEEKLY_LOSS_CAPPED')).toBe(true);
    expect(r.safeWeeklyDeltaKg).toBeLessThan(0); // losing
  });

  it('deficit never exceeds 25% of TDEE', () => {
    const r = applyGuardrails(base({ desiredWeeklyLossKg: 5, tdee: 2000 }));
    const deficit = 2000 - r.dailyKcal;
    expect(deficit).toBeLessThanOrEqual(0.25 * 2000 + 1);
  });
});

describe('guardrails — calorie floors', () => {
  it('raises a sub-floor female target to 1200 and flags the extension', () => {
    // Small woman, low TDEE, aggressive goal -> would dip below floor.
    const r = applyGuardrails(
      base({ sex: 'female', currentWeightKg: 55, targetWeightKg: 50, tdee: 1400, bmi: 21 }),
    );
    expect(r.dailyKcal).toBeGreaterThanOrEqual(CALORIE_FLOOR.female);
    expect(r.flags.some((f) => f.code === 'CALORIE_FLOOR_APPLIED')).toBe(true);
  });

  it('allows below-floor only with a clinician override, flagged critical', () => {
    const r = applyGuardrails(
      base({
        sex: 'female',
        currentWeightKg: 55,
        tdee: 1350,
        desiredWeeklyLossKg: 1,
        clinicianOverride: true,
      }),
    );
    expect(r.flags.some((f) => f.code === 'BELOW_FLOOR_CLINICIAN')).toBe(true);
  });
});

describe('guardrails — pregnancy blocks weight loss', () => {
  it('blocks the cut, adds calories above TDEE, and requires supervision', () => {
    const r = applyGuardrails(base({ goal: 'lose', conditions: ['pregnancy'] }));
    expect(r.weightLossBlocked).toBe(true);
    expect(r.dailyKcal).toBeGreaterThan(2600); // above maintenance
    expect(r.requiresSupervision).toBe(true);
    expect(r.flags.some((f) => f.code === 'NO_DEFICIT_PREGNANCY')).toBe(true);
    expect(r.safeWeeklyDeltaKg).toBeGreaterThanOrEqual(0); // not losing
  });

  it('breastfeeding also adds calories and blocks loss', () => {
    const r = applyGuardrails(base({ goal: 'lose', conditions: ['breastfeeding'] }));
    expect(r.weightLossBlocked).toBe(true);
    expect(r.dailyKcal).toBeGreaterThan(2600);
  });
});

describe('guardrails — chronic kidney disease lowers protein', () => {
  it('overrides protein down to ~0.7 g/kg and requires supervision', () => {
    const r = applyGuardrails(base({ conditions: ['kidney_disease'] }));
    expect(r.proteinPerKg).toBeCloseTo(0.7, 5);
    expect(r.requiresSupervision).toBe(true);
    expect(r.flags.some((f) => f.code === 'CKD_PROTEIN_LOWERED')).toBe(true);
  });

  it('CKD protein override wins even when PCOS would raise it', () => {
    const r = applyGuardrails(base({ conditions: ['pcos', 'kidney_disease'] }));
    expect(r.proteinPerKg).toBeCloseTo(0.7, 5);
  });
});

describe('guardrails — condition modifiers', () => {
  it('hypertension sets a sodium ceiling', () => {
    const r = applyGuardrails(base({ conditions: ['hypertension'] }));
    expect(r.sodiumMaxMg).toBe(2000);
    expect(r.flags.some((f) => f.code === 'HTN_SODIUM')).toBe(true);
  });

  it('heart disease tightens sodium to the lower bound', () => {
    const r = applyGuardrails(base({ conditions: ['hypertension', 'heart_disease'] }));
    expect(r.sodiumMaxMg).toBe(1500);
  });

  it('diabetes adds a low-GI / carb-timing flag', () => {
    const r = applyGuardrails(base({ conditions: ['diabetes'] }));
    expect(r.flags.some((f) => f.code === 'DIABETES_LOWGI')).toBe(true);
    expect(r.requiresSupervision).toBe(true);
  });

  it('PCOS raises protein when kidneys are healthy', () => {
    const r = applyGuardrails(base({ conditions: ['pcos'] }));
    expect(r.proteinPerKg).toBe(2.0);
  });
});

describe('guardrails — supervision by BMI', () => {
  it('flags supervision for BMI >= 35', () => {
    expect(applyGuardrails(base({ bmi: 36 })).requiresSupervision).toBe(true);
  });
  it('flags supervision for BMI < 17', () => {
    expect(applyGuardrails(base({ bmi: 16, goal: 'gain' })).requiresSupervision).toBe(true);
  });
  it('no supervision for a healthy adult with no conditions', () => {
    expect(applyGuardrails(base({ bmi: 24 })).requiresSupervision).toBe(false);
  });
});

describe('guardrails — minors', () => {
  it('blocks weight-loss cuts for under-18s', () => {
    const r = applyGuardrails(base({ ageYears: 15, goal: 'lose' }));
    expect(r.weightLossBlocked).toBe(true);
    expect(r.dailyKcal).toBe(2600); // maintenance, no cut
    expect(r.flags.some((f) => f.code === 'MINOR_GROWTH')).toBe(true);
  });
});

describe('guardrails — remaining condition modifiers', () => {
  it('fatty liver flags added-sugar cuts', () => {
    expect(
      applyGuardrails(base({ conditions: ['fatty_liver'] })).flags.some(
        (f) => f.code === 'FATTY_LIVER_SUGAR',
      ),
    ).toBe(true);
  });

  it('thyroid flags a realistic pace + med timing', () => {
    expect(
      applyGuardrails(base({ conditions: ['thyroid'] })).flags.some(
        (f) => f.code === 'THYROID_PACE',
      ),
    ).toBe(true);
  });

  it('gout flags purine limits', () => {
    expect(
      applyGuardrails(base({ conditions: ['gout'] })).flags.some((f) => f.code === 'GOUT_PURINES'),
    ).toBe(true);
  });

  it('reduced mobility flags diet-led deficit + low-impact movement', () => {
    expect(
      applyGuardrails(base({ reducedMobility: true })).flags.some(
        (f) => f.code === 'REDUCED_MOBILITY',
      ),
    ).toBe(true);
  });

  it('cancer and post-surgery both require supervision', () => {
    expect(applyGuardrails(base({ conditions: ['cancer'] })).requiresSupervision).toBe(true);
    expect(applyGuardrails(base({ conditions: ['post_surgery'] })).requiresSupervision).toBe(true);
  });

  it('a modest deficit request is not capped (no WEEKLY_LOSS_CAPPED / DEFICIT_CAPPED)', () => {
    const r = applyGuardrails(base({ desiredWeeklyLossKg: 0.3, tdee: 2600 }));
    expect(r.flags.some((f) => f.code === 'WEEKLY_LOSS_CAPPED')).toBe(false);
    expect(r.dailyKcal).toBeLessThan(2600);
  });
});

describe('guardrails — maintain & gain', () => {
  it('maintain returns TDEE', () => {
    // A true maintain holds weight — target equals current. (When goal is "maintain" but the target
    // differs from current, the guardrail now honours the target as a lose/gain direction.)
    expect(applyGuardrails(base({ goal: 'maintain', targetWeightKg: 90 })).dailyKcal).toBe(2600);
  });
  it('maintain with a lower target is treated as a loss (honours the target)', () => {
    const r = applyGuardrails(base({ goal: 'maintain', targetWeightKg: 80 }));
    expect(r.dailyKcal).toBeLessThan(2600);
    expect(r.safeWeeklyDeltaKg).toBeLessThan(0);
  });
  it('gain returns a surplus above TDEE', () => {
    const r = applyGuardrails(base({ goal: 'gain' }));
    expect(r.dailyKcal).toBeGreaterThan(2600);
    expect(r.safeWeeklyDeltaKg).toBeGreaterThan(0);
  });
});
