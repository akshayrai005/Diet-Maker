import { describe, it, expect } from 'vitest';
import { computePhasePlan } from '../src/modules/nutrition/phasePlan';

const DAY = 24 * 60 * 60 * 1000;
const WEEK = 7 * DAY;

describe('computePhasePlan', () => {
  it('returns null with no timeframe', () => {
    expect(computePhasePlan(new Date(), null)).toBeNull();
    expect(computePhasePlan(new Date(), 0)).toBeNull();
  });

  it('returns null once the plan has finished', () => {
    const start = new Date(Date.now() - 60 * WEEK);
    expect(computePhasePlan(start, 52)).toBeNull();
  });

  it('is fat_loss for the first ~13 weeks of a 52-week plan', () => {
    const start = new Date(Date.now() - 2 * WEEK);
    const result = computePhasePlan(start, 52)!;
    expect(result.phase).toBe('fat_loss');
    expect(result.phaseIndex).toBe(1);
    expect(result.phaseCount).toBe(3);
    expect(result.weekInPhase).toBe(3);
  });

  it('moves to shape after fat_loss weeks elapse', () => {
    const start = new Date(Date.now() - 15 * WEEK);
    const result = computePhasePlan(start, 52)!;
    expect(result.phase).toBe('shape');
    expect(result.phaseIndex).toBe(2);
  });

  it('moves to muscle_build after fat_loss + shape weeks elapse', () => {
    const start = new Date(Date.now() - 30 * WEEK);
    const result = computePhasePlan(start, 52)!;
    expect(result.phase).toBe('muscle_build');
    expect(result.phaseIndex).toBe(3);
    expect(result.weeksRemaining).toBeGreaterThan(0);
  });

  it('splits proportionally for a short timeline (<=13 weeks)', () => {
    const start = new Date(Date.now() - 1 * WEEK);
    const result = computePhasePlan(start, 8)!;
    expect(result.phase).toBe('fat_loss');
    expect(result.phaseCount).toBe(1);
  });

  it('uses two phases for a mid-length timeline (13-26 weeks)', () => {
    const start = new Date(Date.now() - 15 * WEEK);
    const result = computePhasePlan(start, 20)!;
    expect(result.phase).toBe('shape');
    expect(result.phaseCount).toBe(2);
  });

  it('never returns a future plan (elapsed < 0)', () => {
    const start = new Date(Date.now() + 5 * WEEK);
    expect(computePhasePlan(start, 52)).toBeNull();
  });
});
