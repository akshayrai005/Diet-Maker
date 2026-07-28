import { describe, it, expect } from 'vitest';
import {
  generateWeeklyWorkout,
  cappedIntensity,
} from '../src/modules/exercise/workoutGenerator';
import type { WeeklyWorkout } from '../src/modules/exercise/exercise.types';

/** Total prescribed sets across every training day (ignores rest days' light items too — sums all). */
function totalSets(plan: WeeklyWorkout): number {
  return plan.days
    .filter((d) => !d.rest)
    .reduce((sum, d) => sum + d.exercises.reduce((a, e) => a + e.sets, 0), 0);
}

function allTraining(plan: WeeklyWorkout) {
  return plan.days.filter((d) => !d.rest).flatMap((d) => d.exercises);
}

describe('cappedIntensity — SAFETY gate', () => {
  it('passes intensity through with no caution flags', () => {
    expect(cappedIntensity('beast')).toBe('beast');
    expect(cappedIntensity('hard')).toBe('hard');
    expect(cappedIntensity('standard')).toBe('standard');
    expect(cappedIntensity('easy')).toBe('easy');
  });

  it('clamps beast and hard down to standard for under-18', () => {
    expect(cappedIntensity('beast', { under18: true })).toBe('standard');
    expect(cappedIntensity('hard', { under18: true })).toBe('standard');
  });

  it('clamps beast and hard down to standard for medical caution', () => {
    expect(cappedIntensity('beast', { medicalCaution: true })).toBe('standard');
    expect(cappedIntensity('hard', { medicalCaution: true })).toBe('standard');
  });

  it('never pushes easy/standard below easy', () => {
    expect(cappedIntensity('easy', { under18: true, medicalCaution: true })).toBe('easy');
    expect(cappedIntensity('standard', { under18: true })).toBe('standard');
  });
});

describe('workout scaling — determinism', () => {
  it('same inputs => identical output', () => {
    const opts = {
      startDate: new Date('2026-07-19T00:00:00Z'),
      fitnessLevel: 'advanced' as const,
      intensity: 'beast' as const,
    };
    const a = generateWeeklyWorkout('muscular', 'gym', opts);
    const b = generateWeeklyWorkout('muscular', 'gym', opts);
    expect(JSON.stringify(a)).toBe(JSON.stringify(b));
  });
});

describe('workout scaling — level + intensity change the plan', () => {
  const start = new Date('2026-07-19T00:00:00Z');

  it('beginner vs advanced-beast on the same goal/location is genuinely heavier', () => {
    const beginner = generateWeeklyWorkout('muscular', 'gym', { startDate: start, fitnessLevel: 'beginner' });
    const beast = generateWeeklyWorkout('muscular', 'gym', {
      startDate: start,
      fitnessLevel: 'advanced',
      intensity: 'beast',
    });
    expect(totalSets(beast)).toBeGreaterThan(totalSets(beginner));
  });

  it('at advanced level, sets rise with intensity (easy < standard < beast)', () => {
    const mk = (intensity: 'easy' | 'standard' | 'beast') =>
      generateWeeklyWorkout('muscular', 'gym', { startDate: start, fitnessLevel: 'advanced', intensity });
    expect(totalSets(mk('easy'))).toBeLessThan(totalSets(mk('standard')));
    expect(totalSets(mk('standard'))).toBeLessThan(totalSets(mk('beast')));
  });

  it('beginner caps sets at 3; every day offers a 7-exercise menu to pick from', () => {
    const plan = generateWeeklyWorkout('muscular', 'gym', {
      startDate: start,
      fitnessLevel: 'beginner',
      intensity: 'beast', // even beast is held down for a beginner
    });
    for (const day of plan.days.filter((d) => !d.rest)) {
      // The main block is padded to a 7-exercise menu (user picks any ~5), never more.
      const main = day.exercises.filter((e) => e.name !== 'Conditioning finisher');
      expect(main.length).toBeLessThanOrEqual(7);
      for (const ex of day.exercises) expect(ex.sets).toBeLessThanOrEqual(3);
    }
  });

  it('pads every training day to a 7-exercise menu (pick any ~5)', () => {
    const plan = generateWeeklyWorkout('fatloss', 'home', { startDate: start, fitnessLevel: 'intermediate' });
    for (const day of plan.days.filter((d) => !d.rest)) {
      expect(day.exercises.length).toBe(7);
    }
  });

  it('sets never exceed the global ceiling of 6', () => {
    const plan = generateWeeklyWorkout('muscular', 'gym', {
      startDate: start,
      fitnessLevel: 'advanced',
      intensity: 'beast',
    });
    for (const ex of allTraining(plan)) expect(ex.sets).toBeLessThanOrEqual(6);
  });
});

