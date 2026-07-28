import { prisma } from '../../lib/prisma';
import { localDayKey } from '../../lib/tz';

/**
 * Diet/activity trend over any window (7 or 30 days) — day-by-day aggregates the coach summarises.
 * PURE aggregation (aggregateDietTrend) over already-fetched logs; the wrapper just fetches. The
 * coach never invents a trend — it can only state what these numbers show. Deterministic.
 */

export interface TrendLogRow {
  loggedAt: Date;
  kcal: number;
  proteinG: number;
  carbG: number;
  fatG: number;
}
export interface TrendWaterRow {
  loggedAt: Date;
  amountMl: number;
}
export interface TrendExerciseRow {
  performedAt: Date;
}

export interface DietTrendDay {
  date: string; // YYYY-MM-DD (local)
  kcal: number;
  proteinG: number;
  carbG: number;
  fatG: number;
  waterMl: number;
  workout: boolean;
}

export interface DietTrend {
  windowDays: number;
  days: DietTrendDay[]; // only days with ≥1 food log
  daysLogged: number;
  avgKcal: number;
  avgProteinG: number;
  avgWaterMl: number;
  workoutDays: number;
  /** Comparing the first vs second half of the logged days' avg kcal-adherence proxy (protein). */
  direction: 'improving' | 'slipping' | 'steady' | 'insufficient';
}

const round = (x: number) => Math.round(x);

export function aggregateDietTrend(
  windowDays: number,
  offsetMin: number,
  food: TrendLogRow[],
  water: TrendWaterRow[],
  exercise: TrendExerciseRow[],
): DietTrend {
  const byDay = new Map<string, DietTrendDay>();
  const day = (d: Date) => {
    const k = localDayKey(offsetMin, d.getTime());
    if (!byDay.has(k)) byDay.set(k, { date: k, kcal: 0, proteinG: 0, carbG: 0, fatG: 0, waterMl: 0, workout: false });
    return byDay.get(k)!;
  };
  const foodDays = new Set<string>();
  for (const f of food) {
    const d = day(f.loggedAt);
    d.kcal += f.kcal;
    d.proteinG += f.proteinG;
    d.carbG += f.carbG;
    d.fatG += f.fatG;
    foodDays.add(d.date);
  }
  for (const w of water) day(w.loggedAt).waterMl += w.amountMl;
  const workoutDayKeys = new Set<string>();
  for (const e of exercise) {
    const k = localDayKey(offsetMin, e.performedAt.getTime());
    workoutDayKeys.add(k);
    if (byDay.has(k)) byDay.get(k)!.workout = true;
  }

  // Only days that actually had food logged count as "logged days".
  const days = [...byDay.values()].filter((d) => foodDays.has(d.date)).sort((a, b) => a.date.localeCompare(b.date));
  const daysLogged = days.length;
  const sum = (sel: (d: DietTrendDay) => number) => days.reduce((s, d) => s + sel(d), 0);
  const avgKcal = daysLogged ? round(sum((d) => d.kcal) / daysLogged) : 0;
  const avgProteinG = daysLogged ? round(sum((d) => d.proteinG) / daysLogged) : 0;
  const avgWaterMl = daysLogged ? round(sum((d) => d.waterMl) / daysLogged) : 0;

  let direction: DietTrend['direction'] = 'insufficient';
  if (daysLogged >= 4) {
    const mid = Math.floor(days.length / 2);
    const firstHalf = days.slice(0, mid);
    const secondHalf = days.slice(mid);
    const avg = (arr: DietTrendDay[]) => (arr.length ? arr.reduce((s, d) => s + d.proteinG, 0) / arr.length : 0);
    const delta = avg(secondHalf) - avg(firstHalf);
    // Protein adherence rising ⇒ improving habits; falling ⇒ slipping. 5g band = steady.
    direction = delta >= 5 ? 'improving' : delta <= -5 ? 'slipping' : 'steady';
  }

  return {
    windowDays,
    days,
    daysLogged,
    avgKcal,
    avgProteinG,
    avgWaterMl,
    workoutDays: workoutDayKeys.size,
    direction,
  };
}

/** Fetch + aggregate the diet/activity trend for the last `days` days. */
export async function dietTrend(userId: string, days: number, offsetMin = 0, now: Date = new Date()): Promise<DietTrend> {
  const since = new Date(now.getTime() - days * 86_400_000);
  const [food, water, exercise] = await Promise.all([
    prisma.foodLog.findMany({ where: { userId, loggedAt: { gte: since } }, select: { loggedAt: true, kcal: true, proteinG: true, carbG: true, fatG: true } }),
    prisma.waterLog.findMany({ where: { userId, loggedAt: { gte: since } }, select: { loggedAt: true, amountMl: true } }),
    prisma.exerciseLog.findMany({ where: { userId, performedAt: { gte: since } }, select: { performedAt: true } }),
  ]);
  return aggregateDietTrend(days, offsetMin, food, water, exercise);
}
