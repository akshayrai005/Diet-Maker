-- Reps in reserve (0 = to failure) for a logged set; optional so old logs and quick logs stay valid.
ALTER TABLE "ExerciseLog" ADD COLUMN "rir" INTEGER;
