import { computeStreak } from '../logging/dashboard';

/** The default habit set seeded for a user on first access. */
export interface HabitDef {
  key: string;
  title: string;
  icon: string;
  sortOrder: number;
}

export const DEFAULT_HABITS: HabitDef[] = [
  { key: 'steps-10k', title: '10,000 steps', icon: '🚶', sortOrder: 0 },
  { key: 'morning-walk', title: 'Morning walk', icon: '🌅', sortOrder: 1 },
  { key: 'no-sugar-8pm', title: 'No sugar after 8 pm', icon: '🍭', sortOrder: 2 },
  { key: 'water-goal', title: 'Hit the water goal', icon: '💧', sortOrder: 3 },
  { key: 'sleep-7h', title: '7+ hours of sleep', icon: '😴', sortOrder: 4 },
];

/**
 * Metrics the app already tracks, used to AUTO-complete the matching habit — so hitting your water
 * goal or 10k steps ticks the habit itself, no manual checkbox. `waterMl`/`waterTargetMl` are
 * server-side; `steps`/`sleepHours` come from the device's Health Connect. All optional — a metric
 * we don't have simply leaves its habit manual.
 */
export interface HabitMetrics {
  steps?: number;
  waterMl?: number;
  waterTargetMl?: number;
  sleepHours?: number;
}

export interface AutoHabitStatus {
  /** True when this habit is driven by a tracked metric (so the UI shows progress, not just a box). */
  tracked: boolean;
  /** True when the metric has met the target today. */
  done: boolean;
  current?: number;
  target?: number;
  unit?: string;
}

const STEPS_TARGET = 10_000;
const SLEEP_TARGET_H = 7;

/**
 * PURE: for an auto-trackable habit key, whether today's metric meets the goal. Unknown keys or
 * missing metrics return `{ tracked: false, done: false }` — those habits stay manual.
 */
export function autoHabitStatus(key: string, m: HabitMetrics): AutoHabitStatus {
  switch (key) {
    case 'steps-10k':
      if (m.steps == null) return { tracked: false, done: false };
      return { tracked: true, done: m.steps >= STEPS_TARGET, current: Math.round(m.steps), target: STEPS_TARGET, unit: 'steps' };
    case 'water-goal':
      if (m.waterMl == null || !m.waterTargetMl) return { tracked: false, done: false };
      return { tracked: true, done: m.waterMl >= m.waterTargetMl, current: Math.round(m.waterMl), target: Math.round(m.waterTargetMl), unit: 'ml' };
    case 'sleep-7h':
      if (m.sleepHours == null) return { tracked: false, done: false };
      return { tracked: true, done: m.sleepHours >= SLEEP_TARGET_H, current: Math.round(m.sleepHours * 10) / 10, target: SLEEP_TARGET_H, unit: 'h' };
    default:
      return { tracked: false, done: false };
  }
}

/** Consecutive-day streak for a habit from its done-day keys ending today (or yesterday). PURE. */
export function habitStreak(doneDayKeys: string[], todayKey: string): number {
  return computeStreak(doneDayKeys, todayKey);
}

/**
 * A gentle, non-punitive nudge when a streak has just broken - encouraging a fresh start rather
 * than shaming the miss. Returns null when there's nothing to recover. PURE.
 */
export function recoveryNudge(title: string, brokenStreakLength: number): string | null {
  if (brokenStreakLength <= 0) return null;
  if (brokenStreakLength >= 7) {
    return `Your ${brokenStreakLength}-day “${title}” streak paused - that was a strong run. One tick today starts the next one.`;
  }
  return `“${title}” slipped a day - no problem. Check it off today and you’re right back on track.`;
}

/** A simple, encouraging evening review line from how many of today's habits are done. PURE. */
export function eveningReview(done: number, total: number): string {
  if (total === 0) return 'Add a habit to start building your daily wins.';
  if (done >= total) return `All ${total} habits done today - that’s a clean sweep. 🎉`;
  if (done === 0) return `${total} habits still open - even one tick is momentum. You’ve got time.`;
  return `${done} of ${total} done - a solid day. ${total - done} to go if you can.`;
}
