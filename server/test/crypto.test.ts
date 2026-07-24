import { describe, it, expect } from 'vitest';
import { encryptJson, decryptJson, sha256, randomToken } from '../src/lib/crypto';

describe('crypto (AES-256-GCM)', () => {
  it('round-trips an object (versioned format)', () => {
    const data = { currentWeightKg: 82.5, conditions: ['diabetes'], bp: '120/80' };
    const enc = encryptJson(data);
    expect(enc).not.toContain('82.5'); // ciphertext, not plaintext
    expect(enc.split('.')).toHaveLength(4); // v<N>.iv.tag.ct
    expect(enc.startsWith('v1.')).toBe(true);
    expect(decryptJson(enc)).toEqual(data);
  });

  it('still decrypts legacy 3-part (pre-versioning) payloads', () => {
    const data = { a: 1, b: 'two' };
    const legacy = encryptJson(data).split('.').slice(1).join('.'); // strip the version tag
    expect(legacy.split('.')).toHaveLength(3);
    expect(decryptJson(legacy)).toEqual(data);
  });

  it('produces a different ciphertext each time (random IV)', () => {
    const a = encryptJson({ x: 1 });
    const b = encryptJson({ x: 1 });
    expect(a).not.toBe(b);
    expect(decryptJson(a)).toEqual(decryptJson(b));
  });

  it('fails to decrypt tampered ciphertext (auth tag)', () => {
    const enc = encryptJson({ secret: true });
    const [iv, tag, data] = enc.split('.');
    const tampered = [iv, tag, Buffer.from('garbage').toString('base64')].join('.');
    expect(() => decryptJson(tampered)).toThrow();
  });

  it('rejects malformed input', () => {
    expect(() => decryptJson('not-valid')).toThrow('Malformed ciphertext');
  });

  it('sha256 is stable and hex', () => {
    expect(sha256('abc')).toBe('ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
  });

  it('randomToken returns url-safe strings of differing values', () => {
    const a = randomToken();
    const b = randomToken();
    expect(a).not.toBe(b);
    expect(a).toMatch(/^[A-Za-z0-9_-]+$/);
  });
});
