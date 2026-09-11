import { z } from 'zod';
import { MEAL_SLOTS } from '../food/food.types';

/** One ingredient: a real Food row (id) at a given quantity in grams. */
export const recipeIngredientSchema = z.object({
  foodId: z.string().min(1),
  grams: z.number().positive().max(5000),
});

export const createRecipeSchema = z.object({
  name: z.string().min(1).max(120),
  ingredients: z.array(recipeIngredientSchema).min(1).max(30),
});

export const logRecipeSchema = z
  .object({
    mealSlot: z.enum(MEAL_SLOTS as [string, ...string[]]),
    /** Percent of the whole batch eaten (0-100], e.g. 40 = "I ate 40%". */
    percent: z.number().positive().max(100).optional(),
    /** Or an exact grams amount eaten - whichever the user finds easier to estimate. */
    grams: z.number().positive().max(5000).optional(),
  })
  .refine((d) => d.percent != null || d.grams != null, { message: 'Provide percent or grams' });

export type CreateRecipeBody = z.infer<typeof createRecipeSchema>;
export type LogRecipeBody = z.infer<typeof logRecipeSchema>;
