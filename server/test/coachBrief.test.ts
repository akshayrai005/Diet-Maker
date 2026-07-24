import { describe, it, expect } from 'vitest';
import { buildCoachBrief, type CoachBriefInput } from '../src/modules/coach/coach.brief';

const base: CoachBriefInput = {
  firstName: 'Aman',
  hour: 9,
  overall: 72,
  grade: 'B',
  biggestLever: 'Two more workouts a week would lift you toward an A.',
  adherenceTopActions: ['Log dinner to close today’s food gap.', 'Drink 500 ml more water.'],
  streakDays: 5,
  todayWorkoutScheduled: true,
  rest: false,
  todayFocus: 'Push (Chest/Shoulders/Triceps)',
  todayWorkoutDone: false,
  todayWellnessDone: false,
  weightDeltaKg: -1.2,
  projection: [{ label: '1 month', weeks: 4, weightKg: 78 }],
  calorieRemaining: 600,
};

describe('buildCoachBrief', () => {
  it('is deterministic', () => {
    expect(buildCoachBrief(base)).toEqual(buildCoachBrief(base));
  });

  it('greets by local hour and name', () => {
    expect(buildCoachBrief({ ...base, hour: 9 }).greeting).toBe('Good morning, Aman.');
    expect(buildCoachBrief({ ...base, hour: 14 }).greeting).toBe('Good afternoon, Aman.');
    expect(buildCoachBrief({ ...base, hour: 20 }).greeting).toBe('Good evening, Aman.');
    expect(buildCoachBrief({ ...base, firstName: null }).greeting).toBe('Good morning.');
  });

  it('summarises the rating with the biggest lever and never invents numbers', () => {
    const b = buildCoachBrief(base);
    expect(b.ratingSummary).toContain('72/100');
    expect(b.ratingSummary).toContain('grade B');
    expect(b.ratingSummary).toContain('Two more workouts');
  });

  it('surfaces the scheduled focus for a training day', () => {
    expect(buildCoachBrief(base).moveOrRest).toContain('Push (Chest/Shoulders/Triceps)');
  });

  it('switches to rest-day copy', () => {
    expect(buildCoachBrief({ ...base, rest: true }).moveOrRest.toLowerCase()).toContain('rest');
  });

  it('acknowledges a completed workout / mind session', () => {
    expect(buildCoachBrief({ ...base, todayWorkoutDone: true }).moveOrRest).toContain('✓');
    expect(buildCoachBrief({ ...base, todayWellnessDone: true }).mindNudge).toContain('✓');
  });

  it('caps discipline actions at two and falls back when empty', () => {
    expect(buildCoachBrief(base).disciplineActions).toHaveLength(2);
    expect(buildCoachBrief({ ...base, adherenceTopActions: [] }).disciplineActions).toHaveLength(1);
  });

  it('frames the prediction encouragingly with trend momentum', () => {
    const b = buildCoachBrief(base);
    expect(b.prediction).toContain('78 kg');
    expect(b.prediction).toContain('1.2 kg down');
  });

  it('always includes the educational disclaimer', () => {
    expect(buildCoachBrief(base).disclaimer).toContain('not medical advice');
  });

  it('handles no calorie target gracefully', () => {
    expect(buildCoachBrief({ ...base, calorieRemaining: null }).dietFocus).toContain('Log your meals');
  });
});
