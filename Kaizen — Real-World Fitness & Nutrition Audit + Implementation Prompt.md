You are continuing development of the Kaizen / NutriAI / Diet-Maker app.

I want you to perform a **real-world usability and data-consistency upgrade**, not just add isolated features.

The existing product specification/review document is attached/provided in the project. Read it completely before making changes and respect the existing architecture, business logic, API contracts, repository interfaces, authentication, Health Connect integration, safety guardrails, and existing data models. Existing fields must be extended rather than broken or renamed unless absolutely necessary.

The biggest issue now is that the app looks feature-rich on paper, but when I actually use it day-to-day at the gym and while preparing my diet, I find practical loopholes.

Your job is to identify and fix these issues systematically.

---

# 1. FIRST: AUDIT THE EXISTING CODE

Before coding:

1. Inspect the current Android/client implementation.
2. Inspect backend modules and APIs.
3. Inspect the food/nutrition data model.
4. Inspect exercise catalog and exercise types.
5. Inspect dashboard nutrition calculations.
6. Inspect Daily Diet calculations.
7. Inspect Plan calculations.
8. Inspect workout logging.
9. Inspect calorie-burn calculations.
10. Inspect how planned vs actual data is represented.
11. Identify every place where calories/protein/carbs/fat are calculated separately.

Do NOT assume the existing documentation accurately reflects the current implementation.

Create a concise internal implementation plan and then implement it.

The priority is **correctness and real-world usability**, not simply increasing the feature count.

---

# 2. CRITICAL: ONE SOURCE OF TRUTH FOR NUTRITION

There must be exactly one authoritative nutrition calculation path.

Currently there is a real-world problem where protein/macros shown in Daily Diet can differ from the Dashboard.

This must be eliminated.

Create/use a centralized nutrition calculation service/repository.

The same nutrition result must feed:

- Dashboard
- Daily Diet
- Food Logger
- Meal Plan
- Weekly Plan
- High Protein section
- AI Coach
- Reports
- Remaining macros
- Calorie budget
- Protein target
- Carbs target
- Fat target
- Fiber
- Micronutrients where applicable

Do NOT allow each screen to independently calculate macros.

If the same food and quantity are logged, every screen must show the same values.

Add automated tests specifically checking:

> Dashboard protein == Daily Diet protein == logged food total protein

and the same for calories, carbs and fat.

---

# 3. RAW VS COOKED FOOD MUST BE EXPLICIT

This is a major nutrition accuracy issue.

The food system must distinguish:

- Raw
- Cooked
- Dry
- Prepared

Examples:

- Rice 100g raw
- Rice 100g cooked
- Dal 100g dry
- Dal 100g cooked
- Chicken 100g raw
- Chicken 100g cooked
- Soya chunks 100g dry
- Soya chunks prepared

Never silently treat raw and cooked quantities as interchangeable.

The UI must clearly display the state.

Example:

Chicken Breast
> 100g cooked

Calories: X
Protein: Xg
Carbs: Xg
Fat: Xg

And:

> 100g raw

with its own values.

---

# 4. ADD A PROPER HIGH-PROTEIN FOOD SECTION

Create a dedicated:

## HIGH PROTEIN

section/tab/filter.

Every food should show standardized nutrition.

Required columns:

- Food
- Serving
- Calories
- Protein (P)
- Carbohydrates (C)
- Fat (F)
- Fiber
- Optional important micronutrients

Default reference should be:

> Per 100g

but also allow normal serving sizes.

Example structure:

| Food | Per 100g | kcal | P | C | F | Fiber |
|---|---|---:|---:|---:|---:|---:|
| Soya chunks | 100g dry | — | — | — | — | — |
| Chicken breast | 100g cooked | — | — | — | — | — |
| Eggs | 100g | — | — | — | — | — |
| Paneer | 100g | — | — | — | — | — |
| Tofu | 100g | — | — | — | — | — |
| Greek yogurt | 100g | — | — | — | — | — |
| Dal | 100g cooked | — | — | — | — | — |
| Rajma | 100g cooked | — | — | — | — | — |
| Chana | 100g cooked | — | — | — | — | — |

Do not hardcode fake nutrition numbers. Use the existing authoritative food data source/database and normalize it.

Allow sorting/filtering by:

- Highest protein
- Protein per 100g
- Protein per 100 kcal
- Lowest calories
- Vegetarian
- Non-vegetarian
- Vegan
- Budget-friendly

Most importantly, allow:

> + Add to Today's Diet

and immediately update the same centralized nutrition totals.

