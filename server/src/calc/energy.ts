import { ACTIVITY_FACTORS, ActivityLevel, Anthropometrics, EnergyResult } from './types';
import { round } from './anthropometry';

/**
 * Basal Metabolic Rate - Mifflin-St Jeor:
 *   male:   10*W + 6.25*H − 5*A + 5
 *   female: 10*W + 6.25*H − 5*A − 161
 * (W kg, H cm, A years)
 */
export function bmrMifflinStJeor(a: Anthropometrics): number {
  if (a.weightKg <= 0 || a.heightCm <= 0 || a.ageYears <= 0) {
    throw new RangeError('weight, height and age must be > 0');
  }
  const base = 10 * a.weightKg + 6.25 * a.heightCm - 5 * a.ageYears;
  const sexAdjust = a.sex === 'male' ? 5 : -161;
  return round(base + sexAdjust, 0);
}

/** Total Daily Energy Expenditure = BMR × activity factor. */
export function tdee(bmr: number, activity: ActivityLevel): number {
  return round(bmr * ACTIVITY_FACTORS[activity], 0);
}

export function energy(a: Anthropometrics, activity: ActivityLevel): EnergyResult {
  const b = bmrMifflinStJeor(a);
  return { bmr: b, tdee: tdee(b, activity) };
}
