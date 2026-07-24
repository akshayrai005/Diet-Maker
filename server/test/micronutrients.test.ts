import { describe, it, expect } from 'vitest';
import {
  micronutrientTargets,
  assessMicronutrients,
  type MicronutrientKey,
} from '../src/calc/micronutrients';

/** A full day's intake exactly meeting every RDA for the given sex/age. */
const meetsAll = (sex: 'male' | 'female', age: number): Record<MicronutrientKey, number> =>
  micronutrientTargets(sex, age);

describe('micronutrientTargets', () => {
  it('gives pre-menopausal women more iron', () => {
    expect(micronutrientTargets('female', 30).ironMg).toBe(18);
    expect(micronutrientTargets('male', 30).ironMg).toBe(8);
    expect(micronutrientTargets('female', 55).ironMg).toBe(8); // post-menopausal
  });

  it('raises calcium after 50 and vitamin D after 70', () => {
    expect(micronutrientTargets('male', 60).calciumMg).toBe(1200);
    expect(micronutrientTargets('male', 40).calciumMg).toBe(1000);
    expect(micronutrientTargets('female', 72).vitaminDMcg).toBe(20);
  });

  it('exposes the full expanded panel of 21 nutrients', () => {
    const t = micronutrientTargets('female', 30);
    expect(Object.keys(t)).toHaveLength(21);
    // sex-adjusted Phase 2 values
    expect(t.vitaminAMcg).toBe(700);
    expect(micronutrientTargets('male', 30).vitaminAMcg).toBe(900);
    expect(t.zincMg).toBe(8);
    expect(micronutrientTargets('male', 30).zincMg).toBe(11);
    // B6 rises after 50
    expect(micronutrientTargets('male', 60).vitaminB6Mg).toBe(1.7);
    expect(micronutrientTargets('female', 60).vitaminB6Mg).toBe(1.5);
  });
});

describe('assessMicronutrients', () => {
  it('meeting every RDA yields 100% coverage and no deficiencies', () => {
    const r = assessMicronutrients(meetsAll('female', 30), 'female', 30);
    expect(r.coveragePct).toBe(100);
    expect(r.deficiencies).toHaveLength(0);
    expect(r.nutrients.every((n) => !n.low)).toBe(true);
    expect(r.nutrients.every((n) => n.hasData)).toBe(true);
    expect(r.nutrients).toHaveLength(21);
  });

  it('flags a specific gap and orders deficiencies worst-first', () => {
    const r = assessMicronutrients(
      { ...meetsAll('female', 30), ironMg: 4, calciumMg: 300 },
      'female',
      30,
    );
    expect(r.deficiencies).toContain('ironMg'); // 4/18 = 22%
    expect(r.deficiencies).toContain('calciumMg'); // 300/1000 = 30%
    // iron (22%) is worse than calcium (30%) → comes first
    expect(r.deficiencies.indexOf('ironMg')).toBeLessThan(r.deficiencies.indexOf('calciumMg'));
  });

  it('caps per-nutrient contribution so mega-dosing one does not mask gaps', () => {
    const r = assessMicronutrients(
      { ...meetsAll('male', 30), potassiumMg: 34000, ironMg: 0 },
      'male',
      30,
    );
    expect(r.nutrients.find((n) => n.key === 'ironMg')!.low).toBe(true);
    expect(r.coveragePct).toBeLessThan(100); // iron gap still drags it down
  });

  // --- HONEST "no data" edge cases ---

  it('excludes null nutrients from coverage and never flags them deficient', () => {
    // Only iron and calcium have data (both meeting RDA); everything else is unknown.
    const r = assessMicronutrients(
      { ironMg: 18, calciumMg: 1000, vitaminB12Mcg: null, folateMcg: null },
      'female',
      30,
    );
    const b12 = r.nutrients.find((n) => n.key === 'vitaminB12Mcg')!;
    expect(b12.hasData).toBe(false);
    expect(b12.intake).toBeNull();
    expect(b12.pct).toBeNull();
    expect(b12.low).toBe(false);
    // coverage is averaged only over the two nutrients WITH data → 100%
    expect(r.coveragePct).toBe(100);
    expect(r.deficiencies).not.toContain('vitaminB12Mcg');
    expect(r.deficiencies).not.toContain('folateMcg');
    expect(r.deficiencies).toHaveLength(0);
  });

  it('treats an empty/all-null intake as no data: coverage 0, no deficiencies', () => {
    const empty = assessMicronutrients({}, 'male', 30);
    expect(empty.coveragePct).toBe(0);
    expect(empty.deficiencies).toHaveLength(0);
    expect(empty.nutrients.every((n) => !n.hasData)).toBe(true);
    expect(empty.nutrients.every((n) => n.intake === null && n.pct === null && !n.low)).toBe(true);

    const allNull = assessMicronutrients(
      { ironMg: null, calciumMg: null, zincMg: null },
      'male',
      30,
    );
    expect(allNull.coveragePct).toBe(0);
    expect(allNull.deficiencies).toHaveLength(0);
  });

  it('a nutrient at 0 WITH data is low (distinct from no data)', () => {
    const r = assessMicronutrients({ ironMg: 0 }, 'female', 30);
    const iron = r.nutrients.find((n) => n.key === 'ironMg')!;
    expect(iron.hasData).toBe(true);
    expect(iron.intake).toBe(0);
    expect(iron.pct).toBe(0);
    expect(iron.low).toBe(true);
    expect(r.deficiencies).toContain('ironMg');
    expect(r.coveragePct).toBe(0); // only nutrient with data is at 0%
  });
});
