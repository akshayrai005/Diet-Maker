import { describe, it, expect } from 'vitest';
import { dayRange } from '../src/modules/logging/logging.service';

const IST = 330;
describe("a user's day follows their own clock, not UTC", () => {
  it('IST: an entry at 00:41 IST (19:11Z the day before) still belongs to the PREVIOUS eating day (day starts 04:00)', () => {
    const now = new Date('2026-09-19T20:57:00Z'); // 02:27 IST on 20 Sep -> still the 19 Sep eating day
    const { start, end } = dayRange(now, IST);
    const inToday = (iso: string) => { const t = new Date(iso).getTime(); return t >= start.getTime() && t < end.getTime(); };
    expect(inToday('2026-09-19T19:11:00Z'), '00:41 IST 20 Sep counts to 19 Sep').toBe(true);
    expect(inToday('2026-09-19T20:37:00Z'), '02:07 IST 20 Sep counts to 19 Sep').toBe(true);
    expect(inToday('2026-09-19T12:41:00Z'), '18:11 IST 19 Sep is in the 19 Sep day').toBe(true);
    expect(inToday('2026-09-18T22:30:00Z'), '04:00 IST 19 Sep starts the day').toBe(true);
    expect(inToday('2026-09-18T22:29:00Z'), '03:59 IST 19 Sep is the 18 Sep day').toBe(false);
    expect(inToday('2026-09-19T22:30:00Z'), '04:00 IST 20 Sep is the next day').toBe(false);
  });
  it('a UTC day (offset 0) would have wrongly mixed both days - the bug this guards against', () => {
    const now = new Date('2026-09-19T20:57:00Z');
    const { start, end } = dayRange(now, 0);
    const t = new Date('2026-09-19T05:30:00Z').getTime();
    expect(t >= start.getTime() && t < end.getTime()).toBe(true);
  });
  it('the local day is exactly 24 h and starts at 04:00 local', () => {
    const { start, end } = dayRange(new Date('2026-09-19T20:57:00Z'), IST);
    expect(end.getTime() - start.getTime()).toBe(86_400_000);
    expect(start.toISOString()).toBe('2026-09-18T22:30:00.000Z');
  });
});
