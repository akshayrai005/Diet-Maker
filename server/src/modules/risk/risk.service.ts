import { requireCompleteProfile } from '../profile/profile.service';
import { ageFromDob } from '../nutrition/calc.service';
import { bmi as bmiCalc } from '../../calc/anthropometry';
import { getLatestVitalsForRisk } from '../vitals/vitals.service';
import { assessRisks, type RiskAssessment } from './risk';

/**
 * Runs the deterministic health-risk engine over the user's stored profile numbers
 * (waist, BP, fasting glucose, resting HR) plus their computed BMI AND their latest logged
 * lab/vital time-series (HbA1c, lipid panel, TSH — and fresher BP/glucose/HR override the profile
 * snapshot). Sleep/hydration are optional and passed in from the client (Health Connect / water %).
 */
export async function getRiskAssessment(
  userId: string,
  extra?: { sleepHours?: number; hydrationPct?: number },
): Promise<RiskAssessment> {
  const { profile, sensitive } = await requireCompleteProfile(userId);
  const bmi = bmiCalc(sensitive.currentWeightKg, profile.heightCm);
  const labs = await getLatestVitalsForRisk(userId);
  return assessRisks({
    sex: sensitive.sex,
    ageYears: ageFromDob(sensitive.dob),
    bmi,
    heightCm: profile.heightCm,
    waistCm: sensitive.waistCm,
    // A freshly logged reading (time-series) wins over the older profile snapshot.
    bloodPressure: labs.bloodPressure ?? sensitive.bloodPressure,
    bloodSugar: labs.fastingGlucose ?? sensitive.bloodSugar,
    restingHr: labs.restingHr ?? sensitive.restingHr,
    hba1c: labs.hba1c,
    ldl: labs.ldl,
    hdl: labs.hdl,
    triglycerides: labs.triglycerides,
    tsh: labs.tsh,
    conditions: sensitive.conditions,
    familyHistory: (sensitive as { familyHistory?: string[] }).familyHistory,
    smoking: (sensitive as { smoking?: 'no' | 'occasional' | 'regular' }).smoking,
    alcohol: (sensitive as { alcohol?: 'no' | 'occasional' | 'regular' }).alcohol,
    sleepHours: extra?.sleepHours,
    hydrationPct: extra?.hydrationPct,
  });
}
