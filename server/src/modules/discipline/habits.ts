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

/** Consecutive-day streak for a habit from its done-day keys ending today (or yesterday). PURE. */
export function habitStreak(doneDayKeys: string[], todayKey: string): number {
  return computeStreak(doneDayKeys, todayKey);
}

/**
 * A gentle, non-punitive nudge when a streak has just broken — encouraging a fresh start rather
 * than shaming the miss. Returns null when there's nothing to recover. PURE.
 */
export function recoveryNudge(title: string, brokenStreakLength: number): string | null {
  if (brokenStreakLength <= 0) return null;
  if (brokenStreakLength >= 7) {
    return `Your ${brokenStreakLength}-day “${title}” streak paused — that was a strong run. One tick today starts the next one.`;
  }
  return `“${title}” slipped a day — no problem. Check it off today and you’re right back on track.`;
}

/** A simple, encouraging evening review line from how many of today's habits are done. PURE. */
export function eveningReview(done: number, total: number): string {
  if (total === 0) return 'Add a habit to start building your daily wins.';
  if (done >= total) return `All ${total} habits done today — that’s a clean sweep. 🎉`;
  if (done === 0) return `${total} habits still open — even one tick is momentum. You’ve got time.`;
  return `${done} of ${total} done — a solid day. ${total - done} to go if you can.`;
}
