import { describe, it, expect } from 'vitest';
import { navyBodyFatPct, waistHipRatio, bodyFatBand } from '../src/modules/body/bodyComposition';

describe('US-Navy body-fat estimate', () => {
  it('computes a plausible male estimate', () => {
    // 180cm, neck 38, waist 85 → roughly mid-teens %.
    const bf = navyBodyFatPct({ sex: 'male', heightCm: 180, neckCm: 38, waistCm: 85 })!;
    expect(bf).toBeGreaterThan(8);
    expect(bf).toBeLessThan(25);
  });

  it('computes a plausible female estimate (needs hip)', () => {
    const bf = navyBodyFatPct({ sex: 'female', heightCm: 165, neckCm: 32, waistCm: 74, hipCm: 98 })!;
    expect(bf).toBeGreaterThan(18);
    expect(bf).toBeLessThan(40);
  });

  it('returns null on invalid inputs (never fabricates)', () => {
    expect(navyBodyFatPct({ sex: 'male', heightCm: 180, neckCm: 40, waistCm: 38 })).toBeNull(); // waist ≤ neck
    expect(navyBodyFatPct({ sex: 'female', heightCm: 165, neckCm: 32, waistCm: 74 })).toBeNull(); // no hip
    expect(navyBodyFatPct({ sex: 'male', heightCm: 0, neckCm: 38, waistCm: 85 })).toBeNull();
  });

  it('is clamped to a sane range', () => {
    const bf = navyBodyFatPct({ sex: 'male', heightCm: 200, neckCm: 30, waistCm: 150 })!;
    expect(bf).toBeLessThanOrEqual(60);
    expect(bf).toBeGreaterThanOrEqual(2);
  });
});

describe('waist-to-hip ratio bands (WHO)', () => {
  it('men thresholds', () => {
    expect(waistHipRatio('male', 85, 100)!.risk).toBe('low'); // 0.85
    expect(waistHipRatio('male', 95, 100)!.risk).toBe('moderate'); // 0.95
    expect(waistHipRatio('male', 100, 100)!.risk).toBe('high'); // 1.0
  });
  it('women thresholds are stricter', () => {
    expect(waistHipRatio('female', 70, 100)!.risk).toBe('low'); // 0.70
    expect(waistHipRatio('female', 82, 100)!.risk).toBe('moderate'); // 0.82
    expect(waistHipRatio('female', 88, 100)!.risk).toBe('high'); // 0.88
  });
  it('invalid → null', () => {
    expect(waistHipRatio('male', 0, 100)).toBeNull();
    expect(waistHipRatio('male', 90, 0)).toBeNull();
  });
  it('defers to a doctor in the note', () => {
    expect(waistHipRatio('male', 105, 100)!.note).toMatch(/doctor/i);
  });
});

describe('bodyFatBand', () => {
  it('bands by sex', () => {
    expect(bodyFatBand('male', 10)).toMatch(/fitness/i);
    expect(bodyFatBand('female', 22)).toMatch(/acceptable/i);
    expect(bodyFatBand('male', 30)).toBe('High');
  });
});
