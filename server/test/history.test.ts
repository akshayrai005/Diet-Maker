import { describe, it, expect } from 'vitest';
import { buildDailyHistory, type HistFoodRow } from '../src/modules/logging/history';

const at = (iso: string) => new Date(`${iso}T12:00:00Z`);
const now = at('2026-07-28');

describe('buildDailyHistory — complete per-day series for the trends screen', () => {
  it('emits one entry per calendar day in the window, oldest → newest, zeros for gaps', () => {
    const h = buildDailyHistory(7, 0, [], [], [], [], now);
    expect(h.days.length).toBe(7);
    expect(h.days[0]!.date).toBe('2026-07-22');
    expect(h.days[6]!.date).toBe('2026-07-28');
    expect(h.days.every((d) => d.kcal === 0 && d.waterMl === 0 && !d.workout)).toBe(true);
  });

  it('buckets food, water and workouts into the right day', () => {
    const food: HistFoodRow[] = [
      { loggedAt: at('2026-07-27'), kcal: 500, proteinG: 30 },
      { loggedAt: at('2026-07-27'), kcal: 700, proteinG: 40 },
      { loggedAt: at('2026-07-28'), kcal: 300, proteinG: 20 },
    ];
    const water = [{ loggedAt: at('2026-07-28'), amountMl: 2000 }];
    const exercise = [{ performedAt: at('2026-07-27') }];
    const h = buildDailyHistory(7, 0, food, water, exercise, [], now);
    const d27 = h.days.find((d) => d.date === '2026-07-27')!;
    const d28 = h.days.find((d) => d.date === '2026-07-28')!;
    expect(d27.kcal).toBe(1200);
    expect(d27.proteinG).toBe(70);
    expect(d27.workout).toBe(true);
    expect(d28.kcal).toBe(300);
    expect(d28.waterMl).toBe(2000);
    expect(d28.workout).toBe(false);
  });

  it('includes only weight points inside the window, sorted oldest → newest', () => {
    const weight = [
      { date: '2026-07-01', weightKg: 76 }, // before 7-day window → excluded
      { date: '2026-07-27', weightKg: 74.5 },
      { date: '2026-07-23', weightKg: 75 },
    ];
    const h = buildDailyHistory(7, 0, [], [], [], weight, now);
    expect(h.weight.map((w) => w.date)).toEqual(['2026-07-23', '2026-07-27']);
  });

  it('ignores logs outside the window (no matching day bucket)', () => {
    const food: HistFoodRow[] = [{ loggedAt: at('2026-06-01'), kcal: 999, proteinG: 99 }];
    const h = buildDailyHistory(7, 0, food, [], [], [], now);
    expect(h.days.reduce((s, d) => s + d.kcal, 0)).toBe(0);
  });
});
