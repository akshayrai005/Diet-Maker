import {
  BodyGoal,
  ExerciseItem,
  ExerciseLocation,
  FitnessLevel,
  IntensityPreference,
  TrainingSplit,
  WeeklyWorkout,
  WorkoutDay,
} from './exercise.types';
import { applyMusclePriority } from './physique';
import { withSubstitutions } from './strength';

const s = (name: string, sets: number, reps: string): ExerciseItem => ({ name, sets, reps, type: 'strength' });
const c = (name: string, sets: number, reps: string): ExerciseItem => ({ name, sets, reps, type: 'cardio' });
const m = (name: string, sets: number, reps: string): ExerciseItem => ({ name, sets, reps, type: 'mobility' });

interface DayTemplate {
  focus: string;
  exercises: ExerciseItem[];
}

const WEEKDAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const BLOCK_LETTERS = ['A', 'B', 'C'];

/**
 * PROGRAMS[key] = array of mesocycle BLOCKS; each block is one full week of day-templates.
 * We advance one block every 4 weeks so the same muscles get fresh exercises — this drives
 * continued growth and stops the body adapting/plateauing.
 */
const PROGRAMS: Record<string, DayTemplate[][]> = {
  // ---- GYM · MUSCULAR: dedicated bro-split (Chest/Back/Shoulders/Biceps/Triceps/Legs&Abs) ----
  'muscular:gym': [
    // Block A
    [
      { focus: 'Chest', exercises: [s('Barbell bench press', 4, '8-10'), s('Incline dumbbell press', 4, '10-12'), s('Cable fly', 3, '12-15'), s('Chest dips', 3, '10'), s('Push-up burnout', 2, 'AMRAP')] },
      { focus: 'Back', exercises: [s('Deadlift', 4, '6-8'), s('Lat pulldown', 4, '10-12'), s('Barbell row', 4, '8-10'), s('Seated cable row', 3, '12'), s('Face pull', 3, '15')] },
      { focus: 'Shoulders', exercises: [s('Overhead barbell press', 4, '8-10'), s('Lateral raise', 4, '12-15'), s('Rear-delt fly', 3, '15'), s('Front raise', 3, '12'), s('Barbell shrugs', 3, '15')] },
      { focus: 'Biceps & Forearms', exercises: [s('Barbell curl', 4, '10-12'), s('Incline dumbbell curl', 3, '12'), s('Hammer curl', 3, '12'), s('Concentration curl', 3, '12'), s('Wrist curl', 3, '20')] },
      { focus: 'Triceps & Core', exercises: [s('Close-grip bench press', 4, '10'), s('Rope pushdown', 4, '12-15'), s('Overhead extension', 3, '12'), s('Bench dips', 3, '15'), m('Plank', 3, '60s')] },
      { focus: 'Legs & Abs', exercises: [s('Back squat', 4, '8-10'), s('Leg press', 3, '12'), s('Romanian deadlift', 3, '10'), s('Leg curl', 3, '12'), s('Standing calf raise', 4, '15'), s('Hanging leg raise', 3, '15')] },
    ],
    // Block B (weeks 5-8) — new angles/variations
    [
      { focus: 'Chest', exercises: [s('Incline barbell press', 4, '8-10'), s('Flat dumbbell press', 4, '10-12'), s('Pec-deck fly', 3, '15'), s('Decline press', 3, '10'), s('Cable crossover', 3, '15')] },
      { focus: 'Back', exercises: [s('Pull-ups (weighted)', 4, '8'), s('T-bar row', 4, '10'), s('Single-arm dumbbell row', 3, '12'), s('Straight-arm pulldown', 3, '15'), s('Back extension', 3, '15')] },
      { focus: 'Shoulders', exercises: [s('Arnold press', 4, '10'), s('Cable lateral raise', 4, '15'), s('Reverse pec-deck', 3, '15'), s('Upright row', 3, '12'), s('Dumbbell shrugs', 3, '15')] },
      { focus: 'Biceps & Forearms', exercises: [s('EZ-bar curl', 4, '10'), s('Cable curl', 3, '12'), s('Preacher curl', 3, '12'), s('Reverse curl', 3, '15'), s("Farmer's carry", 3, '40m')] },
      { focus: 'Triceps & Core', exercises: [s('Skull crushers', 4, '10'), s('Single-arm pushdown', 3, '15'), s('Kickbacks', 3, '15'), s('Diamond push-ups', 3, 'AMRAP'), s('Cable crunch', 3, '20')] },
      { focus: 'Legs & Abs', exercises: [s('Front squat', 4, '8'), s('Hack squat', 3, '12'), s('Walking lunges', 3, '12'), s('Leg extension', 3, '15'), s('Seated calf raise', 4, '20'), s('Ab wheel rollout', 3, '12')] },
    ],
    // Block C (weeks 9-12)
    [
      { focus: 'Chest', exercises: [s('Dumbbell bench press', 4, '10'), s('Incline cable press', 4, '12'), s('Machine chest press', 3, '12'), s('Weighted dips', 3, '10'), s('Svend press', 3, '15')] },
      { focus: 'Back', exercises: [s('Rack pulls', 4, '6'), s('Wide-grip pulldown', 4, '10'), s('Chest-supported row', 3, '12'), s('Cable pullover', 3, '15'), s('Reverse fly', 3, '15')] },
      { focus: 'Shoulders', exercises: [s('Push press', 4, '6'), s('Machine shoulder press', 4, '10'), s('Leaning cable lateral', 3, '15'), s('Rear-delt row', 3, '15'), s('Barbell shrugs', 4, '12')] },
      { focus: 'Biceps & Forearms', exercises: [s('Dumbbell curl', 4, '10'), s('Spider curl', 3, '12'), s('Rope hammer curl', 3, '12'), s('Zottman curl', 3, '12'), s('Reverse wrist curl', 3, '20')] },
      { focus: 'Triceps & Core', exercises: [s('Weighted dips', 4, '10'), s('Cable overhead extension', 3, '12'), s('JM press', 3, '10'), s('Rope pushdown', 3, '15'), m('Hanging knee raise', 3, '15')] },
      { focus: 'Legs & Abs', exercises: [s('Deadlift', 4, '5'), s('Bulgarian split squat', 3, '10'), s('Leg press', 3, '15'), s('Nordic curl', 3, '8'), s('Standing calf raise', 4, '20'), s('Cable crunch', 3, '20')] },
    ],
  ],

  // ---- HOME · MUSCULAR: bodyweight, 2 rotating blocks ----
  'muscular:home': [
    [
      { focus: 'Push (Chest / Shoulders / Triceps)', exercises: [s('Push-ups', 4, '15-20'), s('Pike push-ups', 3, '10'), s('Diamond push-ups', 3, '12'), s('Chair dips', 3, '15'), m('Plank', 3, '45s')] },
      { focus: 'Pull (Back / Biceps)', exercises: [s('Backpack rows', 4, '12'), s('Doorway rows', 3, '12'), s('Superman', 3, '15'), s('Reverse snow-angels', 3, '15'), s('Backpack curls', 3, '15')] },
      { focus: 'Legs & Abs', exercises: [s('Bodyweight squats', 4, '20'), s('Reverse lunges', 3, '15'), s('Glute bridges', 3, '20'), s('Wall sit', 3, '45s'), s('Leg raises', 3, '15')] },
      { focus: 'Push (Advanced)', exercises: [s('Feet-elevated push-ups', 4, '12'), s('Archer push-ups', 3, '8'), s('Pike push-ups', 3, '10'), s('Chair dips', 3, '15'), m('Plank', 3, '60s')] },
      { focus: 'Pull & Shoulders', exercises: [s('Backpack rows', 4, '15'), s('Towel curls', 3, '15'), s('Superman pulls', 3, '15'), s('Y-raise', 3, '15'), s('Backpack shrugs', 3, '20')] },
      { focus: 'Legs & Core', exercises: [s('Bulgarian split squat (chair)', 3, '12'), s('Jump squats', 3, '15'), s('Single-leg glute bridge', 3, '12'), s('Step-ups', 3, '15'), s('Bicycle crunch', 3, '20')] },
    ],
    [
      { focus: 'Push (Explosive)', exercises: [s('Clap push-ups', 4, '8'), s('Wide push-ups', 3, '15'), s('Hindu push-ups', 3, '12'), s('Bench dips (feet up)', 3, '15'), m('Side plank', 3, '30s each')] },
      { focus: 'Pull (Volume)', exercises: [s('Towel door rows', 4, '15'), s('Inverted rows (table)', 3, '10'), s('Superman hold', 3, '30s'), s('Backpack curls', 4, '15'), s('Prone Y-T-W', 3, '10')] },
      { focus: 'Legs & Abs', exercises: [s('Pistol squat (assisted)', 3, '8'), s('Curtsy lunges', 3, '15'), s('Hip thrust (feet up)', 3, '20'), s('Calf raises', 4, '25'), s('Hollow-body hold', 3, '30s')] },
      { focus: 'Push (Tempo)', exercises: [s('Slow push-ups (3s down)', 4, '10'), s('Decline push-ups', 3, '12'), s('Pseudo-planche push-ups', 3, '8'), s('Chair dips', 3, '15'), m('Plank reach', 3, '20')] },
      { focus: 'Pull & Rear delts', exercises: [s('Backpack high-row', 4, '15'), s('Reverse fly (backpack)', 3, '15'), s('Superman pulls', 3, '15'), s('Hammer curls (backpack)', 3, '15'), s('Face-pull (towel)', 3, '15')] },
      { focus: 'Legs & Core', exercises: [s('Jump lunges', 3, '12'), s('Wall sit', 3, '60s'), s('Single-leg RDL', 3, '12'), s('Step-ups', 3, '15'), s('Russian twist', 3, '20')] },
    ],
  ],

  // ---- Other combos: 1 block each (still rotate exercises within the week) ----
  'athletic:gym': [[
    { focus: 'Upper strength', exercises: [s('Bench press', 4, '6'), s('Weighted pull-ups', 4, '6'), s('Overhead press', 3, '8'), s('Barbell row', 3, '8'), m('Plank', 3, '45s')] },
    { focus: 'Lower strength & core', exercises: [s('Back squat', 4, '6'), s('Romanian deadlift', 3, '8'), s('Walking lunges', 3, '12'), s('Hanging leg raise', 3, '15'), s('Calf raise', 3, '20')] },
    { focus: 'HIIT conditioning', exercises: [c('Rowing intervals', 10, '30s hard / 30s easy'), s('Kettlebell swings', 4, '20'), c('Battle ropes', 4, '30s'), c('Burpees', 4, '12')] },
    { focus: 'Upper hypertrophy', exercises: [s('Incline dumbbell press', 4, '10'), s('Lat pulldown', 4, '12'), s('Lateral raise', 4, '15'), s('Cable curl', 3, '12'), s('Tricep pushdown', 3, '12')] },
    { focus: 'Lower power', exercises: [c('Box jumps', 5, '5'), s('Trap-bar deadlift', 4, '5'), s('Bulgarian split squat', 3, '10'), s('Nordic curl', 3, '8'), m('Plank', 3, '60s')] },
    { focus: 'Cardio & mobility', exercises: [c('Steady run', 1, '30-40 min'), m('Mobility flow', 1, '15 min'), s('Core circuit', 3, 'rounds')] },
  ]],
  'athletic:home': [[
    { focus: 'Full-body circuit', exercises: [s('Push-ups', 3, '15'), s('Bodyweight squats', 3, '20'), m('Plank', 3, '45s'), s('Reverse lunges', 3, '12'), s('Superman', 3, '15')] },
    { focus: 'HIIT', exercises: [c('Burpees', 5, '12'), c('High knees', 5, '30s'), c('Jump squats', 5, '15'), c('Mountain climbers', 5, '30s')] },
    { focus: 'Core & mobility', exercises: [m('Plank', 3, '60s'), s('Leg raises', 3, '15'), s('Russian twists', 3, '20'), s('Dead bug', 3, '12'), m('Mobility flow', 1, '10 min')] },
    { focus: 'Full-body circuit', exercises: [s('Pike push-ups', 3, '10'), s('Reverse lunges', 3, '15'), s('Chair dips', 3, '15'), s('Glute bridges', 3, '20'), s('Superman', 3, '15')] },
    { focus: 'Plyometrics', exercises: [c('Jump squats', 4, '15'), c('Broad jumps', 4, '8'), c('Skater jumps', 4, '20'), c('Burpees', 4, '10'), c('Plank jacks', 4, '20')] },
    { focus: 'Cardio & core', exercises: [c('Brisk walk / jog', 1, '30-40 min'), s('Core circuit', 3, 'rounds')] },
  ]],
  'fatloss:gym': [[
    { focus: 'Full-body weights', exercises: [s('Back squat', 3, '10'), s('Bench press', 3, '10'), s('Seated row', 3, '10'), s('Overhead press', 3, '10'), m('Plank', 3, '45s')] },
    { focus: 'HIIT', exercises: [c('Treadmill sprints', 10, '30s on / 60s off'), s('Kettlebell swings', 4, '20'), c('Mountain climbers', 4, '30s')] },
    { focus: 'Full-body weights', exercises: [s('Deadlift', 3, '8'), s('Incline press', 3, '10'), s('Lat pulldown', 3, '12'), s('Walking lunges', 3, '12'), s('Hanging knee raise', 3, '15')] },
    { focus: 'Steady cardio & core', exercises: [c('Incline walk / cycle', 1, '40-45 min'), s('Core circuit', 3, 'rounds')] },
    { focus: 'Full-body weights', exercises: [s('Leg press', 3, '12'), s('Dumbbell press', 3, '12'), s('Cable row', 3, '12'), s('Lateral raise', 3, '15'), s('Russian twist', 3, '20')] },
    { focus: 'HIIT & core', exercises: [c('Bike intervals', 12, '20s on / 40s off'), c('Burpees', 4, '12'), m('Plank complex', 3, 'rounds')] },
  ]],
  'fatloss:home': [[
    { focus: 'Cardio & circuit', exercises: [c('Brisk walk / jog', 1, '25-30 min'), s('Bodyweight squats', 3, '20'), s('Push-ups', 3, '12'), m('Plank', 3, '40s')] },
    { focus: 'HIIT', exercises: [c('Jumping jacks', 5, '40s'), c('Burpees', 5, '10'), c('Mountain climbers', 5, '30s'), c('High knees', 5, '30s')] },
    { focus: 'Core & walk', exercises: [m('Plank', 3, '45s'), s('Leg raises', 3, '15'), s('Bicycle crunches', 3, '20'), c('Walk', 1, '20-30 min')] },
    { focus: 'Bodyweight circuit', exercises: [s('Reverse lunges', 3, '15'), s('Push-ups', 3, '12'), s('Glute bridges', 3, '20'), s('Superman', 3, '15'), s('Wall sit', 3, '45s')] },
    { focus: 'HIIT', exercises: [c('Squat jumps', 5, '15'), c('Skater jumps', 5, '20'), c('Burpees', 5, '10'), c('Plank jacks', 5, '20')] },
    { focus: 'Steady cardio', exercises: [c('Brisk walk / cycle', 1, '35-40 min'), m('Stretching', 1, '10 min')] },
  ]],

  // ---- Selectable splits (Push/Pull/Legs, Upper/Lower, Full-body). Core/warm-up/cool-down are
  // appended automatically. body_part → 'muscular', fat_loss → 'fatloss' reuse the programs above. ----
  'ppl:gym': [[
    { focus: 'Push (Chest / Shoulders / Triceps)', exercises: [s('Barbell bench press', 4, '8-10'), s('Overhead barbell press', 4, '8-10'), s('Incline dumbbell press', 3, '10-12'), s('Lateral raise', 3, '15'), s('Rope pushdown', 3, '12-15')] },
    { focus: 'Pull (Back / Biceps)', exercises: [s('Deadlift', 4, '6-8'), s('Lat pulldown', 4, '10-12'), s('Barbell row', 3, '8-10'), s('Face pull', 3, '15'), s('Barbell curl', 3, '10-12')] },
    { focus: 'Legs', exercises: [s('Back squat', 4, '8-10'), s('Romanian deadlift', 3, '10'), s('Leg press', 3, '12'), s('Leg curl', 3, '12'), s('Standing calf raise', 4, '15')] },
    { focus: 'Push (Volume)', exercises: [s('Incline barbell press', 4, '8-10'), s('Dumbbell shoulder press', 3, '10'), s('Cable fly', 3, '15'), s('Lateral raise', 3, '15'), s('Skull crushers', 3, '10')] },
    { focus: 'Pull (Volume)', exercises: [s('Weighted pull-ups', 4, '8'), s('T-bar row', 3, '10'), s('Seated cable row', 3, '12'), s('Rear-delt fly', 3, '15'), s('EZ-bar curl', 3, '10')] },
    { focus: 'Legs (Volume)', exercises: [s('Front squat', 4, '8'), s('Hack squat', 3, '12'), s('Walking lunges', 3, '12'), s('Leg extension', 3, '15'), s('Seated calf raise', 4, '20')] },
  ]],
  'ppl:home': [[
    { focus: 'Push (Chest / Shoulders / Triceps)', exercises: [s('Push-ups', 4, '15-20'), s('Pike push-ups', 3, '10'), s('Diamond push-ups', 3, '12'), s('Chair dips', 3, '15')] },
    { focus: 'Pull (Back / Biceps)', exercises: [s('Backpack rows', 4, '12'), s('Doorway rows', 3, '12'), s('Superman', 3, '15'), s('Backpack curls', 3, '15')] },
    { focus: 'Legs', exercises: [s('Bodyweight squats', 4, '20'), s('Reverse lunges', 3, '15'), s('Glute bridges', 3, '20'), s('Wall sit', 3, '45s')] },
    { focus: 'Push (Advanced)', exercises: [s('Feet-elevated push-ups', 4, '12'), s('Archer push-ups', 3, '8'), s('Pike push-ups', 3, '10'), s('Chair dips', 3, '15')] },
    { focus: 'Pull (Advanced)', exercises: [s('Backpack rows', 4, '15'), s('Towel curls', 3, '15'), s('Superman pulls', 3, '15'), s('Y-raise', 3, '15')] },
    { focus: 'Legs (Advanced)', exercises: [s('Bulgarian split squat (chair)', 3, '12'), s('Jump squats', 3, '15'), s('Single-leg glute bridge', 3, '12'), s('Step-ups', 3, '15')] },
  ]],
  'upperlower:gym': [[
    { focus: 'Upper (Strength)', exercises: [s('Barbell bench press', 4, '6-8'), s('Barbell row', 4, '8'), s('Overhead barbell press', 3, '8'), s('Lat pulldown', 3, '10'), s('Barbell curl', 3, '10'), s('Rope pushdown', 3, '12')] },
    { focus: 'Lower (Strength)', exercises: [s('Back squat', 4, '6-8'), s('Romanian deadlift', 3, '10'), s('Leg press', 3, '12'), s('Leg curl', 3, '12'), s('Standing calf raise', 4, '15')] },
    { focus: 'Upper (Hypertrophy)', exercises: [s('Incline dumbbell press', 4, '10'), s('Weighted pull-ups', 3, '8'), s('Lateral raise', 4, '15'), s('Seated cable row', 3, '12'), s('Hammer curl', 3, '12'), s('Skull crushers', 3, '10')] },
    { focus: 'Lower (Hypertrophy)', exercises: [s('Front squat', 4, '8'), s('Deadlift', 3, '6'), s('Walking lunges', 3, '12'), s('Leg extension', 3, '15'), s('Seated calf raise', 4, '20')] },
  ]],
  'upperlower:home': [[
    { focus: 'Upper', exercises: [s('Push-ups', 4, '15-20'), s('Backpack rows', 4, '12'), s('Pike push-ups', 3, '10'), s('Doorway rows', 3, '12'), s('Chair dips', 3, '15'), s('Backpack curls', 3, '15')] },
    { focus: 'Lower', exercises: [s('Bodyweight squats', 4, '20'), s('Reverse lunges', 3, '15'), s('Glute bridges', 3, '20'), s('Wall sit', 3, '45s'), s('Single-leg glute bridge', 3, '12')] },
    { focus: 'Upper (Advanced)', exercises: [s('Feet-elevated push-ups', 4, '12'), s('Archer push-ups', 3, '8'), s('Backpack rows', 4, '15'), s('Y-raise', 3, '15'), s('Chair dips', 3, '15')] },
    { focus: 'Lower (Advanced)', exercises: [s('Bulgarian split squat (chair)', 3, '12'), s('Jump squats', 3, '15'), s('Step-ups', 3, '15'), s('Single-leg glute bridge', 3, '12'), s('Wall sit', 3, '60s')] },
  ]],
  // Full-body: each day hits ALL major groups with ~2 movements each (chest, back, shoulders,
  // legs, biceps, triceps) ≈ 12 exercises. (Beginners are still trimmed by the level's exercise cap.)
  'fullbody:gym': [[
    { focus: 'Full-body A', exercises: [s('Barbell bench press', 4, '8-10'), s('Cable fly', 3, '12'), s('Barbell row', 4, '8-10'), s('Lat pulldown', 3, '12'), s('Overhead barbell press', 3, '10'), s('Lateral raise', 3, '15'), s('Back squat', 4, '8-10'), s('Leg curl', 3, '12'), s('Barbell curl', 3, '12'), s('Rope pushdown', 3, '12'), s('Romanian deadlift', 3, '10'), s('Standing calf raise', 3, '15')] },
    { focus: 'Full-body B', exercises: [s('Incline dumbbell press', 4, '10'), s('Chest dips', 3, '10'), s('Weighted pull-ups', 4, '8'), s('Seated cable row', 3, '12'), s('Dumbbell shoulder press', 3, '10'), s('Rear-delt fly', 3, '15'), s('Front squat', 4, '8'), s('Leg press', 3, '12'), s('Hammer curl', 3, '12'), s('Skull crushers', 3, '10'), s('Deadlift', 3, '6'), s('Seated calf raise', 3, '20')] },
    { focus: 'Full-body C', exercises: [s('Flat dumbbell press', 4, '10'), s('Pec-deck fly', 3, '15'), s('T-bar row', 4, '10'), s('Straight-arm pulldown', 3, '15'), s('Arnold press', 3, '10'), s('Cable lateral raise', 3, '15'), s('Hack squat', 4, '12'), s('Walking lunges', 3, '12'), s('EZ-bar curl', 3, '10'), s('Cable overhead extension', 3, '12'), s('Leg extension', 3, '15'), s('Calf raise', 3, '20')] },
  ]],
  'fullbody:home': [[
    { focus: 'Full-body A', exercises: [s('Push-ups', 4, '15-20'), s('Chair dips', 3, '15'), s('Backpack rows', 4, '15'), s('Doorway rows', 3, '12'), s('Pike push-ups', 3, '10'), s('Backpack curls', 3, '15'), s('Bodyweight squats', 4, '20'), s('Glute bridges', 3, '20'), s('Reverse lunges', 3, '15'), s('Wall sit', 3, '45s')] },
    { focus: 'Full-body B', exercises: [s('Diamond push-ups', 4, '12'), s('Wide push-ups', 3, '15'), s('Backpack rows', 4, '15'), s('Superman', 3, '15'), s('Pike push-ups', 3, '10'), s('Y-raise', 3, '15'), s('Bulgarian split squat (chair)', 3, '12'), s('Single-leg glute bridge', 3, '12'), s('Step-ups', 3, '15'), s('Towel curls', 3, '15')] },
    { focus: 'Full-body C', exercises: [s('Feet-elevated push-ups', 4, '12'), s('Archer push-ups', 3, '8'), s('Doorway rows', 4, '12'), s('Reverse snow-angels', 3, '15'), s('Pike push-ups', 3, '12'), s('Backpack shrugs', 3, '20'), s('Jump squats', 3, '15'), s('Reverse lunges', 3, '15'), s('Wall sit', 3, '60s'), s('Backpack curls', 3, '15')] },
  ]],
};

