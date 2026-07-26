import { Goal, Sex } from '../calc/types';

/** Medical conditions that modify targets or trigger supervision. */
export type Condition =
  | 'diabetes'
  | 'hypertension'
  | 'kidney_disease'
  | 'fatty_liver'
  | 'thyroid'
  | 'pcos'
  | 'pregnancy'
  | 'breastfeeding'
  | 'gout'
  | 'heart_disease'
  | 'cancer'
  | 'post_surgery';

export type FlagSeverity = 'info' | 'warning' | 'critical';

export interface Flag {
  code: string;
  severity: FlagSeverity;
  message: string;
}

export interface GuardrailInput {
  sex: Sex;
  ageYears: number;
  currentWeightKg: number;
  targetWeightKg: number;
  /** Height (cm) - lets the gate catch an underweight target (BMI < 18.5). */
  heightCm?: number;
  tdee: number;
  bmi: number;
  goal: Goal;
  conditions: Condition[];
  /** Desired weekly weight change (kg, positive = loss). Optional. */
  desiredWeeklyLossKg?: number;
  /** Set true only when a clinician has authorised a below-floor plan. */
  clinicianOverride?: boolean;
  /** Age below 18 -> minor rules. Derived from ageYears if omitted. */
  isMinor?: boolean;
  reducedMobility?: boolean;
}

export interface GuardrailResult {
  /** Final, safety-adjusted daily calorie target. */
  dailyKcal: number;
  /** Protein g/kg after condition overrides. */
  proteinPerKg: number;
  /** Fat g/kg floor after condition overrides. */
  fatPerKgMin: number;
  /** Sodium ceiling (mg) when a condition constrains it. */
  sodiumMaxMg?: number;
  /** The weekly change actually planned for (kg), after capping. */
  safeWeeklyDeltaKg: number;
  /** Whether a medical-supervision banner must be shown. */
  requiresSupervision: boolean;
  /** Whether weight loss is blocked entirely (e.g. pregnancy). */
  weightLossBlocked: boolean;
  flags: Flag[];
}
