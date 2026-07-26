import { FoodItem } from '../food/food.types';

export interface ChatContext {
  targets: { dailyKcal: number; proteinG: number; waterMl: number } | null;
  conditions: string[];
  /** Substring lookup over the food DB. */
  findFood: (term: string) => FoodItem | undefined;
  firstName?: string;
}

export interface ChatReply {
  intent:
    | 'greeting'
    | 'food_safety'
    | 'targets'
    | 'weight_pace'
    | 'water'
    | 'help'
    | 'fallback'
    | 'llm';
  reply: string;
  sources: string[];
}

const DISCLAIMER = 'This is educational guidance, not medical advice - please consult a professional.';

function withDisclaimer(text: string): string {
  return `${text}\n\n${DISCLAIMER}`;
}

const has = (arr: string[], v: string) => arr.includes(v);

/** Assess a food against the user's conditions and return portion guidance. */
function assessFood(food: FoodItem, conditions: string[]): string {
  const cautions: string[] = [];
  const tags = food.tags;

  if (
    has(conditions, 'diabetes') &&
    (tags.includes('high-sugar') ||
      tags.includes('high-gi') ||
      food.sugarG >= 10 ||
      (food.glycemicIndex ?? 0) >= 55)
  ) {
    cautions.push('it is high in sugar / glycaemic load, so keep the portion small and pair it with protein or fibre to blunt the glucose spike');
  }
  if ((has(conditions, 'hypertension') || has(conditions, 'heart_disease')) && (tags.includes('high-sodium') || food.sodiumMg >= 400)) {
    cautions.push('it is relatively high in sodium, so watch the quantity');
  }
  if (has(conditions, 'heart_disease') && tags.includes('high-satfat')) {
    cautions.push('it is high in saturated fat - keep it occasional');
  }
  if (has(conditions, 'gout') && tags.includes('high-purine')) {
    cautions.push('it is high in purines, which can aggravate gout - limit it');
  }
  if (has(conditions, 'kidney_disease') && (tags.includes('high-potassium') || tags.includes('high-phosphorus'))) {
    cautions.push('it is high in potassium/phosphorus - check with your care team about the amount');
  }

  const serving = `A typical serving is about ${food.typicalServingG} g (~${Math.round((food.kcal * food.typicalServingG) / 100)} kcal, ${Math.round((food.proteinG * food.typicalServingG) / 100)} g protein).`;

  if (cautions.length > 0) {
    return `${food.name}: yes, in moderation - but ${cautions.join('; ')}. ${serving}`;
  }
  return `${food.name}: yes, that fits a balanced plan. ${serving}`;
}

/** Extracts a likely food term from a "can I eat X" style question. */
function extractFoodTerm(msg: string): string | undefined {
  const patterns = [
    /can i (?:eat|have|take|drink)\s+(?:some\s+|a\s+|an\s+)?([a-z\s]+?)\??$/i,
    /is\s+([a-z\s]+?)\s+(?:ok|okay|good|safe|healthy|fine|allowed)/i,
    /should i (?:eat|avoid|have)\s+([a-z\s]+?)\??$/i,
  ];
  for (const p of patterns) {
    const m = msg.match(p);
    if (m?.[1]) return m[1].trim();
  }
  return undefined;
}

/**
 * Deterministic rules-based diet chat. No LLM: intent detection + food-DB lookups +
 * guardrail-aware templates. Every answer carries the disclaimer.
 */
export function answer(message: string, ctx: ChatContext): ChatReply {
  const msg = message.toLowerCase().trim();
  const name = ctx.firstName ? ` ${ctx.firstName}` : '';

  if (/^(hi|hello|hey|namaste|good (morning|evening|afternoon))\b/.test(msg)) {
    return {
      intent: 'greeting',
      reply: withDisclaimer(`Hello${name}! Ask me things like "how much protein should I eat?", "can I eat mango?", or "how fast can I lose weight?".`),
      sources: [],
    };
  }

  // Food-safety questions.
  const term = extractFoodTerm(msg);
  if (term) {
    const food = ctx.findFood(term);
    if (food) {
      return { intent: 'food_safety', reply: withDisclaimer(assessFood(food, ctx.conditions)), sources: [food.id] };
    }
    return {
      intent: 'food_safety',
      reply: withDisclaimer(`I don't have "${term}" in the food database yet. As a rule: favour whole, minimally-processed versions, watch the portion, and log it so your dashboard stays accurate.`),
      sources: [],
    };
  }

  if (/water|hydrat/.test(msg)) {
    const w = ctx.targets?.waterMl;
    return {
      intent: 'water',
      reply: withDisclaimer(w ? `Aim for about ${w} ml of water a day (roughly ${(w / 250).toFixed(1)} glasses). Spread it across the day and more around exercise.` : 'Aim for roughly 30-35 ml of water per kg of body weight per day. Complete your profile for a personalised target.'),
      sources: [],
    };
  }

  if (/protein|calorie|kcal|carb|fat|macro|target/.test(msg)) {
    if (!ctx.targets) {
      return { intent: 'targets', reply: withDisclaimer('Complete your health profile and run the calculator, then I can give you exact calorie and protein targets.'), sources: [] };
    }
    return {
      intent: 'targets',
      reply: withDisclaimer(`Your daily targets are about ${ctx.targets.dailyKcal} kcal and ${ctx.targets.proteinG} g of protein. Hit the protein first - it protects muscle and keeps you full.`),
      sources: [],
    };
  }

  if (/lose weight|weight loss|how (fast|quick)|per week|weekly/.test(msg)) {
    return {
      intent: 'weight_pace',
      reply: withDisclaimer('A safe pace is up to about 0.75% of your body weight per week. Faster than that risks muscle loss and rebound. Your plan already caps the deficit and raises calories to a safe floor if needed.'),
      sources: [],
    };
  }

  if (/help|what can you|how do you/.test(msg)) {
    return {
      intent: 'help',
      reply: withDisclaimer('I can check whether a specific food fits your plan, tell you your calorie/protein/water targets, and explain a safe weight-loss pace. For medical questions, please see a professional.'),
      sources: [],
    };
  }

  return {
    intent: 'fallback',
    reply: withDisclaimer(`I'm a rules-based diet assistant${name}. Try asking about a specific food ("can I eat paneer?"), your targets ("how much protein?"), or weight-loss pace.`),
    sources: [],
  };
}
