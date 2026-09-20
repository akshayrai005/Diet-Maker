import { llmTextJson } from '../../ai/vision';
import type { WeeklyReport } from './report';

/** The AI read-out that leads a weekly/monthly report: what went well and what to change, for eating, drinking and training. */
export interface ReportInsights {
  headline: string;
  eat: string[];
  drink: string[];
  exercise: string[];
  nextWeek: string[];
  /** 'ai' when an LLM wrote it, 'rules' for the built-in fallback */
  source: 'ai' | 'rules';
}

interface Facts {
  loggedDays: number;
  avgKcal: number | null;
  targetKcal: number | null;
  adherencePct: number | null;
  avgProteinG: number | null;
  targetProteinG: number | null;
  avgWaterMl: number | null;
  targetWaterMl: number | null;
  weightDeltaKg: number | null;
  sessions: number;
  activeDays: number;
  totalSets: number;
  kcalBurned: number;
  muscles: { group: string; sets: number }[];
  top: { name: string; sets: number; bestKg: number | null }[];
}

function facts(r: WeeklyReport): Facts {
  const loggedDays = r.days.length;
  const avgProtein = loggedDays ? Math.round(r.days.reduce((s, d) => s + d.proteinG, 0) / loggedDays) : null;
  const waterDays = r.waterByDay.length;
  const avgWater = waterDays ? Math.round(r.waterByDay.reduce((s, d) => s + d.ml, 0) / waterDays) : null;
  const ex = r.exercise;
  return {
    loggedDays,
    avgKcal: r.avgKcal,
    targetKcal: r.targets?.dailyKcal ?? null,
    adherencePct: r.adherencePct,
    avgProteinG: avgProtein,
    targetProteinG: r.targets?.proteinG ?? null,
    avgWaterMl: avgWater,
    targetWaterMl: r.targets?.waterMl ?? null,
    weightDeltaKg: r.weightDeltaKg,
    sessions: ex?.sessions ?? 0,
    activeDays: ex?.activeDays ?? 0,
    totalSets: ex?.totalSets ?? 0,
    kcalBurned: ex?.kcalBurned ?? 0,
    muscles: ex?.byMuscle ?? [],
    top: ex?.top ?? [],
  };
}

