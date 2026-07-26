import {
  ActivityLevel,
  Goal,
  Sex,
  anthropometrySummary,
  bmi,
  computeMacros,
  energy,
  round,
} from '../../calc';
import { applyGuardrails, Condition, Flag } from '../../guardrails';
import { hydrationTargetMl, type Climate } from '../../calc/hydration';

/** Everything the /calc endpoint needs, in one call. */
export interface CalcProfileInput {
  heightCm: number;
  currentWeightKg: number;
  targetWeightKg: number;
  ageYears: number;
  sex: Sex;
  activityLevel: ActivityLevel;
  goal: Goal;
  waistCm?: number;
  conditions?: Condition[];
  desiredWeeklyLossKg?: number;
  clinicianOverride?: boolean;
  reducedMobility?: boolean;
  /** Local climate — adapts the hydration target (hot climates need more). */
  climate?: Climate;
}

export interface CalcResult {
  bmi: number;
  bmiCategory: string;
  whtr?: number;
  bmr: number;
  tdee: number;
  idealWeightMinKg: number;
  idealWeightMaxKg: number;
  bodyFatEstimate: number;
  dailyKcal: number;
  proteinG: number;
  fatG: number;
  carbG: number;
  fiberG: number;
  waterMl: number;
  sodiumMaxMg?: number;
  safeWeeklyDeltaKg: number;
  requiresSupervision: boolean;
  weightLossBlocked: boolean;
  projection: ProjectionPoint[];
  flags: Flag[];
}

/** A free, deterministic "how you'll look" forecast — projected weight/BMI over time. */
export interface ProjectionPoint {
  label: string;
  weeks: number;
  weightKg: number;
  bmi: number;
}

const MILESTONES: { label: string; weeks: number }[] = [
  { label: 'Now', weeks: 0 },
  { label: '1 month', weeks: 4 },
  { label: '3 months', weeks: 13 },
  { label: '6 months', weeks: 26 },
  { label: '1 year', weeks: 52 },
];

/** Projects weight forward at the safe weekly rate, without overshooting the goal. */
function projectWeight(
  currentKg: number,
  targetKg: number,
  weeklyDeltaKg: number,
  weeks: number,
): number {
  const raw = currentKg + weeklyDeltaKg * weeks;
  if (weeklyDeltaKg < 0) return round(Math.max(targetKg, raw), 1); // losing
  if (weeklyDeltaKg > 0) return round(Math.min(targetKg, raw), 1); // gaining
  return round(currentKg, 1);
}

/**
 * Runs the deterministic pipeline: anthropometry -> energy -> guardrails -> macros.
 * This is the authoritative snapshot persisted as a versioned CalcResult.
 */
export function computeCalcResult(input: CalcProfileInput): CalcResult {
  const conditions = input.conditions ?? [];

  const anthro = anthropometrySummary({
    heightCm: input.heightCm,
    weightKg: input.currentWeightKg,
    ageYears: input.ageYears,
    sex: input.sex,
    waistCm: input.waistCm,
  });

  const { bmr, tdee } = energy(
    {
      heightCm: input.heightCm,
      weightKg: input.currentWeightKg,
      ageYears: input.ageYears,
      sex: input.sex,
    },
    input.activityLevel,
  );

  const guard = applyGuardrails({
    sex: input.sex,
    ageYears: input.ageYears,
    currentWeightKg: input.currentWeightKg,
    targetWeightKg: input.targetWeightKg,
    heightCm: input.heightCm,
    tdee,
    bmi: anthro.bmi,
    goal: input.goal,
    conditions,
    desiredWeeklyLossKg: input.desiredWeeklyLossKg,
    clinicianOverride: input.clinicianOverride,
    reducedMobility: input.reducedMobility,
  });

  // Scale protein to CURRENT weight (preserves lean mass in a deficit), but for obesity cap it
  // at an adjusted body weight — ideal-max + 25% of the excess — so we don't over-dose protein
  // from fat mass. At or below a healthy weight, current weight is used directly.
  const idealMaxKg = anthro.idealWeight.maxKg;
  const proteinRefWeightKg =
    input.currentWeightKg > idealMaxKg
      ? Math.round((idealMaxKg + 0.25 * (input.currentWeightKg - idealMaxKg)) * 10) / 10
      : input.currentWeightKg;

  const macros = computeMacros({
    dailyKcal: guard.dailyKcal,
    targetWeightKg: input.targetWeightKg,
    currentWeightKg: input.currentWeightKg,
    proteinRefWeightKg,
    proteinPerKg: guard.proteinPerKg,
    fatPerKgMin: guard.fatPerKgMin,
  });
  // Adaptive hydration: base ml/kg + activity + climate top-ups (overrides the flat macros default).
  const adaptiveWaterMl = hydrationTargetMl(input.currentWeightKg, input.activityLevel, input.climate);

  return {
    bmi: anthro.bmi,
    bmiCategory: anthro.category,
    whtr: anthro.whtr,
    bmr,
    tdee,
    idealWeightMinKg: anthro.idealWeight.minKg,
    idealWeightMaxKg: anthro.idealWeight.maxKg,
    bodyFatEstimate: anthro.bodyFatEstimate,
    dailyKcal: guard.dailyKcal,
    proteinG: macros.proteinG,
    fatG: macros.fatG,
    carbG: macros.carbG,
    fiberG: macros.fiberG,
    waterMl: adaptiveWaterMl,
    sodiumMaxMg: guard.sodiumMaxMg,
    safeWeeklyDeltaKg: guard.safeWeeklyDeltaKg,
    requiresSupervision: guard.requiresSupervision,
    weightLossBlocked: guard.weightLossBlocked,
    projection: MILESTONES.map((m) => {
      const w = projectWeight(
        input.currentWeightKg,
        input.targetWeightKg,
        guard.safeWeeklyDeltaKg,
        m.weeks,
      );
      return { label: m.label, weeks: m.weeks, weightKg: w, bmi: bmi(w, input.heightCm) };
    }),
    flags: guard.flags,
  };
}
