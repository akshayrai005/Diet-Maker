import { prisma } from '../../lib/prisma';
import { HttpError } from '../../middleware/error';
import { getWellness } from './wellness';
import { latestBodyWeightKg } from '../logging/bodyWeight';
import { wellnessKcal, type WellnessType } from '../../calc/activityCalories';
import { computeStreak } from '../logging/dashboard';
import { localDayKey } from '../../lib/tz';

/** Resolve a content id to its canonical type / name / duration - the server never trusts the
 * client for these, so calories and streaks stay authoritative. */
function resolveContent(refId: string): { type: WellnessType; refName: string; durationMin: number } | null {
  const { yoga, meditation } = getWellness();
  const flow = yoga.find((y) => y.id === refId);
  if (flow) return { type: 'yoga', refName: flow.name, durationMin: flow.durationMin };
  const med = meditation.find((m) => m.id === refId);
  if (med) {
    // A meditation with a breathing pattern is logged as 'breathing'.
    return { type: med.pattern ? 'breathing' : 'meditation', refName: med.name, durationMin: med.durationMin };
  }
  return null;
}

export async function logWellnessSession(userId: string, refId: string) {
  const content = resolveContent(refId);
  if (!content) throw new HttpError(404, 'Unknown wellness session');

  const bodyWeightKg = await latestBodyWeightKg(userId);
  const kcal = wellnessKcal(content.type, content.durationMin, bodyWeightKg);

  const session = await prisma.wellnessSession.create({
    data: {
      userId,
      type: content.type,
      refId,
      refName: content.refName,
      durationMin: content.durationMin,
      kcal,
    },
  });
  return session;
}

export interface WellnessHistory {
  streakDays: number;
  weekCount: number;
  totalSessions: number;
  totalMinutes: number;
  totalKcal: number;
  sessions: {
    id: string;
    type: string;
    refName: string;
    durationMin: number;
    kcal: number;
    completedAt: string;
  }[];
}

export async function wellnessHistory(userId: string, offsetMin = 0, now: Date = new Date()): Promise<WellnessHistory> {
  const rows = await prisma.wellnessSession.findMany({
    where: { userId },
    orderBy: { completedAt: 'desc' },
    take: 200,
  });

  const todayKey = localDayKey(offsetMin, now.getTime());
  const dayKeys = rows.map((r) => localDayKey(offsetMin, r.completedAt.getTime()));
  const streakDays = computeStreak(dayKeys, todayKey);

  const weekAgoMs = now.getTime() - 7 * 86_400_000;
  const weekCount = rows.filter((r) => r.completedAt.getTime() >= weekAgoMs).length;

  return {
    streakDays,
    weekCount,
    totalSessions: rows.length,
    totalMinutes: rows.reduce((s, r) => s + r.durationMin, 0),
    totalKcal: rows.reduce((s, r) => s + r.kcal, 0),
    sessions: rows.slice(0, 30).map((r) => ({
      id: r.id,
      type: r.type,
      refName: r.refName,
      durationMin: r.durationMin,
      kcal: r.kcal,
      completedAt: r.completedAt.toISOString(),
    })),
  };
}

/** Sum of calories burned from wellness sessions on the given local day (for the calorie picture). */
export async function wellnessKcalOnDay(userId: string, start: Date, end: Date): Promise<number> {
  const agg = await prisma.wellnessSession.aggregate({
    where: { userId, completedAt: { gte: start, lt: end } },
    _sum: { kcal: true },
  });
  return agg._sum.kcal ?? 0;
}