const REST_DAY: ExerciseItem[] = [
  c('Light walk', 1, '20-30 min'),
  m('Full-body stretching', 1, '10 min'),
];

const DISCLAIMER =
  'Educational guidance, not a substitute for a doctor or certified trainer. Warm up, use good form, and stop if you feel pain.';

export interface WorkoutOptions {
  restDayOfWeek?: number; // 0=Sun..6=Sat; omitted => 7 training days
  startDate?: Date;
  today?: Date;
  days?: number;
  /** Training experience — scales volume and progression ceilings. Defaults to 'intermediate'. */
  fitnessLevel?: FitnessLevel;
  /** How hard to push — scales sets/volume. Defaults to 'standard'. Safety-capped below. */
  intensity?: IntensityPreference;
  /** Under-18 lifter — SAFETY cap: forces hard/beast down to standard. */
  under18?: boolean;
  /** Medical caution (any flagged condition / reduced mobility) — SAFETY cap, same effect. */
  medicalCaution?: boolean;
  /** Priority muscle groups to bring up — extra volume within the level's set caps. */
  priorityMuscles?: string[];
  /** User-selected training split — overrides the goal-derived program when set. */
  split?: TrainingSplit;
}

/** Map a selectable split + location to a PROGRAMS key (body_part/fat_loss reuse existing programs). */
function splitProgramKey(split: TrainingSplit, location: ExerciseLocation): string {
  const loc = location === 'none' ? 'home' : location;
  const base: Record<TrainingSplit, string> = {
    body_part: 'muscular',
    fat_loss: 'fatloss',
    full_body: 'fullbody',
    push_pull_legs: 'ppl',
    upper_lower: 'upperlower',
  };
  return `${base[split]}:${loc}`;
}

