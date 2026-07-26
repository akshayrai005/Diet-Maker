import { Router } from 'express';
import { z } from 'zod';
import { asyncHandler } from '../../lib/asyncHandler';
import { requireAuth, type AuthedRequest } from '../../middleware/auth';
import { getProfile, upsertProfile, requireCompleteProfile } from './profile.service';
import { profileUpsertSchema, calcPreviewSchema } from './profile.schemas';
import { computeCalcResult } from '../nutrition/calcResult';
import { computeAndSaveForUser, latestCalcResult, ageFromDob } from '../nutrition/calc.service';
import { goalTimeline } from '../../calc/goalTimeline';

export const profileRouter = Router();

const goalTimelineSchema = z.object({
  targetWeightKg: z.number().positive().max(500),
  desiredWeeks: z.number().int().min(1).max(260),
});

profileRouter.get(
  '/profile',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const profile = await getProfile(req.user!.id);
    res.json({ profile });
  }),
);

profileRouter.put(
  '/profile',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const body = profileUpsertSchema.parse(req.body);
    const profile = await upsertProfile(req.user!.id, body);
    res.json({ profile });
  }),
);

/** Authenticated: compute + persist the CalcResult from the stored profile. */
profileRouter.post(
  '/calc',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const result = await computeAndSaveForUser(req.user!.id);
    res.json({ result });
  }),
);

profileRouter.get(
  '/calc/latest',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const result = await latestCalcResult(req.user!.id);
    res.json({ result });
  }),
);

/** Live preview of a goal timeline for the user's current weight — safe, clamped; nothing stored. */
profileRouter.post(
  '/goal-timeline',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const body = goalTimelineSchema.parse(req.body);
    const { profile, sensitive } = await requireCompleteProfile(req.user!.id);
    void profile;
    const weightLossBlocked = (sensitive.conditions ?? []).some((c) => ['pregnancy', 'breastfeeding', 'cancer'].includes(c));
    res.json(
      goalTimeline({
        currentWeightKg: sensitive.currentWeightKg,
        targetWeightKg: body.targetWeightKg,
        desiredWeeks: body.desiredWeeks,
        isMinor: ageFromDob(sensitive.dob) < 18,
        weightLossBlocked,
      }),
    );
  }),
);

/** Unauthenticated onboarding preview — pure calc, nothing stored. */
profileRouter.post(
  '/calc/preview',
  asyncHandler(async (req, res) => {
    const body = calcPreviewSchema.parse(req.body);
    res.json({ result: computeCalcResult(body) });
  }),
);
