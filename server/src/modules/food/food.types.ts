// Pure food + meal-plan types. No I/O — the generator operates on plain arrays so it is
// fully unit-testable without a database.

export type FoodCategory = 'vegan' | 'vegetarian' | 'egg' | 'nonveg';

export type MealSlot =
  | 'wakeup'
  | 'breakfast'
  | 'midmorning'
  | 'lunch'
  | 'eveningsnack'
  | 'dinner'
  | 'bedtime';

export const MEAL_SLOTS: MealSlot[] = [
  'wakeup',
  'breakfast',
  'midmorning',
  'lunch',
  'eveningsnack',
  'dinner',
  'bedtime',
];

/** Fraction of daily calories per slot (sums to 1). */
export const SLOT_KCAL_WEIGHTS: Record<MealSlot, number> = {
  wakeup: 0.05,
  breakfast: 0.2,
  midmorning: 0.1,
  lunch: 0.3,
  eveningsnack: 0.1,
  dinner: 0.2,
  bedtime: 0.05,
};

export interface FoodItem {
  id: string;
  name: string;
  locale: string; // 'IN' | 'global'
  region?: string;
  category: FoodCategory;
  mealSlots: MealSlot[];
  /** Per 100 g. */
  kcal: number;
  proteinG: number;
  carbG: number;
  fatG: number;
  fiberG: number;
  sugarG: number;
  sodiumMg: number;
  glycemicIndex?: number;
  typicalServingG: number;
  costTier: 1 | 2 | 3; // 1 cheap .. 3 pricey
  tags: string[];
  allergens: string[];
}

export interface PlanTargets {
  dailyKcal: number;
  proteinG: number;
  fatG: number;
  carbG: number;
  fiberG: number;
  sodiumMaxMg?: number;
  /** Daily kcal adjustment applied to compensate for last week (carry-over). */
  carryOverKcal?: number;
  /** Generator version this plan was built with — lets the client auto-refresh stale plans. */
  planVersion?: number;
}

export interface PlanPreferences {
  dietType: string;
  allergies: string[];
  conditions: string[];
  /** Monthly food budget in the user's currency; undefined = no constraint. */
  monthlyBudget?: number;
  /** Budget bias for food selection: 'low' prefers costTier 1, 'medium' ≤2, 'flexible' no bias. */
  budgetTier?: 'low' | 'medium' | 'flexible';
  /** Foods/tags the user already has — floated to the front so plans use what's on hand. */
  pantryTags?: string[];
  /** Macro-band strictness: 'relaxed' widens the acceptable bands, 'strict' narrows them. */
  strictness?: 'relaxed' | 'standard' | 'strict';
  locale?: string; // bias toward this locale's foods
}

export interface MealItem {
  foodId: string;
  name: string;
  grams: number;
  kcal: number;
  proteinG: number;
  carbG: number;
  fatG: number;
  fiberG: number;
  sugarG: number;
  sodiumMg: number;
}

export interface Meal {
  slot: MealSlot;
  items: MealItem[];
  kcal: number;
  proteinG: number;
  /** Optional condition-friendliness scores + highlight labels (see friendliness.ts). */
  friendliness?: import('./friendliness').MealFriendliness;
}

export interface DayPlan {
  dayIndex: number; // 0..6
  date?: string; // YYYY-MM-DD (today + dayIndex)
  label?: string; // e.g. "Today", "Tomorrow", weekday name
  meals: Meal[];
  totals: {
    kcal: number;
    proteinG: number;
    carbG: number;
    fatG: number;
    fiberG: number;
    sodiumMg: number;
  };
}

export interface WeekPlan {
  days: DayPlan[];
  targets: PlanTargets;
}
