import { prisma } from '../../lib/prisma';
import { HttpError } from '../../middleware/error';
import { dayRange } from '../logging/logging.service';
import { latestBodyWeightKg } from '../logging/bodyWeight';
import { exerciseKcal } from '../../calc/activityCalories';
import type { ExerciseLogBody } from './exerciseLog.schemas';

export async function logExercise(userId: string, body: ExerciseLogBody) {
  const bodyWeightKg = await latestBodyWeightKg(userId);
  const kcal = exerciseKcal({
    name: body.exerciseName,
    sets: body.sets ?? null,
    reps: body.reps ?? null,
    weightKg: body.weightKg ?? null,
    durationMin: body.durationMin ?? null,
    bodyWeightKg,
  });
  return prisma.exerciseLog.create({
    data: {
      userId,
      performedAt: body.performedAt ? new Date(body.performedAt) : new Date(),
      exerciseName: body.exerciseName,
      focus: body.focus ?? null,
      sets: body.sets ?? null,
      reps: body.reps ?? null,
      weightKg: body.weightKg ?? null,
      durationMin: body.durationMin ?? null,
      kcal,
      notes: body.notes ?? null,
    },
  });
}

export async function listExercise(userId: string, date: Date, offsetMin = 0) {
  const { start, end } = dayRange(date, offsetMin);
  return prisma.exerciseLog.findMany({
    where: { userId, performedAt: { gte: start, lt: end } },
    orderBy: { performedAt: 'asc' },
  });
}

export async function deleteExercise(userId: string, id: string) {
  const res = await prisma.exerciseLog.deleteMany({ where: { id, userId } });
  if (res.count === 0) throw new HttpError(404, 'Exercise log not found');
}

/** Most recent performed set per exercise - progression hints ("last time: 40 kg × 8"). */
export async function lastPerformance(
  userId: string,
): Promise<Record<string, { weightKg: number | null; reps: number | null; sets: number | null; performedAt: string }>> {
  const rows = await prisma.exerciseLog.findMany({
    where: { userId },
    orderBy: { performedAt: 'desc' },
    take: 300,
  });
  const last: Record<string, { weightKg: number | null; reps: number | null; sets: number | null; performedAt: string }> = {};
  for (const r of rows) {
    if (!last[r.exerciseName]) {
      last[r.exerciseName] = {
        weightKg: r.weightKg,
        reps: r.reps,
        sets: r.sets,
        performedAt: r.performedAt.toISOString(),
      };
    }
  }
  return last;
}
