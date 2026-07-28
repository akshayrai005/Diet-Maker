import { describe, it, expect } from 'vitest';
import { answer, type ChatContext } from '../src/modules/chat/chat.engine';
import type { CoachContext } from '../src/modules/coach/coachContext';
import type { DietTrend } from '../src/modules/logging/dietTrend';

const strip = (s: string) => s.split('This is educational guidance')[0]!.trim();

const trend = (over: Partial<DietTrend>): DietTrend => ({
  windowDays: 7,
  days: [],
  daysLogged: 5,
  avgKcal: 1900,
  avgProteinG: 95,
  avgWaterMl: 2000,
  workoutDays: 3,
  direction: 'improving',
  ...over,
});

const coach: CoachContext = {
  today: {
    kcal: 1450, targetKcal: 2000, remainingKcal: 550,
    proteinG: 82, targetProteinG: 120, carbG: 160, fatG: 45,
    fiberG: 22, sugarG: 30, sodiumMg: 1800, waterMl: 1500, targetWaterMl: 2500,
    burnedKcal: 300, netKcal: 1150, streakDays: 6,
  },
  todayFoods: [
    { name: 'Oats', slot: 'breakfast', grams: 60, kcal: 230 },
    { name: 'Paneer', slot: 'lunch', grams: 100, kcal: 265 },
  ],
  week: trend({ windowDays: 7 }),
  month: trend({ windowDays: 30, daysLogged: 22, avgKcal: 2050, avgProteinG: 88, workoutDays: 10, direction: 'steady' }),
  topFoods: [
    { foodId: null, name: 'Samosa', times: 9, totalGrams: 900, totalKcal: 2340, firstAt: '2026-05-01T00:00:00.000Z', lastAt: '2026-07-27T00:00:00.000Z', perWeekAvg: 0.8, unhealthy: true },
    { foodId: 'oats', name: 'Oats', times: 20, totalGrams: 1200, totalKcal: 4600, firstAt: '2026-05-01T00:00:00.000Z', lastAt: '2026-07-28T00:00:00.000Z', perWeekAvg: 1.6, unhealthy: false },
  ],
  frequency: [
    { foodId: null, name: 'Samosa', times: 9, totalGrams: 900, totalKcal: 2340, firstAt: '2026-05-01T00:00:00.000Z', lastAt: '2026-07-27T00:00:00.000Z', perWeekAvg: 0.8, unhealthy: true },
    { foodId: 'oats', name: 'Oats', times: 20, totalGrams: 1200, totalKcal: 4600, firstAt: '2026-05-01T00:00:00.000Z', lastAt: '2026-07-28T00:00:00.000Z', perWeekAvg: 1.6, unhealthy: false },
  ],
  deficiencies: ['Iron', 'Vitamin B12'],
};

const ctx = (over: Partial<ChatContext> = {}): ChatContext => ({
  targets: { dailyKcal: 2000, proteinG: 120, waterMl: 2500 },
  conditions: [],
  findFood: () => undefined,
  coach,
  ...over,
});

describe('coach answers from whole-app context (deterministic, AI_PROVIDER=rules)', () => {
  it('answers "how much carbs and fat did I take today" from the log — never "I cannot access it"', () => {
    const carbs = strip(answer('how many carbs did I take today?', ctx()).reply);
    expect(carbs).toContain('160 g');
    expect(carbs.toLowerCase()).not.toContain("can't access");
    const fat = answer('how much fat today?', ctx());
    expect(fat.intent).toBe('coach_today');
    expect(strip(fat.reply)).toContain('45 g');
  });

  it('lists what the user ate today with running totals', () => {
    const r = answer('what did I eat today?', ctx());
    expect(r.intent).toBe('coach_today');
    const body = strip(r.reply);
    expect(body).toContain('Oats');
    expect(body).toContain('Paneer');
    expect(body).toContain('1450 kcal');
  });

  it('summarises the week trend', () => {
    const r = answer("how's my week going?", ctx());
    expect(r.intent).toBe('coach_trend');
    const body = strip(r.reply);
    expect(body).toContain('7 days');
    expect(body).toContain('1900 kcal');
  });

  it('summarises the month trend when asked about 30 days', () => {
    const r = answer('how has my month been?', ctx());
    expect(r.intent).toBe('coach_trend');
    expect(strip(r.reply)).toContain('30 days');
  });

  it('answers "how many times have I eaten samosa" with the exact count', () => {
    const r = answer('how many times have I eaten samosa?', ctx());
    expect(r.intent).toBe('coach_frequency');
    expect(strip(r.reply)).toContain('9 times');
  });

  it('flags foods eaten too often, surfacing the habit-to-watch', () => {
    const r = answer('what do I eat too often?', ctx());
    expect(r.intent).toBe('coach_habits');
    const body = strip(r.reply);
    expect(body).toContain('Samosa');
    expect(body.toLowerCase()).toContain('watch');
  });

  it('degrades gracefully when coach context is absent (no crash, falls through)', () => {
    const r = answer('how many carbs today?', ctx({ coach: null }));
    // Falls through to the generic targets intent rather than throwing.
    expect(['targets', 'fallback']).toContain(r.intent);
  });

  it('handles a never-logged food honestly', () => {
    const r = answer('how many times have I eaten pizza?', ctx());
    expect(r.intent).toBe('coach_frequency');
    expect(strip(r.reply).toLowerCase()).toContain("haven't logged");
  });
});
