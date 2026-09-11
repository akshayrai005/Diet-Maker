-- Homemade recipe builder: user-created recipes with computed nutrition, logged as a scaled
-- portion of the saved batch.
CREATE TABLE "user_recipes" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "ingredients" JSONB NOT NULL,
    "totalGrams" DOUBLE PRECISION NOT NULL,
    "kcal" DOUBLE PRECISION NOT NULL,
    "proteinG" DOUBLE PRECISION NOT NULL,
    "carbG" DOUBLE PRECISION NOT NULL,
    "fatG" DOUBLE PRECISION NOT NULL,
    "fiberG" DOUBLE PRECISION NOT NULL,
    "sugarG" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "sodiumMg" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "user_recipes_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "user_recipes_userId_idx" ON "user_recipes"("userId");

ALTER TABLE "user_recipes" ADD CONSTRAINT "user_recipes_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;
