-- CreateTable
CREATE TABLE "reminder_prefs" (
    "userId" TEXT NOT NULL,
    "mealsEnabled" BOOLEAN NOT NULL DEFAULT true,
    "waterEnabled" BOOLEAN NOT NULL DEFAULT true,
    "workoutEnabled" BOOLEAN NOT NULL DEFAULT false,
    "weighInEnabled" BOOLEAN NOT NULL DEFAULT true,
    "walkEnabled" BOOLEAN NOT NULL DEFAULT false,
    "workoutTime" TEXT,
    "waterIntervalMin" INTEGER NOT NULL DEFAULT 60,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "reminder_prefs_pkey" PRIMARY KEY ("userId")
);

-- AddForeignKey
ALTER TABLE "reminder_prefs" ADD CONSTRAINT "reminder_prefs_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;
