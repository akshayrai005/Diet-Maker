import { describe, it, expect } from 'vitest';
import { recommendWellness, suggestNow } from '../src/modules/wellness/wellness';

describe('recommendWellness', () => {
  it('always returns a yoga + meditation with a reason', () => {
    const r = recommendWellness({});
    expect(r.yoga).not.toBeNull();
    expect(r.meditation).not.toBeNull();
    expect(r.reason.length).toBeGreaterThan(0);
  });

  it('picks period-relief yoga during the period', () => {
    expect(recommendWellness({ phase: 'menstrual' }).yoga?.id).toBe('period-relief');
  });

  it('picks calming yoga + stress reset when mood is low', () => {
    const r = recommendWellness({ mood: 1 });
    expect(r.yoga?.id).toBe('wind-down');
    expect(r.meditation?.id).toBe('stress-reset');
  });

  it('picks a desk reset for a sedentary lifestyle', () => {
    expect(recommendWellness({ activityLevel: 'sedentary', mood: 3 }).yoga?.id).toBe('desk-reset');
  });

  it('leans energising when mood is high in the follicular phase', () => {
    const r = recommendWellness({ mood: 5, phase: 'follicular' });
    expect(r.yoga?.id).toBe('core-strength');
    expect(r.meditation?.id).toBe('gratitude');
  });
});

describe('suggestNow', () => {
  it('always returns a yoga + meditation with a reason', () => {
    const s = suggestNow({ hour: 9 });
    expect(s.yoga).not.toBeNull();
    expect(s.meditation).not.toBeNull();
    expect(s.reason.length).toBeGreaterThan(0);
  });

  it('winds down for sleep late at night', () => {
    const s = suggestNow({ hour: 23 });
    expect(s.yoga?.id).toBe('wind-down');
    expect(s.meditation?.id).toBe('478-sleep');
  });

  it('energises in the morning by default', () => {
    expect(suggestNow({ hour: 7 }).yoga?.id).toBe('morning-energizer');
  });

  it('goes gentle in the morning after poor sleep', () => {
    const s = suggestNow({ hour: 7, sleepHours: 4.5 });
    expect(s.yoga?.id).toBe('desk-reset');
  });

  it('offers a stress reset when mood is low', () => {
    const s = suggestNow({ hour: 13, mood: 1 });
    expect(s.meditation?.id).toBe('stress-reset');
  });

  it('is deterministic', () => {
    const a = suggestNow({ hour: 15, energy: 2 });
    const b = suggestNow({ hour: 15, energy: 2 });
    expect(a).toEqual(b);
  });
})
