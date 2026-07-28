import { round } from '../../calc/anthropometry';

export interface NutritionTotals {
  kcal: number;
  proteinG: number;
  carbG: number;
  fatG: number;
  fiberG: number;
  sugarG: number;
  sodiumMg: number;
}

export interface LoggedNutrition {
  kcal: number;
  proteinG: number;
  carbG: number;
  fatG: number;
  fiberG: number;
  sugarG?: number;
  sodiumMg: number;
}

export const EMPTY_TOTALS: NutritionTotals = {
  kcal: 0,
  proteinG: 0,
  carbG: 0,
  fatG: 0,
  fiberG: 0,
  sugarG: 0,
  sodiumMg: 0,
};

/** UTC day key 'YYYY-MM-DD'. */
export function dayKey(date: Date): string {
  return date.toISOString().slice(0, 10);
}

export function sumTotals(entries: LoggedNutrition[]): NutritionTotals {
  const t = entries.reduce<NutritionTotals>(
    (acc, e) => ({
      kcal: acc.kcal + e.kcal,
      proteinG: acc.proteinG + e.proteinG,
      carbG: acc.carbG + e.carbG,
      fatG: acc.fatG + e.fatG,
      fiberG: acc.fiberG + e.fiberG,
      sugarG: acc.sugarG + (e.sugarG ?? 0),
      sodiumMg: acc.sodiumMg + e.sodiumMg,
    }),
    { ...EMPTY_TOTALS },
  );
  return {
    kcal: round(t.kcal, 0),
    proteinG: round(t.proteinG, 1),
    carbG: round(t.carbG, 1),
    fatG: round(t.fatG, 1),
    fiberG: round(t.fiberG, 1),
    sugarG: round(t.sugarG, 1),
    sodiumMg: round(t.sodiumMg, 0),
  };
}

/**
 * Consecutive-day logging streak ending today (or yesterday - a streak stays alive until a
 * full day is missed). `dayKeys` is any collection of 'YYYY-MM-DD' with at least one log.
 */
export function computeStreak(dayKeys: Iterable<string>, todayKey: string): number {
  const set = new Set(dayKeys);
  if (set.size === 0) return 0;

  const start = new Date(`${todayKey}T00:00:00Z`);
  // If nothing logged today, the streak can still be counted from yesterday.
  let cursor = start;
  if (!set.has(todayKey)) cursor = new Date(start.getTime() - 86_400_000);

  let streak = 0;
  while (set.has(dayKey(cursor))) {
    streak += 1;
    cursor = new Date(cursor.getTime() - 86_400_000);
  }
  return streak;
}

export interface WeightPoint {
  date: string; // ISO
  weightKg: number;
}

export function weightTrend(points: WeightPoint[]) {
  if (points.length === 0) return { latestKg: null, firstKg: null, deltaKg: null, points: [] };
  const sorted = [...points].sort((a, b) => a.date.localeCompare(b.date));
  const first = sorted[0]!;
  const latest = sorted[sorted.length - 1]!;
  return {
    latestKg: latest.weightKg,
    firstKg: first.weightKg,
    deltaKg: round(latest.weightKg - first.weightKg, 1),
    points: sorted,
  };
}

export interface DashboardInput {
  targets: { dailyKcal: number; proteinG: number; waterMl: number } | null;
  todayTotals: NutritionTotals;
  waterTodayMl: number;
  logDayKeys: string[];
  todayKey: string;
  weightPoints: WeightPoint[];
  bmi?: number | null;
  projection?: { label: string; weeks: number; weightKg: number; bmi: number }[];
  /** Calories burned today from logged exercise + wellness sessions. */
  burnedTodayKcal?: number;
  micronutrients?: MicronutrientBlock;
  /** True when today is the user's weekly fasting day (target already reduced). */
  fastingToday?: boolean;
}

export interface MicronutrientTargetView {
  key: string;
  label: string;
  rda: number;
  intake?: number | null;
  pct?: number | null;
  low?: boolean;
}

export interface MicronutrientBlock {
  /** True once we can estimate intake from logged foods; false shows targets only. */
  available: boolean;
  note: string;
  coveragePct?: number;
  targets: MicronutrientTargetView[];
  /** Actionable, food-source tips for the biggest computed gaps. */
  tips: string[];
  /** Specific diet-compatible foods per real deficiency (respects diet/allergies/budget). */
  foodFixes?: { key: string; label: string; foods: string[]; note: string }[];
}

/** Assembles the dashboard payload. Pure - the service just feeds it fetched data. */
export function buildDashboard(input: DashboardInput) {
  const { targets, todayTotals, waterTodayMl } = input;
  const pct = (have: number, target: number) =>
    target > 0 ? round((have / target) * 100, 0) : 0;

  const burnedKcal = round(input.burnedTodayKcal ?? 0, 0);

  return {
    date: input.todayKey,
    calories: {
      consumed: todayTotals.kcal,
      target: targets?.dailyKcal ?? null,
      remaining: targets ? round(targets.dailyKcal - todayTotals.kcal, 0) : null,
      percent: targets ? pct(todayTotals.kcal, targets.dailyKcal) : null,
    },
    energy: {
      // Movement connected to the calorie picture: intake − calories burned today.
      burnedKcal,
      netKcal: round(todayTotals.kcal - burnedKcal, 0),
    },
    protein: {
      consumed: todayTotals.proteinG,
      target: targets?.proteinG ?? null,
      percent: targets ? pct(todayTotals.proteinG, targets.proteinG) : null,
    },
    water: {
      consumedMl: waterTodayMl,
      targetMl: targets?.waterMl ?? null,
      percent: targets ? pct(waterTodayMl, targets.waterMl) : null,
    },
    macros: {
      carbG: todayTotals.carbG,
      fatG: todayTotals.fatG,
      fiberG: todayTotals.fiberG,
      sugarG: todayTotals.sugarG,
      sodiumMg: todayTotals.sodiumMg,
    },
    streakDays: computeStreak(input.logDayKeys, input.todayKey),
    bmi: input.bmi ?? null,
    weight: weightTrend(input.weightPoints),
    projection: input.projection ?? [],
    micronutrients: input.micronutrients ?? null,
    fastingToday: input.fastingToday ?? false,
  };
}
