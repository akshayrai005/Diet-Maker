import { prisma } from '../../lib/prisma';
import { gatherUserContext } from '../rating/context';
import { getWeeklyReport } from './reports.service';
import { getRiskAssessment } from '../risk/risk.service';
import { buildAnalysisNarrative, type AnalysisNarrative } from './analysis';
import type { ReportAnalysis } from './reportHtml';
import type { RatingResult } from '../rating/rating';

export interface AnalysisResult {
  narrative: AnalysisNarrative;
  rating: RatingResult;
}

/** Whether the user currently has any risk findings (best-effort; false if the profile is incomplete). */
async function hasRisk(userId: string): Promise<boolean> {
  try {
    return (await getRiskAssessment(userId)).findings.length > 0;
  } catch {
    return false;
  }
}

/** Shared computation: rating + coach-voice narrative from server-computed facts. */
export async function getAnalysis(userId: string, offsetMin = 0, now: Date = new Date()): Promise<AnalysisResult> {
  const [ctx, report, user, risk] = await Promise.all([
    gatherUserContext(userId, offsetMin, now),
    getWeeklyReport(userId, now.toISOString(), now, 7),
    prisma.user.findUnique({ where: { id: userId }, select: { firstName: true } }),
    hasRisk(userId),
  ]);

  const narrative = buildAnalysisNarrative({
    firstName: user?.firstName ?? null,
    overall: ctx.rating.overall,
    grade: ctx.rating.grade,
    pillars: ctx.rating.pillars.map((p) => ({ key: p.key, label: p.label, score: p.score, weight: p.weight })),
    biggestLever: ctx.rating.biggestLever.message,
    streakDays: ctx.loggingStreakDays,
    weightDeltaKg: report.weightDeltaKg,
    prediction: report.prediction
      ? {
          projectedWeightKg: report.prediction.projectedWeightKg,
          projectedMonthlyDeltaKg: report.prediction.projectedMonthlyDeltaKg,
          note: report.prediction.note,
          blocked: report.prediction.blocked,
        }
      : null,
    hasRiskFindings: risk,
  });

  return { narrative, rating: ctx.rating };
}

/** The analysis shaped for the HTML report header. */
export function toReportAnalysis(result: AnalysisResult): ReportAnalysis {
  return {
    rating: {
      overall: result.rating.overall,
      grade: result.rating.grade,
      pillars: result.rating.pillars.map((p) => ({ label: p.label, score: p.score })),
      biggestLever: result.rating.biggestLever.message,
    },
    paragraphs: result.narrative.paragraphs,
  };
}
