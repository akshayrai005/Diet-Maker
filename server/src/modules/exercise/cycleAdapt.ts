import { computeCycle } from '../cycle/cycle';
import type { ExerciseItem, WeeklyWorkout, WorkoutDay } from './exercise.types';

const DAY_MS = 86_400_000;

const e = (name: string, sets: number, reps: string, type: ExerciseItem['type']): ExerciseItem => ({ name, sets, reps, type });

/** Gentle recovery session used on period days (low-impact + restorative). */
const GENTLE: ExerciseItem[] = [
  e('Easy walk', 1, '20-30 min', 'cardio'),
  e('Cat-Cow', 2, '8 breaths', 'mobility'),
  e("Child's pose", 1, '2 min', 'mobility'),
  e('Reclining twist (each side)', 1, '6 breaths', 'mobility'),
  e('Legs-up-the-wall', 1, '3 min', 'mobility'),
  e('Gentle full-body stretch', 1, '5 min', 'mobility'),
];

export function estimateCycleLen(starts: Date[]): number {
  if (starts.length < 2) return 28;
  const gaps: number[] = [];
  for (let i = 0; i < starts.length - 1; i++) {
    const g = Math.round((starts[i]!.getTime() - starts[i + 1]!.getTime()) / DAY_MS);
    if (g >= 21 && g <= 45) gaps.push(g);
  }
  if (gaps.length === 0) return 28;
  return Math.round(gaps.reduce((a, b) => a + b, 0) / gaps.length);
}

/**
 * Makes a weekly workout period-aware: any day that falls in the menstrual phase is
 * swapped to a gentle recovery session, matching the "keep it gentle on your period"
 * guidance. Non-period days are untouched. Pure.
 */
export function adaptWorkoutToCycle(plan: WeeklyWorkout, periodStarts: Date[], periodLen = 5): WeeklyWorkout {
  if (periodStarts.length === 0) return plan;
  const cycleLen = estimateCycleLen(periodStarts);
  const lastStart = periodStarts[0]!;

  let adapted = false;
  const days: WorkoutDay[] = plan.days.map((d) => {
    if (!d.date || d.rest) return d;
    const phase = computeCycle(lastStart, new Date(`${d.date}T12:00:00Z`), cycleLen, periodLen).phase;
    if (phase !== 'menstrual') return d;
    adapted = true;
    const base = d.label ? d.label.replace(/ · .*/, '') : undefined;
    return {
      ...d,
      label: base ? `${base} · Period` : 'Period',
      focus: '🌸 Period - gentle recovery',
      exercises: GENTLE,
    };
  });

  if (!adapted) return plan;
  return {
    ...plan,
    days,
    note: `🌸 Period-aware: your period days are eased to gentle recovery. ${plan.note}`,
  };
}
