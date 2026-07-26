/**
 * Deterministic OVERALL HEALTH RATING engine.
 *
 * Correlates the three behavioural pillars a user actually controls — Diet, Exercise and
 * Discipline — into a single explainable 0–100 score with a letter grade and one coaching
 * "biggest lever". Product weighting is fixed: Diet 60%, Exercise 25%, Discipline 15%.
 *
 * PURE: no DB, no I/O, no Date.now(), no randomness, no LLM. Every number in the output is
 * derived from the inputs by a fixed formula, so the same input always yields the same result.
 * When a pillar has no data at all it is dropped and the remaining weights are renormalised, so
 * the overall still spans a true 0–100 rather than being dragged down by "missing = zero".
 */

export interface RatingInput {
  diet?: {
    /** Logged calories as a % of target (0–100 = closer to target is better). */
    calorieAdherencePct?: number;
    /** Days protein target was hit, as a % (0–100). */
    proteinHitPct?: number;
    /** Micronutrient coverage as a % of RDA (0–100). */
    micronutrientCoveragePct?: number;
    /** Water intake as a % of goal (0–100). */
    waterPct?: number;
  };
  exercise?: {
    workoutsCompleted?: number;
    workoutsScheduled?: number;
    /** Progressive-overload direction over the period. */
    overloadTrend?: 'up' | 'flat' | 'down';
    /** Session consistency as a % (0–100). */
    consistencyPct?: number;
  };
  discipline?: {
    /** Overall plan-adherence score (0–100). */
    adherenceScore?: number;
    /** Current habit streak in days (mapped to quality against a 30-day horizon). */
    habitStreakDays?: number;
    /** How consistently the user logs, as a % (0–100). */
    loggingConsistencyPct?: number;
  };
}

export type Grade = 'A' | 'B' | 'C' | 'D' | 'F';
export type PillarKey = 'diet' | 'exercise' | 'discipline';

export interface PillarResult {
  key: PillarKey;
  label: string;
  /** Product weight of this pillar in the overall (before renormalisation). */
  weight: number;
  /** Explainable 0–100 sub-score; 0 when the pillar has no data. */
  score: number;
  /** Raw inputs echoed back for transparency (null where the user gave nothing). */
  inputs: Record<string, number | string | null>;
}

export interface RatingResult {
  overall: number; // 0–100
  grade: Grade;
  /** False for a brand-new user with nothing logged yet — clients should show an encouraging
   *  "start logging" state rather than a (shaming) 0 / F rating. */
  hasData: boolean;
  pillars: PillarResult[];
  biggestLever: { pillar: PillarKey; message: string };
}

/** Fixed product weighting. */
const PILLAR_WEIGHT: Record<PillarKey, number> = {
  diet: 0.6,
  exercise: 0.25,
  discipline: 0.15,
};

const PILLAR_LABEL: Record<PillarKey, string> = {
  diet: 'Diet',
  exercise: 'Exercise',
  discipline: 'Discipline',
};

const clamp0to100 = (n: number): number => Math.max(0, Math.min(100, n));
const round = (n: number): number => Math.round(n * 10) / 10;

/** A single explainable component of a pillar: a sub-weight and a 0–100 quality (null = absent). */
interface Component {
  weight: number;
  quality: number | null;
}

/** Weighted average of the PRESENT components, with sub-weights renormalised over what's present. */
function scoreComponents(components: Component[]): { score: number; hasData: boolean } {
  const present = components.filter((c) => c.quality != null);
  if (present.length === 0) return { score: 0, hasData: false };
  const totalWeight = present.reduce((s, c) => s + c.weight, 0);
  const weighted = present.reduce((s, c) => s + c.weight * (c.quality as number), 0);
  return { score: round(weighted / totalWeight), hasData: true };
}

/** Maps the overload trend to a 0–100 quality (more progression = better). */
function overloadQuality(trend?: 'up' | 'flat' | 'down'): number | null {
  if (trend == null) return null;
  return trend === 'up' ? 100 : trend === 'flat' ? 50 : 0;
}

interface PillarComputation {
  result: PillarResult;
  hasData: boolean;
}

function computeDiet(d: RatingInput['diet']): PillarComputation {
  const cal = d?.calorieAdherencePct;
  const protein = d?.proteinHitPct;
  const micro = d?.micronutrientCoveragePct;
  const water = d?.waterPct;

  const { score, hasData } = scoreComponents([
    { weight: 0.35, quality: cal == null ? null : clamp0to100(cal) },
    { weight: 0.3, quality: protein == null ? null : clamp0to100(protein) },
    { weight: 0.2, quality: micro == null ? null : clamp0to100(micro) },
    { weight: 0.15, quality: water == null ? null : clamp0to100(water) },
  ]);

  return {
    hasData,
    result: {
      key: 'diet',
      label: PILLAR_LABEL.diet,
      weight: PILLAR_WEIGHT.diet,
      score,
      inputs: {
        calorieAdherencePct: cal ?? null,
        proteinHitPct: protein ?? null,
        micronutrientCoveragePct: micro ?? null,
        waterPct: water ?? null,
      },
    },
  };
}

