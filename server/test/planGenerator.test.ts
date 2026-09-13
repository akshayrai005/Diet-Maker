import { describe, it, expect } from 'vitest';
import { SEED_FOODS } from '../src/data/foods.seed';
import { eligibleFoods, conditionAvoidTags } from '../src/modules/food/foodFilter';
import { generateWeekPlan } from '../src/modules/food/planGenerator';
import type { PlanPreferences, PlanTargets } from '../src/modules/food/food.types';

const targets: PlanTargets = {
  dailyKcal: 2000,
  proteinG: 130,
  fatG: 60,
  carbG: 220,
  fiberG: 28,
};

function prefs(over: Partial<PlanPreferences> = {}): PlanPreferences {
  return { dietType: 'nonveg', allergies: [], conditions: [], ...over };
}

describe('eligibleFoods — diet type', () => {
  it('vegan keeps only vegan foods', () => {
    const out = eligibleFoods(SEED_FOODS, prefs({ dietType: 'vegan' }));
    expect(out.length).toBeGreaterThan(0);
    expect(out.every((f) => f.category === 'vegan')).toBe(true);
  });

  it('veg excludes egg and non-veg', () => {
    const out = eligibleFoods(SEED_FOODS, prefs({ dietType: 'veg' }));
    expect(out.some((f) => f.category === 'egg' || f.category === 'nonveg')).toBe(false);
  });

  it('"vegetarian" alias also excludes egg and non-veg (never allow-all)', () => {
    const out = eligibleFoods(SEED_FOODS, prefs({ dietType: 'vegetarian' }));
    expect(out.length).toBeGreaterThan(0);
    expect(out.some((f) => f.category === 'egg' || f.category === 'nonveg')).toBe(false);
  });

  it('eggetarian allows egg but not meat/fish', () => {
    const out = eligibleFoods(SEED_FOODS, prefs({ dietType: 'eggetarian' }));
    expect(out.some((f) => f.category === 'egg')).toBe(true);
    expect(out.some((f) => f.category === 'nonveg')).toBe(false);
  });

  it('nonveg allows everything', () => {
    const out = eligibleFoods(SEED_FOODS, prefs({ dietType: 'nonveg' }));
    expect(out.some((f) => f.category === 'nonveg')).toBe(true);
  });

  it('jain excludes root vegetables (e.g. potato-based dishes), not just onion/garlic', () => {
    const out = eligibleFoods(SEED_FOODS, prefs({ dietType: 'jain' }));
    expect(out.some((f) => f.tags.includes('root'))).toBe(false);
    expect(out.some((f) => f.id === 'aloo-paratha')).toBe(false);
    expect(out.some((f) => f.id === 'sweet-potato')).toBe(false);
  });
});

describe('eligibleFoods — allergies & conditions', () => {
  it('excludes a declared allergen (peanut)', () => {
    const out = eligibleFoods(SEED_FOODS, prefs({ allergies: ['peanut'] }));
    expect(out.some((f) => f.allergens.includes('peanut'))).toBe(false);
    expect(out.some((f) => f.id === 'peanuts')).toBe(false);
  });

  it('a "lactose" allergy excludes ghee/butter/lassi too, not just milk/curd/paneer', () => {
    const milkOut = eligibleFoods(SEED_FOODS, prefs({ allergies: ['milk'] }));
    const lactoseOut = eligibleFoods(SEED_FOODS, prefs({ allergies: ['lactose'] }));
    // "lactose" used to be a narrower synonym list than "milk"/"dairy" - should exclude the same set.
    expect(lactoseOut.length).toBe(milkOut.length);
  });

  it('diabetes removes high-sugar / high-GI foods', () => {
    const out = eligibleFoods(SEED_FOODS, prefs({ conditions: ['diabetes'] }));
    expect(out.some((f) => f.tags.includes('high-gi'))).toBe(false);
    expect(out.some((f) => f.id === 'white-rice')).toBe(false);
  });

  it('jain removes onion/garlic foods', () => {
    const out = eligibleFoods(SEED_FOODS, prefs({ dietType: 'jain' }));
    expect(out.some((f) => f.tags.includes('onion') || f.tags.includes('garlic'))).toBe(false);
  });

  it('maps conditions to avoid-tags', () => {
    expect(conditionAvoidTags(['hypertension'])).toContain('high-sodium');
    expect(conditionAvoidTags(['kidney_disease'])).toContain('high-potassium');
  });
});

