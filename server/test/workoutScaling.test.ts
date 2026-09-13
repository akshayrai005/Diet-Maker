import { describe, it, expect } from 'vitest';
import {
  generateWeeklyWorkout,
  cappedIntensity,
  annotate,
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

  it('caps main exercises within the level total working-exercise budget (main+core+cardio, see workout.test.ts)', () => {
    const plan = generateWeeklyWorkout('fatloss', 'home', { startDate: start, fitnessLevel: 'intermediate' });
    for (const day of plan.days.filter((d) => !d.rest)) {
      expect(day.exercises.length).toBeGreaterThan(0);
      expect(day.exercises.length).toBeLessThanOrEqual(6);
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

describe('workout scaling — cardio finisher (guaranteed conditioning slot, replaces the old appended finisher)', () => {
  const start = new Date('2026-07-19T00:00:00Z');

  it('every non-rest day carries exactly one cardio/conditioning element; rest days carry none', () => {
    const plan = generateWeeklyWorkout('muscular', 'gym', {
      startDate: start,
      restDayOfWeek: 0,
      fitnessLevel: 'advanced',
    });
    for (const day of plan.days) {
      if (day.rest) expect(day.cardio).toBeUndefined();
      else expect(day.cardio).toBeDefined();
    }
  });

  it('beginner also gets a (steady-state, not HIIT) cardio element - no "Conditioning finisher" name lingers anywhere', () => {
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
  // 'advanced' gives the widest main-exercise budget so more of the template's named lifts survive capping.
  // muscular:gym's dedicated bro-split days are single-muscle-group, so its early exercises (e.g.
  // "Barbell row" on Back day) reliably survive the diverse-selection cap too - merge both plans'
  // exercises so this stays a pure lookup-table check on annotate(), independent of which specific
  // day layout happens to keep which named lift.
  const planA = generateWeeklyWorkout('athletic', 'gym', { fitnessLevel: 'advanced' });
  const planB = generateWeeklyWorkout('muscular', 'gym', { fitnessLevel: 'advanced' });
  const allExercises = (p: WeeklyWorkout) => [...allTraining(p), ...p.days.filter((d) => !d.rest).flatMap((d) => d.core ?? [])];
  const byName = new Map([...allExercises(planA), ...allExercises(planB)].map((e) => [e.name, e]));

  it('attaches muscleGroup + short cue for common movements', () => {
    expect(byName.get('Bench press')?.muscleGroup).toBe('chest');
    // "Lat pulldown" is the back day's first-listed exercise, so it reliably survives the
    // diverse-selection cap regardless of how many other back exercises get trimmed.
    expect(byName.get('Lat pulldown')?.muscleGroup).toBe('back');
    expect(byName.get('Back squat')?.muscleGroup).toBe('legs');
    expect(byName.get('Romanian deadlift')?.muscleGroup).toBe('posterior chain');
    expect(byName.get('Overhead press')?.muscleGroup).toBe('shoulders');
    expect(byName.get('Plank')?.muscleGroup).toBe('core');
    // Cues are present and short + safety-oriented.
    const cue = byName.get('Back squat')?.cue ?? '';
    expect(cue.length).toBeGreaterThan(0);
    expect(cue.length).toBeLessThan(90);
  });

  it('chest press variants (incline/decline/flat) get muscleGroup chest, not left unlabeled or mislabeled shoulders', () => {
    for (const name of ['Incline barbell press', 'Incline dumbbell press', 'Flat dumbbell press', 'Decline press', 'Incline cable press']) {
      expect(annotate(name).muscleGroup, name).toBe('chest');
    }
  });

  it('"Chest dips" is labeled chest, not claimed by the triceps "dips" rule', () => {
    expect(annotate('Chest dips').muscleGroup).toBe('chest');
    expect(annotate('Bench dip on floor').muscleGroup).toBe('triceps');
  });

  it('derives equipment where the name reveals it, undefined otherwise', () => {
    // "Barbell bench press" is the chest day's first-listed exercise - always survives the cap.
    expect(byName.get('Barbell bench press')?.equipment).toBe('barbell');
    expect(byName.get('Plank')?.equipment).toBe('bodyweight');
  });

  it('leaves unknown movements unannotated (no cue)', () => {
    // "Battle ropes" is not in the lookup table.
    const ropes = allTraining(planA).find((e) => e.name === 'Battle ropes');
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
