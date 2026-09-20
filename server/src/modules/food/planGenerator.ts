import { round } from '../../calc/anthropometry';
import {
  DayPlan,
  FoodItem,
  Meal,
  MealItem,
  MealSlot,
  MEAL_SLOTS,
  FASTING_KCAL_FACTOR,
  PlanPreferences,
  PlanTargets,
  SLOT_KCAL_WEIGHTS,
  WeekPlan,
  type TrainTime,
} from './food.types';
import { eligibleFoods } from './foodFilter';
import { mealFriendliness } from './friendliness';

const MAIN_SLOTS: MealSlot[] = ['breakfast', 'lunch', 'dinner'];
const MIN_GRAMS = 15;
const MAX_GRAMS = 400;

function proteinDensity(f: FoodItem): number {
  return f.proteinG / Math.max(1, f.kcal);
}

/** Realistic upper bound for one item: about 2x a normal serving (never below 90 g, never above MAX_GRAMS). */
function capFor(food: FoodItem): number {
  return Math.max(90, Math.min(MAX_GRAMS, Math.round(food.typicalServingG * 2)));
}
/** Per-item serving cap, remembered when the item is built so later scaling/balancing can respect it. */
const itemCaps = new WeakMap<MealItem, number>();

/** Sizes a serving (g) to deliver `kcalTarget`, clamped and rounded to 5 g. */
function gramsForKcal(food: FoodItem, kcalTarget: number): number {
  if (food.kcal <= 0) return food.typicalServingG;
  const grams = (kcalTarget / food.kcal) * 100;
  const clamped = Math.min(capFor(food), Math.max(MIN_GRAMS, grams));
  return Math.round(clamped / 5) * 5;
}

