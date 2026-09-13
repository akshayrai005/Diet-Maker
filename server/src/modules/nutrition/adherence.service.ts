import { prisma } from '../../lib/prisma';
import { decryptJson } from '../../lib/crypto';
import { generateWeeklyWorkout } from '../exercise/workoutGenerator';
import { weightSlopePerDay, type WeightSample } from './adaptiveTdee';
import { computeAdherence, type AdherenceResult } from './adherence';
import type { SensitiveData } from '../profile/profile.schemas';

const WINDOW_DAYS = 7;
/** Weight-trend needs a wider span than the 7-day adherence window to be statistically meaningful - same reasoning/gate as adaptiveTdee.ts. */
const TREND_WINDOW_DAYS = 21;
const DEFAULT_TARGET_STEPS = 8000;

function dayKey(d: Date): string {
  return d.toISOString().slice(0, 10);
}

/**
 * Gathers real logged behavior over the last 7 days and scores adherence across calories,
 * protein, training consistency, steps, and weigh-in frequency - then reasons across them
 * (see adherence.ts) instead of a flat pass/fail on calories alone.
 */
export async function getAdherence(userId: string, now: Date = new Date()): Promise<AdherenceResult> {
  const since = new Date(now.getTime() - WINDOW_DAYS * 86_400_000);
  const trendSince = new Date(now.getTime() - TREND_WINDOW_DAYS * 86_400_000);

  const [snapshot, profile, foodLogs, exerciseLogs, steps, checkins] = await Promise.all([
    prisma.calcResultSnapshot.findFirst({ where: { userId }, orderBy: { createdAt: 'desc' } }),
    prisma.profile.findUnique({ where: { userId } }),
    prisma.foodLog.findMany({ where: { userId, loggedAt: { gte: since } }, select: { loggedAt: true, kcal: true, proteinG: true } }),
    prisma.exerciseLog.findMany({ where: { userId, performedAt: { gte: since } }, select: { performedAt: true } }),
    prisma.dailySteps.findMany({ where: { userId, date: { gte: since } }, select: { steps: true } }),
    prisma.weeklyCheckin.findMany({ where: { userId, date: { gte: trendSince } }, orderBy: { date: 'asc' }, select: { date: true, measurementsEnc: true } }),
  ]);

  const result = snapshot?.result as { dailyKcal?: number; proteinG?: number } | undefined;
  const targetKcal = result?.dailyKcal ?? 2000;
  const targetProteinG = result?.proteinG ?? 100;

  const kcalByDay = new Map<string, number>();
  const proteinByDay = new Map<string, number>();
  for (const l of foodLogs) {
    const k = dayKey(l.loggedAt);
    kcalByDay.set(k, (kcalByDay.get(k) ?? 0) + l.kcal);
    proteinByDay.set(k, (proteinByDay.get(k) ?? 0) + l.proteinG);
  }
  const loggedDays = kcalByDay.size;
  const avgKcal = loggedDays > 0 ? [...kcalByDay.values()].reduce((s, v) => s + v, 0) / loggedDays : null;
  const avgProteinG = loggedDays > 0 ? [...proteinByDay.values()].reduce((s, v) => s + v, 0) / loggedDays : null;

  const trainingDayKeys = new Set(exerciseLogs.map((e) => dayKey(e.performedAt)));
  const trainingDaysLogged = trainingDayKeys.size;

  const sensitive = profile?.sensitiveEnc ? decryptJson<SensitiveData>(profile.sensitiveEnc) : null;

  let trainingDaysPlanned = 5; // sane fallback if the plan can't be generated for some reason
  try {
    const plan = generateWeeklyWorkout(
      sensitive?.bodyGoal ?? 'athletic',
      sensitive?.exerciseLocation ?? 'home',
      {
        fitnessLevel: sensitive?.fitnessLevel ?? 'intermediate',
        restDayOfWeek: sensitive?.workoutRestDay,
      },
    );
    trainingDaysPlanned = plan.days.filter((d) => !d.rest).length;
  } catch {
    // Keep the fallback - adherence scoring shouldn't fail because plan generation had an edge case.
  }

  const avgSteps = steps.length > 0 ? steps.reduce((s, r) => s + r.steps, 0) / steps.length : null;

  const weightSamples: WeightSample[] = checkins
    .map((c) => {
      if (!c.measurementsEnc) return null;
      try {
        const m = decryptJson<{ weightKg?: number }>(c.measurementsEnc);
        return m.weightKg ? { date: dayKey(c.date), weightKg: m.weightKg } : null;
      } catch {
        return null;
      }
    })
    .filter((p): p is WeightSample => p !== null);
  const weighInsLogged = weightSamples.filter((s) => new Date(`${s.date}T00:00:00Z`) >= since).length;

  const goal = (profile?.goal as 'lose' | 'gain' | 'maintain') ?? 'maintain';
  const slope = weightSlopePerDay(weightSamples, now); // kg/day, positive = gaining
  let weightOnTrack: boolean | null = null;
  if (slope !== null) {
    if (goal === 'lose') weightOnTrack = slope < 0.005; // essentially flat-or-down counts as on track
    else if (goal === 'gain') weightOnTrack = slope > -0.005;
    else weightOnTrack = Math.abs(slope) < 0.03; // maintain: roughly flat
  }

  return computeAdherence({
    avgKcal,
    targetKcal,
    avgProteinG,
    targetProteinG,
    trainingDaysLogged,
    trainingDaysPlanned,
    avgSteps,
    targetSteps: DEFAULT_TARGET_STEPS,
    weighInsLogged,
    windowDays: WINDOW_DAYS,
    weightOnTrack,
  });
}
