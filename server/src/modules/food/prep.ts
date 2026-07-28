import type { FoodItem, PrepLevel } from './food.types';

/**
 * Kitchen-access classification for foods. PURE and deterministic — no LLM. Drives PG / hostel /
 * travel planning where the user can't cook. 'none' = ready-to-eat or just-mix; 'kettle' = hot
 * water only; 'microwave'; 'stove' = needs a hob/pan (the default for anything cooked).
 */

/** Explicit prep level for foods where the id is known. Everything else falls back to tag heuristics. */
const PREP_BY_ID: Record<string, PrepLevel> = {
  // assemble-only / ready-to-eat
  banana: 'none', apple: 'none', 'soaked-almonds': 'none', peanuts: 'none', 'roasted-chana': 'none',
  curd: 'none', buttermilk: 'none', paneer: 'none', 'sprouts-salad': 'none',
  sattu: 'none', muesli: 'none', 'bread-pb': 'none', 'whey-shake': 'none', 'soaked-oats': 'none',
  'cold-milk': 'none', 'roasted-makhana': 'none', 'dry-fruits-mix': 'none',
  // hot water only
  'green-tea': 'kettle', 'warm-milk': 'kettle', 'turmeric-milk': 'kettle',
  'oats-porridge': 'kettle', 'boiled-egg': 'kettle', 'instant-poha-cup': 'kettle',
};

/** Deterministic prep level for a food. */
export function inferPrep(f: Pick<FoodItem, 'id' | 'tags'> & { prep?: PrepLevel }): PrepLevel {
  if (f.prep) return f.prep;
  const byId = PREP_BY_ID[f.id];
  if (byId) return byId;
  const tags = f.tags.map((t) => t.toLowerCase());
  if (tags.includes('fruit') || tags.includes('nuts')) return 'none';
  if (tags.includes('beverage')) return 'kettle';
  return 'stove';
}

/**
 * The assemble-only staples a PG / hostel student can actually rely on: high-protein, cheap, and
 * needing no more than hot water. The PG plan mode draws primarily from these.
 */
export const PG_STAPLE_IDS = [
  'curd', 'cold-milk', 'paneer', 'boiled-egg', 'roasted-chana', 'peanuts', 'sattu',
  'soaked-oats', 'muesli', 'bread-pb', 'sprouts-salad', 'buttermilk', 'banana',
  'whey-shake', 'roasted-makhana', 'dry-fruits-mix',
] as const;

/** True when the living situation / kitchen implies assemble-only planning. */
export function isPgMode(opts: { livingSituation?: string; kitchen?: PrepLevel }): boolean {
  // Only a genuine NO-kitchen situation forces assemble-only staples. Living in a PG/hostel doesn't
  // by itself mean you can't cook (many have a shared stove/induction), and a kettle can still make
  // daliya/oats — so those keep normal Indian plans (roti+dal, rice+sabji) rather than dropping to
  // curd-and-nuts. Users who truly can't cook select kitchen = 'none'.
  return opts.kitchen === 'none';
}
