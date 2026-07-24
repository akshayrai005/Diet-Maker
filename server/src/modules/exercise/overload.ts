/**
 * Progressive-overload engine — PURE and deterministic.
 *
 * Given a flat history of logged sets, it recommends the next session per
 * exercise using capped, explainable rules (double-progression + a periodic
 * deload). There is NO database, no I/O, no `Date.now()`, no `Math.random()`
 * and no LLM here — the same inputs always yield the same output. `Date` is
 * used ONLY to parse the caller-supplied ISO date strings, which is a pure,
 * deterministic transformation of the input.
 *
 * The rules are intentionally conservative: at most ONE small increment per
 * session (never a big jump), and a scheduled ~10% deload every N weeks to
 * shed fatigue and resensitise for the next block.
 */

import type { FitnessLevel } from './exercise.types';

/**
 * One performed/logged set, aligned to the persisted `ExerciseLog` fields
 * (`exerciseName`, `weightKg`, `reps`, `sets`, `performedAt` → `date`).
 * Bodyweight movements carry `weightKg: null`.
 */
export type LoggedSet = {
  exerciseName: string;
  date: string; // ISO date or datetime, e.g. "2026-01-29" or "2026-01-29T10:00:00Z"
  weightKg: number | null;
  reps: number | null;
  sets: number | null;
};

/** A recommendation for the next session of a single exercise. */
export interface PerExercise {
  exerciseName: string;
  suggestedWeightKg: number | null;
  suggestedReps: number;
  suggestedSets: number;
  deload: boolean;
  rationale: string;
}

export interface RecommendOptions {
  /** Deload cadence in weeks (default 4). Values ≤ 0 fall back to the default. */
  deloadEveryWeeks?: number;
}

const WEIGHT_INCREMENT_KG = 2.5; // one small plate jump for weighted lifts
const REP_INCREMENT = 1; // one rep for bodyweight lifts
const DELOAD_FACTOR = 0.9; // ~-10% load & reps on a deload week
const DEFAULT_DELOAD_EVERY_WEEKS = 4;
const DEFAULT_REPS = 8; // conservative fallback when reps weren't logged
const DEFAULT_SETS = 3; // conservative fallback when sets weren't logged
const MS_PER_WEEK = 7 * 24 * 60 * 60 * 1000;

/** Nearest 0.5 kg — keeps suggestions on a realistic plate grid. */
function roundToHalf(x: number): number {
  return Math.round(x * 2) / 2;
}

/** Whole reps, never below 1. */
function clampReps(x: number): number {
  return Math.max(1, Math.round(x));
}

/** Deterministic parse of a caller-supplied ISO date string to epoch ms. */
function toMs(dateIso: string): number {
  const ms = new Date(dateIso).getTime();
  return Number.isFinite(ms) ? ms : 0;
}

function isBodyweightSet(s: LoggedSet): boolean {
  return s.weightKg === null || s.weightKg === undefined;
}

/** Human-readable summary of a session for rationale text. */
function describe(s: LoggedSet): string {
  const sets = s.sets ?? DEFAULT_SETS;
  const reps = s.reps ?? DEFAULT_REPS;
  return isBodyweightSet(s) ? `${sets}×${reps} bodyweight` : `${sets}×${reps} @ ${s.weightKg} kg`;
}

/**
 * Did the most recent session REGRESS vs the prior one (a "miss")?
 * A heavier top load with fewer reps is expected progress, not a miss; only
 * count it as a miss when reps or sets dropped at the same-or-lighter load.
 */
function didRegress(recent: LoggedSet, prev: LoggedSet): boolean {
  const recentW = recent.weightKg ?? 0;
  const prevW = prev.weightKg ?? 0;
  if (recentW > prevW) return false; // added load — treat as a progression attempt
  const recentReps = recent.reps ?? 0;
  const prevReps = prev.reps ?? 0;
  const recentSets = recent.sets ?? 0;
  const prevSets = prev.sets ?? 0;
  return recentReps < prevReps || recentSets < prevSets;
}

