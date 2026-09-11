import { describe, it, expect } from 'vitest';
import { isNewToDieting, dietRampFor, applyDietRamp } from '../src/modules/nutrition/dietRamp';

describe('isNewToDieting', () => {
  it('is true when membership is unknown/never reported, or under a month', () => {
    expect(isNewToDieting(null)).toBe(true);
    expect(isNewToDieting(undefined)).toBe(true);
    expect(isNewToDieting(1)).toBe(true);
  });

  it('is false for 3+ months', () => {
    expect(isNewToDieting(3)).toBe(false);
  });
});

describe('dietRampFor', () => {
  it('eases in over 2 weeks: week 1 at 50%, week 2 at 75%', () => {
    expect(dietRampFor(null, 0)).toEqual({ week: 1, factor: 0.5 });
    expect(dietRampFor(null, 6)).toEqual({ week: 1, factor: 0.5 });
    expect(dietRampFor(null, 7)).toEqual({ week: 2, factor: 0.75 });
    expect(dietRampFor(null, 13)).toEqual({ week: 2, factor: 0.75 });
  });

  it('returns null (full target) once week 2 is over', () => {
    expect(dietRampFor(null, 14)).toBeNull();
  });

  it('never ramps someone who already reports 3+ months of gym membership', () => {
    expect(dietRampFor(3, 0)).toBeNull();
  });
});

describe('applyDietRamp', () => {
  const base = { dailyKcal: 1700, tdee: 2200, proteinG: 140, carbG: 150, fatG: 50 };

  it('blends dailyKcal toward TDEE by the ramp factor, keeping it between the two', () => {
    const ramped = applyDietRamp(base, { week: 1, factor: 0.5 });
    // Halfway between 1700 and 2200 = 1950.
    expect(ramped.dailyKcal).toBe(1950);
    expect(ramped.dailyKcal).toBeGreaterThan(base.dailyKcal);
    expect(ramped.dailyKcal).toBeLessThanOrEqual(base.tdee);
  });

  it('never touches protein', () => {
    const ramped = applyDietRamp(base, { week: 1, factor: 0.5 });
    expect(ramped.proteinG).toBe(base.proteinG);
  });

  it('scales carb/fat up proportionally to fill the eased calorie budget', () => {
    const ramped = applyDietRamp(base, { week: 1, factor: 0.5 });
    expect(ramped.carbG).toBeGreaterThan(base.carbG);
    expect(ramped.fatG).toBeGreaterThan(base.fatG);
  });

  it('a surplus (gain) target eases DOWN toward maintenance, not up', () => {
    const gain = { dailyKcal: 2700, tdee: 2200, proteinG: 140, carbG: 300, fatG: 80 };
    const ramped = applyDietRamp(gain, { week: 1, factor: 0.5 });
    expect(ramped.dailyKcal).toBe(2450); // halfway between 2700 and 2200
    expect(ramped.dailyKcal).toBeLessThan(gain.dailyKcal);
  });
});
