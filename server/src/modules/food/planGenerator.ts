import { round } from '../../calc/anthropometry';
import {
  DayPlan,
  FoodItem,
  Meal,
  MealItem,
  MealSlot,
  MEAL_SLOTS,
  PlanPreferences,
  PlanTargets,
  SLOT_KCAL_WEIGHTS,
  WeekPlan,
} from './food.types';
import { eligibleFoods } from './foodFilter';
import { mealFriendliness } from './friendliness';

const MAIN_SLOTS: MealSlot[] = ['breakfast', 'lunch', 'dinner'];
const MIN_GRAMS = 15;
const MAX_GRAMS = 400;

function proteinDensity(f: FoodItem): number {
  return f.proteinG / Math.max(1, f.kcal);
}

/** Sizes a serving (g) to deliver `kcalTarget`, clamped and rounded to 5 g. */
function gramsForKcal(food: FoodItem, kcalTarget: number): number {
  if (food.kcal <= 0) return food.typicalServingG;
  const grams = (kcalTarget / food.kcal) * 100;
  const clamped = Math.min(MAX_GRAMS, Math.max(MIN_GRAMS, grams));
  return Math.round(clamped / 5) * 5;
}

function toItem(food: FoodItem, grams: number): MealItem {
  const factor = grams / 100;
  return {
    foodId: food.id,
    name: food.name,
    grams,
    kcal: round(food.kcal * factor, 0),
    proteinG: round(food.proteinG * factor, 1),
    carbG: round(food.carbG * factor, 1),
    fatG: round(food.fatG * factor, 1),
    fiberG: round(food.fiberG * factor, 1),
    sugarG: round(food.sugarG * factor, 1),
    sodiumMg: round(food.sodiumMg * factor, 0),
  };
}

/** Candidate foods for a slot, ordered so rotation yields variety and good protein. */
function candidatesForSlot(
  foods: FoodItem[],
  slot: MealSlot,
  dietType: string,
): FoodItem[] {
  const inSlot = foods.filter((f) => f.mealSlots.includes(slot));
  const list = inSlot.length > 0 ? inSlot : foods; // fall back to any eligible food
  const sorted = [...list];
  if (dietType === 'keto' || dietType === 'lowcarb') {
    sorted.sort((a, b) => a.carbG - b.carbG);
  } else {
    sorted.sort((a, b) => proteinDensity(b) - proteinDensity(a));
  }
  return sorted;
}

function pickRotated<T>(list: T[], offset: number): T | undefined {
  if (list.length === 0) return undefined;
  return list[((offset % list.length) + list.length) % list.length];
}

const isGrain = (f: FoodItem) => f.tags.includes('grain');
const isProteinFood = (f: FoodItem) =>
  f.tags.includes('legume') ||
  f.tags.includes('high-protein') ||
  f.category === 'egg' ||
  f.category === 'nonveg' ||
  f.tags.includes('dairy');
const isVegFood = (f: FoodItem) => f.tags.includes('vegetable') || f.tags.includes('salad');

function meal(slot: MealSlot, items: MealItem[]): Meal {
  return {
    slot,
    items,
    kcal: round(items.reduce((s, i) => s + i.kcal, 0), 0),
    proteinG: round(items.reduce((s, i) => s + i.proteinG, 0), 1),
  };
}

/** Foods that suit a light slot (snack/mid-morning/bedtime) — not a heavy curry or meat. */
const LIGHT_SLOT_TAGS = new Set([
  'fruit',
  'beverage',
  'light',
  'probiotic',
  'high-fiber',
  'nuts',
  'snack',
  'salad',
]);

/**
 * Builds a proper Indian plate for a main meal: exactly ONE staple grain (roti OR rice) + a
 * non-grain protein/dal + a vegetable when available. The protein slot NEVER falls back to a
 * grain, so a plate can never be "two grains". `skip(f)` marks foods to avoid if possible
 * (already eaten today, or the dish being swapped out). Shared by generation and swap.
 */