// ---- Level + intensity scaling (PURE, deterministic) ----

/** Per-intensity multiplier applied to each exercise's set count. */
const INTENSITY_SET_FACTOR: Record<IntensityPreference, number> = {
  easy: 0.75,
  standard: 1,
  hard: 1.15,
  beast: 1.3,
};

/** Hard ceiling on sets per exercise, by level. Global sane cap is 6; beginners cap at 3. */
const LEVEL_MAX_SETS: Record<FitnessLevel, number> = {
  beginner: 3,
  intermediate: 5,
  advanced: 6,
};

/** Max exercises kept per training day, by level. Beginners keep it simple (~5). */
const LEVEL_MAX_EXERCISES: Record<FitnessLevel, number> = {
  beginner: 5,
  intermediate: Number.POSITIVE_INFINITY,
  advanced: Number.POSITIVE_INFINITY,
};

export interface IntensityCapOptions {
  under18?: boolean;
  medicalCaution?: boolean;
}

/**
 * SAFETY gate over the requested intensity — PURE.
 *
 * When the lifter is under 18 OR has a medical caution flag, we clamp the two
 * top gears (`hard` and `beast`) down to `standard`. `easy`/`standard` pass
 * through unchanged, and we never push anyone below `easy`. With no caution
 * flags the preference is returned as-is.
 */
