import { prisma } from '../../lib/prisma';
import { decryptJson } from '../../lib/crypto';
import { HttpError } from '../../middleware/error';
import { DELETE_GRACE_DAYS } from './grace';

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
 * Soft-deletes the account: marks `deletedAt` and revokes sessions. The data is retained through a
 * grace window (see grace.ts) during which logging back in restores the account; a later purge job
 * hard-deletes accounts past the window (cascades via schema onDelete).
 */
export async function deleteAccount(userId: string): Promise<void> {
  await prisma.auditLog.create({ data: { userId, action: 'account.delete_requested' } }).catch(() => undefined);
  await prisma.$transaction([
    prisma.user.update({ where: { id: userId }, data: { deletedAt: new Date() } }),
    prisma.refreshToken.deleteMany({ where: { userId } }),
  ]);
}

/** Hard-deletes accounts whose grace window has elapsed. Intended for a scheduled purge job. */
export async function purgeExpiredAccounts(now: Date = new Date()): Promise<number> {
  const cutoff = new Date(now.getTime() - DELETE_GRACE_DAYS * 86_400_000);
  const stale = await prisma.user.findMany({
    where: { deletedAt: { not: null, lt: cutoff } },
    select: { id: true },
  });
  for (const u of stale) {
    await prisma.user.delete({ where: { id: u.id } }).catch(() => undefined);
  }
  return stale.length;
}
