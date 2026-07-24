import { describe, it, expect } from 'vitest';
import { orderByPreference } from '../src/modules/food/planGenerator';
import type { FoodItem, PlanPreferences } from '../src/modules/food/food.types';

function food(over: Partial<FoodItem> & { id: string; costTier: 1 | 2 | 3 }): FoodItem {
  return {
    name: over.id,
    locale: 'IN',
    category: 'vegan',
    mealSlots: ['lunch'],
    kcal: 100,
    proteinG: 5,
    carbG: 10,
    fatG: 2,
    fiberG: 2,
    sugarG: 1,
    sodiumMg: 10,
    typicalServingG: 100,
    tags: [],
    allergens: [],
    ...over,
  };
}

const base: PlanPreferences = { dietType: 'nonveg', allergies: [], conditions: [] };

describe('orderByPreference', () => {
  const cheap = food({ id: 'cheap', costTier: 1 });
  const mid = food({ id: 'mid', costTier: 2 });
  const pricey = food({ id: 'pricey', costTier: 3 });

  it('prefers cheaper foods when budget is low', () => {
    const out = orderByPreference([pricey, mid, cheap], { ...base, budgetTier: 'low' });
    expect(out.map((f) => f.id)).toEqual(['cheap', 'mid', 'pricey']);
  });

  it('does not reorder by cost when budget is flexible (stable)', () => {
    const out = orderByPreference([pricey, mid, cheap], { ...base, budgetTier: 'flexible' });
    expect(out.map((f) => f.id)).toEqual(['pricey', 'mid', 'cheap']);
  });

  it('floats pantry foods to the front', () => {
    const oats = food({ id: 'oats', costTier: 2, tags: ['grain'] });
    const rice = food({ id: 'rice', costTier: 1, tags: ['grain'] });
    const out = orderByPreference([rice, oats], { ...base, pantryTags: ['oats'] });
    expect(out[0]!.id).toBe('oats');
  });

  it('biases toward the requested locale', () => {
    const local = food({ id: 'local', costTier: 2, locale: 'IN' });
    const foreign = food({ id: 'foreign', costTier: 2, locale: 'global' });
    const out = orderByPreference([foreign, local], { ...base, locale: 'IN' });
    expect(out[0]!.id).toBe('local');
  });

  it('is deterministic and preserves order on ties', () => {
    const a = orderByPreference([cheap, mid, pricey], base);
    const b = orderByPreference([cheap, mid, pricey], base);
    expect(a.map((f) => f.id)).toEqual(b.map((f) => f.id));
    expect(a.map((f) => f.id)).toEqual(['cheap', 'mid', 'pricey']); // no budget bias → original order
  });
});
