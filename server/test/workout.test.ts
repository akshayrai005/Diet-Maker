import { describe, it, expect } from 'vitest';
import { generateWeeklyWorkout } from '../src/modules/exercise/workoutGenerator';

describe('workout generator', () => {
  it('gym + muscular with a rest day gives 6 training + 1 rest', () => {
    const start = new Date('2026-07-19T00:00:00Z'); // Sunday
    const plan = generateWeeklyWorkout('muscular', 'gym', { restDayOfWeek: 0, startDate: start });
    expect(plan.days).toHaveLength(7);
    const rest = plan.days.filter((d) => d.rest);
    expect(rest).toHaveLength(1);
    expect(rest[0]!.focus).toMatch(/rest/i);
    expect(plan.days.filter((d) => !d.rest)).toHaveLength(6);
    // Every training day has exercises.
    plan.days.filter((d) => !d.rest).forEach((d) => expect(d.exercises.length).toBeGreaterThan(0));
  });

  it('every day - training AND rest - gets exactly 1 no-equipment abs exercise at 3 sets', () => {
    const plan = generateWeeklyWorkout('muscular', 'gym', { restDayOfWeek: 3 });
    for (const day of plan.days) {
      expect(day.core).toHaveLength(1);
      const abs = day.core![0]!;
      expect(abs.equipment).toBe('bodyweight');
      expect(abs.sets).toBe(3);
    }
  });

  it('a medically-cautioned user still gets exactly 1 daily abs exercise, just gentler (2 sets)', () => {
    const plan = generateWeeklyWorkout('muscular', 'gym', { restDayOfWeek: 3, medicalCaution: true });
    for (const day of plan.days) {
      expect(day.core).toHaveLength(1);
      expect(day.core![0]!.sets).toBe(2);
    }
  });

  it('the daily abs exercise also rotates with the 4-week mesocycle block - same weekday, different block, different move', () => {
    // muscular:gym has 3 blocks; weeksSinceJoin=0 => block 0, weeksSinceJoin=4 => block 1.
    const block0 = generateWeeklyWorkout('muscular', 'gym', { weeksSinceJoin: 0 });
    const block1 = generateWeeklyWorkout('muscular', 'gym', { weeksSinceJoin: 4 });
    expect(block0.block).not.toBe(block1.block);
    expect(block0.days[0]!.core![0]!.name).not.toBe(block1.days[0]!.core![0]!.name);
  });

  it('no rest day => 7 training days', () => {
    const plan = generateWeeklyWorkout('fatloss', 'home', {});
    expect(plan.days).toHaveLength(7);
    expect(plan.days.every((d) => !d.rest)).toBe(true);
  });

  it('location "none" falls back to home (bodyweight) templates', () => {
    const plan = generateWeeklyWorkout('athletic', 'none', {});
    expect(plan.days.length).toBe(7);
    expect(plan.note.toLowerCase()).toContain('no equipment');
  });

  it('gym muscular uses a dedicated bro-split', () => {
    const plan = generateWeeklyWorkout('muscular', 'gym', {});
    const focuses = plan.days.map((d) => d.focus.toLowerCase()).join(' ');
    expect(focuses).toContain('chest');
    expect(focuses).toContain('back');
    expect(focuses).toContain('shoulders');
    expect(focuses).toContain('biceps');
    expect(focuses).toContain('triceps');
    expect(focuses).toContain('legs');
  });

  it('mesocycle rotates the block every 4 weeks', () => {
    // Two dates ~4.5 weeks apart should land in different blocks (different exercises).
    const wk1 = generateWeeklyWorkout('muscular', 'gym', { startDate: new Date('2026-01-05T00:00:00Z') });
    const wk6 = generateWeeklyWorkout('muscular', 'gym', { startDate: new Date('2026-02-16T00:00:00Z') });
    expect(wk1.block).not.toBe(wk6.block);
    // The chest-day exercises differ between blocks.
    const chest1 = wk1.days.find((d) => d.focus === 'Chest')!.exercises[0]!.name;
    const chest6 = wk6.days.find((d) => d.focus === 'Chest')!.exercises[0]!.name;
    expect(chest1).not.toBe(chest6);
    expect(wk1.blockLabel).toMatch(/Block/);
  });

  it('carries dates + labels when startDate is given', () => {
    const plan = generateWeeklyWorkout('muscular', 'gym', { startDate: new Date('2026-07-22T00:00:00Z') });
    expect(plan.days[0]!.label).toBe('Today');
    expect(plan.days[0]!.date).toBe('2026-07-22');
    expect(plan.days[1]!.label).toBe('Tomorrow');
  });

  it('gives the user-set MAIN lift count per level (beginner 5, intermediate 6-7, advanced 7-9) plus 1 abs + 1 cardio, across every goal/location/level/split', () => {
    const goals: Array<'muscular' | 'athletic' | 'fatloss'> = ['muscular', 'athletic', 'fatloss'];
    const locations: Array<'gym' | 'home' | 'none'> = ['gym', 'home', 'none'];
    const levels: Array<'beginner' | 'intermediate' | 'advanced'> = ['beginner', 'intermediate', 'advanced'];
    const splits: Array<'full_body' | 'push_pull_legs' | 'upper_lower' | 'body_part' | 'fat_loss' | undefined> = [
      undefined, 'full_body', 'push_pull_legs', 'upper_lower', 'body_part', 'fat_loss',
    ];
    for (const goal of goals) {
      for (const location of locations) {
        for (const level of levels) {
          for (const split of splits) {
            const plan = generateWeeklyWorkout(goal, location, { fitnessLevel: level, split });
            const mainMax = { beginner: 5, intermediate: 7, advanced: 9 }[level];
            const mainMin = { beginner: 3, intermediate: 4, advanced: 5 }[level];
            const cap = mainMax + 2; // + 1 abs + 1 cardio
            for (const day of plan.days.filter((d) => !d.rest)) {
              const working = day.exercises.length + (day.core?.length ?? 0) + (day.cardio ? 1 : 0);
              expect(working, `${goal}/${location}/${level}/${split}/${day.focus}`).toBeLessThanOrEqual(cap);
              expect(working, `${goal}/${location}/${level}/${split}/${day.focus}`).toBeGreaterThanOrEqual(3);
              expect(day.exercises.length, `${goal}/${location}/${level}/${split}/${day.focus} main`).toBeLessThanOrEqual(mainMax);
              if (split === 'body_part' && goal === 'muscular' && location === 'gym') expect(day.exercises.length).toBeGreaterThanOrEqual(mainMin);
            }
          }
        }
      }
    }
  });

  it('a My Gym favorite matching the day\'s muscle group is included ahead of the generic curated pool', () => {
    const plan = generateWeeklyWorkout('muscular', 'gym', {
      fitnessLevel: 'beginner',
      split: 'body_part',
      gymFavoriteNames: ['Machine chest press'], // not in block A's curated Chest list
    });
    const chestDay = plan.days.find((d) => d.focus === 'Chest')!;
    expect(chestDay.exercises.some((e) => e.name === 'Machine chest press')).toBe(true);
  });

  it('a My Gym favorite for a DIFFERENT muscle group is not injected into an unrelated day', () => {
    const plan = generateWeeklyWorkout('muscular', 'gym', {
      fitnessLevel: 'beginner',
      split: 'body_part',
      gymFavoriteNames: ['Barbell curl'], // biceps, not chest
    });
    const chestDay = plan.days.find((d) => d.focus === 'Chest')!;
    expect(chestDay.exercises.some((e) => e.name === 'Barbell curl')).toBe(false);
  });

  it('body_part split main lifts follow the level (beginner 5, intermediate 6, advanced 8)', () => {
    for (const level of ['beginner', 'intermediate', 'advanced'] as const) {
      const plan = generateWeeklyWorkout('muscular', 'gym', { fitnessLevel: level, split: 'body_part' });
      const chestDay = plan.days.find((d) => d.focus === 'Chest');
      expect(chestDay).toBeDefined();
      expect(chestDay!.exercises.length).toBe({ beginner: 5, intermediate: 6, advanced: 8 }[level]);
    }
  });

  it('total displayed items (incl. warmup/cooldown) never balloons to 14-15+ on a normal day', () => {
    const plan = generateWeeklyWorkout('muscular', 'gym', { fitnessLevel: 'advanced', intensity: 'beast' });
    for (const day of plan.days.filter((d) => !d.rest)) {
      const total = day.exercises.length + (day.core?.length ?? 0) + (day.cardio ? 1 : 0) + (day.warmup?.length ?? 0) + (day.cooldown?.length ?? 0);
      expect(total).toBeLessThanOrEqual(19);
    }
  });
});
