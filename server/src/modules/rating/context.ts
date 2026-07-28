import { prisma } from '../../lib/prisma';
import { decryptJson } from '../../lib/crypto';
import { dayRange } from '../logging/logging.service';
import { localDayKey } from '../../lib/tz';
import { computeStreak, dayKey } from '../logging/dashboard';
import { computeRating, type RatingResult } from './rating';
import { aggregateRatingInputs } from './aggregate';
import { computeAdherence, type AdherenceResult } from '../discipline/adherence';
import { overloadTrendFromHistory, type LoggedSet } from '../exercise/overload';
import { weeklyWorkoutForUser } from '../exercise/planForUser';

export interface Projection {
  label: string;
  weeks: number;
  weightKg: number;
  bmi: number;
}

/** Everything the rating engine and the coach brief need, fetched once. */
export interface UserContext {
  todayKey: string;
  target: { dailyKcal: number; proteinG: number; waterMl: number } | null;
  today: { kcal: number; proteinG: number; waterMl: number };
  loggingStreakDays: number;
  todayWorkoutScheduled: boolean;
  todayWorkoutDone: boolean;
  todayWellnessDone: boolean;
  todayFocus: string | null;
  rest: boolean;
  weightDeltaKg: number | null;
  /** Latest logged body weight (kg) from check-ins, if any. */
  latestWeightKg: number | null;
  /** Workout days completed in the last 7 days. */
  weeklyWorkoutsCompleted: number;
  /** Progressive-overload trend over the last ~60 days. */
  overloadTrend: 'up' | 'flat' | 'down';
  projection: Projection[];
  adherence: AdherenceResult;
  rating: RatingResult;
}

const WINDOW_DAYS = 7;

export async function gatherUserContext(userId: string, offsetMin = 0, now: Date = new Date()): Promise<UserContext> {
  const todayKey = localDayKey(offsetMin, now.getTime());
  const { start: todayStart, end: todayEnd } = dayRange(now, offsetMin);
  const windowStart = new Date(todayStart.getTime() - (WINDOW_DAYS - 1) * 86_400_000);
  const trendStart = new Date(now.getTime() - 60 * 86_400_000);

  const [snapshot, windowFood, windowWater, windowExercise, trendExercise, allFoodDays, todayExerciseCount, todayWellnessCount, checkins, plan] =
    await Promise.all([
      prisma.calcResultSnapshot.findFirst({ where: { userId }, orderBy: { createdAt: 'desc' } }),
      prisma.foodLog.findMany({
        where: { userId, loggedAt: { gte: windowStart } },
        select: { loggedAt: true, kcal: true, proteinG: true },
      }),
      prisma.waterLog.findMany({
        where: { userId, loggedAt: { gte: windowStart } },
        select: { loggedAt: true, amountMl: true },
      }),
      prisma.exerciseLog.findMany({
        where: { userId, performedAt: { gte: windowStart } },
        select: { performedAt: true },
      }),
      prisma.exerciseLog.findMany({
        where: { userId, performedAt: { gte: trendStart } },
        orderBy: { performedAt: 'desc' },
        select: { exerciseName: true, performedAt: true, weightKg: true, reps: true, sets: true },
      }),
      prisma.foodLog.findMany({ where: { userId }, select: { loggedAt: true } }),
      prisma.exerciseLog.count({ where: { userId, performedAt: { gte: todayStart, lt: todayEnd } } }),
      prisma.wellnessSession.count({ where: { userId, completedAt: { gte: todayStart, lt: todayEnd } } }),
      prisma.weeklyCheckin.findMany({ where: { userId }, orderBy: { date: 'asc' } }),
      weeklyWorkoutForUser(userId, offsetMin),
    ]);

  const result = snapshot?.result as
    | { dailyKcal: number; proteinG: number; waterMl: number; bmi: number; projection?: Projection[] }
    | undefined;
  const target = result ? { dailyKcal: result.dailyKcal, proteinG: result.proteinG, waterMl: result.waterMl } : null;

  // Per-day diet aggregates across the window.
  const byDay = new Map<string, { kcal: number; proteinG: number; waterMl: number }>();
  const bump = (k: string) => byDay.get(k) ?? byDay.set(k, { kcal: 0, proteinG: 0, waterMl: 0 }).get(k)!;
  for (const f of windowFood) {
    const d = bump(localDayKey(offsetMin, f.loggedAt.getTime()));
    d.kcal += f.kcal;
    d.proteinG += f.proteinG;
  }
  for (const w of windowWater) {
    const d = bump(localDayKey(offsetMin, w.loggedAt.getTime()));
    d.waterMl += w.amountMl;
  }
  const dietDays = [...byDay.values()];
  const today = byDay.get(todayKey) ?? { kcal: 0, proteinG: 0, waterMl: 0 };

  // Exercise.
  const workoutDaysCompleted = new Set(windowExercise.map((e) => localDayKey(offsetMin, e.performedAt.getTime()))).size;
  const history: LoggedSet[] = trendExercise.map((e) => ({
    exerciseName: e.exerciseName,
    date: e.performedAt.toISOString(),
    weightKg: e.weightKg,
    reps: e.reps,
    sets: e.sets,
  }));
  const overloadTrend = overloadTrendFromHistory(history);

  const todayDay = plan?.days.find((d) => d.label === 'Today') ?? null;
  const rest = todayDay?.rest ?? false;
  const todayWorkoutScheduled = todayDay != null && !todayDay.rest;
  const workoutsScheduled = plan?.days.filter((d) => !d.rest).length ?? 0;

  const loggingStreakDays = computeStreak(allFoodDays.map((d) => dayKey(d.loggedAt)), todayKey);

  // Weight trend delta from encrypted check-ins.
  const weights = checkins
    .map((c) => (c.measurementsEnc ? decryptJson<{ weightKg?: number }>(c.measurementsEnc).weightKg ?? null : null))
    .filter((w): w is number => w != null);
  const weightDeltaKg =
    weights.length >= 2 ? Math.round((weights[weights.length - 1]! - weights[0]!) * 10) / 10 : null;
  const latestWeightKg = weights.length > 0 ? weights[weights.length - 1]! : null;

  // Today's discipline score.
  const adherence = computeAdherence({
    kcalConsumed: today.kcal,
    kcalTarget: target?.dailyKcal,
    waterMl: today.waterMl,
    waterTargetMl: target?.waterMl,
    proteinG: today.proteinG,
    proteinTargetG: target?.proteinG,
    workoutScheduled: todayWorkoutScheduled,
    workoutDone: todayExerciseCount > 0,
    wellnessDone: todayWellnessCount > 0,
  });

  const rating = computeRating(
    aggregateRatingInputs({
      target,
      dietDays,
      daysInWindow: WINDOW_DAYS,
      workoutsCompleted: workoutDaysCompleted,
      workoutsScheduled,
      overloadTrend,
      loggingStreakDays,
      todayAdherence: dietDays.length > 0 || todayExerciseCount > 0 ? adherence.score : null,
    }),
  );

  return {
    todayKey,
    target,
    today,
    loggingStreakDays,
    todayWorkoutScheduled,
    todayWorkoutDone: todayExerciseCount > 0,
    todayWellnessDone: todayWellnessCount > 0,
    todayFocus: todayDay?.focus ?? null,
    rest,
    weightDeltaKg,
    latestWeightKg,
    weeklyWorkoutsCompleted: workoutDaysCompleted,
    overloadTrend,
    projection: result?.projection ?? [],
    adherence,
    rating,
  };
}