function recommendForExercise(
  name: string,
  sets: LoggedSet[],
  deloadEveryWeeks: number,
): PerExercise {
  // Oldest → newest (stable). The last element is the most recent session.
  const sorted = [...sets].sort((a, b) => toMs(a.date) - toMs(b.date));
  const recent = sorted[sorted.length - 1]!;
  const prev = sorted.length >= 2 ? sorted[sorted.length - 2]! : undefined;

  const bodyweight = isBodyweightSet(recent);
  const baseReps = recent.reps != null ? recent.reps : DEFAULT_REPS;
  const baseSets = recent.sets != null ? recent.sets : DEFAULT_SETS;
  const baseWeight = bodyweight ? null : recent.weightKg!;

  // How many whole weeks of training this exercise spans (first → most recent).
  const weeksElapsed = Math.floor((toMs(recent.date) - toMs(sorted[0]!.date)) / MS_PER_WEEK);
  const deloadDue = weeksElapsed >= deloadEveryWeeks && weeksElapsed % deloadEveryWeeks === 0;

  // 1) Scheduled deload — overrides progression/hold.
  if (deloadDue) {
    const reps = clampReps(baseReps * DELOAD_FACTOR);
    const weight = baseWeight === null ? null : roundToHalf(baseWeight * DELOAD_FACTOR);
    const target = weight === null ? `${reps} reps` : `${weight} kg × ${reps}`;
    return {
      exerciseName: name,
      suggestedWeightKg: weight,
      suggestedReps: reps,
      suggestedSets: baseSets,
      deload: true,
      rationale:
        `Scheduled deload (every ${deloadEveryWeeks} weeks; ${weeksElapsed} weeks in): ` +
        `easing ~10% to ${target} to shed accumulated fatigue and resensitise before the next block.`,
    };
  }

  // 2) Missed last session (regressed vs the prior) — hold the load.
  if (prev !== undefined && didRegress(recent, prev)) {
    return {
      exerciseName: name,
      suggestedWeightKg: baseWeight,
      suggestedReps: baseReps,
      suggestedSets: baseSets,
      deload: false,
      rationale:
        `Last session (${describe(recent)}) fell short of the prior (${describe(prev)}). ` +
        `Holding at ${describe(recent)} — nail every rep before adding load.`,
    };
  }

  // 3) Hit all reps/sets (or a first single session) — ONE small increment.
  if (bodyweight) {
    const reps = clampReps(baseReps + REP_INCREMENT);
    return {
      exerciseName: name,
      suggestedWeightKg: null,
      suggestedReps: reps,
      suggestedSets: baseSets,
      deload: false,
      rationale:
        `Bodyweight — completed ${baseSets}×${baseReps}. No load to add, so add 1 rep to ` +
        `${baseSets}×${reps}; reps carry the overload.`,
    };
  }

  const weight = roundToHalf(baseWeight! + WEIGHT_INCREMENT_KG);
  return {
    exerciseName: name,
    suggestedWeightKg: weight,
    suggestedReps: baseReps,
    suggestedSets: baseSets,
    deload: false,
    rationale:
      `Hit all ${baseSets}×${baseReps} at ${baseWeight} kg — one small step: +${WEIGHT_INCREMENT_KG} kg ` +
      `to ${weight} kg, reps unchanged.`,
  };
}

/**
 * Recommend the next session for every exercise found in `history`.
 *
 * Rules (deterministic, capped):
 *  - Group by exercise; evaluate each exercise's MOST RECENT session.
 *  - Hit all reps/sets (or the only logged session) → one increment:
 *    +2.5 kg for weighted lifts, or +1 rep for bodyweight lifts.
 *  - Missed (regressed vs the previous session) → hold the load and note it.
 *  - Every `deloadEveryWeeks` weeks (default 4) → ~-10% load & reps, `deload: true`.
 *  - No history at all → empty array (nothing to progress from).
 *
 * Output is sorted by exercise name for stable, deterministic results.
 */
