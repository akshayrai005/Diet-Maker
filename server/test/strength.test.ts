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

  it('every training day has a labeled core block with >=2 movements, all muscleGroup=core', () => {
    for (const d of training({ fitnessLevel: 'intermediate' })) {
      expect((d.core?.length ?? 0)).toBeGreaterThanOrEqual(2);
      expect(d.core!.every((e) => e.muscleGroup === 'core')).toBe(true);
      expect(d.core!.some((e) => /plank/i.test(e.name))).toBe(true); // a plank hold
    }
  });

  it('appears across goals (full-body / fatloss too)', () => {
    for (const goal of ['athletic', 'fatloss'] as const) {
      const days = generateWeeklyWorkout(goal, 'home', { fitnessLevel: 'beginner' }).days.filter((d) => !d.rest);
      expect(days.every((d) => (d.core?.length ?? 0) >= 2)).toBe(true);
    }
  });

  it('advanced gets more core volume than beginner', () => {
    const beg = training({ fitnessLevel: 'beginner' })[0]!.core!.length;
    const adv = training({ fitnessLevel: 'advanced' })[0]!.core!.length;
    expect(adv).toBeGreaterThan(beg);
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

  it('full_body (gym, non-beginner) has ~12 exercises covering all major groups', () => {
    const day = generateWeeklyWorkout('muscular', 'gym', { split: 'full_body', fitnessLevel: 'intermediate' })
      .days.find((d) => !d.rest)!;
    expect(day.exercises.length).toBeGreaterThanOrEqual(10);
    const groups = new Set(day.exercises.map((e) => e.muscleGroup));
    for (const g of ['chest', 'back', 'shoulders', 'legs']) expect(groups.has(g)).toBe(true);
  });
  it('splits work for home too, and every day still gets warm-up + core', () => {
    const days = generateWeeklyWorkout('muscular', 'home', { split: 'push_pull_legs' }).days.filter((d) => !d.rest);
    expect(days.length).toBeGreaterThan(0);
    for (const d of days) {
      expect((d.warmup?.length ?? 0)).toBeGreaterThan(0);
      expect((d.core?.length ?? 0)).toBeGreaterThanOrEqual(2);
    }
  });
  it('no split falls back to the goal-derived program (unchanged)', () => {
    const withGoal = generateWeeklyWorkout('fatloss', 'gym', {}).days.filter((d) => !d.rest).map((d) => d.focus);
    expect(withGoal.length).toBeGreaterThan(0);
  });
});
