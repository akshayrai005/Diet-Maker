import { describe, it, expect } from 'vitest';
import { generateWeeklyWorkout } from '../src/modules/exercise/workoutGenerator';
import { infoFor } from '../src/modules/exercise/exerciseInfo';

const MAP = { 0: 'Back & Biceps', 1: 'Mix (weak point)', 2: 'Arms & Core', 3: 'Rest', 4: 'Chest & Triceps', 5: 'Legs & Abs', 6: 'Shoulders & Triceps' };
const START = new Date(Date.UTC(2026, 8, 13));

describe('every generated exercise carries coaching info', () => {
  for (const level of ['beginner', 'intermediate', 'advanced'] as const) {
    it(`${level}: pattern, setup, mistakes and safety present; beginners never get an advanced-difficulty lift`, () => {
      const plan = generateWeeklyWorkout('muscular', 'gym', { split: 'body_part', fitnessLevel: level, startDate: START, today: START, dayFocusOverride: MAP });
      let n = 0;
      for (const day of plan.days) {
        for (const ex of day.exercises as { name: string; info?: ReturnType<typeof infoFor> }[]) {
          n++;
          expect(ex.info, ex.name).toBeDefined();
          expect(ex.info!.setup.length, ex.name).toBeGreaterThan(10);
          expect(ex.info!.mistakes.length, ex.name).toBeGreaterThan(0);
          expect(ex.info!.safety.length, ex.name).toBeGreaterThan(10);
          if (level === 'beginner') expect(ex.info!.difficulty, ex.name).not.toBe('advanced');
        }
      }
      expect(n).toBeGreaterThan(30);
    });
  }

  it('abs guidance never presents spot reduction as real', () => {
    expect(infoFor('Cable crunch').safety).toMatch(/spot reduction does not work/);
  });

  it('unknown names still resolve, by muscle group or generically', () => {
    expect(infoFor('Totally new lift', 'chest').pattern).toBe('horizontal push');
    expect(infoFor('Totally new lift').safety).toMatch(/physiotherapist or doctor/);
  });
});
