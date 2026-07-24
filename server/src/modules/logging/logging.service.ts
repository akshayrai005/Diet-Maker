import { prisma } from '../../lib/prisma';
import { decryptJson, encryptJson } from '../../lib/crypto';
import { round } from '../../calc/anthropometry';
import { HttpError } from '../../middleware/error';
import type { CheckinBody, FoodLogBody, WaterLogBody } from './logging.schemas';
import { buildDashboard, dayKey, sumTotals, type MicronutrientBlock, type WeightPoint } from './dashboard';
import { wellnessKcalOnDay } from '../wellness/wellnessLog.service';
import { micronutrientTargets, MICRONUTRIENT_LABELS, type MicronutrientKey } from '../../calc/micronutrients';
import type { SensitiveData } from '../profile/profile.schemas';
import type { Sex } from '../../calc/types';
import { localDayKey } from '../../lib/tz';

/** Whole years between an ISO date-of-birth and now. */
function ageFromDob(dob: string, now: Date): number {
  const born = new Date(dob);
  let age = now.getUTCFullYear() - born.getUTCFullYear();
  const m = now.getUTCMonth() - born.getUTCMonth();
  if (m < 0 || (m === 0 && now.getUTCDate() < born.getUTCDate())) age -= 1;
  return Math.max(0, age);
}

/**
 * Micronutrient RDA targets for the user. Intake is not yet computed (foods carry no nutrient
 * columns until Step 4), so this returns targets with `available: false` rather than flagging every
 * nutrient as deficient. Server-computed; educational.
 */
function micronutrientBlock(sex: Sex, ageYears: number): MicronutrientBlock {
  const rda = micronutrientTargets(sex, ageYears);
  const keys = Object.keys(rda) as MicronutrientKey[];
  return {
    available: false,
    note: 'Micronutrient tracking begins once your logged foods carry nutrient data.',
    targets: keys.map((key) => ({ key, label: MICRONUTRIENT_LABELS[key], rda: rda[key], intake: null, pct: null, low: false })),
  };
}

/**
 * UTC [start, end) instants bounding the local calendar day containing `date`.
 * offsetMin = the user's UTC offset in minutes (0 => plain UTC day, backward-compatible).
 */
export function dayRange(date: Date, offsetMin = 0): { start: Date; end: Date } {
  const w = new Date(date.getTime() + offsetMin * 60_000);
  const startMs = Date.UTC(w.getUTCFullYear(), w.getUTCMonth(), w.getUTCDate()) - offsetMin * 60_000;
  return { start: new Date(startMs), end: new Date(startMs + 86_400_000) };
}

async function nutritionBasis(body: FoodLogBody) {
  if (body.foodId) {
    const food = await prisma.food.findUnique({ where: { id: body.foodId } });
    if (!food) throw new HttpError(404, 'Food not found');
    return {
      name: body.foodName ?? food.name,
      per100g: {
        kcal: food.kcal,
        proteinG: food.proteinG,
        carbG: food.carbG,
        fatG: food.fatG,
        fiberG: food.fiberG,
        sugarG: food.sugarG,
        sodiumMg: food.sodiumMg,
      },
    };
  }
  // Validated by schema refine: foodName + per100g present here.
  return { name: body.foodName!, per100g: body.per100g! };
}

export async function logFood(userId: string, body: FoodLogBody) {
  const basis = await nutritionBasis(body);
  const factor = body.grams / 100;
  const entry = await prisma.foodLog.create({
    data: {
      userId,
      loggedAt: body.loggedAt ? new Date(body.loggedAt) : new Date(),
      mealSlot: body.mealSlot,
      foodId: body.foodId ?? null,
      foodName: basis.name,
      grams: body.grams,
      entryMethod: body.entryMethod,
      kcal: round(basis.per100g.kcal * factor, 0),
      proteinG: round(basis.per100g.proteinG * factor, 1),
      carbG: round(basis.per100g.carbG * factor, 1),
      fatG: round(basis.per100g.fatG * factor, 1),
      fiberG: round(basis.per100g.fiberG * factor, 1),
      sugarG: round(basis.per100g.sugarG * factor, 1),
      sodiumMg: round(basis.per100g.sodiumMg * factor, 0),
      notes: body.notes ?? null,
      mood: body.mood ?? null,
      hunger: body.hunger ?? null,
    },
  });
  return entry;
}

export async function listFood(userId: string, date: Date, offsetMin = 0) {
  const { start, end } = dayRange(date, offsetMin);
  return prisma.foodLog.findMany({
    where: { userId, loggedAt: { gte: start, lt: end } },
    orderBy: { loggedAt: 'asc' },
  });
}

