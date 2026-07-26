import { Router } from 'express';
import { z } from 'zod';
import { asyncHandler } from '../../lib/asyncHandler';
import { requireAuth, type AuthedRequest } from '../../middleware/auth';
import { HttpError } from '../../middleware/error';
import { VITAL_TYPES, isValidReading, type VitalType, type VitalReading } from './vitals';
import { logVital, getVitalSeries, getVitalsSummary } from './vitals.service';

export const vitalsRouter = Router();

const logSchema = z
  .object({
    type: z.enum(VITAL_TYPES),
    value: z.number().finite().positive().max(100000).optional(),
    systolic: z.number().int().positive().max(300).optional(),
    diastolic: z.number().int().positive().max(200).optional(),
    note: z.string().max(500).optional(),
    // Accept a full ISO datetime OR a date-only string (the client's date picker sends yyyy-MM-dd).
    measuredAt: z.union([z.string().datetime(), z.string().date()]).optional(),
  })
  .refine((b) => isValidReading(b.type as VitalType, b as VitalReading), {
    message: 'Provide systolic & diastolic for blood pressure, or a positive value for other metrics.',
  });

/** Log a vital/lab reading. Returns the stored point + its educational band. */
vitalsRouter.post(
  '/vitals',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const body = logSchema.parse(req.body);
    const result = await logVital(req.user!.id, body);
    res.status(201).json(result);
  }),
);

const rangeSchema = z.object({
  type: z.enum(VITAL_TYPES),
  range: z.coerce.number().int().min(1).max(3650).optional(),
});

/** Time-series for one metric: points + trend + latest band. `range` is in days (default 180). */
vitalsRouter.get(
  '/vitals',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const parsed = rangeSchema.safeParse(req.query);
    if (!parsed.success) throw new HttpError(400, 'A valid `type` is required.');
    const series = await getVitalSeries(req.user!.id, parsed.data.type, parsed.data.range ?? 180);
    res.json(series);
  }),
);

/** Latest reading + band for every metric the user has logged — for the overview/Home. */
vitalsRouter.get(
  '/vitals/summary',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const summary = await getVitalsSummary(req.user!.id);
    res.json(summary);
  }),
);
