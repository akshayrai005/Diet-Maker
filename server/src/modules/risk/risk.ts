import type { Sex } from '../../calc/types';

/**
 * Deterministic, non-diagnostic health-risk engine. Turns already-collected profile + check-in
 * numbers (waist, BMI, blood pressure, fasting glucose, resting HR, sleep, hydration) into
 * plain-language risk flags — each with WHY, a recommendation and a concrete next action.
 *
 * This is education, not diagnosis: it never names a disease as present, only "higher risk", and
 * always defers to a clinician. Pure & fully unit-tested — never delegated to an LLM.
 */

export type RiskLevel = 'low' | 'moderate' | 'high';

export interface RiskFinding {
  id: string;
  label: string;
  level: RiskLevel;
  /** Why we flagged it — the specific numbers involved. */
  why: string;
  /** What generally helps. */
  recommendation: string;
  /** One concrete next step. */
  nextAction: string;
}

export interface RiskInput {
  sex: Sex;
  ageYears: number;
  bmi: number;
  heightCm: number;
  waistCm?: number;
  /** Blood pressure as "120/80" (mmHg). */
  bloodPressure?: string;
  /** Fasting blood glucose (mg/dL). */
  bloodSugar?: number;
  /** Resting heart rate (bpm). */
  restingHr?: number;
  /** HbA1c (%) — 3-month average glucose. */
  hba1c?: number;
  /** LDL cholesterol (mg/dL). */
  ldl?: number;
  /** HDL cholesterol (mg/dL). */
  hdl?: number;
  /** Triglycerides (mg/dL). */
  triglycerides?: number;
  /** TSH (mIU/L) — thyroid. */
  tsh?: number;
  /** Last night's sleep (hours). */
  sleepHours?: number;
  /** Today's hydration as a % of target (0–100). */
  hydrationPct?: number;
  /** Already-declared conditions (skip a risk we'd otherwise infer). */
  conditions?: string[];
  /** Family history of major conditions (diabetes, heart_disease, hypertension, stroke, cancer, thyroid). */
  familyHistory?: string[];
  /** Smoking status. */
  smoking?: 'no' | 'occasional' | 'regular';
  /** Alcohol use. */
  alcohol?: 'no' | 'occasional' | 'regular';
}

export interface RiskAssessment {
  findings: RiskFinding[];
  /** Overall 0–100 (higher = more flags / more severe). Coarse, for a summary badge. */
  overallScore: number;
  disclaimer: string;
}

const DISCLAIMER =
  'These are educational risk signals from the numbers you entered — not a diagnosis. Confirm anything concerning with a doctor and a proper blood test.';

const has = (conds: string[] | undefined, c: string) => (conds ?? []).includes(c);

/** Parses "120/80" → {systolic, diastolic}; null if unparseable. */
function parseBp(bp?: string): { systolic: number; diastolic: number } | null {
  if (!bp) return null;
  const m = bp.match(/(\d{2,3})\s*[/\\-]\s*(\d{2,3})/);
  if (!m) return null;
  const systolic = Number(m[1]);
  const diastolic = Number(m[2]);
  if (!isFinite(systolic) || !isFinite(diastolic)) return null;
  return { systolic, diastolic };
}

const LEVEL_SCORE: Record<RiskLevel, number> = { low: 0, moderate: 1, high: 2 };