export function cappedIntensity(
  pref: IntensityPreference,
  opts: IntensityCapOptions = {},
): IntensityPreference {
  if (opts.under18 || opts.medicalCaution) {
    if (pref === 'beast' || pref === 'hard') return 'standard';
  }
  return pref;
}

/** Scale one exercise's sets by intensity, floored at 1 and capped by level + the global 6. */
function scaleSets(base: number, factor: number, level: FitnessLevel): number {
  const scaled = Math.round(base * factor);
  const capped = Math.min(scaled, LEVEL_MAX_SETS[level], 6);
  return Math.max(1, capped);
}

/**
 * Small deterministic form-cue lookup keyed by tokens in the exercise name.
 * The FIRST matching rule wins; unknown movements get no annotation. Cues are
 * intentionally short and safety-first.
 */
const CUE_RULES: Array<{ match: string[]; muscleGroup: string; cue: string }> = [
  { match: ['deadlift', 'romanian', 'rdl', 'rack pull'], muscleGroup: 'posterior chain', cue: 'brace your core, neutral spine, drive through your heels' },
  { match: ['close-grip', 'skull crusher', 'pushdown', 'kickback', 'tricep', 'jm press', 'dips'], muscleGroup: 'triceps', cue: 'keep your elbows tucked, full lockout' },
  { match: ['squat', 'wall sit', 'leg press', 'hack squat', 'split squat'], muscleGroup: 'legs', cue: 'chest up, brace your core, knees track over your toes' },
  { match: ['bench', 'chest press', 'chest fly', 'cable fly', 'pec-deck', 'crossover', 'svend'], muscleGroup: 'chest', cue: 'shoulder blades retracted, control the descent' },
  { match: ['push-up', 'push up', 'pushup'], muscleGroup: 'chest', cue: 'brace your core, keep a straight line head to heels' },
  { match: ['overhead', 'shoulder press', 'arnold', 'push press', 'military', 'upright row'], muscleGroup: 'shoulders', cue: 'brace your core, ribs down, press straight overhead' },
  { match: ['lateral raise', 'front raise', 'rear-delt', 'reverse fly', 'reverse pec', 'y-raise', 'face pull', 'face-pull'], muscleGroup: 'shoulders', cue: 'lead with the elbows, no swinging' },
  { match: ['pulldown', 'pull-up', 'pull up', 'pullup', 'chin-up', 'pullover'], muscleGroup: 'back', cue: 'drive your elbows down, control the stretch' },
  { match: ['row'], muscleGroup: 'back', cue: 'flat back, pull to the ribs, squeeze the shoulder blades' },
  { match: ['curl'], muscleGroup: 'biceps', cue: 'keep your elbows pinned, no swinging' },
  { match: ['lunge', 'step-up', 'step up'], muscleGroup: 'legs', cue: 'torso tall, front knee over the ankle' },
  { match: ['plank'], muscleGroup: 'core', cue: "brace your core, straight line, don't let the hips sag" },
  { match: ['crunch', 'leg raise', 'knee raise', 'russian twist', 'hollow', 'dead bug', 'ab wheel', 'sit-up'], muscleGroup: 'core', cue: 'brace your core, move slowly and controlled' },
  { match: ['glute bridge', 'hip thrust'], muscleGroup: 'glutes', cue: 'squeeze the glutes at the top, ribs down' },
  { match: ['calf raise'], muscleGroup: 'calves', cue: 'full range of motion, pause at the top' },
  { match: ['leg curl', 'nordic'], muscleGroup: 'hamstrings', cue: 'controlled tempo, no jerking' },
  { match: ['leg extension'], muscleGroup: 'quads', cue: 'controlled tempo, squeeze at the top' },
];

