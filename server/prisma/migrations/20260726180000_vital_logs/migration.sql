-- CreateTable
CREATE TABLE "vital_logs" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "unit" TEXT NOT NULL,
    "measuredAt" TIMESTAMP(3) NOT NULL,
    "valueEnc" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "vital_logs_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "vital_logs_userId_type_measuredAt_idx" ON "vital_logs"("userId", "type", "measuredAt");

-- AddForeignKey
ALTER TABLE "vital_logs" ADD CONSTRAINT "vital_logs_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;
