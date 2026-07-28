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
  it('AC2: kitchen "none" does NOT drop cooked foods — a PG/mess user still gets roti/dal/rice', () => {
    const eligible = eligibleFoods(SEED_FOODS, prefs({ dietType: 'nonveg', kitchen: 'none' }));
    const ids = eligible.map((f) => f.id);
    // Cooked Indian staples remain available — not cooking yourself ≠ assemble-only.
    expect(ids).toContain('roti');
    expect(ids).toContain('dal-tadka');
    expect(ids).toContain('white-rice');
    expect(eligible.some((f) => inferPrep(f) === 'stove')).toBe(true);
  });

  // AC3: kettle access is the same — the menu isn't restricted by how you cook.
  it('AC3: kitchen "kettle" also keeps the full Indian menu', () => {
    const eligible = eligibleFoods(SEED_FOODS, prefs({ dietType: 'nonveg', kitchen: 'kettle' }));
    expect(eligible.map((f) => f.id)).toContain('dal-tadka');
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

  // AC6: assemble-only PG mode is reserved for a genuine NO-kitchen situation — so a PG/hostel
  // student with any way to cook still gets full Indian plans (roti+dal, rice+sabji).
  it('AC6: PG assemble-only mode triggers only for kitchen "none"', () => {
    expect(isPgMode({ kitchen: 'none' })).toBe(true);
    expect(isPgMode({ livingSituation: 'pg' })).toBe(false);
    expect(isPgMode({ livingSituation: 'hostel' })).toBe(false);
    expect(isPgMode({ kitchen: 'kettle' })).toBe(false);
    expect(isPgMode({ livingSituation: 'home', kitchen: 'stove' })).toBe(false);
  });

  // AC7: PG staples still provide a real high-protein option (whey/paneer/eggs/sattu).
  it('AC7: PG staples include enough protein to build a plan', () => {
    const staples = SEED_FOODS.filter((f) => (PG_STAPLE_IDS as readonly string[]).includes(f.id));
    const highProtein = staples.filter((f) => f.proteinG >= 8);
    expect(highProtein.length).toBeGreaterThanOrEqual(4);
  });

  // AC8: allergen filtering still applies (fail-closed) even though kitchen no longer restricts —
  // a dairy-allergic user gets no milk foods, but still gets cooked non-dairy Indian meals.
  it('AC8: allergen filter still excludes milk foods, while cooked non-dairy foods remain', () => {
    const eligible = eligibleFoods(SEED_FOODS, prefs({ kitchen: 'none', allergies: ['dairy'] }));
    expect(eligible.length).toBeGreaterThan(0);
    for (const f of eligible) expect(f.allergens).not.toContain('milk');
    expect(eligible.map((f) => f.id)).toContain('dal-tadka'); // cooked, non-dairy → still available
  });
});
