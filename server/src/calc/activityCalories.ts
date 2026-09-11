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
const WALK_RUN_RE = /walk|run|jog|sprint|treadmill|hike/i;
const CYCLE_RE = /cycl|bike|spin/i;

/**
 * Speed+incline-aware MET for walking/running, via the standard ACSM metabolic equation
 * (VO2 = speed term + grade term + resting). Running kicks in above ~6.4 km/h (~4 mph), matching
 * where ACSM switches equations. Incline meaningfully raises the estimate - climbing at 3 km/h and
 * 10% grade burns far more than flat walking at the same speed, unlike a flat per-exercise MET.
 */
function walkRunMet(speedKmh: number, inclinePct: number): number {
  const speedMPerMin = (speedKmh * 1000) / 60;
  const grade = Math.max(0, inclinePct) / 100;
  const running = speedKmh >= 6.4;
  const vo2 = running
    ? 0.2 * speedMPerMin + 0.9 * speedMPerMin * grade + 3.5
    : 0.1 * speedMPerMin + 1.8 * speedMPerMin * grade + 3.5;
  return Math.max(1.5, vo2 / 3.5);
}

/** Speed-banded MET for cycling (compendium-style lookup - grade not modeled for cycling). */
function cyclingMet(speedKmh: number): number {
  if (speedKmh < 16) return 4;
  if (speedKmh < 19) return 6;
  if (speedKmh < 22.5) return 8;
  if (speedKmh < 25.5) return 10;
  if (speedKmh < 30.5) return 12;
  return 15.8;
}

/** Best-effort MET for an exercise from its name, refined by speed/incline when logged. */
export function exerciseMet(name: string, speedKmh?: number | null, inclinePct?: number | null): number {
  if (speedKmh && speedKmh > 0) {
    if (WALK_RUN_RE.test(name)) return walkRunMet(speedKmh, inclinePct ?? 0);
    if (CYCLE_RE.test(name)) return cyclingMet(speedKmh);
  }
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
  /** Treadmill/outdoor walk-run speed in km/h - sharpens the MET estimate when known. */
  speedKmh?: number | null;
  /** Treadmill incline as a percent grade (e.g. 8 = 8%) - raises the estimate for uphill work. */
  inclinePct?: number | null;
  /** Distance covered in km - used to back out speed when duration is logged but speed isn't. */
  distanceKm?: number | null;
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
  const minutes = estimateMinutes(input);
  // Back out speed from distance+duration when speed itself wasn't logged.
  const speedKmh = input.speedKmh && input.speedKmh > 0
    ? input.speedKmh
    : input.distanceKm && input.distanceKm > 0 && minutes > 0
      ? (input.distanceKm / minutes) * 60
      : undefined;
  const met = exerciseMet(input.name, speedKmh, input.inclinePct);
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
