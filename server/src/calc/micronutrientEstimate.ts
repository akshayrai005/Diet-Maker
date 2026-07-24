import type { Micronutrients } from './micronutrients';

/**
 * Per-100g micronutrient ESTIMATE for a food from its category/tags/name — PURE.
 *
 * These are IFCT-2017 / DRI-informed category approximations, NOT per-food laboratory values:
 * enough to surface "you're likely low on iron/calcium" from what someone logs, while being
 * honest that it's an educational estimate. When the Food table gains measured micronutrient
 * columns, this becomes the fallback for foods that still lack them.
 *
 * Units match Micronutrients: iron mg, calcium mg, B12 mcg, vitD mcg, folate mcg, potassium mg,
 * magnesium mg — all per 100 g.
 */

export interface FoodLike {
  name: string;
  category?: string | null; // vegan | vegetarian | egg | nonveg
  tags?: string[] | null;
  proteinG?: number | null;
}

const EMPTY: Micronutrients = {
  ironMg: 0,
  calciumMg: 0,
  vitaminB12Mcg: 0,
  vitaminDMcg: 0,
  folateMcg: 0,
  potassiumMg: 0,
  magnesiumMg: 0,
};

const add = (a: Micronutrients, b: Partial<Micronutrients>): Micronutrients => ({
  ironMg: a.ironMg + (b.ironMg ?? 0),
  calciumMg: a.calciumMg + (b.calciumMg ?? 0),
  vitaminB12Mcg: a.vitaminB12Mcg + (b.vitaminB12Mcg ?? 0),
  vitaminDMcg: a.vitaminDMcg + (b.vitaminDMcg ?? 0),
  folateMcg: a.folateMcg + (b.folateMcg ?? 0),
  potassiumMg: a.potassiumMg + (b.potassiumMg ?? 0),
  magnesiumMg: a.magnesiumMg + (b.magnesiumMg ?? 0),
});

export function estimateMicronutrientsPer100g(food: FoodLike): Micronutrients {
  const name = (food.name ?? '').toLowerCase();
  const tags = (food.tags ?? []).map((t) => t.toLowerCase());
  const has = (...keys: string[]) => keys.some((k) => tags.includes(k) || name.includes(k));

  let m: Micronutrients = { ...EMPTY };

  // Leafy greens — iron, folate, calcium, potassium, magnesium.
  if (has('spinach', 'palak', 'methi', 'leafy', 'saag', 'greens')) {
    m = add(m, { ironMg: 2.7, calciumMg: 99, folateMcg: 120, potassiumMg: 500, magnesiumMg: 79 });
  }
  // Dairy — calcium + B12.
  if (has('dairy', 'milk', 'paneer', 'curd', 'yogurt', 'buttermilk', 'chaas', 'cheese')) {
    m = add(m, { calciumMg: 180, vitaminB12Mcg: 0.9, potassiumMg: 150, magnesiumMg: 12 });
  }
  // Legumes / dal — iron, folate, magnesium, potassium.
  if (has('legume', 'dal', 'dhal', 'rajma', 'chana', 'lentil', 'bean', 'sprout', 'moong')) {
    m = add(m, { ironMg: 2.5, folateMcg: 130, potassiumMg: 350, magnesiumMg: 45, calciumMg: 30 });
  }
  // Egg — B12, some iron + vitamin D.
  if (has('egg')) {
    m = add(m, { vitaminB12Mcg: 1.1, ironMg: 1.2, vitaminDMcg: 2.0, potassiumMg: 126 });
  }
  // Meat / fish — B12, heme iron, potassium; fish adds vitamin D.
  if (has('nonveg', 'chicken', 'mutton', 'fish', 'egg-nonveg', 'prawn')) {
    m = add(m, { vitaminB12Mcg: 1.5, ironMg: 1.3, potassiumMg: 300 });
    if (has('fish', 'salmon', 'sardine')) m = add(m, { vitaminDMcg: 5.0, calciumMg: 20 });
  }
  // Nuts / seeds — magnesium, calcium.
  if (has('nuts', 'almond', 'peanut', 'seed', 'sesame', 'til')) {
    m = add(m, { magnesiumMg: 120, calciumMg: 90, ironMg: 2.0, potassiumMg: 400 });
  }
  // Fruit — potassium + a little folate.
  if (has('fruit', 'banana', 'apple', 'orange', 'papaya', 'guava')) {
    m = add(m, { potassiumMg: 250, folateMcg: 20, magnesiumMg: 15 });
  }
  // Whole grains — magnesium, some iron.
  if (has('grain', 'roti', 'oats', 'ragi', 'bajra', 'jowar', 'millet', 'wheat', 'brown rice')) {
    m = add(m, { magnesiumMg: 40, ironMg: 1.5, potassiumMg: 120 });
    if (has('ragi')) m = add(m, { calciumMg: 344 }); // ragi is exceptionally calcium-rich (IFCT)
  }
  // Vegetables (non-leafy) — modest potassium/magnesium/folate.
  if (has('vegetable', 'sabzi', 'veg')) {
    m = add(m, { potassiumMg: 200, magnesiumMg: 20, folateMcg: 40, ironMg: 0.8 });
  }

  return m;
}
