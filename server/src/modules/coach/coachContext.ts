import { getDashboard, listFood } from '../logging/logging.service';
import { dietTrend, type DietTrend } from '../logging/dietTrend';
import { foodFrequency, type FoodFrequencyItem } from '../logging/foodFrequency';

/**
 * ONE authoritative "what I know about you right now" object the coach uses on every message.
 * Assembled from services the app already computes; every domain is wrapped in try/catch so a single
 * failure degrades to null and never breaks chat. Numbers here are the deterministic source of truth
 * — the LLM may cite them but must never invent beyond them.
 */

export interface CoachToday {
  kcal: number;
  targetKcal: number | null;
  remainingKcal: number | null;
  proteinG: number;
  targetProteinG: number | null;
  carbG: number;
  fatG: number;
  fiberG: number;
  sugarG: number;
  sodiumMg: number;
  waterMl: number;
  targetWaterMl: number | null;
  burnedKcal: number;
  netKcal: number;
  streakDays: number;
}

export interface CoachTodayFood {
  name: string;
  slot: string;
  grams: number;
  kcal: number;
}

export interface CoachContext {
  today: CoachToday | null;
  todayFoods: CoachTodayFood[] | null;
  week: DietTrend | null;
  month: DietTrend | null;
  /** Top most-frequent foods (all-time), for "you eat X too often" prompts. */
  topFoods: FoodFrequencyItem[] | null;
  /** Full all-time frequency list — used for exact "how many times X" lookups. */
  frequency: FoodFrequencyItem[] | null;
  /** Today's micronutrient deficiencies (labels), if computed. */
  deficiencies: string[] | null;
}

async function safe<T>(fn: () => Promise<T>): Promise<T | null> {
  try {
    return await fn();
  } catch {
    return null;
  }
}

export async function buildCoachContext(userId: string, offsetMin = 0, now: Date = new Date()): Promise<CoachContext> {
  const [dash, foods, week, month, freq] = await Promise.all([
    safe(() => getDashboard(userId, offsetMin, now)),
    safe(() => listFood(userId, now, offsetMin)),
    safe(() => dietTrend(userId, 7, offsetMin, now)),
    safe(() => dietTrend(userId, 30, offsetMin, now)),
    safe(() => foodFrequency(userId, { now })),
  ]);

  let today: CoachToday | null = null;
  let deficiencies: string[] | null = null;
  if (dash) {
    const d = dash as unknown as {
      calories: { consumed: number; target: number | null; remaining: number | null };
      protein: { consumed: number; target: number | null };
      macros: { carbG: number; fatG: number; fiberG: number; sugarG: number; sodiumMg: number };
      water: { consumedMl: number; targetMl: number | null };
      energy: { burnedKcal: number; netKcal: number };
      streakDays: number;
      micronutrients?: { available?: boolean; targets?: { label: string; low?: boolean }[] } | null;
    };
    today = {
      kcal: d.calories.consumed,
      targetKcal: d.calories.target,
      remainingKcal: d.calories.remaining,
      proteinG: d.protein.consumed,
      targetProteinG: d.protein.target,
      carbG: d.macros.carbG,
      fatG: d.macros.fatG,
      fiberG: d.macros.fiberG,
      sugarG: d.macros.sugarG,
      sodiumMg: d.macros.sodiumMg,
      waterMl: d.water.consumedMl,
      targetWaterMl: d.water.targetMl,
      burnedKcal: d.energy.burnedKcal,
      netKcal: d.energy.netKcal,
      streakDays: d.streakDays,
    };
    if (d.micronutrients?.available && d.micronutrients.targets) {
      deficiencies = d.micronutrients.targets.filter((t) => t.low).map((t) => t.label);
    }
  }

  const todayFoods: CoachTodayFood[] | null = foods
    ? (foods as unknown as { foodName?: string; name?: string; mealSlot?: string; slot?: string; grams: number; kcal: number }[]).map((f) => ({
        name: f.foodName ?? f.name ?? 'food',
        slot: f.mealSlot ?? f.slot ?? '',
        grams: Math.round(f.grams),
        kcal: Math.round(f.kcal),
      }))
    : null;

  return {
    today,
    todayFoods,
    week,
    month,
    topFoods: freq ? freq.slice(0, 5) : null,
    frequency: freq,
    deficiencies,
  };
}
