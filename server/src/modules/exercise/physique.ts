import type { Goal } from '../../calc/types';
import type { WeeklyWorkout } from './exercise.types';

/**
 * Physique / aesthetic goals - deterministic, safety-first. A physique goal maps to a base calorie
 * Goal (routed through the existing guardrail engine, so floors/caps/minor-blocks still apply) and a
 * protein hint, and priority muscle groups add extra training volume WITHIN the level's set caps.
 * Never an aggressive cut for minors or the medically weight-loss-blocked. Body-neutral. Pure & tested.
 */

export type PhysiqueGoal = 'recomp' | 'lean_bulk' | 'cut' | 'maintain';

export interface PhysiqueNutrition {
  /** The base Goal fed to the safe calorie engine. */
  mappedGoal: Goal;
  /** Suggested protein (g per kg bodyweight) - higher when preserving muscle in a deficit. */
  proteinPerKgHint: number;
  note: string;
  /** True if the requested physique goal was downgraded for safety (e.g. a minor's cut). */
  blocked: boolean;
}

/**
 * Map a physique goal → safe calorie/protein implications. `ctx.weightLossBlocked` = the guardrail
 * already forbids a deficit (minor / pregnancy / cancer / underweight).
 */
export function physiqueNutrition(
  goal: PhysiqueGoal,
  ctx: { isMinor?: boolean; weightLossBlocked?: boolean } = {},
): PhysiqueNutrition {
  const noCut = ctx.isMinor || ctx.weightLossBlocked;
  if (goal === 'cut' && noCut) {
    return {
      mappedGoal: 'maintain',
      proteinPerKgHint: 1.6,
      note: 'A cut isn’t safe here, so this uses maintenance calories with good protein instead. Focus on strength and habits.',
      blocked: true,
    };
  }
  switch (goal) {
    case 'lean_bulk':
      return { mappedGoal: 'gain', proteinPerKgHint: 1.8, note: 'A slight calorie surplus with high protein to build muscle while minimising fat gain.', blocked: false };
    case 'cut':
      return { mappedGoal: 'lose', proteinPerKgHint: 2.0, note: 'A safe calorie deficit with high protein to keep muscle while losing fat.', blocked: false };
    case 'recomp':
      return { mappedGoal: 'maintain', proteinPerKgHint: 2.0, note: 'Maintenance calories, high protein and hard training to build muscle and lose fat together.', blocked: false };
    case 'maintain':
    default:
      return { mappedGoal: 'maintain', proteinPerKgHint: 1.6, note: 'Maintenance calories to hold your current physique.', blocked: false };
  }
}

/** User-facing priority label → the internal muscleGroup tokens it should boost. */
const PRIORITY_SYNONYMS: Record<string, string[]> = {
  shoulders: ['shoulders'],
  back: ['back'],
  chest: ['chest'],
  arms: ['biceps', 'triceps', 'arms', 'forearms'],
  legs: ['legs', 'quads', 'hamstrings', 'posterior chain', 'calves'],
  glutes: ['glutes', 'legs', 'posterior chain'],
  core: ['core', 'abs'],
};

function matchesPriority(muscleGroup: string, priorities: string[]): boolean {
  const g = muscleGroup.toLowerCase();
  for (const p of priorities) {
    const tokens = PRIORITY_SYNONYMS[p.toLowerCase()] ?? [p.toLowerCase()];
    if (tokens.some((t) => g.includes(t) || t.includes(g))) return true;
  }
  return false;
}

/**
 * Add ONE extra set to each strength exercise that hits a priority muscle group, capped at `maxSets`
 * so total volume never exceeds the level's safety ceiling. Antagonists/other groups are untouched
 * (balance preserved). Returns the plan unchanged if there are no priorities. Appends an explaining note.
 */
export function applyMusclePriority(plan: WeeklyWorkout, priorities: string[] | undefined, maxSets: number): WeeklyWorkout {
  const prio = (priorities ?? []).filter((p) => typeof p === 'string' && p.trim()).slice(0, 4);
  if (prio.length === 0) return plan;

  let boosted = false;
  const days = plan.days.map((day) => {
    if (day.rest) return day;
    const exercises = day.exercises.map((ex) => {
      if (ex.type === 'strength' && ex.muscleGroup && matchesPriority(ex.muscleGroup, prio) && ex.sets < maxSets) {
        boosted = true;
        return { ...ex, sets: Math.min(maxSets, ex.sets + 1) };
      }
      return ex;
    });
    return { ...day, exercises };
  });

  if (!boosted) return plan;
  const extra = `Prioritising ${prio.join(', ')} - a little extra volume there, kept within safe limits.`;
  return { ...plan, days, note: plan.note ? `${plan.note} ${extra}` : extra };
}

/** The level's max-sets cap (mirrors workoutGenerator; kept here so callers don't reach in). */
export function maxSetsForLevel(level: 'beginner' | 'intermediate' | 'advanced'): number {
  return level === 'beginner' ? 3 : level === 'advanced' ? 6 : 5;
}