---

# 5. HIGH-PROTEIN SECTION MUST ALSO BE A PLANNING TOOL

Don't make it merely a static food list.

If the user has:

> Protein remaining: 72g

the screen should help them select foods to close that gap.

Example:

> You need approximately 72g more protein today.

Show food combinations and allow the user to add them directly.

The user should be able to select quantity:

- 50g
- 100g
- 150g
- 200g
- Custom grams

and see:

> Protein +Xg
> Calories +X
> Carbs +Xg
> Fat +Xg

before adding.

---

# 6. EXERCISE LIBRARY IS NOT REALISTIC ENOUGH

The exercise system must cover actual gym usage.

Do a full audit of the exercise catalog.

Add/verify proper entries for:

## CARDIO

- Treadmill Walk
- Incline Treadmill Walk
- Treadmill Jog
- Treadmill Run
- Outdoor Walk
- Brisk Walk
- Outdoor Run
- Stationary Bike
- Outdoor Cycling
- Elliptical
- Stair Climber
- Rowing
- Swimming
- Jump Rope

For treadmill specifically support:

- Duration
- Speed
- Incline
- Distance

Do not represent all treadmill activity as simply "Cardio."

---

# 7. ADD NO-EQUIPMENT EXERCISES

Normal gym/home workouts frequently contain exercises without equipment.

Add comprehensive bodyweight/no-equipment exercises.

Examples:

## Chest
- Push-up
- Wide Push-up
- Incline Push-up
- Decline Push-up
- Diamond Push-up

## Legs
- Bodyweight Squat
- Jump Squat
- Reverse Lunge
- Walking Lunge
- Bulgarian Split Squat
- Step-up
- Wall Sit
- Bodyweight Calf Raise

## Core
- Plank
- Side Plank
- Dead Bug
- Bird Dog
- Mountain Climbers
- Leg Raise
- Crunch
- Bicycle Crunch
- Hollow Hold

## Full Body
- Burpee
- Bear Crawl
- High Knees
- Jumping Jacks
- Mountain Climbers

The exercise catalog should classify exercises by:

- Muscle group
- Equipment required
- Exercise type
- Difficulty
- Movement pattern
- Cardio/strength/core/mobility/HIIT
- Logging mode

---

# 8. EQUIPMENT FILTER

The user should be able to specify what equipment is available TODAY.

Options:

- Full Gym
- Machines
- Cable
- Barbell
- Dumbbells
- Bench
- Resistance Bands
- Home Equipment
- No Equipment
- Outdoor

The workout generator must respect this.

If user selects:

> No Equipment

the AI must not generate:

> Cable Fly
> Lat Pulldown
> Leg Press
> Smith Machine Squat

unless the user explicitly overrides the equipment restriction.

---

# 9. MIXED WORKOUT SUPPORT — VERY IMPORTANT

A real workout is often mixed.

Example:

5 min treadmill
+
Bench Press
+
Lat Pulldown
+
Squats
+
10 min incline walk
+
Stretching

The app must allow the user to create one workout containing multiple activity types.

Example:

## TODAY'S WORKOUT

1. Treadmill Walk
   10 min
   5.5 km/h
   5% incline

2. Bench Press
   4 × 10
   40kg

3. Lat Pulldown
   3 × 12
   35kg

4. Squat
   4 × 8
   50kg

5. Incline Treadmill Walk
   10 min
   5 km/h
   8% incline

6. Stretching
   5 min

The user should be able to:

> + Add Exercise
> + Add Cardio
> + Add Activity

to the same session.

The entire session should have:

- Total duration
- Estimated calories
- Exercises completed
- Volume where applicable
- Cardio details
- Completion percentage

---

# 10. DYNAMIC EXERCISE LOGGING

Do NOT use one logging form for every exercise.

Different exercise types require different inputs.

### Strength

Sets
Reps
Weight

### Bodyweight

Sets
Reps

### Isometric

Sets
Duration

### Treadmill

Duration
Speed
Incline
Distance

### Cycling

Duration
Distance
Resistance/level

### Walking

Duration
Distance
Pace

### HIIT

Rounds
Work duration
Rest duration

The UI should dynamically display the appropriate fields based on exercise type.

---

# 11. PLANNED VS ACTUAL MUST BE SEPARATE

This applies to both nutrition and exercise.

If the plan says:

> Protein: 150g

but user actually eats:

> 123g

show:

Planned: 150g
Consumed: 123g
Remaining: 27g

Similarly:

Workout planned:
> 5 exercises

Actually completed:
> 3 exercises

