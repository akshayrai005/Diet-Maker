/**
 * Deterministic mood / stress / sleep-quality insight from a series of gentle 1–5 self check-ins.
 * This is supportive reflection, NOT a clinical assessment or diagnosis. If entries show sustained
 * low mood it surfaces a gentle, non-alarmist "consider talking to someone" nudge — it never
 * diagnoses depression or any condition. Pure & unit-tested; never an LLM.
 */

export interface MoodEntry {
  /** 1 (very low) – 5 (great). */
  mood?: number | null;
  /** 1 (calm) – 5 (very stressed). */
  stress?: number | null;
  /** 1 (poor) – 5 (great). */
  sleepQuality?: number | null;
}

export type MoodDirection = 'improving' | 'declining' | 'steady' | 'insufficient';

export interface MoodInsight {
  entryCount: number;
  avgMood: number | null;
  avgStress: number | null;
  avgSleepQuality: number | null;
  direction: MoodDirection;
  message: string;
  /** Gentle "consider professional support" flag — sustained low mood, never a diagnosis. */
  professionalNudge: boolean;
  disclaimer: string;
}

export const MOOD_DISCLAIMER =
  'This is a gentle self-reflection, not a mental-health diagnosis. If low mood, stress or poor sleep persist or worry you, please talk to a doctor or a mental-health professional.';

const avg = (xs: number[]): number | null =>
  xs.length ? Math.round((xs.reduce((a, b) => a + b, 0) / xs.length) * 10) / 10 : null;

function nums(entries: MoodEntry[], key: keyof MoodEntry): number[] {
  return entries
    .map((e) => e[key])
    .filter((v): v is number => typeof v === 'number' && isFinite(v));
}

/**
 * @param entries oldest → newest.
 */
export function moodInsight(entries: MoodEntry[]): MoodInsight {
  const moods = nums(entries, 'mood');
  const avgMood = avg(moods);
  const avgStress = avg(nums(entries, 'stress'));
  const avgSleepQuality = avg(nums(entries, 'sleepQuality'));

  // Direction: compare the newest third vs the oldest third of mood entries.
  let direction: MoodDirection = 'insufficient';
  if (moods.length >= 4) {
    const k = Math.max(1, Math.floor(moods.length / 3));
    const early = avg(moods.slice(0, k))!;
    const recent = avg(moods.slice(-k))!;
    const delta = recent - early;
    if (delta >= 0.5) direction = 'improving';
    else if (delta <= -0.5) direction = 'declining';
    else direction = 'steady';
  } else if (moods.length >= 2) {
    direction = 'steady';
  }

  // Sustained low mood: the last 3 mood check-ins each ≤ 2 (need at least 3 to avoid a bad-day flag).
  const lastThree = moods.slice(-3);
  const professionalNudge = lastThree.length >= 3 && lastThree.every((m) => m <= 2);

  let message: string;
  if (moods.length === 0) {
    message = 'Add a quick mood check-in to start noticing patterns over time.';
  } else if (professionalNudge) {
    message =
      "Your mood has felt low for a little while. That's worth being kind to yourself about — small daily walks, sunlight and reaching out to someone you trust can help, and a professional can help more.";
  } else if (direction === 'improving') {
    message = 'Your mood has been trending up lately — whatever you\'re doing, keep it going.';
  } else if (direction === 'declining') {
    message = 'Your mood has dipped a bit recently. Gentle movement, better sleep and time outdoors often help.';
  } else if (avgStress != null && avgStress >= 4) {
    message = 'Stress has been running high. A few minutes of slow breathing or a short walk can take the edge off.';
  } else {
    message = 'Thanks for checking in. Steady self-awareness is a healthy habit.';
  }

  return {
    entryCount: entries.length,
    avgMood,
    avgStress,
    avgSleepQuality,
    direction,
    message,
    professionalNudge,
    disclaimer: MOOD_DISCLAIMER,
  };
}
