/**
 * Mind & body content: guided yoga flows and meditation/breathing sessions. Static,
 * deterministic, zero-cost (text + timing only — no audio/video hosting). Educational.
 */

export interface YogaPose {
  name: string;
  hold: string; // e.g. "5 breaths", "60s"
  cue: string;
}

export interface YogaFlow {
  id: string;
  name: string;
  focus: string;
  durationMin: number;
  level: 'beginner' | 'all' | 'intermediate';
  poses: YogaPose[];
}

export interface Meditation {
  id: string;
  name: string;
  goal: string;
  durationMin: number;
  steps: string[];
  /** Optional breathing pattern (seconds) the app can animate as a breathing guide. */
  pattern?: { inhaleSec: number; hold1Sec: number; exhaleSec: number; hold2Sec: number };
}

const YOGA: YogaFlow[] = [
  {
    id: 'morning-energizer',
    name: 'Morning Energizer',
    focus: 'Wake up the body & mind',
    durationMin: 12,
    level: 'all',
    poses: [
      { name: 'Cat–Cow', hold: '6 breaths', cue: 'Arch and round with your breath to warm the spine.' },
      { name: 'Downward Dog', hold: '5 breaths', cue: 'Press hips up and back, pedal the heels.' },
      { name: 'Sun Salutation A', hold: '3 rounds', cue: 'Flow with the breath, one movement per inhale/exhale.' },
      { name: 'Warrior I (each side)', hold: '5 breaths', cue: 'Front knee over ankle, arms reaching up.' },
      { name: 'Standing Forward Fold', hold: '5 breaths', cue: 'Soft knees, let the head hang heavy.' },
      { name: 'Mountain Pose + big breath', hold: '5 breaths', cue: 'Stand tall, inhale arms up, exhale down.' },
    ],
  },
  {
    id: 'wind-down',
    name: 'Wind-Down for Sleep',
    focus: 'Calm the nervous system before bed',
    durationMin: 10,
    level: 'beginner',
    poses: [
      { name: "Child's Pose", hold: '8 breaths', cue: 'Knees wide, forehead down, breathe into the back.' },
      { name: 'Reclining Twist (each side)', hold: '6 breaths', cue: 'Drop knees to one side, gaze the other way.' },
      { name: 'Happy Baby', hold: '6 breaths', cue: 'Hold the feet, gently rock side to side.' },
      { name: 'Legs-Up-the-Wall', hold: '3 min', cue: 'Sit close to a wall, swing legs up, relax fully.' },
      { name: 'Savasana', hold: '2 min', cue: 'Lie still, soften the whole body, slow the breath.' },
    ],
  },
  {
    id: 'desk-reset',
    name: 'Desk Reset',
    focus: 'Undo sitting — neck, shoulders, hips',
    durationMin: 7,
    level: 'all',
    poses: [
      { name: 'Neck rolls', hold: '5 each way', cue: 'Slow half-circles; never crank the neck back.' },
      { name: 'Seated Cat–Cow', hold: '6 breaths', cue: 'Move the spine right there in your chair.' },
      { name: 'Seated Spinal Twist (each side)', hold: '5 breaths', cue: 'Lengthen up on the inhale, twist on the exhale.' },
      { name: 'Standing Forward Fold', hold: '6 breaths', cue: 'Release the low back and hamstrings.' },
      { name: 'Low Lunge hip opener (each side)', hold: '6 breaths', cue: 'Sink the hips to open the front of the leg.' },
    ],
  },
  {
    id: 'period-relief',
    name: 'Period Relief',
    focus: 'Gentle poses to ease cramps & bloating',
    durationMin: 12,
    level: 'beginner',
    poses: [
      { name: "Child's Pose", hold: '8 breaths', cue: 'Knees wide, breathe into the lower back.' },
      { name: 'Cat–Cow', hold: '6 breaths', cue: 'Soft, slow spinal waves.' },
      { name: 'Reclining Bound Angle', hold: '2 min', cue: 'Soles together, knees fall open, fully supported.' },
      { name: 'Supine Twist (each side)', hold: '6 breaths', cue: 'Gently wring out the belly and low back.' },
      { name: 'Legs-Up-the-Wall', hold: '3 min', cue: 'Eases bloating and calms the system.' },
    ],
  },
  {
    id: 'core-strength',
    name: 'Core & Strength Flow',
    focus: 'Build stability and heat',
    durationMin: 15,
    level: 'intermediate',
    poses: [
      { name: 'Sun Salutation B', hold: '3 rounds', cue: 'Chair, lunge, plank, updog, downdog — flowing.' },
      { name: 'Plank', hold: '45s', cue: 'Straight line head to heels, brace the belly.' },
      { name: 'Side Plank (each side)', hold: '30s', cue: 'Stack the shoulders, lift the hips.' },
      { name: 'Boat Pose', hold: '5 breaths ×3', cue: 'Lift the chest, long spine, don’t collapse.' },
      { name: 'Bridge', hold: '6 breaths', cue: 'Press through the feet, lift the hips.' },
    ],
  },
  {
    id: 'power-flow',
    name: 'Power Vinyasa Flow',
    focus: 'Build strength & stamina (advanced)',
    durationMin: 25,
    level: 'intermediate',
    poses: [
      { name: 'Sun Salutation B', hold: '5 rounds', cue: 'Link breath to movement, steady and strong.' },
      { name: 'Warrior II → Reverse Warrior (each side)', hold: '5 breaths', cue: 'Front knee bent, open the chest.' },
      { name: 'Side Angle → Triangle (each side)', hold: '5 breaths', cue: 'Long side body, reach through the top hand.' },
      { name: 'Chair → Twisted Chair (each side)', hold: '5 breaths', cue: 'Sit low, hook the elbow, keep knees level.' },
      { name: 'Crow Pose (or knees-on-arms prep)', hold: '3 tries', cue: 'Gaze forward, squeeze the knees, lean in slowly.' },
      { name: 'Wheel or Bridge', hold: '5 breaths ×2', cue: 'Open the front body; stop if the low back pinches.' },
      { name: 'Savasana', hold: '3 min', cue: 'Earn the rest — let everything go.' },
    ],
  },
];

