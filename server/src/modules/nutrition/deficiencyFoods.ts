import type { MicronutrientKey } from '../../calc/micronutrients';
import { MICRONUTRIENT_LABELS } from '../../calc/micronutrients';

/**
 * Deterministic "deficiency → food fix": for a real micronutrient deficiency, suggest specific
 * whole foods rich in it, filtered to the user's diet pattern, allergies and budget. Educational
 * food guidance (nutrient-density from standard food-composition data), never a supplement
 * prescription. Pure & unit-tested.
 */

/** Coarse diet class of a food, from most to least restrictive-friendly. */
type FoodClass = 'vegan' | 'vegetarian' | 'egg' | 'fish' | 'meat';
type Budget = 'low' | 'mid' | 'high';

interface FoodSource {
  name: string;
  cls: FoodClass;
  /** Allergen tags for exclusion (matched against the user's free-text allergies too). */
  allergens?: string[];
  budget: Budget;
  /** Excluded from Jain/Satvik (roots, onion, garlic). */
  rootOrAllium?: boolean;
}

export type DietPattern =
  | 'veg'
  | 'eggetarian'
  | 'nonveg'
  | 'vegan'
  | 'jain'
  | 'keto'
  | 'mediterranean'
  | 'lowcarb'
  | 'highprotein'
  | 'satvik'
  | 'if'
  | 'custom';

export interface FoodSuggestion {
  key: MicronutrientKey;
  label: string;
  foods: string[];
  note: string;
}

const SOURCE_NOTE = 'Food sources by nutrient density (standard food-composition data). Educational, not a supplement prescription.';

// A compact, deliberately Indian-diet-aware source map for the commonly-deficient nutrients.
const SOURCES: Partial<Record<MicronutrientKey, FoodSource[]>> = {
  ironMg: [
    { name: 'Spinach (palak)', cls: 'vegan', budget: 'low' },
    { name: 'Rajma (kidney beans)', cls: 'vegan', budget: 'low' },
    { name: 'Dates', cls: 'vegan', budget: 'low' },
    { name: 'Pumpkin seeds', cls: 'vegan', allergens: ['seeds'], budget: 'mid' },
    { name: 'Chicken liver', cls: 'meat', budget: 'mid' },
  ],
  calciumMg: [
    { name: 'Ragi (finger millet)', cls: 'vegan', budget: 'low' },
    { name: 'Sesame (til)', cls: 'vegan', allergens: ['sesame'], budget: 'low' },
    { name: 'Milk / curd', cls: 'vegetarian', allergens: ['dairy'], budget: 'low' },
    { name: 'Paneer', cls: 'vegetarian', allergens: ['dairy'], budget: 'mid' },
    { name: 'Tofu', cls: 'vegan', allergens: ['soy'], budget: 'low' },
  ],
  vitaminB12Mcg: [
    { name: 'Milk / curd', cls: 'vegetarian', allergens: ['dairy'], budget: 'low' },
    { name: 'Eggs', cls: 'egg', allergens: ['egg'], budget: 'low' },
    { name: 'Fortified cereals / nutritional yeast', cls: 'vegan', budget: 'mid' },
    { name: 'Fish (sardines)', cls: 'fish', allergens: ['fish'], budget: 'mid' },
  ],
  vitaminDMcg: [
    { name: 'Sunlight (15–20 min) + fortified milk', cls: 'vegetarian', allergens: ['dairy'], budget: 'low' },
    { name: 'Egg yolk', cls: 'egg', allergens: ['egg'], budget: 'low' },
    { name: 'Mushrooms (sun-exposed)', cls: 'vegan', budget: 'low' },
    { name: 'Fatty fish (salmon)', cls: 'fish', allergens: ['fish'], budget: 'high' },
  ],
  folateMcg: [
    { name: 'Spinach & leafy greens', cls: 'vegan', budget: 'low' },
    { name: 'Chana (chickpeas)', cls: 'vegan', budget: 'low' },
    { name: 'Beetroot', cls: 'vegan', rootOrAllium: true, budget: 'low' },
    { name: 'Oranges', cls: 'vegan', budget: 'mid' },
  ],
  magnesiumMg: [
    { name: 'Almonds', cls: 'vegan', allergens: ['nuts'], budget: 'mid' },
    { name: 'Whole grains (bajra, oats)', cls: 'vegan', allergens: ['gluten'], budget: 'low' },
    { name: 'Bananas', cls: 'vegan', budget: 'low' },
    { name: 'Dark chocolate', cls: 'vegetarian', budget: 'mid' },
  ],
  potassiumMg: [
    { name: 'Bananas', cls: 'vegan', budget: 'low' },
    { name: 'Coconut water', cls: 'vegan', budget: 'low' },
    { name: 'Sweet potato', cls: 'vegan', rootOrAllium: true, budget: 'low' },
    { name: 'Spinach', cls: 'vegan', budget: 'low' },
  ],
  zincMg: [
    { name: 'Chana & legumes', cls: 'vegan', budget: 'low' },
    { name: 'Pumpkin seeds', cls: 'vegan', allergens: ['seeds'], budget: 'mid' },
    { name: 'Cashews', cls: 'vegan', allergens: ['nuts'], budget: 'mid' },
    { name: 'Eggs', cls: 'egg', allergens: ['egg'], budget: 'low' },
  ],
  vitaminCMg: [
    { name: 'Amla (Indian gooseberry)', cls: 'vegan', budget: 'low' },
    { name: 'Guava', cls: 'vegan', budget: 'low' },
    { name: 'Oranges / lemon', cls: 'vegan', budget: 'low' },
    { name: 'Bell peppers', cls: 'vegan', budget: 'mid' },
  ],
  vitaminAMcg: [
    { name: 'Carrots', cls: 'vegan', rootOrAllium: true, budget: 'low' },
    { name: 'Spinach & greens', cls: 'vegan', budget: 'low' },
    { name: 'Mango / papaya', cls: 'vegan', budget: 'low' },
    { name: 'Egg yolk', cls: 'egg', allergens: ['egg'], budget: 'low' },
  ],
  vitaminB6Mg: [
    { name: 'Chickpeas', cls: 'vegan', budget: 'low' },
    { name: 'Bananas', cls: 'vegan', budget: 'low' },
    { name: 'Potatoes', cls: 'vegan', rootOrAllium: true, budget: 'low' },
  ],
  iodineMcg: [
    { name: 'Iodised salt', cls: 'vegan', budget: 'low' },
    { name: 'Curd / milk', cls: 'vegetarian', allergens: ['dairy'], budget: 'low' },
    { name: 'Fish', cls: 'fish', allergens: ['fish'], budget: 'mid' },
  ],
};

