import type { Sex } from '../../calc/types';

/**
 * Deterministic, non-diagnostic classification of health vitals & lab values into plain-language
 * status bands, each citing the guideline it uses. This is education, NOT diagnosis: a band never
 * names a disease as present, and every result defers to a clinician. Pure & fully unit-tested -
 * never delegated to an LLM.
 */

export const VITAL_TYPES = [
  'bloodPressure',
  'restingHr',
  'fastingGlucose',
  'hba1c',
  'ldl',
  'hdl',
  'triglycerides',
  'tsh',
  'vitaminD',
  'ferritin',
  'hemoglobin',
  'weight',
  'waist',
] as const;

export type VitalType = (typeof VITAL_TYPES)[number];

/** Coarse severity, worst-first, for colour + sorting. `urgent` = the reading warrants prompt care. */
export type VitalSeverity = 'normal' | 'watch' | 'elevated' | 'high' | 'urgent';

export interface VitalReading {
  /** Single-number reading (everything except blood pressure). */
  value?: number;
  /** Blood pressure only. */
  systolic?: number;
  diastolic?: number;
}

export interface VitalClassification {
  /** Short band label, e.g. "Normal", "Stage 1 hypertension", "Prediabetes". */
  band: string;
  severity: VitalSeverity;
  /** Educational one-liner about what the band means / what helps. */
  message: string;
  /** The guideline the cut-offs come from, e.g. "ACC/AHA 2017". */
  guideline: string;
}

export const VITAL_DISCLAIMER =
  'Educational status band from the number you entered - not a diagnosis. Lab reference ranges vary by lab and person; confirm anything concerning with your doctor.';

/** Canonical unit per type (what the UI shows / stores). */
export const VITAL_UNIT: Record<VitalType, string> = {
  bloodPressure: 'mmHg',
  restingHr: 'bpm',
  fastingGlucose: 'mg/dL',
  hba1c: '%',
  ldl: 'mg/dL',
  hdl: 'mg/dL',
  triglycerides: 'mg/dL',
  tsh: 'mIU/L',
  vitaminD: 'ng/mL',
  ferritin: 'ng/mL',
  hemoglobin: 'g/dL',
  weight: 'kg',
  waist: 'cm',
};

export const VITAL_LABEL: Record<VitalType, string> = {
  bloodPressure: 'Blood pressure',
  restingHr: 'Resting heart rate',
  fastingGlucose: 'Fasting glucose',
  hba1c: 'HbA1c',
  ldl: 'LDL cholesterol',
  hdl: 'HDL cholesterol',
  triglycerides: 'Triglycerides',
  tsh: 'TSH (thyroid)',
  vitaminD: 'Vitamin D',
  ferritin: 'Ferritin (iron store)',
  hemoglobin: 'Hemoglobin',
  weight: 'Weight',
  waist: 'Waist',
};

export function isVitalType(t: string): t is VitalType {
  return (VITAL_TYPES as readonly string[]).includes(t);
}

/** True if the reading has the numeric field(s) the type needs, all finite and positive. */
export function isValidReading(type: VitalType, r: VitalReading): boolean {
  if (type === 'bloodPressure') {
    return (
      typeof r.systolic === 'number' &&
      typeof r.diastolic === 'number' &&
      isFinite(r.systolic) &&
      isFinite(r.diastolic) &&
      r.systolic > 0 &&
      r.diastolic > 0 &&
      r.systolic <= 300 &&
      r.diastolic <= 200 &&
      r.systolic > r.diastolic
    );
  }
  return typeof r.value === 'number' && isFinite(r.value) && r.value > 0;
}

/** The single number used for trend lines (systolic for BP). null if the reading is invalid. */
export function primaryValue(type: VitalType, r: VitalReading): number | null {
  if (!isValidReading(type, r)) return null;
  return type === 'bloodPressure' ? r.systolic! : r.value!;
}

const c = (band: string, severity: VitalSeverity, message: string, guideline: string): VitalClassification => ({
  band,
  severity,
  message,
  guideline,
});

/**
 * Classify a reading into an educational band. `sex` sharpens the sex-specific bands
 * (HDL, hemoglobin, ferritin, waist); omit it and those fall back to the more cautious cut-off.
 */
