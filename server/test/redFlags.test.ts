import { describe, it, expect } from 'vitest';
import { checkRedFlags } from '../src/modules/safety/redFlags';

describe('checkRedFlags', () => {
  it('escalates chest pain to urgent, never reassures', () => {
    const r = checkRedFlags('I have crushing chest pain and pain radiating to arm');
    expect(r.urgent).toBe(true);
    expect(r.matched.some((m) => m.id === 'cardiac')).toBe(true);
    expect(r.message).toMatch(/emergency|emergency department|emergency number/i);
    expect(r.message).not.toMatch(/you.?re fine|probably nothing|don.?t worry/i);
  });

  it('detects stroke signs', () => {
    expect(checkRedFlags('sudden numbness and slurred speech').urgent).toBe(true);
  });

  it('detects diabetic emergencies', () => {
    expect(checkRedFlags('my blood sugar over 400 and vomiting and very thirsty').urgent).toBe(true);
  });

  it('routes self-harm to a compassionate crisis message', () => {
    const r = checkRedFlags('I want to die');
    expect(r.urgent).toBe(true);
    expect(r.matched.some((m) => m.id === 'suicidal')).toBe(true);
    expect(r.message).toMatch(/crisis|support|professional|trust/i);
  });

  it('does NOT fire on ordinary text (no false reassurance either)', () => {
    const r = checkRedFlags('I had a mild headache after lunch and feel a bit tired');
    expect(r.urgent).toBe(false);
    expect(r.matched).toHaveLength(0);
    expect(r.message).toBe('');
  });

  it('never diagnoses — only escalates', () => {
    const r = checkRedFlags('chest tightness');
    expect(r.message).not.toMatch(/you have|diagnos/i);
    expect(r.message).toMatch(/get.*medical help|emergency/i);
  });
});
