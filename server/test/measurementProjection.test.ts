import { describe, it, expect } from 'vitest';
import { projectMeasurement, type TrendPoint } from '../src/modules/body/measurementProjection';

describe('projectMeasurement - heuristic path (no logged history)', () => {
  it('shrinks waist over time for someone losing weight, never a single point number', () => {
    const p = projectMeasurement('waistCm', 90, -0.4, []); // losing 0.4 kg/week
    expect(p.source).toBe('estimate');
    expect(p.milestones).toHaveLength(3);
    for (const m of p.milestones) {
      expect(m.lowCm).toBeLessThan(90);
      expect(m.highCm).toBeLessThan(90);
      expect(m.lowCm).toBeLessThanOrEqual(m.highCm); // always a real range
    }
    // Farther out should shrink more than closer in.
    const [m3, m6, m12] = p.milestones;
    expect(m12!.highCm).toBeLessThan(m3!.highCm);
    expect(m6!.highCm).toBeLessThanOrEqual(m3!.highCm);
  });

  it('grows arms/chest over time for someone gaining weight', () => {
    const p = projectMeasurement('armCm', 30, 0.3, []); // gaining 0.3 kg/week
    expect(p.source).toBe('estimate');
    for (const m of p.milestones) {
      expect(m.lowCm).toBeGreaterThan(30);
    }
  });

  it('stays flat (no change) when maintaining', () => {
    const p = projectMeasurement('waistCm', 90, 0, []);
    for (const m of p.milestones) {
      expect(m.lowCm).toBeCloseTo(90, 0);
      expect(m.highCm).toBeCloseTo(90, 0);
    }
  });

  it('never projects below 60% or above 160% of the current measurement, however extreme the input', () => {
    const p = projectMeasurement('waistCm', 100, -10, [], [60]); // absurd 10kg/week loss, 5 years out
    const m = p.milestones[0]!;
    expect(m.lowCm).toBeGreaterThanOrEqual(60 - 0.01);
    expect(m.highCm).toBeLessThanOrEqual(160 + 0.01);
  });
});

describe('projectMeasurement - trend path (real logged history)', () => {
  it('extrapolates the users OWN observed shrink rate, ignoring the heuristic entirely', () => {
    // Waist went 95 -> 90 cm over 60 days = -0.5 cm/week trend.
    const history: TrendPoint[] = [
      { daysAgo: 60, valueCm: 95 },
      { daysAgo: 0, valueCm: 90 },
    ];
    const p = projectMeasurement('waistCm', 90, -5 /* heuristic would predict a huge drop */, history, [4]);
    expect(p.source).toBe('trend');
    // ~4 months = ~17.4 weeks * -0.5cm/week ≈ -8.7cm from 90 => ~81.3cm, nowhere near what the
    // (deliberately extreme) heuristic weeklyWeightDeltaKg would have produced.
    const m = p.milestones[0]!;
    expect(m.highCm).toBeLessThan(90);
    expect(m.highCm).toBeGreaterThan(70); // sane, not the heuristic's runaway number
  });

  it('falls back to the heuristic when history has too little time spread to trust', () => {
    const history: TrendPoint[] = [
      { daysAgo: 2, valueCm: 90.2 },
      { daysAgo: 0, valueCm: 90 },
    ];
    const p = projectMeasurement('waistCm', 90, -0.4, history);
    expect(p.source).toBe('estimate');
  });

  it('falls back to the heuristic with only one logged point', () => {
    const p = projectMeasurement('waistCm', 90, -0.4, [{ daysAgo: 0, valueCm: 90 }]);
    expect(p.source).toBe('estimate');
  });
});
