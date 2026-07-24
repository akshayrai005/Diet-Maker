-- AlterTable: soft-delete timestamp for the account-deletion grace period
ALTER TABLE "users" ADD COLUMN "deletedAt" TIMESTAMP(3);
