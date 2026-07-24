import { Router } from 'express';
import { z } from 'zod';
import { asyncHandler } from '../../lib/asyncHandler';
import { requireAuth, type AuthedRequest } from '../../middleware/auth';
import { prisma } from '../../lib/prisma';

export const remindersRouter = Router();

const DEFAULTS = {
  mealsEnabled: true,
  waterEnabled: true,
  workoutEnabled: false,
  weighInEnabled: true,
  walkEnabled: false,
  workoutTime: null as string | null,
  waterIntervalMin: 60,
};

const prefsSchema = z
  .object({
    mealsEnabled: z.boolean(),
    waterEnabled: z.boolean(),
    workoutEnabled: z.boolean(),
    weighInEnabled: z.boolean(),
    walkEnabled: z.boolean(),
    workoutTime: z
      .string()
      .regex(/^([01]\d|2[0-3]):[0-5]\d$/, 'workoutTime must be HH:MM')
      .nullable(),
    waterIntervalMin: z.number().int().min(15).max(240),
  })
  .partial();

interface PrefsRow {
  mealsEnabled: boolean;
  waterEnabled: boolean;
  workoutEnabled: boolean;
  weighInEnabled: boolean;
  walkEnabled: boolean;
  workoutTime: string | null;
  waterIntervalMin: number;
}

function view(row: PrefsRow) {
  return {
    mealsEnabled: row.mealsEnabled,
    waterEnabled: row.waterEnabled,
    workoutEnabled: row.workoutEnabled,
    weighInEnabled: row.weighInEnabled,
    walkEnabled: row.walkEnabled,
    workoutTime: row.workoutTime,
    waterIntervalMin: row.waterIntervalMin,
  };
}

/** The user's reminder preferences (server mirror of the on-device schedule). */
remindersRouter.get(
  '/reminders/prefs',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const row = await prisma.reminderPrefs.findUnique({ where: { userId: req.user!.id } });
    res.json({ prefs: row ? view(row) : DEFAULTS });
  }),
);

/** Update (partial) reminder preferences; upserts on first write. */
remindersRouter.put(
  '/reminders/prefs',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const patch = prefsSchema.parse(req.body);
    const row = await prisma.reminderPrefs.upsert({
      where: { userId: req.user!.id },
      update: patch,
      create: { userId: req.user!.id, ...DEFAULTS, ...patch },
    });
    res.json({ prefs: view(row) });
  }),
);
