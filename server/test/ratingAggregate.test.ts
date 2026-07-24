import { describe, it, expect } from 'vitest';
import { aggregateRatingInputs } from '../src/modules/rating/aggregate';

const target = { dailyKcal: 2000, proteinG: 100, waterMl: 3000 };

describe('aggregateRatingInputs', () => {
  it('omits the diet pillar when there is no target or no logged days', () => {
    expect(aggregateRatingInputs({
      target: null, dietDays: [], daysInWindow: 7, workoutsCompleted: 0,
      workoutsScheduled: 0, overloadTrend: 'flat', loggingStreakDays: 0, todayAdherence: null,
    }).diet).toBeUndefined();
  });

  it('scores on-target days highly and off-target days lower', () => {
    const onTarget = aggregateRatingInputs({
      target, dietDays: [{ kcal: 2000, proteinG: 100, waterMl: 3000 }],
      daysInWindow: 7, workoutsCompleted: 0, workoutsScheduled: 0,
      overloadTrend: 'flat', loggingStreakDays: 1, todayAdherence: null,
    }).diet!;
    expect(onTarget.calorieAdherencePct).toBe(100);
    expect(onTarget.proteinHitPct).toBe(100);
    expect(onTarget.waterPct).toBe(100);

    const under = aggregateRatingInputs({
      target, dietDays: [{ kcal: 800, proteinG: 30, waterMl: 500 }],
      daysInWindow: 7, workoutsCompleted: 0, workoutsScheduled: 0,
      overloadTrend: 'flat', loggingStreakDays: 1, todayAdherence: null,
    }).diet!;
    expect(under.calorieAdherencePct).toBeLessThan(60);
    expect(under.proteinHitPct).toBe(0);
  });

  it('computes exercise consistency from completed vs scheduled', () => {
    const ex = aggregateRatingInputs({
      target, dietDays: [], daysInWindow: 7, workoutsCompleted: 3,
      workoutsScheduled: 5, overloadTrend: 'up', loggingStreakDays: 0, todayAdherence: null,
    }).exercise!;
    expect(ex.workoutsCompleted).toBe(3);
    expect(ex.workoutsScheduled).toBe(5);
    expect(ex.overloadTrend).toBe('up');
    expect(ex.consistencyPct).toBe(60);
  });

  it('maps discipline from adherence, streak and logging consistency', () => {
    const disc = aggregateRatingInputs({
      target, dietDays: [{ kcal: 2000, proteinG: 100, waterMl: 3000 }, { kcal: 1950, proteinG: 95, waterMl: 2800 }],
      daysInWindow: 7, workoutsCompleted: 0, workoutsScheduled: 0,
      overloadTrend: 'flat', loggingStreakDays: 4, todayAdherence: 82,
    }).discipline!;
    expect(disc.adherenceScore).toBe(82);
    expect(disc.habitStreakDays).toBe(4);
    expect(disc.loggingConsistencyPct).toBe(29); // 2 of 7 days ≈ 29%
  });

  it('drops adherenceScore when today has no data', () => {
    const disc = aggregateRatingInputs({
      target, dietDays: [], daysInWindow: 7, workoutsCompleted: 0,
      workoutsScheduled: 0, overloadTrend: 'flat', loggingStreakDays: 0, todayAdherence: null,
    }).discipline!;
    expect(disc.adherenceScore).toBeUndefined();
  });
});