export function classifyVital(
  type: VitalType,
  r: VitalReading,
  ctx: { sex?: Sex } = {},
): VitalClassification | null {
  if (!isValidReading(type, r)) return null;
  const v = r.value ?? 0;
  const female = ctx.sex === 'female';

  switch (type) {
    case 'bloodPressure': {
      const s = r.systolic!;
      const d = r.diastolic!;
      if (s > 180 || d > 120)
        return c('Hypertensive crisis', 'urgent', 'This is very high. If you have symptoms (chest pain, breathlessness, vision changes), seek emergency care; otherwise contact a doctor promptly.', 'ACC/AHA 2017');
      if (s >= 140 || d >= 90)
        return c('Stage 2 hypertension', 'high', 'Consistently high. A doctor may discuss lifestyle changes and treatment; cut salt (DASH), move daily, limit alcohol.', 'ACC/AHA 2017');
      if (s >= 130 || d >= 80)
        return c('Stage 1 hypertension', 'elevated', 'Above target. Reduce salt/processed food, add potassium-rich veg and daily walks; recheck over a week.', 'ACC/AHA 2017');
      if (s >= 120)
        return c('Elevated', 'watch', 'Slightly above ideal. Lifestyle habits now keep it from rising.', 'ACC/AHA 2017');
      return c('Normal', 'normal', 'A healthy blood pressure - keep it up.', 'ACC/AHA 2017');
    }
    case 'restingHr': {
      if (v > 100) return c('Elevated (tachycardia range)', 'elevated', 'Above the typical 60-100 resting range. Check caffeine, hydration, sleep, stress; regular cardio lowers it. Mention persistent highs to a doctor.', 'AHA');
      if (v < 60) return c('Low (bradycardia range)', 'watch', 'Below 60 is common and healthy in fit people; only a concern with dizziness/fatigue - then see a doctor.', 'AHA');
      return c('Normal', 'normal', 'Within the healthy 60-100 resting range.', 'AHA');
    }
    case 'fastingGlucose': {
      if (v >= 126) return c('Diabetes range', 'high', 'In the diabetes range on this reading. A doctor confirms with a repeat test / HbA1c. Low-GI carbs, fibre and post-meal walks help.', 'ADA 2024');
      if (v >= 100) return c('Prediabetes range', 'elevated', 'Prediabetes range. Losing 5-7% body weight and daily activity can reverse this stage.', 'ADA 2024');
      if (v < 70) return c('Low', 'watch', 'Below 70 can mean hypoglycaemia, especially on diabetes medication - treat with fast carbs and discuss with your doctor.', 'ADA 2024');
      return c('Normal', 'normal', 'A healthy fasting glucose.', 'ADA 2024');
    }
    case 'hba1c': {
      if (v >= 6.5) return c('Diabetes range', 'high', 'In the diabetes range. Confirm with your doctor; steady low-GI eating and activity improve it over months.', 'ADA 2024');
      if (v >= 5.7) return c('Prediabetes range', 'elevated', 'Prediabetes range (average glucose over ~3 months). Diet and activity changes can bring it down.', 'ADA 2024');
      return c('Normal', 'normal', 'A healthy 3-month average glucose.', 'ADA 2024');
    }
    case 'ldl': {
      if (v >= 190) return c('Very high', 'high', 'Very high LDL. Discuss with a doctor; reduce saturated fat, add soluble fibre and activity.', 'NCEP ATP III');
      if (v >= 160) return c('High', 'elevated', 'High LDL. Lifestyle changes (fibre, unsaturated fats, exercise) help lower it.', 'NCEP ATP III');
      if (v >= 130) return c('Borderline high', 'watch', 'Borderline. Small diet changes now keep it in check.', 'NCEP ATP III');
      if (v >= 100) return c('Near optimal', 'normal', 'Near optimal LDL.', 'NCEP ATP III');
      return c('Optimal', 'normal', 'An optimal LDL level.', 'NCEP ATP III');
    }
    case 'hdl': {
      const low = female ? 50 : 40;
      if (v < low) return c('Low (less protective)', 'elevated', `Below ${low} mg/dL. HDL is protective - regular exercise, healthy fats and not smoking raise it.`, 'NCEP ATP III');
      if (v >= 60) return c('Protective', 'normal', 'A protective HDL level (≥60 is favourable).', 'NCEP ATP III');
      return c('Acceptable', 'normal', 'An acceptable HDL level.', 'NCEP ATP III');
    }
    case 'triglycerides': {
      if (v >= 500) return c('Very high', 'high', 'Very high - a doctor should review. Cut refined carbs/alcohol/sugary drinks; add omega-3 and activity.', 'NCEP ATP III');
      if (v >= 200) return c('High', 'elevated', 'High triglycerides. Reduce refined carbs and alcohol; more activity helps.', 'NCEP ATP III');
      if (v >= 150) return c('Borderline high', 'watch', 'Borderline. Watch sugary drinks and refined carbs.', 'NCEP ATP III');
      return c('Normal', 'normal', 'A healthy triglyceride level.', 'NCEP ATP III');
    }
    case 'tsh': {
      if (v > 4.0) return c('Above range (possible underactive thyroid)', 'elevated', 'Above the usual 0.4-4.0 range - can suggest an underactive thyroid. A doctor interprets TSH with symptoms and other thyroid tests.', 'ATA');
      if (v < 0.4) return c('Below range (possible overactive thyroid)', 'elevated', 'Below the usual range - can suggest an overactive thyroid. Discuss with your doctor.', 'ATA');
      return c('Normal', 'normal', 'Within the usual thyroid reference range.', 'ATA');
    }
    case 'vitaminD': {
      if (v < 20) return c('Deficient', 'elevated', 'Deficiency range. Sunlight, D-rich/fortified foods and a doctor-advised supplement help.', 'Endocrine Society');
      if (v < 30) return c('Insufficient', 'watch', 'Insufficient. A modest top-up usually gets you into range.', 'Endocrine Society');
      if (v > 100) return c('High', 'elevated', 'Unusually high - avoid over-supplementing and check with a doctor.', 'Endocrine Society');
      return c('Sufficient', 'normal', 'A sufficient vitamin D level.', 'Endocrine Society');
    }
    case 'ferritin': {
      if (v < 30) return c('Low (low iron stores)', 'elevated', 'Low iron stores - common cause of fatigue. Iron-rich foods with vitamin C help; a doctor can check for a cause.', 'WHO');
      if (v > 300) return c('High', 'watch', 'Above the usual range - can rise with inflammation; discuss with a doctor.', 'WHO');
      return c('Normal', 'normal', 'Healthy iron stores.', 'WHO');
    }
    case 'hemoglobin': {
      const low = female ? 12 : 13;
      if (v < low) return c('Low (anaemia range)', 'elevated', `Below ${low} g/dL suggests anaemia - iron/B12/folate-rich foods help; a doctor can find the cause.`, 'WHO');
      if (v > (female ? 15.5 : 17)) return c('High', 'watch', 'Above the usual range - discuss with a doctor (hydration, altitude and other factors matter).', 'WHO');
      return c('Normal', 'normal', 'A healthy hemoglobin level.', 'WHO');
    }
    case 'waist': {
      const high = female ? 80 : 90; // Asian-Indian cut-offs (IDF)
      if (v >= high) return c('Above healthy range', 'elevated', `At/above ${high} cm signals higher visceral fat for South-Asian bodies. Fibre, protein and daily movement shrink it.`, 'IDF (Asian)');
      return c('Healthy range', 'normal', 'Within a healthy waist range.', 'IDF (Asian)');
    }
    case 'weight':
      // Weight has no universal "band" without height/goal - the app tracks its trend instead.
      return c('Logged', 'normal', 'Tracked for your weight trend.', '-');
  }
}

