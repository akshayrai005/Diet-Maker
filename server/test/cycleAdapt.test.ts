import { describe, it, expect } from 'vitest';
import { adaptWorkoutToCycle, GENTLE } from '../src/modules/exercise/cycleAdapt';
import type { WeeklyWorkout } from '../src/modules/exercise/exercise.types';

const day = (date: string, rest = false) => ({
  dayIndex: 0,
  date,
  label: 'Workout',
  focus: 'Chest',
  rest,
  exercises: [{ name: 'Bench press', sets: 4, reps: '8', type: 'strength' as const }],
});

const plan: WeeklyWorkout = {
  location: 'gym',
  goal: 'muscular',
  days: [day('2026-06-01'), day('2026-06-05'), day('2026-06-10')],
  block: 0,
  blockLabel: '',
  note: 'base note',
  disclaimer: 'd',
};

describe('adaptWorkoutToCycle', () => {
  it('eases period days to gentle recovery and leaves other days alone', () => {
    const out = adaptWorkoutToCycle(plan, [new Date('2026-06-01T12:00:00Z')], 5);
    // Day 1 and day 5 are within the 5-day period → gentle.
    expect(out.days[0]!.focus).toContain('Period');
    expect(out.days[0]!.exercises.some((e) => /walk/i.test(e.name))).toBe(true);
    expect(out.days[1]!.focus).toContain('Period');
    // Day 10 is follicular → untouched.
    expect(out.days[2]!.focus).toBe('Chest');
    expect(out.note).toContain('Period-aware');
  });

  it('is a no-op with no logged periods', () => {
    expect(adaptWorkoutToCycle(plan, [])).toBe(plan);
  });

  it('clears the leftover core/cardio from the original (un-eased) day - a period day should only carry the gentle recovery moves, not gentle exercises PLUS the original intense core+cardio', () => {
    const busyDay = { ...plan.days[0]!, core: [{ name: 'Plank', sets: 3, reps: '60s', type: 'strength' as const }], cardio: { name: 'HIIT conditioning', sets: 1, reps: '6 rounds', type: 'cardio' as const } };
    const out = adaptWorkoutToCycle({ ...plan, days: [busyDay, plan.days[1]!, plan.days[2]!] }, [new Date('2026-06-01T12:00:00Z')], 5);
    expect(out.days[0]!.core).toEqual([]);
    expect(out.days[0]!.cardio).toBeUndefined();
    expect(out.days[0]!.exercises).toEqual(GENTLE);
  });

  it('a period day that was labelled "Today" keeps a label starting with "Today" - the Android app matches this by prefix, not equality, so it must never rename it to something else entirely', () => {
    const todayPlan: WeeklyWorkout = {
      ...plan,
      days: [{ ...plan.days[0]!, label: 'Today' }],
    };
    const out = adaptWorkoutToCycle(todayPlan, [new Date('2026-06-01T12:00:00Z')], 5);
    expect(out.days[0]!.label?.startsWith('Today')).toBe(true);
  });
});
