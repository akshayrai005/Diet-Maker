import { describe, it, expect } from 'vitest';
import { exerciseMet, exerciseKcal, wellnessKcal } from '../src/calc/activityCalories';

describe('exerciseMet', () => {
  it('classifies cardio movements as high intensity', () => {
    expect(exerciseMet('Running')).toBe(8);
    expect(exerciseMet('Burpees')).toBe(8);
    expect(exerciseMet('Jumping Jacks')).toBe(8);
    expect(exerciseMet('Mountain Climbers')).toBe(8);
  });

  it('classifies mobility/stretch as low intensity', () => {
    expect(exerciseMet('Hamstring Stretch')).toBe(2.8);
    expect(exerciseMet('Plank hold')).toBe(2.8);
  });

  it('defaults resistance work to a moderate MET', () => {
    expect(exerciseMet('Barbell Bench Press')).toBe(5);
    expect(exerciseMet('Goblet Squat')).toBe(5);
  });

  it('a no-speed-logged brisk walk/cycle gets walk/cycle-pace MET, not the vigorous-cardio 8 (user-reported: a 20-min walk logged 197 kcal, ~2x a real brisk-walk estimate)', () => {
    expect(exerciseMet('Steady-state cardio (brisk walk/cycle)')).toBe(3.8);
    expect(exerciseMet('Brisk walk / jog')).toBe(3.8);
    expect(exerciseMet('Light walk')).toBe(3.8);
  });

  it('a no-speed-logged cycle-only name gets a moderate cycling MET, not vigorous-cardio 8', () => {
    expect(exerciseMet('Cycle Cross Trainer')).toBe(6);
    expect(exerciseMet('Incline walk / cycle')).toBe(3.8); // "walk" wins - it's listed first and is the gentler assumption
  });

  it('an explicit run/jog/sprint with no speed logged still gets the vigorous MET (not flattened to walking pace)', () => {
    expect(exerciseMet('Treadmill sprints')).toBe(8);
    expect(exerciseMet('Jog interval')).toBe(8);
  });

  it('speed-aware walking still overrides the flat default when speed IS logged', () => {
    // A real brisk pace (6 km/h) computed via the ACSM equation, not the flat 3.8 default.
    const met = exerciseMet('Steady-state cardio (brisk walk/cycle)', 6, 0);
    expect(met).toBeGreaterThan(3.8);
    expect(met).toBeLessThan(8);
  });
});

describe('exerciseKcal', () => {
  it('uses logged duration when present (cardio)', () => {
    // 8 MET × 70 kg × 0.5 h = 280
    expect(exerciseKcal({ name: 'Running', durationMin: 30, bodyWeightKg: 70 })).toBe(280);
  });

  it('estimates minutes from set count when no duration', () => {
    // 3 sets → round(3×1.8)=5 min; 5 MET × 70 × 5/60 ≈ 29
    expect(exerciseKcal({ name: 'Bench Press', sets: 3, reps: 8, weightKg: 40, bodyWeightKg: 70 })).toBe(29);
  });

  it('falls back to a default body weight when unknown', () => {
    // default 70 kg
    expect(exerciseKcal({ name: 'Running', durationMin: 30 })).toBe(280);
    expect(exerciseKcal({ name: 'Running', durationMin: 30, bodyWeightKg: null })).toBe(280);
  });

  it('is never negative and floors at a small session', () => {
    const k = exerciseKcal({ name: 'Bench Press', sets: 1, bodyWeightKg: 70 });
    expect(k).toBeGreaterThan(0);
  });
});

describe('wellnessKcal', () => {
  it('estimates a yoga flow', () => {
    // 2.5 × 70 × 12/60 = 35
    expect(wellnessKcal('yoga', 12, 70)).toBe(35);
  });

  it('estimates a short meditation', () => {
    // 1.3 × 70 × 4/60 ≈ 6
    expect(wellnessKcal('meditation', 4, 70)).toBe(6);
    expect(wellnessKcal('breathing', 4, 70)).toBe(6);
  });

  it('returns 0 for a zero-minute session', () => {
    expect(wellnessKcal('yoga', 0, 70)).toBe(0);
  });

  it('uses the default body weight when unknown', () => {
    expect(wellnessKcal('yoga', 12)).toBe(35);
  });
});
