import { prisma } from '../../lib/prisma';
import { decryptJson } from '../../lib/crypto';
import { round } from '../../calc/anthropometry';
import { HttpError } from '../../middleware/error';
import { dayKey, computeStreak, weightTrend, type WeightPoint } from '../logging/dashboard';
import { waterTotal, listFood } from '../logging/logging.service';
import type { DayPlan, FoodItem, MealSlot } from '../food/food.types';
import { buildGroceryList } from './grocery';
import { buildWeeklyReport, type ReportDay } from './report';
import { renderReportHtml, type ReportAnalysis } from './reportHtml';
import { getAnalysis, toReportAnalysis } from './analysis.service';
import { computeBadges, badgeSummary, type GamificationStats } from './gamification';

function toFoodItem(f: {
  id: string; name: string; locale: string; region: string | null; category: string;
  mealSlots: string[]; kcal: number; proteinG: number; carbG: number; fatG: number;
  fiberG: number; sugarG: number; sodiumMg: number; glycemicIndex: number | null;
  typicalServingG: number; costTier: number; tags: string[]; allergens: string[];
}): FoodItem {
  return {
    id: f.id, name: f.name, locale: f.locale, region: f.region ?? undefined,
    category: f.category as FoodItem['category'], mealSlots: f.mealSlots as MealSlot[],
    kcal: f.kcal, proteinG: f.proteinG, carbG: f.carbG, fatG: f.fatG, fiberG: f.fiberG,
    sugarG: f.sugarG, sodiumMg: f.sodiumMg, glycemicIndex: f.glycemicIndex ?? undefined,
    typicalServingG: f.typicalServingG, costTier: (f.costTier as 1 | 2 | 3) ?? 2,
    tags: f.tags, allergens: f.allergens,
  };
}

async function latestTargets(userId: string) {
  const snap = await prisma.calcResultSnapshot.findFirst({
    where: { userId },
    orderBy: { createdAt: 'desc' },
  });
  return snap?.result as
    | { dailyKcal: number; proteinG: number; waterMl: number; bmi: number; tdee?: number; weightLossBlocked?: boolean }
    | undefined;
}

/** Current weight from the encrypted profile (for the weight prediction). */
async function currentWeight(userId: string): Promise<number | null> {
  const profile = await prisma.profile.findUnique({ where: { userId } });
  if (!profile?.sensitiveEnc) return null;
  try {
    return decryptJson<{ currentWeightKg: number }>(profile.sensitiveEnc).currentWeightKg;
  } catch {
    return null;
  }
}

async function weightPoints(userId: string): Promise<WeightPoint[]> {
  const checkins = await prisma.weeklyCheckin.findMany({ where: { userId }, orderBy: { date: 'asc' } });
  return checkins
    .map((c) => {
      if (!c.measurementsEnc) return null;
      try {
        return { date: c.date.toISOString(), weightKg: decryptJson<{ weightKg: number }>(c.measurementsEnc).weightKg };
      } catch {
        return null;
      }
    })
    .filter((p): p is WeightPoint => p !== null);
}

export async function getGrocery(userId: string) {
  const plan = await prisma.dietPlan.findFirst({ where: { userId }, orderBy: { createdAt: 'desc' } });
  if (!plan) throw new HttpError(404, 'Generate a plan first');
  const foods = await prisma.food.findMany();
  const days = plan.days as unknown as DayPlan[];
  const grocery = buildGroceryList(days, foods.map(toFoodItem));
  // The user's weekly calorie target (daily × 7) so the UI can show the grocery total against it.
  const targets = await latestTargets(userId);
  const targetWeeklyKcal = targets ? Math.round(targets.dailyKcal * days.length) : null;
  return { ...grocery, targetWeeklyKcal };
}

async function daysInRange(userId: string, now: Date, windowDays = 7): Promise<ReportDay[]> {
  const since = new Date(now.getTime() - windowDays * 86_400_000);
  const logs = await prisma.foodLog.findMany({
    where: { userId, loggedAt: { gte: since } },
    select: { loggedAt: true, kcal: true, proteinG: true },
  });
  const map = new Map<string, { kcal: number; proteinG: number }>();
  for (const l of logs) {
    const k = dayKey(l.loggedAt);
    const cur = map.get(k) ?? { kcal: 0, proteinG: 0 };
    cur.kcal += l.kcal;
    cur.proteinG += l.proteinG;
    map.set(k, cur);
  }
  return [...map.entries()]
    .sort((a, b) => a[0].localeCompare(b[0]))
    .map(([date, v]) => ({ date, kcal: round(v.kcal, 0), proteinG: round(v.proteinG, 1) }));
}

/** Itemised food-log entries for the last 7 days (newest first), for the detailed table. */
async function entriesInRange(userId: string, now: Date, windowDays = 7) {
  const since = new Date(now.getTime() - windowDays * 86_400_000);
  const logs = await prisma.foodLog.findMany({
    where: { userId, loggedAt: { gte: since } },
    orderBy: { loggedAt: 'desc' },
    select: { loggedAt: true, mealSlot: true, foodName: true, grams: true, kcal: true, proteinG: true },
  });
  return logs.map((l) => ({
    date: dayKey(l.loggedAt),
    mealSlot: l.mealSlot,
    name: l.foodName,
    grams: round(l.grams, 0),
    kcal: round(l.kcal, 0),
    proteinG: round(l.proteinG, 1),
  }));
}