function buildPlate(
  candidates: FoodItem[],
  eligible: FoodItem[],
  slotKcal: number,
  seed: number,
  skip: (f: FoodItem) => boolean,
): MealItem[] {
  const chosen = new Set<string>();
  const items: MealItem[] = [];
  const add = (f: FoodItem | undefined, share: number) => {
    if (f && !chosen.has(f.id)) {
      chosen.add(f.id);
      items.push(toItem(f, gramsForKcal(f, slotKcal * share)));
    }
  };
  const pickFrom = (list: FoodItem[], s: number) => {
    const fresh = list.filter((f) => !chosen.has(f.id) && !skip(f));
    const notInMeal = list.filter((f) => !chosen.has(f.id));
    return pickRotated(fresh.length ? fresh : notInMeal.length ? notInMeal : list, s);
  };
  // Staples/proteins/veg from the slot's candidates, falling back to the whole pool if the slot
  // itself has none (so a plate always gets a base + a dal).
  const pickGroup = (pred: (f: FoodItem) => boolean) => {
    const inSlot = candidates.filter(pred);
    return inSlot.length ? inSlot : eligible.filter(pred);
  };
  const nonGrain = (list: FoodItem[]) => list.filter((f) => !isGrain(f));
  const grains = pickGroup(isGrain);
  const proteins = pickGroup((f) => isProteinFood(f) && !isGrain(f));
  const veggies = pickGroup((f) => isVegFood(f) && !isGrain(f) && !isProteinFood(f));

  // 1) one staple grain
  add(pickFrom(grains.length ? grains : candidates, seed), veggies.length && proteins.length ? 0.45 : 0.55);
  // 2) a dal/protein — strictly NON-grain so we never end up with two grains
  const proteinPool = proteins.length ? proteins : nonGrain(candidates);
  if (proteinPool.length) add(pickFrom(proteinPool, seed + 1), veggies.length ? 0.35 : 0.45);
  // 3) a vegetable when available
  if (veggies.length) add(pickFrom(veggies, seed + 2), 0.2);
  // Safety net: guarantee ≥2 items, preferring a non-grain complement.
  if (items.length < 2) {
    const complement = nonGrain(candidates).filter((f) => !chosen.has(f.id));
    add(pickFrom(complement.length ? complement : candidates, seed + 3), 0.5);
  }
  return items;
}

function buildMeal(
  slot: MealSlot,
  eligible: FoodItem[],
  slotKcal: number,
  dietType: string,
  dayIndex: number,
  usedToday: Set<string>,
): Meal {
  const candidates = candidatesForSlot(eligible, slot, dietType);
  const slotSeed = MEAL_SLOTS.indexOf(slot) * 3 + dayIndex * 2;

  // Main meals = a proper Indian plate. Light slots stay a single, genuinely light item.
  if (MAIN_SLOTS.includes(slot) && candidates.length > 1) {
    const items = buildPlate(candidates, eligible, slotKcal, slotSeed, (f) => usedToday.has(f.id));
    items.forEach((i) => usedToday.add(i.foodId));
    return meal(slot, items);
  }

  // Light slots: prefer genuinely light foods (fruit, buttermilk, nuts) over the top
  // protein-dense pick, and still avoid anything already eaten today.
  const lightFirst = [...candidates].sort((a, b) => {
    const la = a.tags.some((t) => LIGHT_SLOT_TAGS.has(t)) ? 0 : 1;
    const lb = b.tags.some((t) => LIGHT_SLOT_TAGS.has(t)) ? 0 : 1;
    if (la !== lb) return la - lb;
    return a.kcal - b.kcal;
  });
  const only = pickRotated(
    lightFirst.filter((f) => !usedToday.has(f.id)).length
      ? lightFirst.filter((f) => !usedToday.has(f.id))
      : lightFirst,
    slotSeed,
  );
  const items = only ? [toItem(only, gramsForKcal(only, slotKcal))] : [];
  items.forEach((i) => usedToday.add(i.foodId));
  return meal(slot, items);
}

/**
 * Builds a single replacement meal for a slot at (roughly) a target calorie level, avoiding
 * the foods currently used so a "swap" actually changes the dish. `variant` rotates the pick.
 */
export function buildSwapMeal(
  slot: MealSlot,
  eligible: FoodItem[],
  kcalTarget: number,
  dietType: string,
  dayIndex: number,
  avoidIds: string[],
  variant: number,
): Meal {
  const candidates = candidatesForSlot(eligible, slot, dietType);
  const slotSeed = MEAL_SLOTS.indexOf(slot) * 3 + dayIndex * 2 + variant;
  const avoid = new Set(avoidIds);

  // Main meals get a proper plate (staple + dal/protein + veg), avoiding the swapped-out foods
  // so the dish actually changes — and never producing two grains.
  if (MAIN_SLOTS.includes(slot) && candidates.length > 1) {
    const items = buildPlate(candidates, eligible, kcalTarget, slotSeed, (f) => avoid.has(f.id));
    return meal(slot, items);
  }

  const pool = candidates.filter((f) => !avoid.has(f.id));
  const only = pickRotated(pool.length ? pool : candidates, slotSeed);
  return meal(slot, only ? [toItem(only, gramsForKcal(only, kcalTarget))] : []);
}

