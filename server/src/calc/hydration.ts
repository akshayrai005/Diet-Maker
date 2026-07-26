import type { ActivityLevel } from './types';

/**
 * Deterministic daily hydration target (ml) adapting to body weight, activity and climate.
 * Base ~33 ml/kg, plus activity and hot-climate top-ups. Educational only — people on a medical
 * FLUID RESTRICTION (kidney/heart) must follow their doctor's limit, not this (guardrails flag that).
 * Pure & unit-tested.
 */

export type Climate = 'temperate' | 'hot' | 'cold';

const ACTIVITY_BONUS_ML: Record<ActivityLevel, number> = {
  sedentary: 0,
  light: 250,
  moderate: 500,
  active: 750,
  veryactive: 1000,
};

const CLIMATE_BONUS_ML: Record<Climate, number> = {
  cold: 0,
  temperate: 0,
  hot: 600,
};

/** Adaptive target, clamped to a sane 1500–5000 ml range. */
export function hydrationTargetMl(
  weightKg: number,
  activity: ActivityLevel = 'moderate',
  climate: Climate = 'temperate',
): number {
  const base = (typeof weightKg === 'number' && isFinite(weightKg) && weightKg > 0 ? weightKg : 70) * 33;
  const total = base + (ACTIVITY_BONUS_ML[activity] ?? 0) + (CLIMATE_BONUS_ML[climate] ?? 0);
  const rounded = Math.round(total / 50) * 50; // nearest 50 ml
  return Math.min(5000, Math.max(1500, rounded));
}
