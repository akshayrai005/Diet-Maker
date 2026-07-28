import { FoodItem, type MealSlot } from '../food/food.types';
import type { CoachContext } from '../coach/coachContext';
import { assessFoodSuitability, rankSuitable, type FoodSituation } from '../food/suitability';

export interface ChatContext {
  targets: {
    dailyKcal: number;
    proteinG: number;
    waterMl: number;
    /** Optional richer plan numbers so the coach can explain the full macro split + deficit. */
    carbG?: number;
    fatG?: number;
    fiberG?: number;
    tdee?: number;
    bmr?: number;
    safeWeeklyDeltaKg?: number;
    goal?: string;
  } | null;
  conditions: string[];
  /** Substring lookup over the food DB. */
  findFood: (term: string) => FoodItem | undefined;
  firstName?: string;
  /** Whole-app snapshot the coach answers "what did I eat / how's my week" from. Null = degrade gracefully. */
  coach?: CoachContext | null;
  /** Full food catalog — powers "what should I eat now" suggestions. */
  foods?: FoodItem[];
  /** User's diet pattern (veg/nonveg/…) for the suitability scorer. */
  dietType?: string;
  /** Meal slot for the current time of day (server-computed), used when the user doesn't name one. */
  nowSlot?: MealSlot;
}

export interface ChatReply {
  intent:
    | 'greeting'
    | 'food_safety'
    | 'targets'
    | 'weight_pace'
    | 'water'
    | 'help'
    | 'coach_today'
    | 'coach_trend'
    | 'coach_frequency'
    | 'coach_habits'
    | 'coach_plan'
    | 'coach_suggest'
    | 'coach_exercise'
    | 'coach_mind'
    | 'fallback'
    | 'llm';
  reply: string;
  sources: string[];
}

const DISCLAIMER = 'This is educational guidance, not medical advice - please consult a professional.';

