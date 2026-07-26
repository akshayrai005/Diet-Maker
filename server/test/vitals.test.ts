import { describe, it, expect } from 'vitest';
import {
  classifyVital,
  computeTrend,
  isValidReading,
  primaryValue,
  VITAL_TYPES,
  VITAL_UNIT,
} from '../src/modules/vitals/vitals';

describe('vitals: validation', () => {
  it('blood pressure needs systolic > diastolic, both positive & in range', () => {
    expect(isValidReading('bloodPressure', { systolic: 120, diastolic: 80 })).toBe(true);
    expect(isValidReading('bloodPressure', { systolic: 80, diastolic: 120 })).toBe(false); // inverted
    expect(isValidReading('bloodPressure', { systolic: 120 })).toBe(false); // missing diastolic
    expect(isValidReading('bloodPressure', { value: 120 })).toBe(false); // wrong shape
    expect(isValidReading('bloodPressure', { systolic: -1, diastolic: 80 })).toBe(false);
  });

  it('single-value metrics need a finite positive value', () => {
    expect(isValidReading('fastingGlucose', { value: 95 })).toBe(true);
    expect(isValidReading('fastingGlucose', { value: 0 })).toBe(false);
    expect(isValidReading('fastingGlucose', { value: -5 })).toBe(false);
    expect(isValidReading('fastingGlucose', { value: Number.NaN })).toBe(false);
    expect(isValidReading('fastingGlucose', {})).toBe(false);
  });

  it('classify returns null for invalid readings (never fabricates a band)', () => {
    expect(classifyVital('bloodPressure', { systolic: 80, diastolic: 120 })).toBeNull();
    expect(classifyVital('hba1c', { value: Number.NaN })).toBeNull();
    expect(classifyVital('ldl', {})).toBeNull();
  });

  it('every declared type has a unit', () => {
    for (const t of VITAL_TYPES) expect(VITAL_UNIT[t]).toBeTruthy();
  });
});

describe('vitals: blood pressure bands (ACC/AHA 2017)', () => {
  const b = (s: number, d: number) => classifyVital('bloodPressure', { systolic: s, diastolic: d })!;
  it('normal', () => expect(b(118, 76).severity).toBe('normal'));
  it('elevated (120-129 / <80)', () => expect(b(125, 78).band).toBe('Elevated'));
  it('stage 1 by systolic', () => expect(b(134, 78).band).toBe('Stage 1 hypertension'));
  it('stage 1 by diastolic only', () => expect(b(118, 82).band).toBe('Stage 1 hypertension'));
  it('stage 2', () => expect(b(150, 95).severity).toBe('high'));
  it('crisis escalates to urgent', () => {
    const r = b(185, 100);
    expect(r.severity).toBe('urgent');
    expect(r.band).toMatch(/crisis/i);
  });
  it('cites the guideline', () => expect(b(120, 80).guideline).toBe('ACC/AHA 2017'));
});

describe('vitals: glucose & HbA1c (ADA 2024)', () => {
  it('fasting glucose bands', () => {
    expect(classifyVital('fastingGlucose', { value: 90 })!.band).toBe('Normal');
    expect(classifyVital('fastingGlucose', { value: 110 })!.band).toBe('Prediabetes range');
    expect(classifyVital('fastingGlucose', { value: 130 })!.severity).toBe('high');
    expect(classifyVital('fastingGlucose', { value: 60 })!.band).toBe('Low');
  });
  it('boundary: 100 is prediabetes, 99 is normal', () => {
    expect(classifyVital('fastingGlucose', { value: 99 })!.band).toBe('Normal');
    expect(classifyVital('fastingGlucose', { value: 100 })!.band).toBe('Prediabetes range');
  });
  it('hba1c bands', () => {
    expect(classifyVital('hba1c', { value: 5.4 })!.band).toBe('Normal');
    expect(classifyVital('hba1c', { value: 6.0 })!.band).toBe('Prediabetes range');
    expect(classifyVital('hba1c', { value: 7.2 })!.severity).toBe('high');
  });
});

describe('vitals: lipids (NCEP ATP III), sex-aware HDL', () => {
  it('LDL bands', () => {
    expect(classifyVital('ldl', { value: 90 })!.band).toBe('Optimal');
    expect(classifyVital('ldl', { value: 140 })!.band).toBe('Borderline high');
    expect(classifyVital('ldl', { value: 170 })!.severity).toBe('elevated');
    expect(classifyVital('ldl', { value: 200 })!.severity).toBe('high');
  });
  it('HDL threshold differs by sex', () => {
    expect(classifyVital('hdl', { value: 45 }, { sex: 'male' })!.severity).toBe('normal');
    expect(classifyVital('hdl', { value: 45 }, { sex: 'female' })!.severity).toBe('elevated');
    expect(classifyVital('hdl', { value: 65 }, { sex: 'male' })!.band).toBe('Protective');
  });
  it('triglycerides bands', () => {
    expect(classifyVital('triglycerides', { value: 120 })!.band).toBe('Normal');
    expect(classifyVital('triglycerides', { value: 170 })!.band).toBe('Borderline high');
    expect(classifyVital('triglycerides', { value: 550 })!.severity).toBe('high');
  });
});

describe('vitals: thyroid, vitamin D, iron, hemoglobin, waist', () => {
  it('TSH low/normal/high', () => {
    expect(classifyVital('tsh', { value: 2.0 })!.band).toBe('Normal');
    expect(classifyVital('tsh', { value: 6.0 })!.band).toMatch(/underactive/i);
    expect(classifyVital('tsh', { value: 0.2 })!.band).toMatch(/overactive/i);
  });
  it('vitamin D deficiency ladder', () => {
    expect(classifyVital('vitaminD', { value: 15 })!.band).toBe('Deficient');
    expect(classifyVital('vitaminD', { value: 25 })!.band).toBe('Insufficient');
    expect(classifyVital('vitaminD', { value: 45 })!.band).toBe('Sufficient');
  });
  it('hemoglobin anaemia cut-off differs by sex', () => {
    expect(classifyVital('hemoglobin', { value: 12.5 }, { sex: 'male' })!.severity).toBe('elevated');
    expect(classifyVital('hemoglobin', { value: 12.5 }, { sex: 'female' })!.severity).toBe('normal');
  });
  it('waist uses Asian sex-specific cut-off', () => {
    expect(classifyVital('waist', { value: 85 }, { sex: 'male' })!.severity).toBe('normal');
    expect(classifyVital('waist', { value: 85 }, { sex: 'female' })!.severity).toBe('elevated');
  });
});

describe('vitals: primaryValue & trend', () => {
  it('primary value is systolic for BP, value otherwise', () => {
    expect(primaryValue('bloodPressure', { systolic: 130, diastolic: 85 })).toBe(130);
    expect(primaryValue('weight', { value: 72 })).toBe(72);
    expect(primaryValue('weight', { value: -1 })).toBeNull();
  });
  it('needs 2+ points', () => {
    expect(computeTrend([120]).direction).toBe('insufficient');
    expect(computeTrend([]).direction).toBe('insufficient');
  });
  it('detects falling / rising / stable', () => {
    expect(computeTrend([140, 135, 128]).direction).toBe('falling');
    expect(computeTrend([120, 130, 145]).direction).toBe('rising');
    expect(computeTrend([120, 120.3, 119.8]).direction).toBe('stable');
  });
  it('reports the signed change from first to latest', () => {
    const t = computeTrend([150, 140, 132]);
    expect(t.change).toBe(-18);
    expect(t.summary).toMatch(/Down 18/);
  });
});
