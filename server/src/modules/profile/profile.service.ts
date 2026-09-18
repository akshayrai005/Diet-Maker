import { invalidateCalcCache } from '../nutrition/calc.service';
import { prisma } from '../../lib/prisma';
import { decryptJson, encryptJson } from '../../lib/crypto';
import { HttpError } from '../../middleware/error';
import type { ProfileUpsertBody, SensitiveData } from './profile.schemas';

async function audit(userId: string, action: string, detail?: string) {
  await prisma.auditLog.create({ data: { userId, action, detail: detail ?? null } });
}

export async function upsertProfile(userId: string, body: ProfileUpsertBody) {
  invalidateCalcCache(userId);
  const { sensitive, ...rest } = body;
  const sensitiveEnc = encryptJson(sensitive);
  const data = { ...rest, sensitiveEnc };

  const profile = await prisma.profile.upsert({
    where: { userId },
    create: { userId, ...data },
    update: data,
  });
  await audit(userId, 'profile.upsert', 'health data written');
  return decorate(profile.id, rest, sensitive);
}

export async function getProfile(userId: string) {
  const profile = await prisma.profile.findUnique({ where: { userId } });
  if (!profile) return null;
  await audit(userId, 'profile.read', 'health data accessed');
  const sensitive = profile.sensitiveEnc
    ? decryptJson<SensitiveData>(profile.sensitiveEnc)
    : null;
  return decorate(profile.id, profile, sensitive);
}

/** Loads a profile and returns the decrypted sensitive data, or 400 if incomplete. */
export async function requireCompleteProfile(userId: string) {
  const profile = await prisma.profile.findUnique({ where: { userId } });
  if (!profile || !profile.sensitiveEnc) {
    throw new HttpError(400, 'Complete your health profile first');
  }
  const sensitive = decryptJson<SensitiveData>(profile.sensitiveEnc);
  return { profile, sensitive };
}

function decorate(
  id: string,
  base: { heightCm: number; activityLevel: string; goal: string; dietType: string; reducedMobility: boolean },
  sensitive: SensitiveData | null,
) {
  return {
    id,
    heightCm: base.heightCm,
    activityLevel: base.activityLevel,
    goal: base.goal,
    dietType: base.dietType,
    reducedMobility: base.reducedMobility,
    sensitive,
  };
}
