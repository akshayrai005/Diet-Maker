/**
 * Deterministic daily DISCIPLINE / adherence score. Turns a day's already-collected numbers
 * (food logged vs target, water, protein, workout, wellness, sleep, check-in) into a single
 * 0-100 "how well did I stick to the plan today" score - with an explainable point breakdown
 * per component and 1-2 encouraging next steps for the biggest misses.
 *
 * Design rules:
 *  - Pure & deterministic: no DB, no I/O, no Date.now, no random, no LLM.
 *  - NEVER penalise missing data. A component with no data is EXCLUDED from the denominator
 *    (renormalised over only what we know) - it is not scored as a zero.
 *  - Food-logged-vs-target carries the largest weight.
 *  - Workout only counts when it was actually scheduled; otherwise it's excluded entirely.
 *  - topActions surfaces the 1-2 biggest gaps as positive, actionable nudges.
 */

export interface AdherenceInput {
  kcalConsumed?: number;
  kcalTarget?: number;
  waterMl?: number;
  waterTargetMl?: number;
  proteinG?: number;
  proteinTargetG?: number;
  workoutScheduled?: boolean;
  workoutDone?: boolean;
  wellnessDone?: boolean;
  sleepHours?: number;
  checkedInToday?: boolean;
}

export interface AdherenceComponent {
  key: string;
  label: string;
  /** Points earned for this component (0..maxPoints). */
  points: number;
  /** The component's weight - its maximum possible points. */
  maxPoints: number;
  /** Plain-language explanation of how the points were earned. */
  detail: string;
}

export interface AdherenceResult {
  /** Overall 0-100, normalised over only the components that had data. */
  score: number;
  components: AdherenceComponent[];
  /** 1-2 biggest misses phrased as encouraging next steps. */
  topActions: string[];
}

/** Relative weights. Food is the heaviest signal of daily discipline. */
const WEIGHTS = {
  food: 35,
  water: 20,
  protein: 15,
  workout: 15,
  wellness: 8,
  sleep: 12,
  checkin: 5,
} as const;

const clamp01 = (n: number) => Math.max(0, Math.min(1, n));
const round = (n: number, dp = 0) => {
  const f = 10 ** dp;
  return Math.round(n * f) / f;
};
const pct = (n: number) => Math.round(n * 100);
const isNum = (n: unknown): n is number => typeof n === 'number' && isFinite(n);

/**
 * Fraction of credit for hitting a target where BOTH under- and over-shooting are imperfect,
 * but under-logging is the common failure. Full credit within a tolerance band around the
 * target, tapering linearly to zero as intake drifts far from it. Returns 0..1.
 */
function targetFraction(actual: number, target: number): number {
  if (target <= 0) return 0;
  const ratio = actual / target;
  // Ideal band: 90-110% of target = full credit.
  if (ratio >= 0.9 && ratio <= 1.1) return 1;
  if (ratio < 0.9) {
    // Under target: credit scales from 0 (nothing logged) up to 1 at 90%.
    return clamp01(ratio / 0.9);
  }
  // Over target: taper from 1 at 110% down to ~0.4 at 150%+ (over-eating still logged effort).
  const over = ratio - 1.1;
  return clamp01(1 - over / 0.4) * 0.6 + 0.4 * clamp01(1 - over / 1.5);
}

/**
 * Sleep credit: 7-9h is ideal (full credit). Outside that, partial credit that tapers with
 * distance from the nearest edge of the ideal band, floored at 0.
 */
function sleepFraction(hours: number): number {
  if (hours >= 7 && hours <= 9) return 1;
  const dist = hours < 7 ? 7 - hours : hours - 9;
  // Lose ~0.33 credit per hour outside the band.
  return clamp01(1 - dist / 3);
}