function toItem(food: FoodItem, grams: number): MealItem {
  const factor = grams / 100;
  const item: MealItem = {
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
  itemCaps.set(item, capFor(food));
  return item;
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

/** Foods that suit a light slot (snack/mid-morning/bedtime) - not a heavy curry or meat. */
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
  slot: MealSlot,
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
    // Never double up a protein family on one plate (Rajma chawal + Rajma, two chickens...).
    const famTaken = (f: FoodItem) => {
      const fam = proteinFamily(f.name);
      return !!fam && items.some((i) => proteinFamily(i.name) === fam);
    };
    const fresh = list.filter((f) => !chosen.has(f.id) && !skip(f) && !famTaken(f));
    const notInMeal = list.filter((f) => !chosen.has(f.id) && !famTaken(f));
    return pickRotated(fresh.length ? fresh : notInMeal.length ? notInMeal : list, s);
  };
  // Staples/proteins/veg from the slot's candidates, falling back to the whole pool if the slot
  // itself has none (so a plate always gets a base + a dal).
  const pickGroup = (pred: (f: FoodItem) => boolean) => {
    const inSlot = candidates.filter(pred);
    // Only lunch/dinner may borrow from the whole pool. Breakfast must never pull in a dinner curry.
    return inSlot.length ? inSlot : slot === 'breakfast' ? [] : eligible.filter(pred);
  };
  const nonGrain = (list: FoodItem[]) => list.filter((f) => !isGrain(f));
  const grains = pickGroup(isGrain);
  const proteins = pickGroup((f) => isProteinFood(f) && !isGrain(f));
  const veggies = pickGroup((f) => isVegFood(f) && !isGrain(f) && !isProteinFood(f));

  // 1) one staple grain
  add(pickFrom(grains.length ? grains : candidates, seed), veggies.length && proteins.length ? 0.45 : 0.55);
  // 2) a dal/protein - strictly NON-grain so we never end up with two grains
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

/** Coarse protein family of a dish name, so one meal never serves two chickens or soya + chicken. 'dal' is the only family allowed alongside another. */
function proteinFamily(name: string): string | null {
  const n = name.toLowerCase();
  if (/chicken/.test(n)) return 'chicken';
  if (/(^|[^a-z])egg|omelette|omelet/.test(n)) return 'egg';
  if (/fish|prawn|shrimp|salmon|tuna|rohu|surmai|pomfret/.test(n)) return 'fish';
  if (/mutton|lamb|keema|goat/.test(n)) return 'mutton';
  if (/soya|soy |tofu|nutrela/.test(n)) return 'soya';
  if (/paneer|cottage cheese/.test(n)) return 'paneer';
  if (/dal|lentil|moong|masoor|toor|arhar|urad|rajma|chole|chana|kidney bean|sprout/.test(n)) return 'dal';
  return null;
}
/** True when adding `name` would double up a protein in this meal (two chickens, soya + chicken, ...). */
function clashesWithMeal(m: Meal, name: string): boolean {
  const fam = proteinFamily(name);
  if (!fam) return false;
  const have = m.items.map((i) => proteinFamily(i.name)).filter((f): f is string => !!f);
  if (have.includes(fam)) return true;
  if (fam === 'dal') return false;
  return have.some((f) => f !== 'dal'); // already has a non-dal main protein
}

/** Cooking ingredients / condiments - never a standalone snack. */
const NOT_A_SNACK = /\b(onion|garlic|ginger|chilli|chili|lemon|lime|salt|oil|masala|turmeric|coriander|curry leaf)\b/i;

/** Light-slot portion: sized to the slot but never more than a realistic single serving (no 400 g of raw onion). */
function lightGrams(food: FoodItem, kcalTarget: number): number {
  const cap = Math.max(100, Math.min(MAX_GRAMS, food.typicalServingG * 2));
  // A snack is a snack: never more than ~250 kcal (the day's top-up puts any shortfall in main meals).
  return Math.min(cap, gramsForKcal(food, Math.min(kcalTarget, 250)));
}

function buildMeal(
  slot: MealSlot,
  eligible: FoodItem[],
  slotKcal: number,
  dietType: string,
  dayIndex: number,
  usedToday: Set<string>,
  mains: MealSlot[] = MAIN_SLOTS,
): Meal {
  const candidates = candidatesForSlot(eligible, slot, dietType);
  const slotSeed = MEAL_SLOTS.indexOf(slot) * 3 + dayIndex * 2;

  // Main meals = a proper Indian plate. Light slots stay a single, genuinely light item.
  if (mains.includes(slot) && candidates.length > 1) {
    // A promoted evening meal (morning+night pattern) is built like a lunch plate, from lunch-type dishes -
    // its own "snack" foods (chai, shakes, yoghurt) made a jumble that under-filled the meal.
    const plateSlot: MealSlot = slot === 'eveningsnack' ? 'lunch' : slot;
    const plateCandidates = slot === 'eveningsnack' ? candidatesForSlot(eligible, 'lunch', dietType) : candidates;
    const items = buildPlate(plateSlot, plateCandidates.length > 1 ? plateCandidates : candidates, eligible, slotKcal, slotSeed, (f) => usedToday.has(f.id));
    items.forEach((i) => usedToday.add(i.foodId));
    return meal(slot, items);
  }

  // Light slots: prefer genuinely light foods (fruit, buttermilk, nuts) over the top
  // protein-dense pick, and still avoid anything already eaten today.
  const snackable = candidates.filter((f) => !NOT_A_SNACK.test(f.name));
  const lightFirst = [...(snackable.length ? snackable : candidates)].sort((a, b) => {
    const la = a.tags.some((t) => LIGHT_SLOT_TAGS.has(t)) ? 0 : 1;
    const lb = b.tags.some((t) => LIGHT_SLOT_TAGS.has(t)) ? 0 : 1;
    if (la !== lb) return la - lb;
    // Fat-dense foods (nuts, seeds, ghee-heavy) go last: they blow the day's fat target far past the
    // dashboard's (e.g. 87 g planned vs 58 g target from one 120 g peanut snack).
    const fatShare = (f: FoodItem) => (f.fatG * 9) / Math.max(1, f.kcal);
    const fa = fatShare(a) > 0.5 ? 1 : 0;
    const fb = fatShare(b) > 0.5 ? 1 : 0;
    if (fa !== fb) return fa - fb;
    return a.kcal - b.kcal;
  });
  const only = pickRotated(
    lightFirst.filter((f) => !usedToday.has(f.id)).length
      ? lightFirst.filter((f) => !usedToday.has(f.id))
      : lightFirst,
    slotSeed,
  );
  const items = only ? [toItem(only, gramsForKcal(only, Math.min(slotKcal, 300)))] : [];
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
  // so the dish actually changes - and never producing two grains.
  if (MAIN_SLOTS.includes(slot) && candidates.length > 1) {
    const items = buildPlate(slot, candidates, eligible, kcalTarget, slotSeed, (f) => avoid.has(f.id));
    return meal(slot, items);
  }

  const pool = candidates.filter((f) => !avoid.has(f.id));
  const only = pickRotated(pool.length ? pool : candidates, slotSeed);
  return meal(slot, only ? [toItem(only, lightGrams(only, kcalTarget))] : []);
}

/** Scales a meal item's grams (and every nutrient, linearly) by `factor`, capped at MAX_GRAMS. */
function scaleItem(it: MealItem, factor: number, ignoreCap = false): void {
  const cap = ignoreCap ? MAX_GRAMS : (itemCaps.get(it) ?? MAX_GRAMS);
  // Never shrink below the current size when the item is already over its cap (so a scale-up can never grow it).
  const newGrams = Math.min(Math.max(cap, factor < 1 ? 0 : it.grams), Math.round((it.grams * factor) / 5) * 5);
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
function normaliseDayToTarget(meals: Meal[], dailyKcal: number, mains: MealSlot[] = MAIN_SLOTS): void {
  // Snacks stay snacks: only main meals get scaled up.
  const scalable = meals.filter((m) => mains.includes(m.slot)).flatMap((m) => m.items).filter((i) => i.kcal >= 20);
  const rawKcal = scalable.reduce((s, i) => s + i.kcal, 0);
  if (rawKcal <= 0) return;
  // Snacks are not scaled, so the main meals only have to cover what the snacks leave of the day.
  const fixedKcal = meals.reduce((t, m) => t + m.kcal, 0) - meals.filter((m) => mains.includes(m.slot)).reduce((t, m) => t + m.kcal, 0);
  const factor = Math.min(1.3, Math.max(1, (dailyKcal - fixedKcal) / rawKcal));
  if (factor <= 1.01) return;
  for (const it of scalable) scaleItem(it, factor);
  for (const m of meals) {
    m.kcal = round(m.items.reduce((s, i) => s + i.kcal, 0), 0);
    m.proteinG = round(m.items.reduce((s, i) => s + i.proteinG, 0), 1);
  }
}

/**
 * When the day is still well short on protein after portion-shifting, add ONE lean protein food to
 * lunch or dinner and pay for it by trimming that meal's biggest carb item (calorie-neutral).
 */
function proteinTopUp(meals: Meal[], targets: { proteinG: number }, pool: FoodItem[], usedToday: Set<string>): void {
  const total = () => meals.reduce((t, m) => t + m.items.reduce((u, i) => u + i.proteinG, 0), 0);
  const shortfall = targets.proteinG - total();
  if (shortfall < targets.proteinG * 0.12) return;
  // Lunch first (dinner is kept light, especially after the gym); never stack a second main protein on a plate.
  const order: Record<string, number> = { lunch: 0, eveningsnack: 1, dinner: 2 };
  const mainMeal = meals.filter((m) => m.slot in order && m.items.length > (m.slot === 'eveningsnack' ? 1 : 0)).sort((a, b) => order[a.slot]! - order[b.slot]!)[0];
  if (!mainMeal) return;
  const lean = pool
    .filter((f) => !usedToday.has(f.id) && f.proteinG >= 8 && f.fatG / Math.max(1, f.kcal) < 0.11 && f.mealSlots.includes(mainMeal.slot) && isProteinFood(f) && !isGrain(f) && !clashesWithMeal(mainMeal, f.name))
    .sort((a, b) => b.proteinG / Math.max(1, b.kcal) - a.proteinG / Math.max(1, a.kcal))[0];
  if (!lean) return;
  const wantProtein = Math.min(shortfall * 0.8, 40);
  const grams = Math.min(250, Math.max(MIN_GRAMS, Math.round(((wantProtein / lean.proteinG) * 100) / 5) * 5));
  const item = toItem(lean, grams);
  const carb = [...mainMeal.items].sort((a, b) => b.carbG - a.carbG)[0];
  if (!carb || carb.kcal <= item.kcal + 80) return;
  scaleItem(carb, (carb.kcal - item.kcal) / carb.kcal);
  mainMeal.items.push(item);
  usedToday.add(lean.id);
}

/**
 * Pulls the day's FAT and PROTEIN toward the dashboard targets (the calorie engine's numbers) by
 * shrinking the item that overshoots most and putting its calories back onto the lowest-fat,
 * lowest-protein item in the day (a grain/veg). Bounded and calorie-neutral, so kcal stays on target.
 * Mutates `meals`. PURE besides that.
 */
function balanceMacros(meals: Meal[], targets: { proteinG: number; fatG: number }): void {
  const items = () => meals.flatMap((m) => m.items);
  const total = (pick: (i: MealItem) => number) => items().reduce((t, i) => t + pick(i), 0);
  const roomFor = (i: MealItem) => i.grams < (itemCaps.get(i) ?? MAX_GRAMS) - 10;
  const dens = (i: MealItem) => i.proteinG / Math.max(1, i.kcal);
  for (let pass = 0; pass < 8; pass++) {
    const fat = total((i) => i.fatG);
    const protein = total((i) => i.proteinG);
    const fatOver = fat > targets.fatG * 1.12;
    const proteinOver = protein > targets.proteinG * 1.25;
    const proteinUnder = protein < targets.proteinG * 0.9 && !fatOver;
    if (!fatOver && !proteinOver && !proteinUnder) return;
    // Calories only move between items of the SAME meal, so no meal (or snack) can balloon - the earlier
    // day-wide version dumped everything on the "leanest" item anywhere (flour snack, 1,200 kcal dinner).
    let moved = false;
    if (proteinUnder) {
      for (const m of meals) {
        const from = m.items.filter((i) => i.grams > 60 && i.kcal >= 80).sort((a, b) => dens(a) - dens(b))[0];
        const to = m.items.filter((i) => i !== from && roomFor(i) && i.fatG / Math.max(1, i.kcal) < 0.06).sort((a, b) => dens(b) - dens(a))[0];
        if (!from || !to || dens(to) <= dens(from)) continue;
        const freed = from.kcal * 0.15;
        scaleItem(from, 0.85);
        scaleItem(to, 1 + freed / Math.max(1, to.kcal));
        moved = true;
        break;
      }
    } else {
      const key = (i: MealItem) => (fatOver ? i.fatG : i.proteinG);
      const donors = items().filter((i) => i.grams > 40 && i.kcal >= 40).sort((a, b) => key(b) / Math.max(1, b.kcal) - key(a) / Math.max(1, a.kcal));
      for (const donor of donors) {
        const meal = meals.find((m) => m.items.includes(donor));
        const receiver = meal?.items
          .filter((i) => i !== donor && roomFor(i))
          .sort((a, b) => (a.fatG + a.proteinG) / Math.max(1, a.kcal) - (b.fatG + b.proteinG) / Math.max(1, b.kcal))[0];
        if (!meal || !receiver) continue;
        const freed = donor.kcal * 0.2;
        scaleItem(donor, 0.8);
        scaleItem(receiver, 1 + freed / Math.max(1, receiver.kcal));
        moved = true;
        break;
      }
    }
    if (!moved) return;
  }
}

/**
 * When uniform scaling (normaliseDayToTarget) still leaves the day meaningfully short - because
 * items were already MAX_GRAMS-capped, common with high calorie targets, low-calorie-density
 * dishes, or few meal slots (e.g. the 3-slot morning+night pattern) - adds ONE more food item to
 * the largest meal to close most of the remaining gap, instead of silently underfeeding the day.
 * Mutates `meals` in place. PURE besides that mutation (no I/O).
 */
function topUpDayToTarget(
  meals: Meal[],
  dailyKcal: number,
  pool: FoodItem[],
  dietType: string,
  dayIndex: number,
  usedToday: Set<string>,
  shares: Record<string, number> = {},
  mains: MealSlot[] = MAIN_SLOTS,
): void {
  const currentKcal = meals.reduce((s, m) => s + m.kcal, 0);
  const shortfall = dailyKcal - currentKcal;
  if (shortfall < dailyKcal * 0.08) return; // close enough - not worth a whole extra item
  // Never fill a meal past ~110% of its own share of the day (that is how dinner used to balloon); prefer any meal with room.
  const roomy = (m: Meal) => m.kcal < (shares[m.slot] ?? 0.3) * dailyKcal * 1.25;
  const withItems = meals.filter((m) => mains.includes(m.slot) && m.items.length > 0);
  const mainMeals = withItems.filter(roomy);
  if (mainMeals.length === 0) return;
  // The main meal furthest below its own share of the day (so lunch fills up before breakfast or dinner balloon).
  const deficit = (m: Meal) => (shares[m.slot] ?? 0.25) * dailyKcal - m.kcal;
  const target = (mainMeals.length ? mainMeals : meals.filter((m) => m.items.length > 0)).reduce(
    (best, m) => (best === undefined || deficit(m) > deficit(best) ? m : best),
    undefined as Meal | undefined,
  );
  if (!target) return;
  const candidates = candidatesForSlot(pool, target.slot, dietType).filter((f) => !usedToday.has(f.id) && !clashesWithMeal(target, f.name) && !NOT_A_SNACK.test(f.name));
  const pick = candidates[0];
  if (!pick) {
    // Nothing new suits this slot (few-meal patterns): enlarge what is already there, at most 1.4x and never past 400 g.
    const factor = Math.min(1.4, (target.kcal + shortfall) / Math.max(1, target.kcal));
    if (factor <= 1.02) return;
    for (const it of target.items) scaleItem(it, factor, true);
    target.kcal = round(target.items.reduce((s2, i) => s2 + i.kcal, 0), 0);
    target.proteinG = round(target.items.reduce((s2, i) => s2 + i.proteinG, 0), 1);
    return;
  }
  const item = toItem(pick, gramsForKcal(pick, Math.min(shortfall, Math.max(150, deficit(target)))));
  target.items.push(item);
  usedToday.add(pick.id);
  target.kcal = round(target.items.reduce((s, i) => s + i.kcal, 0), 0);
  target.proteinG = round(target.items.reduce((s, i) => s + i.proteinG, 0), 1);
}

/**
 * Keeps every meal a size a person can actually eat. A main meal is capped at ~30% of the day; whatever that trims is moved into the
 * snacks, which may grow to ~11% of the day each (a second light item if one is not enough). If the meals still cannot carry the
 * whole target the day lands a little under it, which is better than a 1,200 kcal breakfast. Mutates `meals` in place.
 */
function capMeals(meals: Meal[], dailyKcal: number, mains: MealSlot[], shares: Record<string, number>, pool: FoodItem[], dietType: string, usedToday: Set<string>): void {
  // A main meal may be a little above its own share of the day (never a huge one); a one-meal pattern keeps its big meal.
  const capFor = (slot: string) => {
    const share = shares[slot] ?? 0.3;
    return dailyKcal * (share >= 0.5 ? Math.min(0.9, share * 1.05) : Math.min(0.34, Math.max(0.3, share * 1.1)));
  };
  const snackCap = Math.min(320, Math.max(250, dailyKcal * 0.11));
  const refresh = (m: Meal) => {
    m.kcal = round(m.items.reduce((s, i) => s + i.kcal, 0), 0);
    m.proteinG = round(m.items.reduce((s, i) => s + i.proteinG, 0), 1);
  };
  let freed = 0;
  for (const m of meals) {
    const mainCap = capFor(m.slot);
    if (!mains.includes(m.slot) || m.kcal <= mainCap) continue;
    const factor = Math.max(0.6, mainCap / m.kcal);
    for (const it of m.items) scaleItem(it, factor, true);
    const before = m.kcal;
    refresh(m);
    freed += before - m.kcal;
  }
  if (freed <= 0) return;
  const snacks = meals.filter((m) => !mains.includes(m.slot) && m.items.length > 0).sort((a, b) => a.kcal - b.kcal);
  for (const m of snacks) {
    if (freed <= 5) break;
    const room = Math.min(freed, snackCap - m.kcal);
    if (room < 40) continue;
    // 1) grow the existing item (up to its own realistic serving cap)
    // fat-dense snack items (nuts, seeds) are not grown - that is how the day's fat overshoots
    const grow = m.items.filter((i) => i.kcal >= 20 && (i.fatG * 9) / Math.max(1, i.kcal) <= 0.5);
    const cur = grow.reduce((t, i) => t + i.kcal, 0);
    if (cur > 0) {
      for (const it of grow) scaleItem(it, Math.min(2, (cur + room) / cur));
      const added = m.items.reduce((t, i) => t + i.kcal, 0) - m.kcal;
      refresh(m);
      freed -= Math.max(0, added);
    }
    // 2) still room: add one more light item
    const left = Math.min(freed, snackCap - m.kcal);
    if (left >= 60) {
      const cands = candidatesForSlot(pool, m.slot, dietType).filter(
        (f) => !usedToday.has(f.id) && !NOT_A_SNACK.test(f.name) && !clashesWithMeal(m, f.name) && (f.fatG * 9) / Math.max(1, f.kcal) <= 0.35,
      );
      const pick = cands.find((f) => f.tags.some((t) => LIGHT_TAGS.has(t))) ?? cands[0];
      if (pick) {
        const item = toItem(pick, gramsForKcal(pick, left));
        m.items.push(item);
        usedToday.add(pick.id);
        refresh(m);
        freed -= item.kcal;
      }
    }
  }
}

const LIGHT_TAGS = new Set(['fruit', 'beverage', 'light', 'probiotic', 'high-fiber']);

/**
 * What a day looks like for each eating pattern the user can pick (these mirror the Meal Timing cards in the app's Office tab).
 * `mains` are the real plates; every other slot is a small snack. Weights are the share of the day's calories per slot.
 */
export interface EatingPatternPlan {
  slots: MealSlot[];
  weights: Partial<Record<MealSlot, number>>;
  mains: MealSlot[];
}
export const EATING_PATTERNS: Record<string, EatingPatternPlan> = {
  // 7:30 big meal, 10:30 pocket snack, pre-gym snack, 8:30 pm high-protein meal, optional bedtime curd/milk
  morning_night: { slots: ['breakfast', 'midmorning', 'eveningsnack', 'dinner', 'bedtime'], weights: { breakfast: 0.28, midmorning: 0.13, eveningsnack: 0.19, dinner: 0.28, bedtime: 0.12 }, mains: ['breakfast', 'dinner'] },
  // 5 meals through the day
  home: { slots: ['breakfast', 'midmorning', 'lunch', 'eveningsnack', 'dinner'], weights: { breakfast: 0.25, midmorning: 0.09, lunch: 0.29, eveningsnack: 0.10, dinner: 0.27 }, mains: ['breakfast', 'lunch', 'dinner'] },
  // breakfast, snack, canteen lunch, pre-gym, dinner
  office_canteen: { slots: ['breakfast', 'midmorning', 'lunch', 'eveningsnack', 'dinner'], weights: { breakfast: 0.27, midmorning: 0.09, lunch: 0.26, eveningsnack: 0.11, dinner: 0.27 }, mains: ['breakfast', 'lunch', 'dinner'] },
  // biggest breakfast, pocket snack, tiffin, pre-gym, dinner
  office_no_canteen: { slots: ['breakfast', 'midmorning', 'lunch', 'eveningsnack', 'dinner'], weights: { breakfast: 0.29, midmorning: 0.10, lunch: 0.24, eveningsnack: 0.10, dinner: 0.27 }, mains: ['breakfast', 'lunch', 'dinner'] },
  // breakfast before leaving, dhaba lunch, pocket fuel, dinner
  field: { slots: ['breakfast', 'midmorning', 'lunch', 'eveningsnack', 'dinner'], weights: { breakfast: 0.28, midmorning: 0.10, lunch: 0.28, eveningsnack: 0.11, dinner: 0.23 }, mains: ['breakfast', 'lunch', 'dinner'] },
  // "breakfast" before the shift, "lunch" mid-shift, pre-gym snack, LIGHT "dinner" after the shift
  night_shift: { slots: ['breakfast', 'lunch', 'eveningsnack', 'dinner', 'bedtime'], weights: { breakfast: 0.31, lunch: 0.29, eveningsnack: 0.13, dinner: 0.17, bedtime: 0.10 }, mains: ['breakfast', 'lunch', 'dinner'] },
  // one large meal in the eating window plus a small closer
  omad: { slots: ['dinner', 'bedtime'], weights: { dinner: 0.85, bedtime: 0.15 }, mains: ['dinner'] },
};

const SLOT_ORDER = new Map(MEAL_SLOTS.map((s, i) => [s, i] as const));

interface TrainingAdjust {
  slots: MealSlot[];
  weights: Record<string, number>;
  mains: MealSlot[];
  pre: MealSlot;
  post: MealSlot;
}

/**
 * Times the day's meals around the workout: a light carb-forward meal before it, a bigger protein meal after it.
 * Morning trainers get a small wake-up snack and a protein breakfast; evening trainers a pre-gym snack and a protein dinner;
 * night trainers a pre-gym snack, a lighter dinner and a protein bedtime meal.
 */
export function applyTraining(slots: MealSlot[], weights: Record<string, number>, mains: MealSlot[], when: TrainTime): TrainingAdjust {
  const w: Record<string, number> = { ...weights };
  const set = new Set<MealSlot>(slots);
  const has = (s: MealSlot) => set.has(s);
  const add = (s: MealSlot, weight: number) => {
    if (!has(s)) {
      set.add(s);
      w[s] = weight;
    }
  };
  const bump = (s: MealSlot, by: number) => {
    w[s] = (w[s] ?? 0.1) + by;
  };
  let pre: MealSlot;
  let post: MealSlot;
  if (when === 'morning') {
    pre = 'wakeup';
    add('wakeup', 0.06);
    if (!has('breakfast')) add('breakfast', 0.28);
    post = 'breakfast';
    bump('breakfast', 0.05);
  } else if (when === 'afternoon') {
    pre = has('midmorning') ? 'midmorning' : has('lunch') ? 'lunch' : 'eveningsnack';
    add('eveningsnack', 0.12);
    post = 'eveningsnack';
    bump('eveningsnack', 0.06);
  } else if (when === 'evening') {
    pre = 'eveningsnack';
    add('eveningsnack', 0.1);
    if (!has('dinner')) add('dinner', 0.27);
    post = 'dinner';
    bump('dinner', 0.04);
  } else {
    pre = 'eveningsnack';
    add('eveningsnack', 0.1);
    post = 'bedtime';
    add('bedtime', 0.12);
    bump('bedtime', 0.03);
    if (has('dinner')) w.dinner = Math.max(0.12, (w.dinner ?? 0.25) - 0.06); // an earlier, lighter dinner before a late session
  }
  const ordered = [...set].sort((a, b) => SLOT_ORDER.get(a)! - SLOT_ORDER.get(b)!);
  const nextMains = mains.filter((m) => m !== pre);
  if (!nextMains.includes(post) && (post === 'breakfast' || post === 'dinner')) nextMains.push(post);
  return { slots: ordered, weights: w, mains: nextMains, pre, post };
}

const PRE_NOTE: Record<TrainTime, string> = {
  morning: 'Small and carb-based, 30-45 min before you train - light enough to sit well.',
  afternoon: 'Carb-forward and light, about 60-90 min before you train.',
  evening: 'Carb-forward and low in fat, about 60-90 min before you train.',
  night: 'Carb-forward and light, about 60-90 min before your late session.',
};
const POST_NOTE: Record<TrainTime, string> = {
  morning: 'Protein-rich meal within an hour after training to repair and refuel.',
  afternoon: 'Protein-rich snack within an hour after training.',
  evening: 'Protein-rich dinner after training to repair muscle.',
  night: 'Light, protein-rich meal after training - easy on sleep.',
};

function buildDay(
  dayIndex: number,
  eligible: FoodItem[],
  targets: PlanTargets,
  dietType: string,
  fasting = false,
  eatingPattern?: string,
  trainTime?: TrainTime,
): DayPlan {
  // Morning + Night working pattern (spec Section 6): most common for office users who skip lunch.
  // Only 3 slots, front-loaded 40% breakfast / 25% evening snack / 35% dinner.
  const pattern = !fasting && dietType !== 'if' && eatingPattern ? EATING_PATTERNS[eatingPattern] : undefined;

  // Fasting day: fewer, lighter meals at ~40% of calories.
  const slots: MealSlot[] = fasting
    ? (['midmorning', 'lunch', 'eveningsnack'] as MealSlot[])
    : pattern
      ? pattern.slots
      : dietType === 'if'
        ? (['lunch', 'eveningsnack', 'dinner'] as MealSlot[])
        : MEAL_SLOTS;

  // Per-slot calorie weights: the morning+night pattern overrides the standard distribution.
  let slotWeight: Record<string, number> = pattern ? (pattern.weights as Record<string, number>) : SLOT_KCAL_WEIGHTS;
  let activeSlots: MealSlot[] = slots;
  let mains: MealSlot[] = pattern ? pattern.mains : MAIN_SLOTS;
  // On a training day the meals are timed around the workout (not on fasting days / IF).
  let training: TrainingAdjust | undefined;
  if (trainTime && !fasting && dietType !== 'if') {
    training = applyTraining(slots, slotWeight, mains, trainTime);
    activeSlots = training.slots;
    slotWeight = training.weights;
    mains = training.mains;
  }

  const dailyKcal = fasting ? Math.round(targets.dailyKcal * FASTING_KCAL_FACTOR) : targets.dailyKcal;

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
  const weightSum = activeSlots.reduce((s, sl) => s + slotWeight[sl]!, 0);

  // Shared across the day's meals so the same food is never served twice in one day.
  const usedToday = new Set<string>();
  // With only three meals the evening one is a real plate, not a snack (otherwise its calories pile onto breakfast/dinner).
  const meals = activeSlots.map((slot) => {
    const slotKcal = (dailyKcal * slotWeight[slot]!) / weightSum;
    return buildMeal(slot, pool, slotKcal, dietType, dayIndex, usedToday, mains);
  });

  // Greedy per-slot sizing tends to UNDERSHOOT the target by ~5% (grams clamping + rounding),
  // which makes portions look unrealistically small. Nudge every item up (once) so the day lands
  // on its calorie target. Capped so no single serving blows past MAX_GRAMS.
  normaliseDayToTarget(meals, dailyKcal, mains);

  // Uniform scaling still can't close the gap when items were already MAX_GRAMS-capped at build
  // time (common for high-calorie targets on low-calorie-density Indian dishes, or few meal slots
  // e.g. the 3-slot morning+night pattern) - top up with one more food item on the biggest meal
  // rather than silently leaving the day under target.
  // Realistic serving caps can leave a big target short (esp. with only 3 meals) - add up to 3 extra items, one per pass.
  for (let pass = 0; pass < 3; pass++) topUpDayToTarget(meals, dailyKcal, pool, dietType, dayIndex, usedToday, Object.fromEntries(activeSlots.map((sl) => [sl, slotWeight[sl]! / weightSum])), mains);
  balanceMacros(meals, { proteinG: targets.proteinG, fatG: targets.fatG });
  proteinTopUp(meals, { proteinG: targets.proteinG }, pool, usedToday);
  proteinTopUp(meals, { proteinG: targets.proteinG }, pool, usedToday); // a second lean item if the day is still well short
  // Last, so nothing above can re-inflate a meal: no plate is bigger than a person can reasonably eat.
  capMeals(meals, dailyKcal, mains, Object.fromEntries(activeSlots.map((sl) => [sl, slotWeight[sl]! / weightSum])), pool, dietType, usedToday);
  for (const m of meals) {
    m.kcal = round(m.items.reduce((s, i) => s + i.kcal, 0), 0);
    m.proteinG = round(m.items.reduce((s, i) => s + i.proteinG, 0), 1);
  }

  if (training && trainTime) {
    // A pre-workout meal must be real fuel, not black coffee: swap a near-empty one for a light carb food (~180 kcal).
    const preMeal = meals.find((m) => m.slot === training!.pre);
    if (preMeal && preMeal.kcal < 120) {
      const carb = pool
        .filter((f) => !usedToday.has(f.id) && f.kcal >= 40 && !NOT_A_SNACK.test(f.name) && (f.fatG * 9) / Math.max(1, f.kcal) <= 0.3 &&
          (f.tags.includes('fruit') || /banana|dates|oats|poha|toast|bread|idli|upma|sattu|khichdi|corn/i.test(f.name)))
        .sort((x, y) => Number(/banana|dates/i.test(y.name)) - Number(/banana|dates/i.test(x.name)) || x.kcal - y.kcal)[0];
      if (carb) {
        const item = toItem(carb, gramsForKcal(carb, 180));
        usedToday.add(carb.id);
        preMeal.items = [item];
        preMeal.kcal = round(item.kcal, 0);
        preMeal.proteinG = round(item.proteinG, 1);
      }
    }
    for (const m of meals) {
      if (m.slot === training.pre) { m.tag = 'pre-workout'; m.note = PRE_NOTE[trainTime]; }
      else if (m.slot === training.post) { m.tag = 'post-workout'; m.note = POST_NOTE[trainTime]; }
    }
  }

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
    ...(training && trainTime ? { training: trainTime } : {}),
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
  /** Office/lifestyle eating pattern (spec Section 6), e.g. 'morning_night' → 3 front-loaded meals. */
  eatingPattern?: string;
  /** Regeneration counter - shifts every food pick so Regenerate week gives a different plan. */
  variant?: number;
  /** YYYY-MM-DD -> when the user trains that day; the meals are timed around it. */
  training?: Record<string, TrainTime>;
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
 * No LLM, no randomness - the same inputs always yield the same plan.
 */
/**
 * Orders foods by a deterministic preference score (lower = preferred): locale match, pantry-on-hand,
 * and budget (prefer cheaper `costTier` when the budget is tight). PURE - buildDay picks from the
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
    const dayKey = dt ? dt.toISOString().slice(0, 10) : undefined;
    const day = buildDay(d + (options.variant ?? 0) * 5, ordered, targets, prefs.dietType, fasting, options.eatingPattern, dayKey ? options.training?.[dayKey] : undefined);
    if (dt) {
      day.date = dt.toISOString().slice(0, 10);
      const base = dayLabel(dt, options.today ?? options.startDate ?? dt);
      day.label = fasting ? `${base} · Fasting day` : base;
    }
    days.push(day);
  }
  return { days, targets };
}