Show:

> Workout adherence: 60%

Calories burned should be based on actual logged activity whenever possible, not merely the planned workout.

---

# 12. HOMEMADE FOOD / RECIPE BUILDER

Real users don't eat only individual database foods.

Add:

## Create Recipe

User can enter:

- Ingredient
- Quantity
- Raw/cooked state
- Cooking oil
- Ghee
- Butter
- Other ingredients

Example:

Paneer Bhurji

Paneer — 200g
Onion — 100g
Tomato — 100g
Oil — 10g

Calculate total recipe nutrition.

Then allow:

> I ate 40%

or:

> I ate 150g

and calculate actual consumed nutrition.

Recipes should be saveable and reusable.

---

# 13. COOKING OIL MUST NOT BE IGNORED

Track:

- Oil
- Ghee
- Butter
- Sauces
- Dressings
- Other calorie-dense cooking ingredients

These should be included in recipe nutrition.

Do not assume "chicken" or "paneer" contains only the base ingredient nutrition when the user logs a prepared dish.

---

# 14. SERVING/QUANTITY SYSTEM

Support practical Indian food units:

- grams
- kg
- ml
- litre
- piece
- egg
- roti
- chapati
- bowl
- cup
- tablespoon
- teaspoon
- scoop

Internally normalize quantities to a consistent base unit so calculations remain accurate.

---

# 15. SMART FOOD SEARCH

Users should not need to know exact database names.

Search:

> treadmill

should find treadmill exercises.

Search:

> walking

should find walking-related activities.

Search:

> abs

should find core exercises.

Search:

> no equipment chest

should filter to suitable bodyweight chest exercises.

Search should support aliases/synonyms.

---

# 16. QUICK LOG MODE

When the user is actually at the gym, speed matters.

Create a fast logging experience:

## QUICK LOG

### Workout
- Add Exercise
- Add Cardio
- Add Activity
- Add Set
- Complete Workout

### Food
- Scan
- Search
- Recent
- Favorites
- High Protein
- Quick Add

Minimize unnecessary navigation.

---

# 17. RECENT FOODS AND RECENT EXERCISES

Show recently used items prominently.

If the user frequently logs:

- Eggs
- Chicken
- Paneer
- Rice
- Roti

they should be one-tap additions.

Same for exercises.

---

# 18. "WHAT SHOULD I EAT NOW?"

Add a context-aware recommendation action.

The system should consider:

- Daily calorie target
- Calories consumed
- Protein remaining
- Carbs remaining
- Fat remaining
- Workout time
- Workout completed
- Diet preference
- Budget
- Food preferences
- Foods already eaten
- Available foods

Example:

> You need ~40g protein and ~50g carbs.

Suggestions:

1. Chicken + rice
2. Paneer + roti
3. Soya + rice
4. Eggs + toast

Allow:

> Add to today's diet

with the exact calculated nutrition.

---

# 19. "WHAT CAN I MAKE WITH WHAT I HAVE?"

Add a useful AI flow:

User enters:

> I have eggs, bread, paneer and oats.

AI generates suitable meals using those ingredients while respecting:

- Remaining calories
- Protein target
- Diet type
- Goal
- Remaining macros
- Allergies/restrictions

User can select:

> Add meal to today's diet

---

# 20. PRE-WORKOUT / POST-WORKOUT NUTRITION

Nutrition and exercise should communicate.

If workout is at 7 AM, recommendations should differ from an 8 PM workout.

The system should provide context-aware:

- Pre-workout meal suggestions
- Post-workout meal suggestions
- Hydration guidance

Use the user's actual workout schedule.

---

# 21. GROCERY LIST FROM MEAL PLAN

Generate a weekly grocery list from the actual meal plan.

Example:

Chicken — 1.5kg
Paneer — 500g
Rice — 1kg
Oats — 500g
Soya — 250g

Allow the user to mark:

> Already have

and subtract it from the required shopping quantity.

---

# 22. NUTRITION DATA VALIDATION

Add validation rules to prevent impossible or suspicious data.

Examples:

- Protein cannot be negative.
- Calories cannot be negative.
- Macro totals should be internally consistent.
- Serving quantity must be positive.
- Raw/cooked state must be explicit where relevant.
- Food entries should have a reliable source.
- Don't silently mix serving units.

Where exact nutrition values are uncertain, clearly identify them as estimates.

---

# 23. CALORIE BURN SHOULD BE ESTIMATED, NOT PRETENDED TO BE EXACT

For exercise calorie expenditure, use the existing calculation architecture where possible.

