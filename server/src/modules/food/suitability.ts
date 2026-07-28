import { prisma } from '../../lib/prisma';
import { conditionAvoidTags, expandAllergen } from './foodFilter';
import { foodFriendliness } from './friendliness';
import { PREP_RANK, type FoodItem, type MealSlot, type PrepLevel } from './food.types';

/**
 * ONE consolidated "is this food right for me, right now?" scorer. It folds together every check the
 * app scattered across the plan generator, friendliness, and chat guardrails: diet type, allergens,
 * conditions, GI/sodium/purine/potassium-phosphorus, budget, micronutrient gaps, meal slot, region,
 * pantry, portion, plus NEW kitchen-access, food-drug (medication) flags, and cycle-phase fit.
 *
 * PURE and deterministic — no LLM. `assessFoodSuitability` never throws; `findSuitable` is the thin
 * DB wrapper. The server owns the verdict; the coach may explain it but never overrides it.
 */

export interface FoodSituation {
  dietType?: string;
  allergies?: string[];
  conditions?: string[];
  budgetTier?: 'low' | 'medium' | 'flexible';
  pantryTags?: string[];
  slot?: MealSlot;
  region?: string;
  /** Kitchen the user has access to right now. A food needing more than this is 'avoid'. */
  kitchen?: PrepLevel;
  /** Only these food IDs are actually available (PG pantry, travel bag). */
  availableFoodIds?: string[];
  /** Current medications — flags food-drug interactions (warfarin↔vitamin-K, MAOI↔tyramine, …). */
  medications?: string[];
  /** Menstrual-cycle phase for gentle phase-appropriate nudges (never a hard block). */
  cyclePhase?: 'menstrual' | 'follicular' | 'ovulatory' | 'luteal';
  /** Micronutrient labels currently running low — a food that fills one is boosted to 'good'. */
  microGaps?: string[];
  /** Desired portion; defaults to the food's typical serving. */
  portionG?: number;
}

export type SuitabilityVerdict = 'good' | 'moderate' | 'avoid';

export interface Verdict {
  ok: boolean; // true unless verdict === 'avoid'
  verdict: SuitabilityVerdict;
  reasons: string[];
  portionG: number;
  kcalForPortion: number;
}

const norm = (s: string) => s.toLowerCase().trim();
const has = (f: FoodItem, tag: string) => f.tags.map(norm).includes(tag);

/** Which foods each medication class does not mix with (by tag or name substring). */
const MED_INTERACTIONS: { match: RegExp; avoidTags?: string[]; avoidNames?: string[]; note: string }[] = [
  { match: /warfarin|coumadin|acitrom|acenocoumarol/, avoidTags: ['high-vitamin-k', 'leafy-green'], note: 'high vitamin-K greens can blunt your blood thinner — keep the amount steady and consistent' },
  { match: /maoi|phenelzine|tranylcypromine|isocarboxazid/, avoidTags: ['fermented', 'aged', 'high-tyramine'], note: 'aged/fermented (tyramine) foods can spike blood pressure on an MAOI' },
  { match: /statin|atorvastatin|simvastatin|rosuvastatin/, avoidNames: ['grapefruit'], avoidTags: ['grapefruit'], note: 'grapefruit interferes with statins' },
  { match: /metformin/, avoidTags: ['alcohol'], note: 'alcohol with metformin raises lactic-acidosis risk' },
  { match: /levothyroxine|thyroxine|eltroxin/, avoidTags: ['high-calcium', 'high-iron', 'soy'], note: 'calcium/iron/soy can block thyroid-hormone absorption — separate them by a few hours' },
  { match: /lithium/, avoidTags: ['high-sodium'], note: 'big swings in salt intake change lithium levels' },
];

/** Cycle-phase foods to lean into (gentle boost, never a block). */
const CYCLE_FAVOR: Record<NonNullable<FoodSituation['cyclePhase']>, { tags: string[]; note: string }> = {
  menstrual: { tags: ['high-iron', 'iron-rich'], note: 'iron-rich — helpful during your period' },
  follicular: { tags: ['high-fiber', 'fermented'], note: 'fibre/fermented foods suit the follicular phase' },
  ovulatory: { tags: ['antioxidant', 'high-fiber'], note: 'antioxidant-rich — good around ovulation' },
  luteal: { tags: ['high-magnesium', 'complex-carb', 'whole-grain'], note: 'magnesium/complex carbs ease luteal-phase cravings' },
};

