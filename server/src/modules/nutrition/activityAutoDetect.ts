// Pure module. No I/O, no Date.now() - all inputs passed in as plain data.
// Infers real activity level from logged exercise sessions AND real step-count history, and
// compares it to the user's static onboarding self-report, so someone whose real movement comes
// from daily steps (an office job with active days when time allows, not just gym sessions) gets
// the same fair treatment as a consistent gym-goer - a one-time self-report never updates either
// way otherwise.

import { ACTIVITY_FACTORS, type ActivityLevel } from '../../calc/types';

/** One logged workout session in the trailing window, collapsed to session-level. */
export interface ExerciseSessionSample {
  /** Distinct calendar day the session happened on (any local-day key works, just needs to be unique per day). */
  dayKey: string;
}

export interface ActivityAutoDetectResult {
  /** Activity tier inferred from the higher of session frequency and real step count. */
  inferredLevel: ActivityLevel;
  /** Sessions-per-week rate the session-based half of the inference was based on. */
  sessionsPerWeek: number;
  /** Average daily steps the step-based half of the inference was based on, if any step history exists. */
  avgDailySteps?: number;
  /** Which signal actually drove the inferred tier - useful for an honest flag message. */
  inferredFrom: 'sessions' | 'steps' | 'sessions_and_steps';
  /** True when inferred is strictly higher than the onboarding self-report. */
  higherThanReported: boolean;
  /** True when inferred is strictly lower than the onboarding self-report. */
  lowerThanReported: boolean;
  /** The activity level actually used for TDEE this run - reported, or bumped up to inferred. */
  effectiveLevel: ActivityLevel;
}

const TIER_ORDER: ActivityLevel[] = ['sedentary', 'light', 'moderate', 'active', 'veryactive'];

/** Session-frequency -> inferred tier. Deliberately conservative: only frequency-based, no guessing at intensity. */
function tierFromSessionsPerWeek(sessionsPerWeek: number): ActivityLevel {
  // Gym sessions alone never justify 'veryactive' (x1.9 = a physical-labour job or two-a-day athletes):
  // an hour of training a day on an otherwise sedentary day is 'active' (x1.725) at most.
  if (sessionsPerWeek >= 4) return 'active';
  if (sessionsPerWeek >= 2) return 'moderate';
  if (sessionsPerWeek >= 1) return 'light';
  return 'sedentary';
}

/** Average daily steps -> inferred tier, using standard step-based activity bands. */
function tierFromAvgDailySteps(avgSteps: number): ActivityLevel {
  if (avgSteps >= 12_500) return 'veryactive';
  if (avgSteps >= 10_000) return 'active';
  if (avgSteps >= 7_500) return 'moderate';
  if (avgSteps >= 5_000) return 'light';
  return 'sedentary';
}

/**
 * Infers an activity tier from trailing logged exercise sessions and/or real step-count history,
 * and decides the effective activity level to feed into TDEE. Someone whose real movement comes
 * from steps rather than structured gym sessions (a desk job with active days when time allows)
 * gets credit too - the two signals are independent and the HIGHER tier wins, since either one
 * alone is real evidence of more activity than a static self-report. Only ever adjusts UPWARD
 * from the onboarding self-report (more real activity than reported -> more food) - it never
 * auto-downgrades someone's calories just because they didn't log; that case is surfaced as a
 * flag instead.
 */
export function detectActivityLevel(
  reportedLevel: ActivityLevel,
  sessions: ExerciseSessionSample[],
  windowDays: number,
  avgDailySteps?: number,
): ActivityAutoDetectResult {
  const distinctDays = new Set(sessions.map((s) => s.dayKey)).size;
  const weeks = Math.max(windowDays / 7, 1e-9);
  const sessionsPerWeek = distinctDays / weeks;
  const sessionsTier = tierFromSessionsPerWeek(sessionsPerWeek);
  const stepsTier = avgDailySteps != null ? tierFromAvgDailySteps(avgDailySteps) : null;

  const sessionsRank = TIER_ORDER.indexOf(sessionsTier);
  const stepsRank = stepsTier != null ? TIER_ORDER.indexOf(stepsTier) : -1;

  let inferredLevel: ActivityLevel;
  let inferredFrom: ActivityAutoDetectResult['inferredFrom'];
  if (stepsRank > sessionsRank) {
    inferredLevel = stepsTier!;
    inferredFrom = 'steps';
  } else if (sessionsRank > stepsRank) {
    inferredLevel = sessionsTier;
    inferredFrom = 'sessions';
  } else {
    inferredLevel = sessionsTier;
    inferredFrom = stepsTier != null ? 'sessions_and_steps' : 'sessions';
  }

  const reportedRank = TIER_ORDER.indexOf(reportedLevel);
  const inferredRank = TIER_ORDER.indexOf(inferredLevel);

  const higherThanReported = inferredRank > reportedRank;
  const lowerThanReported = inferredRank < reportedRank;

  return {
    inferredLevel,
    sessionsPerWeek: Math.round(sessionsPerWeek * 10) / 10,
    avgDailySteps: avgDailySteps != null ? Math.round(avgDailySteps) : undefined,
    inferredFrom,
    higherThanReported,
    lowerThanReported,
    effectiveLevel: higherThanReported ? inferredLevel : reportedLevel,
  };
}

/** TDEE delta (kcal) between two activity levels at a given BMR - used to explain the bump in a flag message. */
export function tdeeDeltaForLevelChange(bmr: number, from: ActivityLevel, to: ActivityLevel): number {
  return Math.round(bmr * (ACTIVITY_FACTORS[to] - ACTIVITY_FACTORS[from]));
}
