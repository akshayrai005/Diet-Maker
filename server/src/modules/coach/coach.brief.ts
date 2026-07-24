/**
 * Deterministic, templated 4-pillar daily coach brief — PURE. No DB, no Date.now, no randomness,
 * no LLM. Every field is a fixed template over server-computed facts, so it works with
 * AI_PROVIDER=rules and never invents a number. An LLM layer (if enabled) may only rephrase these
 * exact facts; this templated output is always the fallback and the source of truth.
 */

export interface CoachBriefInput {
  firstName?: string | null;
  /** Local hour 0–23, passed in so this stays pure/deterministic. */
  hour: number;
  overall: number;
  grade: string;
  biggestLever: string;
  adherenceTopActions: string[];
  streakDays: number;
  todayWorkoutScheduled: boolean;
  rest: boolean;
  todayFocus: string | null;
  todayWorkoutDone: boolean;
  todayWellnessDone: boolean;
  weightDeltaKg: number | null;
  projection: { label: string; weeks: number; weightKg: number }[];
  calorieRemaining: number | null;
}

export interface CoachBrief {
  greeting: string;
  ratingSummary: string;
  dietFocus: string;
  moveOrRest: string;
  mindNudge: string;
  disciplineActions: string[];
  streak: string;
  prediction: string;
  disclaimer: string;
}

const DISCLAIMER = 'Educational guidance, not medical advice — consult a professional.';

function greetingFor(hour: number, name?: string | null): string {
  const part = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
  return name && name.trim() ? `${part}, ${name.trim()}.` : `${part}.`;
}

function dietFocusFor(remaining: number | null): string {
  if (remaining == null) return 'Log your meals to track today’s calories.';
  if (remaining > 200) return `You have ~${remaining} kcal left today — make them count with protein + veg.`;
  if (remaining < -200) return `You’re ~${Math.abs(remaining)} kcal over — go lighter tonight and add a glass of water.`;
  return 'Right on target for calories — keep the balance.';
}

function moveFor(input: CoachBriefInput): string {
  if (input.rest) return 'Rest & recovery day — a light walk and some stretching is plenty.';
  if (input.todayWorkoutDone) return 'Workout logged today — nicely done. ✓';
  if (input.todayWorkoutScheduled) {
    return input.todayFocus
      ? `Today’s session: ${input.todayFocus}. Log your sets in Move.`
      : 'You have a session today — log your sets in Move.';
  }
  return 'No session scheduled today — a brisk 10-minute walk still counts.';
}

function mindFor(done: boolean): string {
  return done
    ? 'Mind session done — a good reset. ✓'
    : 'Take 4 minutes for box breathing in Move › Meditation.';
}

function predictionFor(projection: CoachBriefInput['projection'], deltaKg: number | null): string {
  const pick = projection.find((p) => p.weeks >= 3 && p.weeks <= 5) ?? projection[projection.length - 1];
  if (pick) {
    const base = `On this track you’re heading toward ~${pick.weightKg} kg in about ${pick.weeks} weeks.`;
    if (deltaKg != null && deltaKg !== 0) {
      const dir = deltaKg < 0 ? 'down' : 'up';
      return `${base} You’re already ${Math.abs(deltaKg)} kg ${dir} — momentum is on your side.`;
    }
    return base;
  }
  return 'Keep going — your trend takes a few weeks of logging to show, and it will.';
}

export function buildCoachBrief(input: CoachBriefInput): CoachBrief {
  const actions = input.adherenceTopActions.filter((a) => a && a.trim()).slice(0, 2);
  return {
    greeting: greetingFor(input.hour, input.firstName),
    ratingSummary: `Your health rating is ${input.overall}/100 (grade ${input.grade}). Biggest lever: ${input.biggestLever}`,
    dietFocus: dietFocusFor(input.calorieRemaining),
    moveOrRest: moveFor(input),
    mindNudge: mindFor(input.todayWellnessDone),
    disciplineActions: actions.length > 0 ? actions : ['Keep logging today to build your streak.'],
    streak: input.streakDays > 0
      ? `🔥 ${input.streakDays}-day logging streak — keep it alive.`
      : 'Log something today to start a streak.',
    prediction: predictionFor(input.projection, input.weightDeltaKg),
    disclaimer: DISCLAIMER,
  };
}