/** Built-in read-out used when no LLM is configured or it fails. Plain, specific, never invents numbers. */
export function ruleInsights(r: WeeklyReport): ReportInsights {
  const f = facts(r);
  const eat: string[] = [];
  const drink: string[] = [];
  const exercise: string[] = [];
  const nextWeek: string[] = [];

  if (!f.loggedDays) {
    eat.push('No food was logged in this period, so there is nothing to judge yet. Log meals as you eat them and the next report will be specific.');
    nextWeek.push('Log every meal for the next 7 days.');
  } else {
    if (f.avgKcal != null && f.targetKcal) {
      const diff = f.avgKcal - f.targetKcal;
      if (Math.abs(diff) <= f.targetKcal * 0.08) eat.push(`Calories were on target: ${f.avgKcal} kcal a day against a target of ${f.targetKcal}.`);
      else if (diff < 0) {
        eat.push(`You averaged ${f.avgKcal} kcal a day, ${Math.abs(diff)} under your ${f.targetKcal} target.`);
        nextWeek.push('Add one solid meal or snack a day so calories reach your target.');
      } else {
        eat.push(`You averaged ${f.avgKcal} kcal a day, ${diff} over your ${f.targetKcal} target.`);
        nextWeek.push('Trim portions of the biggest meal of the day a little.');
      }
    }
    if (f.avgProteinG != null && f.targetProteinG) {
      if (f.avgProteinG >= f.targetProteinG * 0.9) eat.push(`Protein was strong at ${f.avgProteinG} g a day (target ${f.targetProteinG} g).`);
      else {
        eat.push(`Protein averaged ${f.avgProteinG} g a day, short of your ${f.targetProteinG} g target.`);
        nextWeek.push('Add a protein source (eggs, dal, paneer, curd, whey) to breakfast and one snack.');
      }
    }
  }

  if (f.avgWaterMl == null) drink.push('No water was logged, so hydration could not be checked.');
  else if (f.targetWaterMl && f.avgWaterMl >= f.targetWaterMl * 0.9) drink.push(`Hydration was good: about ${f.avgWaterMl} ml a day.`);
  else {
    drink.push(`Water averaged ${f.avgWaterMl} ml a day${f.targetWaterMl ? ` against a ${f.targetWaterMl} ml target` : ''}.`);
    nextWeek.push('Keep a bottle within reach and finish one before lunch and one before dinner.');
  }

  if (!f.sessions) {
    exercise.push('No training was logged in this period.');
    nextWeek.push('Plan at least 3 workouts and log each set as you do it.');
  } else {
    exercise.push(`You trained on ${f.activeDays} day${f.activeDays === 1 ? '' : 's'}: ${f.totalSets} sets, about ${f.kcalBurned} kcal burned.`);
    const worked = f.muscles.filter((m) => m.group !== 'Other');
    const top = worked[0];
    if (top) {
      exercise.push(`Most work went to ${top.group.toLowerCase()} (${top.sets} sets).`);
      const all = ['Chest', 'Back', 'Shoulders', 'Arms', 'Legs', 'Core'];
      const missing = all.filter((g) => !worked.some((m) => m.group === g));
      if (missing.length) {
        exercise.push(`Nothing was logged for ${missing.map((g) => g.toLowerCase()).join(', ')}.`);
        nextWeek.push(`Train ${(missing[0] ?? 'the missed group').toLowerCase()} early next week so the split stays balanced.`);
      }
    }
    if (f.activeDays < 3) nextWeek.push('Aim for 3 training days; even a short session counts.');
  }

  const headline = !f.loggedDays && !f.sessions
    ? 'Not enough logged to judge this period yet.'
    : `${f.loggedDays} day${f.loggedDays === 1 ? '' : 's'} of food logged, ${f.sessions} workout${f.sessions === 1 ? '' : 's'}.`;
  return { headline, eat, drink, exercise, nextWeek: nextWeek.slice(0, 5), source: 'rules' };
}

const strArr = (v: unknown): string[] =>
  Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string' && x.trim().length > 0).map((s) => s.trim().slice(0, 300)).slice(0, 5) : [];

/** Asks the configured LLM to write the read-out from the report's real numbers; falls back to [ruleInsights] on any failure. */
export async function aiInsights(r: WeeklyReport, periodLabel: string): Promise<ReportInsights> {
  const fallback = ruleInsights(r);
  const f = facts(r);
  if (!f.loggedDays && !f.sessions) return fallback;
  const prompt = [
    'You are a supportive, straight-talking fitness and nutrition coach writing a report for one person.',
    `Period: ${periodLabel}. Use ONLY the numbers below; never invent data. Be specific and brief. No medical advice.`,
    `Facts (JSON): ${JSON.stringify(f)}`,
    'Return JSON with keys: headline (one sentence), eat (2-4 short strings), drink (1-2), exercise (2-4), nextWeek (3-5 concrete actions).',
  ].join('\n');
  try {
    const out = await llmTextJson(prompt);
    if (!out) return fallback;
    const eat = strArr(out.eat);
    const exercise = strArr(out.exercise);
    if (!eat.length && !exercise.length) return fallback;
    return {
      headline: typeof out.headline === 'string' && out.headline.trim() ? out.headline.trim().slice(0, 200) : fallback.headline,
      eat: eat.length ? eat : fallback.eat,
      drink: strArr(out.drink).length ? strArr(out.drink) : fallback.drink,
      exercise: exercise.length ? exercise : fallback.exercise,
      nextWeek: strArr(out.nextWeek).length ? strArr(out.nextWeek) : fallback.nextWeek,
      source: 'ai',
    };
  } catch {
    return fallback;
  }
}
