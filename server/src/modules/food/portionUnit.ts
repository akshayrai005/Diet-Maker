// Smart portion units (spec Section 8): Indians measure most foods by count / katori / glass, not
// grams. We DERIVE the unit from the food's name + tags (no DB column, so no migration), and also
// return the grams for one base unit so the client can convert the user's choice back to grams for
// logging (all macros stay per-100g on the server).

export type PortionUnit = 'count' | 'katori' | 'bowl' | 'glass' | 'scoop' | 'cup' | 'slice' | 'grams';

export interface PortionInfo {
  portionUnit: PortionUnit;
  /** Grams in ONE base unit (e.g. 1 egg = 50 g, 1 roti = 40 g, 1 small katori = 150 g). */
  unitGrams: number;
}

const has = (s: string, ...words: string[]) => words.some((w) => s.includes(w));

/**
 * Best-effort unit for a food, matched on its (lower-cased) name first, then category/tags.
 * Falls back to grams (meat, paneer, nuts, powders by weight).
 */
export function portionInfoFor(input: {
  name: string;
  tags?: string[];
  category?: string;
  typicalServingG?: number;
}): PortionInfo {
  const n = input.name.toLowerCase();
  const tags = (input.tags ?? []).map((t) => t.toLowerCase());
  const serving = input.typicalServingG && input.typicalServingG > 0 ? input.typicalServingG : 100;

  // Whey / protein powder → scoops (1 scoop ≈ 30 g).
  if (has(n, 'whey', 'protein powder', 'protein isolate', 'casein')) return { portionUnit: 'scoop', unitGrams: 30 };

  // Eggs → count (1 egg ≈ 50 g; egg white ≈ 33 g).
  if (has(n, 'egg white')) return { portionUnit: 'count', unitGrams: 33 };
  if (has(n, 'egg')) return { portionUnit: 'count', unitGrams: 50 };

  // Flatbreads & baked → count (1 roti/paratha ≈ 40 g).
  if (has(n, 'roti', 'chapati', 'chapatti', 'paratha', 'phulka', 'idli', 'dosa', 'chilla', 'thepla')) {
    return { portionUnit: 'count', unitGrams: n.includes('idli') ? 35 : 45 };
  }
  if (has(n, 'bread', 'toast', 'slice')) return { portionUnit: 'slice', unitGrams: 30 };
  if (has(n, 'biscuit', 'cookie', 'rusk')) return { portionUnit: 'count', unitGrams: 12 };

  // Whole fruit → count (grams from its typical serving).
  if (has(n, 'banana', 'apple', 'orange', 'guava', 'mango', 'pear', 'kiwi', 'pomegranate') || (tags.includes('fruit') && !n.includes('juice'))) {
    return { portionUnit: 'count', unitGrams: serving };
  }

  // Drinks → glass (250 ml ≈ 250 g).
  if (has(n, 'milk', 'buttermilk', 'chaas', 'lassi', 'coconut water', 'juice', 'smoothie', 'shake')) {
    return { portionUnit: 'glass', unitGrams: 250 };
  }
  if (has(n, 'chai', 'tea', 'coffee')) return { portionUnit: 'cup', unitGrams: 200 };

  // Curd / yogurt → katori.
  if (has(n, 'curd', 'dahi', 'yogurt', 'yoghurt', 'raita')) return { portionUnit: 'katori', unitGrams: 150 };

  // Rice → bowl.
  if (has(n, 'rice', 'pulao', 'biryani', 'khichdi')) return { portionUnit: 'bowl', unitGrams: 150 };

  // Dal / sabzi / curry / gravy → katori.
  if (has(n, 'dal', 'sabzi', 'sabji', 'curry', 'rajma', 'chana', 'sambar', 'kadhi', 'palak', 'bhaji') ||
      tags.includes('legume') || tags.includes('vegetable')) {
    return { portionUnit: 'katori', unitGrams: 150 };
  }

  // Everything else (meat, fish, paneer, tofu, nuts, oats, seeds) → grams.
  return { portionUnit: 'grams', unitGrams: 100 };
}
