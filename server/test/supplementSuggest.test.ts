import { describe, it, expect } from 'vitest';
import { eligibleSupplements, suggestSupplements, type SupplementSuggestInput } from '../src/modules/nutrition/supplementSuggest';

function base(overrides: Partial<SupplementSuggestInput> = {}): SupplementSuggestInput {
  return { dietType: 'nonveg', conditions: [], allergies: [], proteinAdherencePct: 95, ...overrides };
}

describe('eligibleSupplements', () => {
  it('excludes creatine for kidney disease', () => {
    const list = eligibleSupplements(base({ conditions: ['kidney_disease'] }));
    expect(list.some((s) => s.id === 'creatine-monohydrate')).toBe(false);
  });

  it('excludes whey protein for a milk/dairy allergy', () => {
    const list = eligibleSupplements(base({ allergies: ['milk'] }));
    expect(list.some((s) => s.id === 'whey-protein')).toBe(false);
    // Plant protein must still be offered - the gap doesn't just disappear.
    expect(list.some((s) => s.id === 'plant-protein')).toBe(true);
  });

  it('excludes whey protein for a lactose allergy too (synonym expansion)', () => {
    const list = eligibleSupplements(base({ allergies: ['lactose'] }));
    expect(list.some((s) => s.id === 'whey-protein')).toBe(false);
  });

  it('only offers plant protein (not whey) to a vegan user', () => {
    const list = eligibleSupplements(base({ dietType: 'vegan' }));
    expect(list.some((s) => s.id === 'whey-protein')).toBe(false);
    expect(list.some((s) => s.id === 'plant-protein')).toBe(true);
  });

  it('excludes electrolyte supplements for hypertension', () => {
    const list = eligibleSupplements(base({ conditions: ['hypertension'] }));
    expect(list.some((s) => s.id === 'electrolytes')).toBe(false);
  });

  it('a nonveg user with no conditions/allergies sees the full catalog (both protein sources included - plant protein suits everyone, not just vegan/jain/satvik)', () => {
    const list = eligibleSupplements(base());
    const ids = list.map((s) => s.id);
    expect(ids).toContain('whey-protein');
    expect(ids).toContain('plant-protein');
    expect(ids).toContain('creatine-monohydrate');
    expect(ids).toContain('vitamin-d3');
    expect(ids).toContain('omega-3');
    expect(ids).toContain('electrolytes');
  });
});

describe('suggestSupplements', () => {
  it('flags the protein supplement with a reason when protein adherence is genuinely low', () => {
    const list = suggestSupplements(base({ proteinAdherencePct: 55 }));
    const whey = list.find((s) => s.id === 'whey-protein')!;
    expect(whey.reason).toBeDefined();
    expect(whey.reason).toContain('55%');
  });

  it('does NOT flag protein supplements when adherence is fine - not a default nudge', () => {
    const list = suggestSupplements(base({ proteinAdherencePct: 95 }));
    const whey = list.find((s) => s.id === 'whey-protein')!;
    expect(whey.reason).toBeUndefined();
  });

  it('does NOT flag protein supplements when adherence is unknown (no data yet)', () => {
    const list = suggestSupplements(base({ proteinAdherencePct: null }));
    const whey = list.find((s) => s.id === 'whey-protein')!;
    expect(whey.reason).toBeUndefined();
  });

  it('suggests the diet-appropriate protein source (plant, not whey) for a vegan user with a low protein average', () => {
    const list = suggestSupplements(base({ dietType: 'vegan', proteinAdherencePct: 50 }));
    const plant = list.find((s) => s.id === 'plant-protein')!;
    expect(plant.reason).toContain('50%');
    expect(list.some((s) => s.id === 'whey-protein')).toBe(false);
  });

  it('never suggests an unsafe supplement even when it would otherwise be the flagged one', () => {
    // Someone with a milk allergy AND low protein - must never see whey flagged, only plant.
    const list = suggestSupplements(base({ allergies: ['dairy'], proteinAdherencePct: 40 }));
    expect(list.some((s) => s.id === 'whey-protein')).toBe(false);
    const plant = list.find((s) => s.id === 'plant-protein');
    expect(plant?.reason).toContain('40%');
  });
});
