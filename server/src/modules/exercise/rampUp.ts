/**
 * New-to-training volume ramp-up - PURE and deterministic.
 *
 * Someone who just joined the gym (or reports under a month of membership) gets thrown into the
 * same full-intensity program as someone who's already adapted, with nothing easing them in - a
 * real injury/adherence risk. `gymMembershipMonths` is collected at onboarding but otherwise never
 * used anywhere; this scales workout volume up over the first 4 weeks of the account's life for
 * people who report being new (or don't say), then hands off to normal progressive overload.
 */

/** Whether the ramp applies at all: unknown/never-reported membership, or under a month. */
export function isNewToTraining(gymMembershipMonths: number | null | undefined): boolean {
  return gymMembershipMonths == null || gymMembershipMonths <= 1;
}

export interface RampResult {
  /** 1-4: which week of the ramp this is. */
  week: number;
  /** Multiplier applied to set counts; 1.0 = full intensity, ramp is over. */
  factor: number;
}

/**
 * Ramp state for a given account age, or `null` when the ramp doesn't apply (either the user
 * isn't new to training, or they've passed the 4-week window and are at full intensity anyway).
 */
export function rampFor(gymMembershipMonths: number | null | undefined, accountAgeDays: number): RampResult | null {
  if (!isNewToTraining(gymMembershipMonths)) return null;
  if (accountAgeDays < 0 || accountAgeDays >= 28) return null;
  const week = Math.min(4, Math.floor(accountAgeDays / 7) + 1);
  const factor = week === 1 ? 0.6 : week === 2 ? 0.75 : week === 3 ? 0.9 : 1.0;
  if (factor >= 1) return null;
  return { week, factor };
}
