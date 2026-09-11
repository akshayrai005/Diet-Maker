// Pure module. No I/O, no Date.now() - all inputs passed in as plain data.
// Infers real activity level from logged exercise sessions and compares it to the
// user's static onboarding self-report, so a consistent gym-goer's TDEE reflects
// their actual behavior instead of a one-time answer that never updates.

import { ACTIVITY_FACTORS, type ActivityLevel } from '../../calc/types';

/** One logged workout session in the trailing window, collapsed to session-level. */
export interface ExerciseSessionSample {
  /** Distinct calendar day the session happened on (any local-day key works, just needs to be unique per day). */
  dayKey: string;
}

export interface ActivityAutoDetectResult {
  /** Activity tier inferred purely from logged session frequency over the window. */
  inferredLevel: ActivityLevel;
  /** Sessions-per-week rate the inference was based on. */
  sessionsPerWeek: number;
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
  if (sessionsPerWeek >= 6) return 'veryactive';
  if (sessionsPerWeek >= 4) return 'active';
  if (sessionsPerWeek >= 2) return 'moderate';
  if (sessionsPerWeek >= 1) return 'light';
  return 'sedentary';
}

/**
 * Infers an activity tier from trailing logged exercise sessions and decides the effective
 * activity level to feed into TDEE. Only ever adjusts UPWARD from the onboarding self-report
 * (more logged activity than reported -> more food) - it never auto-downgrades someone's
 * calories just because they didn't log; that case is surfaced as a flag instead.
 */
export function detectActivityLevel(
  reportedLevel: ActivityLevel,
  sessions: ExerciseSessionSample[],
  windowDays: number,
): ActivityAutoDetectResult {
  const distinctDays = new Set(sessions.map((s) => s.dayKey)).size;
  const weeks = Math.max(windowDays / 7, 1e-9);
  const sessionsPerWeek = distinctDays / weeks;
  const inferredLevel = tierFromSessionsPerWeek(sessionsPerWeek);

  const reportedRank = TIER_ORDER.indexOf(reportedLevel);
  const inferredRank = TIER_ORDER.indexOf(inferredLevel);

  const higherThanReported = inferredRank > reportedRank;
  const lowerThanReported = inferredRank < reportedRank;

  return {
    inferredLevel,
    sessionsPerWeek: Math.round(sessionsPerWeek * 10) / 10,
    higherThanReported,
    lowerThanReported,
    effectiveLevel: higherThanReported ? inferredLevel : reportedLevel,
  };
}

/** TDEE delta (kcal) between two activity levels at a given BMR - used to explain the bump in a flag message. */
export function tdeeDeltaForLevelChange(bmr: number, from: ActivityLevel, to: ActivityLevel): number {
  return Math.round(bmr * (ACTIVITY_FACTORS[to] - ACTIVITY_FACTORS[from]));
}
