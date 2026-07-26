import { prisma } from '../../lib/prisma';
import { requireCompleteProfile } from '../profile/profile.service';
import { ageFromDob } from '../nutrition/calc.service';
import { geminiVisionJson, visionAvailable } from '../../ai/vision';
import { bmi as bmiCalc, bodyFatDeurenberg } from '../../calc/anthropometry';
import { encryptJson, decryptJson } from '../../lib/crypto';
import { HttpError } from '../../middleware/error';
import type { Sex } from '../../calc/types';
import type { SensitiveData } from '../profile/profile.schemas';
import { navyBodyFatPct, waistHipRatio, bodyFatBand, type WhrResult } from './bodyComposition';

const DISCLAIMER =
  'This is a rough estimate for motivation only — not a medical or DEXA measurement. Body composition is best tracked as a trend over time.';

export interface BodyAssessment {
  available: boolean; // vision provider configured
  refused?: boolean;
  reason?: string;
  bodyFatLow?: number;
  bodyFatHigh?: number;
  category?: string;
  notes?: string;
  confidence?: string;
  formulaEstimatePct?: number | null;
  /** Where the estimate came from: 'ai' (photo) or 'formula' (BMI/age/sex fallback). */
  source?: 'ai' | 'formula';
  disclaimer: string;
}

/** Body-fat category from % and sex (ACE-style bands). */
function bfCategory(pct: number, sex: string): string {
  const male = sex === 'male';
  if (pct < (male ? 11 : 16)) return 'lean';
  if (pct < (male ? 18 : 24)) return 'fit';
  if (pct < (male ? 25 : 32)) return 'average';
  return 'higher';
}

/**
 * Deterministic body-fat estimate from height/weight/age/sex (Deurenberg). Always available,
 * zero-cost — used as the fallback whenever photo AI is unavailable or can't read the image.
 */
function formulaAssessment(
  bfPct: number,
  sex: string,
  fromPhotoAttempt: boolean,
): BodyAssessment {
  const low = Math.max(3, Math.round((bfPct - 3.5) * 10) / 10);
  const high = Math.round((bfPct + 3.5) * 10) / 10;
  return {
    available: true,
    bodyFatLow: low,
    bodyFatHigh: high,
    category: bfCategory(bfPct, sex),
    notes: fromPhotoAttempt
      ? 'Couldn’t read the photo clearly, so this is estimated from your height, weight, age and sex. For a photo-based read, try a well-lit, front-facing shot.'
      : 'Estimated from your height, weight, age and sex. Track the trend as your weight changes.',
    confidence: 'low',
    formulaEstimatePct: bfPct,
    source: 'formula',
    disclaimer: DISCLAIMER,
  };
}

/**
 * Rough body-fat estimate from a user photo via Gemini vision. The image is forwarded to
 * Gemini for this one request and never stored. Refuses for minors and body-image safety.
 */
