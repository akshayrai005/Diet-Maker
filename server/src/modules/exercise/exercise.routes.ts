import { Router } from 'express';
import { asyncHandler } from '../../lib/asyncHandler';
import { requireAuth, type AuthedRequest } from '../../middleware/auth';
import { prisma } from '../../lib/prisma';
import { decryptJson } from '../../lib/crypto';
import { HttpError } from '../../middleware/error';
import type { SensitiveData } from '../profile/profile.schemas';
import { generateWeeklyWorkout } from './workoutGenerator';
import { localSunday, localToday, tzOffsetMin } from '../../lib/tz';
import type { BodyGoal, ExerciseLocation, FitnessLevel } from './exercise.types';
import { exerciseLogSchema } from './exerciseLog.schemas';
import * as logSvc from './exerciseLog.service';
import { adaptWorkoutToCycle } from './cycleAdapt';
import { recommendNextSession, suggestLevelChange, type LoggedSet } from './overload';
import { ageFromDob } from '../nutrition/calc.service';

export const exerciseRouter = Router();

function dateParam(q: unknown): Date {
  if (typeof q === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(q)) return new Date(`${q}T12:00:00Z`);
  return new Date();
}

/** Sensible default body goal from the diet goal when the user hasn't chosen one. */
function goalToBody(goal: string | null | undefined): BodyGoal {
  if (goal === 'gain') return 'muscular';
  if (goal === 'maintain') return 'athletic';
  return 'fatloss';
}

exerciseRouter.get(
  '/exercise-plan',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const profile = await prisma.profile.findUnique({ where: { userId: req.user!.id } });
    if (!profile || !profile.sensitiveEnc) throw new HttpError(400, 'Complete your health profile first');

    const s = decryptJson<SensitiveData>(profile.sensitiveEnc);
    const location: ExerciseLocation = s.exerciseLocation ?? 'home';
    const goal: BodyGoal = s.bodyGoal ?? goalToBody(profile.goal);
    const currentLevel: FitnessLevel = s.fitnessLevel ?? 'intermediate';
    const under18 = ageFromDob(s.dob) < 18;
    const medicalCaution = (s.conditions?.length ?? 0) > 0 || profile.reducedMobility;

    const offset = tzOffsetMin(req);
    let plan = generateWeeklyWorkout(goal, location, {
      restDayOfWeek: s.workoutRestDay,
      startDate: localSunday(offset),
      today: localToday(offset),
      fitnessLevel: currentLevel,
      intensity: s.intensityPreference,
      under18,
      medicalCaution,
    });

    // Period-aware: for female profiles, ease period days to gentle recovery.
    if (s.sex === 'female') {
      const periods = await prisma.periodLog.findMany({
        where: { userId: req.user!.id },
        orderBy: { startDate: 'desc' },
        take: 12,
      });
      if (periods.length > 0) {
        const durations = periods
          .filter((p) => p.endDate)
          .map((p) => Math.round((p.endDate!.getTime() - p.startDate.getTime()) / 86_400_000) + 1)
          .filter((d) => d >= 2 && d <= 10);
        const periodLen = durations.length
          ? Math.round(durations.reduce((a, b) => a + b, 0) / durations.length)
          : 5;
        plan = adaptWorkoutToCycle(plan, periods.map((p) => p.startDate), periodLen);
      }
    }

    // Progressive-overload: attach each exercise's next-session suggestion from logged history.
    const logs = await prisma.exerciseLog.findMany({
      where: { userId: req.user!.id },
      orderBy: { performedAt: 'desc' },
      take: 300,
    });
    const history: LoggedSet[] = logs.map((l) => ({
      exerciseName: l.exerciseName,
      date: l.performedAt.toISOString(),
      weightKg: l.weightKg,
      reps: l.reps,
      sets: l.sets,
    }));
    if (history.length > 0) {
      const byName = new Map(recommendNextSession(history).map((s) => [s.exerciseName, s]));
      plan = {
        ...plan,
        days: plan.days.map((day) => ({
          ...day,
          exercises: day.exercises.map((ex) => {
            const s = byName.get(ex.name);
            return s
              ? {
                  ...ex,
                  nextSession: {
                    suggestedWeightKg: s.suggestedWeightKg,
                    suggestedReps: s.suggestedReps,
                    suggestedSets: s.suggestedSets,
                    deload: s.deload,
                    rationale: s.rationale,
                  },
                }
              : ex;
          }),
        })),
      };
    }

    // Auto promotion/demotion nudge so the app can prompt "level up / ease down".
    const levelSuggestion = suggestLevelChange(history, currentLevel);

    res.json({ plan, levelSuggestion });
  }),
);

// ---- Workout logging (what the user actually performed) ----
exerciseRouter.post(
  '/exercise-logs',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const body = exerciseLogSchema.parse(req.body);
    const entry = await logSvc.logExercise(req.user!.id, body);
    res.status(201).json({ entry });
  }),
);

exerciseRouter.get(
  '/exercise-logs',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const entries = await logSvc.listExercise(req.user!.id, dateParam(req.query.date), tzOffsetMin(req));
    res.json({ entries });
  }),
);

exerciseRouter.get(
  '/exercise-logs/last',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const last = await logSvc.lastPerformance(req.user!.id);
    res.json({ last });
  }),
);

exerciseRouter.delete(
  '/exercise-logs/:id',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    await logSvc.deleteExercise(req.user!.id, req.params.id!);
    res.status(204).end();
  }),
);
