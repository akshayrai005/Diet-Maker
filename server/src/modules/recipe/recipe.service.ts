import { llmTextJson, llmAvailable } from '../../ai/vision';
import { prisma } from '../../lib/prisma';
import { FOOD_INGREDIENTS } from '../../data/ingredients';

export interface Recipe {
  title: string;
  timeMin: number | null;
  servings: number | null;
  ingredients: string[];
  steps: string[];
  source: 'ai' | 'basic';
  note?: string;
}

const strArr = (v: unknown): string[] =>
  Array.isArray(v) ? v.filter((x) => typeof x === 'string').slice(0, 15) : [];
const numOrNull = (v: unknown): number | null => (typeof v === 'number' && isFinite(v) ? v : null);

/** Cache key for a dish: lower-case, punctuation-light, single spaces - "Vegetable  Upma" and "vegetable upma" share one recipe. */
export function recipeKey(foodName: string): string {
  return foodName.toLowerCase().replace(/[^a-z0-9ऀ-ॿ]+/g, ' ').trim().replace(/\s+/g, ' ').slice(0, 120);
}

/** A saved recipe for this dish, or null. Never throws: a cache problem must not break recipes. */
async function cachedRecipe(key: string): Promise<Recipe | null> {
  try {
    const row = await prisma.recipeCache.findUnique({ where: { key } });
    if (!row) return null;
    void prisma.recipeCache.update({ where: { key }, data: { hits: { increment: 1 } } }).catch(() => undefined);
    return { title: row.title, timeMin: row.timeMin, servings: row.servings, ingredients: row.ingredients as string[], steps: row.steps as string[], source: 'ai' };
  } catch {
    return null;
  }
}

/** Saves an AI recipe so it is never generated twice. Only real AI output is stored, never the basic fallback. */
async function saveRecipe(key: string, r: Recipe): Promise<void> {
  try {
    await prisma.recipeCache.upsert({
      where: { key },
      create: { key, title: r.title, timeMin: r.timeMin, servings: r.servings, ingredients: r.ingredients, steps: r.steps, source: 'ai' },
      update: {},
    });
  } catch {
    /* best effort */
  }
}

/** A cooking recipe for a planned dish: the saved copy if we have one, else Gemini/Groq (then saved), else a basic fallback. */
export async function getRecipe(foodName: string, foodId?: string): Promise<Recipe> {
  const key = recipeKey(foodName);
  const saved = key ? await cachedRecipe(key) : null;
  if (saved) return saved;
  if (llmAvailable()) {
    const prompt = [
      `Give a simple, healthy home recipe for "${foodName}" (Indian home-style where relevant).`,
      'Respond ONLY as JSON: {"title":string,"timeMin":number,"servings":number,"ingredients":string[],"steps":string[]}.',
      'Keep 5-9 concise steps and realistic ingredient quantities for the servings.',
    ].join(' ');
    const r = await llmTextJson(prompt);
    if (r) {
      const steps = strArr(r.steps);
      const ingredients = strArr(r.ingredients);
      if (steps.length > 0 && ingredients.length > 0) {
        const recipe: Recipe = {
          title: (typeof r.title === 'string' && r.title) || foodName,
          timeMin: numOrNull(r.timeMin),
          servings: numOrNull(r.servings),
          ingredients,
          steps,
          source: 'ai',
        };
        if (key) await saveRecipe(key, recipe);
        return recipe;
      }
    }
  }
  return basicRecipe(foodName, foodId);
}

/** Deterministic fallback: ingredient list from the grocery mapping + a generic method. */
function basicRecipe(foodName: string, foodId?: string): Recipe {
  const mapped = foodId ? FOOD_INGREDIENTS[foodId] : undefined;
  const ingredients = mapped?.map((i) => i.name) ?? [foodName];
  return {
    title: foodName,
    timeMin: null,
    servings: null,
    ingredients,
    steps: [
      'Gather and wash/prep the ingredients above.',
      'Heat a little oil in a pan; add aromatics (onion, ginger, garlic, spices) if using.',
      'Add the main ingredients and cook through, stirring, until done.',
      'Season to taste with salt and spices; add water for the right consistency.',
      'Serve warm.',
    ],
    source: 'basic',
    note: llmAvailable()
      ? 'Quick guide shown - the AI was busy just now (the free server may be waking up). Tap 📖 again in a moment for full steps.'
      : 'A quick guide - set an AI provider key (Groq or Gemini) on the server for full step-by-step recipes.',
  };
}
