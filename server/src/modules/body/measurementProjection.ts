/**
 * "Where could my body be in N months?" - PURE and deterministic.
 *
 * Deliberately does NOT promise a precise number like "waist 28-30in in 1 year" as fact - body-part
 * circumference change depends on genetics, training consistency and dozens of things this app can't
 * observe. Two honest paths:
 *
 *  1. TREND: when the user has logged 2+ measurements for a body part with real time between them,
 *     extrapolate their OWN observed rate of change forward. This is genuinely their data, not a
 *     population guess.
 *  2. ESTIMATE: with no logged history, fall back to a conservative, clearly-labelled heuristic
 *     (rough cm-per-kg-of-bodyweight-change ratios, commonly cited in fitness coaching - never
 *     treated as exact) and always return a RANGE, never a single number, so it doesn't read as a
 *     guarantee.
 *
 * Every result carries `source` so the UI can be honest about which path produced it.
 */

export type MeasurementKey = 'waistCm' | 'hipCm' | 'chestCm' | 'armCm' | 'thighCm' | 'neckCm';

/** Rough cm change per kg of bodyweight LOST (fat-loss dominant). Waist/hip shrink the most. */
const FAT_LOSS_CM_PER_KG: Record<MeasurementKey, number> = {
  waistCm: 1.0,
  hipCm: 0.5,
  chestCm: 0.3,
  armCm: 0.15,
  thighCm: 0.4,
  neckCm: 0.1,
};

/** Rough cm change per kg of bodyweight GAINED (muscle-gain dominant, natural/moderate surplus). */
const MUSCLE_GAIN_CM_PER_KG: Record<MeasurementKey, number> = {
  waistCm: 0.05,
  hipCm: 0.1,
  chestCm: 0.5,
  armCm: 0.4,
  thighCm: 0.3,
  neckCm: 0.05,
};

const MONTHS_TO_WEEKS = (months: number) => Math.round(months * 4.345);

export interface TrendPoint {
  /** Days before "now" this point was measured (0 = most recent). */
  daysAgo: number;
  valueCm: number;
}

export interface ProjectionMilestone {
  months: number;
  lowCm: number;
  highCm: number;
}

export interface MeasurementProjection {
  key: MeasurementKey;
  currentCm: number;
  /** 'trend' = extrapolated from the user's own logged history; 'estimate' = heuristic fallback. */
  source: 'trend' | 'estimate';
  milestones: ProjectionMilestone[];
}

/** Least-squares slope (cm per week) from {daysAgo, valueCm} points. Needs 2+ points, some spread. */
function trendSlopeCmPerWeek(points: TrendPoint[]): number | null {
  if (points.length < 2) return null;
  // Convert to weeks-since-earliest so the regression is well-conditioned.
  const maxDays = Math.max(...points.map((p) => p.daysAgo));
  if (maxDays < 10) return null; // too little real time spread to trust a slope
  const xs = points.map((p) => (maxDays - p.daysAgo) / 7); // weeks since earliest point
  const ys = points.map((p) => p.valueCm);
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
  return num / den;
}

/**
 * Projects one measurement forward to 3/6/12 months. `weeklyWeightDeltaKg` is signed (negative =
 * losing, positive = gaining) from the user's current safe target - used only for the heuristic
 * fallback when there's no real trend to extrapolate.
 */
/**
 * Natural-beginner lean-muscle-gain rate (kg/week) used ONLY for priority-muscle body parts during
 * a recomp/lean-bulk goal, so those parts don't inherit the overall fat-loss shrink rate just
 * because total bodyweight is trending down. Conservative and deliberately independent of
 * `weeklyWeightDeltaKg` - recomp means losing fat while holding or slowly building trained muscle,
 * not shrinking everywhere uniformly.
 */
const RECOMP_MUSCLE_GAIN_KG_PER_WEEK = 0.1;

export function projectMeasurement(
  key: MeasurementKey,
  currentCm: number,
  weeklyWeightDeltaKg: number,
  history: TrendPoint[] = [],
  milestoneMonths: number[] = [3, 6, 12],
  /** True when this body part is a priority-muscle target under a recomp/lean-bulk goal. */
  isRecompMuscleTarget = false,
): MeasurementProjection {
  const slope = trendSlopeCmPerWeek(history);

  if (slope != null) {
    const milestones = milestoneMonths.map((months) => {
      const weeks = MONTHS_TO_WEEKS(months);
      const projected = currentCm + slope * weeks;
      // Trend-based still gets a modest ±10% band - a straight-line extrapolation of 2-3 data
      // points is directional, not a lab measurement.
      const spread = Math.abs(projected - currentCm) * 0.1;
      return clampMilestone(months, projected - spread, projected + spread, currentCm);
    });
    return { key, currentCm, source: 'trend', milestones };
  }

  const effectiveWeeklyDeltaKg = isRecompMuscleTarget ? RECOMP_MUSCLE_GAIN_KG_PER_WEEK : weeklyWeightDeltaKg;
  const cmPerKg = effectiveWeeklyDeltaKg < 0 ? FAT_LOSS_CM_PER_KG[key] : MUSCLE_GAIN_CM_PER_KG[key];
  const milestones = milestoneMonths.map((months) => {
    const weeks = MONTHS_TO_WEEKS(months);
    const totalKgChange = effectiveWeeklyDeltaKg * weeks;
    const change = totalKgChange * cmPerKg;
    // Wider ±30% band for the heuristic path - it's a population-level ballpark, not this
    // person's measured pattern.
    const low = change * (change >= 0 ? 0.7 : 1.3);
    const high = change * (change >= 0 ? 1.3 : 0.7);
    return clampMilestone(months, currentCm + low, currentCm + high, currentCm);
  });
  return { key, currentCm, source: 'estimate', milestones };
}

/** Keeps a projected range sane: never below 60% or above 160% of the current measurement. */
function clampMilestone(months: number, low: number, high: number, currentCm: number): ProjectionMilestone {
  const floor = currentCm * 0.6;
  const ceil = currentCm * 1.6;
  const lo = Math.min(low, high);
  const hi = Math.max(low, high);
  return {
    months,
    lowCm: Math.round(Math.max(floor, Math.min(ceil, lo)) * 10) / 10,
    highCm: Math.round(Math.max(floor, Math.min(ceil, hi)) * 10) / 10,
  };
}
