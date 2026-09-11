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

  it('caps total working exercises (main+core+cardio, not warmup/cooldown) to a realistic 5-7 per day, across every goal/location/level/split', () => {
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
            for (const day of plan.days.filter((d) => !d.rest)) {
              const working = day.exercises.length + (day.core?.length ?? 0) + (day.cardio ? 1 : 0);
              expect(working, `${goal}/${location}/${level}/${split}/${day.focus}`).toBeLessThanOrEqual(7);
              expect(working, `${goal}/${location}/${level}/${split}/${day.focus}`).toBeGreaterThanOrEqual(3);
            }
          }
        }
      }
    }
  });

  it('total displayed items (incl. warmup/cooldown) never balloons to 14-15+ on a normal day', () => {
    const plan = generateWeeklyWorkout('muscular', 'gym', { fitnessLevel: 'advanced', intensity: 'beast' });
    for (const day of plan.days.filter((d) => !d.rest)) {
      const total = day.exercises.length + (day.core?.length ?? 0) + (day.cardio ? 1 : 0) + (day.warmup?.length ?? 0) + (day.cooldown?.length ?? 0);
      expect(total).toBeLessThanOrEqual(14);
    }
  });
});
