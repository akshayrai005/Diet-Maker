/**
 * Coaching metadata for exercises - PURE lookup. Rules match by name tokens (first match wins), with a generic
 * per-muscle-group fallback so EVERY exercise resolves to something safe. This is guidance, not medical advice.
 */
export type MovementPattern =
  | 'horizontal push' | 'vertical push' | 'horizontal pull' | 'vertical pull' | 'squat' | 'hip hinge' | 'lunge'
  | 'isolation' | 'carry' | 'anti-extension' | 'anti-rotation' | 'trunk flexion' | 'lateral core' | 'stability' | 'conditioning';
export type Difficulty = 'beginner' | 'intermediate' | 'advanced';

export interface ExerciseInfo {
  pattern: MovementPattern;
  secondary: string[];
  difficulty: Difficulty;
  setup: string;
  mistakes: string[];
  safety: string;
  regression?: string;
  progression?: string;
}

interface Rule { match: string[]; info: ExerciseInfo }

const RULES: Rule[] = [
  { match: ['pallof'], info: { pattern: 'anti-rotation', secondary: ['obliques', 'shoulders'], difficulty: 'beginner', setup: 'Stand side-on to a cable at chest height, hold the handle at your sternum, feet hip-width.', mistakes: ['Letting the torso rotate toward the cable', 'Shrugging the shoulders'], safety: 'Keep ribs down and breathe steadily; use a light load until the torso stays still.', regression: 'Half-kneeling Pallof hold', progression: 'Pallof press with an overhead reach or a split stance' } },
  { match: ['side plank'], info: { pattern: 'lateral core', secondary: ['obliques', 'glutes', 'shoulders'], difficulty: 'beginner', setup: 'Forearm under shoulder, feet stacked (or knee down), body in one straight line.', mistakes: ['Hips sagging', 'Rolling forward or back'], safety: 'Stop when the hips drop; do both sides equally.', regression: 'Side plank from the knees', progression: 'Side plank with a top-leg raise' } },
  { match: ['dead bug'], info: { pattern: 'stability', secondary: ['hip flexors'], difficulty: 'beginner', setup: 'On your back, arms up, knees at 90 degrees, lower back gently pressed into the floor.', mistakes: ['Lower back arching off the floor', 'Moving too fast'], safety: 'Only go as low as the back stays flat.', progression: 'Add a light weight or a slower tempo' } },
  { match: ['ab wheel', 'rollout'], info: { pattern: 'anti-extension', secondary: ['lats', 'shoulders'], difficulty: 'advanced', setup: 'Kneel on a mat, wheel under shoulders, ribs down and glutes tight.', mistakes: ['Lower back sagging', 'Going further than you can control'], safety: 'Stop the range before the back arches; build up from short rollouts against a wall.', regression: 'Plank, then short-range rollouts', progression: 'Full-range or standing rollout' } },
  { match: ['hanging leg raise', 'hanging knee raise'], info: { pattern: 'trunk flexion', secondary: ['hip flexors', 'forearms'], difficulty: 'advanced', setup: 'Hang from a bar with an active grip, shoulders packed down.', mistakes: ['Swinging', 'Lifting with the hip flexors only, no pelvic tilt'], safety: 'Stop if the shoulders or grip give out; control the way down.', regression: 'Reverse crunch or captain-chair raises', progression: 'Straight-leg raises to the bar' } },
  { match: ['plank'], info: { pattern: 'anti-extension', secondary: ['glutes', 'shoulders'], difficulty: 'beginner', setup: 'Forearms under shoulders, body in a straight line, glutes and abs tight.', mistakes: ['Hips sagging or piking', 'Holding the breath'], safety: 'End the set when form breaks, not when the timer says so.', regression: 'Plank on the knees or with hands on a bench', progression: 'Longer holds, then a weighted plank' } },
  { match: ['reverse crunch'], info: { pattern: 'trunk flexion', secondary: ['hip flexors'], difficulty: 'beginner', setup: 'Lie on your back, knees bent over hips, hands by your sides.', mistakes: ['Swinging the legs with momentum', 'Pulling on the neck'], safety: 'Curl the pelvis up slowly; do not force the range.', progression: 'Decline bench reverse crunch' } },
  { match: ['crunch', 'sit-up', 'russian twist', 'v-up', 'flutter', 'hollow', 'leg raise'], info: { pattern: 'trunk flexion', secondary: ['obliques', 'hip flexors'], difficulty: 'beginner', setup: 'Lie or sit with a neutral neck, feet anchored or lifted as the variation requires.', mistakes: ['Pulling on the neck', 'Using momentum'], safety: 'Stop for any lower-back pain. These build ab strength, but visible abs come from overall fat loss - spot reduction does not work.', progression: 'Add load (cable crunch) or a slower tempo' } },
  { match: ['bench press', 'chest press', 'push-up', 'push up', 'dips', 'incline press', 'decline'], info: { pattern: 'horizontal push', secondary: ['triceps', 'front delts'], difficulty: 'intermediate', setup: 'Shoulder blades pulled back and down, feet planted, bar or hands over the mid-chest.', mistakes: ['Flaring the elbows to 90 degrees', 'Bouncing the bar off the chest', 'Lifting the hips'], safety: 'Use a spotter or safety arms for heavy barbell work; stop 1-2 reps before failure.', regression: 'Machine chest press or incline push-ups', progression: 'Add load in small steps, or a slower lowering phase' } },
  { match: ['overhead press', 'shoulder press', 'arnold', 'military'], info: { pattern: 'vertical push', secondary: ['triceps', 'upper chest'], difficulty: 'intermediate', setup: 'Ribs down, glutes tight, forearms vertical, press in a slight arc.', mistakes: ['Leaning back', 'Pressing in front of the head'], safety: 'Stop if the shoulder pinches; use a neutral grip if the overhead range hurts.', regression: 'Seated dumbbell or machine press', progression: 'Standing barbell press with small load jumps' } },
  { match: ['lateral raise', 'front raise', 'reverse pec', 'face pull', 'rear delt'], info: { pattern: 'isolation', secondary: ['traps', 'rotator cuff'], difficulty: 'beginner', setup: 'Slight elbow bend, light weight, controlled tempo without swinging.', mistakes: ['Using momentum', 'Shrugging the traps'], safety: 'Light loads only; the delts respond to volume and control, not heavy swinging.', progression: 'Slow the lowering phase or use cables for constant tension' } },
  { match: ['pull-up', 'pull up', 'chin-up', 'pulldown', 'lat pull'], info: { pattern: 'vertical pull', secondary: ['biceps', 'rear delts'], difficulty: 'intermediate', setup: 'Shoulder blades down first, chest up, pull the elbows toward your hips.', mistakes: ['Kipping', 'Half range', 'Shrugging into the ears'], safety: 'Ease into the full hang; build volume gradually to protect the elbows.', regression: 'Lat pulldown or band-assisted pull-ups', progression: 'Full pull-ups, then weighted' } },
  { match: ['row', 'rack pull'], info: { pattern: 'horizontal pull', secondary: ['biceps', 'rear delts', 'erectors'], difficulty: 'intermediate', setup: 'Hinge with a flat back, pull the elbows back, squeeze the shoulder blades.', mistakes: ['Rounding the back', 'Jerking the weight with the hips'], safety: 'Keep the spine neutral; reduce load if the back rounds.', regression: 'Chest-supported or seated cable row', progression: 'Heavier barbell row or single-arm variations' } },
  { match: ['deadlift', 'romanian', 'good morning', 'hip thrust', 'glute bridge', 'nordic'], info: { pattern: 'hip hinge', secondary: ['glutes', 'hamstrings', 'erectors'], difficulty: 'intermediate', setup: 'Push the hips back, bar close to the legs, neutral spine, tension before lifting.', mistakes: ['Rounding the lower back', 'Bar drifting away from the body', 'Squatting the lift'], safety: 'Learn the hinge with light loads first; stop the set when the back rounds.', regression: 'Romanian deadlift with dumbbells or a glute bridge', progression: 'Add load slowly, only when the hinge is solid' } },
  { match: ['squat', 'leg press', 'hack', 'wall sit', 'leg extension'], info: { pattern: 'squat', secondary: ['glutes', 'core'], difficulty: 'intermediate', setup: 'Feet shoulder-width, brace the core, knees track over the toes, depth you can control.', mistakes: ['Knees caving in', 'Heels lifting', 'Losing the brace at the bottom'], safety: 'Use safety pins for heavy squats; reduce depth if the back or knees hurt.', regression: 'Goblet squat, box squat or leg press', progression: 'Add load in small steps, then pause or front squats' } },
  { match: ['lunge', 'split squat', 'step-up', 'step up'], info: { pattern: 'lunge', secondary: ['glutes', 'core'], difficulty: 'intermediate', setup: 'A stride long enough that the front shin stays near vertical; torso tall.', mistakes: ['Front knee collapsing inward', 'Too short a stride'], safety: 'Hold a support if balance is a limit; train both legs equally.', regression: 'Assisted split squat', progression: 'Dumbbells, then a rear-foot-elevated split squat' } },
  { match: ['leg curl', 'calf raise', 'calf'], info: { pattern: 'isolation', secondary: [], difficulty: 'beginner', setup: 'Full range of motion with a controlled tempo; adjust the machine to your joints.', mistakes: ['Bouncing', 'Cutting the range short'], safety: 'Stop for sharp joint pain.', progression: 'Pause at the stretch, or single-leg versions' } },
  { match: ['curl', 'preacher', 'concentration'], info: { pattern: 'isolation', secondary: ['forearms'], difficulty: 'beginner', setup: 'Elbows by your sides, wrists neutral, no swinging.', mistakes: ['Swinging the torso', 'Elbows drifting forward'], safety: 'Reduce the load if the elbows or wrists ache.', progression: 'Slow lowering phase, or incline curls for a longer stretch' } },
  { match: ['tricep', 'pushdown', 'skull', 'extension', 'kickback', 'jm press', 'close-grip'], info: { pattern: 'isolation', secondary: ['chest', 'shoulders'], difficulty: 'beginner', setup: 'Elbows fixed close to the body, full lockout, control the return.', mistakes: ['Elbows flaring or drifting', 'Using body swing'], safety: 'Keep loads moderate on overhead and skull-crusher work to protect the elbows.', progression: 'Add reps first, then small load increases' } },
  { match: ['shrug', 'farmer'], info: { pattern: 'carry', secondary: ['forearms', 'traps'], difficulty: 'beginner', setup: 'Stand tall, shoulders down, grip firm; lift straight up, no rolling.', mistakes: ['Rolling the shoulders', 'Craning the neck'], safety: 'Use straps only once the grip is the limit.', progression: 'Heavier loads or longer carries' } },
  { match: ['run', 'walk', 'jog', 'cycle', 'bike', 'burpee', 'jump', 'climber', 'jack', 'skater', 'high knees', 'swing', 'hiit', 'cardio'], info: { pattern: 'conditioning', secondary: ['legs', 'heart'], difficulty: 'beginner', setup: 'Warm up first; start at a pace you can talk through and build gradually.', mistakes: ['Starting too fast', 'Skipping the warm-up'], safety: 'Stop for chest pain, dizziness or unusual breathlessness and seek medical advice. Calorie-burn figures are estimates.', progression: 'Add time before intensity' } },
];

const groupInfo = (token: string): ExerciseInfo => RULES.find((r) => r.match.includes(token))!.info;
const BY_GROUP: Record<string, ExerciseInfo> = {
  chest: groupInfo('bench press'),
  back: groupInfo('row'),
  shoulders: groupInfo('overhead press'),
  biceps: groupInfo('curl'),
  triceps: groupInfo('tricep'),
  legs: groupInfo('squat'),
  core: groupInfo('plank'),
  cardio: groupInfo('run'),
};

const GENERIC: ExerciseInfo = {
  pattern: 'stability',
  secondary: [],
  difficulty: 'beginner',
  setup: 'Move slowly through a comfortable range with good posture and steady breathing.',
  mistakes: ['Rushing the reps', 'Holding the breath'],
  safety: 'Stop if anything hurts sharply. This is general guidance, not a substitute for a physiotherapist or doctor.',
};

/** Coaching info for any exercise name; never returns undefined. */
export function infoFor(name: string, muscleGroup?: string): ExerciseInfo {
  const lower = name.toLowerCase();
  for (const r of RULES) if (r.match.some((t) => lower.includes(t))) return r.info;
  return (muscleGroup && BY_GROUP[muscleGroup]) || GENERIC;
}
