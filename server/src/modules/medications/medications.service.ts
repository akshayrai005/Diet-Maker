import { prisma } from '../../lib/prisma';
import { encryptJson, decryptJson } from '../../lib/crypto';
import { HttpError } from '../../middleware/error';
import { localDayRange } from '../../lib/tz';
import {
  type MedicationType,
  normalizeTimes,
  MEDICATION_DISCLAIMER,
} from './medications';

interface MedDetails {
  name: string;
  dose?: string;
  times?: string[];
  notes?: string;
}

export interface MedicationView {
  id: string;
  type: MedicationType;
  active: boolean;
  name: string;
  dose?: string;
  times: string[];
  notes?: string;
  takenToday: number;
  createdAt: string;
}

export interface MedicationInput {
  type: MedicationType;
  name: string;
  dose?: string;
  times?: string[];
  notes?: string;
  active?: boolean;
}

function toDetails(input: MedicationInput): MedDetails {
  const details: MedDetails = { name: input.name.trim().slice(0, 120) };
  if (input.dose) details.dose = input.dose.trim().slice(0, 80);
  const times = normalizeTimes(input.times);
  if (times.length) details.times = times;
  if (input.notes) details.notes = input.notes.trim().slice(0, 500);
  return details;
}

export async function createMedication(userId: string, input: MedicationInput): Promise<MedicationView> {
  const details = toDetails(input);
  const row = await prisma.medication.create({
    data: {
      userId,
      type: input.type,
      active: input.active ?? true,
      detailsEnc: encryptJson(details),
    },
  });
  return { id: row.id, type: row.type as MedicationType, active: row.active, ...details, times: details.times ?? [], takenToday: 0, createdAt: row.createdAt.toISOString() };
}

export async function updateMedication(userId: string, id: string, input: MedicationInput): Promise<MedicationView> {
  const existing = await prisma.medication.findFirst({ where: { id, userId } });
  if (!existing) throw new HttpError(404, 'Medication not found');
  const details = toDetails(input);
  const row = await prisma.medication.update({
    where: { id },
    data: { type: input.type, active: input.active ?? existing.active, detailsEnc: encryptJson(details) },
  });
  return { id: row.id, type: row.type as MedicationType, active: row.active, ...details, times: details.times ?? [], takenToday: 0, createdAt: row.createdAt.toISOString() };
}

export async function setActive(userId: string, id: string, active: boolean): Promise<void> {
  const existing = await prisma.medication.findFirst({ where: { id, userId } });
  if (!existing) throw new HttpError(404, 'Medication not found');
  await prisma.medication.update({ where: { id }, data: { active } });
}

export async function deleteMedication(userId: string, id: string): Promise<void> {
  const existing = await prisma.medication.findFirst({ where: { id, userId } });
  if (!existing) throw new HttpError(404, 'Medication not found');
  await prisma.medication.delete({ where: { id } });
}

export async function logAdherence(userId: string, medicationId: string): Promise<void> {
  const existing = await prisma.medication.findFirst({ where: { id: medicationId, userId } });
  if (!existing) throw new HttpError(404, 'Medication not found');
  await prisma.medicationLog.create({ data: { userId, medicationId } });
}

export async function listMedications(
  userId: string,
  offsetMin = 0,
): Promise<{ medications: MedicationView[]; disclaimer: string }> {
  const rows = await prisma.medication.findMany({ where: { userId }, orderBy: { createdAt: 'asc' } });
  const { start, end } = localDayRange(offsetMin);
  const todayLogs = await prisma.medicationLog.groupBy({
    by: ['medicationId'],
    where: { userId, takenAt: { gte: start, lte: end } },
    _count: { _all: true },
  });
  const takenMap = new Map(todayLogs.map((l) => [l.medicationId, l._count._all]));

  const medications: MedicationView[] = rows.map((row) => {
    const details = safeDetails(row.detailsEnc);
    return {
      id: row.id,
      type: row.type as MedicationType,
      active: row.active,
      name: details.name,
      dose: details.dose,
      times: details.times ?? [],
      notes: details.notes,
      takenToday: takenMap.get(row.id) ?? 0,
      createdAt: row.createdAt.toISOString(),
    };
  });
  return { medications, disclaimer: MEDICATION_DISCLAIMER };
}

function safeDetails(payload: string): MedDetails {
  try {
    return decryptJson<MedDetails>(payload);
  } catch {
    return { name: '(unreadable)' };
  }
}
