import { prisma } from '../../lib/prisma';
import { HttpError } from '../../middleware/error';
import { logFood } from '../logging/logging.service';
import type { CreateRecipeBody, LogRecipeBody } from './userRecipe.schemas';

const round = (n: number, dp = 1) => {
  const f = 10 ** dp;
  return Math.round(n * f) / f;
};

/**
 * Creates a homemade recipe: resolves each ingredient against the real Food table (so cooking
 * oil/ghee/butter contribute their own calories exactly like any other ingredient, never silently
 * dropped) and stores the TOTAL nutrition for the whole batch - the single source of truth every
 * "log a portion" call scales from, never recomputed ad hoc per screen.
 */
export async function createRecipe(userId: string, body: CreateRecipeBody) {
  const foodIds = body.ingredients.map((i) => i.foodId);
  const foods = await prisma.food.findMany({ where: { id: { in: foodIds } } });
  const byId = new Map(foods.map((f) => [f.id, f]));

  const missing = foodIds.filter((id) => !byId.has(id));
  if (missing.length > 0) throw new HttpError(400, `Unknown ingredient food id(s): ${missing.join(', ')}`);

  let totalGrams = 0;
  let kcal = 0, proteinG = 0, carbG = 0, fatG = 0, fiberG = 0, sugarG = 0, sodiumMg = 0;
  const ingredientsOut: { foodId: string; name: string; grams: number }[] = [];

  for (const ing of body.ingredients) {
    const food = byId.get(ing.foodId)!;
    const factor = ing.grams / 100;
    totalGrams += ing.grams;
    kcal += food.kcal * factor;
    proteinG += food.proteinG * factor;
    carbG += food.carbG * factor;
    fatG += food.fatG * factor;
    fiberG += food.fiberG * factor;
    sugarG += food.sugarG * factor;
    sodiumMg += food.sodiumMg * factor;
    ingredientsOut.push({ foodId: food.id, name: food.name, grams: ing.grams });
  }

  return prisma.userRecipe.create({
    data: {
      userId,
      name: body.name,
      ingredients: ingredientsOut,
      totalGrams: round(totalGrams, 0),
      kcal: round(kcal, 0),
      proteinG: round(proteinG, 1),
      carbG: round(carbG, 1),
      fatG: round(fatG, 1),
      fiberG: round(fiberG, 1),
      sugarG: round(sugarG, 1),
      sodiumMg: round(sodiumMg, 0),
    },
  });
}

export async function listRecipes(userId: string) {
  return prisma.userRecipe.findMany({ where: { userId }, orderBy: { createdAt: 'desc' } });
}

export async function deleteRecipe(userId: string, id: string) {
  const res = await prisma.userRecipe.deleteMany({ where: { id, userId } });
  if (res.count === 0) throw new HttpError(404, 'Recipe not found');
}

/**
 * Logs a portion of a saved recipe as a food-log entry, via the SAME logFood() path every other
 * food log goes through - no separate nutrition table, no separate math. "I ate 40%" or "I ate
 * 150g" scales the recipe's per-100g-of-batch nutrition, exactly like scaling any other food.
 */
export async function logRecipePortion(userId: string, recipeId: string, body: LogRecipeBody) {
  const recipe = await prisma.userRecipe.findUnique({ where: { id: recipeId } });
  if (!recipe || recipe.userId !== userId) throw new HttpError(404, 'Recipe not found');
  if (recipe.totalGrams <= 0) throw new HttpError(400, 'Recipe has no ingredients');

  const grams = body.grams ?? (recipe.totalGrams * (body.percent ?? 0)) / 100;
  const per100g = {
    kcal: recipe.kcal / (recipe.totalGrams / 100),
    proteinG: recipe.proteinG / (recipe.totalGrams / 100),
    carbG: recipe.carbG / (recipe.totalGrams / 100),
    fatG: recipe.fatG / (recipe.totalGrams / 100),
    fiberG: recipe.fiberG / (recipe.totalGrams / 100),
    sugarG: recipe.sugarG / (recipe.totalGrams / 100),
    sodiumMg: recipe.sodiumMg / (recipe.totalGrams / 100),
  };

  return logFood(userId, {
    mealSlot: body.mealSlot,
    grams: round(grams, 0),
    foodName: recipe.name,
    per100g,
    entryMethod: 'text',
  });
}
