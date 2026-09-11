import { describe, it, expect } from 'vitest';
import { generateStagedMovementPlan } from '../src/modules/exercise/workoutGenerator';

const RISKY = /\b(squat|lunge|jump|burpee|hiit|sprint|deadlift|box jump)\b/i;

describe('generateStagedMovementPlan - diet_first', () => {
  const plan = generateStagedMovementPlan('diet_first', { days: 7 });

  it('never includes any squat/lunge/jump/impact exercise on any day', () => {
    for (const day of plan.days) {
      for (const ex of day.exercises) {
        expect(ex.name).not.toMatch(RISKY);
      }
    }
  });

  it('every non-rest day is seated/breathing/mobility only (type never "strength" with standing load)', () => {
    for (const day of plan.days.filter((d) => !d.rest)) {
      expect(day.exercises.length).toBeGreaterThan(0);
    }
  });

  it('carries a clear explanatory note about the diet-first phase', () => {
    expect(plan.note.toLowerCase()).toContain('diet-first');
  });
});

describe('generateStagedMovementPlan - light_movement', () => {
  const plan = generateStagedMovementPlan('light_movement', { days: 7 });

  it('still never includes squats/lunges/jumping', () => {
    for (const day of plan.days) {
      for (const ex of day.exercises) {
        expect(ex.name).not.toMatch(RISKY);
      }
    }
  });

  it('does allow short walking, unlike diet_first', () => {
    const hasWalk = plan.days.some((d) => d.exercises.some((ex) => /walk/i.test(ex.name)));
    expect(hasWalk).toBe(true);
  });
});