export async function assessBodyFromPhoto(
  userId: string,
  imageBase64: string,
  mimeType: string,
): Promise<BodyAssessment> {
  const { profile, sensitive } = await requireCompleteProfile(userId);
  const age = ageFromDob(sensitive.dob);

  if (age < 18) {
    return {
      available: true,
      refused: true,
      reason: 'Body-fat photo analysis is disabled for under-18s. Focus on healthy habits, not a number.',
      disclaimer: DISCLAIMER,
    };
  }

  const snapshot = await prisma.calcResultSnapshot.findFirst({
    where: { userId },
    orderBy: { createdAt: 'desc' },
  });
  // Deterministic body-fat estimate (Deurenberg) — always computable, and the fallback when
  // photo AI is down. Prefer the persisted snapshot; else compute from current stats.
  const formulaEstimatePct =
    (snapshot?.result as { bodyFatEstimate?: number } | undefined)?.bodyFatEstimate ??
    bodyFatDeurenberg({
      bmi: bmiCalc(sensitive.currentWeightKg, profile.heightCm),
      ageYears: age,
      sex: sensitive.sex,
    });

  // No photo AI configured / quota exhausted → still give a real number from the formula.
  if (!visionAvailable()) {
    return formulaAssessment(formulaEstimatePct, sensitive.sex, false);
  }

  const prompt = [
    'You are a supportive fitness assistant giving a rough, educational body-composition estimate from a photo.',
    `Context: ${age}-year-old ${sensitive.sex}, height ${profile.heightCm} cm, weight ${sensitive.currentWeightKg} kg.`,
    formulaEstimatePct != null ? `A formula (BMI-based) estimate is about ${formulaEstimatePct}% body fat.` : '',
    'Give a VISUAL body-fat estimate as a RANGE. Be kind and factual; never comment on attractiveness.',
    'Respond ONLY as JSON with keys: bodyFatLow (number), bodyFatHigh (number), category (one of "lean","fit","average","higher"), notes (1-2 short sentences on visible muscle definition and where fat is stored), confidence ("low" or "medium").',
    'If no clear human body is visible, return bodyFatLow 0, bodyFatHigh 0, notes "No clear body photo detected — try a well-lit, front-facing photo.".',
  ]
    .filter(Boolean)
    .join(' ');

  const result = await geminiVisionJson(imageBase64, mimeType, prompt);
  // Vision failed (quota/error) or couldn't see a body → fall back to the formula estimate
  // so the user still gets a useful number instead of a dead-end error.
  if (!result) {
    return formulaAssessment(formulaEstimatePct, sensitive.sex, true);
  }

  const low = num(result.bodyFatLow);
  const high = num(result.bodyFatHigh);
  if (low === 0 && high === 0) {
    return formulaAssessment(formulaEstimatePct, sensitive.sex, true);
  }

  return {
    available: true,
    bodyFatLow: low,
    bodyFatHigh: high,
    category: str(result.category),
    notes: str(result.notes),
    confidence: str(result.confidence) || 'low',
    formulaEstimatePct,
    source: 'ai',
    disclaimer: DISCLAIMER,
  };
}

const num = (v: unknown): number => (typeof v === 'number' && isFinite(v) ? Math.round(v * 10) / 10 : 0);
const str = (v: unknown): string => (typeof v === 'string' ? v : '');

// ======================================================================================
// Physique time-series: measurements, Navy body-fat trend, waist-hip, progress photos.
// ======================================================================================

const MEASUREMENT_KEYS = ['weightKg', 'waistCm', 'hipCm', 'chestCm', 'armCm', 'thighCm', 'neckCm', 'bodyFatPct'] as const;
type MeasurementKey = (typeof MEASUREMENT_KEYS)[number];
type Measurements = Partial<Record<MeasurementKey, number>>;

export interface BodyMetricInput extends Measurements {
  measuredAt?: string;
}
export interface BodyPoint extends Measurements {
  id: string;
  measuredAt: string;
}

interface ProfileBio {
  sex?: Sex;
  heightCm: number;
  isMinor: boolean;
}

async function bio(userId: string): Promise<ProfileBio> {
  const profile = await prisma.profile.findUnique({ where: { userId }, select: { sensitiveEnc: true, heightCm: true } });
  if (!profile?.sensitiveEnc) return { heightCm: profile?.heightCm ?? 0, isMinor: false };
  try {
    const s = decryptJson<SensitiveData>(profile.sensitiveEnc);
    return { sex: s.sex, heightCm: profile.heightCm, isMinor: ageFromDob(s.dob) < 18 };
  } catch {
    return { heightCm: profile.heightCm, isMinor: false };
  }
}

const BODY_DISCLAIMER =
  'Body-fat % is a tape-measure ESTIMATE (US-Navy method, ±3–4%), not a lab measurement. Track the trend, not the exact number, and be kind to yourself.';

