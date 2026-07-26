import { Router } from 'express';
import { z } from 'zod';
import { asyncHandler } from '../../lib/asyncHandler';
import { requireAuth, type AuthedRequest } from '../../middleware/auth';
import { checkRedFlags } from './redFlags';

export const safetyRouter = Router();

const bodySchema = z.object({ text: z.string().max(2000) });

/**
 * Conservative red-flag check on free text a user entered (e.g. a symptom in coach/check-in).
 * Only ever escalates to "seek urgent care" - never reassures or diagnoses.
 */
safetyRouter.post(
  '/safety/red-flags',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const { text } = bodySchema.parse(req.body);
    res.json(checkRedFlags(text));
  }),
);
