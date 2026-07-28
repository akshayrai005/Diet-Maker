import { describe, it, expect } from 'vitest';
import { eligibleFoods } from '../src/modules/food/foodFilter';
import { inferPrep, isPgMode, PG_STAPLE_IDS } from '../src/modules/food/prep';
import { SEED_FOODS } from '../src/data/foods.seed';
import type { FoodItem, PlanPreferences } from '../src/modules/food/food.types';

const prefs = (over: Partial<PlanPreferences> = {}): PlanPreferences => ({
  dietType: 'veg', allergies: [], conditions: [], locale: 'IN', ...over,
});

describe('Feature 3 — PG / no-cook plans', () => {
  // AC1: prep is inferred/carried for every food; assemble-only staples are 'none'.
  it('AC1: assemble-only staples are prep "none"; cooked dishes default to "stove"', () => {
    const byId = (id: string) => SEED_FOODS.find((f) => f.id === id)!;
    expect(inferPrep(byId('curd'))).toBe('none');
    expect(inferPrep(byId('banana'))).toBe('none');
    expect(inferPrep(byId('sattu'))).toBe('none');
    expect(inferPrep(byId('dal-tadka'))).toBe('stove');
    expect(inferPrep(byId('white-rice'))).toBe('stove');
  });

  // AC2: a no-kitchen user never gets a food that needs cooking.
  it('AC2: kitchen "none" drops every food that needs more than assembling', () => {
    const eligible = eligibleFoods(SEED_FOODS, prefs({ dietType: 'nonveg', kitchen: 'none' }));
    expect(eligible.length).toBeGreaterThan(0);
    for (const f of eligible) expect(inferPrep(f)).toBe('none');
  });

  // AC3: kettle access allows 'none' + 'kettle' but still no stove dishes.
  it('AC3: kitchen "kettle" allows hot-water foods but no stove dishes', () => {
    const eligible = eligibleFoods(SEED_FOODS, prefs({ dietType: 'nonveg', kitchen: 'kettle' }));
    const preps = new Set(eligible.map((f) => inferPrep(f)));
    expect(preps.has('stove')).toBe(false);
    expect([...preps].every((p) => p === 'none' || p === 'kettle')).toBe(true);
  });

  // AC4: availableFoodIds intersects — only foods the user actually has.
  it('AC4: availableFoodIds restricts the plan to what the user has on hand', () => {
    const eligible = eligibleFoods(SEED_FOODS, prefs({ availableFoodIds: ['curd', 'banana', 'sattu'] }));
    expect(eligible.map((f) => f.id).sort()).toEqual(['banana', 'curd', 'sattu']);
  });

  // AC5: PG staples exist in the catalog and need no more than a kettle (never a stove).
  it('AC5: every PG staple is present and needs no more than hot water', () => {
    for (const id of PG_STAPLE_IDS) {
      const f = SEED_FOODS.find((x) => x.id === id);
      expect(f, `missing PG staple ${id}`).toBeTruthy();
      expect(inferPrep(f as FoodItem)).not.toBe('stove');
    }
  });

  // AC6: isPgMode triggers on living situation OR low kitchen access.
  it('AC6: PG mode is detected from living situation or kitchen access', () => {
    expect(isPgMode({ livingSituation: 'pg' })).toBe(true);
    expect(isPgMode({ livingSituation: 'hostel' })).toBe(true);
    expect(isPgMode({ kitchen: 'none' })).toBe(true);
    expect(isPgMode({ kitchen: 'kettle' })).toBe(true);
    expect(isPgMode({ livingSituation: 'home', kitchen: 'stove' })).toBe(false);
  });

  // AC7: PG staples still provide a real high-protein option (whey/paneer/eggs/sattu).
  it('AC7: PG staples include enough protein to build a plan', () => {
    const staples = SEED_FOODS.filter((f) => (PG_STAPLE_IDS as readonly string[]).includes(f.id));
    const highProtein = staples.filter((f) => f.proteinG >= 8);
    expect(highProtein.length).toBeGreaterThanOrEqual(4);
  });

  // AC8: allergen + kitchen filters compose (a dairy-allergic PG user gets no milk staples).
  it('AC8: allergen and kitchen filters compose safely', () => {
    const eligible = eligibleFoods(SEED_FOODS, prefs({ kitchen: 'none', allergies: ['dairy'] }));
    expect(eligible.length).toBeGreaterThan(0);
    for (const f of eligible) {
      expect(inferPrep(f)).toBe('none');
      expect(f.allergens).not.toContain('milk');
    }
  });
});
