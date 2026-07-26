/**
 * Pluggable chat-AI provider abstraction. A provider turns a user message plus the
 * deterministic diet context into an LLM reply, or returns `null` to signal
 * "I can't answer - fall back to the rules engine". Providers MUST NEVER throw.
 */

export interface AiChatContext {
  /** Deterministic targets from the latest CalcResult snapshot. Never invented by the LLM. */
  targets: { dailyKcal: number; proteinG: number; waterMl: number } | null;
  /** Decrypted health conditions (e.g. 'diabetes', 'hypertension'). */
  conditions: string[];
  firstName?: string;
  /** Recent conversation for memory across sessions (oldest first). */
  history?: { role: 'user' | 'assistant'; content: string }[];
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

  lines.push(
    'NEVER invent specific calorie, macro, or nutrient numbers beyond the targets provided above.',
    'For medical questions (diagnosis, medication, symptoms), defer to a qualified professional.',
  );

  if (ctx.firstName) {
    lines.push(`The user's first name is ${ctx.firstName}.`);
  }

  return lines.join('\n');
}