/** Detect equipment from the exercise name; undefined when it can't be told. */
function equipmentFor(lower: string): string | undefined {
  if (lower.includes('barbell') || lower.includes('ez-bar') || lower.includes('trap-bar')) return 'barbell';
  if (lower.includes('dumbbell')) return 'dumbbell';
  if (lower.includes('cable')) return 'cable';
  if (lower.includes('machine') || lower.includes('leg press') || lower.includes('pec-deck') || lower.includes('hack squat')) return 'machine';
  if (lower.includes('kettlebell')) return 'kettlebell';
  if (lower.includes('backpack') || lower.includes('towel') || lower.includes('band')) return 'minimal';
  if (
    lower.includes('push-up') || lower.includes('pushup') || lower.includes('plank') ||
    lower.includes('bodyweight') || lower.includes('pull-up') || lower.includes('chin-up') ||
    lower.includes('sit') || lower.includes('bridge') || lower.includes('superman') ||
    lower.includes('burpee') || lower.includes('lunge') || lower.includes('crunch')
  ) return 'bodyweight';
  return undefined;
}

/** Deterministic cue/muscleGroup/equipment annotation for a movement, keyed by name. */
function annotate(name: string): Pick<ExerciseItem, 'cue' | 'muscleGroup' | 'equipment'> {
  const lower = name.toLowerCase();
  for (const rule of CUE_RULES) {
    if (rule.match.some((token) => lower.includes(token))) {
      const out: Pick<ExerciseItem, 'cue' | 'muscleGroup' | 'equipment'> = {
        cue: rule.cue,
        muscleGroup: rule.muscleGroup,
      };
      const eq = equipmentFor(lower);
      if (eq !== undefined) out.equipment = eq;
      return out;
    }
  }
  const eq = equipmentFor(lower);
  return eq !== undefined ? { equipment: eq } : {};
}