export async function deleteFood(userId: string, id: string) {
  const res = await prisma.foodLog.deleteMany({ where: { id, userId } });
  if (res.count === 0) throw new HttpError(404, 'Log entry not found');
}

export async function logWater(userId: string, body: WaterLogBody) {
  return prisma.waterLog.create({
    data: {
      userId,
      amountMl: body.amountMl,
      loggedAt: body.loggedAt ? new Date(body.loggedAt) : new Date(),
    },
  });
}

export async function waterTotal(userId: string, date: Date, offsetMin = 0): Promise<number> {
  const { start, end } = dayRange(date, offsetMin);
  const agg = await prisma.waterLog.aggregate({
    where: { userId, loggedAt: { gte: start, lt: end } },
    _sum: { amountMl: true },
  });
  return agg._sum.amountMl ?? 0;
}

export async function createCheckin(userId: string, body: CheckinBody) {
  const { date, energy, sleepHours, mood, pain, notes, ...measurements } = body;
  const checkin = await prisma.weeklyCheckin.create({
    data: {
      userId,
      date: date ? new Date(date) : new Date(),
      measurementsEnc: encryptJson(measurements),
      energy: energy ?? null,
      sleepHours: sleepHours ?? null,
      mood: mood ?? null,
      pain: pain ?? null,
      notes: notes ?? null,
    },
  });
  await prisma.auditLog.create({
    data: { userId, action: 'checkin.create', detail: 'weight/measurements written' },
  });
  return { id: checkin.id, date: checkin.date };
}

export async function listCheckins(userId: string) {
  const rows = await prisma.weeklyCheckin.findMany({
    where: { userId },
    orderBy: { date: 'asc' },
  });
  return rows.map((r) => {
    const m = r.measurementsEnc
      ? decryptJson<{ weightKg: number; waistCm?: number }>(r.measurementsEnc)
      : null;
    return {
      id: r.id,
      date: r.date,
      measurements: m,
      energy: r.energy,
      sleepHours: r.sleepHours,
      mood: r.mood,
      pain: r.pain,
      notes: r.notes,
    };
  });
}

export async function getDashboard(userId: string, offsetMin = 0, now: Date = new Date()) {
  const todayKey = localDayKey(offsetMin, now.getTime());
  const { start: dayStart, end: dayEnd } = dayRange(now, offsetMin);

  const [todayFood, waterMl, snapshot, checkins, distinctDays, exerciseBurn, wellnessBurn, profile] = await Promise.all([
    listFood(userId, now, offsetMin),
    waterTotal(userId, now, offsetMin),
    prisma.calcResultSnapshot.findFirst({ where: { userId }, orderBy: { createdAt: 'desc' } }),
    prisma.weeklyCheckin.findMany({ where: { userId }, orderBy: { date: 'asc' } }),
    prisma.foodLog.findMany({ where: { userId }, select: { loggedAt: true } }),
    prisma.exerciseLog.aggregate({
      where: { userId, performedAt: { gte: dayStart, lt: dayEnd } },
      _sum: { kcal: true },
    }),
    wellnessKcalOnDay(userId, dayStart, dayEnd),
    prisma.profile.findUnique({ where: { userId } }),
  ]);

  let micronutrients: MicronutrientBlock | undefined;
  if (profile?.sensitiveEnc) {
    const s = decryptJson<SensitiveData>(profile.sensitiveEnc);
    micronutrients = micronutrientBlock(s.sex as Sex, ageFromDob(s.dob, now));
  }

  const result = snapshot?.result as
    | {
        dailyKcal: number;
        proteinG: number;
        waterMl: number;
        bmi: number;
        projection?: { label: string; weeks: number; weightKg: number; bmi: number }[];
      }
    | undefined;

  const weightPoints: WeightPoint[] = checkins
    .map((c) => {
      if (!c.measurementsEnc) return null;
      const m = decryptJson<{ weightKg: number }>(c.measurementsEnc);
      return { date: c.date.toISOString(), weightKg: m.weightKg };
    })
    .filter((p): p is WeightPoint => p !== null);

  return buildDashboard({
    targets: result
      ? { dailyKcal: result.dailyKcal, proteinG: result.proteinG, waterMl: result.waterMl }
      : null,
    todayTotals: sumTotals(todayFood),
    waterTodayMl: waterMl,
    logDayKeys: distinctDays.map((d) => dayKey(d.loggedAt)),
    todayKey,
    weightPoints,
    bmi: result?.bmi ?? null,
    projection: result?.projection ?? [],
    burnedTodayKcal: (exerciseBurn._sum.kcal ?? 0) + wellnessBurn,
    micronutrients,
  });
}
