import { describe, it, expect } from 'vitest';
import { computeAdherence, type AdherenceInputs } from '../src/modules/nutrition/adherence';

function base(overrides: Partial<AdherenceInputs> = {}): AdherenceInputs {
  return {
    avgKcal: 2000,
    targetKcal: 2000,
    avgProteinG: 130,
    targetProteinG: 130,
    trainingDaysLogged: 4,
    trainingDaysPlanned: 4,
    avgSteps: 8000,
    targetSteps: 8000,
    weighInsLogged: 3,
    windowDays: 7,
    weightOnTrack: true,
    ...overrides,
  };
}

describe('computeAdherence', () => {
  it('reports no calorie data and asks the user to log more, even if a 0-count dimension (e.g. 0 weigh-ins) is real signal, not missing data', () => {
    const r = computeAdherence(base({ avgKcal: null, avgProteinG: null, trainingDaysLogged: 0, avgSteps: null, weighInsLogged: 0 }));
    expect(r.calories.level).toBe('none');
    expect(r.message).toMatch(/log your food/i);
  });

  it('overall is "none" when truly nothing at all was logged (including weigh-ins)', () => {
    const r = computeAdherence(base({
      avgKcal: null, avgProteinG: null, trainingDaysLogged: 0, trainingDaysPlanned: 0,
      avgSteps: null, weighInsLogged: 0, windowDays: 0, weightOnTrack: null,
    }));
    expect(r.overall).toBe('none');
  });

  it('a perfect week scores excellent across the board', () => {
    const r = computeAdherence(base());
    expect(r.calories.level).toBe('excellent');
    expect(r.protein.level).toBe('excellent');
    expect(r.overall).toBe('excellent');
    expect(r.holdSteady).toBe(false);
  });

  it('holds steady when calories + weight trend are on track despite low protein/steps - does not tell the user to cut further', () => {
    const r = computeAdherence(base({ avgProteinG: 70, avgSteps: 3000, weightOnTrack: true }));
    expect(r.holdSteady).toBe(true);
    expect(r.message.toLowerCase()).toContain('hold steady');
    // The advice must be "don't cut", never an unqualified instruction to cut.
    expect(r.message.toLowerCase()).toContain("don't cut");
  });

  it('does NOT hold steady when weight trend is not confirmed on track, even if calories look fine', () => {
    const r = computeAdherence(base({ avgProteinG: 70, weightOnTrack: null }));
    expect(r.holdSteady).toBe(false);
  });

  it('flags under-logged calories as the priority over any other dimension', () => {
    const r = computeAdherence(base({ avgKcal: 1000, avgProteinG: 40 })); // 50% of 2000 target
    expect(r.calories.level).toBe('low');
    expect(r.message.toLowerCase()).toContain('under-logging');
  });

  it('flags low protein specifically when calories are fine but protein is genuinely low', () => {
    const r = computeAdherence(base({ avgProteinG: 50, weightOnTrack: false }));
    expect(r.protein.level).toBe('low');
    expect(r.message.toLowerCase()).toContain('protein is low');
  });

  it('flags missed training days when nutrition is on track', () => {
    const r = computeAdherence(base({ trainingDaysLogged: 1, trainingDaysPlanned: 5 }));
    expect(r.training.level).toBe('low');
    expect(r.message.toLowerCase()).toContain('training');
  });

  it('training score is null (not zero) when the plan calls for zero training days', () => {
    const r = computeAdherence(base({ trainingDaysPlanned: 0, trainingDaysLogged: 0 }));
    expect(r.training.level).toBe('none');
  });

  it('overeating (over 125% of target) is scored low, not excellent, even though it is "logged"', () => {
    const r = computeAdherence(base({ avgKcal: 3000 })); // 150% of 2000 target
    expect(r.calories.level).toBe('low');
  });
});