describe('workout scaling — finisher', () => {
  const start = new Date('2026-07-19T00:00:00Z');

  it('advanced/beast appends ONE finisher on every non-rest day, never on rest days', () => {
    const plan = generateWeeklyWorkout('muscular', 'gym', {
      startDate: start,
      restDayOfWeek: 0,
      fitnessLevel: 'advanced',
    });
    for (const day of plan.days) {
      const finishers = day.exercises.filter((e) => e.name === 'Conditioning finisher');
      if (day.rest) expect(finishers).toHaveLength(0);
      else expect(finishers).toHaveLength(1);
    }
  });

  it('beginner never gets a finisher', () => {
    const plan = generateWeeklyWorkout('muscular', 'gym', {
      startDate: start,
      fitnessLevel: 'beginner',
      intensity: 'beast',
    });
    expect(allTraining(plan).some((e) => e.name === 'Conditioning finisher')).toBe(false);
  });
});

describe('workout scaling — under-18 safety cap forces beast down', () => {
  const start = new Date('2026-07-19T00:00:00Z');

  it('a minor on beast gets the exact standard plan (fewer sets, no finisher)', () => {
    const beast = generateWeeklyWorkout('muscular', 'gym', {
      startDate: start,
      fitnessLevel: 'intermediate',
      intensity: 'beast',
    });
    const minorBeast = generateWeeklyWorkout('muscular', 'gym', {
      startDate: start,
      fitnessLevel: 'intermediate',
      intensity: 'beast',
      under18: true,
    });
    const standard = generateWeeklyWorkout('muscular', 'gym', {
      startDate: start,
      fitnessLevel: 'intermediate',
      intensity: 'standard',
    });
    expect(JSON.stringify(minorBeast)).toBe(JSON.stringify(standard));
    expect(totalSets(minorBeast)).toBeLessThan(totalSets(beast));
  });

  it('medical caution clamps main-lift intensity to standard (core is gentler by design)', () => {
    const minorMed = generateWeeklyWorkout('muscular', 'gym', {
      startDate: start,
      fitnessLevel: 'intermediate',
      intensity: 'hard',
      medicalCaution: true,
    });
    const standard = generateWeeklyWorkout('muscular', 'gym', {
      startDate: start,
      fitnessLevel: 'intermediate',
      intensity: 'standard',
    });
    // Intensity clamp: the MAIN lifts (sets × reps) match the explicitly-standard plan.
    const mains = (p: ReturnType<typeof generateWeeklyWorkout>) =>
      p.days.map((d) => d.exercises.map((e) => `${e.name}:${e.sets}x${e.reps}`));
    expect(mains(minorMed)).toEqual(mains(standard));
    // Medical caution additionally gets a gentler, back-friendly core — a deliberate difference.
    const day = minorMed.days.find((d) => !d.rest)!;
    expect(day.core!.some((e) => /dead bug|bird dog|glute bridge/i.test(e.name))).toBe(true);
  });
});

describe('workout scaling — form cues / annotations', () => {
  // athletic:gym is a single (non-rotating) block, so names are deterministic across dates.
  const plan = generateWeeklyWorkout('athletic', 'gym', {});
  const byName = new Map(allTraining(plan).map((e) => [e.name, e]));

  it('attaches muscleGroup + short cue for common movements', () => {
    expect(byName.get('Bench press')?.muscleGroup).toBe('chest');
    expect(byName.get('Barbell row')?.muscleGroup).toBe('back');
    expect(byName.get('Back squat')?.muscleGroup).toBe('legs');
    expect(byName.get('Romanian deadlift')?.muscleGroup).toBe('posterior chain');
    expect(byName.get('Overhead press')?.muscleGroup).toBe('shoulders');
    expect(byName.get('Plank')?.muscleGroup).toBe('core');
    // Cues are present and short + safety-oriented.
    const cue = byName.get('Back squat')?.cue ?? '';
    expect(cue.length).toBeGreaterThan(0);
    expect(cue.length).toBeLessThan(90);
  });

  it('derives equipment where the name reveals it, undefined otherwise', () => {
    expect(byName.get('Barbell row')?.equipment).toBe('barbell');
    expect(byName.get('Plank')?.equipment).toBe('bodyweight');
  });

  it('leaves unknown movements unannotated (no cue)', () => {
    // "Battle ropes" is not in the lookup table.
    const ropes = allTraining(plan).find((e) => e.name === 'Battle ropes');
    if (ropes) expect(ropes.cue).toBeUndefined();
  });
});

describe('workout scaling — rest guidance in the note', () => {
  it('beginners get a longer rest note (~90-120s)', () => {
    const plan = generateWeeklyWorkout('muscular', 'gym', { fitnessLevel: 'beginner' });
    expect(plan.note).toMatch(/90-120s/);
  });

  it('advanced / hard get a shorter rest note (~45-60s)', () => {
    const adv = generateWeeklyWorkout('muscular', 'gym', { fitnessLevel: 'advanced' });
    expect(adv.note).toMatch(/45-60s/);
    const hard = generateWeeklyWorkout('muscular', 'gym', { fitnessLevel: 'intermediate', intensity: 'hard' });
    expect(hard.note).toMatch(/45-60s/);
  });
});
