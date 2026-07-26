import { prisma } from '../../lib/prisma';
import { decryptJson } from '../../lib/crypto';
import type { SensitiveData } from '../profile/profile.schemas';

/**
 * The user's best-known current body weight in kg - most recent weekly check-in first, then the
 * onboarding profile weight. Returns null when nothing is known (callers fall back to a neutral
 * default). Reads only encrypted-at-rest fields and decrypts them here.
 */
export async function latestBodyWeightKg(userId: string): Promise<number | null> {
  const checkin = await prisma.weeklyCheckin.findFirst({
    where: { userId, measurementsEnc: { not: null } },
    orderBy: { date: 'desc' },
  });
  if (checkin?.measurementsEnc) {
    const m = decryptJson<{ weightKg?: number }>(checkin.measurementsEnc);
    if (m.weightKg && m.weightKg > 0) return m.weightKg;
  }

  const profile = await prisma.profile.findUnique({ where: { userId } });
  if (profile?.sensitiveEnc) {
    const s = decryptJson<SensitiveData>(profile.sensitiveEnc);
    if (s.currentWeightKg && s.currentWeightKg > 0) return s.currentWeightKg;
  }
  return null;
}
