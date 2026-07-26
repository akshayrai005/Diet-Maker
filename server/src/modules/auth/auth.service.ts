import { prisma } from '../../lib/prisma';
import { randomToken, sha256, decryptJson } from '../../lib/crypto';
import { HttpError } from '../../middleware/error';
import { withinGrace } from '../account/grace';
import { hashPassword, verifyPassword } from './password';
import type { SensitiveData } from '../profile/profile.schemas';
import {
  ACCESS_TTL_SECONDS,
  refreshExpiryDate,
  signAccessToken,
} from './tokens';
import type { User } from '@prisma/client';

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export interface RegisterInput {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
}

function publicUser(u: User) {
  return {
    id: u.id,
    email: u.email,
    firstName: u.firstName,
    lastName: u.lastName,
    role: u.role,
  };
}

async function issueTokens(user: User): Promise<AuthTokens> {
  const accessToken = signAccessToken({ sub: user.id, role: user.role });
  const refreshToken = randomToken();
  await prisma.refreshToken.create({
    data: {
      userId: user.id,
      tokenHash: sha256(refreshToken),
      expiresAt: refreshExpiryDate(),
    },
  });
  return { accessToken, refreshToken, expiresIn: ACCESS_TTL_SECONDS };
}

export async function register(input: RegisterInput) {
  const email = input.email.toLowerCase().trim();
  const existing = await prisma.user.findUnique({ where: { email } });
  if (existing) {
    if (existing.deletedAt) {
      // The email belongs to a SELF-deleted account still in its grace window. Signing up again with
      // it is an explicit "I want a fresh account" — so hard-delete the old one (cascades all its
      // data) and continue, instead of blocking the user with their own deletion.
      await prisma.user.delete({ where: { id: existing.id } });
    } else {
      throw new HttpError(409, 'An account with this email already exists');
    }
  }

  const user = await prisma.user.create({
    data: {
      email,
      passwordHash: await hashPassword(input.password),
      firstName: input.firstName.trim(),
      lastName: input.lastName.trim(),
    },
  });
  await prisma.auditLog.create({
    data: { userId: user.id, action: 'auth.register' },
  });
  return { user: publicUser(user), tokens: await issueTokens(user) };
}

export async function login(email: string, password: string) {
  const user = await prisma.user.findUnique({ where: { email: email.toLowerCase().trim() } });
  // Verify even when the user is missing to reduce timing/enumeration signal.
  const ok = user
    ? await verifyPassword(user.passwordHash, password)
    : await verifyPassword('$argon2id$v=19$m=19456,t=2,p=1$AAAAAAAAAAA$AAAAAAAAAAAAAAAAAAAAAA', password);
  if (!user || !ok) throw new HttpError(401, 'Invalid email or password');

  // Soft-deleted accounts: a successful login inside the grace window restores the account;
  // past the window it's treated as gone (no credential-leaking distinction).
  if (user.deletedAt) {
    if (!withinGrace(user.deletedAt, new Date())) throw new HttpError(401, 'Invalid email or password');
    await prisma.user.update({ where: { id: user.id }, data: { deletedAt: null } });
    await prisma.auditLog.create({ data: { userId: user.id, action: 'account.restored' } });
  }

  await prisma.auditLog.create({ data: { userId: user.id, action: 'auth.login' } });
  return { user: publicUser(user), tokens: await issueTokens(user) };
}

/** Rotates the refresh token: the presented one is revoked and a new pair is issued. */
/** The user's date of birth from their encrypted profile (the reliable copy), 'YYYY-MM-DD'. */
async function storedDob(userId: string): Promise<string | null> {
  const profile = await prisma.profile.findUnique({ where: { userId } });
  if (!profile?.sensitiveEnc) return null;
  try {
    return decryptJson<SensitiveData>(profile.sensitiveEnc).dob;
  } catch {
    return null;
  }
}

/** Checks the account identity for a password reset: email exists AND date of birth matches. */
export async function verifyIdentity(email: string, dob: string): Promise<boolean> {
  const user = await prisma.user.findUnique({ where: { email: email.toLowerCase().trim() } });
  if (!user) return false;
  const d = await storedDob(user.id);
  return d != null && d === dob;
}

/**
 * Self-service password reset gated by email + date of birth. Re-verifies server-side (never
 * trusts a client "verified" flag), sets the new password, and revokes every existing session
 * for safety. Note: knowledge-based (email + DOB) verification is convenient but weaker than an
 * emailed one-time link — swap to that once an email provider is configured.
 */
export async function resetPassword(email: string, dob: string, newPassword: string): Promise<void> {
  const user = await prisma.user.findUnique({ where: { email: email.toLowerCase().trim() } });
  const ok = user ? (await storedDob(user.id)) === dob : false;
  if (!user || !ok) {
    throw new HttpError(400, "We couldn't verify your details. Check your email and date of birth.");
  }
  await prisma.user.update({
    where: { id: user.id },
    data: { passwordHash: await hashPassword(newPassword) },
  });
  await prisma.refreshToken.deleteMany({ where: { userId: user.id } }); // log out everywhere
  await prisma.auditLog.create({ data: { userId: user.id, action: 'auth.reset_password' } });
}

export async function refresh(rawToken: string): Promise<AuthTokens> {
  const tokenHash = sha256(rawToken);
  const record = await prisma.refreshToken.findUnique({
    where: { tokenHash },
    include: { user: true },
  });
  if (!record || record.revokedAt || record.expiresAt < new Date()) {
    throw new HttpError(401, 'Invalid or expired refresh token');
  }
  await prisma.refreshToken.update({
    where: { id: record.id },
    data: { revokedAt: new Date() },
  });
  return issueTokens(record.user);
}

export async function logout(rawToken: string): Promise<void> {
  const tokenHash = sha256(rawToken);
  await prisma.refreshToken.updateMany({
    where: { tokenHash, revokedAt: null },
    data: { revokedAt: new Date() },
  });
}

export async function getMe(userId: string) {
  const user = await prisma.user.findUnique({ where: { id: userId } });
  if (!user) throw new HttpError(404, 'User not found');
  return publicUser(user);
}
