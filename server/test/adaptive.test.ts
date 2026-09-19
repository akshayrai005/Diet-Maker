import { describe, it, expect } from 'vitest';
import { computeAdaptation, weeklyWeightChange } from '../src/modules/chat/adaptive';

const day = (n: number) => new Date(Date.UTC(2026, 0, 1 + n)).toISOString();
const pts = (ws: number[], step = 7) => ws.map((w, i) => ({ date: day(i * step), weightKg: w }));
const base = { goal: 'lose' as const, targetKcal: 2000, loggedDailyKcals: [2000, 1980, 2020, 2010, 1990, 2000, 2005] };

describe('adaptation never reacts to a single weigh-in', () => {
  it('two weigh-ins (however different) give no trend', () => {
    expect(weeklyWeightChange(pts([80, 78]))).toBeNull();
  });
  it('a spike inside a real downward trend does not read as flat', () => {
    // 80, 79.4, 78.9 (+1 kg water spike), 78.2 ... overall about -0.4 kg/wk
    const w = weeklyWeightChange(pts([80, 79.4, 79.9, 78.4, 78.0]))!;
    expect(w).toBeLessThan(-0.3);
    const a = computeAdaptation({ ...base, weightPoints: pts([80, 79.4, 79.9, 78.4, 78.0]) });
    expect(a.status).toBe('on_track');
    expect(a.suggestedKcalDelta).toBe(0);
  });
  it('no calorie change with only 3 logged days even if weight looks flat', () => {
    const a = computeAdaptation({ ...base, loggedDailyKcals: [2000, 2000, 2000], weightPoints: pts([80, 80, 80]) });
    expect(a.suggestedKcalDelta).toBe(0);
  });
  it('a genuinely flat 2+ week trend with good adherence suggests a small (150) cut', () => {
    const a = computeAdaptation({ ...base, weightPoints: pts([80, 80.1, 79.9, 80.0]) });
    expect(a.status).toBe('adjust_target');
    expect(a.suggestedKcalDelta).toBe(-150);
  });
  it('adherence problems are addressed before any calorie change', () => {
    const a = computeAdaptation({ ...base, loggedDailyKcals: [2600, 2700, 2500, 2650, 2600], weightPoints: pts([80, 80, 80, 80]) });
    expect(a.status).toBe('adjust_behaviour');
    expect(a.suggestedKcalDelta).toBe(0);
  });
  it('flags too-rapid loss (>1% body weight/week) and never pushes harder', () => {
    const a = computeAdaptation({ ...base, weightPoints: pts([80, 78.6, 77.2, 75.8]) });
    expect(a.status).toBe('review_deficit');
    expect(a.suggestedKcalDelta).toBe(0);
    expect(a.message).toMatch(/doctor or dietitian/);
  });
  it('steady 0.5%/week loss is on track', () => {
    const a = computeAdaptation({ ...base, weightPoints: pts([80, 79.6, 79.2, 78.8]) });
    expect(a.status).toBe('on_track');
  });
});

import { completeLoggedDays } from '../src/modules/chat/adaptive';
describe('adaptation only averages finished, non-fast days', () => {
  const perDay = new Map<string, number>([
    ['2026-09-14', 2900], ['2026-09-15', 506], // Tuesday = fast day
    ['2026-09-16', 2850], ['2026-09-17', 2950], ['2026-09-18', 2880],
    ['2026-09-19', 875], // today, still in progress
  ]);
  it("drops today's partial day and the fast day", () => {
    expect(completeLoggedDays(perDay, '2026-09-19', 2)).toEqual([2900, 2850, 2950, 2880]);
  });
  it('without a fast day only today is dropped', () => {
    expect(completeLoggedDays(perDay, '2026-09-19')).toEqual([2900, 506, 2850, 2950, 2880]);
  });
  it('so a normal week no longer reads as under-eating', () => {
    const days = completeLoggedDays(perDay, '2026-09-19', 2);
    const a = computeAdaptation({ goal: 'lose', targetKcal: 2895, loggedDailyKcals: days, weightPoints: [] });
    expect(a.status).not.toBe('adjust_behaviour');
  });
});