/** Assess one food against the current situation. Pure. */
export function assessFoodSuitability(food: FoodItem, situation: FoodSituation = {}): Verdict {
  const reasons: string[] = [];
  const portionG = Math.round(situation.portionG ?? food.typicalServingG);
  const kcalForPortion = Math.round((food.kcal * portionG) / 100);
  const name = norm(food.name);

  // Severity: 0 good, 1 moderate, 2 avoid — numeric so control-flow narrowing stays out of the way.
  let sev = 0;
  const worsen = (to: SuitabilityVerdict) => { sev = Math.max(sev, to === 'avoid' ? 2 : 1); };

  // --- Hard blocks (verdict 'avoid') ---
  // Availability: not in the accessible set.
  if (situation.availableFoodIds && situation.availableFoodIds.length > 0 && !situation.availableFoodIds.includes(food.id)) {
    return { ok: false, verdict: 'avoid', reasons: ["you don't have this on hand right now"], portionG, kcalForPortion };
  }

  // Kitchen access: the food needs more cooking than the user can do.
  if (situation.kitchen) {
    const need = PREP_RANK[food.prep ?? 'stove'];
    if (need > PREP_RANK[situation.kitchen]) {
      return { ok: false, verdict: 'avoid', reasons: [`needs ${food.prep ?? 'stove'} cooking but you only have ${situation.kitchen} access`], portionG, kcalForPortion };
    }
  }

  // Allergens — fail-closed.
  const allergyTerms = (situation.allergies ?? []).flatMap(expandAllergen).filter(Boolean);
  const foodAllergens = food.allergens.map(norm);
  for (const term of allergyTerms) {
    if (name.includes(term) || foodAllergens.some((x) => x.includes(term) || term.includes(x))) {
      return { ok: false, verdict: 'avoid', reasons: [`contains ${term}, which you're allergic to`], portionG, kcalForPortion };
    }
  }

  // Diet incompatibility (category).
  if (situation.dietType && !dietAllows(situation.dietType, food.category)) {
    return { ok: false, verdict: 'avoid', reasons: [`not compatible with a ${situation.dietType} diet`], portionG, kcalForPortion };
  }

  // Food–drug interactions.
  for (const med of situation.medications ?? []) {
    const m = norm(med);
    for (const rule of MED_INTERACTIONS) {
      if (!rule.match.test(m)) continue;
      const hitTag = (rule.avoidTags ?? []).some((t) => has(food, t));
      const hitName = (rule.avoidNames ?? []).some((n) => name.includes(n));
      if (hitTag || hitName) {
        worsen('avoid');
        reasons.push(rule.note);
      }
    }
  }

  // --- Condition cautions ---
  const avoid = new Set(conditionAvoidTags(situation.conditions ?? []).map(norm));
  const conditionHits = food.tags.map(norm).filter((t) => avoid.has(t));
  if (conditionHits.length > 0) {
    // High-risk pairings → avoid; softer ones → moderate.
    const highRisk = conditionHits.some((t) => ['high-sugar', 'high-gi', 'high-purine', 'high-potassium', 'high-phosphorus'].includes(t));
    worsen(highRisk ? 'avoid' : 'moderate');
    reasons.push(`carries ${conditionHits.join('/')} — a flag for your conditions`);
  }

  // --- Softer nudges (moderate) ---
  const budgetCap = situation.budgetTier === 'low' ? 1 : situation.budgetTier === 'medium' ? 2 : 3;
  if (food.costTier > budgetCap) {
    worsen('moderate');
    reasons.push('pricier than your budget tier');
  }
  if (situation.slot && food.mealSlots.length > 0 && !food.mealSlots.includes(situation.slot)) {
    worsen('moderate');
    reasons.push(`usually eaten at another time, not ${situation.slot}`);
  }

  // --- Positive signals (reasons only; can lift a 'good' explanation) ---
  if (situation.pantryTags && situation.pantryTags.length > 0 && food.tags.some((t) => situation.pantryTags!.map(norm).includes(norm(t)))) {
    reasons.push('uses something already in your pantry');
  }
  if (situation.region && food.region && norm(food.region) === norm(situation.region)) {
    reasons.push('familiar to your region');
  }
  if (situation.microGaps && situation.microGaps.length > 0 && fillsMicroGap(food, situation.microGaps)) {
    if (sev === 1) sev = 0; // a gap-filler is worth recommending despite a soft flag
    reasons.push(`helps top up ${situation.microGaps.join('/')}, which you're low on`);
  }
  if (situation.cyclePhase) {
    const fav = CYCLE_FAVOR[situation.cyclePhase];
    if (fav.tags.some((t) => has(food, t))) reasons.push(fav.note);
  }

  const verdict: SuitabilityVerdict = sev === 2 ? 'avoid' : sev === 1 ? 'moderate' : 'good';
  if (verdict === 'good' && reasons.length === 0) reasons.push('fits your plan');
  return { ok: verdict !== 'avoid', verdict, reasons, portionG, kcalForPortion };
}

