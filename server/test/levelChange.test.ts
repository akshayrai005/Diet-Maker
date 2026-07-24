import { describe, it, expect } from 'vitest';
import { suggestLevelChange } from '../src/modules/exercise/overload';
import type { LoggedSet } from '../src/modules/exercise/overload';

function set(over: Partial<LoggedSet> & { date: string }): LoggedSet {
  return { exerciseName: 'Squat', weightKg: 40, reps: 8, sets: 3, ...over };
}

describe('suggestLevelChange — promote (up)', () => {
  it('promotes when progressing consistently over enough sessions', () => {
    const history: LoggedSet[] = [
      set({ date: '2026-01-01', weightKg: 40 }),
      set({ date: '2026-01-04', weightKg: 45 }),
      set({ date: '2026-01-08', weightKg: 50 }),
      set({ date: '2026-01-11', weightKg: 55 }),
    ];
    const out = suggestLevelChange(history, 'intermediate');
    expect(out.direction).toBe('up');
    expect(out.reason).toMatch(/progress/i);
  });

  it('does NOT promote with too few sessions even if progressing', () => {
    const history: LoggedSet[] = [
      set({ date: '2026-01-01', weightKg: 40 }),
      set({ date: '2026-01-04', weightKg: 45 }),
    ];
    // 2 sessions < default 4 needed → not up.
    expect(suggestLevelChange(history, 'intermediate').direction).not.toBe('up');
  });
});

describe('suggestLevelChange — demote (down)', () => {
  it('demotes on a downward trend', () => {
    const history: LoggedSet[] = [
      set({ date: '2026-01-01', weightKg: 60 }),
      set({ date: '2026-01-08', weightKg: 50 }),
    ];
    const out = suggestLevelChange(history, 'intermediate');
    expect(out.direction).toBe('down');
    expect(out.reason).toMatch(/down/i);
  });

  it('demotes when there are too few sessions to sustain the level', () => {
    const history: LoggedSet[] = [set({ date: '2026-01-01', weightKg: 50 })];
    expect(suggestLevelChange(history, 'intermediate').direction).toBe('down');
  });

  it('demotes on repeated misses across exercises', () => {
    const history: LoggedSet[] = [
      set({ exerciseName: 'Squat', date: '2026-01-01', weightKg: 40, reps: 8 }),
      set({ exerciseName: 'Squat', date: '2026-01-04', weightKg: 40, reps: 6 }), // miss
      set({ exerciseName: 'Bench', date: '2026-01-01', weightKg: 50, reps: 8 }),
      set({ exerciseName: 'Bench', date: '2026-01-04', weightKg: 50, reps: 6 }), // miss
    ];
    const out = suggestLevelChange(history, 'intermediate');
    expect(out.direction).toBe('down');
    expect(out.reason).toMatch(/miss/i);
  });

  it('treats an empty history as too little to sustain the level', () => {
    expect(suggestLevelChange([], 'intermediate').direction).toBe('down');
  });
});

describe('suggestLevelChange — hold', () => {
  it('holds when training is steady (flat, enough but not promotable)', () => {
    const history: LoggedSet[] = [
      set({ date: '2026-01-01', weightKg: 50, reps: 8, sets: 3 }),
      set({ date: '2026-01-04', weightKg: 50, reps: 8, sets: 3 }),
      set({ date: '2026-01-08', weightKg: 50, reps: 8, sets: 3 }),
    ];
    expect(suggestLevelChange(history, 'intermediate').direction).toBe('hold');
  });
});

describe('suggestLevelChange — boundaries', () => {
  it('an already-advanced lifter cannot go up (holds)', () => {
    const history: LoggedSet[] = [
      set({ date: '2026-01-01', weightKg: 40 }),
      set({ date: '2026-01-04', weightKg: 45 }),
      set({ date: '2026-01-08', weightKg: 50 }),
      set({ date: '2026-01-11', weightKg: 55 }),
    ];
    const out = suggestLevelChange(history, 'advanced');
    expect(out.direction).toBe('hold');
    expect(out.reason).toMatch(/advanced/i);
  });

  it('an already-beginner lifter cannot go down (holds)', () => {
    const history: LoggedSet[] = [
      set({ date: '2026-01-01', weightKg: 60 }),
      set({ date: '2026-01-08', weightKg: 50 }),
    ];
    const out = suggestLevelChange(history, 'beginner');
    expect(out.direction).toBe('hold');
    expect(out.reason).toMatch(/beginner/i);
  });

  it('is deterministic — same inputs, same suggestion', () => {
    const history: LoggedSet[] = [
      set({ date: '2026-01-01', weightKg: 40 }),
      set({ date: '2026-01-08', weightKg: 45 }),
    ];
    const a = suggestLevelChange(history, 'intermediate');
    const b = suggestLevelChange(history, 'intermediate');
    expect(a).toEqual(b);
  });
});
