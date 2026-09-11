import { Router } from 'express';
import { asyncHandler } from '../../lib/asyncHandler';
import { requireAuth, type AuthedRequest } from '../../middleware/auth';
import { createRecipeSchema, logRecipeSchema } from './userRecipe.schemas';
import * as svc from './userRecipe.service';

export const userRecipeRouter = Router();

/** Create a homemade recipe from real Food ingredients (id + grams). */
userRecipeRouter.post(
  '/recipes',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const body = createRecipeSchema.parse(req.body);
    const recipe = await svc.createRecipe(req.user!.id, body);
    res.status(201).json({ recipe });
  }),
);

userRecipeRouter.get(
  '/recipes',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const recipes = await svc.listRecipes(req.user!.id);
    res.json({ recipes });
  }),
);

userRecipeRouter.delete(
  '/recipes/:id',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    await svc.deleteRecipe(req.user!.id, req.params.id!);
    res.status(204).end();
  }),
);

/** Log "I ate X% / Xg of this recipe" as a real food-log entry via the shared logFood() path. */
userRecipeRouter.post(
  '/recipes/:id/log',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const body = logRecipeSchema.parse(req.body);
    const entry = await svc.logRecipePortion(req.user!.id, req.params.id!, body);
    res.status(201).json({ entry });
  }),
);
