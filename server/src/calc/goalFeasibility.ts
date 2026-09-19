// Pure module - no I/O. "Is this target realistic, and how long will it take?" for body-measurement goals.
// Deterministic, educational estimates for a NATURAL (no drugs) trainee. Not medical advice or a promise.

export interface FeasibilityInput {
  sex: 'male' | 'female';
  heightCm: number;
  weightKg: number;
  targetWeightKg?: number;
  waistCm?: number;
  targetWaistCm?: number;
  chestCm?: number;
  targetChestCm?: number;
  armCm?: number;
  targetArmCm?: number;
  thighCm?: number;
  targetThighCm?: number;
  forearmCm?: number;
  targetForearmCm?: number;
  bodyFatPct?: number;
  targetBodyFatPct?: number;
  /** Months of real lifting behind the user already (a returning lifter builds faster). */
  trainingMonths?: number;
}

export type Verdict = 'realistic' | 'ambitious' | 'long_term' | 'unrealistic';

export interface FeasibilityItem {
  key: 'weight' | 'waist' | 'bodyFat' | 'chest' | 'arm' | 'thigh' | 'forearm';
  label: string;
  unit: 'kg' | 'cm' | '%';
  current: number;
  target: number;
  verdict: Verdict;
  /** Realistic time range in months (null when the target is not reachable naturally). */
  monthsMin: number | null;
  monthsMax: number | null;
  /** A target we would suggest instead when the asked one is beyond natural limits. */
  suggestedTarget?: number;
  note: string;
}

export interface FeasibilityReport {
  items: FeasibilityItem[];
  /** Months until every REALISTIC target is met (upper end). null if there is nothing to compute. */
  recommendedMonths: number | null;
  summary: string;
  disclaimer: string;
}

export const FEASIBILITY_DISCLAIMER =
  'Educational estimates for natural training with consistent food, sleep and effort. Genetics and consistency change the pace - measure every 2-4 weeks and adjust.';

const round1 = (n: number) => Math.round(n * 10) / 10;

/** Cumulative cm gained after `months` of good training, by year (diminishing returns). */
function cumulativeGain(yearlyCm: number[], months: number): number {
  let left = months;
  let total = 0;
  for (let y = 0; y < yearlyCm.length && left > 0; y++) {
    const m = Math.min(12, left);
    total += (yearlyCm[y]! * m) / 12;
    left -= m;
  }
  if (left > 0) total += (0.25 * left) / 12; // after the listed years, ~0.25 cm/yr
  return total;
}

/** Months needed to add `deltaCm`, given yearly-gain schedule; capped at 120. */
function monthsToGain(yearlyCm: number[], deltaCm: number, speedUp: number): number {
  let m = 0;
  while (m < 120 && cumulativeGain(yearlyCm, m) * speedUp < deltaCm) m++;
  return m;
}

const YEARLY = {
  arm: [4.0, 2.0, 1.0, 0.5],
  chest: [6.0, 3.0, 1.5, 1.0],
  thigh: [4.5, 3.0, 1.5, 1.0],
  forearm: [1.5, 0.8, 0.4, 0.2],
};

function verdictFromMonths(months: number): Verdict {
  if (months <= 12) return 'realistic';
  if (months <= 30) return 'ambitious';
  if (months <= 72) return 'long_term';
  return 'unrealistic';
}

