import { round } from '../../calc/anthropometry';
import type { WeightPoint } from '../logging/dashboard';

export interface AdaptationInput {
  goal: 'lose' | 'maintain' | 'gain';
  targetKcal: number;
  loggedDailyKcals: number[]; // one entry per day with logs
  weightPoints: WeightPoint[];
}

export interface Adaptation {
  status: 'insufficient_data' | 'on_track' | 'adjust_behaviour' | 'adjust_target' | 'review_deficit';
  message: string;
  /** Suggested change to the daily calorie target (kcal). 0 = keep target, change behaviour. */
  suggestedKcalDelta: number;
  avgLoggedKcal: number | null;
  weeklyWeightChangeKg: number | null;
}

function mean(xs: number[]): number {
  return xs.reduce((a, b) => a + b, 0) / xs.length;
}

/**
 * The days that may be averaged into "you're eating X kcal a day": finished days only. Today is still in progress and a
 * fast day is deliberately tiny, so both would read as "under-eating" and trigger a false nag. Pure.
 */
export function completeLoggedDays(perDay: Map<string, number>, todayKey: string, fastDayOfWeek?: number): number[] {
  const out: number[] = [];
  for (const [key, kcal] of perDay) {
    if (key >= todayKey) continue;
    if (fastDayOfWeek !== undefined && new Date(`${key}T12:00:00Z`).getUTCDay() === fastDayOfWeek) continue;
    out.push(Math.round(kcal));
  }
  return out;
}

/** A weight trend needs real time and several weigh-ins: one high or low reading (water, salt, glycogen, a heavy training day) must never move calories. */
export const MIN_TREND_DAYS = 14;
export const MIN_TREND_POINTS = 3;
/** Days of food logs needed before the plan's calories may be changed (behaviour advice needs only 3). */
export const MIN_LOG_DAYS_TO_ADJUST = 5;

/**
 * Weekly weight change (kg/week) as a least-squares slope over all weigh-ins, or null when there are too few
 * points or they span under two weeks. A slope over several points is far less noisy than first-vs-last.
 */
export function weeklyWeightChange(points: WeightPoint[]): number | null {
  if (points.length < MIN_TREND_POINTS) return null;
  const sorted = [...points].sort((a, b) => a.date.localeCompare(b.date));
  const t0 = new Date(sorted[0]!.date).getTime();
  const xs = sorted.map((p) => (new Date(p.date).getTime() - t0) / 86_400_000);
  if (xs[xs.length - 1]! < MIN_TREND_DAYS) return null;
  const ys = sorted.map((p) => p.weightKg);
  const mx = mean(xs);
  const my = mean(ys);
  const den = xs.reduce((s, x) => s + (x - mx) ** 2, 0);
  if (den === 0) return null;
  const slope = xs.reduce((s, x, i) => s + (x - mx) * (ys[i]! - my), 0) / den;
  return round(slope * 7, 2);
}

/**
 * Nightly adaptation: compares logged intake and measured weight trend to the plan and
 * suggests a behaviour or target tweak. Deterministic and unit-tested.
 */
export function computeAdaptation(input: AdaptationInput): Adaptation {
  const { goal, targetKcal, loggedDailyKcals, weightPoints } = input;

  if (loggedDailyKcals.length < 3) {
    return {
      status: 'insufficient_data',
      message: 'Log a few more days and I can fine-tune your plan.',
      suggestedKcalDelta: 0,
      avgLoggedKcal: loggedDailyKcals.length ? round(mean(loggedDailyKcals), 0) : null,
      weeklyWeightChangeKg: weeklyWeightChange(weightPoints),
    };
  }

  const avg = round(mean(loggedDailyKcals), 0);
  const weekly = weeklyWeightChange(weightPoints);
  const overBy = avg - targetKcal;

  // Behaviour first: if intake is well off the target, fix adherence before changing the plan.
  if (Math.abs(overBy) > 250) {
    return {
      status: 'adjust_behaviour',
      message:
        overBy > 0
          ? `You're averaging ${avg} kcal - about ${overBy} over your ${targetKcal} target. Tighten portions or swap high-calorie items before we change the plan.`
          : `You're averaging ${avg} kcal - about ${Math.abs(overBy)} under your ${targetKcal} target. Under-eating stalls progress; add a protein-rich snack.`,
      suggestedKcalDelta: 0,
      avgLoggedKcal: avg,
      weeklyWeightChangeKg: weekly,
    };
  }

  const latestKg = [...weightPoints].sort((a, b) => a.date.localeCompare(b.date)).at(-1)?.weightKg ?? 0;
  // Losing faster than ~1% of body weight a week is more likely muscle/water loss than fat: flag it, never push it harder.
  if (goal === 'lose' && weekly !== null && latestKg > 0 && -weekly > latestKg * 0.01) {
    return {
      status: 'review_deficit',
      message: `Weight is falling ${Math.abs(weekly)} kg/week - faster than the ~1% of body weight a week that is generally sustainable. The deficit may be too aggressive for keeping muscle and energy; consider eating a little more, and see a doctor or dietitian if you feel weak, dizzy or unwell.`,
      suggestedKcalDelta: 0,
      avgLoggedKcal: avg,
      weeklyWeightChangeKg: weekly,
    };
  }
  // Intake is close to target - judge by the scale, and only with enough logging and a real trend.
  if (weekly !== null && loggedDailyKcals.length >= MIN_LOG_DAYS_TO_ADJUST) {
    if (goal === 'lose' && weekly >= -0.05) {
      return {
        status: 'adjust_target',
        message: `You're on target with intake but weight is flat (${weekly} kg/wk). Nudging the daily target down by ~150 kcal should restart progress.`,
        suggestedKcalDelta: -150,
        avgLoggedKcal: avg,
        weeklyWeightChangeKg: weekly,
      };
    }
    if (goal === 'gain' && weekly <= 0.05) {
      return {
        status: 'adjust_target',
        message: `Intake is on target but weight is flat (${weekly} kg/wk). Adding ~150 kcal/day should help you gain steadily.`,
        suggestedKcalDelta: 150,
        avgLoggedKcal: avg,
        weeklyWeightChangeKg: weekly,
      };
    }
  }

  return {
    status: 'on_track',
    message: `Nicely on track - averaging ${avg} kcal against a ${targetKcal} target${weekly !== null ? `, weight moving ${weekly} kg/wk` : ''}. Keep it up!`,
    suggestedKcalDelta: 0,
    avgLoggedKcal: avg,
    weeklyWeightChangeKg: weekly,
  };
}
