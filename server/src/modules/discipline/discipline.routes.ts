import { Router } from 'express';
import { z } from 'zod';
import { asyncHandler } from '../../lib/asyncHandler';
import { requireAuth, type AuthedRequest } from '../../middleware/auth';
import { tzOffsetMin, localDayKey } from '../../lib/tz';
import { getDisciplineToday, toggleHabit, createHabit, deleteHabit } from './discipline.service';

export const disciplineRouter = Router();

const createSchema = z.object({ title: z.string().min(1).max(60), icon: z.string().max(4).optional() });

/** Add a custom habit. */
disciplineRouter.post(
  '/habits',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const { title, icon } = createSchema.parse(req.body);
    const habit = await createHabit(req.user!.id, title, icon);
    res.status(201).json({ habit });
  }),
);

/** Delete a habit. */
disciplineRouter.delete(
  '/habits/:id',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    await deleteHabit(req.user!.id, req.params.id!);
    res.status(204).end();
  }),
);

/** Discipline dashboard - real adherence score + per-habit streaks + evening review + nudges. */
disciplineRouter.get(
  '/discipline/today',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    // Device-sourced metrics (Health Connect) that auto-complete the steps/sleep habits.
    const num = (v: unknown) => {
      const n = Number(v);
      return Number.isFinite(n) && n >= 0 ? n : undefined;
    };
    const deviceMetrics = { steps: num(req.query.steps), sleepHours: num(req.query.sleepHours) };
    const data = await getDisciplineToday(req.user!.id, tzOffsetMin(req), new Date(), deviceMetrics);
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
