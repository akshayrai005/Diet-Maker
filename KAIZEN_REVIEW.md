# Kaizen (NutriAI / Diet-Maker) — Comprehensive Product Review

> **Purpose**: Hand this file to a fresh Claude session to continue building.
> It covers what's built, what's missing, and how the AI should implement each gap —
> from the perspective of every user persona.

---

## 1. What's Built & Working Well

### Onboarding (40+ fields)
- Height, weight, target weight, DOB, sex/gender (incl. self-describe)
- Body measurements: waist, neck, hip
- Activity level, occupation, diet type (veg/eggetarian/nonveg/vegan/jain/keto/high-protein)
- Eating pattern (office canteen, night shift, OMAD, religious fasting, etc.)
- Body goal (fat loss / athletic / muscular), physique goal (recomp / lean bulk / cut / maintain)
- Training split (PPL / upper-lower / body-part / full-body / fat-loss circuits)
- Priority muscles (up to 4: shoulders, back, chest, arms, legs, glutes, core)
- Body type current + goal (visual selector)
- Gym membership duration, exercise location (gym/home/none)
- Medical conditions, family history, smoking, alcohol, contraception
- Budget tier, diet strictness, kitchen type, living situation, fitness level
- Goal timeline with safe-pace server validation
- Minor detection (age < 18 → no "cut" goal)

### Dashboard
- Full calorie math card: Body Need (TDEE) + Burned (steps) = Budget, Budget - Target = Deficit/Surplus
- Eaten / Remaining / Deficit chips
- 2x2 domain cards: Nutrition, Movement, Recovery, Hydration (compact, no borders)
- BMI display, priorities row

### Nutrition / Food
- Barcode scanning (camera-based food logging)
- AI vision food recognition
- Food search + USDA database
- Saved foods / favorites
- Food frequency tracking
- Diet trend analysis
- Meal plan generation (server-side) with 7 meal slots
- Meal slot weights tuned for lighter dinner (3/28/4/37/4/22/2%)
- Prep time awareness, food suitability scoring, portion units

### Movement / Exercise
- 100+ exercises across 12 categories (Chest, Back, Shoulders, Arms, Legs, Glutes, Core, Cardio, HIIT, Mobility, Yoga + All)
- 25 Core/waist exercises including Stomach Vacuum, Woodchoppers, V-Up, etc.
- Breathing cues on every exercise (strength/core/cardio/hiit patterns)
- Hydration reminders every 3rd exercise
- Rest timer with haptic feedback (preset 60/90/120s + custom)
- Exercise logging with sets/reps/weight fields (visible borders)
- Add to tomorrow's plan (race-condition fixed — direct DataStore write)
- Remove exercise from plan (always visible, not date-gated)
- Workout generator (server-side) with progressive overload, physique targeting
- Cycle-aware workout adaptation

### Plan Screen
- Today/Tomorrow day switcher
- Weekly grid (Mon-Sun) with kcal/protein per day
- Long-press to toggle fasting on any day (no longer hardcoded)
- Workout duration selector (30/45/60/90 min)
- Food presets (Indian-focused: rotis, dal, soya chunks, paneer, whey, etc.)
- AI coach review of plan (sends structured prompt to Claude API)
- Adherence scoring (exercise + food)
- Sleep time (bedtime/waketime)
- Trainer notes

