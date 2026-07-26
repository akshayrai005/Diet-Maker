import { describe, it, expect } from 'vitest';
import { moodInsight } from '../src/modules/wellness/moodInsight';

const m = (mood: number) => ({ mood });

describe('moodInsight', () => {
  it('empty → insufficient, no nudge, helpful prompt', () => {
    const r = moodInsight([]);
    expect(r.direction).toBe('insufficient');
    expect(r.professionalNudge).toBe(false);
    expect(r.avgMood).toBeNull();
    expect(r.message).toMatch(/check-in/i);
  });

  it('averages mood/stress/sleep', () => {
    const r = moodInsight([
      { mood: 4, stress: 2, sleepQuality: 4 },
      { mood: 2, stress: 4, sleepQuality: 2 },
    ]);
    expect(r.avgMood).toBe(3);
    expect(r.avgStress).toBe(3);
    expect(r.avgSleepQuality).toBe(3);
  });

  it('detects an improving trend', () => {
    const r = moodInsight([m(2), m(2), m(3), m(4), m(4), m(5)]);
    expect(r.direction).toBe('improving');
    expect(r.professionalNudge).toBe(false);
  });

  it('detects a declining trend', () => {
    const r = moodInsight([m(5), m(4), m(4), m(3), m(2), m(3)]);
    expect(r.direction).toBe('declining');
  });

  it('raises a gentle professional nudge only on sustained low mood (last 3 ≤ 2)', () => {
    expect(moodInsight([m(4), m(2), m(2), m(1)]).professionalNudge).toBe(true);
    // a single bad day does NOT trigger it
    expect(moodInsight([m(4), m(4), m(1)]).professionalNudge).toBe(false);
    // only two low readings → not yet
    expect(moodInsight([m(2), m(2)]).professionalNudge).toBe(false);
  });

  it('the nudge message is supportive, never diagnostic', () => {
    const r = moodInsight([m(1), m(2), m(1)]);
    expect(r.professionalNudge).toBe(true);
    expect(r.message).not.toMatch(/depress|diagnos|disorder/i);
    expect(r.disclaimer).toMatch(/not a mental-health diagnosis/i);
  });
});
