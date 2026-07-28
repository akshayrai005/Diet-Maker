import { describe, it, expect } from 'vitest';
import { aggregateFoodFrequency, type FoodLogRow } from '../src/modules/logging/foodFrequency';
import { aggregateDietTrend, type TrendLogRow } from '../src/modules/logging/dietTrend';

const day = (iso: string) => new Date(`${iso}T12:00:00Z`);
const log = (name: string, iso: string, kcal = 100, foodId: string | null = null, tags: string[] = []): FoodLogRow => ({
  foodId, foodName: name, grams: 100, kcal, tags, loggedAt: day(iso),
});

describe('aggregateFoodFrequency — "how many times have I eaten X"', () => {
  const now = day('2026-07-28');
  const rows: FoodLogRow[] = [
    log('Samosa', '2026-07-01', 260, null, ['fried', 'refined']),
    log('samosa', '2026-07-20'),
    log('Samosa', '2026-07-27'),
    log('Dal tadka', '2026-07-15', 120, 'dal-tadka'),
    log('Banana', '2026-07-26', 89, 'banana'),
  ];
  const freq = aggregateFoodFrequency(rows, now);

  it('counts a food across case variations and ranks by times', () => {
    expect(freq[0]!.name.toLowerCase()).toBe('samosa');
    expect(freq[0]!.times).toBe(3);
    expect(freq[0]!.firstAt.slice(0, 10)).toBe('2026-07-01');
    expect(freq[0]!.lastAt.slice(0, 10)).toBe('2026-07-27');
  });

  it('flags an unhealthy-tagged frequent food as a habit to watch', () => {
    expect(freq.find((f) => f.name.toLowerCase() === 'samosa')!.unhealthy).toBe(true);
    expect(freq.find((f) => f.name === 'Banana')!.unhealthy).toBe(false);
  });

  it('per-week average is computed over the span since first log', () => {
    // samosa: 3 times across ~4 weeks → ~0.7-0.8/week
    const s = freq.find((f) => f.name.toLowerCase() === 'samosa')!;
    expect(s.perWeekAvg).toBeGreaterThan(0);
    expect(s.perWeekAvg).toBeLessThan(2);
  });

  it('empty logs → empty list', () => {
    expect(aggregateFoodFrequency([], now)).toEqual([]);
  });
});

describe('aggregateDietTrend — weekly / monthly aggregates', () => {
  const foodRow = (iso: string, kcal: number, protein: number): TrendLogRow => ({ loggedAt: day(iso), kcal, proteinG: protein, carbG: 0, fatG: 0 });

  it('averages only over days that had food logged', () => {
    const food = [foodRow('2026-07-20', 2000, 100), foodRow('2026-07-20', 200, 10), foodRow('2026-07-22', 1800, 90)];
    const t = aggregateDietTrend(7, 0, food, [], []);
    expect(t.daysLogged).toBe(2);
    expect(t.avgKcal).toBe(2000); // (2200 + 1800) / 2
    expect(t.avgProteinG).toBe(100); // (110 + 90) / 2
  });

  it('reports improving when protein adherence rises over the window', () => {
    const food = [foodRow('2026-07-01', 1800, 60), foodRow('2026-07-02', 1800, 62), foodRow('2026-07-20', 1900, 110), foodRow('2026-07-21', 1900, 115)];
    expect(aggregateDietTrend(30, 0, food, [], []).direction).toBe('improving');
  });

  it('counts workout days', () => {
    const food = [foodRow('2026-07-20', 2000, 100)];
    const t = aggregateDietTrend(7, 0, food, [], [{ performedAt: day('2026-07-20') }, { performedAt: day('2026-07-21') }]);
    expect(t.workoutDays).toBe(2);
  });

  it('too few days → insufficient trend, never a fabricated direction', () => {
    expect(aggregateDietTrend(7, 0, [foodRow('2026-07-20', 2000, 100)], [], []).direction).toBe('insufficient');
  });
});
