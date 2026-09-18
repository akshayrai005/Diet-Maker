import { describe, it, expect } from 'vitest';
import { estimate1RM, strengthTrend, substitutionsFor } from '../src/modules/exercise/strength';
import { generateWeeklyWorkout } from '../src/modules/exercise/workoutGenerator';

describe('estimate1RM (Epley)', () => {
  it('1 rep = the weight itself', () => {
    expect(estimate1RM(100, 1)).toBe(100);
  });
  it('scales up with reps', () => {
    expect(estimate1RM(100, 5)).toBeCloseTo(116.7, 1); // 100*(1+5/30)
    expect(estimate1RM(60, 10)).toBeCloseTo(80, 1);
  });
  it('rejects invalid / out-of-range', () => {
    expect(estimate1RM(0, 5)).toBeNull();
    expect(estimate1RM(100, 0)).toBeNull();
    expect(estimate1RM(100, 20)).toBeNull(); // past ~12 reps
    expect(estimate1RM(NaN, 5)).toBeNull();
  });
});

describe('strengthTrend', () => {
  it('takes the best est-1RM per day and reports change', () => {
    const logs = [
      { exerciseName: 'Bench Press', weightKg: 60, reps: 5, performedAt: '2026-07-01T00:00:00Z' }, // 70
      { exerciseName: 'bench press', weightKg: 65, reps: 5, performedAt: '2026-07-08T00:00:00Z' }, // 75.8
      { exerciseName: 'Bench Press', weightKg: 50, reps: 5, performedAt: '2026-07-08T00:00:00Z' }, // lower same day → ignored
      { exerciseName: 'Squat', weightKg: 80, reps: 3, performedAt: '2026-07-05T00:00:00Z' },
    ];
    const t = strengthTrend(logs);
    const bench = t.find((x) => x.exerciseName.toLowerCase() === 'bench press')!;
    expect(bench.points).toHaveLength(2); // two distinct days
    expect(bench.change).toBeGreaterThan(0);
    expect(bench.best).toBeCloseTo(bench.latest, 1);
  });
  it('ignores unweighted / invalid logs', () => {
    const t = strengthTrend([
      { exerciseName: 'Plank', weightKg: null, reps: null, performedAt: '2026-07-01T00:00:00Z' },
      { exerciseName: 'Row', weightKg: 40, reps: 30, performedAt: '2026-07-01T00:00:00Z' }, // reps>12 → null
    ]);
    expect(t).toHaveLength(0);
  });
});

describe('substitutionsFor', () => {
  it('offers equipment-free / injury-friendly alternatives', () => {
    expect(substitutionsFor('Barbell bench press').length).toBeGreaterThan(0);
    expect(substitutionsFor('Back squat').join(' ')).toMatch(/bodyweight|split|wall/i);
    expect(substitutionsFor('Deadlift').join(' ')).toMatch(/glute|hip|back-friendly/i);
  });
  it('returns [] for an unknown movement', () => {
    expect(substitutionsFor('Interpretive dance')).toEqual([]);
  });

  it('covers every curated exercise name in the body-part program - none silently fall through with no swap option', () => {
    // The full set of curated exercise names, gathered from every focus across all 3 mesocycle
    // blocks of muscular:gym (see workoutGenerator.ts PROGRAMS['muscular:gym']) plus EXTRA_POOL.
    const names = [
      'Barbell bench press', 'Incline dumbbell press', 'Cable fly', 'Chest dips', 'Push-up burnout',
      'Deadlift', 'Lat pulldown', 'Barbell row', 'Seated cable row', 'Face pull',
      'Overhead barbell press', 'Lateral raise', 'Rear-delt fly', 'Front raise', 'Barbell shrugs',
      'Barbell curl', 'Incline dumbbell curl', 'Hammer curl', 'Concentration curl', 'Wrist curl',
      'Close-grip bench press', 'Rope pushdown', 'Overhead extension', 'Bench dips',
      'Incline barbell press', 'Flat dumbbell press', 'Pec-deck fly', 'Decline press', 'Cable crossover',
      "Pull-ups (weighted)", 'T-bar row', 'Single-arm dumbbell row', 'Straight-arm pulldown', 'Back extension', 'Superman',
      'Arnold press', 'Cable lateral raise', 'Reverse pec-deck', 'Upright row', 'Dumbbell shrugs',
      'Push press', 'Machine shoulder press', 'Leaning cable lateral', 'Rear-delt row',
      "Farmer's carry", 'Skull crushers', 'Single-arm pushdown', 'Kickbacks', 'Diamond push-ups',
      'Rack pulls', 'Wide-grip pulldown', 'Chest-supported row', 'Cable pullover', 'Reverse fly',
      'Standing calf raise', 'Seated calf raise', 'Leg extension', 'Hanging leg raise', 'Russian twist',
      'Svend press', 'JM press', 'Hanging knee raise',
    ];
    const missing = names.filter((n) => substitutionsFor(n).length === 0);
    expect(missing, `no substitutions for: ${missing.join(', ')}`).toEqual([]);
  });
});