/** A light mobility/cardio finisher appended for advanced lifters / beast intensity. */
const FINISHER: ExerciseItem = {
  name: 'Conditioning finisher',
  sets: 1,
  reps: '3 rounds',
  type: 'cardio',
  cue: 'push the pace but keep clean form',
  muscleGroup: 'full body',
  equipment: 'bodyweight',
};

/** Rest-guidance text tuned to level + intensity. */
function restGuidance(level: FitnessLevel, intensity: IntensityPreference): string {
  if (level === 'beginner') return 'Rest ~90-120s between sets while you learn the movements.';
  if (level === 'advanced' || intensity === 'hard' || intensity === 'beast') {
    return 'Rest ~45-60s between sets to keep the intensity high.';
  }
  return 'Rest ~60-90s between sets.';
}

/**
 * PURE post-processing scale: rebuilds every day/exercise (no shared refs mutated)
 * so that fitness LEVEL and (already-capped) INTENSITY meaningfully change the plan —
 * sets, exercise volume, a level-gated finisher, form cues, and rest guidance.
 */
function applyScaling(
  plan: WeeklyWorkout,
  level: FitnessLevel,
  intensity: IntensityPreference,
): WeeklyWorkout {
  const factor = INTENSITY_SET_FACTOR[intensity];
  const maxExercises = LEVEL_MAX_EXERCISES[level];
  const addFinisher = level !== 'beginner' && (level === 'advanced' || intensity === 'beast');

  const days: WorkoutDay[] = plan.days.map((day) => {
    if (day.rest) {
      // Rest days keep their light recovery items but still gain cues/annotations.
      return { ...day, exercises: day.exercises.map((ex) => ({ ...ex, ...annotate(ex.name) })) };
    }

    let items = day.exercises.map((ex) => ({
      ...ex,
      ...annotate(ex.name),
      sets: scaleSets(ex.sets, factor, level),
    }));

    if (Number.isFinite(maxExercises)) items = items.slice(0, maxExercises);
    if (addFinisher) items = [...items, { ...FINISHER }];

    return { ...day, exercises: items };
  });

  const note = `${plan.note} ${restGuidance(level, intensity)}`;
  return { ...plan, days, note };
}

