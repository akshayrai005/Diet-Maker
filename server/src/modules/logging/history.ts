import { prisma } from '../../lib/prisma';
import { decryptJson } from '../../lib/crypto';
import { localDayKey } from '../../lib/tz';

/**
 * Day-by-day history for the trends screen — calories, protein, water and workout status for EVERY
 * day in the window (not just logged days, so gaps show as zeros), plus the body-weight series.
 * PURE aggregation (buildDailyHistory) over already-fetched rows; the wrapper only fetches. Steps
 * are device-local (Health Connect) and are merged on the client — the server never stores them.
 */

export interface HistoryDay {
  date: string; // YYYY-MM-DD (local)
  kcal: number;
  proteinG: number;
  waterMl: number;
  workout: boolean;
}

export interface WeightPointH {
  date: string; // YYYY-MM-DD
  weightKg: number;
}

export interface DailyHistory {
  windowDays: number;
  days: HistoryDay[]; // oldest → newest, one entry per calendar day
  weight: WeightPointH[];
}

export interface HistFoodRow {
  loggedAt: Date;
  kcal: number;
  proteinG: number;
}
export interface HistWaterRow {
  loggedAt: Date;
  amountMl: number;
}
export interface HistExerciseRow {
  performedAt: Date;
}

const DAY_MS = 86_400_000;

/** Pure: complete per-day series over the last `windowDays` days ending today (local). */
export function buildDailyHistory(
  windowDays: number,
  offsetMin: number,
  food: HistFoodRow[],
  water: HistWaterRow[],
  exercise: HistExerciseRow[],
  weight: WeightPointH[],
  now: Date,
): DailyHistory {
  // Every day key in the window, oldest → newest, seeded to zero so gaps render honestly.
  const days: HistoryDay[] = [];
  const index = new Map<string, HistoryDay>();
  for (let i = windowDays - 1; i >= 0; i--) {
    const key = localDayKey(offsetMin, now.getTime() - i * DAY_MS);
    const d: HistoryDay = { date: key, kcal: 0, proteinG: 0, waterMl: 0, workout: false };
    days.push(d);
    index.set(key, d);
  }

  for (const f of food) {
    const d = index.get(localDayKey(offsetMin, f.loggedAt.getTime()));
    if (d) {
      d.kcal += f.kcal;
      d.proteinG += f.proteinG;
    }
  }
  for (const w of water) {
    const d = index.get(localDayKey(offsetMin, w.loggedAt.getTime()));
    if (d) d.waterMl += w.amountMl;
  }
  for (const e of exercise) {
    const d = index.get(localDayKey(offsetMin, e.performedAt.getTime()));
    if (d) d.workout = true;
  }

  for (const d of days) {
    d.kcal = Math.round(d.kcal);
    d.proteinG = Math.round(d.proteinG);
    d.waterMl = Math.round(d.waterMl);
  }

  // Weight points that fall inside the window, oldest → newest.
  const startKey = days[0]!.date;
  const inWindow = weight.filter((w) => w.date >= startKey).sort((a, b) => a.date.localeCompare(b.date));

  return { windowDays, days, weight: inWindow };
}

/** Fetch + aggregate the daily history for the last `days` days. */
export async function getHistory(userId: string, days: number, offsetMin = 0, now: Date = new Date()): Promise<DailyHistory> {
  const windowDays = Math.min(90, Math.max(1, Math.round(days)));
  const since = new Date(now.getTime() - windowDays * DAY_MS);
  const [food, water, exercise, checkins] = await Promise.all([
    prisma.foodLog.findMany({ where: { userId, loggedAt: { gte: since } }, select: { loggedAt: true, kcal: true, proteinG: true } }),
    prisma.waterLog.findMany({ where: { userId, loggedAt: { gte: since } }, select: { loggedAt: true, amountMl: true } }),
    prisma.exerciseLog.findMany({ where: { userId, performedAt: { gte: since } }, select: { performedAt: true } }),
    prisma.weeklyCheckin.findMany({ where: { userId }, orderBy: { date: 'asc' }, select: { date: true, measurementsEnc: true } }),
  ]);

  const weight: WeightPointH[] = checkins
    .map((c) => {
      if (!c.measurementsEnc) return null;
      try {
        const m = decryptJson<{ weightKg?: number }>(c.measurementsEnc);
        return m.weightKg != null ? { date: c.date.toISOString().slice(0, 10), weightKg: m.weightKg } : null;
      } catch {
        return null;
      }
    })
    .filter((w): w is WeightPointH => w !== null);

  return buildDailyHistory(windowDays, offsetMin, food, water, exercise, weight, now);
}
