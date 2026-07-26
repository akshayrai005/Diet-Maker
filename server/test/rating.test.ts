import { describe, it, expect } from 'vitest';
import { computeRating, type RatingInput } from '../src/modules/rating/rating';

// A fully-populated, middling baseline we can perturb one pillar at a time.
const baseline: RatingInput = {
  diet: { calorieAdherencePct: 50, proteinHitPct: 50, micronutrientCoveragePct: 50, waterPct: 50 },
  exercise: { workoutsCompleted: 1, workoutsScheduled: 2, overloadTrend: 'flat', consistencyPct: 50 },
  discipline: { adherenceScore: 50, habitStreakDays: 15, loggingConsistencyPct: 50 },
};

describe('computeRating — monotonicity per pillar', () => {
  it('overall rises when DIET inputs improve (others held constant)', () => {
    const low = computeRating({
      ...baseline,
      diet: { calorieAdherencePct: 30, proteinHitPct: 30, micronutrientCoveragePct: 30, waterPct: 30 },
    });
    const high = computeRating({
      ...baseline,
      diet: { calorieAdherencePct: 90, proteinHitPct: 90, micronutrientCoveragePct: 90, waterPct: 90 },
    });
    expect(high.overall).toBeGreaterThan(low.overall);
  });

  it('overall rises when EXERCISE inputs improve (others held constant)', () => {
    const low = computeRating({
      ...baseline,
      exercise: { workoutsCompleted: 1, workoutsScheduled: 4, overloadTrend: 'down', consistencyPct: 20 },
    });
    const high = computeRating({
      ...baseline,
      exercise: { workoutsCompleted: 4, workoutsScheduled: 4, overloadTrend: 'up', consistencyPct: 95 },
    });
    expect(high.overall).toBeGreaterThan(low.overall);
  });

  it('overall rises when DISCIPLINE inputs improve (others held constant)', () => {
    const low = computeRating({
      ...baseline,
      discipline: { adherenceScore: 20, habitStreakDays: 2, loggingConsistencyPct: 20 },
    });
    const high = computeRating({
      ...baseline,
      discipline: { adherenceScore: 95, habitStreakDays: 40, loggingConsistencyPct: 95 },
    });
    expect(high.overall).toBeGreaterThan(low.overall);
  });
});

describe('computeRating — grade thresholds', () => {
  // With only the diet pillar populated (all four sub-inputs equal to X), overall === X,
  // so we can pin grade boundaries exactly.
  const dietOnly = (x: number) =>
    computeRating({
      diet: { calorieAdherencePct: x, proteinHitPct: x, micronutrientCoveragePct: x, waterPct: x },
    });

  it('maps scores to A/B/C/D/F at the right boundaries', () => {
    expect(dietOnly(90).grade).toBe('A');
    expect(dietOnly(85).grade).toBe('A'); // A >= 85
    expect(dietOnly(70).grade).toBe('B'); // B >= 70
    expect(dietOnly(55).grade).toBe('C'); // C >= 55
    expect(dietOnly(40).grade).toBe('D'); // D >= 40
    expect(dietOnly(39).grade).toBe('F');
    expect(dietOnly(0).grade).toBe('F');
  });

  it('overall equals the sole populated pillar score', () => {
    expect(dietOnly(72).overall).toBe(72);
  });
});

describe('computeRating — missing pillar renormalises weights', () => {
  it('drops a data-less pillar instead of scoring it as zero', () => {
    const r = computeRating({
      diet: { calorieAdherencePct: 80, proteinHitPct: 80, micronutrientCoveragePct: 80, waterPct: 80 },
      exercise: { workoutsCompleted: 2, workoutsScheduled: 5, overloadTrend: 'flat', consistencyPct: 40 },
      // discipline entirely absent
    });

    // Naive fixed weighting (treating missing discipline as 0) would give 0.6*80 + 0.25*exScore.
    // Renormalised over {diet, exercise} it must be strictly higher than that naive number.
    const dietScore = 80;
    const exScore = r.pillars.find((p) => p.key === 'exercise')!.score;
    const naive = 0.6 * dietScore + 0.25 * exScore; // missing pillar = 0
    const renormalised = (0.6 * dietScore + 0.25 * exScore) / (0.6 + 0.25);

    expect(r.overall).toBeGreaterThan(naive);
    expect(r.overall).toBeCloseTo(renormalised, 1);

    const discipline = r.pillars.find((p) => p.key === 'discipline')!;
    expect(discipline.score).toBe(0);
    expect(discipline.inputs.adherenceScore).toBeNull();
  });

  it('hasData is false for a brand-new user with no pillars (client shows encouragement, not F)', () => {
    const r = computeRating({});
    expect(r.hasData).toBe(false);
    expect(r.biggestLever.message).toMatch(/start by logging/i);
  });

  it('hasData is true once any pillar has data', () => {
    expect(computeRating({ diet: { calorieAdherencePct: 50, proteinHitPct: 50, micronutrientCoveragePct: 50, waterPct: 50 } }).hasData).toBe(true);
  });
});

