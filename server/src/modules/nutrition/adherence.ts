/**
 * Adherence intelligence - PURE and deterministic.
 *
 * Most trackers treat a day as pass/fail against one number (calories). That misses the real
 * question: which deviations actually matter? Hitting 96% of calories but only 80% of protein
 * while weight is on-track isn't a failure to fix by cutting further - it's fine, don't
 * overcorrect. This module scores each adherence dimension independently, then reasons across
 * them instead of flattening everything into a single red/green.
 */

export interface AdherenceInputs {
  /** Average logged kcal/day over the window, or null if nothing was logged. */
  avgKcal: number | null;
  targetKcal: number;
  /** Average logged protein g/day over the window, or null if nothing was logged. */
  avgProteinG: number | null;
  targetProteinG: number;
  /** Days in the window with at least one exercise log. */
  trainingDaysLogged: number;
  /** Days in the window the plan actually called for training (not rest days). */
  trainingDaysPlanned: number;
  /** Average logged steps/day over the window, or null if no step data. */
  avgSteps: number | null;
  targetSteps: number;
  /** Days in the window with a logged weight (check-in or body metric). */
  weighInsLogged: number;
  windowDays: number;
  /** True when the weight trend over the window is moving in the goal's intended direction (or holding, for maintain). Null when there isn't enough data to judge. */
  weightOnTrack: boolean | null;
}

export type AdherenceLevel = 'none' | 'low' | 'partial' | 'good' | 'excellent';

export interface DimensionScore {
  /** 0-100+ (can exceed 100, e.g. overeating or over-stepping). Null when no data. */
  pct: number | null;
  level: AdherenceLevel;
}

export interface AdherenceResult {
  calories: DimensionScore;
  protein: DimensionScore;
  training: DimensionScore;
  steps: DimensionScore;
  weighIns: DimensionScore;
  /** Overall read, weighted toward the dimensions that most affect the actual goal (calories, protein, weight trend). */
  overall: AdherenceLevel;
  /** The single most useful thing to say - specifically avoids "you're behind, eat less" when the real story is more nuanced. */
  message: string;
  /** Set when the honest advice is "hold steady, don't overcorrect" despite an imperfect dimension. */
  holdSteady: boolean;
}

/**
 * Bands by absolute deviation from 100%, symmetric both directions - under-hitting a target and
 * significantly OVER-shooting it (e.g. eating 150% of a calorie target) are both poor adherence,
 * not just running short.
 */
function levelFor(pct: number | null): AdherenceLevel {
  if (pct === null) return 'none';
  const deviation = Math.abs(pct - 100);
  if (deviation <= 10) return 'excellent';
  if (deviation <= 25) return 'good';
  if (deviation <= 40) return 'partial';
  return 'low';
}

/** Same banding but symmetric around 100% (works fine for the 0-100 case too, kept for clarity at call sites). */
function pctScore(avg: number | null, target: number): DimensionScore {
  if (avg === null || target <= 0) return { pct: null, level: 'none' };
  const pct = Math.round((avg / target) * 100);
  return { pct, level: levelFor(pct) };
}

export function computeAdherence(input: AdherenceInputs): AdherenceResult {
  const calories = pctScore(input.avgKcal, input.targetKcal);
  const protein = pctScore(input.avgProteinG, input.targetProteinG);
  const training = pctScore(
    input.trainingDaysPlanned > 0 ? input.trainingDaysLogged : null,
    input.trainingDaysPlanned,
  );
  const steps = pctScore(input.avgSteps, input.targetSteps);
  const weighIns = pctScore(input.weighInsLogged, input.windowDays);

  // Overall = weighted average, calories/protein counting double (they're what the goal actually
  // depends on) - not just "worst dimension wins", which would let one noisy step day tank an
  // otherwise on-track week.
  const rank: Record<AdherenceLevel, number> = { none: -1, low: 0, partial: 1, good: 2, excellent: 3 };
  const weighted: [DimensionScore, number][] = [
    [calories, 2],
    [protein, 2],
    [training, 1],
    [steps, 1],
    [weighIns, 1],
  ];
  const present = weighted.filter(([d]) => d.level !== 'none');
  const overall: AdherenceLevel = present.length === 0
    ? 'none'
    : (() => {
        const totalWeight = present.reduce((s, [, w]) => s + w, 0);
        const avgRank = present.reduce((s, [d, w]) => s + rank[d.level] * w, 0) / totalWeight;
        const rounded = Math.round(avgRank);
        return (['low', 'partial', 'good', 'excellent'] as AdherenceLevel[])[rounded] ?? 'partial';
      })();

  // The core "don't overcorrect" case: calories are genuinely on target AND the weight trend
  // confirms it's working, even if protein or steps lagged - the honest advice is to hold, not
  // tighten further just because one secondary dimension was short.
  const caloriesOnTrack = calories.level === 'excellent' || calories.level === 'good';
  const holdSteady = caloriesOnTrack && input.weightOnTrack === true &&
    (protein.level === 'partial' || protein.level === 'low' || steps.level === 'partial' || steps.level === 'low');

  let message: string;
  if (calories.pct === null) {
    message = `Log your food for a few more days to unlock adherence insights.`;
  } else if (holdSteady) {
    const shortfalls: string[] = [];
    if (protein.level === 'partial' || protein.level === 'low') shortfalls.push(`protein at ${protein.pct}%`);
    if (steps.level === 'partial' || steps.level === 'low') shortfalls.push(`steps at ${steps.pct}%`);
    message = `Calories are on target (${calories.pct}%) and your weight trend confirms it's working, even with ${shortfalls.join(' and ')}. Hold steady - don't cut calories further to compensate for that.`;
  } else if (calories.level === 'low' || calories.level === 'partial') {
    const dir = (calories.pct ?? 0) < 100 ? 'under' : 'over';
    message = `You're ${dir}-logging calories at ${calories.pct}% of target over the last ${input.windowDays} days. ${dir === 'under' ? 'Under-logging (not necessarily under-eating) makes every other number here unreliable - log more consistently before reacting to anything else.' : "That's the thing most worth fixing first."}`;
  } else if (protein.level === 'low') {
    message = `Calories are on target but protein is low (${protein.pct}%) - that's the one worth prioritizing; it protects muscle during a deficit more than hitting calories to the gram does.`;
  } else if (training.level === 'low' || training.level === 'partial') {
    message = `Nutrition is on track, but only ${input.trainingDaysLogged}/${input.trainingDaysPlanned} planned training days were logged this window - training consistency is the bigger lever right now.`;
  } else {
    message = `Solid week: calories ${calories.pct}%, protein ${protein.pct}%. Keep going as-is.`;
  }

  return { calories, protein, training, steps, weighIns, overall, message, holdSteady };
}