export function computeAdherence(input: AdherenceInput): AdherenceResult {
  const components: AdherenceComponent[] = [];

  // --- Food logged vs calorie target (largest weight) ---
  if (isNum(input.kcalConsumed) && isNum(input.kcalTarget) && input.kcalTarget > 0) {
    const frac = targetFraction(input.kcalConsumed, input.kcalTarget);
    components.push({
      key: 'food',
      label: 'Food logged vs target',
      points: round(frac * WEIGHTS.food, 1),
      maxPoints: WEIGHTS.food,
      detail: `You logged ${Math.round(input.kcalConsumed)} of ${Math.round(input.kcalTarget)} kcal (${pct(input.kcalConsumed / input.kcalTarget)}% of target).`,
    });
  }

  // --- Water vs target ---
  if (isNum(input.waterMl) && isNum(input.waterTargetMl) && input.waterTargetMl > 0) {
    // Water: under-drinking is the only failure; being over target is fine (cap at full).
    const frac = clamp01(input.waterMl / input.waterTargetMl);
    components.push({
      key: 'water',
      label: 'Hydration',
      points: round(frac * WEIGHTS.water, 1),
      maxPoints: WEIGHTS.water,
      detail: `You drank ${Math.round(input.waterMl)} of ${Math.round(input.waterTargetMl)} ml (${pct(input.waterMl / input.waterTargetMl)}% of goal).`,
    });
  }

  // --- Protein vs target ---
  if (isNum(input.proteinG) && isNum(input.proteinTargetG) && input.proteinTargetG > 0) {
    // Protein: hitting or exceeding the target is a win; only under-shooting loses credit.
    const frac = clamp01(input.proteinG / input.proteinTargetG);
    components.push({
      key: 'protein',
      label: 'Protein intake',
      points: round(frac * WEIGHTS.protein, 1),
      maxPoints: WEIGHTS.protein,
      detail: `You hit ${Math.round(input.proteinG)} of ${Math.round(input.proteinTargetG)} g protein (${pct(input.proteinG / input.proteinTargetG)}% of target).`,
    });
  }

  // --- Workout (ONLY counts if scheduled; otherwise excluded from the denominator) ---
  if (input.workoutScheduled === true) {
    const done = input.workoutDone === true;
    components.push({
      key: 'workout',
      label: 'Workout',
      points: done ? WEIGHTS.workout : 0,
      maxPoints: WEIGHTS.workout,
      detail: done ? 'You completed your scheduled workout.' : 'A workout was scheduled but not completed yet.',
    });
  }

  // --- Wellness (breathing / mindfulness / stretch) ---
  if (typeof input.wellnessDone === 'boolean') {
    const done = input.wellnessDone;
    components.push({
      key: 'wellness',
      label: 'Wellness activity',
      points: done ? WEIGHTS.wellness : 0,
      maxPoints: WEIGHTS.wellness,
      detail: done ? 'You did your wellness activity today.' : 'No wellness activity logged today.',
    });
  }

  // --- Sleep in range ---
  if (isNum(input.sleepHours)) {
    const frac = sleepFraction(input.sleepHours);
    components.push({
      key: 'sleep',
      label: 'Sleep',
      points: round(frac * WEIGHTS.sleep, 1),
      maxPoints: WEIGHTS.sleep,
      detail:
        frac === 1
          ? `You slept ${input.sleepHours}h - right in the ideal 7-9h range.`
          : `You slept ${input.sleepHours}h - outside the ideal 7-9h range.`,
    });
  }

  // --- Daily check-in ---
  if (typeof input.checkedInToday === 'boolean') {
    const done = input.checkedInToday;
    components.push({
      key: 'checkin',
      label: 'Daily check-in',
      points: done ? WEIGHTS.checkin : 0,
      maxPoints: WEIGHTS.checkin,
      detail: done ? 'You checked in today.' : 'You have not checked in yet today.',
    });
  }

  // --- Normalised score over ONLY the components that had data ---
  const totalMax = components.reduce((s, c) => s + c.maxPoints, 0);
  const totalPoints = components.reduce((s, c) => s + c.points, 0);
  const score = totalMax > 0 ? round((totalPoints / totalMax) * 100, 0) : 0;

  // --- topActions: 1-2 biggest gaps (by absolute points missed), as encouraging nudges ---
  const NUDGE: Record<string, string> = {
    food: 'Log the rest of your meals to close in on your calorie target - every entry counts.',
    water: 'Grab a glass of water now; a few more through the day will top up your hydration goal.',
    protein: 'Add a protein-rich snack (dal, eggs, curd or a shake) to reach your protein target.',
    workout: 'Your workout is still open - even a short session ticks it off and lifts your score.',
    wellness: 'Take 5 minutes for some breathing or a stretch to complete your wellness activity.',
    sleep: 'Aim for a 7-9h window tonight - an earlier wind-down will help you land in range.',
    checkin: 'A quick daily check-in keeps your streak alive and rounds out today.',
  };

  const topActions = components
    .map((c) => ({ c, gap: c.maxPoints - c.points }))
    .filter((x) => x.gap > 0.01)
    .sort((a, b) => b.gap - a.gap)
    .slice(0, 2)
    .map((x) => NUDGE[x.c.key] ?? `Improve your ${x.c.label.toLowerCase()} to lift today's score.`);

  return { score, components, topActions };
}
