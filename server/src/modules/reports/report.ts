import { round } from '../../calc/anthropometry';

export interface ReportDay {
  date: string; // YYYY-MM-DD
  kcal: number;
  proteinG: number;
}

/** A single itemised food-log entry for the detailed table. */
export interface FoodEntry {
  date: string; // YYYY-MM-DD
  mealSlot: string;
  name: string;
  grams: number;
  kcal: number;
  proteinG: number;
}

/** Water intake for a single day. */
export interface WaterDay {
  date: string;
  ml: number;
}

/** Lifetime totals so the report doubles as an all-time record. */
export interface AllTimeStats {
  daysLogged: number;
  totalKcal: number;
  avgDailyKcal: number;
  totalWaterMl: number;
}

/** Data-driven next-month weight prediction from actual energy balance. */
export interface Prediction {
  avgDailyIntake: number;
  maintenanceKcal: number; // TDEE - what the body burns/day (its "max" to hold weight)
  dailyBalanceKcal: number; // intake − maintenance (negative = deficit)
  projectedMonthlyDeltaKg: number;
  projectedWeightKg: number | null;
  blocked: boolean;
  note: string;
}

/** ~7700 kcal ≈ 1 kg of body weight. */
const KCAL_PER_KG = 7700;

/**
 * Predicts next month's weight from the user's ACTUAL logged intake vs the calories their body
 * burns (TDEE, which already includes their usual activity level). Honours medical guardrails
 * (no predicted loss when weight loss is blocked for disease/pregnancy/underweight) and reflects
 * fasting automatically (fasting days lower the logged intake). Capped to a sane ±4 kg/month so
 * sparse data can't produce absurd numbers. Pure & testable.
 */
export function predictNextMonth(input: {
  avgDailyIntake: number | null;
  maintenanceKcal: number | null;
  currentWeightKg: number | null;
  weightLossBlocked: boolean;
}): Prediction | null {
  const { avgDailyIntake, maintenanceKcal, currentWeightKg } = input;
  if (avgDailyIntake == null || !maintenanceKcal) return null;
  const rawBalance = Math.round(avgDailyIntake - maintenanceKcal);
  const balance = input.weightLossBlocked ? Math.max(0, rawBalance) : rawBalance;
  let monthlyDelta = Math.round(((balance * 30) / KCAL_PER_KG) * 10) / 10;
  monthlyDelta = Math.max(-4, Math.min(4, monthlyDelta));
  const projectedWeightKg =
    currentWeightKg != null ? Math.round((currentWeightKg + monthlyDelta) * 10) / 10 : null;
  const note = input.weightLossBlocked
    ? 'Weight loss is paused for medical safety, so this assumes you hold steady.'
    : balance < 0
      ? 'Based on your logged intake staying below what your body burns.'
      : balance > 0
        ? 'Your logged intake is currently at/above maintenance - a small gain is likely.'
        : 'Your intake is right at maintenance - weight is holding steady.';
  return {
    avgDailyIntake: Math.round(avgDailyIntake),
    maintenanceKcal,
    dailyBalanceKcal: balance,
    projectedMonthlyDeltaKg: monthlyDelta,
    projectedWeightKg,
    blocked: input.weightLossBlocked,
    note,
  };
}

export interface WeeklyReport {
  name: string;
  generatedAt: string;
  targets: { dailyKcal: number; proteinG: number; waterMl: number } | null;
  bmi: number | null;
  latestWeightKg: number | null;
  weightDeltaKg: number | null;
  days: ReportDay[];
  entries: FoodEntry[];
  waterByDay: WaterDay[];
  allTime: AllTimeStats | null;
  avgKcal: number | null;
  adherencePct: number | null;
  /** Sum of logged calories over the week. */
  totalIntakeKcal: number;
  /** Daily target × logged days. */
  totalTargetKcal: number | null;
  /** TDEE - the body's daily "max" to hold weight. */
  maintenanceKcal: number | null;
  prediction: Prediction | null;
  disclaimer: string;
}

export interface BuildReportInput {
  name: string;
  generatedAt: string; // ISO - passed in so the builder stays pure/deterministic
  targets: { dailyKcal: number; proteinG: number; waterMl: number } | null;
  bmi: number | null;
  latestWeightKg: number | null;
  weightDeltaKg: number | null;
  days: ReportDay[];
  entries?: FoodEntry[];
  waterByDay?: WaterDay[];
  allTime?: AllTimeStats | null;
  maintenanceKcal?: number | null; // TDEE
  currentWeightKg?: number | null;
  weightLossBlocked?: boolean;
}

const DISCLAIMER =
  'Educational guidance, not medical advice. Share with your clinician; do not use for diagnosis.';

