import { FoodItem, PlanPreferences } from './food.types';

/** Which food categories a diet type permits. */
function categoryAllowed(dietType: string, cat: FoodItem['category']): boolean {
  switch (dietType) {
    case 'vegan':
      return cat === 'vegan';
    case 'veg':
    case 'vegetarian': // alias - never let this fall through to "allow everything"
    case 'jain':
    case 'satvik':
    case 'mediterranean': // treat as veg-leaning by default here
      return cat === 'vegan' || cat === 'vegetarian';
    case 'eggetarian':
    case 'egg':
      return cat === 'vegan' || cat === 'vegetarian' || cat === 'egg';
    case 'nonveg':
    case 'keto':
    case 'lowcarb':
    case 'highprotein':
    case 'if':
    case 'custom':
    default:
      return true;
  }
}

/** Tags a diet type forbids (e.g. jain/satvik exclude onion & garlic). */
function forbiddenTags(dietType: string): string[] {
  if (dietType === 'jain') return ['onion', 'garlic', 'root'];
  if (dietType === 'satvik') return ['onion', 'garlic'];
  return [];
}

/** Tags a set of conditions wants to avoid. */
export function conditionAvoidTags(conditions: string[]): string[] {
  const avoid = new Set<string>();
  if (conditions.includes('diabetes')) {
    avoid.add('high-sugar');
    avoid.add('high-gi');
  }
  if (conditions.includes('pcos')) {
    // PCOS is insulin-driven - treat high-GI/sugary/refined carbs like diabetes.
    avoid.add('high-sugar');
    avoid.add('high-gi');
    avoid.add('refined');
  }
  if (conditions.includes('fatty_liver')) {
    avoid.add('high-sugar');
    avoid.add('refined');
    avoid.add('alcohol');
    avoid.add('fried');
  }
  if (conditions.includes('hypertension') || conditions.includes('heart_disease')) {
    avoid.add('high-sodium');
  }
  if (conditions.includes('heart_disease')) avoid.add('high-satfat');
  if (conditions.includes('gout')) avoid.add('high-purine');
  if (conditions.includes('kidney_disease')) {
    avoid.add('high-potassium');
    avoid.add('high-phosphorus');
  }
  return [...avoid];
}

const norm = (s: string) => s.toLowerCase().trim();

/**
 * Allergen → related terms so a declared allergy also excludes obvious members and synonyms
 * (e.g. "nuts" also blocks almond/cashew/peanut). Defence-in-depth on top of the food's own
 * allergen tags; matching is fail-closed (substring both ways).
 */
const ALLERGEN_SYNONYMS: Record<string, string[]> = {
  nut: ['nut', 'almond', 'cashew', 'walnut', 'pistachio', 'hazelnut', 'pecan', 'peanut', 'groundnut'],
  nuts: ['nut', 'almond', 'cashew', 'walnut', 'pistachio', 'hazelnut', 'pecan', 'peanut', 'groundnut'],
  peanut: ['peanut', 'groundnut'],
  milk: ['milk', 'dairy', 'curd', 'yogurt', 'yoghurt', 'paneer', 'cheese', 'butter', 'ghee', 'khoya', 'lassi'],
  dairy: ['milk', 'dairy', 'curd', 'yogurt', 'yoghurt', 'paneer', 'cheese', 'butter', 'ghee', 'khoya', 'lassi'],
  lactose: ['milk', 'dairy', 'curd', 'yogurt', 'paneer', 'cheese', 'khoya'],
  gluten: ['gluten', 'wheat', 'atta', 'maida', 'roti', 'chapati', 'paratha', 'bread', 'semolina', 'suji', 'rava', 'barley'],
  wheat: ['wheat', 'gluten', 'atta', 'maida', 'roti', 'chapati', 'paratha', 'bread', 'semolina', 'suji', 'rava'],
  egg: ['egg', 'omelette', 'omelet'],
  soy: ['soy', 'soya', 'tofu', 'edamame'],
  soya: ['soy', 'soya', 'tofu', 'edamame'],
  fish: ['fish', 'tuna', 'salmon', 'anchovy', 'mackerel'],
  shellfish: ['shellfish', 'prawn', 'shrimp', 'crab', 'lobster'],
  sesame: ['sesame', 'til', 'tahini'],
};

export function expandAllergen(a: string): string[] {
  const key = norm(a);
  return ALLERGEN_SYNONYMS[key] ? [key, ...ALLERGEN_SYNONYMS[key]] : [key];
}

/**
 * Returns the foods a user may eat: diet-compatible, allergen-free, and not carrying any
 * condition-avoid tag. Deterministic and pure.
 */
export function eligibleFoods(foods: FoodItem[], prefs: PlanPreferences): FoodItem[] {
  const allergyTerms = prefs.allergies.flatMap(expandAllergen).filter(Boolean);
  const forbidden = new Set(forbiddenTags(prefs.dietType).map(norm));
  const avoid = new Set(conditionAvoidTags(prefs.conditions).map(norm));
  // NOTE: kitchen access does NOT restrict the menu. In India a PG/hostel/mess or tiffin still
  // serves cooked roti/dal/sabji/rice — not cooking yourself doesn't mean assemble-only food. The
  // only hard "what do I actually have" constraint is an explicit availableFoodIds list.
  const availableSet = prefs.availableFoodIds && prefs.availableFoodIds.length > 0 ? new Set(prefs.availableFoodIds) : null;

  return foods.filter((f) => {
    if (!categoryAllowed(prefs.dietType, f.category)) return false;

    // Only foods the user explicitly says they have access to (rarely set).
    if (availableSet && !availableSet.has(f.id)) return false;

    // Allergen exclusion - fail-closed: block if any allergy term (incl. synonyms) matches the
    // food's name OR any of its allergen tags, substring in either direction.
    const name = norm(f.name);
    const foodAllergens = f.allergens.map(norm);
    for (const term of allergyTerms) {
      if (name.includes(term)) return false;
      if (foodAllergens.some((x) => x.includes(term) || term.includes(x))) return false;
    }

    const tags = f.tags.map(norm);
    if (tags.some((t) => forbidden.has(t))) return false;
    if (tags.some((t) => avoid.has(t))) return false;

    return true;
  });
}
