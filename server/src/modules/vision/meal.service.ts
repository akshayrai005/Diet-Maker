import { geminiVisionJson, visionAvailable } from '../../ai/vision';

export interface VisionFoodItem {
  name: string;
  grams: number;
  per100g: {
    kcal: number;
    proteinG: number;
    carbG: number;
    fatG: number;
    fiberG: number;
    sugarG: number;
    sodiumMg: number;
  };
}

export interface MealPhotoResult {
  available: boolean;
  items: VisionFoodItem[];
  note: string;
}

const num = (v: unknown, d = 0): number => (typeof v === 'number' && isFinite(v) && v >= 0 ? v : d);
const str = (v: unknown): string => (typeof v === 'string' ? v : '');

/** Identify foods on a plate and estimate portion + per-100g macros via Gemini vision. */
export async function assessMealFromPhoto(
  imageBase64: string,
  mimeType: string,
): Promise<MealPhotoResult> {
  if (!visionAvailable()) return { available: false, items: [], note: 'Photo logging needs the AI provider configured.' };

  const prompt = [
    'You are a nutrition assistant. Identify the distinct foods in this meal photo and estimate each one.',
    'For every item give the food name, the visible portion in grams, and typical per-100g nutrition.',
    'Respond ONLY as JSON: {"items":[{"name":string,"grams":number,"per100g":{"kcal":number,"proteinG":number,"carbG":number,"fatG":number,"fiberG":number,"sugarG":number,"sodiumMg":number}}],"note":string}.',
    'Estimate reasonably from typical values; keep at most 6 items. If no food is visible, return items:[] and a helpful note.',
  ].join(' ');

  const result = await geminiVisionJson(imageBase64, mimeType, prompt);
  if (!result) return { available: true, items: [], note: 'Could not read the photo - try a clearer, well-lit shot of the plate.' };

  const rawItems = Array.isArray(result.items) ? result.items : [];
  const items: VisionFoodItem[] = rawItems
    .slice(0, 6)
    .map((it) => {
      const o = (it ?? {}) as Record<string, unknown>;
      const p = (o.per100g ?? {}) as Record<string, unknown>;
      return {
        name: str(o.name) || 'Food',
        grams: Math.round(num(o.grams, 100)),
        per100g: {
          kcal: Math.round(num(p.kcal)),
          proteinG: Math.round(num(p.proteinG) * 10) / 10,
          carbG: Math.round(num(p.carbG) * 10) / 10,
          fatG: Math.round(num(p.fatG) * 10) / 10,
          fiberG: Math.round(num(p.fiberG) * 10) / 10,
          sugarG: Math.round(num(p.sugarG) * 10) / 10,
          sodiumMg: Math.round(num(p.sodiumMg)),
        },
      };
    })
    .filter((it) => it.per100g.kcal > 0);

  return {
    available: true,
    items,
    note: str(result.note) || (items.length ? 'Estimates - adjust grams before logging.' : 'No food detected in the photo.'),
  };
}
