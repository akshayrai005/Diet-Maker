import { describe, it, expect } from 'vitest';
import { hydrationTargetMl } from '../src/calc/hydration';

describe('hydrationTargetMl — adapts to weight, activity, climate', () => {
  it('scales with body weight (~33 ml/kg base)', () => {
    const light = hydrationTargetMl(50, 'sedentary', 'temperate');
    const heavy = hydrationTargetMl(90, 'sedentary', 'temperate');
    expect(heavy).toBeGreaterThan(light);
    expect(hydrationTargetMl(70, 'sedentary', 'temperate')).toBe(2300); // 70*33=2310 → nearest 50
  });

  it('adds water for higher activity', () => {
    const base = hydrationTargetMl(70, 'sedentary', 'temperate');
    expect(hydrationTargetMl(70, 'moderate', 'temperate')).toBeGreaterThan(base);
    expect(hydrationTargetMl(70, 'veryactive', 'temperate')).toBeGreaterThan(hydrationTargetMl(70, 'moderate', 'temperate'));
  });

  it('adds water for a hot climate', () => {
    expect(hydrationTargetMl(70, 'moderate', 'hot')).toBeGreaterThan(hydrationTargetMl(70, 'moderate', 'temperate'));
    expect(hydrationTargetMl(70, 'moderate', 'cold')).toBe(hydrationTargetMl(70, 'moderate', 'temperate'));
  });

  it('clamps to a sane range and tolerates bad input', () => {
    expect(hydrationTargetMl(200, 'veryactive', 'hot')).toBeLessThanOrEqual(5000);
    expect(hydrationTargetMl(20, 'sedentary', 'cold')).toBeGreaterThanOrEqual(1500);
    expect(hydrationTargetMl(0)).toBeGreaterThanOrEqual(1500); // falls back to 70kg
  });

  it('rounds to the nearest 50 ml', () => {
    expect(hydrationTargetMl(70, 'moderate', 'temperate') % 50).toBe(0);
  });
});
