/**
 * Supplement suggestions - PURE and deterministic.
 *
 * Not a storefront: the catalog (supplements.seed.ts) is small and curated, and this module just
 * decides WHICH of those are actually relevant to this user right now, safety-gated the same way
 * food is (diet type, allergens, medical conditions). The one genuinely "smart" behavior: when
 * protein adherence is consistently low, it surfaces a protein-powder suggestion matched to the
 * user's diet type (whey for most, a plant blend for vegan/jain/satvik) - explicitly framed as
 * closing a real, measured gap, not a generic upsell.
 */
import { SUPPLEMENTS, type Supplement } from '../../data/supplements.seed';
import { expandAllergen } from '../food/foodFilter';
import type { Condition } from '../../guardrails/types';

export interface SupplementSuggestInput {
  dietType: string;
  conditions: Condition[];
  allergies: string[];
  /** From adherence intelligence: protein % of target over the trailing window, or null if unknown. */
  proteinAdherencePct: number | null;
}

export interface SuggestedSupplement extends Supplement {
  /** Set when this entry is specifically relevant right now (not just generically eligible). */
  reason?: string;
}

const norm = (s: string) => s.toLowerCase().trim();

function isEligible(s: Supplement, input: SupplementSuggestInput): boolean {
  if (s.avoidForConditions?.some((c) => input.conditions.includes(c))) return false;
  if (s.dietCompatible && !s.dietCompatible.map(norm).includes(norm(input.dietType))) return false;
  if (s.allergenKeywords) {
    const allergyTerms = input.allergies.flatMap(expandAllergen);
    if (s.allergenKeywords.some((kw) => allergyTerms.some((t) => kw.includes(t) || t.includes(kw)))) return false;
  }
  return true;
}

/** Every catalog entry that's safe for this user, diet/allergen/condition-filtered. */
export function eligibleSupplements(input: SupplementSuggestInput): Supplement[] {
  return SUPPLEMENTS.filter((s) => isEligible(s, input));
}

/**
 * The full eligible catalog, with the protein-powder entry (whey or plant, whichever fits the
 * diet) flagged with a `reason` when protein adherence is genuinely low - the "smart enough to
 * suggest whey protein" behavior. Never suggests a protein supplement when adherence data is
 * unknown or already fine; a supplement is for a measured, real gap, not a default nudge.
 */
export function suggestSupplements(input: SupplementSuggestInput): SuggestedSupplement[] {
  let eligible = eligibleSupplements(input);
  // One protein powder is enough: the plant blend is only the fallback when whey isn't allowed.
  if (eligible.some((x) => x.category === 'protein' && x.id !== 'plant-protein')) {
    eligible = eligible.filter((x) => x.id !== 'plant-protein');
  }
  const proteinLow = input.proteinAdherencePct !== null && input.proteinAdherencePct < 75;

  return eligible.map((s) => {
    if (s.category === 'protein' && proteinLow) {
      return {
        ...s,
        reason: `Your logged protein has been averaging ${input.proteinAdherencePct}% of target - food should come first, but a scoop of this closes the gap without needing to eat more volume.`,
      };
    }
    return s;
  });
}
