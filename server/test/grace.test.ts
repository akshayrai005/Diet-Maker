import { describe, it, expect } from 'vitest';
import { withinGrace, DELETE_GRACE_DAYS } from '../src/modules/account/grace';

const now = new Date('2026-07-24T12:00:00Z');

describe('withinGrace', () => {
  it('treats a never-deleted account as within grace', () => {
    expect(withinGrace(null, now)).toBe(true);
  });

  it('is true just inside the window', () => {
    const deletedAt = new Date(now.getTime() - (DELETE_GRACE_DAYS * 86_400_000 - 1000));
    expect(withinGrace(deletedAt, now)).toBe(true);
  });

  it('is false once the window has passed', () => {
    const deletedAt = new Date(now.getTime() - (DELETE_GRACE_DAYS + 1) * 86_400_000);
    expect(withinGrace(deletedAt, now)).toBe(false);
  });
});
