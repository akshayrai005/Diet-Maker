/** What the user trained over a report window - sessions, sets, calories and which muscles got the work. Pure & testable. */

export interface ExerciseLogRow {
  exerciseName: string;
  sets: number | null;
  reps: number | null;
  weightKg: number | null;
  durationMin: number | null;
  kcal: number | null;
  performedAt: Date;
  sessionId: string | null;
}

export interface ExerciseSummary {
  /** distinct workouts (a session id, or one per active day when logs carry none) */
  sessions: number;
  activeDays: number;
  totalSets: number;
  kcalBurned: number;
  cardioMin: number;
  byDay: { date: string; sets: number; kcal: number }[];
  /** sets per muscle group, most trained first */
  byMuscle: { group: string; sets: number }[];
  /** the exercises done most, with the heaviest weight lifted */
  top: { name: string; sets: number; bestKg: number | null }[];
}

const GROUP_RULES: [string, RegExp][] = [
  ['Cardio', /treadmill|cycling|elliptical|running|jog|walk|cardio|jump rope|skipping|stair|swim|bike/],
  ['Core', /crunch|plank|abs\b|core|twist|sit-?up|leg raise|russian|woodchop|mountain climber/],
  ['Chest', /bench press|chest|pec|fly|flye|push-?up|dip|incline press|decline press/],
  ['Shoulders', /shoulder|overhead|military|arnold|lateral raise|front raise|rear delt|upright row|face pull|shrug/],
  ['Arms', /curl|tricep|pushdown|skull|hammer|preacher|kickback|forearm|wrist/],
  ['Back', /row|pull-?up|chin-?up|pulldown|lat |lats|deadlift|back extension|pullover/],
  ['Legs', /squat|lunge|leg |calf|glute|hip thrust|step-?up|hamstring|quad|romanian/],
];

export function muscleGroupOf(exerciseName: string): string {
  const n = exerciseName.toLowerCase();
  for (const [group, re] of GROUP_RULES) if (re.test(n)) return group;
  return 'Other';
}

export function summariseExercise(logs: ExerciseLogRow[], dayKeyOf: (d: Date) => string): ExerciseSummary {
  const days = new Map<string, { sets: number; kcal: number }>();
  const muscles = new Map<string, number>();
  const exercises = new Map<string, { sets: number; bestKg: number | null }>();
  const sessionIds = new Set<string>();
  let totalSets = 0;
  let kcalBurned = 0;
  let cardioMin = 0;

  for (const l of logs) {
    const sets = Math.max(1, l.sets ?? 1);
    const key = dayKeyOf(l.performedAt);
    const d = days.get(key) ?? { sets: 0, kcal: 0 };
    d.sets += sets;
    d.kcal += l.kcal ?? 0;
    days.set(key, d);

    const group = muscleGroupOf(l.exerciseName);
    if (group === 'Cardio') cardioMin += l.durationMin ?? 0;
    else muscles.set(group, (muscles.get(group) ?? 0) + sets);

    const e = exercises.get(l.exerciseName) ?? { sets: 0, bestKg: null };
    e.sets += sets;
    if (l.weightKg != null && (e.bestKg == null || l.weightKg > e.bestKg)) e.bestKg = l.weightKg;
    exercises.set(l.exerciseName, e);

    if (l.sessionId) sessionIds.add(l.sessionId);
    totalSets += sets;
    kcalBurned += l.kcal ?? 0;
  }

  return {
    sessions: sessionIds.size || days.size,
    activeDays: days.size,
    totalSets,
    kcalBurned: Math.round(kcalBurned),
    cardioMin,
    byDay: [...days.entries()].sort((a, b) => a[0].localeCompare(b[0])).map(([date, v]) => ({ date, sets: v.sets, kcal: Math.round(v.kcal) })),
    byMuscle: [...muscles.entries()].sort((a, b) => b[1] - a[1]).map(([group, sets]) => ({ group, sets })),
    top: [...exercises.entries()].sort((a, b) => b[1].sets - a[1].sets).slice(0, 6).map(([name, v]) => ({ name, sets: v.sets, bestKg: v.bestKg })),
  };
}