const MEDITATION: Meditation[] = [
  {
    id: 'box-breathing',
    name: 'Box Breathing',
    goal: 'Focus & calm in 4 minutes',
    durationMin: 4,
    steps: [
      'Sit tall, shoulders relaxed, eyes soft or closed.',
      'Inhale through the nose for 4 counts.',
      'Hold gently for 4 counts.',
      'Exhale slowly for 4 counts.',
      'Hold empty for 4 counts, then repeat the box.',
    ],
    pattern: { inhaleSec: 4, hold1Sec: 4, exhaleSec: 4, hold2Sec: 4 },
  },
  {
    id: '478-sleep',
    name: '4-7-8 for Sleep',
    goal: 'Wind down & fall asleep',
    durationMin: 5,
    steps: [
      'Lie down or sit comfortably; rest the tongue behind the top teeth.',
      'Inhale quietly through the nose for 4 counts.',
      'Hold the breath for 7 counts.',
      'Exhale fully through the mouth for 8 counts.',
      'Repeat for 4–8 rounds; let each exhale relax you deeper.',
    ],
    pattern: { inhaleSec: 4, hold1Sec: 7, exhaleSec: 8, hold2Sec: 0 },
  },
  {
    id: 'stress-reset',
    name: 'Stress Reset',
    goal: 'Quickly settle a racing mind',
    durationMin: 3,
    steps: [
      'Take one big sigh out to signal "safe" to your body.',
      'Breathe in for 4, out slowly for 6 — a longer exhale calms the nerves.',
      'Name 5 things you can see, 4 you can hear, 3 you can feel.',
      'Return to the slow breath for a few more rounds.',
    ],
    pattern: { inhaleSec: 4, hold1Sec: 0, exhaleSec: 6, hold2Sec: 0 },
  },
  {
    id: 'body-scan',
    name: 'Body Scan',
    goal: 'Release tension head to toe',
    durationMin: 8,
    steps: [
      'Lie down comfortably and let the breath be natural.',
      'Bring attention to the top of your head; notice any sensation without judging.',
      'Slowly move down — face, neck, shoulders, arms, chest, belly, hips, legs, feet.',
      'Soften each area on the exhale as you pass through it.',
      'Rest for a few breaths feeling the whole body at once.',
    ],
  },
  {
    id: 'gratitude',
    name: 'Morning Gratitude',
    goal: 'Start the day grounded',
    durationMin: 4,
    steps: [
      'Sit comfortably and take three slow breaths.',
      'Bring to mind one person you are grateful for — picture them clearly.',
      'Bring to mind one thing about your body or health you are grateful for.',
      'Set one simple intention for the day.',
      'Breathe it in, then gently begin your morning.',
    ],
  },
  {
    id: 'two-min-calm',
    name: 'Two-Minute Calm',
    goal: 'A quick reset, anywhere',
    durationMin: 2,
    steps: [
      'Wherever you are, let the shoulders drop.',
      'Breathe in through the nose for 4.',
      'Breathe out slowly for 6 — the long exhale is what calms you.',
      'Repeat for a handful of rounds, then carry on.',
    ],
    pattern: { inhaleSec: 4, hold1Sec: 0, exhaleSec: 6, hold2Sec: 0 },
  },
  {
    id: 'deep-relaxation',
    name: 'Deep Relaxation (Yoga Nidra)',
    goal: 'Deep, full-body rest',
    durationMin: 20,
    steps: [
      'Lie down warm and comfortable; let the body feel heavy.',
      'Set a gentle intention for the practice.',
      'Slowly move attention through the body — right side, left side, front, back.',
      'Notice the natural breath without changing it.',
      'Rest in stillness between waking and sleep for several minutes.',
      'Gently deepen the breath and return when you are ready.',
    ],
  },
];

export function getWellness(): { yoga: YogaFlow[]; meditation: Meditation[] } {
  return { yoga: YOGA, meditation: MEDITATION };
}

