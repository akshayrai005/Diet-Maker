/**
 * New-to-dieting calorie ease-in - PURE and deterministic.
 *
 * The exact same gap as the workout ramp (see exercise/rampUp.ts): someone brand new to tracking
 * gets thrown straight into the FULL computed deficit/surplus from day one, with no on-ramp to
 * build the habit. This blends the target toward maintenance (TDEE) for the first 2 weeks, then
 * hands off to the full, already-safety-checked target.
 *
 * Safety note: the ramped target always sits between TDEE and the original safe dailyKcal
 * (inclusive) - since the original dailyKcal already passed every floor/guardrail check in
 * computeCalcResult, any point between it and maintenance is AT LEAST as safe. This never makes
 * the target more restrictive than what was already computed and cleared.
 */

export interface DietRampResult {
  /** 1-2: which week of the ease-in this is. */
  week: number;
  /** 0..1 - how much of the full deficit/surplus (distance from TDEE) is applied. */
  factor: number;
}

/** Whether the ease-in applies at all: unknown/never-reported gym experience, or under a month. */
export function isNewToDieting(gymMembershipMonths: number | null | undefined): boolean {
  return gymMembershipMonths == null || gymMembershipMonths <= 1;
}

/**
 * Ramp state for a given "days since starting" figure, or `null` when the ease-in doesn't apply
 * (not new, or past the 2-week window - full target applies either way).
 */
export function dietRampFor(gymMembershipMonths: number | null | undefined, daysSinceStart: number): DietRampResult | null {
  if (!isNewToDieting(gymMembershipMonths)) return null;
  if (daysSinceStart < 0 || daysSinceStart >= 14) return null;
  const week = daysSinceStart < 7 ? 1 : 2;
  const factor = week === 1 ? 0.5 : 0.75;
  return { week, factor };
}

export interface RampableTargets {
  dailyKcal: number;
  tdee: number;
  proteinG: number;
  carbG: number;
  fatG: number;
}

/**
 * Applies a ramp factor to a computed target: blends dailyKcal toward tdee, keeps protein fixed
 * (protecting muscle/satiety is never something to ease off), and scales carb+fat proportionally
 * so the macros still sum to the new dailyKcal.
 */
export function applyDietRamp<T extends RampableTargets>(targets: T, ramp: DietRampResult): T {
  const easedKcal = targets.tdee + (targets.dailyKcal - targets.tdee) * ramp.factor;
  const proteinKcal = targets.proteinG * 4;
  const flexKcalOriginal = Math.max(1, targets.dailyKcal - proteinKcal);
  const flexKcalEased = Math.max(0, easedKcal - proteinKcal);
  const flexScale = flexKcalEased / flexKcalOriginal;
  return {
    ...targets,
    dailyKcal: Math.round(easedKcal),
    carbG: Math.round(targets.carbG * flexScale),
    fatG: Math.round(targets.fatG * flexScale),
  };
}
