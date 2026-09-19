import { describe, it, expect } from 'vitest';
import { SEED_FOODS } from '../src/data/foods.seed';
import { generateWeekPlan } from '../src/modules/food/planGenerator';
import { isIngredientOnly } from '../src/modules/food/foodFilter';
import type { FoodItem, PlanPreferences } from '../src/modules/food/food.types';

/** Ingredient rows like the ones users' food tables collect from recipes / AI estimates (tagged for every slot). */
const junk = (id: string, name: string, kcal: number, serving: number): FoodItem => ({
  ...SEED_FOODS[0]!, id, name, kcal, proteinG: 8, carbG: 70, fatG: 1, typicalServingG: serving,
  mealSlots: ['breakfast', 'midmorning', 'lunch', 'eveningsnack', 'dinner', 'bedtime'] as FoodItem['mealSlots'], tags: [],
});
const JUNK = [junk('j1', 'wheat flour', 364, 30), junk('j2', 'Whole wheat flour (atta)', 340, 30), junk('j3', 'maida', 350, 30), junk('j4', 'Sunflower oil', 884, 10), junk('j5', 'sugar', 387, 10), junk('j6', 'Garam masala', 300, 5), junk('j7', 'Rice flour', 360, 30)];
const POOL = [...SEED_FOODS, ...JUNK];

const PERSONAS: Array<{ name: string; kcal: number; protein: number; diet: string; conditions: string[] }> = [
  { name: 'user (fatty liver, non-veg)', kcal: 2597, protein: 148, diet: 'nonveg', conditions: ['fatty_liver'] },
  { name: 'small woman veg', kcal: 1500, protein: 90, diet: 'veg', conditions: [] },
  { name: 'big eater', kcal: 3200, protein: 170, diet: 'nonveg', conditions: [] },
  { name: 'vegan', kcal: 1900, protein: 100, diet: 'vegan', conditions: [] },
];

