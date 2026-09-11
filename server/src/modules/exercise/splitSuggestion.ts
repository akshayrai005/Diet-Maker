// Pure module. No I/O.
//
// A gym beginner's real first-month session mixes machine/dumbbell/cardio work across the whole
// body in one visit - not an isolated single-muscle "bro split" day. Once someone is training
// consistently past that first month, a dedicated body-part split gives each muscle more focused
// volume and is the standard progression. This picks the right default when the user hasn't
// explicitly chosen a split, and nudges (never silently overrides) an explicit choice as they age
// past the beginner window - generalized on months-training, not any specific goal or condition.

import { isNewToTraining } from './rampUp';
import type { BodyGoal, TrainingSplit } from './exercise.types';

/**
 * Default split when the user hasn't picked one explicitly. Fat-loss stays on its own dedicated
 * weights+HIIT program (already a mixed, non-isolated session) regardless of tenure. For
 * muscular/athletic goals: full-body mixed sessions for the first month, then a body-part split.
 */
export function defaultTrainingSplit(
  bodyGoal: BodyGoal,
  gymMembershipMonths: number | null | undefined,
): TrainingSplit | undefined {
  if (bodyGoal === 'fatloss') return undefined;
  return isNewToTraining(gymMembershipMonths) ? 'full_body' : 'body_part';
}

export interface SplitSuggestion {
  suggestBodyPartSplit: boolean;
  reason?: string;
}

/**
 * Informational nudge only - never overrides an explicit user choice. Fires when someone is still
 * on a full-body/mixed session (whether by explicit choice or by not having picked one) and has
 * been training long enough that a body-part split would serve them better.
 */
export function suggestSplitUpgrade(
  explicitSplit: TrainingSplit | undefined,
  gymMembershipMonths: number | null | undefined,
): SplitSuggestion {
  const onMixedSession = explicitSplit === undefined || explicitSplit === 'full_body';
  if (!onMixedSession) return { suggestBodyPartSplit: false };
  const months = gymMembershipMonths ?? 0;
  if (months < 2) return { suggestBodyPartSplit: false };
  return {
    suggestBodyPartSplit: true,
    reason: `You've been training consistently for ${months} months - a body-part split (Chest/Back/Shoulders/Arms/Legs, one focus per day) now gives each muscle more dedicated volume. Switch anytime in Profile.`,
  };
}
