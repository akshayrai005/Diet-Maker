import { describe, it, expect } from 'vitest';
import { summariseExercise, muscleGroupOf, type ExerciseLogRow } from '../src/modules/reports/exerciseSummary';
import { ruleInsights } from '../src/modules/reports/reportInsights';
import { buildWeeklyReport } from '../src/modules/reports/report';

const row = (name: string, day: string, over: Partial<ExerciseLogRow> = {}): ExerciseLogRow => ({
  exerciseName: name, sets: 1, reps: 10, weightKg: null, durationMin: null, kcal: 20,
  performedAt: new Date(`${day}T10:00:00Z`), sessionId: null, ...over,
});
const dayKey = (d: Date) => d.toISOString().slice(0, 10);

describe('exercise summary for reports', () => {
  it('groups exercises by muscle', () => {
    expect(muscleGroupOf('Incline Barbell Bench Press')).toBe('Chest');
    expect(muscleGroupOf('Arnold Press')).toBe('Shoulders');
    expect(muscleGroupOf('Cable Standing Rear Delt Row With Rope')).toBe('Shoulders');
    expect(muscleGroupOf('Barbell Curl')).toBe('Arms');
    expect(muscleGroupOf('Elliptical')).toBe('Cardio');
    expect(muscleGroupOf('Back Squat')).toBe('Legs');
  });

  it('totals sets, days, calories and best weight', () => {
    const s = summariseExercise(
      [
        row('Arnold Press', '2026-09-20', { sets: 3, weightKg: 10, sessionId: 'a' }),
        row('Arnold Press', '2026-09-22', { sets: 2, weightKg: 12.5, sessionId: 'b' }),
        row('Elliptical', '2026-09-22', { sets: null, durationMin: 10, kcal: 99, sessionId: 'b' }),
      ],
      dayKey,
    );
    expect(s.totalSets).toBe(6);
    expect(s.activeDays).toBe(2);
    expect(s.sessions).toBe(2);
    expect(s.cardioMin).toBe(10);
    expect(s.top[0]).toMatchObject({ name: 'Arnold Press', sets: 5, bestKg: 12.5 });
    expect(s.byMuscle).toEqual([{ group: 'Shoulders', sets: 5 }]); // cardio is not a muscle group
  });

  it('read-out names the muscles that were skipped and never invents data', () => {
    const exercise = summariseExercise([row('Arnold Press', '2026-09-20', { sets: 3 })], dayKey);
    const r = buildWeeklyReport({
      name: 'A', generatedAt: '2026-09-22T00:00:00Z', targets: { dailyKcal: 2000, proteinG: 120, waterMl: 2500 },
      bmi: 24, latestWeightKg: 70, weightDeltaKg: null, days: [{ date: '2026-09-20', kcal: 1500, proteinG: 60 }], exercise,
    });
    const i = ruleInsights(r);
    expect(i.source).toBe('rules');
    expect(i.eat.join(' ')).toContain('under');
    expect(i.exercise.join(' ')).toContain('chest');
    expect(i.nextWeek.length).toBeGreaterThan(0);
  });

  it('an empty period says there is nothing to judge', () => {
    const r = buildWeeklyReport({ name: 'A', generatedAt: '2026-09-22T00:00:00Z', targets: null, bmi: null, latestWeightKg: null, weightDeltaKg: null, days: [] });
    expect(ruleInsights(r).headline).toMatch(/not enough/i);
  });
});