export function buildWeeklyReport(input: BuildReportInput): WeeklyReport {
  const avgKcal = input.days.length
    ? round(input.days.reduce((s, d) => s + d.kcal, 0) / input.days.length, 0)
    : null;
  const adherencePct =
    input.targets && avgKcal !== null && input.targets.dailyKcal > 0
      ? round((avgKcal / input.targets.dailyKcal) * 100, 0)
      : null;

  const totalIntakeKcal = round(input.days.reduce((s, d) => s + d.kcal, 0), 0);
  const totalTargetKcal = input.targets ? round(input.targets.dailyKcal * input.days.length, 0) : null;
  const prediction = predictNextMonth({
    avgDailyIntake: avgKcal,
    maintenanceKcal: input.maintenanceKcal ?? null,
    currentWeightKg: input.currentWeightKg ?? input.latestWeightKg ?? null,
    weightLossBlocked: input.weightLossBlocked ?? false,
  });

  return {
    name: input.name,
    generatedAt: input.generatedAt,
    targets: input.targets,
    bmi: input.bmi,
    latestWeightKg: input.latestWeightKg,
    weightDeltaKg: input.weightDeltaKg,
    days: input.days,
    entries: input.entries ?? [],
    waterByDay: input.waterByDay ?? [],
    allTime: input.allTime ?? null,
    avgKcal,
    adherencePct,
    totalIntakeKcal,
    totalTargetKcal,
    maintenanceKcal: input.maintenanceKcal ?? null,
    prediction,
    disclaimer: DISCLAIMER,
  };
}

const csvCell = (v: string | number | null): string => {
  const s = v === null ? '' : String(v);
  return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
};

/** Renders the report as CSV (opens directly in Excel/Sheets). */
export function reportToCsv(r: WeeklyReport): string {
  const lines: string[] = [];
  lines.push(csvCell('NutriAI Weekly Report'));
  lines.push([csvCell('Name'), csvCell(r.name)].join(','));
  lines.push([csvCell('Generated'), csvCell(r.generatedAt)].join(','));
  lines.push('');
  lines.push([csvCell('Metric'), csvCell('Value')].join(','));
  lines.push([csvCell('Daily calorie target'), csvCell(r.targets?.dailyKcal ?? '')].join(','));
  lines.push([csvCell('Daily protein target (g)'), csvCell(r.targets?.proteinG ?? '')].join(','));
  lines.push([csvCell('BMI'), csvCell(r.bmi)].join(','));
  lines.push([csvCell('Latest weight (kg)'), csvCell(r.latestWeightKg)].join(','));
  lines.push([csvCell('Weight change (kg)'), csvCell(r.weightDeltaKg)].join(','));
  lines.push([csvCell('Avg calories logged'), csvCell(r.avgKcal)].join(','));
  lines.push([csvCell('Adherence (%)'), csvCell(r.adherencePct)].join(','));
  lines.push('');
  lines.push([csvCell('Date'), csvCell('Calories'), csvCell('Protein (g)')].join(','));
  for (const d of r.days) {
    lines.push([csvCell(d.date), csvCell(d.kcal), csvCell(d.proteinG)].join(','));
  }
  // Itemised food log.
  if (r.entries.length) {
    lines.push('');
    lines.push(csvCell('What you ate'));
    lines.push([csvCell('Date'), csvCell('Meal'), csvCell('Item'), csvCell('Qty (g)'), csvCell('Calories'), csvCell('Protein (g)')].join(','));
    for (const e of r.entries) {
      lines.push([csvCell(e.date), csvCell(e.mealSlot), csvCell(e.name), csvCell(e.grams), csvCell(e.kcal), csvCell(e.proteinG)].join(','));
    }
  }
  // Water log.
  if (r.waterByDay.length) {
    lines.push('');
    lines.push(csvCell('Water intake'));
    lines.push([csvCell('Date'), csvCell('Water (ml)')].join(','));
    for (const w of r.waterByDay) lines.push([csvCell(w.date), csvCell(w.ml)].join(','));
  }
  if (r.allTime) {
    lines.push('');
    lines.push(csvCell('All-time record'));
    lines.push([csvCell('Days logged'), csvCell(r.allTime.daysLogged)].join(','));
    lines.push([csvCell('Total calories consumed'), csvCell(r.allTime.totalKcal)].join(','));
    lines.push([csvCell('Average daily calories'), csvCell(r.allTime.avgDailyKcal)].join(','));
    lines.push([csvCell('Total water (ml)'), csvCell(r.allTime.totalWaterMl)].join(','));
  }
  lines.push('');
  lines.push(csvCell(r.disclaimer));
  return lines.join('\n');
}
