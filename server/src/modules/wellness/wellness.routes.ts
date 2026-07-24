import { Router } from 'express';
import { z } from 'zod';
import { asyncHandler } from '../../lib/asyncHandler';
import { requireAuth, type AuthedRequest } from '../../middleware/auth';
import { getWellness, recommendWellness, suggestNow } from './wellness';
import { logWellnessSession, wellnessHistory } from './wellnessLog.service';
import { requireCompleteProfile } from '../profile/profile.service';
import { getCycle } from '../cycle/cycle.service';
import { tzOffsetMin } from '../../lib/tz';

export const wellnessRouter = Router();

const sessionSchema = z.object({ refId: z.string().min(1).max(80) });

/** Guided yoga flows + meditation/breathing sessions (static, zero-cost). */
wellnessRouter.get(
  '/wellness',
  requireAuth,
  asyncHandler(async (_req, res) => {
    res.json(getWellness());
  }),
);

/**
 * The day's recommended yoga + meditation, chosen from the whole picture: cycle phase,
 * today's mood (?mood=1..5) and lifestyle/conditions. ?mood is optional.
 */
wellnessRouter.get(
  '/wellness/recommend',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const { profile, sensitive } = await requireCompleteProfile(req.user!.id);
    const moodRaw = Number(req.query.mood);
    const mood = Number.isFinite(moodRaw) && moodRaw >= 1 && moodRaw <= 5 ? moodRaw : null;

    let phase: string | null = null;
    if (sensitive.sex === 'female') {
      const cycle = await getCycle(req.user!.id);
      if ('phase' in cycle && cycle.phase) phase = cycle.phase;
    }

    const recommendation = recommendWellness({
      phase,
      mood,
      conditions: sensitive.conditions ?? [],
      activityLevel: profile.activityLevel,
    });
    res.json({ recommendation });
  }),
);

/** A specific flow + meditation for right now, from the local time and how the user feels. */
wellnessRouter.get(
  '/wellness/suggest',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const num = (q: unknown): number | null => {
      const n = Number(q);
      return Number.isFinite(n) ? n : null;
    };
    const localHour = new Date(Date.now() + tzOffsetMin(req) * 60_000).getUTCHours();
    const suggestion = suggestNow({
      hour: localHour,
      mood: num(req.query.mood),
      energy: num(req.query.energy),
      sleepHours: num(req.query.sleep),
    });
    res.json({ suggestion });
  }),
);

/** Log a completed yoga/meditation/breathing session. Duration + calories are server-computed
 * from the content id, so the numbers stay authoritative. */
wellnessRouter.post(
  '/wellness/session',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const { refId } = sessionSchema.parse(req.body);
    const session = await logWellnessSession(req.user!.id, refId);
    res.status(201).json({ session });
  }),
);

/** Wellness streak + weekly totals + recent sessions. */
wellnessRouter.get(
  '/wellness/history',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const history = await wellnessHistory(req.user!.id, tzOffsetMin(req));
    res.json(history);
  }),
);