/** Scales a meal item's grams (and every nutrient, linearly) by `factor`, capped at MAX_GRAMS. */
function scaleItem(it: MealItem, factor: number): void {
  const newGrams = Math.min(MAX_GRAMS, Math.round((it.grams * factor) / 5) * 5);
  const r = it.grams > 0 ? newGrams / it.grams : 1;
  if (r === 1) return;
  it.grams = newGrams;
  it.kcal = round(it.kcal * r, 0);
  it.proteinG = round(it.proteinG * r, 1);
  it.carbG = round(it.carbG * r, 1);
  it.fatG = round(it.fatG * r, 1);
  it.fiberG = round(it.fiberG * r, 1);
  it.sugarG = round(it.sugarG * r, 1);
  it.sodiumMg = round(it.sodiumMg * r, 0);
}

/**
 * Nudges a day's portions so its calories land on `dailyKcal`. Scales substantial items
 * (skips near-zero drinks like plain tea) up by up to +30%, never past MAX_GRAMS per serving,
 * then refreshes each meal's kcal/protein totals. Mutates in place.
 */
function normaliseDayToTarget(meals: Meal[], dailyKcal: number): void {
  const scalable = meals.flatMap((m) => m.items).filter((i) => i.kcal >= 20);
  const rawKcal = scalable.reduce((s, i) => s + i.kcal, 0);
  if (rawKcal <= 0) return;
  const factor = Math.min(1.3, Math.max(1, dailyKcal / rawKcal));
  if (factor <= 1.01) return;
  for (const it of scalable) scaleItem(it, factor);
  for (const m of meals) {
    m.kcal = round(m.items.reduce((s, i) => s + i.kcal, 0), 0);
    m.proteinG = round(m.items.reduce((s, i) => s + i.proteinG, 0), 1);
  }
}

const LIGHT_TAGS = new Set(['fruit', 'beverage', 'light', 'probiotic', 'high-fiber']);

function buildDay(
  dayIndex: number,
  eligible: FoodItem[],
  targets: PlanTargets,
  dietType: string,
  fasting = false,
): DayPlan {
  // Fasting day: fewer, lighter meals at ~40% of calories.
  const slots: MealSlot[] = fasting
    ? (['midmorning', 'lunch', 'eveningsnack'] as MealSlot[])
    : dietType === 'if'
      ? (['lunch', 'eveningsnack', 'dinner'] as MealSlot[])
      : MEAL_SLOTS;

  const dailyKcal = fasting ? Math.round(targets.dailyKcal * 0.4) : targets.dailyKcal;

  // On a fasting day, prefer light foods (fruit, buttermilk, coconut water, khichdi).
  const pool = fasting
    ? (() => {
        const light = eligible.filter(
          (f) => f.kcal < 130 || f.tags.some((t) => LIGHT_TAGS.has(t)),
        );
        return light.length >= 3 ? light : eligible;
      })()
    : eligible;

  // Renormalise slot weights over the active slots so kcal still sums to the target.
  const weightSum = slots.reduce((s, sl) => s + SLOT_KCAL_WEIGHTS[sl], 0);

  // Shared across the day's meals so the same food is never served twice in one day.
  const usedToday = new Set<string>();
  const meals = slots.map((slot) => {
    const slotKcal = (dailyKcal * SLOT_KCAL_WEIGHTS[slot]) / weightSum;
    return buildMeal(slot, pool, slotKcal, dietType, dayIndex, usedToday);
  });

  // Greedy per-slot sizing tends to UNDERSHOOT the target by ~5% (grams clamping + rounding),
  // which makes portions look unrealistically small. Nudge every item up (once) so the day lands
  // on its calorie target. Capped so no single serving blows past MAX_GRAMS.
  normaliseDayToTarget(meals, dailyKcal);

  // Attach condition-friendliness scores per meal (diabetes/heart/gut/inflammation).
  const foodById = new Map(eligible.map((f) => [f.id, f]));
  for (const m of meals) {
    if (m.items.length > 0) m.friendliness = mealFriendliness(m.items, foodById);
  }

  const sum = (pick: (i: MealItem) => number) =>
    meals.reduce((s, m) => s + m.items.reduce((t, i) => t + pick(i), 0), 0);

  return {
    dayIndex,
    meals,
    totals: {
      kcal: round(sum((i) => i.kcal), 0),
      proteinG: round(sum((i) => i.proteinG), 1),
      carbG: round(sum((i) => i.carbG), 1),
      fatG: round(sum((i) => i.fatG), 1),
      fiberG: round(sum((i) => i.fiberG), 1),
      sodiumMg: round(sum((i) => i.sodiumMg), 0),
    },
  };
}

