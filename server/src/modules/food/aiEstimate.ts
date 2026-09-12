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
export async function estimateAndSaveFood(name: string) {
  const prompt = [
    `Estimate the nutrition of "${name}" per 100 grams, as typically consumed/prepared in an Indian kitchen.`,
    'Respond ONLY as JSON: {"kcal":number,"proteinG":number,"carbG":number,"fatG":number,"fiberG":number,"sugarG":number,"sodiumMg":number,"state":"raw"|"cooked"|"dry"|"prepared"|"as_is"}.',
    'Use realistic, standard reference values (e.g. USDA/IFCT-style) - not a rough guess.',
  ].join(' ');

  const result = await llmTextJson(prompt);
  if (!result) throw new HttpError(502, 'AI estimate unavailable right now - try again in a moment');

  const kcal = num(result.kcal);
  if (kcal <= 0) throw new HttpError(422, `Could not estimate nutrition for "${name}" - try a more specific name`);

  const state = typeof result.state === 'string' && ['raw', 'cooked', 'dry', 'prepared', 'as_is'].includes(result.state)
    ? result.state
    : 'as_is';

  return prisma.food.create({
    data: {
      name: name.trim(),
      locale: 'IN',
      category: 'vegetarian',
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
