import { describe, it, expect } from 'vitest';
import { answer, type ChatContext } from '../src/modules/chat/chat.engine';
import { computeAdaptation } from '../src/modules/chat/adaptive';
import { SEED_FOODS } from '../src/data/foods.seed';
import type { FoodItem } from '../src/modules/food/food.types';

function findFood(term: string): FoodItem | undefined {
  const t = term.toLowerCase();
  return SEED_FOODS.find((f) => f.name.toLowerCase().includes(t));
}

function ctx(over: Partial<ChatContext> = {}): ChatContext {
  return {
    targets: { dailyKcal: 2000, proteinG: 130, waterMl: 2600 },
    conditions: [],
    findFood,
    ...over,
  };
}

describe('chat engine', () => {
  it('greets and always includes the disclaimer', () => {
    const r = answer('hi', ctx());
    expect(r.intent).toBe('greeting');
    expect(r.reply).toContain('not medical advice');
  });

  it('answers a food-safety question from the DB', () => {
    const r = answer('can I eat paneer?', ctx());
    expect(r.intent).toBe('food_safety');
    expect(r.reply.toLowerCase()).toContain('paneer');
    expect(r.sources).toContain('paneer');
  });

  it('the structured "Review my plan" prompt is never hijacked by the suggest/targets keyword intents', () => {
    const prompt = 'Review my plan for 2026-09-11 like a supportive gym coach. My daily targets: 1900 kcal, 130g protein. ' +
      'Planned food (total 0 kcal, 0g protein): none yet. Available workout time: 60 min. Planned exercises: none yet. ' +
      'Sleep 23:00-07:00. Tell me if protein/calories are on target, suggest specific Indian food swaps to fix gaps, ' +
      'and whether the workout looks right. Keep it short and friendly.';
    const r = answer(prompt, ctx({ foods: SEED_FOODS }));
    // Must not be hijacked into a generic food-suggestion or bare-targets reply that ignores the
    // actual plan (this exact prompt used to come back as "For more protein, try: Roasted chana...").
    expect(r.intent).not.toBe('coach_suggest');
    expect(r.intent).not.toBe('targets');
  });

  it('never suggests raw/dry foods as something to just eat (they need cooking first)', () => {
    const r = answer('what should I eat for more protein', ctx({ foods: SEED_FOODS, dietType: 'vegetarian' }));
    const lower = r.reply.toLowerCase();
    expect(lower).not.toContain('dry, uncooked');
    expect(lower).not.toContain('raw, uncooked');
  });

  it('answers a food-alternative question with real computed swaps, not the generic target reply', () => {
    const r = answer('alternative of paneer I want very less quantity but 30gram protein', ctx({ foods: SEED_FOODS }));
    expect(r.intent).toBe('coach_alternative');
    // Must NOT be the generic "your daily targets are..." fallback.
    expect(r.reply).not.toContain('daily targets are about');
    expect(r.reply).toContain('30 g protein');
    // Chicken breast has far higher protein-per-kcal than paneer, so it should show up.
    expect(r.reply.toLowerCase()).toContain('chicken');
  });

  it('warns a diabetic about a high-sugar food', () => {
    const r = answer('can I eat banana?', ctx({ conditions: ['diabetes'] }));
    expect(r.intent).toBe('food_safety');
    expect(r.reply.toLowerCase()).toMatch(/sugar|glyc|portion/);
  });

  it('gives protein/calorie targets when known', () => {
    const r = answer('how much protein should I eat?', ctx());
    expect(r.intent).toBe('targets');
    expect(r.reply).toContain('130');
  });

  it('asks to complete the profile when targets are unknown', () => {
    const r = answer('how many calories?', ctx({ targets: null }));
    expect(r.reply.toLowerCase()).toContain('profile');
  });

  it('answers water questions', () => {
    const r = answer('how much water?', ctx());
    expect(r.intent).toBe('water');
    expect(r.reply).toContain('2600');
  });

  it('explains a safe weight-loss pace', () => {
    const r = answer('how fast can I lose weight?', ctx());
    expect(r.intent).toBe('weight_pace');
    expect(r.reply).toMatch(/0\.75%|body weight/);
  });

  it('falls back gracefully on unknown questions', () => {
    const r = answer('what is the capital of France', ctx());
    expect(r.intent).toBe('fallback');
    expect(r.reply).toContain('not medical advice');
  });

  it('handles a food not in the DB', () => {
    const r = answer('can I eat dragonfruit?', ctx());
    expect(r.intent).toBe('food_safety');
    expect(r.reply.toLowerCase()).toContain('dragonfruit');
  });
});

describe('computeAdaptation', () => {
  const wp = (date: string, weightKg: number) => ({ date, weightKg });

  it('needs a few days of data first', () => {
    const a = computeAdaptation({ goal: 'lose', targetKcal: 1800, loggedDailyKcals: [1800], weightPoints: [] });
    expect(a.status).toBe('insufficient_data');
  });

  it('flags over-eating as a behaviour fix, not a target change', () => {
    const a = computeAdaptation({
      goal: 'lose',
      targetKcal: 1800,
      loggedDailyKcals: [2200, 2300, 2150],
      weightPoints: [],
    });
    expect(a.status).toBe('adjust_behaviour');
    expect(a.suggestedKcalDelta).toBe(0);
  });

  it('lowers the target when on-plan but weight is flat', () => {
    const a = computeAdaptation({
      goal: 'lose',
      targetKcal: 1800,
      loggedDailyKcals: [1800, 1820, 1790],
      weightPoints: [wp('2026-07-01T00:00:00Z', 80), wp('2026-07-15T00:00:00Z', 80)],
    });
    expect(a.status).toBe('adjust_target');
    expect(a.suggestedKcalDelta).toBe(-150);
  });

  it('reports on-track when intake and weight are moving well', () => {
    const a = computeAdaptation({
      goal: 'lose',
      targetKcal: 1800,
      loggedDailyKcals: [1800, 1810, 1795],
      weightPoints: [wp('2026-07-01T00:00:00Z', 80), wp('2026-07-15T00:00:00Z', 78.5)],
    });
    expect(a.status).toBe('on_track');
  });
});
