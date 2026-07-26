import { round } from '../../calc/anthropometry';
import type { WeightPoint } from '../logging/dashboard';

export interface AdaptationInput {
  goal: 'lose' | 'maintain' | 'gain';
  targetKcal: number;
  loggedDailyKcals: number[]; // one entry per day with logs
  weightPoints: WeightPoint[];
}

export interface Adaptation {
  status: 'insufficient_data' | 'on_track' | 'adjust_behaviour' | 'adjust_target';
  message: string;
  /** Suggested change to the daily calorie target (kcal). 0 = keep target, change behaviour. */
  suggestedKcalDelta: number;
  avgLoggedKcal: number | null;
  weeklyWeightChangeKg: number | null;
}

function mean(xs: number[]): number {
  return xs.reduce((a, b) => a + b, 0) / xs.length;
}

/** Weekly weight change (kg/week) from the first and last points, or null. */
function weeklyWeightChange(points: WeightPoint[]): number | null {
  if (points.length < 2) return null;
  const sorted = [...points].sort((a, b) => a.date.localeCompare(b.date));
  const first = sorted[0]!;
  const last = sorted[sorted.length - 1]!;
  const days = (new Date(last.date).getTime() - new Date(first.date).getTime()) / 86_400_000;
  if (days < 1) return null;
  return round(((last.weightKg - first.weightKg) / days) * 7, 2);
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

  // Intake is close to target - judge by the scale.
  if (weekly !== null) {
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
