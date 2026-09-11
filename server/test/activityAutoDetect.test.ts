import { describe, it, expect } from 'vitest';
import { detectActivityLevel, tdeeDeltaForLevelChange } from '../src/modules/nutrition/activityAutoDetect';

function daysAgo(...offsets: number[]) {
  return offsets.map((o) => ({ dayKey: `day-${o}` }));
}

describe('detectActivityLevel', () => {
  it('bumps effective level up when logged sessions exceed the reported level', () => {
    // ~5 sessions/week over 28 days -> 20 distinct days -> "active" tier
    const sessions = Array.from({ length: 20 }, (_, i) => ({ dayKey: `d${i}` }));
    const r = detectActivityLevel('sedentary', sessions, 28);
    expect(r.inferredLevel).toBe('active');
    expect(r.higherThanReported).toBe(true);
    expect(r.lowerThanReported).toBe(false);
    expect(r.effectiveLevel).toBe('active');
  });

  it('never auto-downgrades: reported stays effective when inferred is lower', () => {
    const r = detectActivityLevel('active', [], 28);
    expect(r.inferredLevel).toBe('sedentary');
    expect(r.lowerThanReported).toBe(true);
    expect(r.higherThanReported).toBe(false);
    expect(r.effectiveLevel).toBe('active');
  });

  it('matches -> no change either direction', () => {
    // 8 distinct days over 28 days = 2/week -> "moderate"
    const sessions = Array.from({ length: 8 }, (_, i) => ({ dayKey: `d${i}` }));
    const r = detectActivityLevel('moderate', sessions, 28);
    expect(r.inferredLevel).toBe('moderate');
    expect(r.higherThanReported).toBe(false);
    expect(r.lowerThanReported).toBe(false);
    expect(r.effectiveLevel).toBe('moderate');
  });

  it('collapses multiple same-day logs into one session for the frequency count', () => {
    const sessions = [{ dayKey: 'd1' }, { dayKey: 'd1' }, { dayKey: 'd1' }];
    const r = detectActivityLevel('sedentary', sessions, 7);
    expect(r.sessionsPerWeek).toBe(1);
    expect(r.inferredLevel).toBe('light');
  });

  it('the gym-vs-no-exercise scenario: same reported level, different logged history -> different effective TDEE input', () => {
    const gymGoer = detectActivityLevel('sedentary', daysAgo(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), 14);
    const nonExerciser = detectActivityLevel('sedentary', [], 14);
    expect(gymGoer.effectiveLevel).not.toBe(nonExerciser.effectiveLevel);
    expect(nonExerciser.effectiveLevel).toBe('sedentary');
  });
});

describe('tdeeDeltaForLevelChange', () => {
  it('is positive when moving to a higher tier', () => {
    expect(tdeeDeltaForLevelChange(1600, 'sedentary', 'active')).toBeGreaterThan(0);
  });

  it('is zero for no change', () => {
    expect(tdeeDeltaForLevelChange(1600, 'moderate', 'moderate')).toBe(0);
  });
});