export function assessRisks(input: RiskInput): RiskAssessment {
  const findings: RiskFinding[] = [];

  // --- Central obesity (waist-to-height ratio) — the strongest simple cardiometabolic signal ---
  if (input.waistCm && input.heightCm > 0) {
    const whtr = input.waistCm / input.heightCm;
    if (whtr >= 0.6) {
      findings.push({
        id: 'central_obesity',
        label: 'Central obesity',
        level: 'high',
        why: `Your waist-to-height ratio is ${whtr.toFixed(2)} (waist ${input.waistCm} cm). Above 0.6 signals a lot of visceral fat.`,
        recommendation: 'A modest calorie deficit, more fibre and daily movement shrink waist fat fastest.',
        nextAction: 'Aim to keep your waist under half your height — track it monthly.',
      });
    } else if (whtr >= 0.5) {
      findings.push({
        id: 'central_obesity',
        label: 'Waist creeping up',
        level: 'moderate',
        why: `Your waist-to-height ratio is ${whtr.toFixed(2)} — over the 0.5 "keep waist under half your height" line.`,
        recommendation: 'Prioritise protein + fibre, cut refined carbs, and add a daily walk.',
        nextAction: `Re-measure your waist in 4 weeks and aim for under ${Math.round(input.heightCm / 2)} cm.`,
      });
    }
  }

  // --- Weight status by BMI (Asian-Indian cut-offs) ---
  if (input.bmi >= 25) {
    findings.push({
      id: 'obesity',
      label: 'Obesity range (Asian BMI)',
      level: 'high',
      why: `Your BMI is ${input.bmi}. For South-Asian bodies, ≥ 25 is the obesity threshold (risk rises earlier than the global 30).`,
      recommendation: 'A steady 0.25–0.5 kg/week loss with strength training protects muscle while cutting fat.',
      nextAction: 'Follow your calorie target and re-check weight weekly.',
    });
  } else if (input.bmi >= 23) {
    findings.push({
      id: 'overweight',
      label: 'Overweight range (Asian BMI)',
      level: 'moderate',
      why: `Your BMI is ${input.bmi}. For South-Asians, 23–25 is already "overweight".`,
      recommendation: 'Small, sustainable changes now prevent the slide into higher risk.',
      nextAction: 'Hold a light deficit and keep protein high.',
    });
  }

  // --- Hypertension (measured BP) ---
  const bp = parseBp(input.bloodPressure);
  if (bp && !has(input.conditions, 'hypertension')) {
    if (bp.systolic >= 140 || bp.diastolic >= 90) {
      findings.push({
        id: 'hypertension',
        label: 'High blood pressure',
        level: 'high',
        why: `Your reading ${bp.systolic}/${bp.diastolic} mmHg is in the hypertension range (≥ 140/90).`,
        recommendation: 'Cut salt to ~1500–2000 mg/day (DASH pattern), limit alcohol, move daily.',
        nextAction: 'Get your BP confirmed by a doctor — a single high reading needs verifying.',
      });
    } else if (bp.systolic >= 130 || bp.diastolic >= 85) {
      findings.push({
        id: 'hypertension',
        label: 'Blood pressure elevated',
        level: 'moderate',
        why: `Your reading ${bp.systolic}/${bp.diastolic} mmHg is above the ideal < 120/80.`,
        recommendation: 'Reduce salt and processed food, add potassium-rich vegetables and daily walks.',
        nextAction: 'Recheck BP over a week at the same time of day.',
      });
    }
  }

  // --- Diabetes / prediabetes (fasting glucose) ---
  if (input.bloodSugar && !has(input.conditions, 'diabetes')) {
    if (input.bloodSugar >= 126) {
      findings.push({
        id: 'diabetes',
        label: 'High fasting glucose',
        level: 'high',
        why: `Your fasting glucose ${input.bloodSugar} mg/dL is in the diabetes range (≥ 126).`,
        recommendation: 'Low-GI carbs spread across meals, more fibre, and post-meal walks blunt spikes.',
        nextAction: 'See a doctor for an HbA1c test to confirm.',
      });
    } else if (input.bloodSugar >= 100) {
      findings.push({
        id: 'prediabetes',
        label: 'Prediabetes range',
        level: 'moderate',
        why: `Your fasting glucose ${input.bloodSugar} mg/dL is in the prediabetes range (100–125).`,
        recommendation: 'Losing 5–7% of body weight and daily activity can reverse this stage.',
        nextAction: 'Swap refined carbs for whole grains and walk 10 min after meals.',
      });
    }
  }

  // --- HbA1c (3-month glucose) — sharper than a single fasting reading ---
  if (input.hba1c && !has(input.conditions, 'diabetes')) {
    if (input.hba1c >= 6.5) {
      findings.push({
        id: 'diabetes_hba1c',
        label: 'HbA1c in diabetes range',
        level: 'high',
        why: `Your HbA1c ${input.hba1c}% is in the diabetes range (≥ 6.5%) — that's your average glucose over ~3 months.`,
        recommendation: 'Low-GI carbs, more fibre, weight loss and post-meal walks lower HbA1c over months.',
        nextAction: 'See a doctor to confirm and plan management.',
      });
    } else if (input.hba1c >= 5.7) {
      findings.push({
        id: 'prediabetes_hba1c',
        label: 'HbA1c in prediabetes range',
        level: 'moderate',
        why: `Your HbA1c ${input.hba1c}% is in the prediabetes range (5.7–6.4%).`,
        recommendation: 'Losing 5–7% of body weight and daily activity can bring it back to normal.',
        nextAction: 'Cut refined carbs and add a daily walk; recheck in 3 months.',
      });
    }
  }

  // --- Lipids (LDL / triglycerides / HDL) ---
  if (input.ldl && input.ldl >= 160) {
    findings.push({
      id: 'high_ldl',
      label: input.ldl >= 190 ? 'Very high LDL cholesterol' : 'High LDL cholesterol',
      level: input.ldl >= 190 ? 'high' : 'moderate',
      why: `Your LDL ${input.ldl} mg/dL is ${input.ldl >= 190 ? 'very high (≥ 190)' : 'high (≥ 160)'}.`,
      recommendation: 'Cut saturated fat, add soluble fibre (oats, legumes), unsaturated fats and regular exercise.',
      nextAction: 'Discuss your lipid panel with a doctor.',
    });
  }
  if (input.triglycerides && input.triglycerides >= 200) {
    findings.push({
      id: 'high_triglycerides',
      label: input.triglycerides >= 500 ? 'Very high triglycerides' : 'High triglycerides',
      level: input.triglycerides >= 500 ? 'high' : 'moderate',
      why: `Your triglycerides ${input.triglycerides} mg/dL are ${input.triglycerides >= 500 ? 'very high (≥ 500)' : 'high (≥ 200)'}.`,
      recommendation: 'Reduce refined carbs, sugary drinks and alcohol; add omega-3 and activity.',
      nextAction: 'Have a doctor review it, especially if ≥ 500.',
    });
  }
  if (input.hdl != null && input.hdl < (input.sex === 'female' ? 50 : 40)) {
    findings.push({
      id: 'low_hdl',
      label: 'Low HDL (protective) cholesterol',
      level: 'moderate',
      why: `Your HDL ${input.hdl} mg/dL is below the protective threshold (${input.sex === 'female' ? '50' : '40'} mg/dL).`,
      recommendation: 'Regular exercise, healthy fats (nuts, olive oil, fish) and not smoking raise HDL.',
      nextAction: 'Add 150 min/week of activity and recheck at your next panel.',
    });
  }

  // --- Thyroid (TSH) ---
  if (input.tsh != null && !has(input.conditions, 'thyroid')) {
    if (input.tsh > 4.0) {
      findings.push({
        id: 'high_tsh',
        label: 'TSH above range',
        level: 'moderate',
        why: `Your TSH ${input.tsh} mIU/L is above the usual 0.4–4.0 range — this can suggest an underactive thyroid.`,
        recommendation: 'A doctor interprets TSH alongside symptoms and other thyroid tests.',
        nextAction: 'Ask your doctor whether a full thyroid panel is warranted.',
      });
    } else if (input.tsh < 0.4) {
      findings.push({
        id: 'low_tsh',
        label: 'TSH below range',
        level: 'moderate',
        why: `Your TSH ${input.tsh} mIU/L is below the usual 0.4–4.0 range — this can suggest an overactive thyroid.`,
        recommendation: 'A doctor interprets TSH alongside symptoms and other thyroid tests.',
        nextAction: 'Discuss the result with your doctor.',
      });
    }
  }

  // --- Resting heart rate ---
  if (input.restingHr && input.restingHr > 100) {
    findings.push({
      id: 'high_resting_hr',
      label: 'Elevated resting heart rate',
      level: 'moderate',
      why: `Your resting heart rate is ${input.restingHr} bpm (a healthy adult range is ~60–100).`,
      recommendation: 'Regular cardio lowers resting HR; also check hydration, caffeine, sleep and stress.',
      nextAction: 'If it stays above 100 at rest, mention it to a doctor.',
    });
  }

  // --- Sleep ---
  if (input.sleepHours != null && input.sleepHours < 6.5) {
    findings.push({
      id: 'poor_sleep',
      label: 'Short sleep',
      level: input.sleepHours < 5.5 ? 'high' : 'moderate',
      why: `You logged ${input.sleepHours}h. Under ~7h raises appetite, blood sugar and blood pressure.`,
      recommendation: 'Aim for 7–8h; keep a consistent bedtime and cut screens/caffeine late.',
      nextAction: 'Set a wind-down reminder 30 min before bed tonight.',
    });
  }

  // --- Hydration ---
  if (input.hydrationPct != null && input.hydrationPct < 50) {
    findings.push({
      id: 'low_hydration',
      label: 'Under-hydrated today',
      level: 'moderate',
      why: `You're at ${Math.round(input.hydrationPct)}% of your water goal. Low intake worsens focus, hunger and headaches.`,
      recommendation: 'Front-load water earlier in the day; keep a bottle in sight.',
      nextAction: 'Have a glass of water now.',
    });
  }

  // --- Smoking (its own strong, independent risk) ---
  if (input.smoking === 'regular') {
    findings.push({
      id: 'smoking',
      label: 'Smoking raises your risk',
      level: 'high',
      why: 'Regular smoking is a leading cause of heart disease, stroke and cancer — it multiplies every other risk here.',
      recommendation: 'Quitting is the single highest-impact thing you can do; support and nicotine replacement double success rates.',
      nextAction: 'Ask a doctor or a quit-line about a stop-smoking plan.',
    });
  }

  // --- Family history amplifies cardiometabolic risk (never a diagnosis on its own) ---
  const fh = input.familyHistory ?? [];
  const cardiometabolicFh = fh.filter((h) => ['diabetes', 'heart_disease', 'hypertension', 'stroke'].includes(h));
  if (cardiometabolicFh.length > 0) {
    // Only surface as context if there's at least one other cardiometabolic signal, so it sharpens
    // rather than alarms out of nowhere.
    const hasSignal = findings.some((f) =>
      ['obesity', 'overweight', 'central_obesity', 'hypertension', 'prediabetes', 'diabetes', 'prediabetes_hba1c', 'diabetes_hba1c', 'high_ldl', 'high_triglycerides', 'low_hdl', 'smoking'].includes(f.id),
    );
    if (hasSignal) {
      findings.push({
        id: 'family_history',
        label: 'Family history adds context',
        level: 'moderate',
        why: `You noted a family history of ${cardiometabolicFh.join(', ').replace(/_/g, ' ')}. That raises your baseline cardiometabolic risk alongside the signals above.`,
        recommendation: 'It makes the lifestyle steps above matter more — and earlier, regular screening worthwhile.',
        nextAction: 'Mention your family history to your doctor so screening can start at the right age.',
      });
    }
  }

  const rawScore = findings.reduce((s, f) => s + LEVEL_SCORE[f.level], 0);
  const overallScore = Math.min(100, rawScore * 15);

  // Sort high → moderate → low.
  findings.sort((a, b) => LEVEL_SCORE[b.level] - LEVEL_SCORE[a.level]);

  return { findings, overallScore, disclaimer: DISCLAIMER };
}
