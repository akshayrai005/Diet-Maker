export type ExerciseLocation = 'gym' | 'home' | 'none';
export type BodyGoal = 'fatloss' | 'athletic' | 'muscular';

/** Self-reported training experience — scales volume and progression ceilings. */
export type FitnessLevel = 'beginner' | 'intermediate' | 'advanced';
/** How hard the user wants to push — scales sets/volume (capped by safety). */
export type IntensityPreference = 'easy' | 'standard' | 'hard' | 'beast';

/** Progressive-overload hint for the next session, derived from the user's logged history. */
export interface NextSession {
  suggestedWeightKg: number | null;
  suggestedReps: number;
  suggestedSets: number;
  deload: boolean;
  rationale: string;
}

export interface ExerciseItem {
  name: string;
  sets: number;
  reps: string; // "8-10", "20 min", "45s"
  type: 'strength' | 'cardio' | 'mobility';
  /** Present once the user has logged this exercise before. */
  nextSession?: NextSession;
  /** Short, safe form cue (e.g. "brace your core, neutral spine"). */
  cue?: string;
  /** Primary muscle group trained (e.g. "chest", "legs"). */
  muscleGroup?: string;
  /** Equipment needed (e.g. "barbell", "bodyweight"). */
  equipment?: string;
}

export interface WorkoutDay {
  dayIndex: number; // 0..6
  date?: string; // YYYY-MM-DD
  label?: string; // Today / Tomorrow / weekday
  focus: string; // "Push (Chest/Shoulders/Triceps)", "Rest & recovery"
  rest: boolean;
  exercises: ExerciseItem[];
}

export interface WeeklyWorkout {
  location: ExerciseLocation;
  goal: BodyGoal;
  days: WorkoutDay[];
  /** 0-based mesocycle block; rotates every 4 weeks so training keeps progressing. */
  block: number;
  blockLabel: string; // "Month 1 · Block A"
  note: string;
  disclaimer: string;
}
