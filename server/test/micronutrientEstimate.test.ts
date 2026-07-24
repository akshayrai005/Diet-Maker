import { describe, it, expect } from 'vitest';
import { estimateMicronutrientsPer100g } from '../src/calc/micronutrientEstimate';

describe('estimateMicronutrientsPer100g', () => {
  it('gives leafy greens high iron and folate, plus vitamins A, C, K', () => {
    const m = estimateMicronutrientsPer100g({ name: 'Palak (spinach, cooked)', tags: ['vegetable'] });
    expect(m.ironMg).toBeGreaterThan(2);
    expect(m.folateMcg).toBeGreaterThan(100);
    expect(m.vitaminAMcg).toBeGreaterThan(0);
    expect(m.vitaminCMg).toBeGreaterThan(0);
    expect(m.vitaminKMcg).toBeGreaterThan(0);
    // leafy greens give no signal for B12 → no data, not 0
    expect(m.vitaminB12Mcg).toBeUndefined();
  });

  it('gives dairy high calcium and B12, plus riboflavin and phosphorus', () => {
    const m = estimateMicronutrientsPer100g({ name: 'Paneer', tags: ['dairy'] });
    expect(m.calciumMg).toBeGreaterThan(150);
    expect(m.vitaminB12Mcg).toBeGreaterThan(0.5);
    expect(m.vitaminB2Mg).toBeGreaterThan(0);
    expect(m.phosphorusMg).toBeGreaterThan(0);
    // dairy doesn't inform vitamin K
    expect(m.vitaminKMcg).toBeUndefined();
  });

  it('flags ragi as exceptionally calcium-rich', () => {
    const m = estimateMicronutrientsPer100g({ name: 'Ragi malt', tags: ['grain'] });
    expect(m.calciumMg).toBeGreaterThan(300);
  });

  it('gives egg B12, vitamin D, selenium and iodine', () => {
    const m = estimateMicronutrientsPer100g({ name: 'Boiled egg', tags: [], category: 'egg' });
    expect(m.vitaminB12Mcg).toBeGreaterThan(0.5);
    expect(m.vitaminDMcg).toBeGreaterThan(0);
    expect(m.seleniumMcg).toBeGreaterThan(0);
    expect(m.iodineMcg).toBeGreaterThan(0);
  });

  it('gives meat zinc, niacin and B6; fish adds selenium and iodine', () => {
    const chicken = estimateMicronutrientsPer100g({ name: 'Chicken curry', tags: ['nonveg'] });
    expect(chicken.zincMg).toBeGreaterThan(0);
    expect(chicken.vitaminB3Mg).toBeGreaterThan(0);
    expect(chicken.vitaminB6Mg).toBeGreaterThan(0);
    // plain chicken gives no marine iodine signal
    expect(chicken.iodineMcg).toBeUndefined();

    const fish = estimateMicronutrientsPer100g({ name: 'Grilled fish', tags: ['nonveg'] });
    expect(fish.seleniumMcg).toBeGreaterThan(0);
    expect(fish.iodineMcg).toBeGreaterThan(0);
    expect(fish.vitaminDMcg).toBeGreaterThan(0);
  });

  it('gives nuts vitamin E, zinc, copper and manganese', () => {
    const m = estimateMicronutrientsPer100g({ name: 'Almonds', tags: ['nuts'] });
    expect(m.vitaminEMg).toBeGreaterThan(0);
    expect(m.zincMg).toBeGreaterThan(0);
    expect(m.copperMcg).toBeGreaterThan(0);
    expect(m.manganeseMg).toBeGreaterThan(0);
  });

  it('gives whole grains thiamin, niacin, manganese, selenium and copper', () => {
    const m = estimateMicronutrientsPer100g({ name: 'Whole wheat roti', tags: ['grain'] });
    expect(m.vitaminB1Mg).toBeGreaterThan(0);
    expect(m.vitaminB3Mg).toBeGreaterThan(0);
    expect(m.manganeseMg).toBeGreaterThan(0);
    expect(m.seleniumMcg).toBeGreaterThan(0);
    expect(m.copperMcg).toBeGreaterThan(0);
    // grains give no vitamin B12 signal
    expect(m.vitaminB12Mcg).toBeUndefined();
  });

  it('gives citrus/fruit vitamin C', () => {
    const m = estimateMicronutrientsPer100g({ name: 'Orange', tags: ['fruit'] });
    expect(m.vitaminCMg).toBeGreaterThan(0);
    expect(m.potassiumMg).toBeGreaterThan(0);
  });

  it('returns no data (undefined keys) for a nutrient-poor item', () => {
    const m = estimateMicronutrientsPer100g({ name: 'Green tea (unsweetened)', tags: ['beverage'] });
    expect(m.ironMg).toBeUndefined();
    expect(m.calciumMg).toBeUndefined();
    expect(Object.keys(m)).toHaveLength(0);
  });

  it('is deterministic', () => {
    const a = estimateMicronutrientsPer100g({ name: 'Dal tadka', tags: ['legume'] });
    const b = estimateMicronutrientsPer100g({ name: 'Dal tadka', tags: ['legume'] });
    expect(a).toEqual(b);
  });
});
