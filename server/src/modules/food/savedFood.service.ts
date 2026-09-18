import { z } from 'zod';
import { prisma } from '../../lib/prisma';
import { round } from '../../calc/anthropometry';
import { HttpError } from '../../middleware/error';

export const savedFoodSchema = z.object({
  name: z.string().min(1).max(120).regex(/[a-zA-Z]/, 'Name must contain letters'),
  kcal: z.number().nonnegative().max(1000),
  proteinG: z.number().nonnegative().max(100).default(0),
  carbG: z.number().nonnegative().max(100).default(0),
  fatG: z.number().nonnegative().max(100).default(0),
  fiberG: z.number().nonnegative().max(100).default(0),
  sugarG: z.number().nonnegative().max(100).default(0),
  sodiumMg: z.number().nonnegative().max(10000).default(0),
  /** Present when this entry is filling a gap Open Food Facts didn't have. */
  barcode: z.string().min(4).max(32).optional(),
});
export type SavedFoodBody = z.infer<typeof savedFoodSchema>;

export async function createSaved(userId: string, body: SavedFoodBody) {
  // Idempotent by name: auto-saving on every log must not pile up duplicates.
  const existing = await prisma.savedFood.findFirst({ where: { userId, name: { equals: body.name, mode: 'insensitive' } } });
  if (existing) return existing;
  return prisma.savedFood.create({ data: { userId, ...body } });
}

export async function listSaved(userId: string) {
  return prisma.savedFood.findMany({ where: { userId }, orderBy: { createdAt: 'desc' }, take: 100 });
}

export async function deleteSaved(userId: string, id: string) {
  const res = await prisma.savedFood.deleteMany({ where: { id, userId } });
  if (res.count === 0) throw new HttpError(404, 'Saved food not found');
}

/** Distinct recently-logged foods, reconstructed to per-100g for one-tap re-logging. */
export async function recentFoods(userId: string) {
  const rows = await prisma.foodLog.findMany({
    where: { userId },
    orderBy: { loggedAt: 'desc' },
    take: 60,
    select: {
      foodName: true, grams: true, kcal: true, proteinG: true, carbG: true,
      fatG: true, fiberG: true, sugarG: true, sodiumMg: true,
    },
  });
  const seen = new Set<string>();
  const out: { name: string; per100g: Record<string, number> }[] = [];
  for (const r of rows) {
    const key = r.foodName.toLowerCase();
    if (seen.has(key) || r.grams <= 0) continue;
    seen.add(key);
    const f = 100 / r.grams;
    out.push({
      name: r.foodName,
      per100g: {
        kcal: round(r.kcal * f, 0),
        proteinG: round(r.proteinG * f, 1),
        carbG: round(r.carbG * f, 1),
        fatG: round(r.fatG * f, 1),
        fiberG: round(r.fiberG * f, 1),
        sugarG: round(r.sugarG * f, 1),
        sodiumMg: round(r.sodiumMg * f, 0),
      },
    });
    if (out.length >= 12) break;
  }
  return out;
}