export interface GenerateOptions {
  days?: number;
  /** If given, each day gets a real date + label (Yesterday/Today/Tomorrow/weekday). */
  startDate?: Date;
  /** Reference "today" for labels (defaults to startDate). */
  today?: Date;
  /** Optional weekly fasting day: 0=Sun .. 6=Sat. That day gets a light plan. */
  fastDayOfWeek?: number;
}

/** Label a date relative to today: Yesterday / Today / Tomorrow / weekday name. */
export function dayLabel(date: Date, today: Date): string {
  const a = Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate());
  const b = Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate());
  const diff = Math.round((a - b) / 86_400_000);
  if (diff === 0) return 'Today';
  if (diff === 1) return 'Tomorrow';
  if (diff === -1) return 'Yesterday';
  return WEEKDAYS[date.getUTCDay()]!;
}

const WEEKDAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

/**
 * Deterministic `rules` meal-plan generator. Given targets, the food DB and preferences,
 * it produces a rotating multi-day plan honouring diet type, allergies and conditions.
 * No LLM, no randomness — the same inputs always yield the same plan.
 */
/**
 * Orders foods by a deterministic preference score (lower = preferred): locale match, pantry-on-hand,
 * and budget (prefer cheaper `costTier` when the budget is tight). PURE — buildDay picks from the
 * front of this list, so these signals steer selection without touching the macro-fitting logic.
 */
export function orderByPreference(foods: FoodItem[], prefs: PlanPreferences): FoodItem[] {
  const pantry = new Set((prefs.pantryTags ?? []).map((t) => t.toLowerCase()));
  const budgetWeight = prefs.budgetTier === 'low' ? 3 : prefs.budgetTier === 'medium' ? 1.5 : 0;
  const score = (f: FoodItem): number => {
    let s = 0;
    if (prefs.locale && f.locale === prefs.locale) s -= 4;
    if (pantry.size && (pantry.has(f.name.toLowerCase()) || f.tags.some((t) => pantry.has(t.toLowerCase())))) s -= 8;
    if (budgetWeight) s += (f.costTier - 1) * budgetWeight; // costTier 1 adds 0; pricier foods sink
    return s;
  };
  return foods
    .map((f, i) => ({ f, i, s: score(f) }))
    .sort((a, b) => a.s - b.s || a.i - b.i) // ties keep original order (stable, deterministic)
    .map((x) => x.f);
}

export function generateWeekPlan(
  foods: FoodItem[],
  targets: PlanTargets,
  prefs: PlanPreferences,
  options: GenerateOptions = {},
): WeekPlan {
  const eligible = eligibleFoods(foods, prefs);
  if (eligible.length === 0) {
    throw new Error('No foods match the diet/allergy/condition constraints');
  }
  const ordered = orderByPreference(eligible, prefs);

  const dayCount = options.days ?? 7;
  const days: DayPlan[] = [];
  for (let d = 0; d < dayCount; d++) {
    let fasting = false;
    let dt: Date | undefined;
    if (options.startDate) {
      dt = new Date(options.startDate.getTime() + d * 86_400_000);
      fasting = options.fastDayOfWeek !== undefined && dt.getUTCDay() === options.fastDayOfWeek;
    }
    const day = buildDay(d, ordered, targets, prefs.dietType, fasting);
    if (dt) {
      day.date = dt.toISOString().slice(0, 10);
      const base = dayLabel(dt, options.today ?? options.startDate ?? dt);
      day.label = fasting ? `${base} · Fasting day` : base;
    }
    days.push(day);
  }
  return { days, targets };
}
