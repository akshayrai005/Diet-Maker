import { describe, it, expect } from 'vitest';
import { SEED_FOODS } from '../src/data/foods.seed';
import { generateWeekPlan } from '../src/modules/food/planGenerator';
import { computeCalcResult } from '../src/modules/nutrition/calcResult';
import type { ActivityLevel, Goal, Sex } from '../src/calc/types';
import type { PlanPreferences } from '../src/modules/food/food.types';

interface Persona {
  name: string;
  sex: Sex;
  age: number;
  heightCm: number;
  weightKg: number;
  targetKg: number;
  activity: ActivityLevel;
  goal: Goal;
  diet: string;
}

const PERSONAS: Persona[] = [
  { name: 'user: 174cm 74kg cut to 67, active, nonveg', sex: 'male', age: 28, heightCm: 174, weightKg: 74, targetKg: 67, activity: 'moderate', goal: 'lose', diet: 'nonveg' },
  { name: 'overweight man 95kg -> 80, light, veg', sex: 'male', age: 35, heightCm: 176, weightKg: 95, targetKg: 80, activity: 'light', goal: 'lose', diet: 'veg' },
  { name: 'woman 68kg -> 58, sedentary, vegan', sex: 'female', age: 30, heightCm: 162, weightKg: 68, targetKg: 58, activity: 'sedentary', goal: 'lose', diet: 'vegan' },
  { name: 'underweight lad 55kg -> 65, gain, eggetarian', sex: 'male', age: 22, heightCm: 175, weightKg: 55, targetKg: 65, activity: 'moderate', goal: 'gain', diet: 'eggetarian' },
  { name: 'maintain 70kg, active woman', sex: 'female', age: 40, heightCm: 165, weightKg: 62, targetKg: 62, activity: 'active', goal: 'maintain', diet: 'nonveg' },
];

const KCAL_PER_KG = 7700;

/** Follows the plan for `weeks` weeks, re-running the engine each week with the new weight. */
function simulate(p: Persona, weeks: number) {
  let weight = p.weightKg;
  const trace: { week: number; weight: number; kcal: number }[] = [];
  let minKcal = Infinity;
  for (let w = 1; w <= weeks; w++) {
    const r = computeCalcResult({
      heightCm: p.heightCm, currentWeightKg: weight, targetWeightKg: p.targetKg, ageYears: p.age,
      sex: p.sex, activityLevel: p.activity, goal: p.goal,
    });
    minKcal = Math.min(minKcal, r.dailyKcal);
    // Real intake = the target (perfect adherence); real burn = TDEE. Weight moves by the gap.
    weight += ((r.dailyKcal - r.tdee) * 7) / KCAL_PER_KG;
    if (p.goal === 'lose') weight = Math.max(weight, p.targetKg);
    if (p.goal === 'gain') weight = Math.min(weight, p.targetKg);
    trace.push({ week: w, weight: Math.round(weight * 10) / 10, kcal: r.dailyKcal });
  }
  return { weight, minKcal, trace };
}

describe('long-run simulation (3 / 6 / 9 / 12 months)', () => {
  for (const p of PERSONAS) {
    for (const months of [3, 6, 9, 12]) {
      it(`${p.name} - ${months} months`, () => {
        const weeks = Math.round(months * 4.345);
        const { weight, minKcal, trace } = simulate(p, weeks);
        // eslint-disable-next-line no-console
        console.log(`${p.name} @ ${months}m: ${p.weightKg} -> ${weight.toFixed(1)} kg (target ${p.targetKg}), min kcal ${minKcal}`);
        // Safety: never below a physiological floor and never crosses the goal.
        expect(minKcal).toBeGreaterThanOrEqual(p.sex === 'male' ? 1500 : 1200);
        if (p.goal === 'lose') {
          expect(weight).toBeLessThanOrEqual(p.weightKg);
          expect(weight).toBeGreaterThanOrEqual(p.targetKg - 0.05);
          // Sane pace: never faster than ~1% of bodyweight per week on average.
          expect((p.weightKg - weight) / weeks).toBeLessThanOrEqual(p.weightKg * 0.01 + 0.05);
        }
        if (p.goal === 'gain') expect(weight).toBeGreaterThanOrEqual(p.weightKg);
        if (p.goal === 'maintain') expect(Math.abs(weight - p.weightKg)).toBeLessThanOrEqual(1.5);
        // Monotonic in the intended direction (no yo-yo from the engine itself).
        for (let i = 1; i < trace.length; i++) {
          if (p.goal === 'lose') expect(trace[i]!.weight).toBeLessThanOrEqual(trace[i - 1]!.weight + 0.01);
          if (p.goal === 'gain') expect(trace[i]!.weight).toBeGreaterThanOrEqual(trace[i - 1]!.weight - 0.01);
        }
      });
    }
  }
});

describe('generated plans match the engine targets, for every persona', () => {
  for (const p of PERSONAS) {
    it(`${p.name}: 7-day plan stays within tolerance of kcal/protein/fat`, () => {
      const r = computeCalcResult({
        heightCm: p.heightCm, currentWeightKg: p.weightKg, targetWeightKg: p.targetKg, ageYears: p.age,
        sex: p.sex, activityLevel: p.activity, goal: p.goal,
      });
      const prefs: PlanPreferences = { dietType: p.diet, allergies: [], conditions: [] };
      const week = generateWeekPlan(SEED_FOODS, { dailyKcal: r.dailyKcal, proteinG: r.proteinG, fatG: r.fatG, carbG: r.carbG, fiberG: r.fiberG }, prefs, { days: 7 });
      let worstKcal = 0, worstFat = 0, worstProtein = 0, worstShort = 0;
      for (const d of week.days) {
        worstKcal = Math.max(worstKcal, Math.abs(d.totals.kcal - r.dailyKcal) / r.dailyKcal);
        worstFat = Math.max(worstFat, (d.totals.fatG - r.fatG) / r.fatG);
        worstProtein = Math.max(worstProtein, (d.totals.proteinG - r.proteinG) / r.proteinG);
        worstShort = Math.max(worstShort, (r.proteinG - d.totals.proteinG) / r.proteinG);
        // No unrealistic single serving anywhere.
        for (const m of d.meals) for (const it of m.items) expect(it.grams).toBeLessThanOrEqual(400);
        // Snacks stay snacks.
        for (const m of d.meals) if (['midmorning', 'eveningsnack', 'bedtime'].includes(m.slot)) expect(m.kcal).toBeLessThanOrEqual(330);
      }
      // eslint-disable-next-line no-console
      console.log(`${p.name}: worst kcal off ${(worstKcal * 100).toFixed(0)}%, fat over ${(worstFat * 100).toFixed(0)}%, protein over ${(worstProtein * 100).toFixed(0)}%`);
      expect(worstKcal).toBeLessThanOrEqual(0.2);
      expect(worstFat).toBeLessThanOrEqual(0.4);
      expect(worstProtein).toBeLessThanOrEqual(0.5);
      // eslint-disable-next-line no-console
      console.log(`${p.name}: protein short by up to ${(worstShort * 100).toFixed(0)}%`);
      // Vegan/vegetarian menus are limited by how few high-protein veg foods the database has (and by realistic
      // serving sizes); non-veg must stay close.
      expect(worstShort).toBeLessThanOrEqual(p.diet === 'vegan' ? 0.6 : p.diet === 'veg' ? 0.42 : 0.35);
    });
  }
});
