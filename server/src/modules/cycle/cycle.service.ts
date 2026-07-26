import { prisma } from '../../lib/prisma';
import { HttpError } from '../../middleware/error';
import { encryptJson, decryptJson } from '../../lib/crypto';
import { requireCompleteProfile } from '../profile/profile.service';
import { ageFromDob } from '../nutrition/calc.service';
import { computeCycle, cycleGuidance, analyzeCycleHealth } from './cycle';

const DAY_MS = 86_400_000;

/** Sensitive per-period detail - encrypted at rest; never used by the (date-only) cycle math. */
export interface PeriodDetails {
  symptoms?: string[];
  flow?: 'light' | 'medium' | 'heavy';
  mood?: number; // 1..5
  notes?: string;
}

/** Logs a period start (and optional end + encrypted symptom/flow/mood/notes). */
export async function logPeriod(userId: string, startDate: Date, endDate?: Date, details?: PeriodDetails) {
  const hasDetails = details && (details.symptoms?.length || details.flow || details.mood != null || details.notes);
  return prisma.periodLog.create({
    data: {
      userId,
      startDate,
      endDate: endDate ?? null,
      detailsEnc: hasDetails ? encryptJson(details) : null,
    },
  });
}

/** Marks the most recent still-open period as ended (defaults to now). */
export async function endLatestPeriod(userId: string, when: Date = new Date()) {
  const open = await prisma.periodLog.findFirst({
    where: { userId, endDate: null },
    orderBy: { startDate: 'desc' },
  });
  if (!open) throw new HttpError(400, 'No ongoing period to end - log a start first.');
  return prisma.periodLog.update({ where: { id: open.id }, data: { endDate: when } });
}

export async function listPeriods(userId: string) {
  return prisma.periodLog.findMany({
    where: { userId },
    orderBy: { startDate: 'desc' },
    take: 24,
  });
}

/** Median-ish average gap between recent period starts → the user's real cycle length. */
function estimateCycleLength(starts: Date[]): number {
  if (starts.length < 2) return 28;
  const gaps: number[] = [];
  for (let i = 0; i < starts.length - 1; i++) {
    const g = Math.round((starts[i]!.getTime() - starts[i + 1]!.getTime()) / DAY_MS);
    if (g >= 21 && g <= 45) gaps.push(g);
  }
  if (gaps.length === 0) return 28;
  const avg = gaps.reduce((a, b) => a + b, 0) / gaps.length;
  return Math.round(avg);
}

function estimatePeriodLength(rows: { startDate: Date; endDate: Date | null }[]): number {
  const lens: number[] = [];
  for (const r of rows) {
    if (r.endDate) {
      const d = Math.round((r.endDate.getTime() - r.startDate.getTime()) / DAY_MS) + 1;
      if (d >= 2 && d <= 10) lens.push(d);
    }
  }
  if (lens.length === 0) return 5;
  return Math.round(lens.reduce((a, b) => a + b, 0) / lens.length);
}

/** Full cycle state + phase guidance for the current user (female profiles only). */
export async function getCycle(userId: string, now: Date = new Date()) {
  const { sensitive } = await requireCompleteProfile(userId);
  if (sensitive.sex !== 'female') {
    return { applicable: false as const };
  }

  const rows = await listPeriods(userId);
  if (rows.length === 0) {
    return { applicable: true as const, needsSetup: true as const };
  }

  const starts = rows.map((r) => r.startDate);
  const cycleLengthDays = estimateCycleLength(starts);
  const periodLengthDays = estimatePeriodLength(rows);
  const lastStart = starts[0]!;
  const state = computeCycle(lastStart, now, cycleLengthDays, periodLengthDays);

  // Raw arrays for the health analysis.
  const gaps: number[] = [];
  for (let i = 0; i < starts.length - 1; i++) {
    const g = Math.round((starts[i]!.getTime() - starts[i + 1]!.getTime()) / DAY_MS);
    if (g >= 15 && g <= 60) gaps.push(g);
  }
  const periodLengths = rows
    .filter((r) => r.endDate)
    .map((r) => Math.round((r.endDate!.getTime() - r.startDate.getTime()) / DAY_MS) + 1)
    .filter((d) => d >= 1 && d <= 15);
  const daysSinceLastStart = Math.max(0, Math.round((now.getTime() - lastStart.getTime()) / DAY_MS));
  const periodOngoing = rows[0]!.endDate == null && state.cycleDay <= periodLengthDays + 2;

  const health = analyzeCycleHealth({
    cycleLengths: gaps,
    periodLengths,
    daysSinceLastStart,
    avgCycleLength: cycleLengthDays,
    ageYears: ageFromDob(sensitive.dob),
    contraception: sensitive.contraception,
    smoking: sensitive.smoking,
    alcohol: sensitive.alcohol,
  });

  return {
    applicable: true as const,
    needsSetup: false as const,
    lastPeriodStart: lastStart.toISOString().slice(0, 10),
    cycleLengthDays,
    periodLengthDays,
    periodOngoing,
    ...state,
    guidance: cycleGuidance(state.phase),
    health,
  };
}
