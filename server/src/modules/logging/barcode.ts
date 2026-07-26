import { HttpError } from '../../middleware/error';

export interface BarcodeFood {
  code: string;
  name: string;
  per100g: {
    kcal: number;
    proteinG: number;
    carbG: number;
    fatG: number;
    fiberG: number;
    sugarG: number;
    sodiumMg: number;
  };
  source: 'openfoodfacts';
}

const OFF_URL = 'https://world.openfoodfacts.org/api/v2/product';

function num(v: unknown): number {
  const n = typeof v === 'string' ? parseFloat(v) : (v as number);
  return Number.isFinite(n) ? Math.max(0, Math.round(n * 10) / 10) : 0;
}

/**
 * Looks up a barcode via Open Food Facts (free, no key). Degrades gracefully:
 * 404 when the product is unknown, 502 when OFF is unreachable - never crashes.
 */
export async function lookupBarcode(code: string): Promise<BarcodeFood> {
  const url = `${OFF_URL}/${encodeURIComponent(code)}.json?fields=product_name,nutriments`;
  let json: { status?: number; product?: { product_name?: string; nutriments?: Record<string, unknown> } };
  try {
    const res = await fetch(url, {
      headers: { 'User-Agent': 'NutriAI/0.1 (https://github.com/akr01239-hub/Diet-Maker)' },
      signal: AbortSignal.timeout(8000),
    });
    if (!res.ok) throw new Error(`OFF ${res.status}`);
    json = (await res.json()) as typeof json;
  } catch {
    throw new HttpError(502, 'Barcode service unavailable - try again or enter manually');
  }

  if (json.status !== 1 || !json.product) {
    throw new HttpError(404, 'Product not found for this barcode');
  }

  const n = json.product.nutriments ?? {};
  return {
    code,
    name: json.product.product_name || `Item ${code}`,
    per100g: {
      kcal: num(n['energy-kcal_100g']),
      proteinG: num(n['proteins_100g']),
      carbG: num(n['carbohydrates_100g']),
      fatG: num(n['fat_100g']),
      fiberG: num(n['fiber_100g']),
      sugarG: num(n['sugars_100g']),
      // OFF reports sodium in grams per 100g.
      sodiumMg: num((n['sodium_100g'] as number) * 1000),
    },
    source: 'openfoodfacts',
  };
}
