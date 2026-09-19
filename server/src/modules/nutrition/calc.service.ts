import { prisma } from '../../lib/prisma';
import { requireCompleteProfile } from '../profile/profile.service';
import { computeCalcResult, type CalcResult } from './calcResult';
import { physiqueNutrition, type PhysiqueGoal } from '../exercise/physique';
import { goalTimeline } from '../../calc/goalTimeline';
import type { ActivityLevel, Goal } from '../../calc/types';
import { CALORIE_FLOOR, type Condition } from '../../guardrails';
import { dietRampFor, applyDietRamp } from './dietRamp';
import { detectActivityLevel, averageCompletedDaySteps, tdeeDeltaForLevelChange, type ExerciseSessionSample } from './activityAutoDetect';
import { computeAdaptiveTdee } from './adaptiveTdee';
import { decryptJson } from '../../lib/crypto';

/** Whole years between dob and now. */
export function ageFromDob(dobISO: string, now: Date = new Date()): number {
  const dob = new Date(dobISO);
  let age = now.getUTCFullYear() - dob.getUTCFullYear();
  const m = now.getUTCMonth() - dob.getUTCMonth();
  if (m < 0 || (m === 0 && now.getUTCDate() < dob.getUTCDate())) age -= 1;
  return age;
}

// Short per-user cache: the dashboard, coach chat, plan and adaptive advice all call this, and each call
// used to re-run ~8 queries and write 2 rows. A 30 s window makes repeat calls instant and stops the
// snapshot/audit tables filling with duplicates. Cleared whenever the profile is saved.
const CALC_TTL_MS = 30_000;
const calcCache = new Map<string, { at: number; result: CalcResult }>();
export function invalidateCalcCache(userId: string): void {
  calcCache.delete(userId);
}

/** Computes the authoritative CalcResult for a user and persists a versioned snapshot. */
export async function computeAndSaveForUser(userId: string): Promise<CalcResult> {
  const hit = calcCache.get(userId);
  if (hit && Date.now() - hit.at < CALC_TTL_MS) return hit.result;
  const result = await computeAndSaveUncached(userId);
  calcCache.set(userId, { at: Date.now(), result });
  return result;
}

