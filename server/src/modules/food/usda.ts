import { env } from '../../lib/env';

export interface FoodSearchItem {
  id: string;
  name: string;
  kcal: number;
  proteinG: number;
  carbG: number;
  fatG: number;
  fiberG: number;
  sugarG: number;
  sodiumMg: number;
  typicalServingG: number;
  source: 'local' | 'usda';
}

const FDC_SEARCH = 'https://api.nal.usda.gov/fdc/v1/foods/search';

/** USDA FDC nutrient numbers we care about (per 100 g). */
const NUTRIENT = {
  kcal: 1008, // Energy (kcal)
  protein: 1003,
  fat: 1004,
  carb: 1005,
  fiber: 1079,
  sugar: 2000,
  sodium: 1093,
} as const;

function round(n: number, dp = 1): number {
  const f = 10 ** dp;
  return Math.round((n || 0) * f) / f;
}

/**
 * Searches USDA FoodData Central. Requires USDA_FDC_API_KEY; returns [] when the key is
 * unset or on any error (graceful - the app still works without USDA). Values are per 100 g.
 */
export async function searchUsda(query: string, limit = 15): Promise<FoodSearchItem[]> {
  const key = env.USDA_FDC_API_KEY;
  if (!key || !query.trim()) return [];

  // Include Branded (~600k products, incl. many Indian/packaged foods) alongside the
  // curated Foundation/SR Legacy sets, sorted by relevance.
  const url =
    `${FDC_SEARCH}?api_key=${encodeURIComponent(key)}` +
    `&query=${encodeURIComponent(query)}` +
    `&pageSize=${limit}` +
    `&dataType=${encodeURIComponent('Foundation,SR Legacy,Branded')}` +
    `&sortBy=dataType.keyword&sortOrder=asc`;

  try {
    const res = await fetch(url, { signal: AbortSignal.timeout(8000) });
    if (!res.ok) return [];
    const json = (await res.json()) as { foods?: UsdaFood[] };
    return (json.foods ?? []).map(mapUsdaFood).filter((f) => f.kcal > 0);
  } catch {
    return [];
  }
}

interface UsdaFood {
  fdcId: number;
  description?: string;
  foodNutrients?: { nutrientId?: number; value?: number }[];
}

function mapUsdaFood(f: UsdaFood): FoodSearchItem {
  const byId = new Map<number, number>();
  for (const n of f.foodNutrients ?? []) {
    if (typeof n.nutrientId === 'number') byId.set(n.nutrientId, n.value ?? 0);
  }
  const g = (id: number) => byId.get(id) ?? 0;
  return {
    id: `usda:${f.fdcId}`,
    name: (f.description ?? `USDA ${f.fdcId}`).toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase()),
    kcal: round(g(NUTRIENT.kcal), 0),
    proteinG: round(g(NUTRIENT.protein)),
    carbG: round(g(NUTRIENT.carb)),
    fatG: round(g(NUTRIENT.fat)),
    fiberG: round(g(NUTRIENT.fiber)),
    sugarG: round(g(NUTRIENT.sugar)),
    sodiumMg: round(g(NUTRIENT.sodium), 0),
    typicalServingG: 100,
    source: 'usda',
  };
}
