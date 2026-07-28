import { prisma } from '../../lib/prisma';
import { HttpError } from '../../middleware/error';
import { localDayKey } from '../../lib/tz';
import { gatherUserContext } from '../rating/context';
import { DEFAULT_HABITS, habitStreak, recoveryNudge, eveningReview, autoHabitStatus, type HabitMetrics } from './habits';
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
  /** True when a tracked metric (steps/water/sleep) drives this habit — the UI shows progress. */
  autoTracked: boolean;
  /** True when today's completion came from the metric, not a manual tick. */
  autoDone: boolean;
  /** Metric progress toward the goal, when auto-tracked. */
  current?: number;
  target?: number;
  unit?: string;
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

export async function getDisciplineToday(
  userId: string,
  offsetMin = 0,
  now: Date = new Date(),
  deviceMetrics: Pick<HabitMetrics, 'steps' | 'sleepHours'> = {},
): Promise<DisciplineToday> {
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

  // Metrics that auto-complete a habit: water is server-side; steps/sleep come from the device.
  const metrics: HabitMetrics = {
    waterMl: ctx.today.waterMl,
    waterTargetMl: ctx.target?.waterMl,
    steps: deviceMetrics.steps,
    sleepHours: deviceMetrics.sleepHours,
  };

  // When a metric has just met its goal, persist a HabitLog so streaks & adherence count it too.
  const toPersist: { habitId: string }[] = [];
  const nudges: string[] = [];
  const views: HabitView[] = habits.map((h) => {
    const dayKeys = h.logs.map((l) => l.date);
    const manualDone = dayKeys.includes(todayKey);
    const auto = autoHabitStatus(h.key, metrics);
    const doneToday = manualDone || auto.done;
    if (auto.done && !manualDone) toPersist.push({ habitId: h.id });

    // Only nudge for a broken streak on habits that aren't auto-satisfied today.
    const doneYesterday = dayKeys.includes(yesterdayKey);
    if (!doneToday && !doneYesterday) {
      const broken = habitStreak(dayKeys, shiftKey(todayKey, -2));
      const nudge = recoveryNudge(h.title, broken);
      if (nudge) nudges.push(nudge);
    }

    // Include today in the streak calc when auto-completed (the log write below makes it durable).
    const streakKeys = doneToday && !manualDone ? [...dayKeys, todayKey] : dayKeys;
    return {
      id: h.id,
      key: h.key,
      title: h.title,
      icon: h.icon,
      doneToday,
      streakDays: habitStreak(streakKeys, todayKey),
      autoTracked: auto.tracked,
      autoDone: auto.done && !manualDone,
      current: auto.current,
      target: auto.target,
      unit: auto.unit,
    };
  });

  if (toPersist.length > 0) {
    await prisma.habitLog.createMany({
      data: toPersist.map((p) => ({ habitId: p.habitId, userId, date: todayKey, done: true })),
      skipDuplicates: true,
    });
  }

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

/** Slugify a custom habit title into a stable, unique-ish key. */
function slugify(title: string): string {
  return (
    title
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 40) || 'habit'
  );
}

/** Create a custom habit for the user. */
export async function createHabit(userId: string, title: string, icon?: string): Promise<HabitView> {
  const trimmed = title.trim();
  if (!trimmed) throw new HttpError(400, 'Habit title is required');

  const count = await prisma.habit.count({ where: { userId } });
  // Ensure a unique key even if the title collides with an existing slug.
  let key = slugify(trimmed);
  if (await prisma.habit.findUnique({ where: { userId_key: { userId, key } } })) {
    key = `${key}-${count + 1}`;
  }
  const habit = await prisma.habit.create({
    data: { userId, key, title: trimmed, icon: icon?.slice(0, 4) ?? '✅', sortOrder: 100 + count },
  });
  return { id: habit.id, key: habit.key, title: habit.title, icon: habit.icon, doneToday: false, streakDays: 0, autoTracked: false, autoDone: false };
}

/** Remove a habit (and its logs cascade). */
export async function deleteHabit(userId: string, habitId: string): Promise<void> {
  const res = await prisma.habit.deleteMany({ where: { id: habitId, userId } });
  if (res.count === 0) throw new HttpError(404, 'Habit not found');
}
