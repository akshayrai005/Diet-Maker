import { Router } from 'express';
import { z } from 'zod';
import { asyncHandler } from '../../lib/asyncHandler';
import { requireAuth, type AuthedRequest } from '../../middleware/auth';
import { chat, chatHistory } from './chat.service';
import { getAdaptation, defaultReminders } from './adaptive.service';
import { getMe } from '../auth/auth.service';
import { generateAndSavePlan } from '../food/plan.service';
import { tzOffsetMin } from '../../lib/tz';

export const chatRouter = Router();

const chatSchema = z.object({ message: z.string().min(1).max(1000) });

chatRouter.post(
  '/chat',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const { message } = chatSchema.parse(req.body);
    const me = await getMe(req.user!.id);
    const reply = await chat(req.user!.id, message, me.firstName, tzOffsetMin(req));
    res.json({ reply });
  }),
);

chatRouter.get(
  '/chat/history',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const messages = await chatHistory(req.user!.id);
    res.json({ messages });
  }),
);

chatRouter.get(
  '/adapt',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const adaptation = await getAdaptation(req.user!.id);
    res.json({ adaptation });
  }),
);

chatRouter.post(
  '/adapt/apply',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const adaptation = await getAdaptation(req.user!.id);
    // Only a target-adjustment recommendation changes calories; behaviour/on-track just rebuild.
    const kcalDelta = adaptation.status === 'adjust_target' ? adaptation.suggestedKcalDelta : 0;
    const plan = await generateAndSavePlan(req.user!.id, 7, tzOffsetMin(req), kcalDelta);
    res.status(201).json({ adaptation, applied: kcalDelta !== 0, kcalDelta, plan });
  }),
);

chatRouter.get(
  '/reminders',
  requireAuth,
  asyncHandler(async (_req: AuthedRequest, res) => {
    res.json({ reminders: defaultReminders() });
  }),
);