### Backend Modules
- **calc/**: BMR, TDEE, macros, hydration, anthropometry, body fat, micronutrients, goal timeline
- **exercise/**: workout generator, progressive overload, strength tracking, physique targeting, cycle adaptation
- **food/**: plan generator, food filter, suitability scoring, friendliness, USDA lookup, portion units
- **body/**: body composition tracking
- **cycle/**: menstrual cycle tracking + adaptation
- **discipline/**: adherence tracking, habit scoring
- **coach/**: AI coach with context briefing
- **chat/**: adaptive AI chat engine
- **guidance/**: health guidance
- **risk/**: health risk assessment
- **wellness/**: wellness scoring
- **reports/**: analysis + gamification
- **reminders/**: notification scheduling
- **vision/**: food photo recognition
- **logging/**: food logs, body weight, dashboard, diet trends
- **family/**: family member support
- **account/**: account management with grace periods

### Tech Stack
- Android: Kotlin, Jetpack Compose (Material 3), Hilt DI, MVVM, StateFlow, Health Connect
- Backend: Node.js/TypeScript, Prisma, Neon (Postgres), Gemini/Groq AI
- Deployed: diet-maker-qry6.onrender.com (Render free tier)
- Zero-cost infrastructure

---

## 2. What's Missing — By User Persona

### 2.1 Gym Fresher (Beginner, First 3 Months)

**CRITICAL GAPS:**
- **No exercise video/GIF/image demos** — beginners don't know what "Woodchoppers" or "Face Pulls" look like. They need form guidance or they'll injure themselves.
- **No warm-up / cool-down routine** — the plan jumps straight into working sets. A 5-min dynamic warm-up and 5-min static stretch should auto-prepend/append.
- **No progressive overload guidance on-screen** — the backend has `overload.ts` but the client doesn't show "Last time you did 10kg, try 12.5kg today."
- **No RPE / effort scale** — beginners can't gauge intensity. A simple "How hard was that set? 1-10" after each exercise builds awareness.
- **No muscle map / body diagram** — visual feedback showing "today you trained chest + triceps" on a body outline.

**AI SHOULD:**
- Generate a 4-week beginner ramp-up program (Week 1: machines only, Week 2: add compound movements, etc.) based on `fitnessLevel: "beginner"` + `gymMonths`.
- Auto-select "full_body" or "upper_lower" split for beginners (not PPL — too advanced for month 1).
- Include form cues in exercise descriptions: "Keep elbows at 45°", "Don't lock knees".

### 2.2 Experienced Lifter (6+ Months)

**CRITICAL GAPS:**
- **No 1RM tracking / strength standards** — "You bench 60kg, that's intermediate for your weight class."
- **No periodization** — no deload weeks, no mesocycle planning (hypertrophy → strength → peak).
- **No superset / dropset / rest-pause support** — workout cards only show straight sets.
- **No PR (personal record) celebrations** — gamification exists on backend but nothing shows on exercise completion.
- **No workout history graph** — "Your squat has gone from 40kg → 80kg in 6 months" (motivation).
- **No plate calculator** — "Load: 20kg bar + 2×10kg + 2×5kg per side."

**AI SHOULD:**
- Detect plateaus from exercise logs (same weight × same reps for 3+ weeks) and suggest deload or variation.
- Periodize automatically: 4 weeks hypertrophy → 2 weeks strength → 1 week deload.
- Adjust volume based on recovery signals (sleep quality, soreness self-report).

### 2.3 Body Transformation — Measurement Goals

**Example: Arms 13cm → 16cm, Waist 40" → 30", Build Abs**

**CRITICAL GAPS:**
- **No body measurement tracking screen** — onboarding collects waist/neck/hip but there's NO periodic re-measurement flow. User can't log "arms: 14cm this week."
- **No measurement goals** — no way to set "arms target: 16cm" and track progress toward it.
- **No body part measurement history / chart** — waist, chest, arms, thighs, calves over time.
- **No progress photos** — before/after comparison is the #1 motivator. Need a locked photo vault with date stamps.
- **No body fat % estimation from measurements** — the backend has `anthropometry.ts` and `bodyComposition.ts` but the client doesn't surface Navy method BF% from waist/neck/hip.
- **No visible abs roadmap** — "Abs become visible at ~12-15% BF for men, ~18-22% for women. You're at ~25%. Estimated timeline: 16 weeks."

**AI SHOULD:**
- When user sets body part goals (arm circumference, waist), generate a transformation plan:
  - For arms 13→16cm: prioritize bicep/tricep work 3x/week, caloric surplus, high protein.
  - For waist 40→30: caloric deficit, core work daily, HIIT 3x/week, track weekly.
  - For abs: calculate current BF%, estimate weeks to reach visible abs range, adjust deficit.
- Auto-schedule bi-weekly measurement reminders.
- Show "estimated weeks remaining" based on rate of change from logged measurements.

### 2.4 Trainer / Coach Perspective

**CRITICAL GAPS:**
- **No client management** — a trainer can't view/manage multiple users' plans.
- **No exercise substitution engine** — "I don't have a cable machine" should auto-swap Cable Flyes → Dumbbell Flyes.
- **No injury/limitation awareness** — if user reports shoulder pain, ALL pressing movements should be flagged/substituted.
- **No workout template sharing** — trainer can't create a template and assign it to users.
- **No real-time form check** — camera-based pose estimation (future, but worth noting).

**AI SHOULD:**
- Accept free-text trainer notes and cross-reference them with the generated plan (already partially done in `requestReview()`).
- Suggest exercise substitutions when equipment is limited (`exerciseLocation: "home"`).
- Flag exercises contraindicated for reported conditions (e.g., deadlifts + herniated disc).

### 2.5 Dietician / Nutritionist Perspective

**CRITICAL GAPS:**
- **No micronutrient dashboard** — backend has `micronutrients.ts` and `micronutrientEstimate.ts` but the client shows ZERO micro data. No iron, calcium, B12, vitamin D tracking.
- **No meal timing optimization** — pre-workout meal (1-2 hrs before), post-workout (within 30 min) should be prompted based on workout schedule.
- **No water intake tracking with timestamps** — just a glass counter. Should track WHEN water is consumed (spread throughout day vs all at once).
- **No fiber tracking** — critical for gut health and satiety, not visible.
- **No food allergy/intolerance handling in meal plans** — `allergies` field exists in SensitiveData but may not filter plans.
- **No supplement recommendations** — creatine, omega-3, vitamin D based on diet gaps + goals.
- **No glycemic index awareness** — important for diabetic users (condition: diabetes is collected).

**AI SHOULD:**
- Generate a daily micronutrient report card: "You're low on iron (8mg/18mg). Add spinach or fortified cereal."
- Time meals around workouts: "Your workout is at 7 AM. Eat oats + banana by 5:30 AM."
- For diabetic users: flag high-GI foods, suggest low-GI swaps, distribute carbs evenly across meals.
- For PCOS users: recommend anti-inflammatory foods, moderate carb restriction.

### 2.6 Doctor / Medical Perspective

**CRITICAL GAPS:**
- **No vitals tracking** — no blood pressure, blood sugar, heart rate logging.
- **No lab report integration** — HbA1c, lipid panel, thyroid panel values should inform diet/exercise.
- **No medication interaction awareness** — metformin + exercise timing, beta-blockers + heart rate targets.
- **No medical alerts** — "Your BMI is 35 and you have hypertension. Consult a doctor before starting HIIT."
- **No health risk score visualization** — backend has `risk/` module but client doesn't show it.
- **No emergency protocols** — what to do if dizzy during exercise, signs of hypoglycemia.

**AI SHOULD:**
- Surface the risk module's output on the dashboard: "Based on your profile, monitor: blood pressure, fasting glucose."
- Gate high-intensity exercise behind medical clearance for high-risk profiles.
- Adjust calorie floor: never below 1200 kcal (women) / 1500 kcal (men) regardless of deficit target.
- For thyroid conditions: note that metabolism calculations may be inaccurate, suggest regular TSH monitoring.

### 2.7 Female User Perspective

**CRITICAL GAPS:**
- **No cycle-aware UI** — backend has full `cycle/` module + `cycleAdapt.ts` but the client has NO cycle tracking screen, no phase display, no adaptation visibility.
- **No cycle phase in dashboard** — "Day 14 — Ovulation. Energy is high. Good day for PRs."
- **No PMS/symptom logging** — bloating, cramps, mood, energy.
- **No cycle-based nutrition adjustment visibility** — iron needs increase during menstruation, cravings are normal in luteal phase.
- **No pregnancy mode** — completely different calorie/exercise rules.
- **No PCOS-specific plan** — despite collecting the condition.
- **No glute/lower-body emphasis option** — the priority muscles system exists but no UI guidance toward common female goals.

**AI SHOULD:**
- Show cycle phase on dashboard and adjust daily plan: "Luteal phase — expect cravings, +100 kcal today is OK."
- During menstruation: reduce HIIT intensity, suggest iron-rich foods, acknowledge lower energy.
- For PCOS: auto-select anti-inflammatory diet modifications, moderate carb restriction, emphasize strength training.
- Offer "pregnancy mode" that shifts to maintenance + 300 kcal, removes contraindicated exercises.

### 2.8 Male User Perspective

**CRITICAL GAPS:**
- **No testosterone-optimization awareness** — sleep, zinc, compound lifts correlation.
- **No bulk/cut cycle management** — "You've been cutting for 12 weeks. Time for a 2-week diet break at maintenance."
- **No aesthetic physique targets** — shoulder-to-waist ratio, V-taper tracking.
- **No competition prep mode** — bodybuilding/powerlifting meet preparation.

**AI SHOULD:**
- For bulk: cap surplus at +300-500 kcal, monitor waist-to-height ratio (stop bulking if > 0.5).
- For cut: implement diet breaks (2 weeks at maintenance every 8-12 weeks of deficit).
- Track shoulder circumference vs waist ratio as a "V-taper score."

### 2.9 Youth User (Under 18)

**CRITICAL GAPS:**
- **No age-appropriate exercise intensity caps** — minors should not do 1RM testing or extreme deficit diets.
- **No growth-aware nutrition** — teens need more calcium, protein per kg than adults.
- **No parent/guardian involvement** — no shared access or approval flow.
- **No sports-specific training** — "I play cricket/football" → sport-specific conditioning.
- **No screen time / study break reminders** — holistic health for students.

**AI SHOULD:**
- For users < 18: increase protein target to 1.6-2.0g/kg (growth), ensure calcium > 1300mg/day.
- Never set calorie deficit below maintenance for growing teens — body recomposition (same calories, more protein + activity) instead.
- Offer sport-specific templates: cricket (rotational power, running), football (agility, endurance).
- The "cut" physique goal is already blocked — good. Also block "OMAD" eating pattern for minors.

---

## 3. Priority Feature Roadmap

### P0 — Must Have (Next Sprint)

| Feature | Effort | Impact |
|---------|--------|--------|
| Body measurement tracking screen (arms, chest, waist, thighs, calves) + history chart | Medium | Massive — core transformation tracking |
| Measurement goals (set target per body part, see progress) | Low | Enables transformation tracking |
| Progressive overload display ("last time: 10kg → try 12.5kg") | Low | Retention, results |
| Exercise demo images/GIFs (even static illustrations) | High | Safety, beginner retention |
| Cycle tracking UI (expose existing backend) | Medium | Female user retention |
| Micronutrient summary card on dashboard | Medium | Dietician-grade value |

### P1 — Should Have (Next Month)

| Feature | Effort | Impact |
|---------|--------|--------|
| Warm-up / cool-down auto-generation | Low | Injury prevention |
| Workout history graph (weight × exercise over time) | Medium | Motivation |
| Body fat % display (from waist/neck/hip) | Low | Backend ready, just surface it |
| Progress photos vault | Medium | #1 motivator |
| Meal timing around workouts | Low | Performance |
| Exercise substitution engine (equipment-based) | Medium | Accessibility |
| Supplement recommendations based on diet gaps | Low | Value-add |

### P2 — Nice to Have (Next Quarter)

| Feature | Effort | Impact |
|---------|--------|--------|
| 1RM tracking + strength standards | Medium | Experienced users |
| Periodization (hypertrophy/strength/deload cycles) | High | Advanced training |
| Superset / dropset / rest-pause support | Medium | Advanced training |
| Vitals tracking (BP, blood sugar, HR) | Medium | Medical value |
| Plate calculator | Low | Convenience |
| PR celebrations + streaks | Low | Gamification |
| Parent/guardian mode for minors | High | Safety, compliance |
| Competition prep mode | High | Niche but high engagement |

---

## 4. How AI Should Implement Personalization

### 4.1 The Personalization Engine (Backend)

The AI diet/exercise generation should use ALL onboarding data as a unified context:

```
INPUT VECTOR (per user):
- Demographics: age, sex, gender, height, weight, BF%
- Goals: target weight, physique goal, priority muscles, body part measurements + targets
- Constraints: diet type, allergies, conditions, eating pattern, kitchen, budget, equipment
- Training: fitness level, split, gym months, workout duration, exercise location
- Lifestyle: occupation, activity level, smoking, alcohol, sleep times
- Cycle: phase (if tracked), symptoms
- History: food logs (last 7 days), exercise logs (last 7 days), weight trend, measurement trend
- Adherence: discipline score, habit completion, plan vs actual deviation
```

### 4.2 Adaptive Diet Generation

Currently `planGenerator.ts` builds meals from slot weights + food database. It should additionally:

1. **Pattern-aware**: If user consistently skips breakfast, reduce breakfast allocation and redistribute.
2. **Preference-learning**: Track which generated foods get eaten vs skipped. Promote eaten foods, demote skipped.
3. **Seasonal**: Adjust for food availability (mangoes in summer, root vegetables in winter — Indian market context).
4. **Condition-specific macros**:
   - Diabetes: 40% carbs (low GI), 30% protein, 30% fat
   - PCOS: 35% carbs, 30% protein, 35% fat (anti-inflammatory emphasis)
   - Kidney disease: protein cap at 0.8g/kg
   - Hypertension: sodium < 1500mg, potassium-rich foods
5. **Budget-aware portions**: `budgetTier: "low"` → dal/eggs/soya over chicken/whey.
6. **Cooking-aware**: `kitchen: "no_cook"` → only raw/ready-to-eat items.

### 4.3 Adaptive Workout Generation

Currently `workoutGenerator.ts` produces workouts based on split + goal. It should additionally:

1. **Measurement-driven volume**: If user's arms are lagging (13cm vs 16cm target), add 2 extra arm sets per week automatically.
2. **Waist-targeting**: If waist goal requires reduction, add daily 10-min core circuit + increase cardio frequency.
3. **Abs roadmap**: Calculate current BF% → target BF% → weekly deficit → weeks to visible abs. Show this timeline.
4. **Deload detection**: If performance drops for 2 consecutive sessions, auto-insert deload week.
5. **Time-constrained**: If `workoutMinutes: 30`, use supersets and circuits instead of straight sets.
6. **Recovery-aware**: Poor sleep (< 6 hrs logged) → reduce volume by 20%, no HIIT.
7. **Fast-day adjustment**: On fasting days → light yoga/mobility only, no heavy lifting.

### 4.4 Body Transformation Intelligence

For a user wanting: Arms 13cm → 16cm, Waist 40" → 30":

```
AI Analysis:
1. Arms +3cm ≈ +2.5kg muscle on arms → needs ~10kg total muscle gain (arms are ~25% of upper body mass gain)
2. At lean bulk (+300 kcal/day), expect ~2kg muscle/month → 5 months minimum for arms
3. Waist 40→30" = 10" reduction → ~15kg fat loss at minimum
4. CONFLICT: bulk for arms vs cut for waist → SOLUTION: body recomp or phased approach

AI Plan:
Phase 1 (12 weeks): Cut — caloric deficit (-500 kcal), high protein (2g/kg), prioritize compound lifts + arm accessories
  → Expect: waist 40→35", arms may stay 13cm (fat loss offsets small muscle gain)
Phase 2 (12 weeks): Lean bulk — surplus (+300 kcal), PPL split with arm emphasis (4 arm sessions/week)
  → Expect: arms 13→14.5cm, waist 35→36" (slight fat regain is normal)
Phase 3 (8 weeks): Mini-cut — deficit (-400 kcal), maintain training volume
  → Expect: waist 36→33", arms 14.5→14.2cm (minimal muscle loss)
Repeat phases 2-3 until targets hit.

Total estimated timeline: 10-14 months
Measurement checkpoints: every 2 weeks
```

### 4.5 AI Coach Prompt Engineering

The `requestReview()` prompt should be enhanced to include:

```
System prompt additions:
- You are a certified personal trainer + registered dietitian AI.
- User's current measurements: [waist, arms, chest from last log]
- User's measurement targets: [goals if set]
- User's medical conditions: [from profile]
- User's cycle phase: [if tracked]
- Last 7 days adherence: [% from discipline module]
- Recent weight trend: [from body weight logs]
- Detected issues: [plateau, under-eating, skipped workouts]

Response format:
1. Plan score (0-100)
2. Top issue to fix (most impactful single change)
3. One food swap suggestion (Indian-specific)
4. One exercise adjustment
5. Motivational note (reference their progress)
```

---

## 5. UX / Design Notes for Next Session

### Design Rules (ENFORCED)
- NO borders on cards, NO gray borders, NO colored backgrounds
- Cards: `Card(shape = Sharp, elevation = CardDefaults.cardElevation(2.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface))`
- SharpRadius = 8.dp
- Gradient fill for selected/active items
- Compact layouts, no bloat
- Accent system: ThemeStore saves "green"|"pink"|"yellow", accentFor() maps to Accent
- `MoveAccent: Color @Composable get() = MaterialTheme.colorScheme.primary`

### Current Screens
- Onboarding (5 steps: About You, Goals, Movement, Health, Ready)
- Dashboard (PremiumDashboard.kt)
- Nutrition/Food (food logging, barcode, search)
- Movement/Exercise (ExerciseTab, ExerciseLibraryTab, RestTimer)
- Plan (PlanScreen with week grid, food/exercise planning)
- Recovery (sleep tracking)
- Chat/Coach (AI conversation)
- Profile/Settings

### Screens to Add
- **Body Measurements** — log arms, chest, waist, thighs, calves with date. Chart over time. Set goals per part.
- **Cycle Tracker** — calendar view, phase indicator, symptom logging. Backend exists, needs UI.
- **Progress Photos** — date-stamped photo vault with before/after comparison slider.
- **Workout History** — per-exercise weight/volume chart over time. PR highlights.
- **Micronutrient Report** — daily/weekly micro breakdown. Deficiency alerts.
- **Health Risk Dashboard** — surface existing risk module. Show scores + recommendations.

---

## 6. Backend API Gaps

| Endpoint Needed | Purpose |
|-----------------|---------|
| `POST /body/measurements` | Log body part measurements (arms, chest, waist, thighs, calves) |
| `GET /body/measurements/history` | Time series of measurements per body part |
| `POST /body/measurements/goals` | Set target per body part |
| `GET /body/progress-photos` | List progress photos (store URLs) |
| `POST /body/progress-photos` | Upload progress photo with date |
| `GET /exercise/history/:exerciseName` | Weight/volume history for one exercise |
| `GET /exercise/prs` | Personal records across all exercises |
| `GET /nutrients/micro/daily` | Micronutrient breakdown for a date |
| `GET /risk/score` | Health risk score + factors (may already exist) |
| `GET /cycle/phase` | Current cycle phase + adaptation suggestions |
| `POST /cycle/log` | Log cycle day + symptoms |

---

## 7. Business Logic That Is LOCKED (Do Not Change)

- All calculation formulas (BMR, TDEE, macros, hydration, body fat)
- Repository interfaces and database schema
- API contracts (existing endpoints)
- Authentication / token flow
- Health Connect integration
- Barcode scanning behavior
- Camera / vision behavior
- Reminder scheduling logic
- AI provider selection (Gemini/Groq)
- Safety guardrails (`guardrails/`)
- Medical safety logic
- Existing data models (only extend, never modify existing fields)

**EXCEPTION**: Backend improvements for diet distribution, exercise catalog quality, and workout generation are allowed when explicitly requested.

---

## 8. Quick Wins (< 1 Hour Each)

1. **Surface body fat %** — backend `anthropometry.ts` can calculate Navy-method BF% from waist/neck/hip. Just display it on dashboard.
2. **Show "last session" on exercise cards** — query PlanStore for last occurrence of each exercise, show "Last: 12.5kg × 10" in the exercise log dialog.
3. **Warm-up suggestion chip** — add a "5 min warm-up" toggle at top of exercise list that prepends jumping jacks, arm circles, leg swings.
4. **Weekly summary notification** — "This week: 4/7 workouts done, avg 1800 kcal, waist trend: -0.5cm."
5. **Exercise search in Plan screen** — the Plan screen's exercise section uses `exerciseSuggestions(query)` but a search field would help.
6. **Water timestamp** — each glass tap could record time, showing hydration spread across the day.

---

## 9. Waist Exercises Note

The 25 core/waist exercises ARE in the catalog under the **Core (🧱)** category chip. To find them: scroll the category chips rightward past Chest → Back → Shoulders → Arms → Legs → Glutes → **Core**. Or use the search bar: "plank", "crunch", "vacuum", "oblique", "twist", etc.

Exercises include: Plank, Side Plank, Russian Twist, Bicycle Crunch, Leg Raise, Mountain Climbers, Dead Bug, Bird Dog, Hollow Hold, Ab Wheel, Cable Woodchop, Pallof Press, Hanging Knee Raise, Dragon Flag, Stomach Vacuum, Woodchoppers, Standing Oblique Crunch, Seated Twist, Windshield Wipers, V-Up, Toe Touch Crunch, Scissor Kicks, Cross Body Crunch, Spiderman Plank, and more.
