/**
 * "Different people have different problems" - a GENERAL mobility-staging gate, not a rule for
 * any one diagnosis (knee pain, obesity, cardiac caution, elderly, post-injury - all reported the
 * same way: `reducedMobility: true`). PURE and deterministic.
 *
 * The real-world gap this closes: reportedly "reducedMobility" used to only turn workout VOLUME
 * down on the same bodyweight-squat/lunge template - never actually safe for someone whose joints
 * can't take that template at all, regardless of how few sets. This keys off BMI (a generic,
 * defensible proxy for how much load standing/joint-impact work puts on someone, not a diagnosis)
 * to decide whether exercise should even involve standing/joint-loading movement yet, or whether
 * diet should carry the plan alone until the body is in a safer range to add real movement.
 *
 * This is intentionally conservative and general - it does NOT try to model individual conditions
 * (a doctor should for anything specific); it just stops handing joint-loading exercise to anyone
 * who said they have a mobility limitation AND is at a weight where that's genuinely risky.
 */

export type MovementStage = 'diet_first' | 'light_movement' | 'full';

export interface StagingInput {
  /** From the profile - true covers ANY reported mobility limitation, not one specific condition. */
  reducedMobility: boolean;
  heightCm: number;
  currentWeightKg: number;
}

export interface StagingResult {
  stage: MovementStage;
  bmi: number;
  reason: string;
  /** Only set when staged - the rough weight to re-check in, since BMI bands are weight-driven. */
  resumeAroundWeightKg?: number;
}

const round1 = (n: number) => Math.round(n * 10) / 10;

export function determineMovementStage(input: StagingInput): StagingResult {
  const heightM = input.heightCm / 100;
  const bmi = heightM > 0 && input.currentWeightKg > 0 ? round1(input.currentWeightKg / (heightM * heightM)) : 0;

  if (!input.reducedMobility) {
    return { stage: 'full', bmi, reason: 'No mobility limitation reported - standard training applies.' };
  }
  if (bmi === 0) {
    return { stage: 'light_movement', bmi, reason: 'Mobility limitation reported - keeping movement gentle and low-impact by default.' };
  }
  if (bmi >= 30) {
    // Back-calculate the weight where BMI would hit 30, as a concrete "check back in around X kg" figure.
    const resumeAroundWeightKg = round1(30 * heightM * heightM);
    return {
      stage: 'diet_first',
      bmi,
      reason: 'Reported mobility limitation + higher current weight - prioritising diet alone for now. Joint-loading exercise (squats, lunges, standing impact work) is not safe to push here; only seated/zero-impact movement is included.',
      resumeAroundWeightKg,
    };
  }
  if (bmi >= 25) {
    return {
      stage: 'light_movement',
      bmi,
      reason: 'Getting closer to a safer range - gentle, low-impact movement only (no squats/lunges/jumping) while diet continues to do most of the work.',
    };
  }
  return {
    stage: 'full',
    bmi,
    reason: 'Weight is in a range where standard training is reasonable - still ease in and stop if anything hurts.',
  };
}
