import { Router } from 'express';
import { z } from 'zod';
import { asyncHandler } from '../../lib/asyncHandler';
import { requireAuth, type AuthedRequest } from '../../middleware/auth';
import { logBodyMetric, getBodyMetrics, addPhoto, listPhotos, deletePhoto } from './body.service';

export const bodyRouter = Router();

const cm = z.number().positive().max(400).optional();
const metricSchema = z.object({
  measuredAt: z.union([z.string().datetime(), z.string().date()]).optional(),
  weightKg: z.number().positive().max(500).optional(),
  waistCm: cm,
  hipCm: cm,
  chestCm: cm,
  armCm: cm,
  thighCm: cm,
  neckCm: cm,
  bodyFatPct: z.number().positive().max(70).optional(),
});

/** Log a measurement snapshot; returns the point (with derived body-fat for adults) + waist-hip. */
bodyRouter.post(
  '/body/metrics',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    res.status(201).json(await logBodyMetric(req.user!.id, metricSchema.parse(req.body)));
  }),
);

const rangeSchema = z.object({ range: z.coerce.number().int().min(1).max(3650).optional() });

/** Measurement time-series + per-metric trends/deltas + latest body-fat band + waist-hip. */
bodyRouter.get(
  '/body/metrics',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const { range } = rangeSchema.parse(req.query);
    res.json(await getBodyMetrics(req.user!.id, range ?? 365));
  }),
);

const photoSchema = z.object({ localRef: z.string().min(1).max(300), caption: z.string().max(200).optional() });

/** Register a progress-photo metadata record (the image itself stays on the device). */
bodyRouter.post(
  '/body/photos',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const { localRef, caption } = photoSchema.parse(req.body);
    res.status(201).json({ photo: await addPhoto(req.user!.id, localRef, caption) });
  }),
);

bodyRouter.get(
  '/body/photos',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    res.json({ photos: await listPhotos(req.user!.id) });
  }),
);

bodyRouter.delete(
  '/body/photos/:id',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    await deletePhoto(req.user!.id, req.params.id!);
    res.status(204).end();
  }),
);
