// Pure calc types. No I/O, no framework imports - safe to unit test in isolation.

export type Sex = 'male' | 'female';

export type ActivityLevel =
  | 'sedentary'
  | 'light'
  | 'moderate'
  | 'active'
  | 'veryactive';

export type Goal = 'lose' | 'maintain' | 'gain';

/** Activity multipliers for TDEE (Mifflin-St Jeor convention). */
export const ACTIVITY_FACTORS: Record<ActivityLevel, number> = {
  sedentary: 1.2,
  light: 1.375,
  moderate: 1.55,
  active: 1.725,
  veryactive: 1.9,
};

/** Healthy BMI band used for ideal-weight range. */
export const BMI_HEALTHY_MIN = 18.5;
export const BMI_HEALTHY_MAX = 24.9;

export interface Anthropometrics {
  heightCm: number;
  weightKg: number;
  ageYears: number;
  sex: Sex;
  /** Optional waist circumference (cm) for WHtR. */
  waistCm?: number;
}

export interface MacroTargets {
  proteinG: number;
  fatG: number;
  carbG: number;
  fiberG: number;
  waterMl: number;
}

export interface EnergyResult {
  bmr: number;
  tdee: number;
}

export interface BmiResult {
  bmi: number;
  /** WHO category. */
  category: 'underweight' | 'normal' | 'overweight' | 'obese';
}