export function assessGoals(input: FeasibilityInput): FeasibilityReport {
  const male = input.sex === 'male';
  const h = input.heightCm;
  const items: FeasibilityItem[] = [];
  // A returning lifter (12+ months behind them) regains size faster (muscle memory).
  const speedUp = (input.trainingMonths ?? 0) >= 12 ? 1.4 : 1;

  // ---- Size goals (natural ceilings from height; men) ----
  const sizeGoal = (
    key: 'chest' | 'arm' | 'thigh' | 'forearm',
    label: string,
    cur: number | undefined,
    tgt: number | undefined,
    ceiling: number,
  ) => {
    if (cur == null || tgt == null || tgt <= cur) return;
    const delta = tgt - cur;
    const c = male ? ceiling : ceiling * 0.9;
    if (tgt > c) {
      items.push({
        key, label, unit: 'cm', current: cur, target: tgt, verdict: 'unrealistic', monthsMin: null, monthsMax: null,
        suggestedTarget: round1(c * 0.97),
        note: `${tgt} cm is beyond what most natural lifters reach at ${Math.round(h)} cm tall (limit ≈ ${round1(c)} cm). A strong natural goal is about ${round1(c * 0.97)} cm.`,
      });
      return;
    }
    const mid = monthsToGain(YEARLY[key], delta, speedUp);
    const monthsMin = Math.max(1, Math.round(mid * 0.8));
    const monthsMax = Math.round(mid * 1.25);
    items.push({
      key, label, unit: 'cm', current: cur, target: tgt, verdict: verdictFromMonths(mid), monthsMin, monthsMax,
      note: mid <= 12 ? `Reachable within a year with steady training and enough protein.` : `Size comes slowly: gains shrink every year of training, so this takes about ${monthsMin}-${monthsMax} months.`,
    });
  };
  sizeGoal('chest', 'Chest', input.chestCm, input.targetChestCm, 0.62 * h);
  sizeGoal('arm', 'Arm', input.armCm, input.targetArmCm, 0.215 * h);
  sizeGoal('thigh', 'Thigh', input.thighCm, input.targetThighCm, 0.36 * h);
  sizeGoal('forearm', 'Forearm', input.forearmCm, input.targetForearmCm, 0.17 * h);

  // ---- Fat-loss goals ----
  // Safe loss ≈ 0.5% body weight per week; slower while also building muscle.
  const buildingMuscle = [input.targetChestCm, input.targetArmCm, input.targetThighCm, input.targetForearmCm].some((t) => t != null);
  const lossPace = (weightKg: number) => 0.005 * weightKg * (buildingMuscle ? 0.75 : 1); // kg / week

  if (input.targetWeightKg != null && input.targetWeightKg < input.weightKg) {
    const delta = input.weightKg - input.targetWeightKg;
    const weeks = delta / lossPace(input.weightKg);
    const months = weeks / 4.345;
    items.push({
      key: 'weight', label: 'Weight', unit: 'kg', current: input.weightKg, target: input.targetWeightKg,
      verdict: verdictFromMonths(months), monthsMin: Math.max(1, Math.round(months * 0.85)), monthsMax: Math.max(1, Math.round(months * 1.25)),
      note: `About ${round1(lossPace(input.weightKg))} kg per week is a safe pace${buildingMuscle ? ' while you also build muscle' : ''}. The scale can lag while your waist drops.`,
    });
  }

  if (input.waistCm != null && input.targetWaistCm != null && input.targetWaistCm < input.waistCm) {
    const delta = input.waistCm - input.targetWaistCm;
    const months = delta / 1.25; // ≈1-1.5 cm/month in a steady deficit
    items.push({
      key: 'waist', label: 'Waist', unit: 'cm', current: input.waistCm, target: input.targetWaistCm,
      verdict: verdictFromMonths(months), monthsMin: Math.max(1, Math.round(delta / 1.5)), monthsMax: Math.max(1, Math.round(delta / 1.0)),
      note: 'Waist usually drops 1-1.5 cm a month in a steady, moderate calorie deficit.',
    });
  }

  if (input.bodyFatPct != null && input.targetBodyFatPct != null && input.targetBodyFatPct < input.bodyFatPct) {
    const floor = male ? 10 : 18;
    const delta = input.bodyFatPct - input.targetBodyFatPct;
    if (input.targetBodyFatPct < floor) {
      items.push({
        key: 'bodyFat', label: 'Body fat', unit: '%', current: input.bodyFatPct, target: input.targetBodyFatPct, verdict: 'unrealistic',
        monthsMin: null, monthsMax: null, suggestedTarget: floor,
        note: `Below ${floor}% is hard to hold and can hurt hormones and energy. Aim for ${floor}-${floor + 2}% first.`,
      });
    } else {
      const months = delta / 0.8; // ≈0.6-1.0 point a month
      items.push({
        key: 'bodyFat', label: 'Body fat', unit: '%', current: input.bodyFatPct, target: input.targetBodyFatPct,
        verdict: verdictFromMonths(months), monthsMin: Math.max(1, Math.round(delta / 1.0)), monthsMax: Math.max(1, Math.round(delta / 0.6)),
        note: 'Body-fat % (tape-measure estimate) usually falls 0.6-1 point a month while you keep training.',
      });
    }
  }

  const reachable = items.filter((i) => i.monthsMax != null);
  const recommendedMonths = reachable.length ? Math.max(...reachable.map((i) => i.monthsMax!)) : null;
  const unrealistic = items.filter((i) => i.verdict === 'unrealistic');
  let summary = 'Add your targets to see how long each one takes.';
  if (items.length) {
    summary = recommendedMonths != null ? `Your realistic goals take about ${recommendedMonths} months in total.` : 'These targets are beyond natural limits.';
    if (unrealistic.length) summary += ` ${unrealistic.map((i) => i.label).join(', ')} ${unrealistic.length > 1 ? 'are' : 'is'} beyond natural limits - see the suggested targets.`;
  }
  return { items, recommendedMonths, summary, disclaimer: FEASIBILITY_DISCLAIMER };
}