function computeExercise(e: RatingInput['exercise']): PillarComputation {
  const completed = e?.workoutsCompleted;
  const scheduled = e?.workoutsScheduled;
  const trend = e?.overloadTrend;
  const consistency = e?.consistencyPct;

  // Completion quality only when we have a positive schedule to measure against.
  const completionQuality =
    completed != null && scheduled != null && scheduled > 0
      ? clamp0to100((completed / scheduled) * 100)
      : null;

  const { score, hasData } = scoreComponents([
    { weight: 0.5, quality: completionQuality },
    { weight: 0.3, quality: consistency == null ? null : clamp0to100(consistency) },
    { weight: 0.2, quality: overloadQuality(trend) },
  ]);

  return {
    hasData,
    result: {
      key: 'exercise',
      label: PILLAR_LABEL.exercise,
      weight: PILLAR_WEIGHT.exercise,
      score,
      inputs: {
        workoutsCompleted: completed ?? null,
        workoutsScheduled: scheduled ?? null,
        overloadTrend: trend ?? null,
        consistencyPct: consistency ?? null,
      },
    },
  };
}

function computeDiscipline(d: RatingInput['discipline']): PillarComputation {
  const adherence = d?.adherenceScore;
  const streak = d?.habitStreakDays;
  const logging = d?.loggingConsistencyPct;

  // A 30-day streak counts as full quality; longer is capped at 100.
  const streakQuality = streak == null ? null : clamp0to100((streak / 30) * 100);

  const { score, hasData } = scoreComponents([
    { weight: 0.4, quality: adherence == null ? null : clamp0to100(adherence) },
    { weight: 0.3, quality: streakQuality },
    { weight: 0.3, quality: logging == null ? null : clamp0to100(logging) },
  ]);

  return {
    hasData,
    result: {
      key: 'discipline',
      label: PILLAR_LABEL.discipline,
      weight: PILLAR_WEIGHT.discipline,
      score,
      inputs: {
        adherenceScore: adherence ?? null,
        habitStreakDays: streak ?? null,
        loggingConsistencyPct: logging ?? null,
      },
    },
  };
}

function gradeFor(overall: number): Grade {
  if (overall >= 85) return 'A';
  if (overall >= 70) return 'B';
  if (overall >= 55) return 'C';
  if (overall >= 40) return 'D';
  return 'F';
}

function leverMessage(key: PillarKey, score: number): string {
  const label = PILLAR_LABEL[key];
  const weightPct = Math.round(PILLAR_WEIGHT[key] * 100);
  if (key === 'diet') {
    return `Diet carries the most weight (${weightPct}% of your rating) and is your weakest link at ${score}/100. Tightening calorie and protein consistency here moves your overall score more than anything else.`;
  }
  if (key === 'exercise') {
    return `Exercise (${weightPct}% of your rating) is holding you back at ${score}/100. Completing your scheduled workouts and pushing progressive overload is the fastest lift from here.`;
  }
  return `Discipline (${weightPct}% of your rating) is your weakest pillar at ${score}/100. Protecting your habit streak and logging every day compounds into the biggest gain now.`;
}

/**
 * Computes the overall health rating from the three pillars. Overall = 0.60·diet + 0.25·exercise
 * + 0.15·discipline, with the weights renormalised over only the pillars that have data. The
 * biggest lever is the pillar whose lift to a perfect 100 would raise the overall the most, i.e.
 * the weakest *weighted* pillar (renormalised weight × the score gap it still has).
 */
export function computeRating(input: RatingInput): RatingResult {
  const computations: PillarComputation[] = [
    computeDiet(input.diet),
    computeExercise(input.exercise),
    computeDiscipline(input.discipline),
  ];

  const withData = computations.filter((c) => c.hasData);
  const totalWeight = withData.reduce((s, c) => s + c.result.weight, 0);

  const overall =
    totalWeight > 0
      ? round(
          withData.reduce((s, c) => s + c.result.weight * c.result.score, 0) / totalWeight,
        )
      : 0;

  // Biggest lever: among pillars WITH data, the one whose renormalised weight × remaining gap
  // (100 − score) is largest — the single pillar improving which raises `overall` the most.
  let biggestLever: { pillar: PillarKey; message: string };
  if (withData.length > 0) {
    let best = withData[0]!;
    let bestGain = (best.result.weight / totalWeight) * (100 - best.result.score);
    for (const c of withData.slice(1)) {
      const gain = (c.result.weight / totalWeight) * (100 - c.result.score);
      if (gain > bestGain) {
        best = c;
        bestGain = gain;
      }
    }
    biggestLever = {
      pillar: best.result.key,
      message: leverMessage(best.result.key, best.result.score),
    };
  } else {
    biggestLever = {
      pillar: 'diet',
      message:
        'No pillar data yet. Start by logging your meals — Diet is 60% of your health rating and the best place to begin.',
    };
  }

  return {
    overall,
    grade: gradeFor(overall),
    hasData: withData.length > 0,
    pillars: computations.map((c) => c.result),
    biggestLever,
  };
}
