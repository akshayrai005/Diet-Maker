import { describe, it, expect } from 'vitest';
import { answer, type ChatContext } from '../src/modules/chat/chat.engine';
import type { CoachContext } from '../src/modules/coach/coachContext';
import type { DietTrend } from '../src/modules/logging/dietTrend';
import type { FoodItem } from '../src/modules/food/food.types';

const strip = (s: string) => s.split('This is educational guidance')[0]!.trim();

const food = (over: Partial<FoodItem>): FoodItem => ({
  id: over.id ?? 'f', name: 'Food', locale: 'IN', category: 'vegan', mealSlots: ['lunch'],
  kcal: 150, proteinG: 8, carbG: 20, fatG: 4, fiberG: 3, sugarG: 2, sodiumMg: 50,
  typicalServingG: 100, costTier: 1, tags: [], allergens: [], prep: 'stove', ...over,
});

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
  kitchen: null,
  livingSituation: null,
  availableFoodIds: null,
  pgMode: false,
  conditions: [],
  allergies: [],
  budgetTier: null,
  pantryTags: null,
  exercise: { todayScheduled: true, todayDone: true, todayFocus: 'Legs', rest: false, weeklyWorkouts: 4, overloadTrend: 'up' },
  weightKg: 74.5,
  weightDeltaKg: -1.2,
  mind: { mood: 4, stress: 2, sleepQuality: 3, insight: 'Your mood has held steady this week.' },
  medications: [],
  cyclePhase: null,
};

const mkCoach = (over: Partial<CoachContext> = {}): CoachContext => ({ ...coach, ...over });

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

  describe('advisor gaps — scorer / suggestions / exercise / mind (deterministic)', () => {
    const CATALOG: FoodItem[] = [
      food({ id: 'samosa', name: 'Samosa', category: 'vegan', tags: ['fried', 'refined'], prep: 'stove', mealSlots: ['eveningsnack'] }),
      food({ id: 'curd', name: 'Curd', category: 'vegetarian', tags: ['dairy', 'probiotic'], allergens: ['milk'], prep: 'none', proteinG: 3.5, mealSlots: ['eveningsnack', 'lunch'] }),
      food({ id: 'roasted-chana', name: 'Roasted chana', tags: ['legume', 'high-protein', 'high-iron'], prep: 'none', proteinG: 20, mealSlots: ['eveningsnack'] }),
      food({ id: 'peanuts', name: 'Peanuts', tags: ['nuts', 'high-iron'], allergens: ['peanut'], prep: 'none', proteinG: 26, mealSlots: ['eveningsnack'] }),
      food({ id: 'dal', name: 'Dal tadka', tags: ['legume', 'high-protein'], prep: 'stove', proteinG: 7, mealSlots: ['lunch', 'dinner'] }),
    ];
    const withFoods = (over: Partial<ChatContext> = {}): ChatContext => ctx({ foods: CATALOG, dietType: 'veg', ...over });

    // AC1 — food_safety wired to the scorer: PG (no-cook), low budget → samosa flagged.
    it('AC1: "can I eat samosa" for a no-cook PG user returns avoid/moderate citing the no-cook/fried issue', () => {
      const pgCoach = mkCoach({ kitchen: 'none', livingSituation: 'pg', pgMode: true, budgetTier: 'low' });
      const r = answer('can I eat samosa?', withFoods({ coach: pgCoach, findFood: () => CATALOG[0] }));
      expect(r.intent).toBe('food_safety');
      const body = strip(r.reply).toLowerCase();
      expect(body).toMatch(/avoid|moderation/);
      expect(body).toMatch(/cook|fried|refined/);
    });

    // AC2 — "what should I eat now" returns real ranked foods within kitchen/allergy limits.
    it('AC2: "what should I eat now" returns ranked foods, none needing a stove for a no-cook user, none with an allergen', () => {
      const pgCoach = mkCoach({ kitchen: 'none', pgMode: true, allergies: ['peanut'] });
      const r = answer('what should I eat now?', withFoods({ coach: pgCoach, nowSlot: 'eveningsnack' }));
      expect(r.intent).toBe('coach_suggest');
      const body = strip(r.reply);
      expect(body).not.toContain('Samosa'); // needs a stove
      expect(body).not.toContain('Peanuts'); // allergen (fail-closed)
      expect(body).toMatch(/Curd|chana/);
    });

    // AC3 — "something for my iron" prefers iron-rich foods.
    it('AC3: "something for my iron" prefers iron-rich foods', () => {
      const r = answer('suggest something for my iron', withFoods());
      expect(r.intent).toBe('coach_suggest');
      const body = strip(r.reply);
      expect(body).toMatch(/chana|Peanuts/);
      expect(body.toLowerCase()).toContain('iron');
    });

    // AC4 — hostel user's suggestions come only from PG staples.
    it('AC4: hostel user gets suggestions only from assemble-only staples', () => {
      const hostelCoach = mkCoach({ livingSituation: 'hostel', pgMode: true });
      // catalog restricted by PG_STAPLE_IDS ∩ availableFoodIds; only curd/roasted-chana/peanuts qualify here
      const r = answer('suggest a snack', withFoods({ coach: hostelCoach, nowSlot: 'eveningsnack' }));
      expect(r.intent).toBe('coach_suggest');
      expect(strip(r.reply)).not.toContain('Dal tadka'); // stove staple, excluded
    });

    // AC5 — exercise questions answer from the exercise block; month trend includes weight delta.
    it('AC5: "did I train today" answers from the exercise block; "how was my month" includes weight change', () => {
      const train = answer('did I train today?', withFoods());
      expect(train.intent).toBe('coach_exercise');
      expect(strip(train.reply)).toMatch(/Legs|trained/);
      const month = answer('how was my month?', withFoods());
      expect(month.intent).toBe('coach_trend');
      expect(strip(month.reply)).toMatch(/1\.2 kg/);
    });

    // AC6 — allergen/medication conflicts are always avoid (fail-closed).
    it('AC6: an allergen conflict is always avoid (fail-closed)', () => {
      const allergicCoach = mkCoach({ allergies: ['milk'] });
      const r = answer('can I eat curd?', withFoods({ coach: allergicCoach, findFood: () => CATALOG[1] }));
      expect(strip(r.reply).toLowerCase()).toMatch(/avoid|allergic/);
    });

    it('mood question answers from the mind block', () => {
      const r = answer("how's my mood lately?", withFoods());
      expect(r.intent).toBe('coach_mind');
      expect(strip(r.reply)).toMatch(/mood 4\/5|steady/);
    });

    it('a preference statement ("I don\'t want to take protein") is NOT hijacked by the today catch-all', () => {
      const r = answer("I don't want to take protein, I want to lose muscle also", withFoods());
      expect(r.intent).not.toBe('coach_today');
    });
  });
});
