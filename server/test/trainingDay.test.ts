import { describe, it, expect } from 'vitest';
import { SEED_FOODS } from '../src/data/foods.seed';
import { generateWeekPlan, applyTraining } from '../src/modules/food/planGenerator';
import { TRAIN_TIMES, type PlanPreferences, type TrainTime } from '../src/modules/food/food.types';

const prefs: PlanPreferences = { dietType: 'nonveg', allergies: [], conditions: [] };
const targets = { dailyKcal: 2600, proteinG: 150, fatG: 70, carbG: 300, fiberG: 30 };
const start = new Date('2026-09-21T00:00:00Z');

const plan = (training?: Record<string, TrainTime>, eatingPattern?: string) =>
  generateWeekPlan(SEED_FOODS, targets, prefs, { days: 2, startDate: start, today: start, training, eatingPattern });

describe('meals follow the gym plan', () => {
  it('a rest day has no workout tags', () => {
    const d = plan().days[0]!;
    expect(d.training).toBeUndefined();
    expect(d.meals.some((m) => m.tag)).toBe(false);
  });

  for (const when of TRAIN_TIMES) {
    it(`${when}: one pre-workout and one post-workout meal, day still hits the target, only that day changes`, () => {
      const p = plan({ '2026-09-21': when });
      const today = p.days[0]!;
      const tomorrow = p.days[1]!;
      expect(today.training).toBe(when);
      expect(today.meals.filter((m) => m.tag === 'pre-workout')).toHaveLength(1);
      expect(today.meals.filter((m) => m.tag === 'post-workout')).toHaveLength(1);
      expect(today.meals.every((m) => !m.tag || m.note)).toBe(true);
      expect(Math.abs(today.totals.kcal - targets.dailyKcal)).toBeLessThan(targets.dailyKcal * 0.1);
      expect(tomorrow.training).toBeUndefined();
      // the pre-workout meal is a light one, never the biggest of the day
      const pre = today.meals.find((m) => m.tag === 'pre-workout')!;
      expect(pre.kcal).toBeLessThan(Math.max(...today.meals.map((m) => m.kcal)));
    });
  }

  it('morning trainers get a wake-up snack and a protein breakfast; night trainers a bedtime meal and a lighter dinner', () => {
    const morning = plan({ '2026-09-21': 'morning' }).days[0]!;
    expect(morning.meals.find((m) => m.tag === 'pre-workout')!.slot).toBe('wakeup');
    expect(morning.meals.find((m) => m.tag === 'post-workout')!.slot).toBe('breakfast');
    const night = plan({ '2026-09-21': 'night' }).days[0]!;
    expect(night.meals.find((m) => m.tag === 'post-workout')!.slot).toBe('bedtime');
    const rest = plan().days[0]!;
    const dinner = (d: typeof rest) => d.meals.find((m) => m.slot === 'dinner')!.kcal;
    expect(dinner(night)).toBeLessThan(dinner(rest));
  });

  it('works with a working-day eating pattern too', () => {
    const d = plan({ '2026-09-21': 'evening' }, 'office_canteen').days[0]!;
    expect(d.meals.find((m) => m.tag === 'post-workout')!.slot).toBe('dinner');
    expect(d.meals.filter((m) => m.tag).length).toBe(2);
  });

  it('applyTraining keeps slots in day order', () => {
    const r = applyTraining(['breakfast', 'lunch', 'dinner'], { breakfast: 0.3, lunch: 0.3, dinner: 0.3 }, ['breakfast', 'lunch', 'dinner'], 'night');
    expect(r.slots).toEqual(['breakfast', 'lunch', 'eveningsnack', 'dinner', 'bedtime']);
  });
});
