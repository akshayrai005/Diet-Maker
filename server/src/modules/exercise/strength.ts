import type { ExerciseItem } from './exercise.types';

/**
 * Strength math + exercise substitutions - deterministic, unit-tested. Estimated 1RM uses the Epley
 * formula (valid for low reps); substitutions offer equipment-free / injury-friendly alternatives.
 * Educational, never a prescription to lift a specific load; form + safety cues still apply.
 */

/** Epley estimated 1-rep max. Valid for 1-12 reps; returns null for invalid/out-of-range input. */
export function estimate1RM(weightKg: number, reps: number): number | null {
  if (![weightKg, reps].every((v) => typeof v === 'number' && isFinite(v)) || weightKg <= 0 || reps < 1) return null;
  if (reps > 12) return null; // Epley loses accuracy past ~12 reps
  if (reps === 1) return round1(weightKg);
  return round1(weightKg * (1 + reps / 30));
}

const round1 = (x: number) => Math.round(x * 10) / 10;

export interface StrengthLog {
  exerciseName: string;
  weightKg: number | null;
  reps: number | null;
  performedAt: string | Date;
}

export interface StrengthPoint {
  date: string; // ISO day
  est1RM: number;
}

export interface StrengthTrend {
  exerciseName: string;
  points: StrengthPoint[];
  best: number;
  latest: number;
  /** Signed change latest − first (kg). */
  change: number;
}

/**
 * Per-exercise est-1RM trend from logged sets: the BEST estimated 1RM per day, oldest→newest.
 * Only weighted logs with valid reps count. Exercises are grouped case-insensitively by name.
 */
export function strengthTrend(logs: StrengthLog[]): StrengthTrend[] {
  const byExercise = new Map<string, Map<string, number>>(); // name → (dayKey → best est1RM)
  const displayName = new Map<string, string>();

  for (const log of logs) {
    if (log.weightKg == null || log.reps == null) continue;
    const est = estimate1RM(log.weightKg, log.reps);
    if (est == null) continue;
    const key = log.exerciseName.trim().toLowerCase();
    if (!key) continue;
    displayName.set(key, log.exerciseName.trim());
    const day = (typeof log.performedAt === 'string' ? new Date(log.performedAt) : log.performedAt).toISOString().slice(0, 10);
    const days = byExercise.get(key) ?? new Map<string, number>();
    days.set(day, Math.max(days.get(day) ?? 0, est));
    byExercise.set(key, days);
  }

  const out: StrengthTrend[] = [];
  for (const [key, days] of byExercise) {
    const points: StrengthPoint[] = [...days.entries()]
      .map(([date, est1RM]) => ({ date, est1RM }))
      .sort((a, b) => a.date.localeCompare(b.date));
    if (points.length === 0) continue;
    const best = Math.max(...points.map((p) => p.est1RM));
    const first = points[0]!.est1RM;
    const latest = points[points.length - 1]!.est1RM;
    out.push({ exerciseName: displayName.get(key) ?? key, points, best, latest, change: round1(latest - first) });
  }
  // Most-recently-trained first.
  out.sort((a, b) => (b.points[b.points.length - 1]!.date).localeCompare(a.points[a.points.length - 1]!.date));
  return out;
}

// ---- Exercise substitutions (equipment-free / injury-friendly) ----

interface SubRule {
  match: string[];
  subs: string[];
}

const SUB_RULES: SubRule[] = [
  { match: ['barbell bench', 'bench press', 'chest press'], subs: ['Push-ups (feet elevated to progress)', 'Dumbbell floor press (shoulder-friendly)', 'Resistance-band chest press'] },
  { match: ['push-up', 'push up', 'pushup'], subs: ['Incline push-ups (hands on a surface)', 'Knee push-ups', 'Band chest press'] },
  { match: ['squat', 'leg press', 'hack squat'], subs: ['Bodyweight / goblet squats', 'Split squats (knee-friendly, one leg at a time)', 'Wall sit'] },
  { match: ['deadlift', 'romanian', 'rdl'], subs: ['Hip thrusts / glute bridges (back-friendly)', 'Single-leg RDL with dumbbells', 'Band good-mornings'] },
  { match: ['overhead', 'shoulder press', 'military'], subs: ['Pike push-ups', 'Dumbbell/band shoulder press seated (back-supported)', 'Landmine press (shoulder-friendly)'] },
  { match: ['pull-up', 'pull up', 'pullup', 'chin-up', 'pulldown'], subs: ['Band-assisted pull-ups', 'Inverted rows (feet on floor)', 'Lat pulldown with a band'] },
  { match: ['row'], subs: ['Single-arm dumbbell row', 'Band rows', 'Inverted rows'] },
  { match: ['lunge'], subs: ['Split squats (hold onto support)', 'Step-ups', 'Reverse lunges (knee-friendly)'] },
  { match: ['curl'], subs: ['Band curls', 'Dumbbell curls', 'Isometric towel curls'] },
  { match: ['tricep', 'pushdown', 'skull', 'dip'], subs: ['Bench dips', 'Band pushdowns', 'Close-grip push-ups'] },
  { match: ['plank', 'crunch', 'sit-up', 'ab'], subs: ['Dead bug (back-friendly)', 'Bird dog', 'Standing knee raises'] },
];

/** Up to 3 equipment-free / injury-friendly alternatives for a movement (empty if none matched). */
export function substitutionsFor(name: string): string[] {
  const lower = name.toLowerCase();
  for (const rule of SUB_RULES) {
    if (rule.match.some((t) => lower.includes(t))) return rule.subs.slice(0, 3);
  }
  return [];
}

/** Attach substitutions to an exercise item (leaves it untouched if none apply). */
export function withSubstitutions(ex: ExerciseItem): ExerciseItem {
  const subs = substitutionsFor(ex.name);
  return subs.length ? { ...ex, substitutions: subs } : ex;
}
