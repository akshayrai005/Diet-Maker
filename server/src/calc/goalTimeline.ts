import { MAX_WEEKLY_LOSS_FRACTION, KCAL_PER_KG_FAT } from '../guardrails';

/**
 * Deterministic goal-timeline math: "reach my target weight in N weeks" → a SAFE weekly rate, the
 * realistic number of weeks that actually takes, and the daily calorie delta it implies. Safety is
 * non-negotiable: the loss rate is clamped to the same cap the guardrail engine uses (0.75% of body
 * weight/week), gain to a lean ~0.35%/week, and never for minors / pregnancy / medically blocked.
 * If the user's chosen timeframe demands an unsafe pace we DO NOT comply — we clamp and show the
 * realistic date with an explanation. Pure & unit-tested. Educational, not medical advice.
 */

const GAIN_RATE_FRACTION = 0.0035; // ~0.35%/week lean gain cap (matches the guardrail's gain pace)
const WEEKS_PER_MONTH = 4.345;

export const GOAL_TIMELINE_DISCLAIMER =
  'This is an educational projection at a safe pace, not medical advice. Real progress varies week to week; for a personalised or faster plan, work with a doctor or dietitian.';

export interface GoalTimelineInput {
  currentWeightKg: number;
  targetWeightKg: number;
  /** The timeframe the user chose, in weeks. */
  desiredWeeks: number;
  isMinor?: boolean;
  /** True if a deficit is medically blocked (pregnancy / breastfeeding / cancer / underweight). */
  weightLossBlocked?: boolean;
}

export interface GoalTimeline {
  direction: 'lose' | 'gain' | 'maintain';
  totalChangeKg: number; // signed (target − current)
  requestedWeeklyRateKg: number; // magnitude the user asked for
  safeWeeklyRateKg: number; // magnitude after the safety clamp
  clamped: boolean; // true if the requested pace was unsafe and we slowed it
  realisticWeeks: number; // weeks to reach target at the safe pace
  realisticMonths: number;
  /** Signed loss magnitude (kg/week) to feed the calorie engine; 0 when gaining/maintaining/blocked. */
  desiredWeeklyLossKg: number;
  /** Approx daily calorie delta (deficit +, surplus −) implied by the safe rate. */
  dailyKcalDelta: number;
  blocked: boolean; // a loss timeline that isn't safe to plan here
  message: string;
  disclaimer: string;
}

const round1 = (x: number) => Math.round(x * 10) / 10;
const round2 = (x: number) => Math.round(x * 100) / 100;

export function goalTimeline(input: GoalTimelineInput): GoalTimeline {
  const current = input.currentWeightKg;
  const target = input.targetWeightKg;
  const weeks = Math.max(1, Math.floor(input.desiredWeeks || 1));
  const total = round1(target - current);
  const base = {
    totalChangeKg: total,
    disclaimer: GOAL_TIMELINE_DISCLAIMER,
  };

  // Already at (or within 0.5 kg of) target.
  if (Math.abs(total) < 0.5 || !isFinite(current) || !isFinite(target) || current <= 0 || target <= 0) {
    return {
      ...base,
      direction: 'maintain',
      requestedWeeklyRateKg: 0,
      safeWeeklyRateKg: 0,
      clamped: false,
      realisticWeeks: 0,
      realisticMonths: 0,
      desiredWeeklyLossKg: 0,
      dailyKcalDelta: 0,
      blocked: false,
      message: "You're already around your target weight — this keeps you steady.",
    };
  }

  const losing = total < 0;
  const requestedRate = round2(Math.abs(total) / weeks);

  // Minors and the medically weight-loss-blocked never get a loss timeline.
  if (losing && (input.isMinor || input.weightLossBlocked)) {
    return {
      ...base,
      direction: 'lose',
      requestedWeeklyRateKg: requestedRate,
      safeWeeklyRateKg: 0,
      clamped: true,
      realisticWeeks: 0,
      realisticMonths: 0,
      desiredWeeklyLossKg: 0,
      dailyKcalDelta: 0,
      blocked: true,
      message: input.isMinor
        ? "We don't set weight-loss timelines for under-18s — focus on healthy habits and growth. A doctor can guide safe targets."
        : 'A weight-loss timeline is not appropriate right now — please follow the plan your doctor or dietitian gives you.',
    };
  }

  // Keep the cap at full precision — rounding it could nudge the rate ABOVE the safe limit.
  const safeMax = (losing ? MAX_WEEKLY_LOSS_FRACTION : GAIN_RATE_FRACTION) * current;
  const safeRate = Math.min(requestedRate, safeMax);
  const clamped = requestedRate > safeMax + 1e-9;
  const realisticWeeks = Math.max(1, Math.ceil(Math.abs(total) / safeRate));
  const realisticMonths = round1(realisticWeeks / WEEKS_PER_MONTH);
  const dailyKcalDelta = Math.round((safeRate * KCAL_PER_KG_FAT) / 7) * (losing ? 1 : -1);

  const paceStr = `${safeRate.toFixed(2)} kg/week`;
  const message = clamped
    ? `A safe pace is about ${paceStr}, so ${round1(current)} → ${round1(target)} kg takes about ${realisticWeeks} weeks (~${realisticMonths} months). Your ${weeks}-week goal was faster than is safe, so we've set the safe pace instead.`
    : `On track: ${round1(current)} → ${round1(target)} kg at ${paceStr} ≈ ${realisticWeeks} weeks (~${realisticMonths} months).`;

  return {
    ...base,
    direction: losing ? 'lose' : 'gain',
    requestedWeeklyRateKg: requestedRate,
    safeWeeklyRateKg: safeRate,
    clamped,
    realisticWeeks,
    realisticMonths,
    desiredWeeklyLossKg: losing ? safeRate : 0,
    dailyKcalDelta,
    blocked: false,
    message,
  };
}
