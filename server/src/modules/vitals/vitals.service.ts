import { prisma } from '../../lib/prisma';
import { encryptJson, decryptJson } from '../../lib/crypto';
import type { Sex } from '../../calc/types';
import type { SensitiveData } from '../profile/profile.schemas';
import {
  type VitalType,
  type VitalReading,
  type VitalClassification,
  type VitalTrend,
  classifyVital,
  computeTrend,
  primaryValue,
  VITAL_UNIT,
  VITAL_LABEL,
  VITAL_DISCLAIMER,
} from './vitals';

/** Encrypted blob shape stored in VitalLog.valueEnc. */
interface StoredReading extends VitalReading {
  note?: string;
}

export interface VitalPoint {
  id: string;
  measuredAt: string; // ISO
  value?: number;
  systolic?: number;
  diastolic?: number;
  note?: string;
}

export interface VitalSeries {
  type: VitalType;
  label: string;
  unit: string;
  points: VitalPoint[];
  trend: VitalTrend;
  latest: (VitalClassification & { point: VitalPoint }) | null;
  disclaimer: string;
}

/** Reads the user's biological sex for sex-specific bands — undefined if profile incomplete. */
async function readSex(userId: string): Promise<Sex | undefined> {
  const profile = await prisma.profile.findUnique({ where: { userId }, select: { sensitiveEnc: true } });
  if (!profile?.sensitiveEnc) return undefined;
  try {
    return decryptJson<SensitiveData>(profile.sensitiveEnc).sex;
  } catch {
    return undefined;
  }
}

export interface LogVitalInput {
  type: VitalType;
  value?: number;
  systolic?: number;
  diastolic?: number;
  note?: string;
  measuredAt?: string; // ISO; defaults to now
}

export async function logVital(
  userId: string,
  input: LogVitalInput,
): Promise<{ point: VitalPoint; classification: VitalClassification | null }> {
  const reading: StoredReading = {};
  if (input.type === 'bloodPressure') {
    reading.systolic = input.systolic;
    reading.diastolic = input.diastolic;
  } else {
    reading.value = input.value;
  }
  if (input.note) reading.note = input.note.slice(0, 500);

  const measuredAt = input.measuredAt ? new Date(input.measuredAt) : new Date();
  const created = await prisma.vitalLog.create({
    data: {
      userId,
      type: input.type,
      unit: VITAL_UNIT[input.type],
      measuredAt,
      valueEnc: encryptJson(reading),
    },
  });

  const sex = await readSex(userId);
  const point: VitalPoint = {
    id: created.id,
    measuredAt: created.measuredAt.toISOString(),
    ...reading,
  };
  return { point, classification: classifyVital(input.type, reading, { sex }) };
}

/** Series (ascending by time) for one metric over the last `rangeDays`, with trend + latest band. */
export async function getVitalSeries(userId: string, type: VitalType, rangeDays = 180): Promise<VitalSeries> {
  const since = new Date(Date.now() - rangeDays * 86_400_000);
  const rows = await prisma.vitalLog.findMany({
    where: { userId, type, measuredAt: { gte: since } },
    orderBy: { measuredAt: 'asc' },
  });
  const sex = await readSex(userId);

  const points: VitalPoint[] = rows.map((r) => {
    const reading = safeDecrypt(r.valueEnc);
    return { id: r.id, measuredAt: r.measuredAt.toISOString(), ...reading };
  });

  const primaries = points
    .map((p) => primaryValue(type, p))
    .filter((n): n is number => n != null);
  const trend = computeTrend(primaries);

  let latest: VitalSeries['latest'] = null;
  const last = points[points.length - 1];
  if (last) {
    const cls = classifyVital(type, last, { sex });
    if (cls) latest = { ...cls, point: last };
  }

  return {
    type,
    label: VITAL_LABEL[type],
    unit: VITAL_UNIT[type],
    points,
    trend,
    latest,
    disclaimer: VITAL_DISCLAIMER,
  };
}

export interface VitalSummaryItem {
  type: VitalType;
  label: string;
  unit: string;
  latest: VitalPoint;
  classification: VitalClassification | null;
}

/** Latest reading + band per metric that has any data — for the Home/vitals overview. */
export async function getVitalsSummary(userId: string): Promise<{ items: VitalSummaryItem[]; disclaimer: string }> {
  const rows = await prisma.vitalLog.findMany({
    where: { userId },
    orderBy: { measuredAt: 'desc' },
  });
  const sex = await readSex(userId);
  const seen = new Set<string>();
  const items: VitalSummaryItem[] = [];
  for (const r of rows) {
    if (seen.has(r.type)) continue; // rows are desc, so first per type is the latest
    seen.add(r.type);
    if (!isKnownType(r.type)) continue;
    const type = r.type as VitalType;
    const reading = safeDecrypt(r.valueEnc);
    const latest: VitalPoint = { id: r.id, measuredAt: r.measuredAt.toISOString(), ...reading };
    items.push({ type, label: VITAL_LABEL[type], unit: VITAL_UNIT[type], latest, classification: classifyVital(type, reading, { sex }) });
  }
  return { items, disclaimer: VITAL_DISCLAIMER };
}

/** Latest lab/vital values fed into the risk engine (never throws — missing = undefined). */
export async function getLatestVitalsForRisk(userId: string): Promise<{
  bloodPressure?: string;
  restingHr?: number;
  fastingGlucose?: number;
  hba1c?: number;
  ldl?: number;
  hdl?: number;
  triglycerides?: number;
  tsh?: number;
}> {
  const rows = await prisma.vitalLog.findMany({
    where: { userId },
    orderBy: { measuredAt: 'desc' },
  });
  const seen = new Set<string>();
  const out: Record<string, unknown> = {};
  for (const r of rows) {
    if (seen.has(r.type)) continue;
    seen.add(r.type);
    const reading = safeDecrypt(r.valueEnc);
    switch (r.type) {
      case 'bloodPressure':
        if (reading.systolic && reading.diastolic) out.bloodPressure = `${reading.systolic}/${reading.diastolic}`;
        break;
      case 'restingHr':
        out.restingHr = reading.value;
        break;
      case 'fastingGlucose':
        out.fastingGlucose = reading.value;
        break;
      case 'hba1c':
        out.hba1c = reading.value;
        break;
      case 'ldl':
        out.ldl = reading.value;
        break;
      case 'hdl':
        out.hdl = reading.value;
        break;
      case 'triglycerides':
        out.triglycerides = reading.value;
        break;
      case 'tsh':
        out.tsh = reading.value;
        break;
    }
  }
  return out;
}

function isKnownType(t: string): boolean {
  return VITAL_LABEL[t as VitalType] != null;
}

function safeDecrypt(payload: string): StoredReading {
  try {
    return decryptJson<StoredReading>(payload);
  } catch {
    return {};
  }
}
