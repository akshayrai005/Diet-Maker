import { Router } from 'express';
import { z } from 'zod';
import { asyncHandler } from '../../lib/asyncHandler';
import { requireAuth, type AuthedRequest } from '../../middleware/auth';
import { prisma } from '../../lib/prisma';
import { decryptJson } from '../../lib/crypto';
import { HttpError } from '../../middleware/error';
import type { SensitiveData } from '../profile/profile.schemas';
import { generateWeeklyWorkout, generateStagedMovementPlan } from './workoutGenerator';
import { determineMovementStage } from './mobilityStaging';
import { localSunday, localToday, tzOffsetMin } from '../../lib/tz';
import type { BodyGoal, ExerciseLocation, FitnessLevel } from './exercise.types';
import { exerciseLogSchema } from './exerciseLog.schemas';
import * as logSvc from './exerciseLog.service';
import { adaptWorkoutToCycle } from './cycleAdapt';
import { recommendNextSession, suggestLevelChange, type LoggedSet } from './overload';
import { rampFor } from './rampUp';
import { defaultTrainingSplit, suggestSplitUpgrade } from './splitSuggestion';
import { ageFromDob } from '../nutrition/calc.service';
import { strengthTrend } from './strength';

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

    // Mobility staging (see mobilityStaging.ts): a general gate for ANY reported mobility
    // limitation, not one specific condition. When staged, the whole plan is a dedicated
    // zero/low-impact program instead of the normal split - "gentle" volume-scaling on a
    // bodyweight-squat template isn't actually safe for someone this applies to.
    const staging = determineMovementStage({
      reducedMobility: profile.reducedMobility,
      heightCm: profile.heightCm,
      currentWeightKg: s.currentWeightKg,
    });

    const explicitSplit = (s as { trainingSplit?: import('./exercise.types').TrainingSplit }).trainingSplit;
    const effectiveSplit = explicitSplit ?? defaultTrainingSplit(goal, s.gymMembershipMonths);
    const splitSuggestion = suggestSplitUpgrade(explicitSplit, s.gymMembershipMonths);

    let plan = staging.stage !== 'full'
      ? generateStagedMovementPlan(staging.stage, {
          restDayOfWeek: s.workoutRestDay,
          startDate: localSunday(offset),
          today: localToday(offset),
        })
      : generateWeeklyWorkout(goal, location, {
          restDayOfWeek: s.workoutRestDay,
          startDate: localSunday(offset),
          today: localToday(offset),
          fitnessLevel: currentLevel,
          intensity: s.intensityPreference,
          under18,
          medicalCaution,
          // Selectable training split (Chest/Back/... , PPL, Upper-Lower, Full-body) + priority muscles.
          // Unset -> mixed full-body sessions for the first month, then a body-part split (see splitSuggestion.ts).
          split: effectiveSplit,
          priorityMuscles: (s as { priorityMuscles?: string[] }).priorityMuscles,
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

    // New-to-training ramp-up (see rampUp.ts): ease volume in over the first 4 weeks instead of
    // full intensity from day one, for anyone who's new to the gym or didn't say otherwise.
    const accountAgeDays = Math.floor((Date.now() - profile.createdAt.getTime()) / 86_400_000);
    const ramp = rampFor(s.gymMembershipMonths, accountAgeDays);
    if (ramp) {
      const soften = (ex: typeof plan.days[number]['exercises'][number]) => ({
        ...ex,
        sets: Math.max(1, Math.round(ex.sets * ramp.factor)),
      });
      plan = {
        ...plan,
        note: [plan.note, `🌱 New here - easing you in (week ${ramp.week}/4). Full intensity from week 4 as your body adapts.`]
          .filter(Boolean)
          .join(' '),
        days: plan.days.map((day) => ({
          ...day,
          exercises: day.exercises.map(soften),
          core: (day.core ?? []).map(soften),
        })),
      };
    }

    // Soreness check-in softening: when the user reports ≥ 3/5, reduce today's volume.
    const soreness = Number(req.query.soreness) || 0;
    if (soreness >= 3) {
      const factor = soreness >= 5 ? 0.4 : soreness >= 4 ? 0.6 : 0.8;
      plan = {
        ...plan,
        days: plan.days.map((day) => {
          if (day.label !== 'Today') return day;
          const soften = (ex: typeof day.exercises[number]) => ({
            ...ex,
            sets: Math.max(1, Math.round(ex.sets * factor)),
            reps: ex.reps.replace(/\d+/g, (m: string) => String(Math.max(1, Math.round(Number(m) * factor)))),
          });
          return {
            ...day,
            exercises: day.exercises.map(soften),
            warmup: (day.warmup ?? []).map(soften),
            core: (day.core ?? []).map(soften),
            cooldown: (day.cooldown ?? []).map(soften),
          };
        }),
      };
    }

    // Auto promotion/demotion nudge so the app can prompt "level up / ease down".
    const levelSuggestion = suggestLevelChange(history, currentLevel);

    res.json({ plan, levelSuggestion, movementStage: staging, splitSuggestion });
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

// ---- Daily step sync (Health Connect / Google Fit) ----
// Steps were shown on the dashboard from a fresh device read every time but never persisted
// server-side, so nothing (like real-activity detection for TDEE) could see step history. The
// app upserts today's count here whenever it successfully reads Health Connect.
const stepsSyncSchema = z.object({
  date: z.string().date(),
  steps: z.number().int().min(0).max(200_000),
});

exerciseRouter.post(
  '/activity/steps',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const body = stepsSyncSchema.parse(req.body);
    const date = new Date(`${body.date}T00:00:00Z`);
    const row = await prisma.dailySteps.upsert({
      where: { userId_date: { userId: req.user!.id, date } },
      create: { userId: req.user!.id, date, steps: body.steps },
      update: { steps: body.steps },
    });
    res.status(201).json({ date: body.date, steps: row.steps });
  }),
);

/** Estimated-1RM strength trend per exercise (Epley) from logged weighted sets. */
exerciseRouter.get(
  '/exercise/strength-trend',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const logs = await prisma.exerciseLog.findMany({
      where: { userId: req.user!.id, weightKg: { not: null } },
      orderBy: { performedAt: 'asc' },
      select: { exerciseName: true, weightKg: true, reps: true, performedAt: true },
    });
    res.json({ trends: strengthTrend(logs) });
  }),
);

// ---- "My Gym" - a user-curated quick-access subset of the exercise library, so logging a
// workout doesn't require searching the full ~1500-exercise catalog every session. Name-keyed
// to match the client's ExerciseCatalog entries (there's no server-side exercise table).

const gymFavoriteSchema = z.object({ exerciseName: z.string().min(1).max(120) });

exerciseRouter.get(
  '/gym-favorites',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const rows = await prisma.gymFavorite.findMany({
      where: { userId: req.user!.id },
      orderBy: { createdAt: 'asc' },
      select: { exerciseName: true },
    });
    res.json({ exerciseNames: rows.map((r) => r.exerciseName) });
  }),
);

exerciseRouter.post(
  '/gym-favorites',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const { exerciseName } = gymFavoriteSchema.parse(req.body);
    await prisma.gymFavorite.upsert({
      where: { userId_exerciseName: { userId: req.user!.id, exerciseName } },
      create: { userId: req.user!.id, exerciseName },
      update: {},
    });
    res.status(201).json({ exerciseName });
  }),
);

exerciseRouter.delete(
  '/gym-favorites/:exerciseName',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    await prisma.gymFavorite.deleteMany({
      where: { userId: req.user!.id, exerciseName: req.params.exerciseName },
    });
    res.status(204).end();
  }),
);
