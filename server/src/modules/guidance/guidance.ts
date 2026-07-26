/**
 * Deterministic personalization layer: turns a user's conditions, sex, goal and lifestyle
 * (e.g. a sedentary desk job) into concrete diet + exercise guidance. Pure and unit-tested -
 * it never invents numbers and always defers to a clinician for medical conditions.
 */

export interface GuidanceInput {
  sex: string; // 'male' | 'female' | other
  ageYears: number;
  goal: 'lose' | 'maintain' | 'gain';
  activityLevel: string; // sedentary | light | moderate | active | veryactive
  conditions: string[];
  bodyGoal?: string; // fatloss | athletic | muscular
}

export interface Guidance {
  /** Short headline describing what the plan is tuned for. */
  summary: string;
  dietTips: string[];
  exerciseTips: string[];
}

const has = (c: string[], k: string) => c.includes(k);
const isDeskJob = (a: string) => a === 'sedentary' || a === 'light';

export function buildGuidance(input: GuidanceInput): Guidance {
  const { sex, goal, activityLevel, conditions } = input;
  const diet: string[] = [];
  const exercise: string[] = [];
  const tunedFor: string[] = [];

  // ---- Fatty liver (NAFLD) ----
  if (has(conditions, 'fatty_liver')) {
    tunedFor.push('fatty liver');
    diet.push('Fatty liver: cut added sugar and fructose (soft drinks, packaged juice, sweets) - it is the biggest driver of liver fat.');
    diet.push('Swap refined carbs (white rice, maida, white bread) for whole grains, legumes and lots of fibre-rich vegetables.');
    diet.push('Add healthy fats - walnuts, flax, olive oil, fatty fish (omega-3). 2-3 cups of black coffee/day is protective. Avoid alcohol entirely.');
    exercise.push('Fatty liver: 150-200 min/week of moderate cardio (brisk walk, cycling) plus 2-3 resistance sessions - both independently reduce liver fat.');
    exercise.push('Losing even 5-7% of body weight markedly lowers liver fat, so steady consistency matters more than intensity.');
  }

  // ---- PCOS / PCOD ----
  if (has(conditions, 'pcos')) {
    tunedFor.push('PCOS/PCOD');
    diet.push('PCOS: choose low-GI carbs (millets, oats, whole legumes, barley) over white rice, potato and sugar to keep insulin steady.');
    diet.push('Pair protein + fibre at every meal - it blunts sugar spikes and the cravings that come with insulin resistance.');
    diet.push('Lean anti-inflammatory: leafy greens, berries, nuts, seeds, olive oil; limit fried, ultra-processed and sugary foods.');
    exercise.push('PCOS: combine strength training (~3×/week) with moderate cardio - this is the most effective exercise mix for insulin sensitivity and weight/androgen management.');
    exercise.push('Include walking or yoga for stress; avoid chronic over-training, which can raise cortisol and worsen symptoms.');
  }

  // ---- Diabetes / prediabetes ----
  if (has(conditions, 'diabetes')) {
    tunedFor.push('blood sugar');
    diet.push('Diabetes: low-GI carbs spread across meals, plenty of fibre, and protein with each meal. Coordinate carb timing with your medication.');
    exercise.push('Diabetes: a 10-15 min walk after each main meal noticeably blunts glucose spikes. Mix cardio with resistance work.');
  }

  // ---- Blood pressure / heart ----
  if (has(conditions, 'hypertension') || has(conditions, 'heart_disease')) {
    tunedFor.push('heart health');
    diet.push('Blood pressure/heart: DASH-style eating - cut salt and packaged/processed food, and raise potassium (leafy greens, banana, beans).');
    exercise.push('Heart/BP: favour steady aerobic exercise; on strength moves, exhale as you lift and avoid holding your breath under heavy load.');
  }

  // ---- Kidney (supervised) ----
  if (has(conditions, 'kidney_disease')) {
    tunedFor.push('kidney care');
    diet.push('Kidney disease: protein, potassium and phosphorus are limited and must be set with your doctor/renal dietitian - do not self-adjust.');
  }

  // ---- Thyroid ----
  if (has(conditions, 'thyroid')) {
    diet.push('Thyroid: take medication on an empty stomach and keep it away from calcium/iron and high-fibre meals by a few hours. Expect a realistic pace.');
  }

  // ---- Gout ----
  if (has(conditions, 'gout')) {
    diet.push('Gout: limit organ meats, red meat and certain seafood; stay well hydrated and go easy on alcohol and sugary drinks.');
  }

  // ---- Sex-specific nutrition ----
  if (sex === 'female') {
    diet.push('Women: keep iron (leafy greens, dates, legumes, jaggery) and calcium + vitamin-D up - needs are higher, especially around your period.');
  } else if (sex === 'male') {
    diet.push('Men: calorie-dense favourites (fried food, sweets, alcohol) add up fast - lead each meal with protein and vegetables for fullness.');
  }

  // ---- Lifestyle: desk / sitting job ----
  if (isDeskJob(activityLevel)) {
    tunedFor.push('a mostly-sitting day');
    diet.push('Desk job: your daily burn is low, so portion size matters more than "eating clean" - front-load protein at lunch to avoid the 4pm carb crash.');
    exercise.push('Sitting job: stand up and move 3-5 min every 30-60 min - breaking up long sitting is a proven, independent health win.');
    exercise.push('Aim for ~7,000-8,000 steps/day to start; the easiest win is a 10-15 min walk after lunch.');
  } else if (activityLevel === 'active' || activityLevel === 'veryactive') {
    exercise.push('You are already active - prioritise recovery, protein and sleep so training translates into results.');
  }

  // ---- Goal reinforcement ----
  if (goal === 'lose') {
    exercise.push('For fat loss, keep the protein high and lift weights - it protects muscle so the weight you lose is fat, not muscle.');
  } else if (goal === 'gain') {
    exercise.push('For muscle gain, progressively add reps or load each week and eat in a small surplus with ~1.8-2.0 g protein/kg.');
  }

  // ---- Fallbacks so the card is never empty ----
  if (diet.length === 0) {
    diet.push('Build each meal around protein, fibre and vegetables; keep added sugar and fried food occasional. Hydrate well.');
  }
  if (exercise.length === 0) {
    exercise.push('Blend strength training with cardio, progress gradually, and keep 1-2 rest days for recovery.');
  }

  const summary =
    tunedFor.length > 0
      ? `Tuned for ${tunedFor.slice(0, 3).join(', ')}.`
      : 'General healthy-eating and training guidance.';

  // Cap so the card stays scannable.
  return { summary, dietTips: diet.slice(0, 6), exerciseTips: exercise.slice(0, 5) };
}
