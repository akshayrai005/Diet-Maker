import type { FoodItem, MealItem } from './food.types';

/**
 * Condition-friendliness scores (0-100, higher = friendlier) for a food or a whole meal.
 * Pure and deterministic - derived from fields the food DB already has (GI, sugar, sodium,
 * fibre, and tags like high-satfat/fried/refined/fermented/probiotic). No LLM, no new data.
 *
 * These are gentle dietary-pattern signals, not medical scoring.
 */
export interface Friendliness {
  diabetes: number;
  heart: number;
  gut: number;
  inflammation: number;
}

const clamp = (n: number) => Math.max(0, Math.min(100, Math.round(n)));
const has = (f: FoodItem, tag: string) => f.tags.includes(tag);

/** Per-food friendliness from its nutrients + tags. */
export function foodFriendliness(f: FoodItem): Friendliness {
  const gi = f.glycemicIndex ?? 55; // assume medium when unknown
  const sugarPer100 = f.sugarG;
  const sodiumPer100 = f.sodiumMg;
  const fiberPer100 = f.fiberG;

  // Diabetes: low GI + low sugar + high fibre are good; refined/high-gi/high-sugar penalise.
  let diabetes = 100 - (gi - 40) * 1.4 - sugarPer100 * 1.5 + fiberPer100 * 2;
  if (has(f, 'refined') || has(f, 'high-gi')) diabetes -= 15;
  if (has(f, 'high-sugar')) diabetes -= 20;

  // Heart: low sodium + low saturated/trans fat + fibre are good.
  let heart = 100 - sodiumPer100 / 12 + fiberPer100 * 2;
  if (has(f, 'high-satfat')) heart -= 25;
  if (has(f, 'fried')) heart -= 20;
  if (has(f, 'high-sodium')) heart -= 15;

  // Gut: fibre + fermented/probiotic are good; refined/fried penalise.
  let gut = 45 + fiberPer100 * 4;
  if (has(f, 'probiotic') || has(f, 'fermented')) gut += 25;
  if (has(f, 'high-fiber')) gut += 10;
  if (has(f, 'refined') || has(f, 'fried')) gut -= 15;

  // Inflammation: sugar + fried + refined are pro-inflammatory; fibre + fermented anti.
  let inflammation = 80 - sugarPer100 * 1.2 + fiberPer100 * 1.5;
  if (has(f, 'fried')) inflammation -= 20;
  if (has(f, 'refined') || has(f, 'high-sugar')) inflammation -= 15;
  if (has(f, 'probiotic') || has(f, 'fermented')) inflammation += 10;

  return {
    diabetes: clamp(diabetes),
    heart: clamp(heart),
    gut: clamp(gut),
    inflammation: clamp(inflammation),
  };
}

export interface MealFriendliness extends Friendliness {
  /** Up to two human labels for the meal's strongest friendliness dimensions (score ≥ 70). */
  highlights: string[];
}

const LABELS: Array<[keyof Friendliness, string]> = [
  ['diabetes', 'Diabetes-friendly'],
  ['heart', 'Heart-friendly'],
  ['gut', 'Gut-friendly'],
  ['inflammation', 'Anti-inflammatory'],
];

/** Meal-level friendliness = each food's score weighted by grams. */
export function mealFriendliness(
  items: MealItem[],
  byId: Map<string, FoodItem>,
): MealFriendliness {
  let totalG = 0;
  const acc: Friendliness = { diabetes: 0, heart: 0, gut: 0, inflammation: 0 };
  for (const it of items) {
    const food = byId.get(it.foodId);
    if (!food) continue;
    const s = foodFriendliness(food);
    const w = Math.max(1, it.grams);
    totalG += w;
    acc.diabetes += s.diabetes * w;
    acc.heart += s.heart * w;
    acc.gut += s.gut * w;
    acc.inflammation += s.inflammation * w;
  }
  const div = totalG || 1;
  const out: Friendliness = {
    diabetes: clamp(acc.diabetes / div),
    heart: clamp(acc.heart / div),
    gut: clamp(acc.gut / div),
    inflammation: clamp(acc.inflammation / div),
  };
  const highlights = LABELS.filter(([k]) => out[k] >= 70)
    .sort((a, b) => out[b[0]] - out[a[0]])
    .slice(0, 2)
    .map(([, label]) => label);
  return { ...out, highlights };
}
