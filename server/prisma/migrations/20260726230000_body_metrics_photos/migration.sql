-- CreateTable
CREATE TABLE "body_metrics" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "measuredAt" TIMESTAMP(3) NOT NULL,
    "measurementsEnc" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "body_metrics_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "body_photos" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "takenAt" TIMESTAMP(3) NOT NULL,
    "localRef" TEXT NOT NULL,
    "captionEnc" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "body_photos_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "body_metrics_userId_measuredAt_idx" ON "body_metrics"("userId", "measuredAt");

-- CreateIndex
CREATE INDEX "body_photos_userId_takenAt_idx" ON "body_photos"("userId", "takenAt");

-- AddForeignKey
ALTER TABLE "body_metrics" ADD CONSTRAINT "body_metrics_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "body_photos" ADD CONSTRAINT "body_photos_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;
