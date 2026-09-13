import type { Condition } from '../guardrails/types';

/**
 * A small, deliberately curated supplement catalog - not a storefront, not brand endorsements
 * (this app has no commercial relationship with any supplement maker and never will). Each entry
 * is a genuinely well-evidenced, widely-recommended supplement category (the kind any registered
 * dietitian would name), described generically ("a whey protein isolate", not a specific SKU) so
 * nothing here reads as a paid placement. `cautionFor` gates it from ever being suggested to a
 * user with a condition or diet type where it's genuinely inappropriate.
 */
export interface Supplement {
  id: string;
  name: string;
  category: 'protein' | 'creatine' | 'vitamin' | 'omega3' | 'electrolyte';
  /** Plain-language reason this is trusted/evidence-based - not marketing copy. */
  whyTrusted: string;
  typicalDosage: string;
  /** Diet types this fits (undefined = fits everyone). */
  dietCompatible?: string[];
  /** Conditions where this should NOT be suggested (safety gate, not optional). */
  avoidForConditions?: Condition[];
  /** Allergen keywords that exclude it (checked via the same synonym expansion as food). */
  allergenKeywords?: string[];
}

export const SUPPLEMENTS: Supplement[] = [
  {
    id: 'whey-protein',
    name: 'Whey protein isolate/concentrate',
    category: 'protein',
    whyTrusted:
      'The most-studied protein supplement there is - a fast-absorbing complete protein used in decades of muscle-protein-synthesis research. Genuinely useful when food alone consistently falls short of your protein target, not a substitute for real food.',
    typicalDosage: '1 scoop (~25-30g protein) once or twice daily, closest to training or wherever your day is shortest on protein.',
    dietCompatible: ['nonveg', 'veg', 'vegetarian', 'eggetarian', 'egg', 'keto', 'lowcarb', 'highprotein', 'if', 'custom', 'mediterranean'],
    allergenKeywords: ['milk', 'dairy', 'lactose'],
  },
  {
    id: 'plant-protein',
    name: 'Plant-based protein blend (pea + rice)',
    category: 'protein',
    whyTrusted:
      'A pea+rice blend covers the amino-acid gaps either source has alone, giving a complete protein profile comparable to whey in well-controlled studies - the right choice when dairy protein is off the table, whether that is by diet (vegan/jain/satvik) or by a dairy allergy.',
    typicalDosage: '1 scoop (~20-25g protein) once or twice daily.',
    // No dietCompatible restriction - it's dairy-free and suits every diet type, not just
    // vegan/jain/satvik. It's ALSO the fallback for a dairy-allergic nonveg/vegetarian user, so
    // restricting it to those three diet types would leave that user with zero protein options.
  },
  {
    id: 'creatine-monohydrate',
    name: 'Creatine monohydrate',
    category: 'creatine',
    whyTrusted:
      'One of the most-researched supplements in sports nutrition, with a strong safety record in healthy adults at standard doses - consistently shown to support strength and training performance.',
    typicalDosage: '3-5g daily, any time of day, taken consistently (no "loading phase" needed).',
    avoidForConditions: ['kidney_disease'],
  },
  {
    id: 'vitamin-d3',
    name: 'Vitamin D3',
    category: 'vitamin',
    whyTrusted:
      'Widely deficient in people with limited sun exposure or darker skin tones regardless of diet quality - one of the few single-nutrient supplements with genuinely strong evidence behind correcting a real, common gap.',
    typicalDosage: '1000-2000 IU daily (get a blood level checked before going higher).',
  },
  {
    id: 'omega-3',
    name: 'Omega-3 (fish oil or algae-based)',
    category: 'omega3',
    whyTrusted:
      'Most diets - especially ones light on fatty fish - fall short on EPA/DHA. Algae-based options give the same omega-3s without fish, so this fits even a vegan diet.',
    typicalDosage: '1-2g combined EPA+DHA daily, with a meal.',
    avoidForConditions: ['heart_disease'], // needs a doctor's supervision alongside blood thinners - not a blanket ban, but not a self-directed add
  },
  {
    id: 'electrolytes',
    name: 'Electrolyte supplement (sodium/potassium/magnesium)',
    category: 'electrolyte',
    whyTrusted:
      'Useful specifically on high-sweat training days or in hot climates - plain water alone can under-replace what heavy sweating actually loses.',
    typicalDosage: 'As needed on hard-training or hot days, per the product label - not a daily default for everyone.',
    avoidForConditions: ['hypertension', 'kidney_disease', 'heart_disease'],
  },
];
