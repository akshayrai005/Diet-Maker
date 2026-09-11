import { describe, it, expect } from 'vitest';
import { defaultTrainingSplit, suggestSplitUpgrade } from '../src/modules/exercise/splitSuggestion';

describe('defaultTrainingSplit', () => {
  it('mixed full-body sessions for a brand-new gym-goer (unknown/≤1 month membership)', () => {
    expect(defaultTrainingSplit('muscular', null)).toBe('full_body');
    expect(defaultTrainingSplit('athletic', 1)).toBe('full_body');
  });

  it('graduates to a body-part split once past the first month', () => {
    expect(defaultTrainingSplit('muscular', 2)).toBe('body_part');
    expect(defaultTrainingSplit('athletic', 6)).toBe('body_part');
  });

  it('fat-loss goal keeps its own dedicated program regardless of tenure', () => {
    expect(defaultTrainingSplit('fatloss', null)).toBeUndefined();
    expect(defaultTrainingSplit('fatloss', 12)).toBeUndefined();
  });
});

describe('suggestSplitUpgrade', () => {
  it('never suggests for a brand-new user still in their first month', () => {
    expect(suggestSplitUpgrade(undefined, 1).suggestBodyPartSplit).toBe(false);
    expect(suggestSplitUpgrade('full_body', 0).suggestBodyPartSplit).toBe(false);
  });

  it('suggests once 2+ months in on a mixed/full-body session', () => {
    const r = suggestSplitUpgrade(undefined, 3);
    expect(r.suggestBodyPartSplit).toBe(true);
    expect(r.reason).toContain('3 months');
  });

  it('never fires for someone who already explicitly chose a non-full-body split', () => {
    expect(suggestSplitUpgrade('push_pull_legs', 12).suggestBodyPartSplit).toBe(false);
    expect(suggestSplitUpgrade('body_part', 12).suggestBodyPartSplit).toBe(false);
    expect(suggestSplitUpgrade('upper_lower', 12).suggestBodyPartSplit).toBe(false);
  });

  it('still suggests for an explicit full_body choice once tenure passes the threshold (never overrides, only nudges)', () => {
    expect(suggestSplitUpgrade('full_body', 5).suggestBodyPartSplit).toBe(true);
  });
});
