import { prisma } from '../../lib/prisma';
import { requireCompleteProfile } from '../profile/profile.service';
import { computeCalcResult, type CalcResult } from './calcResult';
import { physiqueNutrition, type PhysiqueGoal } from '../exercise/physique';
import type { ActivityLevel, Goal } from '../../calc/types';
import type { Condition } from '../../guardrails';

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

  // A physique goal (recomp/lean_bulk/cut/maintain) refines the base goal — but only via the SAFE
  // calorie engine below (floors/caps/minor & medical blocks still apply). A minor's "cut" downgrades.
  const ageYears = ageFromDob(sensitive.dob);
  const s = sensitive as { physiqueGoal?: PhysiqueGoal; conditions?: string[] };
  const weightLossBlocked =
    (s.conditions ?? []).some((c) => ['pregnancy', 'breastfeeding', 'cancer'].includes(c));
  const effectiveGoal: Goal = s.physiqueGoal
    ? physiqueNutrition(s.physiqueGoal, { isMinor: ageYears < 18, weightLossBlocked }).mappedGoal
    : (profile.goal as Goal);

  const result = computeCalcResult({
    heightCm: profile.heightCm,
    currentWeightKg: sensitive.currentWeightKg,
    targetWeightKg: sensitive.targetWeightKg,
    ageYears,
    sex: sensitive.sex,
    activityLevel: profile.activityLevel as ActivityLevel,
    goal: effectiveGoal,
    waistCm: sensitive.waistCm,
    conditions: sensitive.conditions as Condition[],
    desiredWeeklyLossKg: sensitive.desiredWeeklyLossKg,
    clinicianOverride: sensitive.clinicianOverride,
    reducedMobility: profile.reducedMobility,
    climate: (sensitive as { climate?: 'temperate' | 'hot' | 'cold' }).climate,
  });

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
