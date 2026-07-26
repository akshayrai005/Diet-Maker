import { Router } from 'express';
import { z } from 'zod';
import { asyncHandler } from '../../lib/asyncHandler';
import { requireAuth, type AuthedRequest } from '../../middleware/auth';
import * as svc from './cycle.service';

export const cycleRouter = Router();

const periodSchema = z.object({
  startDate: z.string().datetime().optional(),
  endDate: z.string().datetime().optional(),
  // Sensitive detail - encrypted at rest, never used by the date-only cycle math.
  symptoms: z.array(z.string().max(40)).max(20).optional(),
  flow: z.enum(['light', 'medium', 'heavy']).optional(),
  mood: z.number().int().min(1).max(5).optional(),
  notes: z.string().max(500).optional(),
});

/** Log a period start (defaults to now), optional end, and optional encrypted symptom/flow detail. */
cycleRouter.post(
  '/cycle/period',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const { startDate, endDate, symptoms, flow, mood, notes } = periodSchema.parse(req.body ?? {});
    const row = await svc.logPeriod(
      req.user!.id,
      startDate ? new Date(startDate) : new Date(),
      endDate ? new Date(endDate) : undefined,
      { symptoms, flow, mood, notes },
    );
    res.status(201).json({ period: { id: row.id, startDate: row.startDate, endDate: row.endDate } });
  }),
);

/** Mark the current (ongoing) period as ended today - enables duration analysis. */
cycleRouter.post(
  '/cycle/end',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const row = await svc.endLatestPeriod(req.user!.id);
    res.json({ period: { id: row.id, startDate: row.startDate, endDate: row.endDate } });
  }),
);

/** Current cycle phase + phase-specific diet/exercise/yoga guidance. */
cycleRouter.get(
  '/cycle',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const cycle = await svc.getCycle(req.user!.id);
    res.json({ cycle });
  }),
);
