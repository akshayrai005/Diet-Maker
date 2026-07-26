import { MacroTargets } from './types';
import { round } from './anthropometry';

export interface MacroInput {
  /** Final daily calorie target (already safety-adjusted by guardrails). */
  dailyKcal: number;
  /** Weight used for fat floor (usually target weight). */
  targetWeightKg: number;
  /** Weight used for water target (usually current weight). */
  currentWeightKg: number;
  /**
   * Weight (kg) protein is scaled against. Should be the CURRENT weight (or an
   * obesity-adjusted body weight) so protein preserves lean mass during a deficit -
   * scaling to a lower target weight under-doses protein exactly when muscle is at
   * risk. Falls back to targetWeightKg when omitted (legacy behaviour).
   */
  proteinRefWeightKg?: number;
  /** Protein g/kg (1.6-2.2 typical; guardrails may lower for CKD). */
  proteinPerKg?: number;
  /** Minimum fat g/kg (default 0.6). Fat is also floored at 20% of kcal. */
  fatPerKgMin?: number;
  /** Water ml/kg (30-35 typical). */
  waterPerKg?: number;
}

const KCAL_PER_G_PROTEIN = 4;
const KCAL_PER_G_CARB = 4;
const KCAL_PER_G_FAT = 9;

/**
 * Splits a calorie target into macros.
 * - protein: proteinPerKg × targetWeight
 * - fat: max(fatPerKgMin × targetWeight, 20% of kcal ÷ 9)
 * - carbs: remaining kcal (never negative)
 * - fiber: 14 g per 1000 kcal
 * - water: waterPerKg × currentWeight
 */
export function computeMacros(input: MacroInput): MacroTargets {
  const {
    dailyKcal,
    targetWeightKg,
    currentWeightKg,
    proteinRefWeightKg = targetWeightKg,
    proteinPerKg = 1.8,
    fatPerKgMin = 0.6,
    waterPerKg = 33,
  } = input;

  if (dailyKcal <= 0) throw new RangeError('dailyKcal must be > 0');

  // Minimum carbohydrate to protect against an accidental zero/keto plan. The brain needs
  // ~130 g/day; we reserve a conservative 60 g floor and trim FAT (the flex macro, down to its
  // weight-based floor) to make room when protein + fat would otherwise eat the whole budget.
  const MIN_CARB_G = 60;
  // Protein scales to the reference weight (current / obesity-adjusted), NOT the target - this
  // preserves lean mass during a calorie deficit. Fat floor still tracks target weight.
  const proteinG = round(proteinPerKg * proteinRefWeightKg, 0);
  const fatFloorG = round(fatPerKgMin * targetWeightKg, 0);
  let fatG = round(Math.max(fatPerKgMin * targetWeightKg, (0.2 * dailyKcal) / KCAL_PER_G_FAT), 0);

  const roomForCarb = () => dailyKcal - proteinG * KCAL_PER_G_PROTEIN - fatG * KCAL_PER_G_FAT;
  if (roomForCarb() < MIN_CARB_G * KCAL_PER_G_CARB) {
    const deficitKcal = MIN_CARB_G * KCAL_PER_G_CARB - roomForCarb();
    const trimG = Math.min(fatG - fatFloorG, Math.ceil(deficitKcal / KCAL_PER_G_FAT));
    if (trimG > 0) fatG -= trimG;
  }

  const remainingKcal = dailyKcal - proteinG * KCAL_PER_G_PROTEIN - fatG * KCAL_PER_G_FAT;
  const carbG = round(Math.max(0, remainingKcal / KCAL_PER_G_CARB), 0);

  const fiberG = round(14 * (dailyKcal / 1000), 0);
  const waterMl = round(waterPerKg * currentWeightKg, 0);

  return { proteinG, fatG, carbG, fiberG, waterMl };
}
