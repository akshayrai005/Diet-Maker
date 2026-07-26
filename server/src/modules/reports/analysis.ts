/**
 * Deterministic, coach-voice ANALYSIS narrative - PURE. No DB, no Date.now, no randomness, no LLM.
 * Every sentence is a template over server-computed facts (rating, streak, weight trend, prediction,
 * risk presence), so it works with AI_PROVIDER=rules and never invents a number. An LLM layer, if
 * enabled, may only rephrase these exact facts; this output is the always-present fallback.
 */

export interface AnalysisInput {
  firstName?: string | null;
  overall: number;
  grade: string;
  pillars: { key: string; label: string; score: number; weight: number }[];
  biggestLever: string;
  streakDays: number;
  weightDeltaKg: number | null;
  prediction: {
    projectedWeightKg: number | null;
    projectedMonthlyDeltaKg: number;
    note: string;
    blocked: boolean;
  } | null;
  hasRiskFindings: boolean;
}

export interface AnalysisNarrative {
  title: string;
  opener: string;
  ratingLine: string;
  biggestLever: string;
  correlatedInsight: string;
  nextMonth: string;
  /** Present when the energy-balance prediction and the scale trend disagree; null otherwise. */
  honestyFlag: string | null;
  pillars: { label: string; score: number; line: string }[];
  clinicalNote: string;
  disclaimer: string;
  /** Flattened body for simple markdown / HTML rendering, in reading order. */
  paragraphs: string[];
}

const DISCLAIMER = 'Educational guidance, not medical advice - consult a professional for anything clinical.';

/** One grade up from the current letter (A stays A). */
function nextGradeUp(grade: string): string {
  const order = ['F', 'D', 'C', 'B', 'A'];
  const i = order.indexOf(grade.toUpperCase());
  return i >= 0 && i < order.length - 1 ? order[i + 1]! : 'A';
}

function openerFor(input: AnalysisInput): string {
  const name = input.firstName?.trim();
  const hi = name ? `${name}, ` : '';
  if (input.streakDays >= 3) {
    return `${hi}you’re on a ${input.streakDays}-day logging streak - that consistency is exactly what moves the needle.`;
  }
  if (input.weightDeltaKg != null && input.weightDeltaKg < 0) {
    return `${hi}you’re already ${Math.abs(input.weightDeltaKg)} kg down - real progress worth building on.`;
  }
  return `${hi}here’s an honest look at where you are and the one change that helps most.`;
}

function correlatedInsightFor(input: AnalysisInput): string {
  const withData = input.pillars.filter((p) => p.score > 0);
  const weakest = (withData.length ? withData : input.pillars).slice().sort((a, b) => a.score - b.score)[0];
  const up = nextGradeUp(input.grade);
  if (!weakest) return 'Keep logging across all three pillars and the score will climb on its own.';
  const move = up !== input.grade.toUpperCase() ? ` - enough to push a ${input.grade} toward an ${up}` : '';
  switch (weakest.key) {
    case 'exercise':
      return `Your Exercise pillar has the most room. Two more logged sessions a week would lift it${move}.`;
    case 'discipline':
      return `Your Discipline pillar has the most room. Logging every day - even a 30-second entry - is the cheapest points on the board${move}.`;
    default:
      return `Your Diet pillar has the most room. Tightening calorie + protein consistency is the fastest lift${move}.`;
  }
}

function nextMonthFor(input: AnalysisInput): string {
  const p = input.prediction;
  if (!p) return 'Log your meals for a couple of weeks and the trend will start to predict itself.';
  if (p.blocked) {
    return 'Weight is intentionally held steady for safety right now - the win this month is consistency, not the scale.';
  }
  if (p.projectedWeightKg != null) {
    const dir = p.projectedMonthlyDeltaKg < 0 ? 'toward' : 'around';
    return `Staying on this track points you ${dir} ~${p.projectedWeightKg} kg over the next month. ${p.note}`;
  }
  return p.note;
}

/**
 * Honest flag when the energy-balance prediction and the actual scale trend disagree - e.g. logged
 * intake implies loss but the scale is up (usually under-logging). Returns null when they agree or
 * there isn't enough data. PURE.
 */
function scaleVsIntakeFlag(input: AnalysisInput): string | null {
  const predicted = input.prediction?.projectedMonthlyDeltaKg;
  const actual = input.weightDeltaKg;
  if (predicted == null || actual == null) return null;
  // Only flag a clear contradiction (opposite directions, both non-trivial).
  const predDown = predicted < -0.2;
  const predUp = predicted > 0.2;
  const actUp = actual > 0.3;
  const actDown = actual < -0.3;
  if (predDown && actUp) {
    return 'Heads-up: your logged intake predicts a loss, but the scale is up - you may be under-logging a few things. Log everything for a week and the numbers will line up.';
  }
  if (predUp && actDown) {
    return 'Nice - the scale is moving down faster than your logged intake predicts. Keep it up; just make sure you’re eating enough to stay safe.';
  }
  return null;
}

function pillarLine(label: string, score: number): string {
  if (score >= 80) return `${label} is strong - protect this habit.`;
  if (score >= 60) return `${label} is solid with a little headroom.`;
  if (score >= 40) return `${label} is the swing factor - small daily wins here compound.`;
  return `${label} needs the most attention - start with one easy, repeatable step.`;
}

export function buildAnalysisNarrative(input: AnalysisInput): AnalysisNarrative {
  const opener = openerFor(input);
  const ratingLine = `Your overall health rating is ${input.overall}/100 - grade ${input.grade}. It weights Diet 60%, Exercise 25% and Discipline 15%, and only counts pillars you’ve given data for.`;
  const correlatedInsight = correlatedInsightFor(input);
  const nextMonth = nextMonthFor(input);
  const honestyFlag = scaleVsIntakeFlag(input);
  const pillars = input.pillars.map((p) => ({ label: p.label, score: Math.round(p.score), line: pillarLine(p.label, p.score) }));
  const clinicalNote = input.hasRiskFindings
    ? 'A couple of health signals are worth a professional’s eyes - bring them to your next check-up rather than self-treating.'
    : 'Nothing here replaces a clinician - use it to show up to check-ups better informed.';

  const paragraphs = [
    opener,
    ratingLine,
    `Biggest lever: ${input.biggestLever}`,
    correlatedInsight,
    ...pillars.map((p) => `${p.label} (${p.score}/100): ${p.line}`),
    `Next month: ${nextMonth}`,
    ...(honestyFlag ? [honestyFlag] : []),
    clinicalNote,
    DISCLAIMER,
  ];

  return {
    title: 'Your Analysis',
    opener,
    ratingLine,
    biggestLever: input.biggestLever,
    correlatedInsight,
    nextMonth,
    honestyFlag,
    pillars,
    clinicalNote,
    disclaimer: DISCLAIMER,
    paragraphs,
  };
}
