import { describe, it, expect } from 'vitest';
import { checkRedFlags, checkDisorderedEating } from '../src/modules/safety/redFlags';
import { buildSystemPrompt } from '../src/ai/provider';
import { eligibleFoods } from '../src/modules/food/foodFilter';
import { SEED_FOODS } from '../src/data/foods.seed';
import type { FoodItem } from '../src/modules/food/food.types';

describe('coach safety', () => {
  it('escalates emergencies and self-harm', () => {
    expect(checkRedFlags('I have chest pain while running').urgent).toBe(true);
    expect(checkRedFlags('i want to die').message).toMatch(/crisis|mental-health/);
    expect(checkRedFlags('how much protein should I eat').urgent).toBe(false);
  });
  it('detects disordered-eating language but not normal diet talk', () => {
    for (const t of ['I want to starve myself to get abs', 'how to eat 500 calories a day', 'I make myself throw up after dinner', 'laxatives to lose weight fast']) expect(checkDisorderedEating(t), t).toBe(true);
    for (const t of ['I am cutting to 2000 calories', 'is intermittent fasting ok', 'how do I stop eating junk at night']) expect(checkDisorderedEating(t), t).toBe(false);
  });
  it('the coach prompt forbids the unsafe/unsupported behaviours', () => {
    const p = buildSystemPrompt({ conditions: [], targets: null } as never);
    for (const must of [/never guarantee/i, /photo/i, /spot reduction is a myth/i, /steroids/i, /disordered-eating/i, /never shame/i]) expect(p).toMatch(must);
  });
});

describe('planning pool', () => {
  it('unreviewed AI-estimated foods never enter a plan', () => {
    const ai: FoodItem = { ...SEED_FOODS[0]!, id: 'ai1', name: 'Mystery dish', tags: ['ai-estimated'] };
    const out = eligibleFoods([...SEED_FOODS, ai], { dietType: 'nonveg', allergies: [], conditions: [] });
    expect(out.find((f) => f.id === 'ai1')).toBeUndefined();
  });
  it('staples are unambiguous about cooked vs dry (per-100 g values match the label)', () => {
    const staple = /\b(rice|dal|lentil|rajma|chole|chana|oats|quinoa|pasta|noodles|daliya|dalia)\b/i;
    for (const f of SEED_FOODS) {
      if (!staple.test(f.name)) continue;
      if (/dry|uncooked|raw/i.test(f.name)) expect(f.kcal, `${f.name} dry should be calorie-dense`).toBeGreaterThan(280);
      else if (/cooked|boiled|steamed/i.test(f.name)) expect(f.kcal, `${f.name} cooked should be diluted`).toBeLessThan(250);
    }
  });
});

import { SUPPLEMENTS } from '../src/data/supplements.seed';
describe('supplements', () => {
  it('every supplement is tiered, none is mandatory, no banned substances, vitamin D is capped', () => {
    for (const s of SUPPLEMENTS) {
      expect(['evidence_supported', 'potentially_useful', 'optional']).toContain(s.tier);
      expect(`${s.name} ${s.whyTrusted}`).not.toMatch(/steroid|sarm|clenbuterol|prohormone|fat burner|testosterone booster/i);
    }
    expect(SUPPLEMENTS.find((s) => s.id === 'vitamin-d3')!.typicalDosage).toMatch(/4000 IU/);
  });
});
