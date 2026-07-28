import { prisma } from '../../lib/prisma';

/**
 * Food-frequency & habit detection — "you've logged samosa 14 times". PURE aggregation over food
 * logs (grouped by normalized name / foodId); the DB wrappers just fetch rows. The coach NEVER asks
 * the LLM to count — these exact numbers are computed here. Educational, deterministic.
 */

export interface FoodLogRow {
  foodId: string | null;
  foodName: string;
  grams: number;
  kcal: number;
  tags?: string[];
  loggedAt: Date;
}

export interface FoodFrequencyItem {
  foodId: string | null;
  name: string;
  times: number;
  totalGrams: number;
  totalKcal: number;
  firstAt: string; // ISO
  lastAt: string; // ISO
  perWeekAvg: number;
  /** True if the food carries an unhealthy tag (refined/fried/high-sugar/…) — a "habit to watch". */
  unhealthy: boolean;
}

const UNHEALTHY_TAGS = ['refined', 'fried', 'high-sugar', 'high-satfat', 'high-sodium', 'high-gi'];
const DAY_MS = 86_400_000;
const norm = (s: string) => s.toLowerCase().trim().replace(/\s+/g, ' ');

/** Pure: rank every distinct food ever logged by how often it appears. */
export function aggregateFoodFrequency(rows: FoodLogRow[], now: Date): FoodFrequencyItem[] {
  const byKey = new Map<string, { name: string; foodId: string | null; times: number; grams: number; kcal: number; first: number; last: number; unhealthy: boolean }>();
  for (const r of rows) {
    const key = r.foodId ?? norm(r.foodName);
    const t = r.loggedAt.getTime();
    const cur = byKey.get(key);
    const unhealthy = (r.tags ?? []).some((tag) => UNHEALTHY_TAGS.includes(tag));
    if (!cur) {
      byKey.set(key, { name: r.foodName, foodId: r.foodId, times: 1, grams: r.grams, kcal: r.kcal, first: t, last: t, unhealthy });
    } else {
      cur.times += 1;
      cur.grams += r.grams;
      cur.kcal += r.kcal;
      cur.first = Math.min(cur.first, t);
      cur.last = Math.max(cur.last, t);
      cur.unhealthy = cur.unhealthy || unhealthy;
    }
  }
  const out: FoodFrequencyItem[] = [];
  for (const v of byKey.values()) {
    const weeks = Math.max(1, (now.getTime() - v.first) / (7 * DAY_MS));
    out.push({
      foodId: v.foodId,
      name: v.name,
      times: v.times,
      totalGrams: Math.round(v.grams),
      totalKcal: Math.round(v.kcal),
      firstAt: new Date(v.first).toISOString(),
      lastAt: new Date(v.last).toISOString(),
      perWeekAvg: Math.round((v.times / weeks) * 10) / 10,
      unhealthy: v.unhealthy,
    });
  }
  out.sort((a, b) => b.times - a.times);
  return out;
}

async function fetchLogs(userId: string, since?: Date): Promise<FoodLogRow[]> {
  const rows = await prisma.foodLog.findMany({
    where: { userId, ...(since ? { loggedAt: { gte: since } } : {}) },
    select: { foodId: true, foodName: true, grams: true, kcal: true, loggedAt: true },
    orderBy: { loggedAt: 'asc' },
  });
  // Attach the catalog food's tags (for the "habit to watch" flag) where we have a foodId.
  const ids = [...new Set(rows.map((r) => r.foodId).filter((x): x is string => !!x))];
  const tagById = new Map<string, string[]>();
  if (ids.length) {
    const foods = await prisma.food.findMany({ where: { id: { in: ids } }, select: { id: true, tags: true } });
    for (const f of foods) tagById.set(f.id, f.tags);
  }
  return rows.map((r) => ({ ...r, tags: r.foodId ? tagById.get(r.foodId) ?? [] : [] }));
}

/** All-time (or since `opts.since`) food frequency, ranked, optionally limited. */
export async function foodFrequency(userId: string, opts?: { since?: Date; limit?: number; now?: Date }): Promise<FoodFrequencyItem[]> {
  const rows = await fetchLogs(userId, opts?.since);
  const ranked = aggregateFoodFrequency(rows, opts?.now ?? new Date());
  return opts?.limit ? ranked.slice(0, opts.limit) : ranked;
}

export interface FoodCountResult {
  found: boolean;
  name?: string;
  times: number;
  totalGrams?: number;
  totalKcal?: number;
  firstAt?: string;
  lastAt?: string;
  perWeekAvg?: number;
  unhealthy?: boolean;
}

/** Resolve a fuzzy food name to its exact lifetime count + dates. `found:false` if never logged. */
export async function foodCount(userId: string, query: string, now: Date = new Date()): Promise<FoodCountResult> {
  const q = norm(query);
  if (!q) return { found: false, times: 0 };
  const all = await foodFrequency(userId, { now });
  // Exact-ish match first, then substring either way.
  const hit =
    all.find((f) => norm(f.name) === q) ??
    all.find((f) => norm(f.name).includes(q)) ??
    all.find((f) => q.includes(norm(f.name)));
  if (!hit) return { found: false, times: 0 };
  return {
    found: true,
    name: hit.name,
    times: hit.times,
    totalGrams: hit.totalGrams,
    totalKcal: hit.totalKcal,
    firstAt: hit.firstAt,
    lastAt: hit.lastAt,
    perWeekAvg: hit.perWeekAvg,
    unhealthy: hit.unhealthy,
  };
}