function withDisclaimer(text: string): string {
  return `${text}\n\n${DISCLAIMER}`;
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

const normName = (s: string) => s.toLowerCase().trim().replace(/\s+/g, ' ');

/** "you've eaten samosa 3 times" — exact lifetime count from the pre-computed frequency list. */
function coachFrequencyReply(query: string, coach: CoachContext): string | null {
  const list = coach.frequency;
  if (!list || list.length === 0) return null;
  const q = normName(query);
  if (!q) return null;
  const hit =
    list.find((f) => normName(f.name) === q) ??
    list.find((f) => normName(f.name).includes(q)) ??
    list.find((f) => q.includes(normName(f.name)));
  if (!hit) return `You haven't logged "${query}" yet, so I have no count for it. Once you log it, I'll track how often it shows up.`;
  const since = hit.firstAt.slice(0, 10);
  const habit = hit.unhealthy && hit.times >= 3 ? ` That's one of your more frequent treat foods — worth keeping an eye on.` : '';
  const rate = hit.perWeekAvg > 0 ? ` (about ${hit.perWeekAvg}/week since ${since})` : '';
  return `You've logged ${hit.name} ${hit.times} ${hit.times === 1 ? 'time' : 'times'}${rate}.${habit}`;
}

/** Today's consumed macros — the app computed these; never say "I can't access your log". */
function coachTodayReply(coach: CoachContext): string | null {
  const t = coach.today;
  if (!t) return null;
  const parts = [
    `${t.kcal} kcal`,
    `${t.proteinG} g protein`,
    `${t.carbG} g carbs`,
    `${t.fatG} g fat`,
  ];
  let out = `So far today you've logged ${parts.join(', ')}.`;
  if (t.targetKcal != null && t.remainingKcal != null) {
    out += t.remainingKcal >= 0 ? ` You have about ${t.remainingKcal} kcal left of your ${t.targetKcal} target.` : ` You're about ${Math.abs(t.remainingKcal)} kcal over your ${t.targetKcal} target.`;
  }
  return out;
}

function coachTrendReply(coach: CoachContext, monthly: boolean): string | null {
  const tr = monthly ? coach.month : coach.week;
  if (!tr) return null;
  const label = monthly ? 'month' : 'week';
  if (tr.daysLogged === 0) return `You haven't logged any food in the last ${tr.windowDays} days, so there's no ${label} trend yet. Log a few days and I'll show you how it's going.`;
  const dir =
    tr.direction === 'improving' ? "and your protein habit is trending up — nice work" :
    tr.direction === 'slipping' ? "though your protein has been slipping lately" :
    tr.direction === 'steady' ? "holding fairly steady" :
    "though it's still early to call a trend";
  // Weight change over the tracked check-ins (GAP 5) — only when we actually have it.
  let weight = '';
  if (coach.weightDeltaKg != null && coach.weightDeltaKg !== 0) {
    const d = coach.weightDeltaKg;
    weight = ` Your weight has ${d < 0 ? 'dropped' : 'gone up'} ${Math.abs(d)} kg over your check-ins${coach.weightKg != null ? ` (now ${coach.weightKg} kg)` : ''}.`;
  }
  return `Over the last ${tr.windowDays} days you logged food on ${tr.daysLogged} ${tr.daysLogged === 1 ? 'day' : 'days'}, averaging ${tr.avgKcal} kcal and ${tr.avgProteinG} g protein, with ${tr.workoutDays} workout ${tr.workoutDays === 1 ? 'day' : 'days'} — ${dir}.${weight}`;
}

function coachHabitsReply(coach: CoachContext): string | null {
  const top = coach.topFoods;
  if (!top || top.length === 0) return `You don't have enough logged history yet for me to spot repeat habits. Keep logging and I'll flag anything you lean on too often.`;
  const watch = top.filter((f) => f.unhealthy && f.times >= 3).slice(0, 3);
  const frequent = top.slice(0, 3).map((f) => `${f.name} (${f.times}×)`).join(', ');
  if (watch.length === 0) return `Your most-logged foods are ${frequent} — nothing there stands out as a habit to watch. Keep it up.`;
  const watchStr = watch.map((f) => `${f.name} (${f.times}×)`).join(', ');
  return `Your most-logged foods are ${frequent}. The ones worth watching: ${watchStr} — these carry refined/fried/high-sugar tags, so try swapping in a whole-food option a few times a week.`;
}

/**
 * Assemble the deterministic FoodSituation the suitability scorer consumes — from the coach context
 * (conditions, allergies, budget, kitchen, meds, cycle, deficiencies, remaining macros) + profile.
 * PG users with no explicit available-food list fall back to the assemble-only staples.
 */
function buildSituation(ctx: ChatContext, slot?: MealSlot, microGaps?: string[]): FoodSituation {
  const c = ctx.coach ?? null;
  return {
    dietType: ctx.dietType,
    conditions: ctx.conditions,
    allergies: c?.allergies ?? [],
    budgetTier: c?.budgetTier ?? undefined,
    pantryTags: c?.pantryTags ?? undefined,
    kitchen: c?.kitchen ?? undefined,
    availableFoodIds: c?.availableFoodIds ?? undefined,
    medications: c?.medications ?? undefined,
    cyclePhase: c?.cyclePhase ?? undefined,
    microGaps: microGaps ?? c?.deficiencies ?? undefined,
    slot,
  };
}

/** Nutrient the user explicitly asked to top up, mapped to a coach-deficiency label if present. */
function requestedNutrient(msg: string): string | null {
  const map: Record<string, string> = {
    iron: 'iron', calcium: 'calcium', protein: 'protein', fibre: 'fibre', fiber: 'fibre',
    potassium: 'potassium', magnesium: 'magnesium', zinc: 'zinc', b12: 'b12', vitamin: 'vitamin',
  };
  for (const key of Object.keys(map)) if (msg.includes(key)) return map[key]!;
  return null;
}

/** Slot named in the message, if any. */
function slotFromMessage(msg: string): MealSlot | undefined {
  if (/breakfast/.test(msg)) return 'breakfast';
  if (/lunch/.test(msg)) return 'lunch';
  if (/dinner/.test(msg)) return 'dinner';
  if (/snack/.test(msg)) return 'eveningsnack';
  if (/bed ?time|before (bed|sleep)/.test(msg)) return 'bedtime';
  return undefined;
}

/** True for "suggest me a food" style questions (vs "can I eat X" which is a safety check). */
function isSuggestQuestion(msg: string): boolean {
  return (
    /\bwhat (should|can|could) i eat\b/.test(msg) ||
    /\bwhat to eat\b/.test(msg) ||
    /\bwhat do i eat now\b/.test(msg) ||
    /\b(suggest|recommend|give me|any)\b.*\b(snack|meal|food|breakfast|lunch|dinner|option|options|something|dish|idea|ideas)\b/.test(msg) ||
    /\bsomething (to eat|for my|healthy|cheap|quick|light|filling|no-?cook)\b/.test(msg) ||
    /\b(no-?cook|assemble-?only|without cooking)\b.*\b(option|food|meal|snack|eat)\b/.test(msg)
  );
}

/** "What should I eat?" — 2-3 ranked, situation-appropriate picks with portion + why. */
function coachSuggestReply(ctx: ChatContext, msg: string): string | null {
  const foods = ctx.foods;
  if (!foods || foods.length === 0) return null;
  const nutrient = requestedNutrient(msg);
  const microGaps = nutrient ? [nutrient] : undefined;
  const slot = slotFromMessage(msg) ?? ctx.nowSlot;
  const situation = buildSituation(ctx, slot, microGaps);
  const picks = rankSuitable(foods, situation, { slot, limit: 3 });
  if (picks.length === 0) {
    return `I couldn't find a food that fits all your constraints right now. Tell me what you have on hand and I'll work from that.`;
  }
  const lines = picks.map((p) => {
    const why = p.reasons[0] ? ` — ${p.reasons[0]}` : '';
    return `• ${p.food.name} (~${p.portionG} g, ${p.kcalForPortion} kcal)${why}`;
  });
  const lead = nutrient ? `For more ${nutrient}, try:` : slot ? `Good ${slot} options:` : `Here's what fits you right now:`;
  return `${lead}\n${lines.join('\n')}`;
}

/** "Did I train today / how's my training." */
function coachExerciseReply(ctx: ChatContext): string | null {
  const e = ctx.coach?.exercise;
  if (!e) return null;
  if (e.rest) return `Today is a rest day on your plan — recovery is where the gains land. ${e.weeklyWorkouts} workout${e.weeklyWorkouts === 1 ? '' : 's'} in the last 7 days.`;
  const trendWord = e.overloadTrend === 'up' ? 'your lifts are trending up — keep adding a little each week' : e.overloadTrend === 'down' ? 'your lifts have dipped lately — check recovery and protein' : 'your lifts are holding steady';
  if (e.todayDone) return `Yes — you trained today${e.todayFocus ? ` (${e.todayFocus})` : ''}. ${e.weeklyWorkouts} session${e.weeklyWorkouts === 1 ? '' : 's'} this week, and ${trendWord}.`;
  if (e.todayScheduled) return `You've got a workout scheduled today${e.todayFocus ? ` — ${e.todayFocus}` : ''}, not logged yet. ${e.weeklyWorkouts} done in the last 7 days.`;
  return `No workout scheduled today. You've trained ${e.weeklyWorkouts} time${e.weeklyWorkouts === 1 ? '' : 's'} in the last 7 days, and ${trendWord}.`;
}

/** "How's my mood / sleep / stress lately." */
function coachMindReply(ctx: ChatContext): string | null {
  const m = ctx.coach?.mind;
  if (!m) return null;
  const bits: string[] = [];
  if (m.mood != null) bits.push(`mood ${m.mood}/5`);
  if (m.stress != null) bits.push(`stress ${m.stress}/5`);
  if (m.sleepQuality != null) bits.push(`sleep quality ${m.sleepQuality}/5`);
  const latest = bits.length ? `Your latest check-in: ${bits.join(', ')}.` : '';
  const insight = m.insight ? ` ${m.insight}` : '';
  if (!latest && !insight) return null;
  return `${latest}${insight}`.trim();
}

/** Prescriptive plan: what the body needs, the fat-loss target, and the full macro split. */
function coachPlanReply(t: NonNullable<ChatContext['targets']>): string {
  const lines: string[] = [];
  if (t.tdee) lines.push(`Your body burns roughly ${t.tdee} kcal a day at your activity level — that's maintenance.`);

  const deficit = t.tdee ? t.tdee - t.dailyKcal : 0;
  const pace = t.safeWeeklyDeltaKg ? Math.abs(t.safeWeeklyDeltaKg) : 0;
  if (t.goal === 'lose' && deficit > 0) {
    lines.push(`To lose fat, eat about ${t.dailyKcal} kcal a day — a ${deficit} kcal deficit${pace ? `, a safe ~${pace} kg/week` : ''}.`);
  } else if (t.goal === 'gain' && deficit < 0) {
    lines.push(`To gain, eat about ${t.dailyKcal} kcal a day — a ${Math.abs(deficit)} kcal surplus${pace ? `, ~${pace} kg/week` : ''}.`);
  } else {
    lines.push(`Your daily calorie target is about ${t.dailyKcal} kcal.`);
  }

  const macros = [`protein ${t.proteinG} g`];
  if (t.carbG != null) macros.push(`carbs ${t.carbG} g`);
  if (t.fatG != null) macros.push(`fat ${t.fatG} g`);
  if (t.fiberG != null) macros.push(`fibre ${t.fiberG} g`);
  lines.push(`Daily macros: ${macros.join(', ')}. Hit protein first — it protects muscle in a deficit — then fill the rest with carbs and fat.`);
  return lines.join(' ');
}

/** True for prescriptive "what should my targets/plan be" questions (vs "what did I log"). */
function isPlanQuestion(msg: string): boolean {
  // A plan question is about numbers (calories / macros), never bare "lose weight" (that's a pace
  // question) and never "…today" (retrospective). It also must be PLAN-LEVEL — a full calorie/macro
  // picture — so a concise single-macro ask ("how much protein?") still gets the short targets reply.
  const retrospective = /\b(today|so far|yesterday|logged|i (ate|had|took|consumed) )\b/.test(msg);
  if (retrospective) return false;

  const fatLoss = /(to |for )?(lose|loose|reduce|cut|burn)\b.*(fat|weight)|calorie deficit|maintenance/.test(msg);
  const mentionsCalories = /(calorie|kcal|energy|deficit|maintenance|tdee|\bplan\b|whole plan|exact plan)/.test(msg);
  const macroWords = ['carb', 'fat', 'protein', 'fibre', 'fiber', 'macro'].filter((m) => msg.includes(m)).length;

  // "how fast / how long / per week" with no calorie or macro context is a PACE question — leave it
  // to the weight-pace intent, don't answer with a macro plan.
  const paceOnly = /\b(how (fast|quick|long)|per week|weekly|how many weeks?)\b/.test(msg);
  if (paceOnly && !mentionsCalories && macroWords < 2) return false;
  const planLevel = mentionsCalories || fatLoss || macroWords >= 2;
  if (!planLevel) return false;

  const prescriptive = /\b(should|need|require|requires|target|goal|plan|maintenance|tdee|recommend|ideal|how much|how many|want|give me)\b/.test(msg);
  return prescriptive || fatLoss || mentionsCalories;
}

/**
 * Deterministic rules-based diet chat. No LLM: intent detection + food-DB lookups +
 * guardrail-aware templates. Every answer carries the disclaimer.
 */
export function answer(message: string, ctx: ChatContext): ChatReply {
  const msg = message.toLowerCase().trim();
  const name = ctx.firstName ? ` ${ctx.firstName}` : '';
  const coach = ctx.coach ?? null;

  if (/^(hi|hello|hey|namaste|good (morning|evening|afternoon))\b/.test(msg)) {
    return {
      intent: 'greeting',
      reply: withDisclaimer(`Hello${name}! Ask me things like "how much protein should I eat?", "can I eat mango?", or "how fast can I lose weight?".`),
      sources: [],
    };
  }

  // "What should I eat (now / for breakfast / for my iron / something cheap / no-cook)" — ranked,
  // situation-aware picks. Runs before food-safety so "what should I eat" isn't read as a food name.
  if (ctx.foods && isSuggestQuestion(msg)) {
    const r = coachSuggestReply(ctx, msg);
    if (r) return { intent: 'coach_suggest', reply: withDisclaimer(r), sources: [] };
  }

  // Prescriptive plan question ("how many calories/carbs/fat to lose fat", "my macro targets").
  // Before food-safety so "…to lose fat" isn't misread as a food, and before the retrospective
  // branches so a plan question never gets answered with today's totals. Deterministic calc targets.
  if (ctx.targets && isPlanQuestion(msg)) {
    return { intent: 'coach_plan', reply: withDisclaimer(coachPlanReply(ctx.targets)), sources: [] };
  }

  // Food-safety questions — now scored by the full suitability engine (conditions + allergies +
  // budget + kitchen + medications + cycle phase + today's remaining calories).
  const FILLER_TERMS = new Set(['now', 'something', 'anything', 'more', 'food', 'it', 'this', 'that', 'a snack', 'snack', 'to lose fat', 'to loose fat', 'to eat']);
  const term = extractFoodTerm(msg);
  if (term && !FILLER_TERMS.has(term.toLowerCase().trim())) {
    const food = ctx.findFood(term);
    if (food) {
      const v = assessFoodSuitability(food, buildSituation(ctx));
      const verdictWord = v.verdict === 'good' ? 'yes, that fits your plan' : v.verdict === 'moderate' ? 'yes, but in moderation' : 'best avoided for you';
      // Drop the scorer's generic "fits your plan" placeholder — the verdict already says that.
      const realReasons = v.reasons.filter((r) => r !== 'fits your plan');
      const reasons = realReasons.length ? ` — ${realReasons.join('; ')}` : '';
      const left = ctx.coach?.today?.remainingKcal;
      const budgetNote = v.verdict !== 'avoid' && left != null && left > 0 && v.kcalForPortion > left
        ? ` A ${v.portionG} g serving is ~${v.kcalForPortion} kcal, but you only have about ${left} kcal left today — keep it small.`
        : ` A typical serving is about ${v.portionG} g (~${v.kcalForPortion} kcal).`;
      return { intent: 'food_safety', reply: withDisclaimer(`${food.name}: ${verdictWord}${reasons}.${budgetNote}`), sources: [food.id] };
    }
    return {
      intent: 'food_safety',
      reply: withDisclaimer(`I don't have "${term}" in the food database yet. As a rule: favour whole, minimally-processed versions, watch the portion, and log it so your dashboard stays accurate.`),
      sources: [],
    };
  }

  // ---- Coach whole-app context: answer from what the user actually logged ----
  if (coach) {
    // "did I train today / how's my training / my workout"
    if (/(did i (train|work ?out|exercise|lift)|have i (trained|worked ?out|exercised))|how('?s| is| was) my (training|workout|exercise|gym)|my (training|workout|gym) (today|this week|going)|(train|work ?out|gym) today/.test(msg)) {
      const r = coachExerciseReply(ctx);
      if (r) return { intent: 'coach_exercise', reply: withDisclaimer(r), sources: [] };
    }

    // "how's my mood / sleep / stress lately"
    if (/(how('?s| is| has| have)|hows).*(mood|sleep|stress|mental|feeling)|my (mood|sleep|stress).*(lately|recently|this week|been)|am i sleeping (well|enough)/.test(msg)) {
      const r = coachMindReply(ctx);
      if (r) return { intent: 'coach_mind', reply: withDisclaimer(r), sources: [] };
    }

    // "how many times have I eaten X" / "how often do I eat X"
    const freqMatch = msg.match(/how (?:many times|often) (?:have i |do i |did i )?(?:eat|eaten|ate|log|logged|had|have)\s+(?:some\s+|a\s+|an\s+)?([a-z\s]+?)\??$/i);
    if (freqMatch?.[1]) {
      const r = coachFrequencyReply(freqMatch[1].trim(), coach);
      if (r) return { intent: 'coach_frequency', reply: withDisclaimer(r), sources: [] };
    }

    // "what do I eat too often / my bad habits / what should I cut down"
    if (/(too often|too much|too many times|bad habit|eat.*a lot|cut down|junk|unhealthy).*(eat|food|habit)|(what|which).*(eat|food).*(too often|too much|often)|habits? to watch/.test(msg)) {
      const r = coachHabitsReply(coach);
      if (r) return { intent: 'coach_habits', reply: withDisclaimer(r), sources: [] };
    }

    // "how's my week / month / diet lately / trend"
    if (/(how('?s| is| has| am i doing)|hows).*(week|7 days|month|30 days|lately|recently|doing|going|trend|progress)|my (week|month|trend|progress|last \d+ days)|how('?s| is) my (diet|eating|nutrition)/.test(msg)) {
      const monthly = /(month|30 days)/.test(msg);
      const r = coachTrendReply(coach, monthly);
      if (r) return { intent: 'coach_trend', reply: withDisclaimer(r), sources: [] };
    }

    // "what did I eat today / my meals today / show my food"
    if (/(what|which).*(did i|have i|i)\s*(eat|eaten|ate|logged?|had).*(today|so far)|my (food|meals?|diet|log).*(today|so far)|(show|list).*(food|meals?).*(today)?/.test(msg)) {
      const foods = coach.todayFoods;
      if (foods && foods.length > 0) {
        const lines = foods.map((f) => `• ${f.name}${f.slot ? ` (${f.slot})` : ''} — ${f.grams} g, ${f.kcal} kcal`).join('\n');
        const total = coach.today ? `\n\nTotal: ${coach.today.kcal} kcal, ${coach.today.proteinG} g protein, ${coach.today.carbG} g carbs, ${coach.today.fatG} g fat.` : '';
        return { intent: 'coach_today', reply: withDisclaimer(`Here's what you've logged today:\n${lines}${total}`), sources: [] };
      }
      if (foods) return { intent: 'coach_today', reply: withDisclaimer(`You haven't logged any food yet today. Log a meal and it'll show up here with the running totals.`), sources: [] };
    }

    // "how many carbs/protein/fat/calories/etc did I take today" — consumed, not the target.
    if (/(today|so far|so-far).*(carb|protein|fat|calorie|kcal|sugar|fibre|fiber|sodium|salt|water)|(carb|protein|fat|calorie|kcal|sugar|fibre|fiber|sodium|salt|water).*(today|so far|did i (eat|take|have|log|consume)|have i (eaten|taken|had|logged|consumed))|how much have i (eaten|logged|consumed|had)/.test(msg)) {
      const t = coach.today;
      if (t) {
        let r: string;
        if (/carb/.test(msg)) r = `You've logged ${t.carbG} g of carbs today.`;
        else if (/protein/.test(msg)) r = `You've logged ${t.proteinG} g of protein today${t.targetProteinG ? ` (target ${t.targetProteinG} g)` : ''}.`;
        else if (/fat/.test(msg)) r = `You've logged ${t.fatG} g of fat today.`;
        else if (/sugar/.test(msg)) r = `You've logged ${t.sugarG} g of sugar today.`;
        else if (/fibre|fiber/.test(msg)) r = `You've logged ${t.fiberG} g of fibre today.`;
        else if (/sodium|salt/.test(msg)) r = `You've logged ${t.sodiumMg} mg of sodium today.`;
        else if (/water/.test(msg)) r = `You've had ${t.waterMl} ml of water today${t.targetWaterMl ? ` of a ${t.targetWaterMl} ml target` : ''}.`;
        else r = coachTodayReply(coach) ?? `You've logged ${t.kcal} kcal today.`;
        return { intent: 'coach_today', reply: withDisclaimer(r), sources: [] };
      }
    }

    // Catch-all for a genuine consumption QUESTION we can answer from today's log — even vague or
    // misspelled ("in total meal today how much cards I took"). Gated on interrogative framing so a
    // statement or preference ("I don't want to take protein") is NOT hijacked — those go to the LLM.
    const looksLikeQuestion = /\?$|^(how|what|which|tell me|show|list|do i|did i|have i|give me|how much|how many)\b/.test(msg);
    if (coach.today && looksLikeQuestion && /\b(consumed|consume|intake|eaten|ate|logged|log|total|took)\b/.test(msg) && /(today|so far|meal|food|calorie|kcal|macro|carb|card|protein|fat|diet)/.test(msg)) {
      const r = coachTodayReply(coach);
      if (r) return { intent: 'coach_today', reply: withDisclaimer(r), sources: [] };
    }
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
