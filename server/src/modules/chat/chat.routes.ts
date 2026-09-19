import { Router } from 'express';
import { z } from 'zod';
import { asyncHandler } from '../../lib/asyncHandler';
import { requireAuth, type AuthedRequest } from '../../middleware/auth';
import { chat, chatHistory, clearChatHistory } from './chat.service';
import { getAdaptation, defaultReminders } from './adaptive.service';
import { getMe } from '../auth/auth.service';
import { generateAndSavePlan } from '../food/plan.service';
import { tzOffsetMin } from '../../lib/tz';
import { getAdherence } from '../nutrition/adherence.service';
import { getSupplementSuggestions } from '../nutrition/supplementSuggest.service';

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

chatRouter.delete(
  '/chat/history',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    await clearChatHistory(req.user!.id);
    res.status(204).end();
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

chatRouter.get(
  '/adherence',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const adherence = await getAdherence(req.user!.id);
    res.json({ adherence });
  }),
);

chatRouter.get(
  '/supplements',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const supplements = await getSupplementSuggestions(req.user!.id);
    res.json({ supplements });
  }),
);

chatRouter.post(
  '/adapt/apply',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const adaptation = await getAdaptation(req.user!.id);
    // Only a target-adjustment recommendation changes calories; behaviour/on-track just rebuild.
    const kcalDelta = adaptation.status === 'adjust_target' ? adaptation.suggestedKcalDelta : 0;
    const plan = await generateAndSavePlan(req.user!.id, 2, tzOffsetMin(req), kcalDelta);
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
