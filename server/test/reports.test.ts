import { predictNextMonth } from '../src/modules/reports/report';
import { describe, it, expect } from 'vitest';

describe('predictNextMonth (energy-balance weight prediction)', () => {
  it('predicts loss when logged intake is below maintenance', () => {
    const p = predictNextMonth({ avgDailyIntake: 1800, maintenanceKcal: 2400, currentWeightKg: 80, weightLossBlocked: false })!;
    expect(p.dailyBalanceKcal).toBe(-600);
    expect(p.projectedMonthlyDeltaKg).toBeLessThan(0); // -600*30/7700 ≈ -2.3 kg
    expect(p.projectedWeightKg!).toBeLessThan(80);
  });

  it('predicts a small gain above maintenance', () => {
    const p = predictNextMonth({ avgDailyIntake: 2600, maintenanceKcal: 2400, currentWeightKg: 70, weightLossBlocked: false })!;
    expect(p.projectedMonthlyDeltaKg).toBeGreaterThan(0);
    expect(p.projectedWeightKg!).toBeGreaterThan(70);
  });

  it('never predicts loss when weight loss is medically blocked', () => {
    const p = predictNextMonth({ avgDailyIntake: 1500, maintenanceKcal: 2400, currentWeightKg: 60, weightLossBlocked: true })!;
    expect(p.projectedMonthlyDeltaKg).toBeGreaterThanOrEqual(0);
    expect(p.blocked).toBe(true);
  });

  it('caps the monthly change to a sane range and returns null without data', () => {
    const huge = predictNextMonth({ avgDailyIntake: 500, maintenanceKcal: 4000, currentWeightKg: 90, weightLossBlocked: false })!;
    expect(huge.projectedMonthlyDeltaKg).toBe(-4); // clamped
    expect(predictNextMonth({ avgDailyIntake: null, maintenanceKcal: 2400, currentWeightKg: 80, weightLossBlocked: false })).toBeNull();
  });
});

import { buildGroceryList } from '../src/modules/reports/grocery';
import { buildWeeklyReport, reportToCsv } from '../src/modules/reports/report';
import { computeBadges, badgeSummary } from '../src/modules/reports/gamification';
import { SEED_FOODS } from '../src/data/foods.seed';
import { generateWeekPlan } from '../src/modules/food/planGenerator';
import type { PlanTargets } from '../src/modules/food/food.types';

const targets: PlanTargets = { dailyKcal: 2000, proteinG: 130, fatG: 60, carbG: 220, fiberG: 28 };

describe('grocery list', () => {
  const plan = generateWeekPlan(SEED_FOODS, targets, { dietType: 'veg', allergies: [], conditions: [] });

  it('produces a categorized RAW-ingredient shopping list (not dishes)', () => {
    const g = buildGroceryList(plan.days, SEED_FOODS);
    expect(g.categories.length).toBeGreaterThan(0);
    expect(g.totalItems).toBeGreaterThan(0);
    const allNames = g.categories.flatMap((c) => c.items.map((i) => i.name));
    // raw ingredients, never the cooked dish name
    expect(allNames).not.toContain('Aloo paratha');
    expect(allNames.some((n) => /flour/i.test(n))).toBe(true);
    // every line has a positive meal count
    expect(g.categories.every((c) => c.items.every((i) => i.meals > 0))).toBe(true);
  });

  it('groups ingredients into aisles and de-duplicates', () => {
    const g = buildGroceryList(plan.days, SEED_FOODS);
    // total items equals the sum of unique lines across categories
    const lineCount = g.categories.reduce((s, c) => s + c.items.length, 0);
    expect(lineCount).toBe(g.totalItems);
  });
});

describe('weekly report', () => {
  const report = buildWeeklyReport({
    name: 'Test User',
    generatedAt: '2026-07-21T00:00:00Z',
    targets: { dailyKcal: 2000, proteinG: 130, waterMl: 2500 },
    bmi: 27.8,
    latestWeightKg: 80,
    weightDeltaKg: -2,
    days: [
      { date: '2026-07-19', kcal: 1900, proteinG: 120 },
      { date: '2026-07-20', kcal: 2100, proteinG: 130 },
    ],
  });

  it('computes averages and adherence', () => {
    expect(report.avgKcal).toBe(2000);
    expect(report.adherencePct).toBe(100);
    expect(report.disclaimer).toContain('not medical advice');
  });

  it('renders CSV with a header and rows', () => {
    const csv = reportToCsv(report);
    expect(csv).toContain('Kaizen Report');
    expect(csv).toContain('2026-07-19,1900,120');
    expect(csv.split('\n').length).toBeGreaterThan(5);
  });

  it('handles a user with no logged days', () => {
    const empty = buildWeeklyReport({
      name: 'X', generatedAt: 'now', targets: null, bmi: null,
      latestWeightKg: null, weightDeltaKg: null, days: [],
    });
    expect(empty.avgKcal).toBeNull();
    expect(empty.adherencePct).toBeNull();
  });
});

describe('gamification', () => {
  it('awards badges by threshold', () => {
    const badges = computeBadges({
      totalFoodLogs: 12,
      streakDays: 7,
      metWaterTargetToday: true,
      metProteinTargetToday: false,
      weightLostKg: 1.5,
      checkinCount: 2,
    });
    const earned = new Set(badges.filter((b) => b.earned).map((b) => b.code));
    expect(earned.has('first_log')).toBe(true);
    expect(earned.has('streak_7')).toBe(true);
    expect(earned.has('streak_30')).toBe(false);
    expect(earned.has('hydrated')).toBe(true);
    expect(earned.has('protein_pro')).toBe(false);
    expect(earned.has('down_1kg')).toBe(true);
    expect(earned.has('down_5kg')).toBe(false);
  });

  it('summarises earned vs total', () => {
    const s = badgeSummary(
      computeBadges({
        totalFoodLogs: 0, streakDays: 0, metWaterTargetToday: false,
        metProteinTargetToday: false, weightLostKg: 0, checkinCount: 0,
      }),
    );
    expect(s.earnedCount).toBe(0);
    expect(s.total).toBeGreaterThan(0);
  });
});
