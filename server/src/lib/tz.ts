import type { Request } from 'express';

/**
 * Timezone handling. The Android client sends its UTC offset (in minutes, e.g. +330 for
 * IST) via the `X-TZ-Offset` header. We use it so "today", the Sun→Sat week, and day
 * boundaries match the user's phone clock instead of UTC.
 */
export function tzOffsetMin(req: Request): number {
  const h = req.header('x-tz-offset');
  const n = h ? parseInt(h, 10) : 0;
  if (!Number.isFinite(n)) return 0;
  return Math.max(-14 * 60, Math.min(14 * 60, n));
}

/**
 * The eating day starts at 04:00, not midnight: a late-night meal at 1 am still belongs to the day that just ended, and the
 * new day begins at 4 am. Every "today" (logs, water, plans, dashboard) uses this same boundary.
 */
export const DAY_START_HOUR = 4;

/** Local wall-clock (shifted back by DAY_START_HOUR) as a Date whose UTC getters read the user's local Y/M/D/H. */
function localWall(offsetMin: number, at: number): Date {
  return new Date(at + offsetMin * 60_000 - DAY_START_HOUR * 3_600_000);
}

/** 'YYYY-MM-DD' for the user's local day. */
export function localDayKey(offsetMin: number, at = Date.now()): string {
  return localWall(offsetMin, at).toISOString().slice(0, 10);
}

/** A Date whose UTC Y/M/D equals the user's local date today (midnight UTC of that date). */
export function localToday(offsetMin: number, at = Date.now()): Date {
  const w = localWall(offsetMin, at);
  return new Date(Date.UTC(w.getUTCFullYear(), w.getUTCMonth(), w.getUTCDate()));
}

/** A Date whose UTC Y/M/D equals the most recent local Sunday (start of the local week). */
export function localSunday(offsetMin: number, at = Date.now()): Date {
  const w = localWall(offsetMin, at);
  return new Date(Date.UTC(w.getUTCFullYear(), w.getUTCMonth(), w.getUTCDate() - w.getUTCDay()));
}

/** The UTC instants [start, end) bounding the user's local calendar day. */
export function localDayRange(offsetMin: number, at = Date.now()): { start: Date; end: Date } {
  const w = localWall(offsetMin, at);
  const startMs = Date.UTC(w.getUTCFullYear(), w.getUTCMonth(), w.getUTCDate()) + DAY_START_HOUR * 3_600_000 - offsetMin * 60_000;
  return { start: new Date(startMs), end: new Date(startMs + 86_400_000) };
}
