import { z } from 'zod';

/** A single performed exercise / set the user is logging. */
export const exerciseLogSchema = z.object({
  exerciseName: z.string().min(1).max(120),
  focus: z.string().max(60).optional(),
  sets: z.number().int().min(0).max(50).optional(),
  reps: z.number().int().min(0).max(1000).optional(),
  weightKg: z.number().min(0).max(1000).optional(),
  /** Reps in reserve (0 = to failure, 4+ = easy). Lets progression judge effort, not just reps. */
  rir: z.number().int().min(0).max(10).optional(),
  durationMin: z.number().int().min(0).max(1000).optional(),
  notes: z.string().max(500).optional(),
  performedAt: z.string().datetime().optional(),
  /** Groups this entry with others logged in the same mixed workout session (client-generated id). */
  sessionId: z.string().max(64).optional(),
  /** Treadmill/walk-run speed in km/h. */
  speedKmh: z.number().min(0).max(60).optional(),
  /** Treadmill incline as a percent grade. */
  inclinePct: z.number().min(0).max(40).optional(),
  /** Distance covered in km. */
  distanceKm: z.number().min(0).max(300).optional(),
});

export type ExerciseLogBody = z.infer<typeof exerciseLogSchema>;