export function recommendNextSession(
  history: LoggedSet[],
  opts: RecommendOptions = {},
): PerExercise[] {
  const deloadEveryWeeks =
    opts.deloadEveryWeeks && opts.deloadEveryWeeks > 0
      ? Math.floor(opts.deloadEveryWeeks)
      : DEFAULT_DELOAD_EVERY_WEEKS;

  if (!history || history.length === 0) return [];

  const groups = new Map<string, LoggedSet[]>();
  for (const set of history) {
    if (!set || typeof set.exerciseName !== 'string' || set.exerciseName.length === 0) continue;
    const arr = groups.get(set.exerciseName);
    if (arr) arr.push(set);
    else groups.set(set.exerciseName, [set]);
  }

  const out: PerExercise[] = [];
  for (const [name, setsList] of groups) {
    out.push(recommendForExercise(name, setsList, deloadEveryWeeks));
  }
  out.sort((a, b) => a.exerciseName.localeCompare(b.exerciseName));
  return out;
}

/**
 * Coarse progressive-overload TREND across a history of logged sets — PURE.
 *
 * Per exercise, compares the earliest vs the latest session by a single metric
 * (top weight for weighted lifts, else top reps for bodyweight) and votes
 * up/down. The overall trend is the majority vote across exercises. Used by the
 * rating engine as one signal of the Exercise pillar. Deterministic: `Date` is
 * only used to order the caller-supplied ISO dates.
 */
export function overloadTrendFromHistory(history: LoggedSet[]): 'up' | 'flat' | 'down' {
  if (!history || history.length < 2) return 'flat';

  const groups = new Map<string, LoggedSet[]>();
  for (const s of history) {
    if (!s || typeof s.exerciseName !== 'string' || s.exerciseName.length === 0) continue;
    const arr = groups.get(s.exerciseName);
    if (arr) arr.push(s);
    else groups.set(s.exerciseName, [s]);
  }

  const metric = (s: LoggedSet): number =>
    s.weightKg !== null && s.weightKg !== undefined ? s.weightKg : (s.reps ?? 0);

  let up = 0;
  let down = 0;
  for (const sets of groups.values()) {
    if (sets.length < 2) continue;
    const sorted = [...sets].sort((a, b) => toMs(a.date) - toMs(b.date));
    const first = metric(sorted[0]!);
    const last = metric(sorted[sorted.length - 1]!);
    if (first === 0 && last === 0) continue;
    if (last > first + 1e-6) up += 1;
    else if (last < first - 1e-6) down += 1;
  }

  if (up > down) return 'up';
  if (down > up) return 'down';
  return 'flat';
}

/** Direction the app should nudge the user's self-reported fitness level. */
export interface LevelChangeSuggestion {
  direction: 'up' | 'down' | 'hold';
  reason: string;
}

export interface LevelChangeOptions {
  /**
   * Distinct training sessions required before a promotion is considered
   * (default 4). Guards against promoting on a lucky day or two.
   */
  minSessionsToPromote?: number;
}

const DEFAULT_MIN_SESSIONS_TO_PROMOTE = 4;
/** Fewer than this many distinct sessions counts as "too little to sustain the level". */
const MIN_SESSIONS_TO_SUSTAIN = 2;
/** This many regressed exercises counts as "repeated misses". */
const REPEATED_MISS_COUNT = 2;

/** Distinct calendar days present in the history — one "session" per day. */
function distinctSessionCount(history: LoggedSet[]): number {
  const days = new Set<number>();
  for (const s of history) {
    if (!s || typeof s.exerciseName !== 'string' || s.exerciseName.length === 0) continue;
    days.add(Math.floor(toMs(s.date) / (24 * 60 * 60 * 1000)));
  }
  return days.size;
}

