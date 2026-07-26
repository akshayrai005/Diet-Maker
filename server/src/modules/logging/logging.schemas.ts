import { z } from 'zod';
import { MEAL_SLOTS } from '../food/food.types';

const slotEnum = z.enum(MEAL_SLOTS as [string, ...string[]]);

const per100gSchema = z.object({
  kcal: z.number().nonnegative(),
  proteinG: z.number().nonnegative().default(0),
  carbG: z.number().nonnegative().default(0),
  fatG: z.number().nonnegative().default(0),
  fiberG: z.number().nonnegative().default(0),
  sugarG: z.number().nonnegative().default(0),
  sodiumMg: z.number().nonnegative().default(0),
});

/**
 * A food-log entry: either reference a Food by id (nutrition scaled from the DB),
 * or supply per-100g values (from a barcode or manual entry). `grams` is required.
 */
export const foodLogSchema = z
  .object({
    mealSlot: slotEnum,
    grams: z.number().positive().max(3000),
    foodId: z.string().optional(),
    foodName: z.string().max(120).optional(),
    per100g: per100gSchema.optional(),
    entryMethod: z.enum(['text', 'barcode', 'photo', 'voice']).default('text'),
    notes: z.string().max(500).optional(),
    mood: z.string().max(40).optional(),
    hunger: z.number().int().min(1).max(5).optional(),
    loggedAt: z.string().datetime().optional(),
  })
  .refine((d) => d.foodId || (d.foodName && d.per100g), {
    message: 'Provide foodId, or foodName + per100g',
  });

export const waterLogSchema = z.object({
  amountMl: z.number().int().positive().max(5000),
  loggedAt: z.string().datetime().optional(),
});

export const checkinSchema = z.object({
  date: z.string().datetime().optional(),
  weightKg: z.number().positive().max(500),
  waistCm: z.number().positive().max(300).optional(),
  chestCm: z.number().positive().max(300).optional(),
  hipCm: z.number().positive().max(300).optional(),
  energy: z.number().int().min(1).max(5).optional(),
  sleepHours: z.number().min(0).max(24).optional(),
  mood: z.number().int().min(1).max(5).optional(),
  stress: z.number().int().min(1).max(5).optional(),
  sleepQuality: z.number().int().min(1).max(5).optional(),
  pain: z.number().int().min(1).max(5).optional(),
  notes: z.string().max(500).optional(),
});

/** A quick Mind-pillar check-in - mood/stress/sleep-quality only, no weigh-in required. */
export const moodCheckinSchema = z.object({
  date: z.string().datetime().optional(),
  mood: z.number().int().min(1).max(5).optional(),
  stress: z.number().int().min(1).max(5).optional(),
  sleepQuality: z.number().int().min(1).max(5).optional(),
  notes: z.string().max(500).optional(),
});

export type FoodLogBody = z.infer<typeof foodLogSchema>;
export type WaterLogBody = z.infer<typeof waterLogSchema>;
export type CheckinBody = z.infer<typeof checkinSchema>;
export type MoodCheckinBody = z.infer<typeof moodCheckinSchema>;
