import type { Sex } from './types';
import { round } from './anthropometry';

/**
 * Micronutrient RDA targets and intake-vs-RDA assessment. Deterministic, pure.
 *
 * RDA values are adult reference intakes from the US National Academies (DRI) / ICMR-NIN 2020
 * for the Indian population where they differ; chosen conservatively and cited per value below.
 * These are educational targets, not a prescription — deficiency needs a blood test to confirm.
 *
 * HONEST "no data" handling: intake is a partial map where a `null`/`undefined` value means the
 * nutrient was not estimated from any logged food. Such nutrients are never flagged deficient and
 * never counted in coverage — silence is not a zero.
 */
export interface Micronutrients {
  // --- Minerals & vitamins tracked from Phase 1 ---
  ironMg: number;
  calciumMg: number;
  vitaminB12Mcg: number;
  vitaminDMcg: number;
  folateMcg: number;
  potassiumMg: number;
  magnesiumMg: number;
  // --- Phase 2 vitamins ---
  vitaminAMcg: number; // mcg RAE
  vitaminCMg: number;
  vitaminEMg: number; // mg alpha-tocopherol
  vitaminKMcg: number;
  vitaminB1Mg: number; // thiamin
  vitaminB2Mg: number; // riboflavin
  vitaminB3Mg: number; // niacin, mg NE
  vitaminB6Mg: number;
  // --- Phase 2 minerals ---
  zincMg: number;
  seleniumMcg: number;
  iodineMcg: number;
  phosphorusMg: number;
  copperMcg: number;
  manganeseMg: number;
}

export type MicronutrientKey = keyof Micronutrients;

export const MICRONUTRIENT_LABELS: Record<MicronutrientKey, string> = {
  ironMg: 'Iron',
  calciumMg: 'Calcium',
  vitaminB12Mcg: 'Vitamin B12',
  vitaminDMcg: 'Vitamin D',
  folateMcg: 'Folate',
  potassiumMg: 'Potassium',
  magnesiumMg: 'Magnesium',
  vitaminAMcg: 'Vitamin A',
  vitaminCMg: 'Vitamin C',
  vitaminEMg: 'Vitamin E',
  vitaminKMcg: 'Vitamin K',
  vitaminB1Mg: 'Vitamin B1 (Thiamin)',
  vitaminB2Mg: 'Vitamin B2 (Riboflavin)',
  vitaminB3Mg: 'Vitamin B3 (Niacin)',
  vitaminB6Mg: 'Vitamin B6',
  zincMg: 'Zinc',
  seleniumMcg: 'Selenium',
  iodineMcg: 'Iodine',
  phosphorusMg: 'Phosphorus',
  copperMcg: 'Copper',
  manganeseMg: 'Manganese',
};

