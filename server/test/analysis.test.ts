import { describe, it, expect } from 'vitest';
import { buildAnalysisNarrative, type AnalysisInput } from '../src/modules/reports/analysis';

const base: AnalysisInput = {
  firstName: 'Aman',
  overall: 72,
  grade: 'B',
  pillars: [
    { key: 'diet', label: 'Diet', score: 85, weight: 0.6 },
    { key: 'exercise', label: 'Exercise', score: 40, weight: 0.25 },
    { key: 'discipline', label: 'Discipline', score: 70, weight: 0.15 },
  ],
  biggestLever: 'Two more workouts a week would lift you toward an A.',
  streakDays: 5,
  weightDeltaKg: -1.2,
  prediction: { projectedWeightKg: 78, projectedMonthlyDeltaKg: -1.5, note: 'Based on your logged intake staying below maintenance.', blocked: false },
  hasRiskFindings: false,
};

describe('buildAnalysisNarrative', () => {
  it('is deterministic', () => {
    expect(buildAnalysisNarrative(base)).toEqual(buildAnalysisNarrative(base));
  });

  it('opens on the streak when there is one', () => {
    expect(buildAnalysisNarrative(base).opener).toContain('5-day logging streak');
  });

  it('targets the weakest pillar for the correlated insight', () => {
    expect(buildAnalysisNarrative(base).correlatedInsight).toContain('Exercise');
  });

  it('never invents numbers — rating line echoes the given score/grade', () => {
    const n = buildAnalysisNarrative(base);
    expect(n.ratingLine).toContain('72/100');
    expect(n.ratingLine).toContain('grade B');
  });

  it('frames next month from the prediction', () => {
    expect(buildAnalysisNarrative(base).nextMonth).toContain('78 kg');
  });

  it('respects a medically-blocked prediction', () => {
    const n = buildAnalysisNarrative({ ...base, prediction: { ...base.prediction!, blocked: true } });
    expect(n.nextMonth.toLowerCase()).toContain('steady');
  });

  it('defers clinical concerns when risks are present', () => {
    expect(buildAnalysisNarrative({ ...base, hasRiskFindings: true }).clinicalNote).toContain('professional');
  });

  it('always ends with the disclaimer and produces ordered paragraphs', () => {
    const n = buildAnalysisNarrative(base);
    expect(n.disclaimer).toContain('not medical advice');
    expect(n.paragraphs[0]).toBe(n.opener);
    expect(n.paragraphs[n.paragraphs.length - 1]).toBe(n.disclaimer);
  });

  it('handles no prediction gracefully', () => {
    expect(buildAnalysisNarrative({ ...base, prediction: null }).nextMonth).toContain('trend');
  });
});