async function computeAndSaveUncached(userId: string): Promise<CalcResult> {
  const { profile, sensitive } = await requireCompleteProfile(userId);

  // A physique goal (recomp/lean_bulk/cut/maintain) refines the base goal - but only via the SAFE
  // calorie engine below (floors/caps/minor & medical blocks still apply). A minor's "cut" downgrades.
  const ageYears = ageFromDob(sensitive.dob);
  const s = sensitive as { physiqueGoal?: PhysiqueGoal; conditions?: string[] };
  const weightLossBlocked =
    (s.conditions ?? []).some((c) => ['pregnancy', 'breastfeeding', 'cancer'].includes(c));
  const physiqueDerivedGoal: Goal | null = s.physiqueGoal
    ? physiqueNutrition(s.physiqueGoal, { isMinor: ageYears < 18, weightLossBlocked }).mappedGoal
    : null;
  let effectiveGoal: Goal = physiqueDerivedGoal ?? (profile.goal as Goal);

  // A chosen "reach target by N weeks" timeline drives a SAFE, clamped weekly rate - and takes
  // precedence for the calorie direction (concrete intent), so picking a timeline changes the target.
  const tw = (sensitive as { targetTimeframeWeeks?: number }).targetTimeframeWeeks;
  let desiredWeeklyLossKg = sensitive.desiredWeeklyLossKg;
  let timelineOverrodePhysiqueGoal = false;
  if (tw && sensitive.targetWeightKg) {
    const timeline = goalTimeline({
      currentWeightKg: sensitive.currentWeightKg,
      targetWeightKg: sensitive.targetWeightKg,
      desiredWeeks: tw,
      isMinor: ageYears < 18,
      weightLossBlocked,
    });
    if (!timeline.blocked && timeline.direction === 'lose') {
      if (physiqueDerivedGoal !== null && physiqueDerivedGoal !== 'lose') timelineOverrodePhysiqueGoal = true;
      effectiveGoal = 'lose';
      desiredWeeklyLossKg = timeline.desiredWeeklyLossKg;
    } else if (!timeline.blocked && timeline.direction === 'gain') {
      if (physiqueDerivedGoal !== null && physiqueDerivedGoal !== 'gain') timelineOverrodePhysiqueGoal = true;
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
  // Real step history (see DailySteps) matters just as much as gym sessions - someone whose real
  // movement is daily walking rather than structured workouts deserves the same fair credit.
  const recentSteps = await prisma.dailySteps.findMany({
    where: { userId, date: { gte: windowStart } },
    select: { date: true, steps: true },
  });
  const avgDailySteps = averageCompletedDaySteps(recentSteps, new Date());
  const activityDetection = detectActivityLevel(reportedActivityLevel, sessionSamples, WINDOW_DAYS, avgDailySteps);

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

  // Lifters doing a recomposition / lean bulk: protein to ~2.0 g/kg (muscle-building range) instead of the
  // generic 1.8. Calories stay the same - the extra protein is taken out of carbs. Never for kidney disease.
  const buildsMuscle = s.physiqueGoal === 'recomp' || s.physiqueGoal === 'lean_bulk';
  if (buildsMuscle && !(s.conditions ?? []).includes('kidney_disease') && !weightLossBlocked) {
    const wanted = Math.round(2.0 * Math.min(sensitive.currentWeightKg, 95));
    if (wanted > result.proteinG) {
      const extraG = wanted - result.proteinG;
      result = { ...result, proteinG: wanted, carbG: Math.max(60, Math.round(result.carbG - extraG)) };
    }
  }

  if (timelineOverrodePhysiqueGoal) {
    result = {
      ...result,
      flags: [
        ...result.flags,
        {
          code: 'TIMELINE_OVERRIDES_PHYSIQUE_GOAL',
          severity: 'info',
          message: `Your target-weight timeline (reach ${sensitive.targetWeightKg}kg in ${tw} weeks) is currently driving your calorie target, not your Physique Goal - so changing Physique Goal alone won't change this number. Clear the timeline in Profile if you want Physique Goal to decide the direction instead.`,
        },
      ],
    };
  }

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

  // Adaptive TDEE (MacroFactor-style): recalibrate against the user's OWN logged results, not just
  // the formula. Reuses the same 28-day window as activity auto-detect for consistency.
  const [recentFoodLogs, recentCheckins] = await Promise.all([
    prisma.foodLog.findMany({ where: { userId, loggedAt: { gte: windowStart } }, select: { loggedAt: true, kcal: true } }),
    prisma.weeklyCheckin.findMany({ where: { userId, date: { gte: windowStart } }, orderBy: { date: 'asc' } }),
  ]);
  const intakePerDay = new Map<string, number>();
  for (const l of recentFoodLogs) {
    const k = l.loggedAt.toISOString().slice(0, 10);
    intakePerDay.set(k, (intakePerDay.get(k) ?? 0) + l.kcal);
  }
  const dailyIntake = [...intakePerDay.entries()].map(([date, kcal]) => ({ date, kcal }));
  const weightHistory = recentCheckins
    .map((c) => {
      if (!c.measurementsEnc) return null;
      try {
        const m = decryptJson<{ weightKg?: number }>(c.measurementsEnc);
        return m.weightKg ? { date: c.date.toISOString().slice(0, 10), weightKg: m.weightKg } : null;
      } catch {
        return null;
      }
    })
    .filter((p): p is { date: string; weightKg: number } => p !== null);

  const adaptive = computeAdaptiveTdee(result.tdee, dailyIntake, weightHistory);
  if (adaptive.available && adaptive.adjustedTdee && Math.abs(adaptive.deviationPct ?? 0) > 2) {
    const kcalDelta = adaptive.adjustedTdee - result.tdee;
    const floor = CALORIE_FLOOR[sensitive.sex];
    const recalibratedKcal = Math.max(floor, Math.round(result.dailyKcal + kcalDelta));
    if (recalibratedKcal !== result.dailyKcal) {
      // Protein stays fixed (it's set per kg bodyweight, not per calorie) - the recalibration
      // delta is absorbed by fat/carb, split proportionally to their existing kcal share.
      const proteinKcal = result.proteinG * 4;
      const remainingKcalBefore = Math.max(1, result.dailyKcal - proteinKcal);
      const remainingKcalAfter = Math.max(0, recalibratedKcal - proteinKcal);
      const remScale = remainingKcalAfter / remainingKcalBefore;
      result = {
        ...result,
        dailyKcal: recalibratedKcal,
        fatG: Math.max(0, Math.round(result.fatG * remScale)),
        carbG: Math.max(0, Math.round(result.carbG * remScale)),
        flags: [
          ...result.flags,
          {
            code: 'ADAPTIVE_TDEE',
            severity: 'info',
            message: adaptive.message ?? 'Your calorie target has been recalibrated based on your own logged results.',
          },
        ],
      };
    }
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