/** Store a measurement snapshot; derive Navy body-fat for adults with neck+waist(+hip). */
export async function logBodyMetric(userId: string, input: BodyMetricInput): Promise<{ point: BodyPoint; whr: WhrResult | null }> {
  const { sex, heightCm, isMinor } = await bio(userId);
  const m: Measurements = {};
  for (const k of MEASUREMENT_KEYS) {
    const v = input[k];
    if (typeof v === 'number' && isFinite(v) && v > 0) m[k] = v;
  }
  if (m.bodyFatPct == null && sex && !isMinor && heightCm > 0 && m.waistCm && m.neckCm) {
    const bf = navyBodyFatPct({ sex, heightCm, neckCm: m.neckCm, waistCm: m.waistCm, hipCm: m.hipCm });
    if (bf != null) m.bodyFatPct = bf;
  }
  if (isMinor) delete m.bodyFatPct; // never a body-fat number for minors

  const row = await prisma.bodyMetric.create({
    data: { userId, measuredAt: input.measuredAt ? new Date(input.measuredAt) : new Date(), measurementsEnc: encryptJson(m) },
  });
  const whr = m.waistCm && m.hipCm && sex ? waistHipRatio(sex, m.waistCm, m.hipCm) : null;
  return { point: { id: row.id, measuredAt: row.measuredAt.toISOString(), ...m }, whr };
}

export interface BodySeries {
  points: BodyPoint[];
  trends: { key: MeasurementKey; first: number; last: number; delta: number }[];
  latestBodyFat: { pct: number; band: string } | null;
  whr: WhrResult | null;
  isMinor: boolean;
  disclaimer: string;
}

export async function getBodyMetrics(userId: string, rangeDays = 365): Promise<BodySeries> {
  const since = new Date(Date.now() - rangeDays * 86_400_000);
  const rows = await prisma.bodyMetric.findMany({ where: { userId, measuredAt: { gte: since } }, orderBy: { measuredAt: 'asc' } });
  const { sex, isMinor } = await bio(userId);
  const points: BodyPoint[] = rows.map((r) => {
    const m = safeMeasure(r.measurementsEnc);
    if (isMinor) delete m.bodyFatPct;
    return { id: r.id, measuredAt: r.measuredAt.toISOString(), ...m };
  });

  const trends: BodySeries['trends'] = [];
  for (const key of MEASUREMENT_KEYS) {
    const series = points.map((p) => p[key]).filter((v): v is number => typeof v === 'number');
    if (series.length >= 2) {
      const first = series[0]!;
      const last = series[series.length - 1]!;
      trends.push({ key, first, last, delta: Math.round((last - first) * 10) / 10 });
    }
  }

  const last = points[points.length - 1];
  const latestBodyFat = last?.bodyFatPct != null && sex ? { pct: last.bodyFatPct, band: bodyFatBand(sex, last.bodyFatPct) } : null;
  const whr = last?.waistCm && last?.hipCm && sex ? waistHipRatio(sex, last.waistCm, last.hipCm) : null;
  return { points, trends, latestBodyFat, whr, isMinor, disclaimer: BODY_DISCLAIMER };
}

function safeMeasure(payload: string): Measurements {
  try {
    return decryptJson<Measurements>(payload);
  } catch {
    return {};
  }
}

// ---- Progress photos (metadata only; the image stays on-device) ----

export async function addPhoto(userId: string, localRef: string, caption?: string) {
  const row = await prisma.bodyPhoto.create({
    data: { userId, takenAt: new Date(), localRef, captionEnc: caption ? encryptJson({ caption }) : null },
  });
  return { id: row.id, takenAt: row.takenAt.toISOString(), localRef, caption: caption ?? null };
}

export async function listPhotos(userId: string) {
  const rows = await prisma.bodyPhoto.findMany({ where: { userId }, orderBy: { takenAt: 'asc' } });
  return rows.map((r) => ({
    id: r.id,
    takenAt: r.takenAt.toISOString(),
    localRef: r.localRef,
    caption: r.captionEnc ? decryptJson<{ caption: string }>(r.captionEnc).caption : null,
  }));
}

export async function deletePhoto(userId: string, id: string) {
  const existing = await prisma.bodyPhoto.findFirst({ where: { id, userId } });
  if (!existing) throw new HttpError(404, 'Photo not found');
  await prisma.bodyPhoto.delete({ where: { id } });
}