export type TrendDirection = 'rising' | 'falling' | 'stable' | 'insufficient';

export interface VitalTrend {
  direction: TrendDirection;
  /** Signed change from first to latest point in the window (latest − first). */
  change: number;
  /** Plain-language summary, e.g. "Down 6 from 3 readings". */
  summary: string;
}

/**
 * First-vs-latest trend over an already-sorted-ascending series of primary values.
 * `stable` when the change is within `epsilon` (proportional, so it scales across metrics).
 */
export function computeTrend(values: number[]): VitalTrend {
  const pts = values.filter((v) => typeof v === 'number' && isFinite(v));
  if (pts.length < 2) {
    return { direction: 'insufficient', change: 0, summary: 'Add at least two readings to see a trend.' };
  }
  const first = pts[0]!;
  const last = pts[pts.length - 1]!;
  const change = Math.round((last - first) * 100) / 100;
  const epsilon = Math.max(0.5, Math.abs(first) * 0.02); // ≥0.5 abs or 2% of baseline
  let direction: TrendDirection;
  if (Math.abs(change) <= epsilon) direction = 'stable';
  else direction = change > 0 ? 'rising' : 'falling';
  const word = direction === 'stable' ? 'Steady' : direction === 'rising' ? `Up ${Math.abs(change)}` : `Down ${Math.abs(change)}`;
  return { direction, change, summary: `${word} across ${pts.length} readings.` };
}
