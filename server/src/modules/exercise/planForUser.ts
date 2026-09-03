import { prisma } from '../../lib/prisma';
import { decryptJson } from '../../lib/crypto';
import type { SensitiveData } from '../profile/profile.schemas';
import { generateWeeklyWorkout } from './workoutGenerator';
import { localSunday, localToday } from '../../lib/tz';
import { ageFromDob } from '../nutrition/calc.service';
import type { BodyGoal, ExerciseLocation, WeeklyWorkout } from './exercise.types';

/** Sensible default body goal from the diet goal when the user hasn't chosen one. */
function goalToBody(goal: string | null | undefined): BodyGoal {
  if (goal === 'gain') return 'muscular';
  if (goal === 'maintain') return 'athletic';
  return 'fatloss';
}

/**
 * The user's current base weekly workout (no period adaptation - that only softens intensity, not
 * which days are training vs rest). Returns null when the profile isn't complete. Shared by the
 * rating engine (scheduled-day count) and the coach brief (is today a rest day?).
 */
export async function weeklyWorkoutForUser(userId: string, offsetMin = 0): Promise<WeeklyWorkout | null> {
  const profile = await prisma.profile.findUnique({ where: { userId } });
  if (!profile?.sensitiveEnc) return null;

  const s = decryptJson<SensitiveData>(profile.sensitiveEnc);
  const location: ExerciseLocation = s.exerciseLocation ?? 'home';
  const goal: BodyGoal = s.bodyGoal ?? goalToBody(profile.goal);
  const under18 = ageFromDob(s.dob) < 18;
  const medicalCaution = (s.conditions?.length ?? 0) > 0 || profile.reducedMobility;

  // Weeks since the user joined the gym → progressive-overload phase (spec Section 5).
  const joinIso = (s as { gymJoinDate?: string }).gymJoinDate;
  let weeksSinceJoin: number | undefined;
  if (joinIso) {
    const joined = Date.parse(joinIso);
    if (!Number.isNaN(joined)) {
      weeksSinceJoin = Math.max(0, Math.floor((Date.now() - joined) / (7 * 86_400_000)));
    }
  }

  return generateWeeklyWorkout(goal, location, {
    weeksSinceJoin,
    restDayOfWeek: s.workoutRestDay,
    startDate: localSunday(offsetMin),
    today: localToday(offsetMin),
    fitnessLevel: s.fitnessLevel,
    intensity: s.intensityPreference,
    under18,
    medicalCaution,
    priorityMuscles: (s as { priorityMuscles?: string[] }).priorityMuscles,
    split: (s as { trainingSplit?: import('./exercise.types').TrainingSplit }).trainingSplit,
  });
}
