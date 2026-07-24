import { describe, it, expect } from 'vitest';
import { estimateMicronutrientsPer100g } from '../src/calc/micronutrientEstimate';

describe('estimateMicronutrientsPer100g', () => {
  it('gives leafy greens high iron and folate', () => {
    const m = estimateMicronutrientsPer100g({ name: 'Palak (spinach, cooked)', tags: ['vegetable'] });
    expect(m.ironMg).toBeGreaterThan(2);
    expect(m.folateMcg).toBeGreaterThan(100);
  });

  it('gives dairy high calcium and B12', () => {
    const m = estimateMicronutrientsPer100g({ name: 'Paneer', tags: ['dairy'] });
    expect(m.calciumMg).toBeGreaterThan(150);
    expect(m.vitaminB12Mcg).toBeGreaterThan(0.5);
  });

  it('flags ragi as exceptionally calcium-rich', () => {
    const m = estimateMicronutrientsPer100g({ name: 'Ragi malt', tags: ['grain'] });
    expect(m.calciumMg).toBeGreaterThan(300);
  });

  it('gives egg B12 and vitamin D', () => {
    const m = estimateMicronutrientsPer100g({ name: 'Boiled egg', tags: [], category: 'egg' });
    expect(m.vitaminB12Mcg).toBeGreaterThan(0.5);
    expect(m.vitaminDMcg).toBeGreaterThan(0);
  });

  it('returns all-zero for a nutrient-poor item', () => {
    const m = estimateMicronutrientsPer100g({ name: 'Green tea (unsweetened)', tags: ['beverage'] });
    expect(m.ironMg).toBe(0);
    expect(m.calciumMg).toBe(0);
  });

  it('is deterministic', () => {
    const a = estimateMicronutrientsPer100g({ name: 'Dal tadka', tags: ['legume'] });
    const b = estimateMicronutrientsPer100g({ name: 'Dal tadka', tags: ['legume'] });
    expect(a).toEqual(b);
  });
});
