/**
 * Fixed-calendar-month phase plan for a "recomp" style goal: fat-loss first, then shape
 * (recomp/toning), then muscle-build with whatever timeframe remains. Purely informational —
 * this NEVER feeds back into the calorie/macro engine (see calc.service.ts's LOCKED CORE
 * comment); it only tells the user which stage of their own stated timeline they're in.
 */

export type PlanPhase = 'fat_loss' | 'shape' | 'muscle_build';

export interface PhasePlanResult {
  phase: PlanPhase;
  phaseLabel: string;
  phaseIndex: number; // 1-based, within phaseCount
  phaseCount: number;
  weekInPhase: number; // 1-based
  weeksInPhase: number;
  weeksElapsed: number;
  totalWeeks: number;
  weeksRemaining: number;
}

const FAT_LOSS_WEEKS = 13; // ~3 calendar months
const SHAPE_WEEKS = 13; // ~3 calendar months

const PHASE_LABELS: Record<PlanPhase, string> = {
  fat_loss: 'Fat Loss',
  shape: 'Shape & Tone',
  muscle_build: 'Muscle Build',
};

/**
 * Returns null when there's no concrete timeline to phase (no targetTimeframeWeeks), when the
 * plan hasn't started yet, or when it's already finished.
 */
export function computePhasePlan(
  planStart: Date,
  targetTimeframeWeeks: number | null | undefined,
  now: Date = new Date(),
): PhasePlanResult | null {
  if (!targetTimeframeWeeks || targetTimeframeWeeks <= 0) return null;

  const msElapsed = now.getTime() - planStart.getTime();
  if (msElapsed < 0) return null;
  const weeksElapsed = msElapsed / (7 * 24 * 60 * 60 * 1000);
  if (weeksElapsed >= targetTimeframeWeeks) return null;

  // Short timelines don't get 3 fixed months each - split proportionally into whichever
  // phases actually fit (still fixed-calendar within that proportional split).
  let bounds: { phase: PlanPhase; weeks: number }[];
  if (targetTimeframeWeeks <= FAT_LOSS_WEEKS) {
    bounds = [{ phase: 'fat_loss', weeks: targetTimeframeWeeks }];
  } else if (targetTimeframeWeeks <= FAT_LOSS_WEEKS + SHAPE_WEEKS) {
    bounds = [
      { phase: 'fat_loss', weeks: FAT_LOSS_WEEKS },
      { phase: 'shape', weeks: targetTimeframeWeeks - FAT_LOSS_WEEKS },
    ];
  } else {
    bounds = [
      { phase: 'fat_loss', weeks: FAT_LOSS_WEEKS },
      { phase: 'shape', weeks: SHAPE_WEEKS },
      { phase: 'muscle_build', weeks: targetTimeframeWeeks - FAT_LOSS_WEEKS - SHAPE_WEEKS },
    ];
  }

  let cursor = 0;
  for (let i = 0; i < bounds.length; i++) {
    const entry = bounds[i]!;
    const { phase, weeks } = entry;
    if (weeksElapsed < cursor + weeks || i === bounds.length - 1) {
      return {
        phase,
        phaseLabel: PHASE_LABELS[phase],
        phaseIndex: i + 1,
        phaseCount: bounds.length,
        weekInPhase: Math.floor(weeksElapsed - cursor) + 1,
        weeksInPhase: Math.ceil(weeks),
        weeksElapsed: Math.floor(weeksElapsed),
        totalWeeks: targetTimeframeWeeks,
        weeksRemaining: Math.max(0, Math.ceil(targetTimeframeWeeks - weeksElapsed)),
      };
    }
    cursor += weeks;
  }
  return null;
}