describe('workout gains warm-up, cool-down, cardio & substitutions', () => {
  const plan = generateWeeklyWorkout('muscular', 'gym', { fitnessLevel: 'intermediate' });
  const training = plan.days.filter((d) => !d.rest);

  it('every training day has a warm-up and a cool-down', () => {
    for (const d of training) {
      expect(d.warmup && d.warmup.length).toBeGreaterThan(0);
      expect(d.cooldown && d.cooldown.length).toBeGreaterThan(0);
    }
  });
  it('every training day includes a cardio element', () => {
    for (const d of training) expect(d.cardio?.type).toBe('cardio');
  });
  it('main strength exercises carry substitutions where known', () => {
    const anySub = training.flatMap((d) => d.exercises).some((e) => (e.substitutions?.length ?? 0) > 0);
    expect(anySub).toBe(true);
  });
  it('HIIT only appears at hard/beast without medical caution', () => {
    const hard = generateWeeklyWorkout('muscular', 'gym', { intensity: 'beast' }).days.filter((d) => !d.rest);
    const gentle = generateWeeklyWorkout('muscular', 'gym', { intensity: 'beast', medicalCaution: true }).days.filter((d) => !d.rest);
    expect(hard.some((d) => /hiit/i.test(d.cardio?.name ?? ''))).toBe(true);
    expect(gentle.some((d) => /hiit/i.test(d.cardio?.name ?? ''))).toBe(false);
  });
});

describe('every routine has a visible core/abs block', () => {
  const training = (opts = {}) => generateWeeklyWorkout('muscular', 'gym', opts).days.filter((d) => !d.rest);

  it('every training day has a labeled core block - 1 no-equipment abs move at 3 sets, by explicit user request', () => {
    for (const d of training({ fitnessLevel: 'intermediate' })) {
      expect((d.core?.length ?? 0)).toBe(1);
      expect(d.core!.every((e) => e.muscleGroup === 'core' && e.equipment === 'bodyweight')).toBe(true);
      expect(d.core![0]!.sets).toBe(3);
    }
  });

  it('appears across goals (full-body / fatloss too)', () => {
    for (const goal of ['athletic', 'fatloss'] as const) {
      const days = generateWeeklyWorkout(goal, 'home', { fitnessLevel: 'beginner' }).days.filter((d) => !d.rest);
      expect(days.every((d) => (d.core?.length ?? 0) === 1)).toBe(true);
    }
  });

  it('level no longer changes daily-abs volume - it is a fixed 1 move / 3 sets by explicit user request, not level-scaled', () => {
    const totalSets = (core: { sets: number }[]) => core.reduce((sum, e) => sum + e.sets, 0);
    const beg = totalSets(training({ fitnessLevel: 'beginner' })[0]!.core!);
    const adv = totalSets(training({ fitnessLevel: 'advanced' })[0]!.core!);
    expect(adv).toBe(beg);
    expect(adv).toBe(3);
  });

  it('medical caution => gentle, back-friendly core (no crunches/leg raises)', () => {
    const core = training({ fitnessLevel: 'advanced', medicalCaution: true })[0]!.core!;
    expect(core.some((e) => /dead bug|bird dog|glute bridge/i.test(e.name))).toBe(true);
    expect(core.some((e) => /crunch|leg raise|russian/i.test(e.name))).toBe(false);
  });
});

describe('selectable training splits', () => {
  const focuses = (split: any, loc: any = 'gym') =>
    generateWeeklyWorkout('muscular', loc, { split, fitnessLevel: 'intermediate' })
      .days.filter((d) => !d.rest).map((d) => d.focus.toLowerCase());

  it('body_part = the classic chest/back/shoulders/arms/legs split', () => {
    const f = focuses('body_part').join(' | ');
    expect(f).toMatch(/chest/); expect(f).toMatch(/back/); expect(f).toMatch(/shoulders/);
    expect(f).toMatch(/biceps|triceps/); expect(f).toMatch(/legs/);
  });
  it('push_pull_legs cycles push/pull/legs', () => {
    const f = focuses('push_pull_legs').join(' | ');
    expect(f).toMatch(/push/); expect(f).toMatch(/pull/); expect(f).toMatch(/legs/);
  });
  it('upper_lower alternates upper/lower', () => {
    const f = focuses('upper_lower').join(' | ');
    expect(f).toMatch(/upper/); expect(f).toMatch(/lower/);
  });
  it('full_body days each hit the whole body', () => {
    expect(focuses('full_body').every((x) => /full-body/.test(x))).toBe(true);
  });

  it('full_body (gym, non-beginner) stays within the realistic per-day exercise budget while still spreading across multiple muscle groups', () => {
    const day = generateWeeklyWorkout('muscular', 'gym', { split: 'full_body', fitnessLevel: 'intermediate' })
      .days.find((d) => !d.rest)!;
    // Capped to a doable session (see LEVEL_TOTAL_WORKING): intermediate = 6-7 MAIN lifts (user-set), with
    // 1 abs + 1 cardio on top.
    expect(day.exercises.length).toBeLessThanOrEqual(7);
    // Still diversified across groups (round-robin selection), not collapsed onto just one.
    const groups = new Set(day.exercises.map((e) => e.muscleGroup).filter(Boolean));
    expect(groups.size).toBeGreaterThanOrEqual(2);
  });
  it('splits work for home too, and every day still gets warm-up + core', () => {
    const days = generateWeeklyWorkout('muscular', 'home', { split: 'push_pull_legs' }).days.filter((d) => !d.rest);
    expect(days.length).toBeGreaterThan(0);
    for (const d of days) {
      expect((d.warmup?.length ?? 0)).toBeGreaterThan(0);
      expect((d.core?.length ?? 0)).toBe(1);
    }
  });
  it('no split falls back to the goal-derived program (unchanged)', () => {
    const withGoal = generateWeeklyWorkout('fatloss', 'gym', {}).days.filter((d) => !d.rest).map((d) => d.focus);
    expect(withGoal.length).toBeGreaterThan(0);
  });
});
