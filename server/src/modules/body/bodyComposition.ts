import type { Sex } from '../../calc/types';

/**
 * Deterministic body-composition estimates from tape-measure inputs - NO device needed. The US-Navy
 * circumference method gives a body-fat% ESTIMATE (clearly labelled; ±3-4% vs DEXA), and waist-to-hip
 * ratio gets an educational health band. Body-neutral and non-diagnostic; always defers to a doctor.
 * Pure & unit-tested. Never computed for minors (caller enforces).
 *
 * Source: U.S. Navy circumference method (Hodgdon & Beckett, 1984); WHR bands per WHO 2008.
 */

const log10 = (x: number) => Math.log(x) / Math.LN10;
const round1 = (x: number) => Math.round(x * 10) / 10;

export interface NavyInput {
  sex: Sex;
  heightCm: number;
  neckCm: number;
  waistCm: number;
  /** Required for females (hip circumference). */
  hipCm?: number;
}

/**
 * US-Navy body-fat %. Returns null when inputs are missing/invalid (e.g. waist ≤ neck, or a female
 * without hip) - never a fabricated number. Clamped to a sane 2-60% range.
 */
export function navyBodyFatPct(input: NavyInput): number | null {
  const { sex, heightCm, neckCm, waistCm } = input;
  if (![heightCm, neckCm, waistCm].every((v) => typeof v === 'number' && isFinite(v) && v > 0)) return null;

  let raw: number;
  if (sex === 'female') {
    const hipCm = input.hipCm;
    if (!hipCm || hipCm <= 0) return null;
    const inner = waistCm + hipCm - neckCm;
    if (inner <= 0) return null;
    raw = 495 / (1.29579 - 0.35004 * log10(inner) + 0.221 * log10(heightCm)) - 450;
  } else {
    const inner = waistCm - neckCm;
    if (inner <= 0) return null; // waist must exceed neck
    raw = 495 / (1.0324 - 0.19077 * log10(inner) + 0.15456 * log10(heightCm)) - 450;
  }
  if (!isFinite(raw)) return null;
  return round1(Math.min(60, Math.max(2, raw)));
}

export type WhrRisk = 'low' | 'moderate' | 'high';

export interface WhrResult {
  ratio: number;
  risk: WhrRisk;
  band: string;
  note: string;
}

/** Waist-to-hip ratio + WHO educational health band (sex-specific). null if inputs invalid. */
export function waistHipRatio(sex: Sex, waistCm: number, hipCm: number): WhrResult | null {
  if (![waistCm, hipCm].every((v) => typeof v === 'number' && isFinite(v) && v > 0)) return null;
  const ratio = round2(waistCm / hipCm);
  const female = sex === 'female';
  let risk: WhrRisk;
  if (female) risk = ratio >= 0.85 ? 'high' : ratio >= 0.8 ? 'moderate' : 'low';
  else risk = ratio >= 1.0 ? 'high' : ratio >= 0.9 ? 'moderate' : 'low';
  const band = risk === 'high' ? 'Higher risk' : risk === 'moderate' ? 'Moderate risk' : 'Lower risk';
  const note =
    risk === 'low'
      ? 'A healthy waist-to-hip ratio.'
      : 'A higher ratio signals more central fat. Fibre, protein and daily movement help - discuss with a doctor if concerned.';
  return { ratio, risk, band, note };
}

function round2(x: number) {
  return Math.round(x * 100) / 100;
}

/** Bodyfat category bands (educational, sex-specific - ACE ranges). */
export function bodyFatBand(sex: Sex, pct: number): string {
  const f = sex === 'female';
  if (f) {
    if (pct < 12) return 'Very low - may affect health'; // hormonal/menstrual risk below essential
    if (pct < 16) return 'Athlete range';
    if (pct < 21) return 'Fitness range';
    if (pct < 25) return 'Acceptable range';
    if (pct < 32) return 'Above average';
    return 'High';
  }
  if (pct < 4) return 'Very low - may affect health';
  if (pct < 8) return 'Athlete range';
  if (pct < 14) return 'Fitness range';
  if (pct < 18) return 'Acceptable range';
  if (pct < 25) return 'Above average';
  return 'High';
}
