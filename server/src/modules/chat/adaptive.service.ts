import { computeAndSaveForUser } from '../nutrition/calc.service';
import { prisma } from '../../lib/prisma';
import { decryptJson } from '../../lib/crypto';
import { dayKey, type WeightPoint } from '../logging/dashboard';
import { computeAdaptation, type Adaptation } from './adaptive';

const WINDOW_DAYS = 7;

/** Gathers recent adherence + weight data and returns a plan adaptation recommendation. */
export async function getAdaptation(userId: string, now: Date = new Date()): Promise<Adaptation> {
  const since = new Date(now.getTime() - WINDOW_DAYS * 86_400_000);

  const [snapshot, profile, logs, checkins] = await Promise.all([
    computeAndSaveForUser(userId).then((r) => ({ result: r })).catch(() => prisma.calcResultSnapshot.findFirst({ where: { userId }, orderBy: { createdAt: 'desc' } })),
    prisma.profile.findUnique({ where: { userId } }),
    prisma.foodLog.findMany({ where: { userId, loggedAt: { gte: since } }, select: { loggedAt: true, kcal: true } }),
    prisma.weeklyCheckin.findMany({ where: { userId }, orderBy: { date: 'asc' } }),
  ]);

  const result = snapshot?.result as { dailyKcal: number } | undefined;
  const targetKcal = result?.dailyKcal ?? 2000;
  const goal = (profile?.goal as 'lose' | 'maintain' | 'gain') ?? 'maintain';

  // Sum logged kcal per day.
  const perDay = new Map<string, number>();
  for (const l of logs) {
    const k = dayKey(l.loggedAt);
    perDay.set(k, (perDay.get(k) ?? 0) + l.kcal);
  }

  const weightPoints: WeightPoint[] = checkins
    .map((c) => {
      if (!c.measurementsEnc) return null;
      try {
        const m = decryptJson<{ weightKg: number }>(c.measurementsEnc);
        return { date: c.date.toISOString(), weightKg: m.weightKg };
      } catch {
        return null;
      }
    })
    .filter((p): p is WeightPoint => p !== null);

  return computeAdaptation({
    goal,
    targetKcal,
    loggedDailyKcals: [...perDay.values()].map((v) => Math.round(v)),
    weightPoints,
  });
}

export interface Reminder {
  kind: 'water' | 'meal';
  label: string;
  time: string; // HH:mm (24h), user-local
}

/** Default reminder schedule (water through the day + the main meals). Pure. */
export function defaultReminders(): Reminder[] {
  const water: Reminder[] = ['08:00', '10:30', '13:00', '15:30', '18:00', '20:30'].map((time) => ({
    kind: 'water',
    label: 'Time for a glass of water',
    time,
  }));
  const meals: Reminder[] = [
    { kind: 'meal', label: 'Breakfast', time: '08:30' },
    { kind: 'meal', label: 'Lunch', time: '13:00' },
    { kind: 'meal', label: 'Dinner', time: '20:00' },
  ];
  return [...meals, ...water].sort((a, b) => a.time.localeCompare(b.time));
}
