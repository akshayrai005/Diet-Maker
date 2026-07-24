import { Router } from 'express';
import { z } from 'zod';
import { asyncHandler } from '../../lib/asyncHandler';
import { requireAuth, type AuthedRequest } from '../../middleware/auth';
import { tzOffsetMin, localDayKey } from '../../lib/tz';
import { getDisciplineToday, toggleHabit } from './discipline.service';

export const disciplineRouter = Router();

/** Discipline dashboard — real adherence score + per-habit streaks + evening review + nudges. */
disciplineRouter.get(
  '/discipline/today',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const data = await getDisciplineToday(req.user!.id, tzOffsetMin(req));
    res.json(data);
  }),
);

const toggleSchema = z.object({
  done: z.boolean(),
  date: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/)
    .optional(),
});

/** Check a habit off (or undo) for a day (defaults to today). */
disciplineRouter.post(
  '/habits/:id/toggle',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const { done, date } = toggleSchema.parse(req.body);
    const day = date ?? localDayKey(tzOffsetMin(req), Date.now());
    await toggleHabit(req.user!.id, req.params.id!, day, done);
    const data = await getDisciplineToday(req.user!.id, tzOffsetMin(req));
    res.json(data);
  }),
);
