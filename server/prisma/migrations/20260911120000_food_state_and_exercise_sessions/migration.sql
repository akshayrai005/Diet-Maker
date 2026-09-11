-- Food: explicit raw/cooked/dry/prepared state, plus an optional link to the paired
-- raw<->cooked entry for the same underlying food.
ALTER TABLE "foods" ADD COLUMN "state" TEXT NOT NULL DEFAULT 'as_is';
ALTER TABLE "foods" ADD COLUMN "stateOfId" TEXT;

-- Backfill: foods whose per-100g values are already cooked-basis (name says "(cooked)" etc).
UPDATE "foods" SET "state" = 'cooked'
WHERE "name" ILIKE '%(cooked)%' OR "name" ILIKE '%cooked)%';

-- ExerciseLog: group entries logged in one mixed workout into a single session.
ALTER TABLE "exercise_logs" ADD COLUMN "sessionId" TEXT;
CREATE INDEX "exercise_logs_userId_sessionId_idx" ON "exercise_logs"("userId", "sessionId");
