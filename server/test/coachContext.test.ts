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

  it('answers a vague / misspelled consumption question from the log ("total meal today cards I took")', () => {
    const r = answer('in total meal today which I consumed how much cards I took?', ctx());
    expect(r.intent).toBe('coach_today');
    const body = strip(r.reply);
    expect(body).toContain('160 g carbs');
    expect(body.toLowerCase()).not.toContain('access');
  });

  describe('prescriptive plan questions (coach_plan) — targets, not today\'s log', () => {
    const planTargets = {
      dailyKcal: 2082, proteinG: 127, waterMl: 2950,
      carbG: 210, fatG: 62, fiberG: 30, tdee: 2540, bmr: 1600, safeWeeklyDeltaKg: -0.5, goal: 'lose',
    };
    const planCtx = ctx({ targets: planTargets });

    it('answers "how many calories to lose fat" with maintenance + deficit target, not today\'s totals', () => {
      const r = answer('I want exact plan, total calories body required and calories I need to take to loose fat', planCtx);
      expect(r.intent).toBe('coach_plan');
      const body = strip(r.reply);
      expect(body).toContain('2540 kcal'); // maintenance (TDEE)
      expect(body).toContain('2082 kcal'); // fat-loss target
      expect(body).toContain('458 kcal'); // the deficit
      expect(body.toLowerCase()).not.toContain('so far today');
    });

    it('gives the full macro split when asked for carbs/fat/protein targets', () => {
      const r = answer('how much carbs fat protein should I take daily?', planCtx);
      expect(r.intent).toBe('coach_plan');
      const body = strip(r.reply);
      expect(body).toContain('protein 127 g');
      expect(body).toContain('carbs 210 g');
      expect(body).toContain('fat 62 g');
    });

    it('does NOT hijack a retrospective "carbs today" question', () => {
      const r = answer('how many carbs did I take today?', planCtx);
      expect(r.intent).toBe('coach_today');
    });
  });
});
