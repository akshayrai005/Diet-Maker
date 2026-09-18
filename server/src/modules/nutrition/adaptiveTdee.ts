/**
 * Adaptive TDEE (MacroFactor-style, simplified): recalibrates the formula-based TDEE against the
 * user's OWN observed weight trend vs logged intake, instead of trusting a one-time Mifflin-St
 * Jeor + activity-multiplier guess forever. If someone eats 2000 kcal/day and isn't losing weight
 * as fast as the formula predicted, their real metabolism differs from the formula - this learns
 * that from their actual data rather than leaving them to quietly plateau and blame themselves.
 *
 * Deliberately conservative: requires a minimum amount of real logging before trusting it at all,
 * blends (never fully replaces) the formula TDEE so one noisy week can't swing targets wildly, and
 * clamps the observed value to a sane range of the formula TDEE so bad data (e.g. a week of mostly
 * un-logged food) can't produce a wild swing either.
 */

const KCAL_PER_KG_FAT = 7700;
const MIN_INTAKE_DAYS = 10;
const MIN_WEIGHT_SPAN_DAYS = 14;
/** Observed TDEE is clamped to within this fraction of the formula TDEE either way. */
const MAX_DEVIATION_FRACTION = 0.3;
/** How much weight the observed TDEE gets in the blend, vs the formula. */
const BLEND_WEIGHTS: Record<'low' | 'medium' | 'high', number> = { low: 0.25, medium: 0.4, high: 0.55 };

export interface DailyIntakeSample {
  date: string; // ISO day
  kcal: number;
}

export interface WeightSample {
  date: string; // ISO day
  weightKg: number;
}

export interface AdaptiveTdeeResult {
  available: boolean;
  reason?: string;
  observedTdee?: number;
  /** Blended with the formula TDEE - this is what should actually be used downstream. */
  adjustedTdee?: number;
  confidence?: 'low' | 'medium' | 'high';
  deviationPct?: number; // signed: positive = real metabolism runs higher than the formula guessed
  message?: string;
}

/** Least-squares slope (kg per day) from {daysAgo, weightKg} points. PURE. */
export function weightSlopePerDay(samples: WeightSample[], now: Date): number | null {
  if (samples.length < 2) return null;
  const points = samples.map((s) => ({
    t: (now.getTime() - new Date(`${s.date}T12:00:00Z`).getTime()) / 86_400_000, // days ago
    w: s.weightKg,
  }));
  const maxT = Math.max(...points.map((p) => p.t));
  if (maxT < MIN_WEIGHT_SPAN_DAYS - 1) return null; // too little real time spread to trust a slope
  const xs = points.map((p) => maxT - p.t); // days since earliest point (increasing)
  const ys = points.map((p) => p.w);
  const n = xs.length;
  const meanX = xs.reduce((a, b) => a + b, 0) / n;
  const meanY = ys.reduce((a, b) => a + b, 0) / n;
  let num = 0;
  let den = 0;
  for (let i = 0; i < n; i++) {
    num += (xs[i]! - meanX) * (ys[i]! - meanY);
    den += (xs[i]! - meanX) ** 2;
  }
  if (den === 0) return null;
  return num / den; // kg per day
}

export function computeAdaptiveTdee(
  formulaTdee: number,
  dailyIntake: DailyIntakeSample[],
  weightHistory: WeightSample[],
  now: Date = new Date(),
): AdaptiveTdeeResult {
  if (!isFinite(formulaTdee) || formulaTdee <= 0) {
    return { available: false, reason: 'invalid_formula_tdee' };
  }

  if (dailyIntake.length < MIN_INTAKE_DAYS) {
    return {
      available: false,
      reason: 'not_enough_intake_logs',
      message: `Log your food for at least ${MIN_INTAKE_DAYS} days to unlock a personalised calorie recalibration based on your own results.`,
    };
  }

  const slope = weightSlopePerDay(weightHistory, now);
  if (slope == null) {
    return {
      available: false,
      reason: 'not_enough_weight_logs',
      message: `Log your weight over at least ${MIN_WEIGHT_SPAN_DAYS} days (a few times a week is enough) to unlock a personalised calorie recalibration.`,
    };
  }

  const avgIntake = dailyIntake.reduce((s, d) => s + d.kcal, 0) / dailyIntake.length;
  // Energy balance: ΔWeight/day = (Intake − TDEE) / KCAL_PER_KG_FAT  =>  TDEE = Intake − ΔWeight/day × KCAL_PER_KG_FAT
  const rawObservedTdee = avgIntake - slope * KCAL_PER_KG_FAT;

  if (!isFinite(rawObservedTdee) || rawObservedTdee <= 0) {
    return { available: false, reason: 'implausible_observed_tdee' };
  }

  // Clamp to a sane range of the formula TDEE - protects against bad/sparse data producing an
  // absurd number (e.g. a week of mostly un-logged food skewing average intake very low).
  const minTdee = formulaTdee * (1 - MAX_DEVIATION_FRACTION);
  const maxTdee = formulaTdee * (1 + MAX_DEVIATION_FRACTION);
  const observedTdee = Math.round(Math.max(minTdee, Math.min(maxTdee, rawObservedTdee)));

  const confidence: 'low' | 'medium' | 'high' =
    dailyIntake.length >= 21 && weightHistory.length >= 10 ? 'high' : dailyIntake.length >= 14 ? 'medium' : 'low';

  const blendWeight = BLEND_WEIGHTS[confidence];
  const adjustedTdee = Math.round(formulaTdee * (1 - blendWeight) + observedTdee * blendWeight);
  const deviationPct = Math.round(((observedTdee - formulaTdee) / formulaTdee) * 1000) / 10;

  const direction = deviationPct > 2 ? 'higher' : deviationPct < -2 ? 'lower' : 'about the same as';
  const message =
    Math.abs(deviationPct) > 2
      ? `Based on your own logged weight and food over the last ${dailyIntake.length} days, your real metabolism looks ${Math.abs(deviationPct)}% ${direction} the formula estimate - your calorie target has been recalibrated to match.`
      : `Your logged results match the formula estimate closely - no recalibration needed.`;

  return { available: true, observedTdee, adjustedTdee, confidence, deviationPct, message };
}
