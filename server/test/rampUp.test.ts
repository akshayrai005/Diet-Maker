import { describe, it, expect } from 'vitest';
import { isNewToTraining, rampFor } from '../src/modules/exercise/rampUp';

describe('isNewToTraining', () => {
  it('is true when membership is unknown/never reported', () => {
    expect(isNewToTraining(null)).toBe(true);
    expect(isNewToTraining(undefined)).toBe(true);
  });

  it('is true for under a month, false for a month or more', () => {
    expect(isNewToTraining(1)).toBe(true);
    expect(isNewToTraining(3)).toBe(false);
    expect(isNewToTraining(12)).toBe(false);
  });
});

describe('rampFor', () => {
  it('ramps a brand-new account through 4 weeks of increasing intensity', () => {
    expect(rampFor(null, 0)).toEqual({ week: 1, factor: 0.6 });
    expect(rampFor(null, 6)).toEqual({ week: 1, factor: 0.6 });
    expect(rampFor(null, 7)).toEqual({ week: 2, factor: 0.75 });
    expect(rampFor(null, 13)).toEqual({ week: 2, factor: 0.75 });
    expect(rampFor(null, 14)).toEqual({ week: 3, factor: 0.9 });
    expect(rampFor(null, 20)).toEqual({ week: 3, factor: 0.9 });
  });

  it('returns null (full intensity) once week 4 is reached', () => {
    expect(rampFor(null, 21)).toBeNull();
    expect(rampFor(null, 27)).toBeNull();
  });

  it('returns null once the account is 4+ weeks old, even if still new to training', () => {
    expect(rampFor(1, 30)).toBeNull();
    expect(rampFor(null, 100)).toBeNull();
  });

  it('never ramps someone who already reports 3+ months of gym membership', () => {
    expect(rampFor(3, 0)).toBeNull();
    expect(rampFor(12, 5)).toBeNull();
  });
});
