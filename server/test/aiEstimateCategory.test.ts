import { describe, it, expect } from 'vitest';
import { categoryFromName } from '../src/modules/food/aiEstimate';

describe('categoryFromName - name-based safety net for AI-estimated foods', () => {
  it('catches obvious meat/fish names as nonveg, regardless of the LLM category field', () => {
    for (const name of ['chicken curry', 'Fish', 'Mutton biryani', 'Prawn masala', 'Chicken Keema']) {
      expect(categoryFromName(name)).toBe('nonveg');
    }
  });

  it('catches egg dishes as egg (not vegetarian/vegan)', () => {
    for (const name of ['Egg curry', 'Omelette', 'Boiled egg']) {
      expect(categoryFromName(name)).toBe('egg');
    }
  });

  it('returns null (defer to the LLM) for genuinely vegetarian/vegan names', () => {
    for (const name of ['Bajra flour', 'Paneer bhurji', 'Moong dal chilla', 'Ragi porridge']) {
      expect(categoryFromName(name)).toBeNull();
    }
  });
});
