import { Router } from 'express';
import { asyncHandler } from '../../lib/asyncHandler';
import { requireAuth, type AuthedRequest } from '../../middleware/auth';
import { tzOffsetMin } from '../../lib/tz';
import { gatherUserContext } from './context';

export const ratingRouter = Router();

/** Explainable overall health rating (Diet 60 / Exercise 25 / Discipline 15), computed server-side. */
ratingRouter.get(
  '/rating',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const ctx = await gatherUserContext(req.user!.id, tzOffsetMin(req));
    res.json({ rating: ctx.rating });
  }),
);
