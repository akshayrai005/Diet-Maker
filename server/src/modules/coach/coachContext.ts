import { prisma } from '../../lib/prisma';
import { decryptJson } from '../../lib/crypto';
import { getDashboard, listFood, getMoodInsight } from '../logging/logging.service';
import { dietTrend, type DietTrend } from '../logging/dietTrend';
import { foodFrequency, type FoodFrequencyItem } from '../logging/foodFrequency';
import { gatherUserContext } from '../rating/context';
import { getCycle } from '../cycle/cycle.service';
import { listMedications } from '../medications/medications.service';
import { isPgMode } from '../food/prep';
import type { PrepLevel } from '../food/food.types';

/**
 * ONE authoritative "what I know about you right now" object the coach uses on every message.
 * Assembled from services the app already computes; every domain is wrapped in try/catch so a single
 * failure degrades to null and never breaks chat. Numbers here are the deterministic source of truth
 * — the LLM may cite them but must never invent beyond them. Sensitive domains (medications, cycle)
 * are included ONLY to drive the deterministic food scorer and are never surfaced to the LLM prompt.
 */

export interface CoachToday {
  kcal: number;
  targetKcal: number | null;
  remainingKcal: number | null;
  proteinG: number;
  targetProteinG: number | null;
  remainingProteinG: number | null;
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

export interface CoachExercise {
  todayScheduled: boolean;
  todayDone: boolean;
  todayFocus: string | null;
  rest: boolean;
  weeklyWorkouts: number;
  overloadTrend: 'up' | 'flat' | 'down';
}

export interface CoachMind {
  mood: number | null;
  stress: number | null;
  sleepQuality: number | null;
  insight: string | null;
}

export interface CoachContext {
  today: CoachToday | null;
  todayFoods: CoachTodayFood[] | null;
  week: DietTrend | null;
  month: DietTrend | null;
  topFoods: FoodFrequencyItem[] | null;
  frequency: FoodFrequencyItem[] | null;
  deficiencies: string[] | null;

  // GAP 3 — kitchen / PG awareness (from profile)
  kitchen: PrepLevel | null;
  livingSituation: string | null;
  availableFoodIds: string[] | null;
  pgMode: boolean;

  // Diet profile the food scorer needs
  conditions: string[];
  allergies: string[];
  budgetTier: 'low' | 'medium' | 'flexible' | null;
  pantryTags: string[] | null;

  // GAP 4 — exercise
  exercise: CoachExercise | null;

  // GAP 5 — weight + mind
  weightKg: number | null;
  weightDeltaKg: number | null;
  mind: CoachMind | null;

  // GAP 6 — meds + cycle (drive the scorer only; NEVER sent to the LLM prompt)
  medications: string[];
  cyclePhase: 'menstrual' | 'follicular' | 'ovulatory' | 'luteal' | null;
}

async function safe<T>(fn: () => Promise<T>): Promise<T | null> {
  try {
    return await fn();
  } catch {
    return null;
  }
}

interface SensitiveDietBits {
  conditions?: string[];
  allergies?: string[];
  budgetTier?: 'low' | 'medium' | 'flexible';
  pantryTags?: string[];
  kitchen?: PrepLevel;
  livingSituation?: string;
  availableFoodIds?: string[];
}

export async function buildCoachContext(userId: string, offsetMin = 0, now: Date = new Date()): Promise<CoachContext> {
  const [dash, foods, week, month, freq, userCtx, profile, cycle, meds, mind] = await Promise.all([
    safe(() => getDashboard(userId, offsetMin, now)),
    safe(() => listFood(userId, now, offsetMin)),
    safe(() => dietTrend(userId, 7, offsetMin, now)),
    safe(() => dietTrend(userId, 30, offsetMin, now)),
    safe(() => foodFrequency(userId, { now })),
    safe(() => gatherUserContext(userId, offsetMin, now)),
    safe(() => prisma.profile.findUnique({ where: { userId } })),
    safe(() => getCycle(userId, now)),
    safe(() => listMedications(userId, offsetMin)),
    safe(() => getMoodInsight(userId)),
  ]);

  // --- Diet profile from the encrypted sensitive blob ---
  let bits: SensitiveDietBits = {};
  if (profile?.sensitiveEnc) {
    try {
      bits = decryptJson<SensitiveDietBits>(profile.sensitiveEnc);
    } catch {
      bits = {};
    }
  }
  const kitchen = bits.kitchen ?? null;
  const livingSituation = bits.livingSituation ?? null;
  const availableFoodIds = bits.availableFoodIds && bits.availableFoodIds.length ? bits.availableFoodIds : null;

  // --- Today ---
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
      remainingProteinG: d.protein.target != null ? Math.round(d.protein.target - d.protein.consumed) : null,
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

  // --- Exercise (GAP 4) ---
  const exercise: CoachExercise | null = userCtx
    ? {
        todayScheduled: userCtx.todayWorkoutScheduled,
        todayDone: userCtx.todayWorkoutDone,
        todayFocus: userCtx.todayFocus,
        rest: userCtx.rest,
        weeklyWorkouts: userCtx.weeklyWorkoutsCompleted,
        overloadTrend: userCtx.overloadTrend,
      }
    : null;

  // --- Mind (GAP 5), gated on having any wellness check-in signal ---
  let mindBlock: CoachMind | null = null;
  if (mind && mind.series.length > 0) {
    const last = mind.series[mind.series.length - 1]!;
    mindBlock = { mood: last.mood ?? null, stress: last.stress ?? null, sleepQuality: last.sleepQuality ?? null, insight: mind.insight?.message ?? null };
  }

  // --- Cycle phase (GAP 6), only when tracked; map 'ovulation' → scorer's 'ovulatory' ---
  let cyclePhase: CoachContext['cyclePhase'] = null;
  const c = cycle as { applicable?: boolean; needsSetup?: boolean; phase?: string } | null;
  if (c?.applicable && !c.needsSetup && c.phase) {
    cyclePhase = c.phase === 'ovulation' ? 'ovulatory' : (c.phase as CoachContext['cyclePhase']);
  }

  // --- Active medication names (GAP 6), scorer-only ---
  const medications: string[] = meds?.medications
    ? meds.medications.filter((m) => m.active).map((m) => m.name).filter(Boolean)
    : [];

  return {
    today,
    todayFoods,
    week,
    month,
    topFoods: freq ? freq.slice(0, 5) : null,
    frequency: freq,
    deficiencies,

    kitchen,
    livingSituation,
    availableFoodIds,
    pgMode: isPgMode({ livingSituation: livingSituation ?? undefined, kitchen: kitchen ?? undefined }),

    conditions: bits.conditions ?? [],
    allergies: bits.allergies ?? [],
    budgetTier: bits.budgetTier ?? null,
    pantryTags: bits.pantryTags && bits.pantryTags.length ? bits.pantryTags : null,

    exercise,

    weightKg: userCtx?.latestWeightKg ?? null,
    weightDeltaKg: userCtx?.weightDeltaKg ?? null,
    mind: mindBlock,

    medications,
    cyclePhase,
  };
}
