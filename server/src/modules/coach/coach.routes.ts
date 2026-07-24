import { Router } from 'express';
import { asyncHandler } from '../../lib/asyncHandler';
import { requireAuth, type AuthedRequest } from '../../middleware/auth';
import { tzOffsetMin } from '../../lib/tz';
import { prisma } from '../../lib/prisma';
import { gatherUserContext } from '../rating/context';
import { buildCoachBrief } from './coach.brief';

export const coachRouter = Router();

/** One deterministic 4-pillar daily brief (rules-safe). Numbers come from the server, never an LLM. */
coachRouter.get(
  '/coach/today',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const offsetMin = tzOffsetMin(req);
    const now = new Date();
    const [ctx, user] = await Promise.all([
      gatherUserContext(req.user!.id, offsetMin, now),
      prisma.user.findUnique({ where: { id: req.user!.id }, select: { firstName: true } }),
    ]);

    // Local hour for the greeting — pure input to the templated brief.
    const localHour = new Date(now.getTime() + offsetMin * 60_000).getUTCHours();
    const calorieRemaining = ctx.target ? Math.round(ctx.target.dailyKcal - ctx.today.kcal) : null;

    const brief = buildCoachBrief({
      firstName: user?.firstName ?? null,
      hour: localHour,
      overall: ctx.rating.overall,
      grade: ctx.rating.grade,
      biggestLever: ctx.rating.biggestLever.message,
      adherenceTopActions: ctx.adherence.topActions,
      streakDays: ctx.loggingStreakDays,
      todayWorkoutScheduled: ctx.todayWorkoutScheduled,
      rest: ctx.rest,
      todayFocus: ctx.todayFocus,
      todayWorkoutDone: ctx.todayWorkoutDone,
      todayWellnessDone: ctx.todayWellnessDone,
      weightDeltaKg: ctx.weightDeltaKg,
      projection: ctx.projection,
      calorieRemaining,
    });

    res.json({ brief, rating: ctx.rating });
  }),
);
