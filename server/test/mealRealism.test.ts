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

describe('morning + night pattern: two real meals + three small snacks (matches the Office tab)', () => {
  const prefs: PlanPreferences = { dietType: 'nonveg', allergies: [], conditions: [] };
  for (const kcal of [1800, 2400, 2913, 3300]) {
    it(`${kcal} kcal: breakfast + dinner are the meals, three snacks stay small, day lands near target`, () => {
      const plan = generateWeekPlan(POOL, { dailyKcal: kcal, proteinG: 150, fatG: 64, carbG: 380, fiberG: 36 }, prefs, { eatingPattern: 'morning_night', fastDayOfWeek: 2 });
      for (const d of plan.days) {
        if (d.totals.kcal < kcal * 0.6) continue; // the weekly fasting day is deliberately light
        const by = Object.fromEntries(d.meals.filter((m) => m.kcal > 0).map((m) => [m.slot, m]));
        expect(Object.keys(by).sort(), 'the five slots').toEqual(['bedtime', 'breakfast', 'dinner', 'eveningsnack', 'midmorning']);
        expect(by.breakfast!.kcal / kcal, `${d.dayIndex} breakfast`).toBeLessThanOrEqual(0.4);
        expect(by.dinner!.kcal / kcal, `${d.dayIndex} dinner`).toBeLessThanOrEqual(0.4);
        for (const sn of ['midmorning', 'eveningsnack', 'bedtime']) expect(by[sn]!.kcal / kcal, `${d.dayIndex} ${sn}`).toBeLessThanOrEqual(0.24);
        expect(Math.abs(d.totals.kcal - kcal) / kcal, `day ${d.dayIndex} kcal`).toBeLessThanOrEqual(0.15);
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

describe('constipation / hard-stool mode', () => {
  const prefs: PlanPreferences = { dietType: 'nonveg', allergies: [], conditions: ['constipation'] as PlanPreferences['conditions'] };
  it('plans stay complete and fibre-rich, with no refined, fried or low-fibre starchy foods', () => {
    const plain = generateWeekPlan(POOL, { dailyKcal: 2600, proteinG: 150, fatG: 64, carbG: 380, fiberG: 36 }, { ...prefs, conditions: [] }, {});
    const gut = generateWeekPlan(POOL, { dailyKcal: 2600, proteinG: 150, fatG: 64, carbG: 380, fiberG: 36 }, prefs, {});
    const byId = new Map(POOL.map((f) => [f.id, f]));
    const avgFibre = (p: typeof gut) => p.days.reduce((s, d) => s + d.totals.fiberG, 0) / p.days.length;
    for (const d of gut.days) for (const m of d.meals) for (const it of m.items) {
      const f = byId.get(it.foodId)!;
      expect(f.tags, it.name).not.toContain('refined');
      expect(f.tags, it.name).not.toContain('fried');
      expect(!(f.fiberG < 1.2 && f.carbG > 20 && f.kcal > 100), `${it.name} is a low-fibre starch`).toBe(true);
    }
    expect(gut.days.every((d) => d.meals.filter((m) => m.kcal > 100).length >= 3), 'every day still has its meals').toBe(true);
    expect(avgFibre(gut)).toBeGreaterThanOrEqual(avgFibre(plain) * 0.95);
  });
});

import { EATING_PATTERNS } from '../src/modules/food/planGenerator';
describe('every eating pattern produces the meals it promises', () => {
  for (const [id, pat] of Object.entries(EATING_PATTERNS)) {
    it(`${id}: only its own slots, snacks stay small, main plates realistic, day near target`, () => {
      const kcal = id === 'omad' ? 2000 : 2500;
      const plan = generateWeekPlan(POOL, { dailyKcal: kcal, proteinG: 140, fatG: 64, carbG: 340, fiberG: 34 }, { dietType: 'nonveg', allergies: [], conditions: [] }, { eatingPattern: id, fastDayOfWeek: 2 });
      for (const d of plan.days) {
        if (d.totals.kcal < kcal * 0.6) continue;
        const slots = d.meals.filter((m) => m.kcal > 0).map((m) => m.slot);
        expect([...slots].sort(), `${id} slots`).toEqual([...pat.slots].sort());
        expect(Math.abs(d.totals.kcal - kcal) / kcal, `${id} day ${d.dayIndex} kcal`).toBeLessThanOrEqual(0.2);
        for (const m of d.meals) {
          const isMain = pat.mains.includes(m.slot);
          if (!isMain) expect(m.kcal / kcal, `${id} snack ${m.slot}`).toBeLessThanOrEqual(0.26);
          else if (id !== 'omad') expect(m.kcal / kcal, `${id} main ${m.slot}`).toBeLessThanOrEqual(0.4);
          for (const it of m.items) expect(it.grams, it.name).toBeLessThanOrEqual(400);
        }
      }
    });
  }
});
