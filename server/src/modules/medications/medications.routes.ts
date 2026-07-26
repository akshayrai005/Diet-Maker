import { Router } from 'express';
import { z } from 'zod';
import { asyncHandler } from '../../lib/asyncHandler';
import { requireAuth, type AuthedRequest } from '../../middleware/auth';
import { tzOffsetMin } from '../../lib/tz';
import {
  listMedications,
  createMedication,
  updateMedication,
  setActive,
  deleteMedication,
  logAdherence,
} from './medications.service';

export const medicationsRouter = Router();

const bodySchema = z.object({
  type: z.enum(['med', 'supplement']),
  name: z.string().min(1).max(120),
  dose: z.string().max(80).optional(),
  times: z.array(z.string().max(5)).max(12).optional(),
  notes: z.string().max(500).optional(),
  active: z.boolean().optional(),
});

/** List all meds/supplements + the standing pharmacist/doctor disclaimer. */
medicationsRouter.get(
  '/medications',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    res.json(await listMedications(req.user!.id, tzOffsetMin(req)));
  }),
);

medicationsRouter.post(
  '/medications',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const body = bodySchema.parse(req.body);
    res.status(201).json({ medication: await createMedication(req.user!.id, body) });
  }),
);

medicationsRouter.put(
  '/medications/:id',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const body = bodySchema.parse(req.body);
    res.json({ medication: await updateMedication(req.user!.id, req.params.id!, body) });
  }),
);

const activeSchema = z.object({ active: z.boolean() });

medicationsRouter.patch(
  '/medications/:id/active',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const { active } = activeSchema.parse(req.body);
    await setActive(req.user!.id, req.params.id!, active);
    res.status(204).end();
  }),
);

medicationsRouter.delete(
  '/medications/:id',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    await deleteMedication(req.user!.id, req.params.id!);
    res.status(204).end();
  }),
);

/** Log an adherence event ("I took it"). */
medicationsRouter.post(
  '/medications/:id/log',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    await logAdherence(req.user!.id, req.params.id!);
    res.status(201).json({ ok: true });
  }),
);
