-- AlterTable: kitchen access a food needs (PG / no-cook planning). Default 'stove' = needs a hob.
ALTER TABLE "foods" ADD COLUMN "prep" TEXT NOT NULL DEFAULT 'stove';

-- Backfill: assemble-only / ready-to-eat staples (no cooking).
UPDATE "foods" SET "prep" = 'none'
WHERE "id" IN (
  'banana', 'apple', 'soaked-almonds', 'peanuts', 'roasted-chana',
  'curd', 'buttermilk', 'paneer', 'sprouts-salad',
  'sattu', 'muesli', 'bread-pb', 'whey-shake', 'soaked-oats',
  'cold-milk', 'roasted-makhana', 'dry-fruits-mix'
);

-- Backfill: needs hot water only (kettle / immersion rod).
UPDATE "foods" SET "prep" = 'kettle'
WHERE "id" IN (
  'green-tea', 'warm-milk', 'turmeric-milk', 'oats-porridge', 'boiled-egg', 'instant-poha-cup'
);

-- Everything else keeps the 'stove' default (dals, sabzis, rice, roti, dosa, non-veg, …).
