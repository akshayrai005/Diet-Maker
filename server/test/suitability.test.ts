import { describe, it, expect } from 'vitest';
import { assessFoodSuitability, rankSuitable } from '../src/modules/food/suitability';
import type { FoodItem } from '../src/modules/food/food.types';

const food = (over: Partial<FoodItem>): FoodItem => ({
  id: over.id ?? 'f', name: 'Food', locale: 'IN', category: 'vegan', mealSlots: ['lunch'],
  kcal: 150, proteinG: 8, carbG: 20, fatG: 4, fiberG: 3, sugarG: 2, sodiumMg: 50,
  typicalServingG: 100, costTier: 1, tags: [], allergens: [], prep: 'stove', ...over,
});

describe('assessFoodSuitability — consolidated scorer', () => {
  it('blocks an allergen (fail-closed) regardless of other merits', () => {
    const v = assessFoodSuitability(food({ name: 'Paneer bhurji', allergens: ['milk'] }), { allergies: ['dairy'] });
    expect(v.verdict).toBe('avoid');
    expect(v.ok).toBe(false);
    expect(v.reasons[0]).toMatch(/allergic/i);
  });

  it('blocks a diet-incompatible food', () => {
    const v = assessFoodSuitability(food({ category: 'nonveg', name: 'Chicken' }), { dietType: 'veg' });
    expect(v.verdict).toBe('avoid');
  });

  it('flags a high-sugar food as avoid for diabetes', () => {
    const v = assessFoodSuitability(food({ name: 'Gulab jamun', tags: ['high-sugar'] }), { conditions: ['diabetes'] });
    expect(v.verdict).toBe('avoid');
    expect(v.reasons.join(' ')).toMatch(/high-sugar/);
  });

  it('computes the portion and its calories', () => {
    const v = assessFoodSuitability(food({ kcal: 200, typicalServingG: 150 }), {});
    expect(v.portionG).toBe(150);
    expect(v.kcalForPortion).toBe(300); // 200 * 150/100
    const v2 = assessFoodSuitability(food({ kcal: 200 }), { portionG: 50 });
    expect(v2.kcalForPortion).toBe(100);
  });

  it('marks a food beyond the kitchen access as avoid', () => {
    const v = assessFoodSuitability(food({ name: 'Dal', prep: 'stove' }), { kitchen: 'none' });
    expect(v.verdict).toBe('avoid');
    expect(v.reasons[0]).toMatch(/cooking/);
    // an assemble-only food is fine with no kitchen
    expect(assessFoodSuitability(food({ name: 'Curd', prep: 'none' }), { kitchen: 'none' }).verdict).not.toBe('avoid');
  });

  it('flags a food-drug interaction (warfarin ↔ vitamin-K greens)', () => {
    const v = assessFoodSuitability(food({ name: 'Spinach', tags: ['high-vitamin-k'] }), { medications: ['warfarin'] });
    expect(v.verdict).toBe('avoid');
    expect(v.reasons.join(' ')).toMatch(/vitamin-K|blood thinner/i);
  });

  it('excludes a food not in the available set', () => {
    const v = assessFoodSuitability(food({ id: 'x' }), { availableFoodIds: ['a', 'b'] });
    expect(v.verdict).toBe('avoid');
  });

  it('lifts a soft-flagged food to good when it fills a micronutrient gap', () => {
    // pricier than budget → moderate; but it fills the iron gap → back to good
    const v = assessFoodSuitability(food({ name: 'Spinach', costTier: 3, tags: ['high-iron'] }), { budgetTier: 'low', microGaps: ['iron'] });
    expect(v.verdict).toBe('good');
    expect(v.reasons.join(' ')).toMatch(/iron/);
  });

  it('rankSuitable drops avoid foods and returns the rest best-first', () => {
    const foods = [
      food({ id: 'junk', name: 'Gulab jamun', tags: ['high-sugar'] }),
      food({ id: 'good', name: 'Moong dal', fiberG: 8, proteinG: 12 }),
    ];
    const out = rankSuitable(foods, { conditions: ['diabetes'] }, { limit: 5 });
    expect(out.map((o) => o.food.id)).toEqual(['good']);
  });
});
