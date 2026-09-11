import { prisma } from '../../lib/prisma';
import { requireCompleteProfile } from '../profile/profile.service';
import { computeCalcResult, type CalcResult } from './calcResult';
import { physiqueNutrition, type PhysiqueGoal } from '../exercise/physique';
import { goalTimeline } from '../../calc/goalTimeline';
import type { ActivityLevel, Goal } from '../../calc/types';
import type { Condition } from '../../guardrails';
import { dietRampFor, applyDietRamp } from './dietRamp';
import { detectActivityLevel, tdeeDeltaForLevelChange, type ExerciseSessionSample } from './activityAutoDetect';

/** Whole years between dob and now. */
export function ageFromDob(dobISO: string, now: Date = new Date()): number {
  const dob = new Date(dobISO);
  let age = now.getUTCFullYear() - dob.getUTCFullYear();
  const m = now.getUTCMonth() - dob.getUTCMonth();
  if (m < 0 || (m === 0 && now.getUTCDate() < dob.getUTCDate())) age -= 1;
  return age;
}

/** Computes the authoritative CalcResult for a user and persists a versioned snapshot. */
export async function computeAndSaveForUser(userId: string): Promise<CalcResult> {
  const { profile, sensitive } = await requireCompleteProfile(userId);

  // A physique goal (recomp/lean_bulk/cut/maintain) refines the base goal - but only via the SAFE
  // calorie engine below (floors/caps/minor & medical blocks still apply). A minor's "cut" downgrades.
  const ageYears = ageFromDob(sensitive.dob);
  const s = sensitive as { physiqueGoal?: PhysiqueGoal; conditions?: string[] };
  const weightLossBlocked =
    (s.conditions ?? []).some((c) => ['pregnancy', 'breastfeeding', 'cancer'].includes(c));
  let effectiveGoal: Goal = s.physiqueGoal
    ? physiqueNutrition(s.physiqueGoal, { isMinor: ageYears < 18, weightLossBlocked }).mappedGoal
    : (profile.goal as Goal);

  // A chosen "reach target by N weeks" timeline drives a SAFE, clamped weekly rate - and takes
  // precedence for the calorie direction (concrete intent), so picking a timeline changes the target.
  const tw = (sensitive as { targetTimeframeWeeks?: number }).targetTimeframeWeeks;
  let desiredWeeklyLossKg = sensitive.desiredWeeklyLossKg;
  if (tw && sensitive.targetWeightKg) {
    const timeline = goalTimeline({
      currentWeightKg: sensitive.currentWeightKg,
      targetWeightKg: sensitive.targetWeightKg,
      desiredWeeks: tw,
      isMinor: ageYears < 18,
      weightLossBlocked,
    });
    if (!timeline.blocked && timeline.direction === 'lose') {
      effectiveGoal = 'lose';
      desiredWeeklyLossKg = timeline.desiredWeeklyLossKg;
    } else if (!timeline.blocked && timeline.direction === 'gain') {
      effectiveGoal = 'gain';
    }
  }

  const reportedActivityLevel = profile.activityLevel as ActivityLevel;

  // Real logged workouts over the trailing 28 days, not the one-time onboarding self-report -
  // so a consistent gym-goer's TDEE reflects actual behavior instead of a static answer.
  const WINDOW_DAYS = 28;
  const windowStart = new Date(Date.now() - WINDOW_DAYS * 86_400_000);
  const recentSessions = await prisma.exerciseLog.findMany({
    where: { userId, performedAt: { gte: windowStart } },
    select: { performedAt: true },
  });
  const sessionSamples: ExerciseSessionSample[] = recentSessions.map((s) => ({
    dayKey: s.performedAt.toISOString().slice(0, 10),
  }));
  const activityDetection = detectActivityLevel(reportedActivityLevel, sessionSamples, WINDOW_DAYS);

  let result = computeCalcResult({
    heightCm: profile.heightCm,
    currentWeightKg: sensitive.currentWeightKg,
    targetWeightKg: sensitive.targetWeightKg,
    ageYears,
    sex: sensitive.sex,
    activityLevel: activityDetection.effectiveLevel,
    goal: effectiveGoal,
    waistCm: sensitive.waistCm,
    conditions: sensitive.conditions as Condition[],
    desiredWeeklyLossKg,
    clinicianOverride: sensitive.clinicianOverride,
    reducedMobility: profile.reducedMobility,
    climate: (sensitive as { climate?: 'temperate' | 'hot' | 'cold' }).climate,
  });

  if (activityDetection.higherThanReported) {
    const deltaKcal = tdeeDeltaForLevelChange(result.bmr, reportedActivityLevel, activityDetection.effectiveLevel);
    result = {
      ...result,
      flags: [
        ...result.flags,
        {
          code: 'ACTIVITY_AUTO_BUMP',
          severity: 'info',
          message: `You've been logging ${activityDetection.sessionsPerWeek}x/week of workouts - more than your "${reportedActivityLevel}" setting, so we raised your calorie budget by ~${deltaKcal} kcal/day to match. Update your activity level in Profile to make this permanent.`,
        },
      ],
    };
  } else if (activityDetection.lowerThanReported) {
    result = {
      ...result,
      flags: [
        ...result.flags,
        {
          code: 'ACTIVITY_STALE',
          severity: 'info',
          message: `Your activity is set to "${reportedActivityLevel}" but we haven't seen logged workouts matching that lately. Your calorie target is unchanged, but consider updating your activity level in Profile for a more accurate plan.`,
        },
      ],
    };
  }

  // New-to-dieting ease-in (see dietRamp.ts): someone who just started tracking got thrown
  // straight into the full computed deficit/surplus from day one. Blend toward maintenance for
  // the first 2 weeks - the ramped target always sits between TDEE and the original safe target,
  // so it can never be MORE restrictive than what computeCalcResult already cleared.
  const gymMonths = (sensitive as { gymMembershipMonths?: number }).gymMembershipMonths;
  const gymJoinIso = (sensitive as { gymJoinDate?: string }).gymJoinDate;
  const startedAt = gymJoinIso ? Date.parse(gymJoinIso) : NaN;
  const daysSinceStart = Number.isNaN(startedAt)
    ? Math.floor((Date.now() - profile.createdAt.getTime()) / 86_400_000)
    : Math.max(0, Math.floor((Date.now() - startedAt) / 86_400_000));
  const ramp = dietRampFor(gymMonths, daysSinceStart);
  if (ramp && effectiveGoal !== 'maintain') {
    result = applyDietRamp(result, ramp);
    result = {
      ...result,
      flags: [
        ...result.flags,
        {
          code: 'DIET_RAMP',
          severity: 'info',
          message: `New here - easing your calorie target in (week ${ramp.week}/2). Full target from week 2 as you build the tracking habit.`,
        },
      ],
    };
  }

  await prisma.calcResultSnapshot.create({
    data: { userId, result: result as unknown as object },
  });
  await prisma.auditLog.create({
    data: { userId, action: 'calc.compute', detail: 'CalcResult snapshot saved' },
  });
  return result;
}

export async function latestCalcResult(userId: string) {
  const snap = await prisma.calcResultSnapshot.findFirst({
    where: { userId },
    orderBy: { createdAt: 'desc' },
  });
  return snap?.result ?? null;
}
