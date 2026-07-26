import { describe, it, expect } from 'vitest';
import { assessRisks, type RiskInput } from '../src/modules/risk/risk';

const base: RiskInput = { sex: 'male', ageYears: 35, bmi: 21, heightCm: 175 };

const ids = (r: ReturnType<typeof assessRisks>) => r.findings.map((f) => f.id);
const byId = (r: ReturnType<typeof assessRisks>, id: string) => r.findings.find((f) => f.id === id);

describe('assessRisks', () => {
  it('a healthy profile with no extra data has no findings', () => {
    const r = assessRisks(base);
    expect(r.findings).toHaveLength(0);
    expect(r.overallScore).toBe(0);
  });

  it('flags central obesity from a high waist-to-height ratio', () => {
    const r = assessRisks({ ...base, waistCm: 110 }); // 110/175 = 0.63
    expect(byId(r, 'central_obesity')?.level).toBe('high');
    expect(byId(r, 'central_obesity')?.why).toContain('0.63');
  });

  it('flags overweight vs obesity by Asian BMI cut-offs', () => {
    expect(byId(assessRisks({ ...base, bmi: 24 }), 'overweight')?.level).toBe('moderate');
    expect(byId(assessRisks({ ...base, bmi: 26 }), 'obesity')?.level).toBe('high');
  });

  it('parses blood pressure and grades hypertension', () => {
    expect(byId(assessRisks({ ...base, bloodPressure: '145/95' }), 'hypertension')?.level).toBe('high');
    expect(byId(assessRisks({ ...base, bloodPressure: '132/86' }), 'hypertension')?.level).toBe('moderate');
    expect(ids(assessRisks({ ...base, bloodPressure: '118/78' }))).not.toContain('hypertension');
  });

  it('does not re-flag a condition the user already declared', () => {
    const r = assessRisks({ ...base, bloodPressure: '150/95', conditions: ['hypertension'] });
    expect(ids(r)).not.toContain('hypertension');
  });

  it('grades fasting glucose into prediabetes / diabetes', () => {
    expect(byId(assessRisks({ ...base, bloodSugar: 110 }), 'prediabetes')?.level).toBe('moderate');
    expect(byId(assessRisks({ ...base, bloodSugar: 140 }), 'diabetes')?.level).toBe('high');
  });

  it('flags short sleep and low hydration', () => {
    expect(byId(assessRisks({ ...base, sleepHours: 5 }), 'poor_sleep')?.level).toBe('high');
    expect(byId(assessRisks({ ...base, sleepHours: 6 }), 'poor_sleep')?.level).toBe('moderate');
    expect(ids(assessRisks({ ...base, hydrationPct: 40 }))).toContain('low_hydration');
  });

  it('every finding carries why + recommendation + nextAction, sorted worst-first', () => {
    const r = assessRisks({ ...base, bmi: 27, waistCm: 112, bloodPressure: '150/96', bloodSugar: 130 });
    expect(r.findings.length).toBeGreaterThanOrEqual(4);
    for (const f of r.findings) {
      expect(f.why).toBeTruthy();
      expect(f.recommendation).toBeTruthy();
      expect(f.nextAction).toBeTruthy();
    }
    // sorted high-first
    expect(r.findings[0].level).toBe('high');
    expect(r.overallScore).toBeGreaterThan(0);
  });

  it('grades HbA1c into prediabetes / diabetes and respects declared diabetes', () => {
    expect(byId(assessRisks({ ...base, hba1c: 6.0 }), 'prediabetes_hba1c')?.level).toBe('moderate');
    expect(byId(assessRisks({ ...base, hba1c: 7.0 }), 'diabetes_hba1c')?.level).toBe('high');
    expect(ids(assessRisks({ ...base, hba1c: 7.0, conditions: ['diabetes'] }))).not.toContain('diabetes_hba1c');
    expect(ids(assessRisks({ ...base, hba1c: 5.2 }))).not.toContain('prediabetes_hba1c');
  });

  it('flags the lipid panel (LDL, triglycerides, low HDL by sex)', () => {
    expect(byId(assessRisks({ ...base, ldl: 170 }), 'high_ldl')?.level).toBe('moderate');
    expect(byId(assessRisks({ ...base, ldl: 195 }), 'high_ldl')?.level).toBe('high');
    expect(byId(assessRisks({ ...base, triglycerides: 250 }), 'high_triglycerides')?.level).toBe('moderate');
    expect(byId(assessRisks({ ...base, triglycerides: 520 }), 'high_triglycerides')?.level).toBe('high');
    // HDL 45: fine for men, flagged for women
    expect(ids(assessRisks({ ...base, sex: 'male', hdl: 45 }))).not.toContain('low_hdl');
    expect(ids(assessRisks({ ...base, sex: 'female', hdl: 45 }))).toContain('low_hdl');
  });

  it('flags TSH out of range unless thyroid already declared', () => {
    expect(byId(assessRisks({ ...base, tsh: 6.0 }), 'high_tsh')?.level).toBe('moderate');
    expect(byId(assessRisks({ ...base, tsh: 0.2 }), 'low_tsh')?.level).toBe('moderate');
    expect(ids(assessRisks({ ...base, tsh: 6.0 }))).not.toContain('low_tsh');
    expect(ids(assessRisks({ ...base, tsh: 2.0 }))).not.toContain('high_tsh');
    expect(ids(assessRisks({ ...base, tsh: 6.0, conditions: ['thyroid'] }))).not.toContain('high_tsh');
  });

  it('flags regular smoking as its own high risk', () => {
    expect(byId(assessRisks({ ...base, smoking: 'regular' }), 'smoking')?.level).toBe('high');
    expect(ids(assessRisks({ ...base, smoking: 'no' }))).not.toContain('smoking');
    expect(ids(assessRisks({ ...base, smoking: 'occasional' }))).not.toContain('smoking');
  });

  it('family history only surfaces alongside another cardiometabolic signal (no lone alarm)', () => {
    // healthy profile + family history alone → no family_history finding
    expect(ids(assessRisks({ ...base, familyHistory: ['diabetes'] }))).not.toContain('family_history');
    // with an actual signal (overweight) it adds context
    const r = assessRisks({ ...base, bmi: 26, familyHistory: ['heart_disease', 'diabetes'] });
    expect(byId(r, 'family_history')?.level).toBe('moderate');
    expect(byId(r, 'family_history')?.why).toMatch(/heart disease/);
  });
});
