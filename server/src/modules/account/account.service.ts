import { prisma } from '../../lib/prisma';
import { decryptJson } from '../../lib/crypto';
import { HttpError } from '../../middleware/error';

/**
 * Full data export for the authenticated user (GDPR "download my data"). Sensitive fields
 * are decrypted so the user receives their own data in the clear.
 */
export async function exportUserData(userId: string) {
  const user = await prisma.user.findUnique({
    where: { id: userId },
    include: {
      profile: true,
      calcResults: true,
      dietPlans: true,
      foodLogs: true,
      waterLogs: true,
      checkins: true,
    },
  });
  if (!user) throw new HttpError(404, 'User not found');

  const { passwordHash, profile, checkins, ...rest } = user;
  void passwordHash; // never exported

  const decProfile = profile
    ? {
        ...profile,
        sensitiveEnc: undefined,
        sensitive: profile.sensitiveEnc ? safeDecrypt(profile.sensitiveEnc) : null,
      }
    : null;

  const decCheckins = checkins.map((c) => ({
    ...c,
    measurementsEnc: undefined,
    measurements: c.measurementsEnc ? safeDecrypt(c.measurementsEnc) : null,
  }));

  await prisma.auditLog.create({ data: { userId, action: 'account.export' } });

  return { ...rest, profile: decProfile, checkins: decCheckins };
}

function safeDecrypt(payload: string): unknown {
  try {
    return decryptJson(payload);
  } catch {
    return null;
  }
}

/**
 * Permanently deletes the account and all its data immediately (cascades via schema onDelete).
 * No grace window or restore - deletion is final, as requested.
 */
export async function deleteAccount(userId: string): Promise<void> {
  await prisma.auditLog.create({ data: { userId, action: 'account.delete_requested' } }).catch(() => undefined);
  await prisma.user.delete({ where: { id: userId } });
}