export interface SuggestNowInput {
  /** Local hour 0–23, passed in so this stays pure/deterministic. */
  hour: number;
  mood?: number | null; // 1 (low) .. 5 (great)
  energy?: number | null; // 1 (drained) .. 5 (buzzing)
  sleepHours?: number | null;
}

export interface NowSuggestion {
  yoga: YogaFlow | null;
  meditation: Meditation | null;
  reason: string;
}

/**
 * Picks a specific flow + meditation for *right now* from the time of day and how the user feels
 * (mood / energy / last night's sleep). Pure & deterministic — the caller passes the local hour.
 */
export function suggestNow(input: SuggestNowInput): NowSuggestion {
  const { hour } = input;
  const low = input.mood != null && input.mood <= 2;
  const highEnergy = input.energy != null && input.energy >= 4;
  const lowEnergy = input.energy != null && input.energy <= 2;
  const poorSleep = input.sleepHours != null && input.sleepHours < 6;
  const morning = hour >= 5 && hour < 11;
  const midday = hour >= 11 && hour < 17;
  const evening = hour >= 17 && hour < 22;
  const night = hour >= 22 || hour < 5;

  const reasons: string[] = [];
  let yogaId: string;
  let medId: string;

  if (night) {
    yogaId = 'wind-down';
    medId = '478-sleep';
    reasons.push('it’s late — wind down for sleep');
  } else if (low) {
    yogaId = 'wind-down';
    medId = 'stress-reset';
    reasons.push('a gentle reset for a heavy moment');
  } else if (evening) {
    yogaId = 'wind-down';
    medId = poorSleep ? '478-sleep' : 'body-scan';
    reasons.push('easing into the evening');
  } else if (morning) {
    if (lowEnergy || poorSleep) {
      yogaId = 'desk-reset';
      medId = 'gratitude';
      reasons.push(poorSleep ? 'a soft start after short sleep' : 'a low-key start to build energy');
    } else if (highEnergy) {
      yogaId = 'core-strength';
      medId = 'gratitude';
      reasons.push('channel that morning energy');
    } else {
      yogaId = 'morning-energizer';
      medId = 'box-breathing';
      reasons.push('wake the body and mind');
    }
  } else {
    // Midday (and any remaining daytime slot).
    yogaId = 'desk-reset';
    medId = lowEnergy ? 'stress-reset' : 'box-breathing';
    reasons.push(midday ? 'undo a stretch of sitting' : 'a quick midday reset');
  }

  const byId = <T extends { id: string }>(list: T[], id: string): T | undefined => list.find((x) => x.id === id);
  return {
    yoga: byId(YOGA, yogaId) ?? YOGA[0] ?? null,
    meditation: byId(MEDITATION, medId) ?? MEDITATION[0] ?? null,
    reason: `For right now — ${reasons[0] ?? 'a balanced session'}.`,
  };
}

export interface WellnessRecommendation {
  yoga: YogaFlow | null;
  meditation: Meditation | null;
  reason: string;
}

export interface RecommendInput {
  phase?: string | null; // menstrual | follicular | ovulation | luteal | null
  mood?: number | null; // 1 (low) .. 5 (great)
  conditions?: string[];
  activityLevel?: string;
}

const byId = <T extends { id: string }>(list: T[], id: string): T | undefined => list.find((x) => x.id === id);

/**
 * Picks the day's yoga flow + meditation from the whole picture — cycle phase, today's mood
 * and lifestyle — so the mind-body plan matches how the user actually is. Pure & deterministic.
 */
export function recommendWellness(input: RecommendInput): WellnessRecommendation {
  const { phase, mood, activityLevel } = input;
  const deskJob = activityLevel === 'sedentary' || activityLevel === 'light';
  const low = mood != null && mood <= 2;
  const high = mood != null && mood >= 4;
  const reasons: string[] = [];

  // ---- Yoga ----
  let yogaId: string;
  if (phase === 'menstrual') {
    yogaId = 'period-relief';
    reasons.push('gentle relief for your period');
  } else if (low || phase === 'luteal') {
    yogaId = 'wind-down';
    reasons.push(low ? 'calming, because your mood is low today' : 'calming for the pre-period phase');
  } else if (deskJob && !high) {
    yogaId = 'desk-reset';
    reasons.push('undoing a day of sitting');
  } else if (high && (phase === 'follicular' || phase === 'ovulation')) {
    yogaId = 'core-strength';
    reasons.push('you have energy to build on');
  } else {
    yogaId = 'morning-energizer';
  }

  // ---- Meditation ----
  let medId: string;
  if (low) {
    medId = 'stress-reset';
    reasons.push('a quick reset for a heavy day');
  } else if (phase === 'luteal') {
    medId = 'stress-reset';
  } else if (high) {
    medId = 'gratitude';
    reasons.push('grounding a good mood');
  } else {
    medId = 'box-breathing';
  }

  const reason =
    reasons.length > 0
      ? `Today's session — ${reasons.slice(0, 2).join(', ')}.`
      : "Today's session to keep you balanced.";

  return {
    yoga: byId(YOGA, yogaId) ?? YOGA[0] ?? null,
    meditation: byId(MEDITATION, medId) ?? MEDITATION[0] ?? null,
    reason,
  };
}