Differentiate:

- Strength training
- Walking
- Running
- Treadmill
- Cycling
- HIIT
- Mobility
- Yoga

Use relevant inputs such as:

- body weight
- duration
- intensity
- speed
- incline
- distance
- exercise type

Display:

> Estimated calories burned

rather than presenting an inherently estimated number as exact.

---

# 24. DASHBOARD RECONCILIATION

Create a clear consistency test.

For the same date:

Food logs → Nutrition service → Dashboard
Food logs → Nutrition service → Daily Diet
Food logs → Nutrition service → Weekly summary

All must agree.

Example:

If Daily Diet says:

Protein = 123g

Dashboard must say:

Protein = 123g

There must be no discrepancy.

---

# 25. DATABASE / BACKEND REQUIREMENT

Before changing the schema, inspect the existing schema carefully.

Respect the project rule:

> Existing data models should be extended, not unnecessarily modified.

If new exercise metadata is needed, extend the model safely.

If new food metadata is needed, extend safely.

Do not break existing API contracts.

Do not replace working authentication, Health Connect, safety guardrails, AI provider configuration, or other locked business logic.

---

# 26. TEST REAL USER SCENARIOS

After implementation, test these complete flows.

### Scenario A — Gym

User enters gym.

1. Starts workout.
2. Adds 5-minute treadmill walk.
3. Adds bench press.
4. Adds lat pulldown.
5. Adds squats.
6. Adds 10-minute incline treadmill walk.
7. Adds stretching.
8. Completes workout.

Verify that the mixed workout works correctly.

---

### Scenario B — No equipment

User selects:

> No Equipment

Generate workout.

Verify there are ZERO exercises requiring unavailable equipment.

---

### Scenario C — High protein planning

User opens High Protein.

Finds:

> Soya chunks

Selects:

> 50g

Adds it.

Verify calories/P/C/F update everywhere.

---

### Scenario D — Homemade meal

User creates:

> Paneer Bhurji

Adds paneer, vegetables and oil.

Eats 50%.

Verify nutrition is exactly half the recipe total.

---

### Scenario E — Dashboard consistency

Log several foods.

Verify:

Dashboard = Daily Diet = Food Logger = Plan totals.

---

### Scenario F — Raw vs cooked

Log:

100g raw rice

and separately:

100g cooked rice.

Verify they are treated as different nutrition entries.

---

### Scenario G — Actual vs planned

Plan:

150g protein.

User eats:

120g.

Verify:

Planned = 150g
Consumed = 120g
Remaining = 30g

---

# 27. DO NOT JUST PATCH THE UI

Important:

If you find a discrepancy caused by backend/data architecture, fix the underlying source rather than adding UI-specific corrections.

Do not:

- hardcode numbers into Dashboard
- hardcode exercise calories
- duplicate food nutrition tables
- create separate macro calculations for each screen
- add fake exercise entries just to make search appear complete

Fix the underlying architecture.

---

# 28. PRIORITY ORDER

Implement in this order:

## P0 — MUST FIX

1. Nutrition single source of truth
2. Dashboard/Daily Diet macro reconciliation
3. Raw vs cooked food handling
4. High Protein tab/database
5. High Protein planning
6. Treadmill + walking exercise types
7. Mixed workouts
8. No-equipment exercises
9. Equipment filtering
10. Dynamic exercise logging
11. Planned vs actual food
12. Planned vs actual exercise
13. Homemade recipe builder
14. Cooking oil tracking
15. Quick Log

## P1

16. Smart search
17. Recent foods
18. Recent exercises
19. What should I eat now?
20. What can I make with what I have?
21. Pre/post-workout nutrition
22. Grocery list
23. Better calorie-burn estimation

Do NOT spend time on lower-priority cosmetic improvements until P0 correctness is complete.

---

# 29. FINAL REQUIREMENT

When finished, provide:

1. What you audited.
2. What was actually broken/missing.
3. What you changed.
4. Files modified.
5. Backend changes.
6. Database changes, if any.
7. New APIs/endpoints, if any.
8. Tests added.
9. Test results.
10. Any remaining known loopholes.

Most importantly:

**Do not tell me that a feature exists just because a screen or backend function exists. Verify that the complete user flow actually works.**

The goal is:

> When I wake up, prepare my diet, go to the gym, perform a mixed workout, log food, plan my next meal, and check my dashboard, the app should behave consistently and practically without me having to work around it manually.

Treat this as a **real-world product quality and correctness sprint**, not a cosmetic feature sprint.