/**
 * Suggest promoting / demoting the user's fitness LEVEL from their logged history — PURE.
 *
 * The metric per exercise is top weight (or top reps for bodyweight), earliest vs
 * latest session. Votes are tallied across exercises, plus a count of exercises whose
 * MOST RECENT session regressed vs the prior ("misses").
 *
 * Deterministic thresholds:
 *  - PROMOTE ('up'): not already 'advanced', AND ≥ `minSessionsToPromote` (default 4)
 *    distinct sessions logged, AND more exercises trending up than down, AND zero
 *    regressed exercises. Consistent progression over enough sessions ⇒ ready for more.
 *  - DEMOTE ('down'): not already 'beginner', AND any of: more exercises trending down
 *    than up, OR fewer than 2 distinct sessions (too little to sustain the level), OR
 *    ≥ 2 regressed exercises (repeated misses). Struggling / stagnating ⇒ ease down.
 *  - HOLD otherwise (including at the 'advanced' ceiling and 'beginner' floor).
 *
 * `Date` is used only to order/bucket the caller-supplied ISO dates, so identical
 * inputs always yield an identical suggestion.
 */
export function suggestLevelChange(
  history: LoggedSet[],
  currentLevel: FitnessLevel,
  opts: LevelChangeOptions = {},
): LevelChangeSuggestion {
  const minToPromote =
    opts.minSessionsToPromote && opts.minSessionsToPromote > 0
      ? Math.floor(opts.minSessionsToPromote)
      : DEFAULT_MIN_SESSIONS_TO_PROMOTE;

  const clean = (history ?? []).filter(
    (s) => s && typeof s.exerciseName === 'string' && s.exerciseName.length > 0,
  );
  const sessions = distinctSessionCount(clean);

  const groups = new Map<string, LoggedSet[]>();
  for (const s of clean) {
    const arr = groups.get(s.exerciseName);
    if (arr) arr.push(s);
    else groups.set(s.exerciseName, [s]);
  }

  const metric = (s: LoggedSet): number =>
    s.weightKg !== null && s.weightKg !== undefined ? s.weightKg : (s.reps ?? 0);

  let up = 0;
  let down = 0;
  let misses = 0;
  for (const sets of groups.values()) {
    if (sets.length < 2) continue;
    const sorted = [...sets].sort((a, b) => toMs(a.date) - toMs(b.date));
    const first = metric(sorted[0]!);
    const last = metric(sorted[sorted.length - 1]!);
    if (!(first === 0 && last === 0)) {
      if (last > first + 1e-6) up += 1;
      else if (last < first - 1e-6) down += 1;
    }
    if (didRegress(sorted[sorted.length - 1]!, sorted[sorted.length - 2]!)) misses += 1;
  }

  // PROMOTE — consistent progression over enough sessions.
  if (
    currentLevel !== 'advanced' &&
    sessions >= minToPromote &&
    up > down &&
    misses === 0
  ) {
    return {
      direction: 'up',
      reason:
        `Progressing consistently — ${up} exercise(s) up vs ${down} down across ` +
        `${sessions} sessions with no regressions. Ready to level up.`,
    };
  }

  // DEMOTE — struggling, stagnating, or too little to sustain the level.
  if (currentLevel !== 'beginner') {
    if (down > up) {
      return {
        direction: 'down',
        reason: `Trending down (${down} exercise(s) down vs ${up} up). Ease the level to rebuild.`,
      };
    }
    if (sessions < MIN_SESSIONS_TO_SUSTAIN) {
      return {
        direction: 'down',
        reason:
          `Only ${sessions} logged session(s) — too little to sustain this level. ` +
          `Ease down and build consistency.`,
      };
    }
    if (misses >= REPEATED_MISS_COUNT) {
      return {
        direction: 'down',
        reason: `Repeated misses on ${misses} exercise(s). Ease the level and nail your reps.`,
      };
    }
  }

  // HOLD — steady, or already at a boundary level.
  const reason =
    currentLevel === 'advanced' && up > down
      ? 'Already at the advanced level — keep progressing where you are.'
      : currentLevel === 'beginner' && (down > up || sessions < MIN_SESSIONS_TO_SUSTAIN)
        ? 'Already at the beginner level — hold and build consistency before easing further.'
        : 'Steady — keep training at your current level.';
  return { direction: 'hold', reason };
}
