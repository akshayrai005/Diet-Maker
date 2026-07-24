import type { RatingInput } from './rating';

/**
 * Pure mapping from already-fetched, per-day aggregates to the RatingInput the rating engine
 * consumes. Kept separate from the DB fetch so the (non-trivial) shaping logic is unit-testable.
 * No I/O, no Date.now, no randomness.
 */
export interface RatingAggregateInput {
  target: { dailyKcal: number; proteinG: number; waterMl: number } | null;
  /** One entry per day in the window that had ANY food log. */
  dietDays: { kcal: number; proteinG: number; waterMl: number }[];
  /** Days in the look-back window (typically 7) — the denominator for logging consistency. */
  daysInWindow: number;
  workoutsCompleted: number;
  workoutsScheduled: number;
  overloadTrend: 'up' | 'flat' | 'down';
  loggingStreakDays: number;
  /** Today's discipline/adherence score (0–100), or null if nothing was known today. */
  todayAdherence: number | null;
}

const clampPct = (n: number) => Math.max(0, Math.min(100, Math.round(n)));

/** Per-day calorie closeness: full credit within 90–110% of target, tapering to 0 far away. */
function calorieCloseness(kcal: number, targetKcal: number): number {
  if (targetKcal <= 0) return 0;
  const ratio = kcal / targetKcal;
  const dist = ratio < 0.9 ? 0.9 - ratio : ratio > 1.1 ? ratio - 1.1 : 0;
  return clampPct(100 * Math.max(0, 1 - dist / 0.9));
}

export function aggregateRatingInputs(a: RatingAggregateInput): RatingInput {
  const input: RatingInput = {};

  // ---- Diet ----
  if (a.target && a.dietDays.length > 0) {
    const t = a.target;
    const closeness = a.dietDays.map((d) => calorieCloseness(d.kcal, t.dailyKcal));
    const calorieAdherencePct = clampPct(closeness.reduce((s, x) => s + x, 0) / closeness.length);
    const proteinHits = a.dietDays.filter((d) => t.proteinG > 0 && d.proteinG >= 0.9 * t.proteinG).length;
    const proteinHitPct = clampPct((100 * proteinHits) / a.dietDays.length);
    const avgWater = a.dietDays.reduce((s, d) => s + d.waterMl, 0) / a.dietDays.length;

    input.diet = {
      calorieAdherencePct,
      proteinHitPct,
      ...(t.waterMl > 0 ? { waterPct: clampPct((100 * avgWater) / t.waterMl) } : {}),
    };
  }

  // ---- Exercise ----
  if (a.workoutsScheduled > 0 || a.workoutsCompleted > 0) {
    input.exercise = {
      workoutsCompleted: a.workoutsCompleted,
      workoutsScheduled: a.workoutsScheduled,
      overloadTrend: a.overloadTrend,
      ...(a.workoutsScheduled > 0
        ? { consistencyPct: clampPct((100 * a.workoutsCompleted) / a.workoutsScheduled) }
        : {}),
    };
  }

  // ---- Discipline ----
  input.discipline = {
    ...(a.todayAdherence != null ? { adherenceScore: clampPct(a.todayAdherence) } : {}),
    habitStreakDays: a.loggingStreakDays,
    ...(a.daysInWindow > 0
      ? { loggingConsistencyPct: clampPct((100 * a.dietDays.length) / a.daysInWindow) }
      : {}),
  };

  return input;
}