function labelFor(date: Date, today: Date): string {
  const a = Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate());
  const b = Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate());
  const diff = Math.round((a - b) / 86_400_000);
  if (diff === 0) return 'Today';
  if (diff === 1) return 'Tomorrow';
  if (diff === -1) return 'Yesterday';
  return WEEKDAYS[date.getUTCDay()]!;
}

/** Which 4-week mesocycle block we're in, from the date (deterministic, rotates monthly). */
function blockForDate(date: Date, blockCount: number): number {
  const weeksSinceEpoch = Math.floor(date.getTime() / (7 * 86_400_000));
  return Math.floor(weeksSinceEpoch / 4) % Math.max(1, blockCount);
}

/**
 * Deterministic weekly workout generator. Bro-split for gym-muscular, plus a 4-week
 * mesocycle: every 4 weeks it advances to a new block of exercises so muscles keep
 * progressing. Same inputs + same week => same plan.
 */
export function generateWeeklyWorkout(
  goal: BodyGoal,
  location: ExerciseLocation,
  options: WorkoutOptions = {},
): WeeklyWorkout {
  const key = options.split ? splitProgramKey(options.split, location) : `${goal}:${location === 'none' ? 'home' : location}`;
  const program = PROGRAMS[key] ?? PROGRAMS[`${goal}:${location === 'none' ? 'home' : location}`] ?? PROGRAMS[`${goal}:home`]!;
  const refDate = options.startDate ?? new Date(0);
  const block = blockForDate(refDate, program.length);
  const templates = program[block]!;
  const dayCount = options.days ?? 7;

  const days: WorkoutDay[] = [];
  let t = 0;
  for (let d = 0; d < dayCount; d++) {
    let date: string | undefined;
    let weekday = d;
    let baseLabel: string | undefined;
    if (options.startDate) {
      const dt = new Date(options.startDate.getTime() + d * 86_400_000);
      weekday = dt.getUTCDay();
      date = dt.toISOString().slice(0, 10);
      baseLabel = labelFor(dt, options.today ?? options.startDate);
    }

    const isRest = options.restDayOfWeek !== undefined && weekday === options.restDayOfWeek;
    if (isRest) {
      days.push({ dayIndex: d, date, label: baseLabel ? `${baseLabel} · Rest` : 'Rest', focus: 'Rest & recovery', rest: true, exercises: REST_DAY });
    } else {
      const tmpl = templates[t % templates.length]!;
      t++;
      days.push({ dayIndex: d, date, label: baseLabel, focus: tmpl.focus, rest: false, exercises: tmpl.exercises });
    }
  }

  const blockLabel = program.length > 1 ? `Month ${block + 1} · Block ${BLOCK_LETTERS[block] ?? block + 1}` : 'Program';
  const note =
    program.length > 1
      ? `${blockLabel} — exercises rotate every 4 weeks so your muscles keep growing and never fully adapt.`
      : location === 'none'
        ? 'No equipment needed — bodyweight only.'
        : options.restDayOfWeek === undefined
          ? '7-day plan (no fixed rest day).'
          : '6 training days + 1 rest day. Progress by adding reps or load each week.';

  const base: WeeklyWorkout = { location, goal, days, block, blockLabel, note, disclaimer: DISCLAIMER };

  // Level + intensity scaling. Intensity is SAFETY-capped for minors / medical caution.
  const level: FitnessLevel = options.fitnessLevel ?? 'intermediate';
  const intensity = cappedIntensity(options.intensity ?? 'standard', {
    under18: options.under18,
    medicalCaution: options.medicalCaution,
  });
  const scaled = applyScaling(base, level, intensity);
  // Aesthetic priority: add volume to chosen muscle groups within the level's set cap.
  const prioritised = applyMusclePriority(scaled, options.priorityMuscles, LEVEL_MAX_SETS[level]);
  // Exercise depth: warm-up + core/abs + cool-down + a cardio element + per-exercise substitutions.
  return enrichDays(prioritised, { level, intensity, medicalCaution: options.medicalCaution });
}

