import { describe, it, expect } from 'vitest';
import { bmrMifflinStJeor, tdee, energy } from '../src/calc/energy';
import { bmi } from '../src/calc/anthropometry';
import { ACTIVITY_FACTORS } from '../src/calc/types';

const man = { sex: 'male' as const, ageYears: 30, heightCm: 175, weightKg: 70 };

describe('energy maths: known values and hostile inputs', () => {
  it('Mifflin-St Jeor matches hand calculation (male 30y 175cm 70kg = 1649, female = 1483)', () => {
    expect(bmrMifflinStJeor(man)).toBe(1649);
    expect(bmrMifflinStJeor({ ...man, sex: 'female' })).toBe(1483);
  });
  it('TDEE is BMR x factor, and factors rise with activity', () => {
    const levels = Object.keys(ACTIVITY_FACTORS) as (keyof typeof ACTIVITY_FACTORS)[];
    const factors = levels.map((l) => ACTIVITY_FACTORS[l]);
    expect([...factors].sort((a, b) => a - b)).toEqual(factors);
    expect(tdee(1649, levels[0]!)).toBe(Math.round(1649 * factors[0]!));
  });
  it('decimals are handled (70.5 kg / 175.5 cm)', () => {
    expect(energy({ ...man, weightKg: 70.5, heightCm: 175.5 }, 'moderate' as never).bmr).toBe(1657);
  });
  it('zero, negative, NaN and missing values throw instead of producing a target', () => {
    for (const bad of [0, -5, NaN, undefined as unknown as number]) {
      expect(() => bmrMifflinStJeor({ ...man, weightKg: bad }), `weight ${bad}`).toThrow(RangeError);
      expect(() => bmrMifflinStJeor({ ...man, heightCm: bad }), `height ${bad}`).toThrow(RangeError);
      expect(() => bmrMifflinStJeor({ ...man, ageYears: bad }), `age ${bad}`).toThrow(RangeError);
      expect(() => bmi(bad, 175), `bmi weight ${bad}`).toThrow(RangeError);
      expect(() => bmi(70, bad), `bmi height ${bad}`).toThrow(RangeError);
    }
  });
  it('absurdly large values stay finite (validation upstream caps them; the maths must not blow up)', () => {
    expect(Number.isFinite(bmrMifflinStJeor({ ...man, weightKg: 1000, heightCm: 272 }))).toBe(true);
  });
  it('BMI of 70 kg / 175 cm is 22.9', () => {
    expect(bmi(70, 175)).toBe(22.9);
  });
});
