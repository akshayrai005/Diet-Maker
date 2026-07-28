/**
 * Pluggable chat-AI provider abstraction. A provider turns a user message plus the
 * deterministic diet context into an LLM reply, or returns `null` to signal
 * "I can't answer - fall back to the rules engine". Providers MUST NEVER throw.
 */

import type { CoachContext } from '../modules/coach/coachContext';

export interface AiChatContext {
  /** Deterministic targets from the latest CalcResult snapshot. Never invented by the LLM. */
  targets: { dailyKcal: number; proteinG: number; waterMl: number } | null;
  /** Decrypted health conditions (e.g. 'diabetes', 'hypertension'). */
  conditions: string[];
  firstName?: string;
  /** Recent conversation for memory across sessions (oldest first). */
  history?: { role: 'user' | 'assistant'; content: string }[];
  /** Whole-app snapshot (today's macros, trends, food frequency) the coach may cite verbatim. */
  coach?: CoachContext | null;
}

export interface AiProvider {
  /** Returns the LLM reply text, or `null` to fall back to the rules engine. */
  chatReply(message: string, ctx: AiChatContext): Promise<string | null>;
}

/**
 * Builds the NutriAI diet-coach system prompt. The known targets and conditions are
 * injected so the model can reference them, with an explicit instruction never to
 * invent calorie/nutrient numbers beyond what is provided.
 */
export function buildSystemPrompt(ctx: AiChatContext): string {
  const lines: string[] = [
    'You are NutriAI, a friendly, practical diet coach.',
    'Give concise, actionable diet and nutrition guidance in plain language.',
    'Keep answers short - a few sentences at most.',
  ];

  if (ctx.targets) {
    lines.push(
      `The user's known daily targets are approximately ${ctx.targets.dailyKcal} kcal, ` +
        `${ctx.targets.proteinG} g protein, and ${ctx.targets.waterMl} ml water. ` +
        'Use ONLY these numbers when referring to their targets.',
    );
  } else {
    lines.push(
      'The user has no calculated targets yet. Do not state any specific calorie or ' +
        'protein numbers - suggest they complete their profile and run the calculator.',
    );
  }

  if (ctx.conditions.length > 0) {
    lines.push(
      `The user has these health conditions: ${ctx.conditions.join(', ')}. ` +
        'Tailor food guidance accordingly.',
    );
  }

  const snapshot = buildKnowledgeBlock(ctx.coach);
  if (snapshot) lines.push(snapshot);

  lines.push(
    'NEVER invent specific calorie, macro, or nutrient numbers beyond the targets and the ' +
      '"WHAT I KNOW ABOUT YOU RIGHT NOW" facts provided above. If a fact is not listed there, ' +
      'say you do not have it logged rather than guessing.',
    'For medical questions (diagnosis, medication, symptoms), defer to a qualified professional.',
  );

  if (ctx.firstName) {
    lines.push(`The user's first name is ${ctx.firstName}.`);
  }

  return lines.join('\n');
}

/**
 * Renders the deterministic coach snapshot into a facts block the LLM may quote verbatim but must
 * never exceed. Every number here was computed by the server. Empty domains are omitted.
 */
function buildKnowledgeBlock(coach: CoachContext | null | undefined): string | null {
  if (!coach) return null;
  const facts: string[] = [];

  if (coach.today) {
    const t = coach.today;
    const left = t.remainingKcal != null ? `, ${t.remainingKcal} kcal remaining of ${t.targetKcal}` : '';
    facts.push(
      `- Today so far: ${t.kcal} kcal, ${t.proteinG} g protein, ${t.carbG} g carbs, ${t.fatG} g fat, ` +
        `${t.fiberG} g fibre, ${t.sugarG} g sugar, ${t.sodiumMg} mg sodium, ${t.waterMl} ml water${left}. ` +
        `Logging streak: ${t.streakDays} day(s).`,
    );
  }
  if (coach.todayFoods && coach.todayFoods.length > 0) {
    facts.push(`- Logged today: ${coach.todayFoods.map((f) => `${f.name} (${f.grams} g)`).join(', ')}.`);
  }
  const trend = (label: string, tr: CoachContext['week']) => {
    if (!tr || tr.daysLogged === 0) return;
    facts.push(`- Last ${tr.windowDays} days (${label}): logged ${tr.daysLogged} day(s), avg ${tr.avgKcal} kcal & ${tr.avgProteinG} g protein, ${tr.workoutDays} workout day(s), trend ${tr.direction}.`);
  };
  trend('week', coach.week);
  trend('month', coach.month);
  if (coach.topFoods && coach.topFoods.length > 0) {
    const top = coach.topFoods.map((f) => `${f.name} ${f.times}×${f.unhealthy ? ' (habit to watch)' : ''}`).join(', ');
    facts.push(`- Most-logged foods all-time: ${top}.`);
  }
  if (coach.deficiencies && coach.deficiencies.length > 0) {
    facts.push(`- Micronutrients running low today: ${coach.deficiencies.join(', ')}.`);
  }
  if (coach.exercise) {
    const e = coach.exercise;
    const state = e.rest ? 'rest day' : e.todayDone ? `trained today${e.todayFocus ? ` (${e.todayFocus})` : ''}` : e.todayScheduled ? `workout scheduled today${e.todayFocus ? ` (${e.todayFocus})` : ''}, not yet done` : 'no workout scheduled today';
    facts.push(`- Training: ${state}; ${e.weeklyWorkouts} session(s) in the last 7 days, lifts ${e.overloadTrend === 'up' ? 'progressing' : e.overloadTrend === 'down' ? 'dipping' : 'flat'}.`);
  }
  if (coach.weightDeltaKg != null && coach.weightDeltaKg !== 0) {
    facts.push(`- Weight change over check-ins: ${coach.weightDeltaKg > 0 ? '+' : ''}${coach.weightDeltaKg} kg${coach.weightKg != null ? ` (now ${coach.weightKg} kg)` : ''}.`);
  }
  if (coach.mind && (coach.mind.mood != null || coach.mind.sleepQuality != null)) {
    const m = coach.mind;
    const parts = [m.mood != null ? `mood ${m.mood}/5` : null, m.stress != null ? `stress ${m.stress}/5` : null, m.sleepQuality != null ? `sleep ${m.sleepQuality}/5` : null].filter(Boolean);
    if (parts.length) facts.push(`- Recent wellness: ${parts.join(', ')}.`);
  }
  // NOTE: medications and cycle phase are intentionally NOT included here — they drive the
  // deterministic food scorer only and must never be echoed into the LLM prompt.

  if (facts.length === 0) return null;
  return ['WHAT I KNOW ABOUT YOU RIGHT NOW (server-computed facts — cite these, never contradict or exceed them):', ...facts].join('\n');
}
