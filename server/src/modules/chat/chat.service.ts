import { prisma } from '../../lib/prisma';
import { decryptJson, encryptJson } from '../../lib/crypto';
import type { FoodItem, MealSlot } from '../food/food.types';
import { answer, type ChatReply } from './chat.engine';
import { buildCoachContext } from '../coach/coachContext';
import { getAiProvider } from '../../ai';

const DISCLAIMER =
  'This is educational guidance, not medical advice - please consult a professional.';

/** Chat content is encrypted at rest. Legacy plaintext rows (pre-encryption) are returned as-is. */
function decryptChatContent(stored: string): string {
  try {
    return decryptJson<string>(stored);
  } catch {
    return stored;
  }
}

// The disclaimer is shown ONCE in the chat UI (a persistent footer), not on every reply.
// Strip it from individual answers so the conversation isn't spammed with it.
function stripDisclaimer(text: string): string {
  return text.split(DISCLAIMER).join('').replace(/\s+$/g, '').trim();
}

function toFoodItem(f: {
  id: string; name: string; locale: string; region: string | null; category: string;
  mealSlots: string[]; kcal: number; proteinG: number; carbG: number; fatG: number;
  fiberG: number; sugarG: number; sodiumMg: number; glycemicIndex: number | null;
  typicalServingG: number; costTier: number; tags: string[]; allergens: string[];
}): FoodItem {
  return {
    id: f.id, name: f.name, locale: f.locale, region: f.region ?? undefined,
    category: f.category as FoodItem['category'], mealSlots: f.mealSlots as MealSlot[],
    kcal: f.kcal, proteinG: f.proteinG, carbG: f.carbG, fatG: f.fatG, fiberG: f.fiberG,
    sugarG: f.sugarG, sodiumMg: f.sodiumMg, glycemicIndex: f.glycemicIndex ?? undefined,
    typicalServingG: f.typicalServingG, costTier: (f.costTier as 1 | 2 | 3) ?? 2,
    tags: f.tags, allergens: f.allergens,
  };
}

/** Fuzzy-ish food matcher: name contains the term, or the term contains a name keyword. */
function makeFindFood(foods: FoodItem[]) {
  const stop = new Set(['and', 'the', 'with', 'dal', 'curry', 'cooked', 'plain']);
  return (term: string): FoodItem | undefined => {
    const t = term.toLowerCase().trim();
    if (!t) return undefined;
    let match = foods.find((f) => f.name.toLowerCase().includes(t));
    if (match) return match;
    match = foods.find((f) =>
      f.name
        .toLowerCase()
        .split(/[^a-z]+/)
        .some((w) => w.length > 2 && !stop.has(w) && t.includes(w)),
    );
    return match;
  };
}

/** Recent conversation (oldest first) so the coach has memory across sessions. */
export async function chatHistory(userId: string, take = 40) {
  const rows = await prisma.chatMessage.findMany({
    where: { userId },
    orderBy: { createdAt: 'desc' },
    take,
  });
  return rows.reverse().map((m) => ({ id: m.id, role: m.role, content: decryptChatContent(m.content), createdAt: m.createdAt }));
}

export async function chat(userId: string, message: string, firstName?: string, offsetMin = 0): Promise<ChatReply> {
  const [snapshot, profile, foods, historyRows, coach] = await Promise.all([
    prisma.calcResultSnapshot.findFirst({ where: { userId }, orderBy: { createdAt: 'desc' } }),
    prisma.profile.findUnique({ where: { userId } }),
    prisma.food.findMany(),
    prisma.chatMessage.findMany({ where: { userId }, orderBy: { createdAt: 'desc' }, take: 10 }),
    buildCoachContext(userId, offsetMin).catch(() => null),
  ]);

  const history = historyRows
    .reverse()
    .map((m) => ({ role: m.role === 'assistant' ? ('assistant' as const) : ('user' as const), content: decryptChatContent(m.content) }));

  const result = snapshot?.result as
    | { dailyKcal: number; proteinG: number; waterMl: number }
    | undefined;

  let conditions: string[] = [];
  if (profile?.sensitiveEnc) {
    try {
      conditions = decryptJson<{ conditions?: string[] }>(profile.sensitiveEnc).conditions ?? [];
    } catch {
      conditions = [];
    }
  }

  const targets = result
    ? { dailyKcal: result.dailyKcal, proteinG: result.proteinG, waterMl: result.waterMl }
    : null;

  // Run the deterministic rules engine FIRST. It owns every number (targets, today's macros,
  // trends, food frequency) — the server computes these, so the coach must never tell the user
  // "I don't have access to your log" for a question we can answer. When the rules engine gives a
  // confident data-backed answer, we return it verbatim and DO NOT ask the LLM (which would only
  // hallucinate or disclaim). The LLM is reserved for open-ended chat the rules engine can't place.
  const base = answer(message, {
    targets,
    conditions,
    findFood: makeFindFood(foods.map(toFoodItem)),
    firstName,
    coach,
  });

  // Intents that are grounded in server-computed data — always prefer these over the LLM.
  const DATA_INTENTS = new Set<ChatReply['intent']>([
    'coach_today', 'coach_trend', 'coach_frequency', 'coach_habits',
    'food_safety', 'targets', 'water', 'weight_pace',
  ]);

  let reply: ChatReply | null = null;
  if (DATA_INTENTS.has(base.intent)) {
    reply = { ...base, reply: stripDisclaimer(base.reply) };
  } else {
    // Open-ended (greeting/help/fallback): let the LLM answer naturally if configured, still fed the
    // full coach context so any numbers it cites are the real ones. Fall back to the rules reply.
    const provider = getAiProvider();
    if (provider) {
      const llm = await provider.chatReply(message, { targets, conditions, firstName, history, coach });
      if (llm && llm.trim()) {
        reply = { intent: 'llm', reply: stripDisclaimer(llm.trim()), sources: [] };
      }
    }
    if (!reply) reply = { ...base, reply: stripDisclaimer(base.reply) };
  }

  // Persist the exchange so the coach remembers it next time.
  await prisma.chatMessage.createMany({
    data: [
      { userId, role: 'user', content: encryptJson(message.slice(0, 2000)) },
      { userId, role: 'assistant', content: encryptJson(reply.reply.slice(0, 2000)) },
    ],
  });
  await prisma.auditLog.create({ data: { userId, action: 'chat.query', detail: reply.intent } });
  return reply;
}
