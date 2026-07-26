import { describe, it, expect } from 'vitest';
import { suggestFoodsForDeficiencies } from '../src/modules/nutrition/deficiencyFoods';

describe('suggestFoodsForDeficiencies', () => {
  it('suggests specific foods for a real deficiency', () => {
    const r = suggestFoodsForDeficiencies(['ironMg'], { dietType: 'nonveg' });
    expect(r).toHaveLength(1);
    expect(r[0]!.key).toBe('ironMg');
    expect(r[0]!.foods.length).toBeGreaterThan(0);
    expect(r[0]!.note).toMatch(/educational/i);
  });

  it('vegan diet excludes all animal sources', () => {
    const r = suggestFoodsForDeficiencies(['vitaminB12Mcg'], { dietType: 'vegan' });
    const foods = r[0]?.foods ?? [];
    expect(foods.join(' ')).not.toMatch(/egg|fish|milk|curd|liver|chicken/i);
    // still offers a plant/fortified option
    expect(foods.join(' ')).toMatch(/fortified|yeast/i);
  });

  it('vegetarian excludes egg/fish/meat but keeps dairy', () => {
    const r = suggestFoodsForDeficiencies(['calciumMg'], { dietType: 'veg' });
    const foods = r[0]!.foods.join(' ');
    expect(foods).not.toMatch(/fish|chicken|egg/i);
    expect(foods).toMatch(/milk|curd|paneer|ragi|sesame|tofu/i);
  });

  it('respects allergies (dairy allergy removes milk/paneer)', () => {
    const r = suggestFoodsForDeficiencies(['calciumMg'], { dietType: 'veg', allergies: ['dairy'] });
    const foods = r[0]!.foods.join(' ');
    expect(foods).not.toMatch(/milk|curd|paneer/i);
    expect(foods).toMatch(/ragi|sesame|tofu/i);
  });

  it('Jain excludes roots/allium (no beetroot for folate)', () => {
    const r = suggestFoodsForDeficiencies(['folateMcg'], { dietType: 'jain' });
    expect(r[0]!.foods.join(' ')).not.toMatch(/beetroot/i);
  });

  it('budget=low prefers cheap sources first', () => {
    const r = suggestFoodsForDeficiencies(['ironMg'], { dietType: 'nonveg', budgetTier: 'low', perNutrient: 2 });
    // top picks should be the low-budget spinach/rajma/dates, not mid-cost seeds/liver
    expect(r[0]!.foods.every((f) => /spinach|rajma|kidney|dates/i.test(f))).toBe(true);
  });

  it('skips deficiencies with no known source list (honest silence)', () => {
    const r = suggestFoodsForDeficiencies(['manganeseMg'], { dietType: 'nonveg' });
    expect(r).toHaveLength(0);
  });

  it('handles multiple deficiencies', () => {
    const r = suggestFoodsForDeficiencies(['ironMg', 'vitaminCMg'], { dietType: 'veg' });
    expect(r.map((x) => x.key)).toEqual(['ironMg', 'vitaminCMg']);
  });
});
