import { describe, it, expect } from 'vitest';
import { goalTimeline } from '../src/calc/goalTimeline';

describe('goalTimeline — safe, clamped goal pacing', () => {
  it('clamps an unsafe fast timeframe and shows the realistic date + explanation', () => {
    // 75 → 68 (−7 kg) in 4 weeks = 1.75 kg/week requested. Safe cap = 0.75% of 75 = 0.5625 kg/wk.
    const r = goalTimeline({ currentWeightKg: 75, targetWeightKg: 68, desiredWeeks: 4 });
    expect(r.direction).toBe('lose');
    expect(r.clamped).toBe(true);
    expect(r.safeWeeklyRateKg).toBeCloseTo(0.5625, 3);
    expect(r.realisticWeeks).toBe(Math.ceil(7 / 0.5625)); // ~13 weeks, not 4
    expect(r.realisticWeeks).toBeGreaterThan(4);
    expect(r.message).toMatch(/safe pace/i);
    expect(r.desiredWeeklyLossKg).toBeCloseTo(0.5625, 3);
    expect(r.dailyKcalDelta).toBeGreaterThan(0); // a deficit
  });

  it('accepts a reasonable timeframe without clamping', () => {
    // 75 → 68 in 14 weeks = 0.5 kg/wk, under the 0.5625 cap.
    const r = goalTimeline({ currentWeightKg: 75, targetWeightKg: 68, desiredWeeks: 14 });
    expect(r.clamped).toBe(false);
    expect(r.safeWeeklyRateKg).toBeCloseTo(0.5, 2);
    expect(r.message).toMatch(/on track/i);
  });

  it('a MINOR cannot set a weight-loss timeline (blocked)', () => {
    const r = goalTimeline({ currentWeightKg: 60, targetWeightKg: 50, desiredWeeks: 8, isMinor: true });
    expect(r.blocked).toBe(true);
    expect(r.desiredWeeklyLossKg).toBe(0);
    expect(r.message).toMatch(/under-18|habits/i);
  });

  it('medically weight-loss-blocked cannot set a loss timeline', () => {
    const r = goalTimeline({ currentWeightKg: 65, targetWeightKg: 58, desiredWeeks: 10, weightLossBlocked: true });
    expect(r.blocked).toBe(true);
    expect(r.desiredWeeklyLossKg).toBe(0);
    expect(r.message).toMatch(/doctor|dietitian/i);
  });

  it('gain is capped to a lean pace', () => {
    // 60 → 65 in 4 weeks = 1.25 kg/wk. Gain cap = 0.35% of 60 = 0.21 kg/wk.
    const r = goalTimeline({ currentWeightKg: 60, targetWeightKg: 65, desiredWeeks: 4 });
    expect(r.direction).toBe('gain');
    expect(r.clamped).toBe(true);
    expect(r.safeWeeklyRateKg).toBeCloseTo(0.21, 2);
    expect(r.desiredWeeklyLossKg).toBe(0); // not a loss
    expect(r.dailyKcalDelta).toBeLessThan(0); // a surplus
  });

  it('already at target → maintain', () => {
    const r = goalTimeline({ currentWeightKg: 60, targetWeightKg: 60, desiredWeeks: 8 });
    expect(r.direction).toBe('maintain');
    expect(r.realisticWeeks).toBe(0);
  });

  it('never returns a pace above the safe cap, whatever the timeframe', () => {
    for (const weeks of [1, 2, 3, 4, 8]) {
      const r = goalTimeline({ currentWeightKg: 90, targetWeightKg: 70, desiredWeeks: weeks });
      expect(r.safeWeeklyRateKg).toBeLessThanOrEqual(0.0075 * 90 + 1e-9);
    }
  });
});
