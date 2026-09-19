-- Saved AI recipes (shared) and the exercise library. Both additive; IF NOT EXISTS keeps re-runs safe.
CREATE TABLE IF NOT EXISTS "recipe_cache" (
    "id" TEXT NOT NULL,
    "key" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "timeMin" INTEGER,
    "servings" INTEGER,
    "ingredients" JSONB NOT NULL,
    "steps" JSONB NOT NULL,
    "source" TEXT NOT NULL DEFAULT 'ai',
    "hits" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "recipe_cache_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX IF NOT EXISTS "recipe_cache_key_key" ON "recipe_cache"("key");

CREATE TABLE IF NOT EXISTS "exercise_library" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "muscle" TEXT NOT NULL,
    "bodyPart" TEXT NOT NULL,
    "equipment" TEXT NOT NULL,
    "category" TEXT NOT NULL,
    "secondaryMuscles" TEXT[],
    "instructions" JSONB NOT NULL,
    "gifPath" TEXT NOT NULL,
    "thumbPath" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "exercise_library_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "exercise_library_muscle_idx" ON "exercise_library"("muscle");
CREATE INDEX IF NOT EXISTS "exercise_library_equipment_idx" ON "exercise_library"("equipment");
