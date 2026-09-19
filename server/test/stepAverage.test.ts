import { describe, it, expect } from 'vitest';
import { averageCompletedDaySteps } from '../src/modules/nutrition/activityAutoDetect';

const d = (iso: string, steps: number) => ({ date: new Date(`${iso}T00:00:00Z`), steps });
const now = new Date('2026-09-19T12:00:00Z');
const past = [10, 11, 12, 13, 14, 15, 16].map((n, i) => d(`2026-09-${n}`, 11_000 + i * 100));

describe('activity steps average is stable through the day', () => {
  it('ignores today no matter what its partial count is', () => {
    const a = averageCompletedDaySteps([...past, d('2026-09-19', 0)], now);
    const b = averageCompletedDaySteps([...past, d('2026-09-19', 9_265)], now);
    const c = averageCompletedDaySteps([...past, d('2026-09-19', 30_000)], now);
    expect(a).toBe(b);
    expect(b).toBe(c);
  });
  it('treats unsynced zero-step days as missing, not as lazy days', () => {
    const withZero = averageCompletedDaySteps([...past, d('2026-09-17', 0)], now);
    expect(withZero).toBe(averageCompletedDaySteps(past, now));
  });
  it('needs at least 5 completed days', () => {
    expect(averageCompletedDaySteps(past.slice(0, 4), now)).toBeUndefined();
    expect(averageCompletedDaySteps(past.slice(0, 5), now)).toBeDefined();
  });
});
