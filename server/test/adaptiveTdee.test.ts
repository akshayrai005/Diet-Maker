import { describe, it, expect } from 'vitest';
import { computeAdaptiveTdee, type DailyIntakeSample, type WeightSample } from '../src/modules/nutrition/adaptiveTdee';

const NOW = new Date('2026-09-13T00:00:00Z');

function daysAgoIso(days: number): string {
  return new Date(NOW.getTime() - days * 86_400_000).toISOString().slice(0, 10);
}

describe('computeAdaptiveTdee', () => {
  it('is unavailable with too little intake logging', () => {
    const r = computeAdaptiveTdee(2200, [{ date: daysAgoIso(1), kcal: 2000 }], [], NOW);
    expect(r.available).toBe(false);
    expect(r.reason).toBe('not_enough_intake_logs');
  });

  it('is unavailable with too little weight-history span', () => {
    const intake: DailyIntakeSample[] = Array.from({ length: 14 }, (_, i) => ({ date: daysAgoIso(i), kcal: 2000 }));
    const weights: WeightSample[] = [
      { date: daysAgoIso(3), weightKg: 80 },
      { date: daysAgoIso(1), weightKg: 79.8 },
    ];
    const r = computeAdaptiveTdee(2200, intake, weights, NOW);
    expect(r.available).toBe(false);
    expect(r.reason).toBe('not_enough_weight_logs');
  });

  it('detects a real metabolism higher than the formula guessed (eating at formula TDEE but still losing weight)', () => {
    const intake: DailyIntakeSample[] = Array.from({ length: 21 }, (_, i) => ({ date: daysAgoIso(i), kcal: 2200 }));
    // Losing ~0.5kg over 18 days while eating at "maintenance" (2200) implies real TDEE is higher.
    // i=0 is "today" (lowest weight, having lost the most); larger i is further in the past (heavier).
    const weights: WeightSample[] = Array.from({ length: 10 }, (_, i) => ({
      date: daysAgoIso(i * 2),
      weightKg: 80 + i * 0.05,
    }));
    const r = computeAdaptiveTdee(2200, intake, weights, NOW);
    expect(r.available).toBe(true);
    expect(r.observedTdee).toBeGreaterThan(2200);
    expect(r.adjustedTdee).toBeGreaterThan(2200);
    expect(r.confidence).toBe('high');
  });

  it('clamps an implausible observed TDEE to within 30% of the formula estimate', () => {
    const intake: DailyIntakeSample[] = Array.from({ length: 21 }, (_, i) => ({ date: daysAgoIso(i), kcal: 500 }));
    // A huge, implausible weight loss implying an absurd TDEE - must be clamped, not trusted raw.
    // i=0 is "today" (lowest weight); larger i is further in the past (heavier).
    const weights: WeightSample[] = Array.from({ length: 10 }, (_, i) => ({
      date: daysAgoIso(i * 2),
      weightKg: 72 + i * 2,
    }));
    const r = computeAdaptiveTdee(2200, intake, weights, NOW);
    expect(r.available).toBe(true);
    expect(r.observedTdee).toBeLessThanOrEqual(2200 * 1.3);
  });

  it('reports no recalibration needed when observed matches the formula closely', () => {
    const intake: DailyIntakeSample[] = Array.from({ length: 21 }, (_, i) => ({ date: daysAgoIso(i), kcal: 2200 }));
    const weights: WeightSample[] = Array.from({ length: 10 }, () => ({ date: daysAgoIso(0), weightKg: 80 }));
    // Flat weight, stable weights list needs distinct dates for a slope - use a tiny realistic spread.
    const spread: WeightSample[] = Array.from({ length: 10 }, (_, i) => ({ date: daysAgoIso(i * 2), weightKg: 80 }));
    const r = computeAdaptiveTdee(2200, intake, spread, NOW);
    expect(r.available).toBe(true);
    expect(Math.abs(r.deviationPct ?? 100)).toBeLessThanOrEqual(2);
    expect(r.message).toContain('no recalibration needed');
    void weights;
  });

  it('is unavailable for an invalid formula TDEE', () => {
    const r = computeAdaptiveTdee(0, [], [], NOW);
    expect(r.available).toBe(false);
    expect(r.reason).toBe('invalid_formula_tdee');
  });
});