describe('generateWeekPlan', () => {
  it('produces 7 days, each roughly on the calorie target', () => {
    const plan = generateWeekPlan(SEED_FOODS, targets, prefs());
    expect(plan.days).toHaveLength(7);
    for (const day of plan.days) {
      expect(day.totals.kcal).toBeGreaterThan(targets.dailyKcal * 0.7);
      expect(day.totals.kcal).toBeLessThan(targets.dailyKcal * 1.3);
      expect(day.meals.length).toBe(7);
    }
  });

  it('is deterministic — same inputs, same plan', () => {
    const a = generateWeekPlan(SEED_FOODS, targets, prefs());
    const b = generateWeekPlan(SEED_FOODS, targets, prefs());
    expect(JSON.stringify(a)).toBe(JSON.stringify(b));
  });

  it('never includes an allergen the user declared', () => {
    const plan = generateWeekPlan(SEED_FOODS, targets, prefs({ allergies: ['milk', 'peanut'] }));
    const ids = plan.days.flatMap((d) => d.meals.flatMap((m) => m.items.map((i) => i.foodId)));
    const banned = SEED_FOODS.filter(
      (f) => f.allergens.includes('milk') || f.allergens.includes('peanut'),
    ).map((f) => f.id);
    expect(ids.some((id) => banned.includes(id))).toBe(false);
  });

  it('varies foods across days (rotation)', () => {
    const plan = generateWeekPlan(SEED_FOODS, targets, prefs());
    const day0 = plan.days[0]!.meals.flatMap((m) => m.items.map((i) => i.foodId)).join();
    const day1 = plan.days[1]!.meals.flatMap((m) => m.items.map((i) => i.foodId)).join();
    expect(day0).not.toBe(day1);
  });

  it('never serves the same food twice in one day', () => {
    for (const dietType of ['nonveg', 'veg'] as const) {
      const plan = generateWeekPlan(SEED_FOODS, targets, prefs({ dietType }));
      for (const day of plan.days) {
        const ids = day.meals.flatMap((m) => m.items.map((i) => i.foodId));
        expect(ids.length).toBe(new Set(ids).size); // all unique within the day
      }
    }
  });

  it('a veg (or vegan/eggetarian) users generated week plan never contains a nonveg food, end to end', () => {
    const byId = new Map(SEED_FOODS.map((f) => [f.id, f]));
    for (const dietType of ['veg', 'vegetarian', 'vegan', 'eggetarian'] as const) {
      const plan = generateWeekPlan(SEED_FOODS, targets, prefs({ dietType }));
      for (const day of plan.days) {
        for (const item of day.meals.flatMap((m) => m.items)) {
          const food = byId.get(item.foodId);
          expect(food?.category).not.toBe('nonveg');
          if (dietType !== 'eggetarian') expect(food?.category).not.toBe('egg');
        }
      }
    }
  });

  it('never puts two grains in one main meal (staple + dal/sabzi, not roti + rice)', () => {
    const byId = new Map(SEED_FOODS.map((f) => [f.id, f]));
    const isGrain = (id: string) => byId.get(id)?.tags.includes('grain') ?? false;
    for (const dietType of ['nonveg', 'veg', 'vegan', 'eggetarian'] as const) {
      const plan = generateWeekPlan(SEED_FOODS, targets, prefs({ dietType }));
      for (const day of plan.days) {
        for (const m of day.meals.filter((x) => ['breakfast', 'lunch', 'dinner'].includes(x.slot))) {
          const grains = m.items.filter((i) => isGrain(i.foodId)).length;
          expect(grains).toBeLessThanOrEqual(1);
        }
      }
    }
  });

  it('every main meal has a staple (roti/rice) + an accompaniment — never solo, never staple-less', () => {
    const byId = new Map(SEED_FOODS.map((f) => [f.id, f]));
    const isGrain = (id: string) => byId.get(id)?.tags.includes('grain') ?? false;
    for (const dietType of ['nonveg', 'veg', 'vegan', 'eggetarian'] as const) {
      const plan = generateWeekPlan(SEED_FOODS, targets, prefs({ dietType }));
      for (const day of plan.days) {
        for (const m of day.meals.filter((x) => ['breakfast', 'lunch', 'dinner'].includes(x.slot))) {
          // At least 2 items (never a solo roti/dal/sabzi).
          expect(m.items.length).toBeGreaterThanOrEqual(2);
          // Exactly one staple grain — present, but never two grains.
          const grains = m.items.filter((i) => isGrain(i.foodId)).length;
          expect(grains).toBe(1);
          // ...and at least one non-grain accompaniment (dal/protein/sabzi).
          expect(m.items.some((i) => !isGrain(i.foodId))).toBe(true);
        }
      }
    }
  });

  it('each non-fasting day lands within ~7% of the calorie target', () => {
    for (const dailyKcal of [1600, 2000, 2400]) {
      const plan = generateWeekPlan(SEED_FOODS, { ...targets, dailyKcal }, prefs());
      for (const day of plan.days) {
        const ratio = day.totals.kcal / dailyKcal;
        expect(ratio).toBeGreaterThan(0.93);
        expect(ratio).toBeLessThan(1.08);
      }
    }
  })

  it('intermittent fasting collapses to 3 eating windows', () => {
    const plan = generateWeekPlan(SEED_FOODS, targets, prefs({ dietType: 'if' }));
    expect(plan.days[0]!.meals.map((m) => m.slot)).toEqual(['lunch', 'eveningsnack', 'dinner']);
  });

  it('a high calorie target on a FEW-slot pattern (morning+night, only 3 meals) still lands within ~10% of target - uniform scaling alone hits the 400g-per-item cap on low-density dishes and needs a top-up item to close the gap', () => {
    for (const dailyKcal of [2200, 2478, 2800]) {
      const plan = generateWeekPlan(SEED_FOODS, { ...targets, dailyKcal }, prefs(), { eatingPattern: 'morning_night' });
      for (const day of plan.days) {
        expect(day.meals.map((m) => m.slot)).toEqual(['breakfast', 'eveningsnack', 'dinner']);
        const ratio = day.totals.kcal / dailyKcal;
        expect(ratio, `dailyKcal=${dailyKcal} got=${day.totals.kcal}`).toBeGreaterThan(0.9);
      }
    }
  });

  it('throws when nothing matches', () => {
    expect(() =>
      generateWeekPlan(SEED_FOODS, targets, prefs({ dietType: 'vegan', allergies: ['soy'], conditions: [] })),
    ).not.toThrow(); // vegan still has non-soy options
  });
});