/** Water intake per day for the last 7 days. */
async function waterInRange(userId: string, now: Date, windowDays = 7) {
  const since = new Date(now.getTime() - windowDays * 86_400_000);
  const logs = await prisma.waterLog.findMany({
    where: { userId, loggedAt: { gte: since } },
    select: { loggedAt: true, amountMl: true },
  });
  const map = new Map<string, number>();
  for (const l of logs) map.set(dayKey(l.loggedAt), (map.get(dayKey(l.loggedAt)) ?? 0) + l.amountMl);
  return [...map.entries()].sort((a, b) => a[0].localeCompare(b[0])).map(([date, ml]) => ({ date, ml }));
}

/** Lifetime totals, so the report doubles as an all-time record. */
async function allTimeStats(userId: string) {
  const [foodAgg, distinctDays, waterAgg] = await Promise.all([
    prisma.foodLog.aggregate({ where: { userId }, _sum: { kcal: true } }),
    prisma.foodLog.findMany({ where: { userId }, select: { loggedAt: true } }),
    prisma.waterLog.aggregate({ where: { userId }, _sum: { amountMl: true } }),
  ]);
  const daysLogged = new Set(distinctDays.map((d) => dayKey(d.loggedAt))).size;
  const totalKcal = round(foodAgg._sum.kcal ?? 0, 0);
  return {
    daysLogged,
    totalKcal,
    avgDailyKcal: daysLogged > 0 ? round(totalKcal / daysLogged, 0) : 0,
    totalWaterMl: waterAgg._sum.amountMl ?? 0,
  };
}

export async function getWeeklyReport(
  userId: string,
  generatedAt: string,
  now: Date = new Date(),
  windowDays = 7,
) {
  const [user, targets, wp, days, entries, waterByDay, allTime, curWeight] = await Promise.all([
    prisma.user.findUnique({ where: { id: userId } }),
    latestTargets(userId),
    weightPoints(userId),
    daysInRange(userId, now, windowDays),
    entriesInRange(userId, now, windowDays),
    waterInRange(userId, now, windowDays),
    allTimeStats(userId),
    currentWeight(userId),
  ]);
  const trend = weightTrend(wp);
  return buildWeeklyReport({
    name: user ? `${user.firstName} ${user.lastName}` : 'NutriAI user',
    generatedAt,
    targets: targets ? { dailyKcal: targets.dailyKcal, proteinG: targets.proteinG, waterMl: targets.waterMl } : null,
    bmi: targets?.bmi ?? null,
    latestWeightKg: trend.latestKg,
    weightDeltaKg: trend.deltaKg,
    days,
    entries,
    waterByDay,
    allTime,
    maintenanceKcal: targets?.tdee ?? null,
    currentWeightKg: curWeight,
    weightLossBlocked: targets?.weightLossBlocked ?? false,
  });
}

/**
 * Renders an in-app HTML report over a consolidated span: `count` weeks or months back from now.
 * count is clamped to 1-12. Returns a self-contained HTML string.
 */
export async function getReportView(
  userId: string,
  range: 'weekly' | 'monthly',
  count: number,
  now: Date = new Date(),
): Promise<string> {
  const c = Math.max(1, Math.min(12, Math.round(count) || 1));
  const windowDays = range === 'monthly' ? c * 30 : c * 7;
  const report = await getWeeklyReport(userId, now.toISOString(), now, windowDays);
  const label =
    range === 'monthly'
      ? c === 1
        ? 'Last month'
        : `Last ${c} months`
      : c === 1
        ? 'Last week'
        : `Last ${c} weeks`;

  // Lead the report with the coach-voice analysis (rating ring + pillar bars + narrative).
  let analysis: ReportAnalysis | undefined;
  try {
    analysis = toReportAnalysis(await getAnalysis(userId, 0, now));
  } catch {
    // Incomplete profile / no rating yet - render the report without the analysis header.
  }
  return renderReportHtml(report, { label, analysis });
}

export async function getGamification(userId: string, now: Date = new Date()) {
  const targets = await latestTargets(userId);
  const [totalFoodLogs, todayFood, waterMl, wp, checkinCount, distinctDays] = await Promise.all([
    prisma.foodLog.count({ where: { userId } }),
    listFood(userId, now),
    waterTotal(userId, now),
    weightPoints(userId),
    prisma.weeklyCheckin.count({ where: { userId } }),
    prisma.foodLog.findMany({ where: { userId }, select: { loggedAt: true } }),
  ]);

  const todayProtein = todayFood.reduce((s, e) => s + e.proteinG, 0);
  const trend = weightTrend(wp);
  const weightLostKg = trend.firstKg !== null && trend.latestKg !== null ? round(trend.firstKg - trend.latestKg, 1) : 0;

  const stats: GamificationStats = {
    totalFoodLogs,
    streakDays: computeStreak(distinctDays.map((d) => dayKey(d.loggedAt)), dayKey(now)),
    metWaterTargetToday: targets ? waterMl >= targets.waterMl : false,
    metProteinTargetToday: targets ? todayProtein >= targets.proteinG : false,
    weightLostKg,
    checkinCount,
  };
  return badgeSummary(computeBadges(stats));
}
