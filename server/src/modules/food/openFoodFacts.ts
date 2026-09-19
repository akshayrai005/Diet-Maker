import { portionInfoFor } from './portionUnit';
import type { FoodSearchItem } from './usda';

const OFF_SEARCH = 'https://search.openfoodfacts.org/search';

interface OffProduct {
  code?: string;
  product_name?: string;
  brands?: string | string[];
  nutriments?: Record<string, number | string | undefined>;
}

const num = (v: unknown): number => {
  const n = typeof v === 'string' ? parseFloat(v) : (v as number);
  return Number.isFinite(n) ? n : 0;
};
const r = (n: number, dp = 1) => Math.round(n * 10 ** dp) / 10 ** dp;

/** Open Food Facts: free, no API key, strong Indian packaged-food coverage. Values per 100 g. [] on any error. */
export async function searchOpenFoodFacts(query: string, limit = 10): Promise<FoodSearchItem[]> {
  if (!query.trim()) return [];
  const url =
    `${OFF_SEARCH}?q=${encodeURIComponent(query)}&page_size=${limit}&fields=code,product_name,brands,nutriments`;
  try {
    const res = await fetch(url, { signal: AbortSignal.timeout(6000), headers: { 'User-Agent': 'Kaizen-DietMaker/1.0' } });
    if (!res.ok) return [];
    const json = (await res.json()) as { hits?: OffProduct[] };
    return (json.hits ?? [])
      .filter((p) => p.code && p.product_name && num(p.nutriments?.['energy-kcal_100g']) > 0)
      .map((p) => {
        const n = p.nutriments ?? {};
        const brand = Array.isArray(p.brands) ? p.brands[0] : p.brands?.split(',')[0]?.trim();
        const name = brand ? `${p.product_name} (${brand})` : p.product_name!;
        const info = portionInfoFor({ name });
        return {
          id: `off:${p.code}`,
          name,
          kcal: r(num(n['energy-kcal_100g']), 0),
          proteinG: r(num(n['proteins_100g'])),
          carbG: r(num(n['carbohydrates_100g'])),
          fatG: r(num(n['fat_100g'])),
          fiberG: r(num(n['fiber_100g'])),
          sugarG: r(num(n['sugars_100g'])),
          sodiumMg: r(num(n['sodium_100g']) * 1000, 0),
          typicalServingG: 100,
          source: 'off' as const,
          portionUnit: info.portionUnit,
          unitGrams: info.unitGrams,
        };
      });
  } catch {
    return [];
  }
}
