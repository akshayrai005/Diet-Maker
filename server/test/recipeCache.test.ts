import { describe, it, expect } from 'vitest';
import { recipeKey } from '../src/modules/recipe/recipe.service';

describe('recipe cache key', () => {
  it('treats case, spacing and punctuation differences as the same dish', () => {
    const k = recipeKey('Vegetable  Upma');
    expect(k).toBe('vegetable upma');
    expect(recipeKey(' vegetable upma ')).toBe(k);
    expect(recipeKey('Vegetable-Upma!')).toBe(k);
  });
  it('keeps different dishes apart and handles empty / hostile input', () => {
    expect(recipeKey('Paneer burji')).not.toBe(recipeKey('Paneer butter masala'));
    expect(recipeKey('   ')).toBe('');
    expect(recipeKey("x'; DROP TABLE foods; --")).toBe('x drop table foods');
    expect(recipeKey('a'.repeat(500)).length).toBe(120);
  });
});
