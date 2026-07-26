/**
 * Pure helpers for medication/supplement tracking. Kaizen tracks what you take and can remind you -
 * it deliberately does NOT check drug interactions or give dosing advice; that's for a pharmacist
 * or doctor. This module holds only deterministic, unit-tested helpers (no diagnosis, no LLM).
 */

export type MedicationType = 'med' | 'supplement';

/** Standing, prominent disclaimer surfaced anywhere meds are shown. */
export const MEDICATION_DISCLAIMER =
  "Kaizen doesn't check drug interactions or give dosing advice. Always confirm medicines, doses and timing with your pharmacist or doctor.";

const TIME_RE = /^([01]\d|2[0-3]):([0-5]\d)$/;

export function isValidTime(t: string): boolean {
  return TIME_RE.test(t);
}

/** Validate "HH:mm" strings, drop invalid ones, de-duplicate, and sort ascending by clock time. */
export function normalizeTimes(times: string[] | undefined): string[] {
  if (!times) return [];
  const valid = times.filter((t) => typeof t === 'string' && isValidTime(t));
  const unique = Array.from(new Set(valid));
  unique.sort((a, b) => toMinutes(a) - toMinutes(b));
  return unique;
}

export function toMinutes(hhmm: string): number {
  const [h, m] = hhmm.split(':');
  return Number(h) * 60 + Number(m);
}

/** Adherence % over a window: taken events vs expected doses (times/day × days). Capped at 100. */
export function adherencePct(takenCount: number, dosesPerDay: number, days: number): number | null {
  const expected = dosesPerDay * days;
  if (expected <= 0) return null; // no schedule → adherence is not meaningful
  return Math.min(100, Math.round((takenCount / expected) * 100));
}
