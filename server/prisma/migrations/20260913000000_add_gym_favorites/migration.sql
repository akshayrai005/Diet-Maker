-- CreateTable
CREATE TABLE "gym_favorites" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "exerciseName" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "gym_favorites_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "gym_favorites_userId_idx" ON "gym_favorites"("userId");

-- CreateIndex
CREATE UNIQUE INDEX "gym_favorites_userId_exerciseName_key" ON "gym_favorites"("userId", "exerciseName");

-- AddForeignKey
ALTER TABLE "gym_favorites" ADD CONSTRAINT "gym_favorites_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;
