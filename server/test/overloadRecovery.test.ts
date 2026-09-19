import { describe, it, expect } from 'vitest';
import { recommendNextSession, assessRecovery, type LoggedSet } from '../src/modules/exercise/overload';

const set = (date: string, w: number, reps: number, rir?: number): LoggedSet => ({ exerciseName: 'Bench press', date, weightKg: w, reps, sets: 3, rir });

describe('progression judges effort and recovery, not just reps', () => {
  it('normal session with reps in reserve adds one small step', () => {
    const r = recommendNextSession([set('2026-09-01', 60, 8, 2)])[0]!;
    expect(r.suggestedWeightKg).toBe(62.5);
  });
  it('a set taken to failure (RIR 0) holds the load', () => {
    const r = recommendNextSession([set('2026-09-01', 60, 8, 0)])[0]!;
    expect(r.suggestedWeightKg).toBe(60);
    expect(r.rationale).toMatch(/failure/);
  });
  it('missing reps vs the previous session holds the load', () => {
    const r = recommendNextSession([set('2026-09-01', 60, 8), set('2026-09-04', 60, 6)])[0]!;
    expect(r.suggestedWeightKg).toBe(60);
  });
  it('poor recovery holds load instead of adding it, and says why', () => {
    const rec = assessRecovery([{ sleepHours: 4.5, sleepQuality: 2, energy: 2 }]);
    expect(rec.low).toBe(true);
    const r = recommendNextSession([set('2026-09-01', 60, 8, 2)], { recovery: rec })[0]!;
    expect(r.suggestedWeightKg).toBe(60);
    expect(r.deload).toBe(false);
    expect(r.rationale).toMatch(/sleep/);
  });
  it('good or missing recovery data never blocks progress', () => {
    expect(assessRecovery([]).low).toBe(false);
    expect(assessRecovery([{ sleepHours: 7.5, sleepQuality: 4, energy: 4, pain: 1 }]).low).toBe(false);
  });
  it('significant pain is flagged with a see-a-professional note', () => {
    const rec = assessRecovery([{ pain: 5 }]);
    expect(rec.low).toBe(true);
    expect(rec.reason).toMatch(/physiotherapist or doctor/);
  });
  it('a scheduled deload still wins', () => {
    const r = recommendNextSession([set('2026-09-01', 60, 8), set('2026-09-29', 60, 8)])[0]!;
    expect(r.deload).toBe(true);
  });
});
