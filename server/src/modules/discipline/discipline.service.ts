import { prisma } from '../../lib/prisma';
import { HttpError } from '../../middleware/error';
import { localDayKey } from '../../lib/tz';
import { gatherUserContext } from '../rating/context';
import { DEFAULT_HABITS, habitStreak, recoveryNudge, eveningReview } from './habits';
import type { AdherenceResult } from './adherence';

/** Seed the default habit set for a user the first time they open the discipline dashboard. */
export async function ensureDefaultHabits(userId: string): Promise<void> {
  const count = await prisma.habit.count({ where: { userId } });
  if (count > 0) return;
  await prisma.habit.createMany({
    data: DEFAULT_HABITS.map((h) => ({ userId, key: h.key, title: h.title, icon: h.icon, sortOrder: h.sortOrder })),
    skipDuplicates: true,
  });
}

export interface HabitView {
  id: string;
  key: string;
  title: string;
  icon: string | null;
  doneToday: boolean;
  streakDays: number;
}

export interface DisciplineToday {
  adherence: AdherenceResult;
  habits: HabitView[];
  review: string;
  nudges: string[];
}

/** Shift a 'YYYY-MM-DD' key by n days (n negative = earlier). */
function shiftKey(key: string, days: number): string {
  const d = new Date(`${key}T00:00:00Z`);
  return new Date(d.getTime() + days * 86_400_000).toISOString().slice(0, 10);
}

export async function getDisciplineToday(userId: string, offsetMin = 0, now: Date = new Date()): Promise<DisciplineToday> {
  await ensureDefaultHabits(userId);
  const todayKey = localDayKey(offsetMin, now.getTime());
  const yesterdayKey = shiftKey(todayKey, -1);

  const [habits, ctx] = await Promise.all([
    prisma.habit.findMany({
      where: { userId, active: true },
      orderBy: { sortOrder: 'asc' },
      include: { logs: { where: { done: true }, select: { date: true } } },
    }),
    gatherUserContext(userId, offsetMin, now),
  ]);

  const nudges: string[] = [];
  const views: HabitView[] = habits.map((h) => {
    const dayKeys = h.logs.map((l) => l.date);
    const doneToday = dayKeys.includes(todayKey);
    const doneYesterday = dayKeys.includes(yesterdayKey);
    // A streak that ended before yesterday, with nothing done since → offer a gentle restart.
    if (!doneToday && !doneYesterday) {
      const broken = habitStreak(dayKeys, shiftKey(todayKey, -2));
      const nudge = recoveryNudge(h.title, broken);
      if (nudge) nudges.push(nudge);
    }
    return {
      id: h.id,
      key: h.key,
      title: h.title,
      icon: h.icon,
      doneToday,
      streakDays: habitStreak(dayKeys, todayKey),
    };
  });

  const doneCount = views.filter((v) => v.doneToday).length;
  return { adherence: ctx.adherence, habits: views, review: eveningReview(doneCount, views.length), nudges };
}

/** Mark a habit done/undone for a local day (defaults to today). Absence = not done. */
export async function toggleHabit(userId: string, habitId: string, date: string, done: boolean): Promise<void> {
  const habit = await prisma.habit.findFirst({ where: { id: habitId, userId } });
  if (!habit) throw new HttpError(404, 'Habit not found');

  if (done) {
    await prisma.habitLog.upsert({
      where: { habitId_date: { habitId, date } },
      update: { done: true },
      create: { habitId, userId, date, done: true },
    });
  } else {
    await prisma.habitLog.deleteMany({ where: { habitId, userId, date } });
  }
}
