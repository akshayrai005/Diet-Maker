-- Reps in reserve (0 = to failure) for a logged set; optional so old logs and quick logs stay valid.
-- (The table is mapped to "exercise_logs"; IF NOT EXISTS makes a re-run safe.)
ALTER TABLE "exercise_logs" ADD COLUMN IF NOT EXISTS "rir" INTEGER;
