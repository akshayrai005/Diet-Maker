import {
  createCipheriv,
  createDecipheriv,
  createHash,
  randomBytes,
} from 'node:crypto';
import { env } from './env';

/**
 * AES-256-GCM encryption for sensitive health data at the application layer.
 *
 * KEY VERSIONING (for rotation without orphaning data):
 *  - Version 1 key: HEALTH_DATA_ENCRYPTION_KEY (base64, 32 bytes).
 *  - Version 2 key (optional): HEALTH_DATA_ENCRYPTION_KEY_V2 — set this to rotate; new writes use
 *    it while old ciphertext still decrypts with V1. Re-encrypt over time, then retire V1.
 *  New output format: v<N>.base64(iv).base64(authTag).base64(ciphertext)
 *  Legacy 3-part payloads (no version tag) are still read as version 1 — fully backward-compatible.
 */
const ALGO = 'aes-256-gcm';
const IV_BYTES = 12;

function keyFor(version: number): Buffer {
  const b64 = version === 2 ? process.env.HEALTH_DATA_ENCRYPTION_KEY_V2 : env.HEALTH_DATA_ENCRYPTION_KEY;
  if (!b64) throw new Error(`No encryption key configured for version ${version}`);
  const key = Buffer.from(b64, 'base64');
  if (key.length !== 32) {
    throw new Error('Encryption key must decode to 32 bytes (base64).');
  }
  return key;
}

/** Highest configured key version — new data is encrypted with this. */
const CURRENT_VERSION = process.env.HEALTH_DATA_ENCRYPTION_KEY_V2 ? 2 : 1;

export function encryptJson(value: unknown): string {
  const iv = randomBytes(IV_BYTES);
  const cipher = createCipheriv(ALGO, keyFor(CURRENT_VERSION), iv);
  const plaintext = Buffer.from(JSON.stringify(value), 'utf8');
  const ciphertext = Buffer.concat([cipher.update(plaintext), cipher.final()]);
  const tag = cipher.getAuthTag();
  return [
    `v${CURRENT_VERSION}`,
    iv.toString('base64'),
    tag.toString('base64'),
    ciphertext.toString('base64'),
  ].join('.');
}

export function decryptJson<T = unknown>(payload: string): T {
  const parts = payload.split('.');
  let version = 1;
  let ivB64: string;
  let tagB64: string;
  let dataB64: string;
  if (parts.length === 4 && parts[0]!.startsWith('v')) {
    version = parseInt(parts[0]!.slice(1), 10) || 1;
    [, ivB64, tagB64, dataB64] = parts as [string, string, string, string];
  } else if (parts.length === 3) {
    // Legacy format written before key-versioning — always version 1.
    [ivB64, tagB64, dataB64] = parts as [string, string, string];
  } else {
    throw new Error('Malformed ciphertext');
  }
  const decipher = createDecipheriv(ALGO, keyFor(version), Buffer.from(ivB64, 'base64'));
  decipher.setAuthTag(Buffer.from(tagB64, 'base64'));
  const plaintext = Buffer.concat([
    decipher.update(Buffer.from(dataB64, 'base64')),
    decipher.final(),
  ]);
  return JSON.parse(plaintext.toString('utf8')) as T;
}

/** SHA-256 hex — used to store refresh tokens without keeping the raw value. */
export function sha256(value: string): string {
  return createHash('sha256').update(value).digest('hex');
}

/** Cryptographically-random opaque token (URL-safe). */
export function randomToken(bytes = 48): string {
  return randomBytes(bytes).toString('base64url');
}
