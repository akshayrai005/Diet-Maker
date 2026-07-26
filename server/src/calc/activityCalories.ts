/**
 * Deterministic MET-based calorie estimates for logged activity - PURE.
 *
 * kcal = MET × bodyWeightKg × durationHours, using standard compendium MET
 * approximations. No DB, no I/O, no Date.now, no randomness. The result is an
 * educational estimate to connect movement to the calorie picture - not a
 * measured value. When body weight is unknown a neutral default is used so the
 * estimate degrades gracefully rather than returning zero.
 */

const DEFAULT_BODY_WEIGHT_KG = 70;

// Keyword → intensity. Cardio burns more than resistance work, which burns more
// than mobility/stretch/isometric holds.
const CARDIO_RE =
  /run|jog|sprint|jump|burpee|skater|hiit|cycl|bike|row|skip|mountain\s*climber|jumping\s*jack|cardio|treadmill|elliptical/i;
const MOBILITY_RE = /stretch|mobility|yoga|foam|cool\s*down|warm\s*up|plank|hold|breath/i;

/** Best-effort MET for an exercise from its name. */
export function exerciseMet(name: string): number {
  if (CARDIO_RE.test(name)) return 8;
  if (MOBILITY_RE.test(name)) return 2.8;
  return 5; // general resistance training
}

export interface ExerciseKcalInput {
  name: string;
  sets?: number | null;
  reps?: number | null;
  weightKg?: number | null;
  durationMin?: number | null;
  bodyWeightKg?: number | null;
}

/** Minutes for an entry: use the logged duration, else estimate from set count. */
function estimateMinutes(input: ExerciseKcalInput): number {
  if (input.durationMin && input.durationMin > 0) return input.durationMin;
  const sets = input.sets && input.sets > 0 ? input.sets : 3;
  // ~50s work + ~60s rest per set ≈ 1.8 min/set; clamp to a sane band.
  return Math.min(60, Math.max(3, Math.round(sets * 1.8)));
}

/** Estimated calories burned for a single logged exercise entry. */
export function exerciseKcal(input: ExerciseKcalInput): number {
  const bw = input.bodyWeightKg && input.bodyWeightKg > 0 ? input.bodyWeightKg : DEFAULT_BODY_WEIGHT_KG;
  const met = exerciseMet(input.name);
  const minutes = estimateMinutes(input);
  return Math.max(0, Math.round((met * bw * minutes) / 60));
}

export type WellnessType = 'yoga' | 'meditation' | 'breathing';

const WELLNESS_MET: Record<WellnessType, number> = {
  yoga: 2.5,
  meditation: 1.3,
  breathing: 1.3,
};

/** Estimated calories burned for a completed wellness session. */
export function wellnessKcal(type: WellnessType, minutes: number, bodyWeightKg?: number | null): number {
  const bw = bodyWeightKg && bodyWeightKg > 0 ? bodyWeightKg : DEFAULT_BODY_WEIGHT_KG;
  const met = WELLNESS_MET[type] ?? WELLNESS_MET.meditation;
  const mins = minutes > 0 ? minutes : 0;
  return Math.max(0, Math.round((met * bw * mins) / 60));
}