/** Which food classes a diet pattern permits. */
function allowedClasses(diet: DietPattern): Set<FoodClass> {
  switch (diet) {
    case 'vegan':
      return new Set(['vegan']);
    case 'veg':
    case 'jain':
    case 'satvik':
      return new Set(['vegan', 'vegetarian']);
    case 'eggetarian':
      return new Set(['vegan', 'vegetarian', 'egg']);
    default:
      // nonveg / keto / mediterranean / lowcarb / highprotein / if / custom → no class restriction.
      return new Set(['vegan', 'vegetarian', 'egg', 'fish', 'meat']);
  }
}

const budgetRank: Record<Budget, number> = { low: 0, mid: 1, high: 2 };

function allergenBlocked(food: FoodSource, allergies: string[]): boolean {
  const lc = allergies.map((a) => a.toLowerCase().trim()).filter(Boolean);
  if (!lc.length) return false;
  const tags = (food.allergens ?? []).map((t) => t.toLowerCase());
  return lc.some((a) => tags.some((t) => t.includes(a) || a.includes(t)) || food.name.toLowerCase().includes(a));
}

export interface SuggestOptions {
  dietType: DietPattern;
  allergies?: string[];
  budgetTier?: Budget;
  /** Max foods per deficiency (default 4). */
  perNutrient?: number;
}

/**
 * For each deficiency, up to `perNutrient` diet-compatible, allergen-safe foods (budget-preferred).
 * Deficiencies with no known source list, or none passing the filters, are skipped (honest silence).
 */
export function suggestFoodsForDeficiencies(
  deficiencies: MicronutrientKey[],
  opts: SuggestOptions,
): FoodSuggestion[] {
  const allow = allowedClasses(opts.dietType);
  const jainLike = opts.dietType === 'jain' || opts.dietType === 'satvik';
  const allergies = opts.allergies ?? [];
  const perNutrient = opts.perNutrient ?? 4;

  const out: FoodSuggestion[] = [];
  for (const key of deficiencies) {
    const sources = SOURCES[key];
    if (!sources) continue;
    const eligible = sources
      .filter((f) => allow.has(f.cls))
      .filter((f) => !(jainLike && f.rootOrAllium))
      .filter((f) => !allergenBlocked(f, allergies))
      .sort((a, b) => {
        // Budget preference first (if a tier is set), then keep declared order.
        if (opts.budgetTier) {
          const pref = budgetRank[opts.budgetTier];
          const da = Math.abs(budgetRank[a.budget] - pref);
          const db = Math.abs(budgetRank[b.budget] - pref);
          if (da !== db) return da - db;
        }
        return 0;
      })
      .slice(0, perNutrient)
      .map((f) => f.name);

    if (eligible.length) {
      out.push({ key, label: MICRONUTRIENT_LABELS[key], foods: eligible, note: SOURCE_NOTE });
    }
  }
  return out;
}
