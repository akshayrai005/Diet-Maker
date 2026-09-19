import { Router } from 'express';
import { z } from 'zod';
import { asyncHandler } from '../../lib/asyncHandler';
import { requireAuth, type AuthedRequest } from '../../middleware/auth';
import { generateAndSavePlan, latestPlan, swapMeal } from './plan.service';
import { tzOffsetMin } from '../../lib/tz';
import { prisma } from '../../lib/prisma';
import { searchUsda, type FoodSearchItem } from './usda';
import { searchOpenFoodFacts } from './openFoodFacts';
import { MEAL_SLOTS, type MealSlot } from './food.types';
import { portionInfoFor } from './portionUnit';

export const planRouter = Router();

const genSchema = z.object({ days: z.number().int().min(1).max(30).optional() });

const swapSchema = z.object({
  dayIndex: z.number().int().min(0).max(30),
  slot: z.enum(MEAL_SLOTS as [string, ...string[]]),
});

/** Generate + persist a new plan from the user's stored profile. */
planRouter.post(
  '/plan',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const { days } = genSchema.parse(req.body ?? {});
    const plan = await generateAndSavePlan(req.user!.id, days ?? 7, tzOffsetMin(req));
    res.status(201).json({ plan });
  }),
);

planRouter.get(
  '/plan/latest',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const plan = await latestPlan(req.user!.id);
    res.json({ plan });
  }),
);

/** Swap one meal in the latest plan for a different dish at similar calories. */
planRouter.post(
  '/plan/swap',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const { dayIndex, slot } = swapSchema.parse(req.body);
    const plan = await swapMeal(req.user!.id, dayIndex, slot as MealSlot);
    res.json({ plan });
  }),
);

/** Browse the local food database (simple search). */
planRouter.get(
  '/foods',
  asyncHandler(async (req, res) => {
    const q = typeof req.query.q === 'string' ? req.query.q : undefined;
    const rows = await prisma.food.findMany({
      where: q ? { name: { contains: q, mode: 'insensitive' } } : undefined,
      orderBy: { name: 'asc' },
      take: 100,
    });
    // Hide junk rows with no letters in the name (e.g. a mistyped "500" saved as a food).
    const foods = rows.filter((f) => /[a-zA-Z]/.test(f.name)).map((f) => {
      const p = portionInfoFor({ name: f.name, tags: f.tags, category: f.category, typicalServingG: f.typicalServingG });
      return { ...f, portionUnit: p.portionUnit, unitGrams: p.unitGrams };
    });
    res.json({ foods });
  }),
);

/**
 * High-protein reference list: real DB foods ranked by protein, for the "High Protein"
 * planning tab. Every row carries per-100g macros + fiber + state (raw/cooked/dry/prepared)
 * so the client can sort/filter without duplicating nutrition data.
 */
planRouter.get(
  '/foods/high-protein',
  asyncHandler(async (_req, res) => {
    const rows = await prisma.food.findMany({
      where: { proteinG: { gte: 6 } },
      orderBy: { proteinG: 'desc' },
      take: 80,
    });
    const foods = rows.map((f) => {
      const p = portionInfoFor({ name: f.name, tags: f.tags, category: f.category, typicalServingG: f.typicalServingG });
      return {
        ...f,
        portionUnit: p.portionUnit,
        unitGrams: p.unitGrams,
        proteinPer100Kcal: f.kcal > 0 ? Math.round((f.proteinG / f.kcal) * 1000) / 10 : 0,
      };
    });
    res.json({ foods });
  }),
);

/**
 * Combined food search: local seed foods first, then USDA FoodData Central (when
 * USDA_FDC_API_KEY is set). Every item carries full per-100g macros so it can be logged
 * without a local DB row (USDA foods have no row).
 */
planRouter.get(
  '/foods/search',
  asyncHandler(async (req, res) => {
    const q = typeof req.query.q === 'string' ? req.query.q.trim() : '';

    const local = await prisma.food.findMany({
      where: q ? { name: { contains: q, mode: 'insensitive' } } : undefined,
      orderBy: { name: 'asc' },
      take: 20,
    });

    const localItems: FoodSearchItem[] = local.map((f) => {
      const p = portionInfoFor({ name: f.name, tags: f.tags, category: f.category, typicalServingG: f.typicalServingG });
      return {
        id: f.id,
        name: f.name,
        kcal: f.kcal,
        proteinG: f.proteinG,
        carbG: f.carbG,
        fatG: f.fatG,
        fiberG: f.fiberG,
        sugarG: f.sugarG,
        sodiumMg: f.sodiumMg,
        typicalServingG: f.typicalServingG,
        source: 'local',
        portionUnit: p.portionUnit,
        unitGrams: p.unitGrams,
      };
    });

    // USDA is best-effort: [] when no key or on error.
    const [usda, off] = q ? await Promise.all([searchUsda(q), searchOpenFoodFacts(q)]) : [[], []];

    res.json({ foods: [...localItems, ...usda, ...off] });
  }),
);

const aiEstimateSchema = z.object({
  // Requires a letter - rejects a stray number (e.g. a batch-weight value mistyped into the
  // ingredient search box) from being "estimated" and saved as a fake Food row.
  name: z.string().min(2).max(80).regex(/[a-zA-Z]/, 'Must contain a food name, not just numbers'),
});

/**
 * Falls back to an LLM estimate when neither the local catalog nor USDA has an ingredient
 * (e.g. raw "bajra flour" - only finished dishes like roti are seeded). Saves it as a real Food
 * row so it's a permanent, searchable catalog entry from then on, not a one-off guess.
 */
planRouter.post(
  '/foods/ai-estimate',
  requireAuth,
  asyncHandler(async (req, res) => {
    const { name } = aiEstimateSchema.parse(req.body);
    const { estimateAndSaveFood } = await import('./aiEstimate');
    const f = await estimateAndSaveFood(name);
    const p = portionInfoFor({ name: f.name, tags: f.tags, category: f.category, typicalServingG: f.typicalServingG });
    const item: FoodSearchItem = {
      id: f.id,
      name: f.name,
      kcal: f.kcal,
      proteinG: f.proteinG,
      carbG: f.carbG,
      fatG: f.fatG,
      fiberG: f.fiberG,
      sugarG: f.sugarG,
      sodiumMg: f.sodiumMg,
      typicalServingG: f.typicalServingG,
      source: 'local',
      portionUnit: p.portionUnit,
      unitGrams: p.unitGrams,
    };
    res.status(201).json({ food: item });
  }),
);
