import { describe, it, expect } from 'vitest';
import { generateWeeklyWorkout } from '../src/modules/exercise/workoutGenerator';

const MAP = { 0: 'Back & Biceps', 1: 'Legs & Abs', 2: 'Arms & Core', 3: 'Rest', 4: 'Chest & Triceps', 5: 'Legs & Abs', 6: 'Shoulders & Triceps' };
const START = new Date(Date.UTC(2026, 8, 13)); // a Sunday

function week(level: 'beginner' | 'intermediate' | 'advanced' = 'intermediate') {
  return generateWeeklyWorkout('muscular', 'gym', {
    split: 'body_part', fitnessLevel: level, startDate: START, today: START, dayFocusOverride: MAP,
  });
}
const groups = (d: { exercises: { muscleGroup?: string }[] }) => d.exercises.map((e) => e.muscleGroup);
const count = (d: { exercises: { muscleGroup?: string }[] }, g: string) => groups(d).filter((x) => x === g).length;

describe('user split: back+biceps / chest+triceps / shoulders+triceps / arms+core / legs+abs', () => {
  const plan = week();
  const byLabel = (label: string) => plan.days.find((d) => d.focus === label)!;

  it('lays the week out exactly as requested, rest on Wednesday', () => {
    expect(plan.days.map((d) => (d.rest ? 'Rest' : d.focus))).toEqual([
      'Back & Biceps', 'Legs & Abs', 'Arms & Core', 'Rest', 'Chest & Triceps', 'Legs & Abs', 'Shoulders & Triceps',
    ]);
  });

  it('chest is Thursday, shoulders Saturday - one day between them', () => {
    const idx = (f: string) => plan.days.findIndex((d) => d.focus === f);
    expect(plan.days[idx('Chest & Triceps')]!.date).toBeDefined();
    expect(idx('Shoulders & Triceps') - idx('Chest & Triceps')).toBe(2);
    expect(plan.days[5]!.focus).not.toBe('Chest & Triceps');
    expect(plan.days[5]!.focus).not.toBe('Shoulders & Triceps');
  });

  it('back day has 3 biceps exercises', () => {
    const d = byLabel('Back & Biceps');
    expect(count(d, 'biceps')).toBe(3);
    expect(count(d, 'back')).toBeGreaterThanOrEqual(3);
  });

  it('chest day has 3 triceps exercises', () => {
    const d = byLabel('Chest & Triceps');
    expect(count(d, 'triceps')).toBe(3);
    expect(count(d, 'chest')).toBeGreaterThanOrEqual(3);
  });

  it('shoulder day has 3 triceps exercises, all different from chest day', () => {
    const chest = byLabel('Chest & Triceps');
    const sh = byLabel('Shoulders & Triceps');
    expect(count(sh, 'triceps')).toBe(3);
    const chestTri = new Set(chest.exercises.filter((e) => e.muscleGroup === 'triceps').map((e) => e.name));
    const shTri = sh.exercises.filter((e) => e.muscleGroup === 'triceps').map((e) => e.name);
    for (const n of shTri) expect(chestTri.has(n)).toBe(false);
    expect(count(sh, 'shoulders') + count(sh, 'traps')).toBeGreaterThanOrEqual(3);
  });

  it('arms day mixes biceps, triceps and core', () => {
    const d = byLabel('Arms & Core');
    expect(count(d, 'biceps')).toBeGreaterThanOrEqual(2);
    expect(count(d, 'triceps')).toBeGreaterThanOrEqual(2);
    expect(count(d, 'core')).toBeGreaterThanOrEqual(1);
  });

  it('legs days have legs + abs, and the two leg days differ', () => {
    const legs = plan.days.filter((d) => d.focus === 'Legs & Abs');
    expect(legs.length).toBe(2);
    expect(legs[0]!.exercises.map((e) => e.name).join('|')).not.toBe(legs[1]!.exercises.map((e) => e.name).join('|'));
    for (const d of legs) expect((d.core?.length ?? 0)).toBeGreaterThanOrEqual(1);
  });

  it('no duplicate exercise within a day and 6 main lifts for intermediate', () => {
    for (const d of plan.days.filter((x) => !x.rest)) {
      const names = d.exercises.map((e) => e.name);
      expect(new Set(names).size, d.focus).toBe(names.length);
      expect(names.length, d.focus).toBe(6);
    }
  });

  it('beginner and advanced levels still produce every day with sensible sizes', () => {
    for (const [lvl, n] of [['beginner', 5], ['advanced', 8]] as const) {
      const p = week(lvl);
      for (const d of p.days.filter((x) => !x.rest)) expect(d.exercises.length, `${lvl}/${d.focus}`).toBeLessThanOrEqual(n + 1);
    }
  });

  it('prints the week', () => {
    for (const d of plan.days) {
      // eslint-disable-next-line no-console
      console.log(`${(d.date ?? '').slice(5)} ${String(d.focus).padEnd(20)} ${d.rest ? '' : d.exercises.map((e) => `${e.name}[${e.muscleGroup}]`).join(', ')}`);
    }
  });
});
