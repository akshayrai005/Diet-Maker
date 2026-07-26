import { describe, it, expect } from 'vitest';
import {
  isValidTime,
  normalizeTimes,
  toMinutes,
  adherencePct,
  MEDICATION_DISCLAIMER,
} from '../src/modules/medications/medications';

describe('medications: time validation', () => {
  it('accepts valid HH:mm and rejects junk', () => {
    expect(isValidTime('08:00')).toBe(true);
    expect(isValidTime('23:59')).toBe(true);
    expect(isValidTime('00:00')).toBe(true);
    expect(isValidTime('24:00')).toBe(false);
    expect(isValidTime('7:5')).toBe(false);
    expect(isValidTime('08:60')).toBe(false);
    expect(isValidTime('morning')).toBe(false);
  });

  it('normalizeTimes drops invalid, de-dupes and sorts', () => {
    expect(normalizeTimes(['21:00', '08:00', '08:00', 'bad', '13:30'])).toEqual(['08:00', '13:30', '21:00']);
    expect(normalizeTimes(undefined)).toEqual([]);
    expect(normalizeTimes([])).toEqual([]);
    expect(normalizeTimes(['25:00', 'x'])).toEqual([]);
  });

  it('toMinutes converts clock time', () => {
    expect(toMinutes('00:00')).toBe(0);
    expect(toMinutes('08:30')).toBe(510);
    expect(toMinutes('23:59')).toBe(1439);
  });
});

describe('medications: adherence', () => {
  it('is null when there is no schedule (no expected doses)', () => {
    expect(adherencePct(3, 0, 7)).toBeNull();
    expect(adherencePct(0, 1, 0)).toBeNull();
  });

  it('computes and caps at 100', () => {
    expect(adherencePct(7, 1, 7)).toBe(100);
    expect(adherencePct(5, 1, 10)).toBe(50);
    expect(adherencePct(20, 1, 7)).toBe(100); // over-logged still caps
    expect(adherencePct(3, 2, 7)).toBe(21); // 3 / 14
  });
});

describe('medications: disclaimer', () => {
  it('names pharmacist/doctor and disclaims interaction checking', () => {
    expect(MEDICATION_DISCLAIMER).toMatch(/interaction/i);
    expect(MEDICATION_DISCLAIMER).toMatch(/pharmacist|doctor/i);
  });
});
