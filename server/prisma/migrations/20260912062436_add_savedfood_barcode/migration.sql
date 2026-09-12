-- AlterTable
ALTER TABLE "saved_foods" ADD COLUMN     "barcode" TEXT;

-- CreateIndex
CREATE INDEX "saved_foods_userId_barcode_idx" ON "saved_foods"("userId", "barcode");
