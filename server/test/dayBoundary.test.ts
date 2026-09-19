import { describe, it, expect } from 'vitest';
import { dayRange } from '../src/modules/logging/logging.service';

const IST = 330;
describe("a user's day follows their own clock, not UTC", () => {
  it('IST: an entry at 00:41 IST (19:11Z the day before) belongs to the NEW local day', () => {
    const now = new Date('2026-09-19T20:57:00Z'); // 02:27 IST on 20 Sep
    const { start, end } = dayRange(now, IST);
    const inToday = (iso: string) => { const t = new Date(iso).getTime(); return t >= start.getTime() && t < end.getTime(); };
    expect(inToday('2026-09-19T19:11:00Z'), '00:41 IST 20 Sep').toBe(true);
    expect(inToday('2026-09-19T20:37:00Z'), '02:07 IST 20 Sep').toBe(true);
    expect(inToday('2026-09-19T12:41:00Z'), '18:11 IST 19 Sep is yesterday').toBe(false);
    expect(inToday('2026-09-19T05:30:00Z'), '11:00 IST 19 Sep is yesterday').toBe(false);
  });
  it('a UTC day (offset 0) would have wrongly mixed both days - the bug this guards against', () => {
    const now = new Date('2026-09-19T20:57:00Z');
    const { start, end } = dayRange(now, 0);
    const t = new Date('2026-09-19T05:30:00Z').getTime();
    expect(t >= start.getTime() && t < end.getTime()).toBe(true);
  });
  it('the local day is exactly 24 h and starts at local midnight', () => {
    const { start, end } = dayRange(new Date('2026-09-19T20:57:00Z'), IST);
    expect(end.getTime() - start.getTime()).toBe(86_400_000);
    expect(start.toISOString()).toBe('2026-09-19T18:30:00.000Z');
  });
});