describe('computeRating — biggest lever is the weakest weighted pillar', () => {
  it('with equal pillar scores, Diet (60% weight) is the lever', () => {
    const r = computeRating({
      diet: { calorieAdherencePct: 60, proteinHitPct: 60, micronutrientCoveragePct: 60, waterPct: 60 },
      exercise: { workoutsCompleted: 3, workoutsScheduled: 5, overloadTrend: 'flat', consistencyPct: 60 },
      discipline: { adherenceScore: 60, habitStreakDays: 18, loggingConsistencyPct: 60 },
    });
    expect(r.biggestLever.pillar).toBe('diet');
  });

  it('when Diet is maxed, the next weakest weighted pillar (Exercise) becomes the lever', () => {
    const r = computeRating({
      diet: { calorieAdherencePct: 100, proteinHitPct: 100, micronutrientCoveragePct: 100, waterPct: 100 },
      exercise: { workoutsCompleted: 1, workoutsScheduled: 2, overloadTrend: 'flat', consistencyPct: 50 }, // ~50
      discipline: { adherenceScore: 50, habitStreakDays: 15, loggingConsistencyPct: 50 }, // 50
    });
    // Equal gaps → 0.25 weight (exercise) beats 0.15 weight (discipline).
    expect(r.biggestLever.pillar).toBe('exercise');
    expect(r.biggestLever.message).toMatch(/exercise/i);
  });

  it('when Diet and Exercise are maxed, Discipline is the lever', () => {
    const r = computeRating({
      diet: { calorieAdherencePct: 100, proteinHitPct: 100, micronutrientCoveragePct: 100, waterPct: 100 },
      exercise: { workoutsCompleted: 4, workoutsScheduled: 4, overloadTrend: 'up', consistencyPct: 100 },
      discipline: { adherenceScore: 40, habitStreakDays: 6, loggingConsistencyPct: 40 },
    });
    expect(r.biggestLever.pillar).toBe('discipline');
  });
});

describe('computeRating — all-zero vs all-strong', () => {
  it('all-zero inputs bottom out at F, all-strong tops out at A', () => {
    const zero = computeRating({
      diet: { calorieAdherencePct: 0, proteinHitPct: 0, micronutrientCoveragePct: 0, waterPct: 0 },
      exercise: { workoutsCompleted: 0, workoutsScheduled: 4, overloadTrend: 'down', consistencyPct: 0 },
      discipline: { adherenceScore: 0, habitStreakDays: 0, loggingConsistencyPct: 0 },
    });
    const strong = computeRating({
      diet: { calorieAdherencePct: 100, proteinHitPct: 100, micronutrientCoveragePct: 100, waterPct: 100 },
      exercise: { workoutsCompleted: 5, workoutsScheduled: 5, overloadTrend: 'up', consistencyPct: 100 },
      discipline: { adherenceScore: 100, habitStreakDays: 60, loggingConsistencyPct: 100 },
    });

    expect(zero.overall).toBe(0);
    expect(zero.grade).toBe('F');
    expect(strong.overall).toBe(100);
    expect(strong.grade).toBe('A');
    expect(strong.overall).toBeGreaterThan(zero.overall);
  });

  it('returns a sensible result with no input at all', () => {
    const r = computeRating({});
    expect(r.overall).toBe(0);
    expect(r.grade).toBe('F');
    expect(r.pillars).toHaveLength(3);
    expect(r.biggestLever.pillar).toBe('diet');
  });
});