// ---- Warm-up / cool-down / cardio / substitutions (PURE) ----

const w = (name: string, reps: string): ExerciseItem => ({ name, sets: 1, reps, type: 'mobility', equipment: 'bodyweight' });

/** Infer the broad region a day trains from its focus text. */
function focusRegion(focus: string): 'upper' | 'lower' | 'full' {
  const f = focus.toLowerCase();
  if (/(push|pull|chest|back|shoulder|arm|upper)/.test(f)) return 'upper';
  if (/(leg|lower|glute|squat|hamstring|quad)/.test(f)) return 'lower';
  return 'full';
}

function warmupFor(focus: string): ExerciseItem[] {
  const region = focusRegion(focus);
  const common = w('Light cardio (march/jog on spot)', '3 min');
  if (region === 'upper') return [common, w('Arm circles & band pull-aparts', '2 × 15'), w('Scapular push-ups', '2 × 10'), w('Shoulder dislocates (band/towel)', '1 × 10')];
  if (region === 'lower') return [common, w('Leg swings (front & side)', '2 × 10 each'), w('Bodyweight squats', '2 × 12'), w('Hip circles / world’s greatest stretch', '1 × 8')];
  return [common, w('Jumping jacks', '2 × 20'), w('Bodyweight squats', '2 × 10'), w('Arm circles + hip openers', '1 × 10')];
}

function cooldownFor(focus: string): ExerciseItem[] {
  const region = focusRegion(focus);
  const breathe = w('Slow diaphragmatic breathing', '2 min');
  if (region === 'upper') return [w('Chest & doorway stretch', '2 × 30s'), w('Cross-body shoulder stretch', '2 × 30s'), breathe];
  if (region === 'lower') return [w('Standing quad & hamstring stretch', '2 × 30s'), w('Hip flexor & glute stretch', '2 × 30s'), breathe];
  return [w('Full-body forward fold + child’s pose', '2 × 30s'), w('Standing quad stretch', '2 × 30s'), breathe];
}

/** A cardio/conditioning element — steady-state by default; HIIT only for hard/beast & no medical caution. */
function cardioFor(intensity: IntensityPreference, medicalCaution?: boolean): ExerciseItem {
  const allowHiit = !medicalCaution && (intensity === 'hard' || intensity === 'beast');
  if (allowHiit) {
    return { name: 'HIIT conditioning (e.g. 30s hard / 90s easy)', sets: 1, reps: '6 rounds', type: 'cardio', cue: 'keep form clean; stop if dizzy or in pain', muscleGroup: 'full body', equipment: 'bodyweight' };
  }
  return { name: 'Steady-state cardio (brisk walk/cycle)', sets: 1, reps: '15-20 min', type: 'cardio', cue: 'a pace you can hold a conversation at', muscleGroup: 'full body', equipment: 'bodyweight' };
}

/**
 * Dedicated core/abs block for every training day — a plank hold + a dynamic ab move + an
 * anti-rotation / lower-ab move, scaled by level. `gentle` (medical caution / reduced mobility)
 * swaps in low-impact, back-friendly options. Core counts as strength; substitutions attach later.
 */
function coreFor(level: FitnessLevel, gentle: boolean): ExerciseItem[] {
  const core = (name: string, sets: number, reps: string): ExerciseItem => ({ name, sets, reps, type: 'strength', muscleGroup: 'core', equipment: 'bodyweight' });
  if (gentle) {
    return [core('Dead bug (slow, controlled)', 2, '8 each side'), core('Glute bridge', 2, '12'), core('Bird dog (anti-rotation)', 2, '8 each side')];
  }
  switch (level) {
    case 'beginner':
      return [core('Plank', 2, '20-30s'), core('Dead bug', 2, '10 each side'), core('Glute bridge', 2, '12')];
    case 'advanced':
      return [core('Plank', 3, '60s'), core('Hanging/lying leg raises', 3, '12'), core('Russian twist (anti-rotation)', 3, '20'), core('Reverse crunch (lower abs)', 3, '15')];
    default:
      return [core('Plank', 2, '45s'), core('Bicycle crunches', 2, '15 each side'), core('Reverse crunch (lower abs)', 2, '12')];
  }
}

function enrichDays(plan: WeeklyWorkout, opts: { level: FitnessLevel; intensity: IntensityPreference; medicalCaution?: boolean }): WeeklyWorkout {
  const gentle = !!opts.medicalCaution;
  const days: WorkoutDay[] = plan.days.map((day) => {
    if (day.rest) return day;
    return {
      ...day,
      warmup: warmupFor(day.focus),
      exercises: day.exercises.map(withSubstitutions),
      core: coreFor(opts.level, gentle).map(withSubstitutions),
      cardio: cardioFor(opts.intensity, opts.medicalCaution),
      cooldown: cooldownFor(day.focus),
    };
  });
  return { ...plan, days };
}
