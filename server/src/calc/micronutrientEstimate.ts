import type { Micronutrients, MicronutrientKey } from './micronutrients';

/**
 * Per-100g micronutrient ESTIMATE for a food from its category/tags/name - PURE.
 *
 * These are IFCT-2017 / USDA / DRI-informed CATEGORY approximations, NOT per-food laboratory
 * values: enough to surface "you're likely low on iron/zinc" from what someone logs, while being
 * honest that it's an educational estimate. When the Food table gains measured micronutrient
 * columns, this becomes the fallback for foods that still lack them.
 *
 * HONESTY: the result is a Partial - it contains ONLY the nutrients the food's category gives a
 * real signal for. Any key that is absent means "we cannot estimate this" (no data), NOT zero.
 *
 * Units match Micronutrients (all per 100 g): iron/calcium/potassium/magnesium/phosphorus mg,
 * B12/vitD/folate/vitA/vitK/selenium/iodine/copper mcg, vitC/vitE/B1/B2/B3/B6/zinc/manganese mg.
 */

export interface FoodLike {
  name: string;
  category?: string | null; // vegan | vegetarian | egg | nonveg
  tags?: string[] | null;
  proteinG?: number | null;
}

/** Adds a partial estimate into the accumulator, tracking only keys that actually appear. */
function add(acc: Partial<Micronutrients>, b: Partial<Micronutrients>): void {
  for (const key of Object.keys(b) as MicronutrientKey[]) {
    const v = b[key];
    if (v == null) continue;
    acc[key] = (acc[key] ?? 0) + v;
  }
}

export function estimateMicronutrientsPer100g(food: FoodLike): Partial<Micronutrients> {
  const name = (food.name ?? '').toLowerCase();
  const tags = (food.tags ?? []).map((t) => t.toLowerCase());
  const has = (...keys: string[]) => keys.some((k) => tags.includes(k) || name.includes(k));

  const m: Partial<Micronutrients> = {};

  // Leafy greens - iron/folate/calcium + vitamins A, C, K (IFCT/USDA: spinach-class greens).
  if (has('spinach', 'palak', 'methi', 'leafy', 'saag', 'greens')) {
    add(m, {
      ironMg: 2.7, calciumMg: 99, folateMcg: 120, potassiumMg: 500, magnesiumMg: 79,
      vitaminAMcg: 469, vitaminCMg: 28, vitaminKMcg: 483,
    });
  }
  // Dairy - calcium + B12 + vitamin A, riboflavin (B2), phosphorus (IFCT/USDA milk/paneer).
  if (has('dairy', 'milk', 'paneer', 'curd', 'yogurt', 'buttermilk', 'chaas', 'cheese')) {
    add(m, {
      calciumMg: 180, vitaminB12Mcg: 0.9, potassiumMg: 150, magnesiumMg: 12,
      vitaminAMcg: 110, vitaminB2Mg: 0.18, phosphorusMg: 100,
    });
  }
  // Legumes / dal - iron, folate, magnesium, potassium (IFCT).
  if (has('legume', 'dal', 'dhal', 'rajma', 'chana', 'lentil', 'bean', 'sprout', 'moong')) {
    add(m, { ironMg: 2.5, folateMcg: 130, potassiumMg: 350, magnesiumMg: 45, calciumMg: 30 });
  }
  // Egg - B12, iron, vitamin D + vitamin A, riboflavin (B2), selenium, iodine (USDA whole egg).
  if (has('egg')) {
    add(m, {
      vitaminB12Mcg: 1.1, ironMg: 1.2, vitaminDMcg: 2.0, potassiumMg: 126,
      vitaminAMcg: 160, vitaminB2Mg: 0.5, seleniumMcg: 30, iodineMcg: 25,
    });
  }
  // Meat / fish - B12, heme iron, potassium + zinc, niacin (B3), B6, phosphorus (USDA/IFCT).
  if (has('nonveg', 'chicken', 'mutton', 'fish', 'egg-nonveg', 'prawn')) {
    add(m, {
      vitaminB12Mcg: 1.5, ironMg: 1.3, potassiumMg: 300,
      zincMg: 3.0, vitaminB3Mg: 5.0, vitaminB6Mg: 0.4, phosphorusMg: 200,
    });
    // Fish specifically - vitamin D, plus selenium and iodine (marine sources).
    if (has('fish', 'salmon', 'sardine')) {
      add(m, { vitaminDMcg: 5.0, calciumMg: 20, seleniumMcg: 40, iodineMcg: 50 });
    }
  }
  // Nuts / seeds - magnesium, calcium + vitamin E, zinc, copper, manganese, phosphorus (USDA).
  if (has('nuts', 'almond', 'peanut', 'seed', 'sesame', 'til')) {
    add(m, {
      magnesiumMg: 120, calciumMg: 90, ironMg: 2.0, potassiumMg: 400,
      vitaminEMg: 15, zincMg: 3.0, copperMcg: 1000, manganeseMg: 2.0, phosphorusMg: 330,
    });
  }
  // Citrus / fruit - potassium, folate + vitamin C (IFCT/USDA).
  if (has('fruit', 'banana', 'apple', 'orange', 'papaya', 'guava', 'citrus', 'lemon', 'mosambi')) {
    add(m, { potassiumMg: 250, folateMcg: 20, magnesiumMg: 15, vitaminCMg: 30 });
  }
  // Whole grains - magnesium, iron + thiamin (B1), niacin (B3), manganese, selenium,
  // phosphorus, copper (USDA/IFCT whole wheat/millets).
  if (has('grain', 'roti', 'oats', 'ragi', 'bajra', 'jowar', 'millet', 'wheat', 'brown rice')) {
    add(m, {
      magnesiumMg: 40, ironMg: 1.5, potassiumMg: 120,
      vitaminB1Mg: 0.4, vitaminB3Mg: 4.0, manganeseMg: 2.5, seleniumMcg: 25, phosphorusMg: 250, copperMcg: 400,
    });
    if (has('ragi')) add(m, { calciumMg: 344 }); // ragi is exceptionally calcium-rich (IFCT)
  }
  // Vegetables (non-leafy) - modest potassium/magnesium/folate + a little vitamin C.
  if (has('vegetable', 'sabzi', 'veg')) {
    add(m, { potassiumMg: 200, magnesiumMg: 20, folateMcg: 40, ironMg: 0.8, vitaminCMg: 12 });
  }

  return m;
}
