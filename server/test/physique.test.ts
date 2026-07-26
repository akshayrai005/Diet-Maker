import { describe, it, expect } from 'vitest';
import { physiqueNutrition, applyMusclePriority, maxSetsForLevel } from '../src/modules/exercise/physique';
import { generateWeeklyWorkout } from '../src/modules/exercise/workoutGenerator';
import type { WeeklyWorkout } from '../src/modules/exercise/exercise.types';

describe('physiqueNutrition — safe calorie/protein mapping', () => {
  it('lean_bulk = surplus (gain) + high protein', () => {
    const r = physiqueNutrition('lean_bulk');
    expect(r.mappedGoal).toBe('gain');
    expect(r.proteinPerKgHint).toBeGreaterThanOrEqual(1.6);
    expect(r.blocked).toBe(false);
  });
  it('cut = deficit (lose) + very high protein for adults', () => {
    const r = physiqueNutrition('cut');
    expect(r.mappedGoal).toBe('lose');
    expect(r.proteinPerKgHint).toBeGreaterThanOrEqual(1.8);
  });
  it('recomp = maintenance calories + high protein', () => {
    const r = physiqueNutrition('recomp');
    expect(r.mappedGoal).toBe('maintain');
    expect(r.proteinPerKgHint).toBeGreaterThanOrEqual(1.8);
  });
  it('a MINOR cannot cut — downgraded to maintenance (blocked)', () => {
    const r = physiqueNutrition('cut', { isMinor: true });
    expect(r.mappedGoal).toBe('maintain');
    expect(r.blocked).toBe(true);
    expect(r.note).toMatch(/isn.t safe/i);
  });
  it('a medically weight-loss-blocked user cannot cut', () => {
    expect(physiqueNutrition('cut', { weightLossBlocked: true }).mappedGoal).toBe('maintain');
  });
});

describe('applyMusclePriority — extra volume within caps', () => {
  const base = (): WeeklyWorkout => generateWeeklyWorkout('muscular', 'gym', { fitnessLevel: 'intermediate' });

  it('adds a set to priority muscle groups but never above the level cap', () => {
    const plan = base();
    const cap = maxSetsForLevel('intermediate');
    const boosted = applyMusclePriority(plan, ['shoulders'], cap);
    // find shoulder exercises in both and compare sets
    const shoulderSets = (p: WeeklyWorkout) =>
      p.days.flatMap((d) => d.exercises).filter((e) => (e.muscleGroup ?? '').includes('shoulders')).map((e) => e.sets);
    const before = shoulderSets(plan);
    const after = shoulderSets(boosted);
    expect(after.length).toBe(before.length);
    // at least one shoulder exercise gained a set (unless already at cap)
    const gained = after.some((s, i) => s > (before[i] ?? 0));
    if (before.some((s) => s < cap)) expect(gained).toBe(true);
    // never exceeds the cap
    expect(after.every((s) => s <= cap)).toBe(true);
  });

  it('"arms" maps to biceps/triceps via synonyms', () => {
    const plan = base();
    const boosted = applyMusclePriority(plan, ['arms'], maxSetsForLevel('intermediate'));
    expect(boosted.note).toMatch(/Prioritising arms/i);
  });

  it('does nothing (same object) when no priorities', () => {
    const plan = base();
    expect(applyMusclePriority(plan, [], 5)).toBe(plan);
    expect(applyMusclePriority(plan, undefined, 5)).toBe(plan);
  });

  it('non-priority (antagonist) groups are left untouched — balance preserved', () => {
    const plan = base();
    const boosted = applyMusclePriority(plan, ['shoulders'], maxSetsForLevel('intermediate'));
    const legSets = (p: WeeklyWorkout) => p.days.flatMap((d) => d.exercises).filter((e) => (e.muscleGroup ?? '').includes('legs')).map((e) => e.sets);
    expect(legSets(boosted)).toEqual(legSets(plan));
  });

  it('priority via the generator options plumbs through', () => {
    const plan = generateWeeklyWorkout('muscular', 'gym', { fitnessLevel: 'advanced', priorityMuscles: ['back'] });
    // note reflects prioritisation (when any back exercise was below cap)
    expect(plan.note.toLowerCase()).toContain('back');
  });
});
