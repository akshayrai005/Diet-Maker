import { round } from '../calc/anthropometry';
import { Condition, Flag, GuardrailInput, GuardrailResult } from './types';

export * from './types';

/** Approx. energy in 1 kg of body fat. */
export const KCAL_PER_KG_FAT = 7700;
/** Max safe weekly loss as a fraction of current body weight. */
export const MAX_WEEKLY_LOSS_FRACTION = 0.0075;
/** Max deficit as a fraction of TDEE. */
export const MAX_DEFICIT_PCT = 0.25;
/** Calorie floors below which we won't cut unless a clinician authorises it. */
export const CALORIE_FLOOR = { female: 1200, male: 1500 } as const;

/** Extra calories for pregnancy / breastfeeding (simplified, 2nd-3rd trimester style). */
const PREGNANCY_KCAL = 340;
const BREASTFEEDING_KCAL = 450;

const DISCLAIMER: Flag = {
  code: 'DISCLAIMER',
  severity: 'info',
  message: 'Educational guidance, not medical advice - consult a professional.',
};

function has(conditions: Condition[], c: Condition): boolean {
  return conditions.includes(c);
}

/**
 * The single safety gate. Takes raw goal + TDEE + conditions and returns a
 * safety-adjusted calorie/macro target plus the flags that must be surfaced.
 * Deterministic and fully unit-tested - never delegated to an LLM.
 */
