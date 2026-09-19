import { describe, it, expect } from 'vitest';
import { assessGoals } from '../src/calc/goalFeasibility';

const USER = {
  sex: 'male' as const, heightCm: 174, weightKg: 75, targetWeightKg: 67,
  waistCm: 89, targetWaistCm: 76, chestCm: 97, targetChestCm: 112, armCm: 30, targetArmCm: 40,
  thighCm: 55, forearmCm: 23, targetForearmCm: 25, bodyFatPct: 22, targetBodyFatPct: 11, trainingMonths: 14,
};
const get = (r: ReturnType<typeof assessGoals>, k: string) => r.items.find((i) => i.key === k)!;

describe('goal feasibility (natural, educational)', () => {
  const r = assessGoals(USER);

  it('flags a 40 cm arm at 174 cm as beyond natural limits and suggests a realistic one', () => {
    const arm = get(r, 'arm');
    expect(arm.verdict).toBe('unrealistic');
    expect(arm.suggestedTarget!).toBeGreaterThan(35);
    expect(arm.suggestedTarget!).toBeLessThan(38);
    expect(arm.monthsMax).toBeNull();
  });

  it('flags chest 112 cm as beyond natural limits for this height', () => {
    expect(get(r, 'chest').verdict).toBe('unrealistic');
  });

  it('waist 89 -> 76 and body fat 22 -> 11 are realistic within about a year and a half', () => {
    const w = get(r, 'waist'); const bf = get(r, 'bodyFat');
    expect(w.monthsMin!).toBeGreaterThanOrEqual(8); expect(w.monthsMax!).toBeLessThanOrEqual(14);
    expect(['realistic', 'ambitious']).toContain(w.verdict);
    expect(bf.monthsMax!).toBeLessThanOrEqual(24); expect(bf.monthsMin!).toBeGreaterThanOrEqual(8);
  });

  it('forearm 23 -> 25 cm is realistic', () => {
    const f = get(r, 'forearm');
    expect(['realistic', 'ambitious']).toContain(f.verdict);
    expect(f.monthsMax!).toBeLessThan(30);
  });

  it('recommends a total timeline from the reachable goals and warns about the rest', () => {
    expect(r.recommendedMonths!).toBeGreaterThan(6);
    expect(r.summary).toMatch(/Arm/);
    expect(r.summary).toMatch(/beyond natural limits/);
    expect(r.disclaimer.length).toBeGreaterThan(20);
  });

  it('body fat below 10% (men) is flagged and suggests 10%', () => {
    const x = get(assessGoals({ ...USER, targetBodyFatPct: 6 }), 'bodyFat');
    expect(x.verdict).toBe('unrealistic');
    expect(x.suggestedTarget).toBe(10);
  });

  it('a returning lifter (12+ months) gains size faster than a brand-new one', () => {
    const base = { sex: 'male' as const, heightCm: 174, weightKg: 70, armCm: 30, targetArmCm: 34 };
    const fresh = assessGoals({ ...base, trainingMonths: 0 }).items[0]!;
    const back = assessGoals({ ...base, trainingMonths: 24 }).items[0]!;
    expect(back.monthsMax!).toBeLessThan(fresh.monthsMax!);
  });

  it('smaller gains take longer and never go negative or NaN', () => {
    const a = assessGoals({ sex: 'male', heightCm: 175, weightKg: 70, armCm: 30, targetArmCm: 31 }).items[0]!;
    const b = assessGoals({ sex: 'male', heightCm: 175, weightKg: 70, armCm: 30, targetArmCm: 35 }).items[0]!;
    expect(a.monthsMax!).toBeLessThan(b.monthsMax!);
    for (const it of [a, b]) { expect(Number.isFinite(it.monthsMax!)).toBe(true); expect(it.monthsMin!).toBeGreaterThanOrEqual(1); }
  });

  it('ignores goals that are already met, and handles empty input', () => {
    expect(assessGoals({ sex: 'male', heightCm: 175, weightKg: 70, armCm: 35, targetArmCm: 34 }).items).toEqual([]);
    const empty = assessGoals({ sex: 'female', heightCm: 160, weightKg: 60 });
    expect(empty.items).toEqual([]);
    expect(empty.recommendedMonths).toBeNull();
  });

  it('women get lower natural ceilings and a higher body-fat floor', () => {
    const f = assessGoals({ sex: 'female', heightCm: 165, weightKg: 62, bodyFatPct: 30, targetBodyFatPct: 14 });
    expect(get(f, 'bodyFat').verdict).toBe('unrealistic');
    expect(get(f, 'bodyFat').suggestedTarget).toBe(18);
  });
});
