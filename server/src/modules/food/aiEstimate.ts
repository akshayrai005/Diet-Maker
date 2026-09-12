import { llmTextJson } from '../../ai/vision';
import { prisma } from '../../lib/prisma';
import { HttpError } from '../../middleware/error';

const num = (v: unknown, d = 0): number => (typeof v === 'number' && isFinite(v) && v >= 0 ? v : d);

/**
 * Estimates per-100g macros for a named ingredient via the LLM (e.g. "bajra flour" - not in the
 * seeded catalog, which only has finished dishes like roti/porridge, not raw grain flours) and
 * saves it as a real Food row. One-time cost per ingredient: it's then in the searchable catalog
 * for every future recipe/search, not just this one - the database grows from real usage.
 */
/** Category keywords checked BEFORE trusting the LLM's own classification - a name containing an
 * unambiguous meat/fish/egg term must never be saved as anything else, regardless of what the
 * model returns, so a mislabeled AI estimate can't silently serve nonveg to a veg/vegan user. */
const NONVEG_NAME_HINTS = [
  'chicken', 'mutton', 'lamb', 'goat meat', 'beef', 'pork', 'bacon', 'ham', 'sausage',
  'fish', 'prawn', 'shrimp', 'crab', 'lobster', 'squid', 'octopus', 'salmon', 'tuna', 'anchovy', 'mackerel',
  'meat', 'keema', 'kheema',
];
const EGG_NAME_HINTS = ['egg', 'omelette', 'omelet'];

export function categoryFromName(name: string): 'nonveg' | 'egg' | null {
  const n = name.toLowerCase();
  if (NONVEG_NAME_HINTS.some((h) => n.includes(h))) return 'nonveg';
  if (EGG_NAME_HINTS.some((h) => n.includes(h))) return 'egg';
  return null;
}

export async function estimateAndSaveFood(name: string) {
  const prompt = [
    `Estimate the nutrition of "${name}" per 100 grams, as typically consumed/prepared in an Indian kitchen.`,
    'Respond ONLY as JSON: {"kcal":number,"proteinG":number,"carbG":number,"fatG":number,"fiberG":number,"sugarG":number,"sodiumMg":number,"state":"raw"|"cooked"|"dry"|"prepared"|"as_is","category":"vegan"|"vegetarian"|"egg"|"nonveg"}.',
    'category is critical for diet-filtering: "nonveg" for any meat/fish/poultry (including as an ingredient in a curry/dish), "egg" for egg-containing dishes with no meat/fish, "vegetarian" for dairy/ghee/honey but no meat/fish/egg, "vegan" only if it also contains no animal product at all.',
    'Use realistic, standard reference values (e.g. USDA/IFCT-style) - not a rough guess.',
  ].join(' ');

  const result = await llmTextJson(prompt);
  if (!result) throw new HttpError(502, 'AI estimate unavailable right now - try again in a moment');

  const kcal = num(result.kcal);
  if (kcal <= 0) throw new HttpError(422, `Could not estimate nutrition for "${name}" - try a more specific name`);

  const state = typeof result.state === 'string' && ['raw', 'cooked', 'dry', 'prepared', 'as_is'].includes(result.state)
    ? result.state
    : 'as_is';

  const llmCategory = typeof result.category === 'string' && ['vegan', 'vegetarian', 'egg', 'nonveg'].includes(result.category)
    ? (result.category as 'vegan' | 'vegetarian' | 'egg' | 'nonveg')
    : 'vegetarian'; // unknown/missing from the LLM - never silently trust "nonveg-free" here, name hints below still catch obvious cases
  // Name-based hints override the LLM: a name that unambiguously says "chicken curry" must never
  // end up vegetarian even if the model's category field was wrong or missing.
  const category = categoryFromName(name) ?? llmCategory;

  return prisma.food.create({
    data: {
      name: name.trim(),
      locale: 'IN',
      category,
      mealSlots: ['breakfast', 'midmorning', 'lunch', 'eveningsnack', 'dinner'],
      kcal,
      proteinG: num(result.proteinG),
      carbG: num(result.carbG),
      fatG: num(result.fatG),
      fiberG: num(result.fiberG),
      sugarG: num(result.sugarG),
      sodiumMg: num(result.sodiumMg),
      typicalServingG: 100,
      costTier: 2,
      tags: ['ai-estimated'],
      allergens: [],
      prep: 'stove',
      state,
    },
  });
}