describe('meal plans are realistic', () => {
  it('raw ingredients are recognised as ingredients, real dishes are not', () => {
    for (const n of ['wheat flour', 'Whole wheat flour (atta)', 'maida', 'Rice flour', 'Sunflower oil', 'sugar', 'ghee', 'Garam masala', 'Cumin powder']) expect(isIngredientOnly(n), n).toBe(true);
    for (const n of ['Sattu drink (roasted gram flour + water)', 'Whole-wheat roti', 'Besan chilla', 'Whole-wheat bread + peanut butter', 'Coffee (milk + sugar)', 'Roasted makhana (fox nuts)', 'Paneer (cottage cheese)', 'Peanut butter']) expect(isIngredientOnly(n), n).toBe(false);
  });

  for (const p of PERSONAS) {
    it(`${p.name}: no ingredient rows, sane portions and meals, snacks stay snacks`, () => {
      const prefs: PlanPreferences = { dietType: p.diet, allergies: [], conditions: p.conditions as PlanPreferences['conditions'] };
      const plan = generateWeekPlan(POOL, { dailyKcal: p.kcal, proteinG: p.protein, fatG: 60, carbG: 300, fiberG: 30 }, prefs, {});
      const serving = new Map(POOL.map((f) => [f.id, f.typicalServingG]));
      for (const d of plan.days) {
        for (const m of d.meals) {
          for (const it of m.items) {
            expect(JUNK.some((j) => j.id === it.foodId), `${d.dayIndex}/${m.slot}: ${it.name}`).toBe(false);
            expect(isIngredientOnly(it.name), it.name).toBe(false);
            // <= 2x a normal serving (up to 1.4x more only when nothing new fits), never above 400 g
            const limit = Math.min(400, Math.max(90, (serving.get(it.foodId) ?? 100) * 2) * 1.4 + 5);
            expect(it.grams, `${d.dayIndex}/${m.slot}: ${it.name} ${it.grams}g`).toBeLessThanOrEqual(limit);
          }
          if (['midmorning', 'eveningsnack', 'bedtime', 'wakeup'].includes(m.slot)) expect(m.kcal, `${m.slot} snack`).toBeLessThanOrEqual(330);
          expect(m.kcal / p.kcal, `${d.dayIndex}/${m.slot} share of day`).toBeLessThanOrEqual(0.46);
          // front-loaded, light dinner; breakfast a proper but not absurd meal
          if (m.slot === 'dinner') expect(m.kcal / p.kcal, `${d.dayIndex} dinner share`).toBeLessThanOrEqual(0.3);
          if (m.slot === 'breakfast') expect(m.kcal / p.kcal, `${d.dayIndex} breakfast share`).toBeLessThanOrEqual(0.36);
          // never two chickens / soya + chicken on one plate, never raw meat
          const fam = (n: string) => (/chicken/i.test(n) ? 'chicken' : /soya|soy /i.test(n) ? 'soya' : /\begg/i.test(n) ? 'egg' : /fish|prawn/i.test(n) ? 'fish' : /paneer/i.test(n) ? 'paneer' : /mutton|keema/i.test(n) ? 'mutton' : '');
          const fams = m.items.map((i) => fam(i.name)).filter(Boolean);
          expect(new Set(fams).size, `${d.dayIndex}/${m.slot} doubled protein: ${m.items.map((i) => i.name).join(' + ')}`).toBe(fams.length);
          expect(fams.length, `${d.dayIndex}/${m.slot} two main proteins: ${m.items.map((i) => i.name).join(' + ')}`).toBeLessThanOrEqual(1);
          for (const it of m.items) expect(it.name, 'raw item').not.toMatch(/[(]raw(?!, uncooked)/i);
        }
        expect(Math.abs(d.totals.kcal - p.kcal) / p.kcal, `day ${d.dayIndex} kcal`).toBeLessThanOrEqual(0.15);
      }
    });
  }
});

describe('morning + night pattern (breakfast, evening meal, dinner)', () => {
  const prefs: PlanPreferences = { dietType: 'nonveg', allergies: [], conditions: [] };
  for (const kcal of [1800, 2400, 2895, 3300]) {
    it(`${kcal} kcal: three real meals, dinner stays the lightest big meal, day lands on target`, () => {
      const plan = generateWeekPlan(POOL, { dailyKcal: kcal, proteinG: 150, fatG: 64, carbG: 380, fiberG: 36 }, prefs, { eatingPattern: 'morning_night', fastDayOfWeek: 2 });
      for (const d of plan.days) {
        if (d.totals.kcal < kcal * 0.6) continue; // the weekly fasting day is deliberately light
        const by = Object.fromEntries(d.meals.filter((m) => m.kcal > 0).map((m) => [m.slot, m]));
        expect(Object.keys(by).sort(), 'only the three meals').toEqual(['breakfast', 'dinner', 'eveningsnack']);
        expect(by.eveningsnack!.items.length, 'evening is a plate, not one snack').toBeGreaterThanOrEqual(2);
        expect(by.breakfast!.kcal / kcal, `${d.dayIndex} breakfast`).toBeLessThanOrEqual(0.42);
        expect(by.eveningsnack!.kcal / kcal, `${d.dayIndex} evening`).toBeLessThanOrEqual(0.4);
        expect(by.dinner!.kcal / kcal, `${d.dayIndex} dinner`).toBeLessThanOrEqual(0.33);
        expect(Math.abs(d.totals.kcal - kcal) / kcal, `day ${d.dayIndex} kcal`).toBeLessThanOrEqual(0.12);
        for (const m of d.meals) for (const it of m.items) expect(it.grams, it.name).toBeLessThanOrEqual(400);
      }
    });
  }
});

describe('week-level nutrition stays near the targets (morning + night pattern)', () => {
  const prefs: PlanPreferences = { dietType: 'nonveg', allergies: [], conditions: [] };
  it('average fat and protein over the training days are within 15% / 25% of target, no meal over 45% of the day', () => {
    const plan = generateWeekPlan(POOL, { dailyKcal: 2895, proteinG: 150, fatG: 64, carbG: 430, fiberG: 36 }, prefs, { eatingPattern: 'morning_night', fastDayOfWeek: 2 });
    const days = plan.days.filter((d) => d.totals.kcal > 2000);
    const avg = (pick: (d: (typeof days)[number]) => number) => days.reduce((s, d) => s + pick(d), 0) / days.length;
    expect(Math.abs(avg((d) => d.totals.fatG) - 64) / 64, 'avg fat').toBeLessThanOrEqual(0.15);
    expect(Math.abs(avg((d) => d.totals.proteinG) - 150) / 150, 'avg protein').toBeLessThanOrEqual(0.25);
    for (const d of days) for (const m of d.meals) expect(m.kcal / 2895, `${d.dayIndex}/${m.slot}`).toBeLessThanOrEqual(0.45);
  });
});
