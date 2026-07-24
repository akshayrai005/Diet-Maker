import { describe, it, expect } from 'vitest';
import { DEFAULT_HABITS, habitStreak, recoveryNudge, eveningReview } from '../src/modules/discipline/habits';

describe('DEFAULT_HABITS', () => {
  it('has unique keys and stable sort order', () => {
    const keys = DEFAULT_HABITS.map((h) => h.key);
    expect(new Set(keys).size).toBe(keys.length);
    expect(DEFAULT_HABITS).toContainEqual(expect.objectContaining({ key: 'steps-10k' }));
  });
});

describe('habitStreak', () => {
  it('counts consecutive days ending today', () => {
    expect(habitStreak(['2026-07-22', '2026-07-23', '2026-07-24'], '2026-07-24')).toBe(3);
  });
  it('is 0 when nothing done', () => {
    expect(habitStreak([], '2026-07-24')).toBe(0);
  });
  it('survives when today is not yet done but yesterday was', () => {
    expect(habitStreak(['2026-07-22', '2026-07-23'], '2026-07-24')).toBe(2);
  });
});

describe('recoveryNudge', () => {
  it('returns null when there was no streak to break', () => {
    expect(recoveryNudge('Morning walk', 0)).toBeNull();
  });
  it('is encouraging and non-punitive for a short slip', () => {
    const n = recoveryNudge('Morning walk', 3)!;
    expect(n.toLowerCase()).toContain('no problem');
  });
  it('acknowledges a long broken streak', () => {
    expect(recoveryNudge('Morning walk', 10)).toContain('10-day');
  });
});

describe('eveningReview', () => {
  it('celebrates a clean sweep', () => {
    expect(eveningReview(5, 5)).toContain('clean sweep');
  });
  it('encourages when none done', () => {
    expect(eveningReview(0, 5)).toContain('momentum');
  });
  it('handles no habits', () => {
    expect(eveningReview(0, 0)).toContain('Add a habit');
  });
});