export function applyGuardrails(input: GuardrailInput): GuardrailResult {
  const {
    sex,
    ageYears,
    currentWeightKg,
    tdee,
    bmi,
    conditions,
    clinicianOverride = false,
    reducedMobility = false,
  } = input;

  const isMinor = input.isMinor ?? ageYears < 18;
  const flags: Flag[] = [DISCLAIMER];

  const pregnant = has(conditions, 'pregnancy');
  const breastfeeding = has(conditions, 'breastfeeding');

  // Target BMI (needs height) - used to refuse an underweight goal.
  const targetBmi =
    input.heightCm && input.heightCm > 0
      ? input.targetWeightKg / (input.heightCm / 100) ** 2
      : undefined;

  // ---- Whether weight loss is allowed at all ----
  let weightLossBlocked = false;

  // ---- Eating-disorder / underweight safety: never plan loss toward an underweight body ----
  const currentlyUnderweight = bmi < 18.5;
  const targetUnderweight = targetBmi !== undefined && targetBmi < 18.5;

  // Effective direction. If the user left the goal at "maintain" but set a target that clearly
  // differs from their current weight, honour the TARGET — otherwise choosing a lower target did
  // nothing and the projection stayed at the current weight. Safety: never auto-infer a loss toward
  // an underweight target (an explicit lose goal is handled by the block just below instead).
  let goal = input.goal;
  if (goal === 'maintain' && isFinite(input.targetWeightKg) && input.targetWeightKg > 0) {
    const diffKg = input.targetWeightKg - currentWeightKg;
    if (diffKg <= -1 && !targetUnderweight) goal = 'lose';
    else if (diffKg >= 1) goal = 'gain';
  }

  if (goal === 'lose' && (currentlyUnderweight || targetUnderweight)) {
    weightLossBlocked = true;
    flags.push({
      code: 'UNDERWEIGHT_NO_LOSS',
      severity: 'critical',
      message: currentlyUnderweight
        ? 'Your BMI is already in the underweight range, so a weight-loss plan is not safe - this plan targets healthy maintenance instead. If you feel a strong need to lose weight, please talk to a doctor.'
        : 'Your target weight is in the underweight range (BMI < 18.5). For safety this plan targets a healthy weight, not that goal. If losing to that weight matters to you, please discuss it with a doctor first.',
    });
  } else if (targetUnderweight) {
    flags.push({
      code: 'TARGET_UNDERWEIGHT',
      severity: 'warning',
      message: 'Your target weight is below the healthy range (BMI < 18.5) - consider aiming for a weight in the healthy band, and check with a professional.',
    });
  }

  // Cancer: often needs to prevent weight loss (cachexia) - do not plan a deficit.
  if (has(conditions, 'cancer') && goal === 'lose') {
    weightLossBlocked = true;
    flags.push({
      code: 'CANCER_NO_DEFICIT',
      severity: 'critical',
      message: 'With a cancer diagnosis, unplanned weight loss can be harmful - this plan avoids a calorie deficit. Your intake should be guided by your oncology team / dietitian.',
    });
  }
  if (pregnant || breastfeeding) {
    weightLossBlocked = true;
    flags.push({
      code: 'NO_DEFICIT_PREGNANCY',
      severity: 'critical',
      message: pregnant
        ? 'Weight-loss diets are not appropriate during pregnancy - calories increased instead.'
        : 'Weight-loss diets are not appropriate while breastfeeding - calories increased instead.',
    });
  }
  if (isMinor) {
    weightLossBlocked = true;
    flags.push({
      code: 'MINOR_GROWTH',
      severity: ageYears < 15 ? 'critical' : 'warning',
      message:
        'Under-18: these calorie/macro numbers use adult formulas and are NOT valid for a growing child or teen - please use a paediatrician and growth charts for real targets. This plan supports healthy growth, never weight loss.',
    });
  }

  // ---- Calorie target ----
  let dailyKcal: number;
  let plannedWeeklyLossKg = 0;

  if (pregnant || breastfeeding) {
    dailyKcal = tdee + (pregnant ? PREGNANCY_KCAL : 0) + (breastfeeding ? BREASTFEEDING_KCAL : 0);
  } else if (goal === 'gain') {
    // Lean surplus, capped ~0.35% body weight per week.
    const weeklyGain = round(0.0035 * currentWeightKg, 3);
    dailyKcal = round(tdee + (weeklyGain * KCAL_PER_KG_FAT) / 7, 0);
    plannedWeeklyLossKg = -weeklyGain;
  } else if (goal === 'lose' && !weightLossBlocked) {
    const cap = MAX_WEEKLY_LOSS_FRACTION * currentWeightKg;
    let desired = input.desiredWeeklyLossKg ?? cap;
    if (desired > cap) {
      flags.push({
        code: 'WEEKLY_LOSS_CAPPED',
        severity: 'warning',
        message: `Weekly loss capped at ${round(cap, 2)} kg (~0.75% body weight) for safety; timeline extended.`,
      });
      desired = cap;
    }
    let deficit = (desired * KCAL_PER_KG_FAT) / 7;
    const maxDeficit = MAX_DEFICIT_PCT * tdee;
    if (deficit > maxDeficit) {
      deficit = maxDeficit;
      flags.push({
        code: 'DEFICIT_CAPPED',
        severity: 'info',
        message: 'Deficit capped at 25% of maintenance calories; timeline extended.',
      });
    }
    dailyKcal = round(tdee - deficit, 0);
  } else {
    // maintain, or a lose-goal that is blocked -> maintenance
    dailyKcal = tdee;
  }

  // ---- Calorie floor ----
  const floor = CALORIE_FLOOR[sex];
  if (dailyKcal < floor) {
    if (clinicianOverride) {
      flags.push({
        code: 'BELOW_FLOOR_CLINICIAN',
        severity: 'critical',
        message: `Calorie target is below the usual ${floor} kcal floor; permitted only under clinician supervision.`,
      });
    } else {
      flags.push({
        code: 'CALORIE_FLOOR_APPLIED',
        severity: 'warning',
        message: `Calorie target raised to the ${floor} kcal floor; weight-loss timeline extended to stay safe.`,
      });
      dailyKcal = floor;
    }
  }

  // Actual planned weekly change from the final calorie delta.
  const dailyDelta = tdee - dailyKcal; // + = deficit (loss)
  const safeWeeklyDeltaKg = round(-(dailyDelta * 7) / KCAL_PER_KG_FAT, 2);
  void plannedWeeklyLossKg;

  // ---- Macro overrides by condition ----
  let proteinPerKg = 1.8;
  let fatPerKgMin = 0.6;
  let sodiumMaxMg: number | undefined;

  // Muscle-building goal: more protein to support hypertrophy.
  if (goal === 'gain') {
    proteinPerKg = 2.0;
    flags.push({
      code: 'MUSCLE_PROTEIN',
      severity: 'info',
      message: 'Muscle-building goal: protein raised to ~2.0 g/kg. Pair with resistance training 3-5×/week.',
    });
  }

  if (has(conditions, 'pcos')) {
    proteinPerKg = 2.0;
    flags.push({
      code: 'PCOS_LOWGI',
      severity: 'info',
      message: 'PCOS/PCOD: favouring lower-GI carbs and higher protein.',
    });
  }

  if (has(conditions, 'kidney_disease')) {
    proteinPerKg = 0.7; // safe for non-dialysis CKD; overrides any high-protein preference
    flags.push({
      code: 'CKD_PROTEIN_LOWERED',
      severity: 'critical',
      message:
        'Kidney disease: this plan uses ~0.7 g/kg protein, which suits non-dialysis CKD. If you are on DIALYSIS you need MORE protein (~1.0-1.2 g/kg) - do not follow this protein target; your renal dietitian/nephrologist must set it. Potassium, phosphorus and fluid are also restricted per your doctor.',
    });
  }

  if (has(conditions, 'diabetes')) {
    flags.push({
      code: 'DIABETES_LOWGI',
      severity: 'warning',
      message:
        'Diabetes: low-GI carbs across meals, higher fibre. If you take insulin or a sulfonylurea, a calorie deficit + fewer carbs can cause LOW blood sugar - check your levels and adjust medication doses with your doctor before cutting intake.',
    });
  }

  // Fluid restriction: advanced kidney disease and heart failure are often fluid-limited.
  if (has(conditions, 'kidney_disease') || has(conditions, 'heart_disease')) {
    flags.push({
      code: 'FLUID_RESTRICTION',
      severity: 'warning',
      message:
        'The water target shown is a general guide. With kidney disease or heart failure you may be on a FLUID RESTRICTION - follow the daily fluid limit your doctor gave you, not this number.',
    });
  }

  if (has(conditions, 'hypertension')) {
    sodiumMaxMg = 2000;
    flags.push({
      code: 'HTN_SODIUM',
      severity: 'warning',
      message: 'Hypertension: DASH-style plan, sodium limited to ~1500-2000 mg/day.',
    });
  }

  if (has(conditions, 'heart_disease')) {
    sodiumMaxMg = Math.min(sodiumMaxMg ?? Infinity, 1500);
    flags.push({
      code: 'HEART_SATFAT_SODIUM',
      severity: 'warning',
      message: 'Heart disease: saturated/trans fat minimised and sodium limited to ~1500 mg/day.',
    });
  }

  if (has(conditions, 'fatty_liver')) {
    flags.push({
      code: 'FATTY_LIVER_SUGAR',
      severity: 'warning',
      message: 'Fatty liver: added sugar, refined carbs and alcohol cut.',
    });
  }

  if (has(conditions, 'thyroid')) {
    flags.push({
      code: 'THYROID_PACE',
      severity: 'info',
      message: 'Thyroid: realistic pace; take medication per your doctor and separate from certain foods.',
    });
  }

  if (has(conditions, 'gout')) {
    flags.push({
      code: 'GOUT_PURINES',
      severity: 'info',
      message: 'Gout: high-purine foods (organ meats, certain seafood) limited.',
    });
  }

  if (reducedMobility) {
    flags.push({
      code: 'REDUCED_MOBILITY',
      severity: 'info',
      message:
        'Reduced mobility: deficit comes mainly from diet; movement kept low-impact (chair/aquatic/physio).',
    });
  }

  // ---- Drug-nutrient interactions ----
  // We don't store your exact prescriptions, so these are phrased conditionally by the
  // medicines commonly used for each condition. They never change your numbers - they warn
  // about foods that can blunt or dangerously amplify a drug. Always confirm with your doctor.
  if (has(conditions, 'heart_disease')) {
    flags.push({
      code: 'DRUG_WARFARIN_VITK',
      severity: 'warning',
      message:
        'If you take warfarin (a blood thinner): keep your vitamin-K intake STEADY. Big swings in leafy greens (spinach, methi, kale, broccoli) change how the drug works. Don’t suddenly load up or cut them out - keep portions consistent and tell your doctor before big diet changes.',
    });
    flags.push({
      code: 'DRUG_STATIN_GRAPEFRUIT',
      severity: 'info',
      message:
        'If you take a statin or some BP tablets (e.g. amlodipine): avoid grapefruit / grapefruit juice - it can raise the drug level in your blood.',
    });
  }

  if (has(conditions, 'hypertension') || has(conditions, 'kidney_disease')) {
    flags.push({
      code: 'DRUG_POTASSIUM_SUBSTITUTE',
      severity: 'warning',
      message:
        'If you take an ACE inhibitor/ARB (…pril or …sartan) or a potassium-sparing diuretic: do NOT use “low-sodium” salt substitutes - they replace sodium with potassium and can push your potassium dangerously high. Use herbs/spices to cut salt instead.',
    });
  }

  if (has(conditions, 'diabetes')) {
    flags.push({
      code: 'DRUG_METFORMIN_B12',
      severity: 'info',
      message:
        'If you take metformin long-term: it can lower vitamin B12 over the years. Keep B12 sources (dairy, eggs; a supplement if vegetarian/vegan) and ask your doctor to check your B12 periodically.',
    });
  }

  if (has(conditions, 'thyroid')) {
    flags.push({
      code: 'DRUG_LEVOTHYROXINE_TIMING',
      severity: 'warning',
      message:
        'If you take levothyroxine (thyroid tablet): take it on an empty stomach and wait ~30-60 min before eating. Keep it 4 hours apart from calcium, iron, milk, soya and high-fibre meals, and don’t take it with coffee - all of these block its absorption.',
    });
  }

  // ---- Medical-supervision banner ----
  const supervisionReasons: string[] = [];
  if (bmi >= 35) supervisionReasons.push('BMI ≥ 35');
  if (bmi < 17) supervisionReasons.push('BMI < 17');
  if (has(conditions, 'diabetes')) supervisionReasons.push('diabetes');
  if (has(conditions, 'kidney_disease')) supervisionReasons.push('kidney disease');
  if (has(conditions, 'heart_disease')) supervisionReasons.push('heart disease');
  if (pregnant) supervisionReasons.push('pregnancy');
  if (breastfeeding) supervisionReasons.push('breastfeeding');
  if (has(conditions, 'cancer')) supervisionReasons.push('cancer');
  if (has(conditions, 'post_surgery')) supervisionReasons.push('post-surgery recovery');

  const requiresSupervision = supervisionReasons.length > 0;
  if (requiresSupervision) {
    flags.push({
      code: 'REQUIRES_SUPERVISION',
      severity: 'critical',
      message: `Please work with a doctor for this plan (${supervisionReasons.join(', ')}).`,
    });
  }

  return {
    dailyKcal,
    proteinPerKg,
    fatPerKgMin,
    sodiumMaxMg,
    safeWeeklyDeltaKg,
    requiresSupervision,
    weightLossBlocked,
    flags,
  };
}
