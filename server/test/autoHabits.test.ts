import { describe, it, expect } from 'vitest';
import { autoHabitStatus } from '../src/modules/discipline/habits';

describe('autoHabitStatus — habits auto-complete from tracked metrics', () => {
  it('ticks 10k steps when the device reports enough steps', () => {
    expect(autoHabitStatus('steps-10k', { steps: 10_500 })).toMatchObject({ tracked: true, done: true, target: 10_000, current: 10_500 });
    expect(autoHabitStatus('steps-10k', { steps: 1903 })).toMatchObject({ tracked: true, done: false });
  });

  it('ticks the water goal when logged water meets the target', () => {
    expect(autoHabitStatus('water-goal', { waterMl: 3000, waterTargetMl: 2950 })).toMatchObject({ tracked: true, done: true });
    expect(autoHabitStatus('water-goal', { waterMl: 250, waterTargetMl: 2950 })).toMatchObject({ tracked: true, done: false, current: 250, target: 2950 });
  });

  it('ticks 7h sleep when the device reports enough sleep', () => {
    expect(autoHabitStatus('sleep-7h', { sleepHours: 7.5 })).toMatchObject({ tracked: true, done: true });
    expect(autoHabitStatus('sleep-7h', { sleepHours: 5 })).toMatchObject({ tracked: true, done: false });
  });

  it('stays MANUAL (untracked) when the metric is missing', () => {
    expect(autoHabitStatus('steps-10k', {})).toEqual({ tracked: false, done: false });
    expect(autoHabitStatus('water-goal', { waterMl: 1000 })).toEqual({ tracked: false, done: false }); // no target
    expect(autoHabitStatus('sleep-7h', {})).toEqual({ tracked: false, done: false });
  });

  it('leaves non-metric habits (no-sugar, morning-walk) manual', () => {
    expect(autoHabitStatus('no-sugar-8pm', { steps: 99_999, waterMl: 9999, waterTargetMl: 100, sleepHours: 12 }).tracked).toBe(false);
    expect(autoHabitStatus('morning-walk', { steps: 99_999 }).tracked).toBe(false);
  });
});