/** RDA for the given sex/age (adult defaults; sex- and age-adjusted where it matters). */
export function micronutrientTargets(sex: Sex, ageYears: number): Micronutrients {
  const female = sex === 'female';
  const older = ageYears >= 51;
  return {
    // Iron: pre-menopausal women need much more (menstrual losses). DRI: 18 mg (f 19-50), 8 mg else.
    ironMg: female && ageYears < 51 ? 18 : 8,
    // Calcium rises after 50 (esp. women, bone loss). DRI: 1000 mg adult, 1200 mg 51+.
    calciumMg: older ? 1200 : 1000,
    vitaminB12Mcg: 2.4, // DRI RDA 2.4 mcg adults
    // Vitamin D: 15 mcg (600 IU) adults, 20 mcg (800 IU) for 70+. DRI RDA.
    vitaminDMcg: ageYears >= 70 ? 20 : 15,
    folateMcg: 400, // DRI RDA 400 mcg DFE
    // Potassium (adequate intake): men higher than women. DRI AI: 3400 m / 2600 f.
    potassiumMg: female ? 2600 : 3400,
    // Magnesium: men higher; slight bump after 30. DRI RDA.
    magnesiumMg: female ? (ageYears >= 31 ? 320 : 310) : ageYears >= 31 ? 420 : 400,
    // Vitamin A: DRI RDA 900 mcg RAE (m) / 700 mcg RAE (f).
    vitaminAMcg: female ? 700 : 900,
    // Vitamin C: DRI RDA 90 mg (m) / 75 mg (f). (smokers +35 mg, not modelled).
    vitaminCMg: female ? 75 : 90,
    // Vitamin E: DRI RDA 15 mg alpha-tocopherol (both sexes).
    vitaminEMg: 15,
    // Vitamin K: DRI AI 120 mcg (m) / 90 mcg (f).
    vitaminKMcg: female ? 90 : 120,
    // Thiamin (B1): DRI RDA 1.2 mg (m) / 1.1 mg (f).
    vitaminB1Mg: female ? 1.1 : 1.2,
    // Riboflavin (B2): DRI RDA 1.3 mg (m) / 1.1 mg (f).
    vitaminB2Mg: female ? 1.1 : 1.3,
    // Niacin (B3): DRI RDA 16 mg NE (m) / 14 mg NE (f).
    vitaminB3Mg: female ? 14 : 16,
    // Vitamin B6: DRI RDA 1.3 mg (19-50); rises to 1.7 mg (m) / 1.5 mg (f) after 50.
    vitaminB6Mg: older ? (female ? 1.5 : 1.7) : 1.3,
    // Zinc: DRI RDA 11 mg (m) / 8 mg (f).
    zincMg: female ? 8 : 11,
    // Selenium: DRI RDA 55 mcg (both sexes).
    seleniumMcg: 55,
    // Iodine: DRI RDA 150 mcg (both sexes).
    iodineMcg: 150,
    // Phosphorus: DRI RDA 700 mg (both sexes).
    phosphorusMg: 700,
    // Copper: DRI RDA 900 mcg (both sexes).
    copperMcg: 900,
    // Manganese: DRI AI 2.3 mg (m) / 1.8 mg (f).
    manganeseMg: female ? 1.8 : 2.3,
  };
}

export interface MicronutrientStatus {
  key: MicronutrientKey;
  label: string;
  /** Estimated intake, or null when no logged food gave a signal for this nutrient. */
  intake: number | null;
  rda: number;
  /** Intake as a % of RDA (0..999, capped for display); null when there's no data. */
  pct: number | null;
  /** True when there's data AND it is clearly below adequacy (< 67% of RDA). */
  low: boolean;
  /** True when at least one logged food contributed an estimate for this nutrient. */
  hasData: boolean;
}

export interface MicronutrientAssessment {
  nutrients: MicronutrientStatus[];
  /** Average coverage across nutrients WITH data, 0..100 (each capped at 100); 0 if none have data. */
  coveragePct: number;
  /** Keys with data flagged low, worst first. No-data nutrients are never included. */
  deficiencies: MicronutrientKey[];
}

const LOW_THRESHOLD = 0.67; // < 67% of RDA reads as a real gap

/**
 * Compares a day's micronutrient intake against RDA. A key that is `undefined` OR `null` means
 * "no data" for that nutrient: it is reported with intake/pct null, low=false, and is excluded
 * from both coverage and deficiencies. A key present with a number (including 0) counts as data.
 */
export function assessMicronutrients(
  intake: Partial<Record<MicronutrientKey, number | null>>,
  sex: Sex,
  ageYears: number,
): MicronutrientAssessment {
  const rda = micronutrientTargets(sex, ageYears);
  const keys = Object.keys(rda) as MicronutrientKey[];
  const nutrients: MicronutrientStatus[] = keys.map((key) => {
    const raw = intake[key];
    const hasData = raw != null; // excludes undefined and null
    const target = rda[key];
    if (!hasData) {
      return { key, label: MICRONUTRIENT_LABELS[key], intake: null, rda: round(target, 1), pct: null, low: false, hasData: false };
    }
    const got = raw;
    const ratio = target > 0 ? got / target : 0;
    return {
      key,
      label: MICRONUTRIENT_LABELS[key],
      intake: round(got, 1),
      rda: round(target, 1),
      pct: Math.min(999, Math.round(ratio * 100)),
      low: ratio < LOW_THRESHOLD,
      hasData: true,
    };
  });

  const withData = nutrients.filter((n) => n.hasData);
  const coveragePct =
    withData.length === 0
      ? 0
      : round(withData.reduce((s, n) => s + Math.min(100, n.pct ?? 0), 0) / withData.length, 0);

  const deficiencies = withData
    .filter((n) => n.low)
    .sort((a, b) => (a.pct ?? 0) - (b.pct ?? 0))
    .map((n) => n.key);

  return { nutrients, coveragePct, deficiencies };
}