function fillsMicroGap(food: FoodItem, gaps: string[]): boolean {
  const tags = food.tags.map(norm);
  return gaps.some((g) => {
    const key = norm(g);
    return tags.some((t) => t.includes(key) || key.includes(t.replace(/^high-/, '')));
  });
}

/** Diet category rule (mirrors foodFilter.categoryAllowed, kept local so this module is standalone). */
function dietAllows(dietType: string, cat: FoodItem['category']): boolean {
  switch (norm(dietType)) {
    case 'vegan':
      return cat === 'vegan';
    case 'veg':
    case 'vegetarian':
    case 'jain':
    case 'satvik':
    case 'mediterranean':
      return cat === 'vegan' || cat === 'vegetarian';
    case 'eggetarian':
    case 'egg':
      return cat === 'vegan' || cat === 'vegetarian' || cat === 'egg';
    default:
      return true;
  }
}

const rank: Record<SuitabilityVerdict, number> = { good: 0, moderate: 1, avoid: 2 };

/** Pure: score every food and return the suitable ones (verdict ≠ avoid), best first. */
export function rankSuitable(foods: FoodItem[], situation: FoodSituation, opts?: { slot?: MealSlot; limit?: number }): (Verdict & { food: FoodItem })[] {
  const slot = opts?.slot ?? situation.slot;
  const scored = foods
    .map((food) => ({ food, ...assessFoodSuitability(food, { ...situation, slot }) }))
    .filter((v) => v.verdict !== 'avoid');
  scored.sort((a, b) => {
    if (rank[a.verdict] !== rank[b.verdict]) return rank[a.verdict] - rank[b.verdict];
    // Within the same verdict, prefer higher all-round friendliness, then more protein.
    const fa = avgFriendliness(a.food);
    const fb = avgFriendliness(b.food);
    if (fb !== fa) return fb - fa;
    return b.food.proteinG - a.food.proteinG;
  });
  return opts?.limit ? scored.slice(0, opts.limit) : scored;
}

function avgFriendliness(f: FoodItem): number {
  const s = foodFriendliness(f);
  return (s.diabetes + s.heart + s.gut + s.inflammation) / 4;
}

function toFoodItem(f: {
  id: string; name: string; locale: string; region: string | null; category: string;
  mealSlots: string[]; kcal: number; proteinG: number; carbG: number; fatG: number;
  fiberG: number; sugarG: number; sodiumMg: number; glycemicIndex: number | null;
  typicalServingG: number; costTier: number; tags: string[]; allergens: string[]; prep?: string | null;
}): FoodItem {
  return {
    id: f.id, name: f.name, locale: f.locale, region: f.region ?? undefined,
    category: f.category as FoodItem['category'], mealSlots: f.mealSlots as MealSlot[],
    kcal: f.kcal, proteinG: f.proteinG, carbG: f.carbG, fatG: f.fatG, fiberG: f.fiberG,
    sugarG: f.sugarG, sodiumMg: f.sodiumMg, glycemicIndex: f.glycemicIndex ?? undefined,
    typicalServingG: f.typicalServingG, costTier: (f.costTier as 1 | 2 | 3) ?? 2,
    tags: f.tags, allergens: f.allergens, prep: (f.prep as PrepLevel) ?? 'stove',
  };
}

/** DB wrapper: rank the whole catalog for this situation. */
export async function findSuitable(situation: FoodSituation, opts?: { slot?: MealSlot; limit?: number }): Promise<(Verdict & { food: FoodItem })[]> {
  const rows = await prisma.food.findMany();
  return rankSuitable(rows.map(toFoodItem), situation, opts);
}
