-- ExerciseLog: cardio metrics (speed/incline/distance) so treadmill/walk/run entries carry more
-- than a bare duration, and the calorie estimate can be speed+incline aware.
ALTER TABLE "exercise_logs" ADD COLUMN "speedKmh" DOUBLE PRECISION;
ALTER TABLE "exercise_logs" ADD COLUMN "inclinePct" DOUBLE PRECISION;
ALTER TABLE "exercise_logs" ADD COLUMN "distanceKm" DOUBLE PRECISION;
