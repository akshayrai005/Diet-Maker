import { PrismaClient } from '@prisma/client';
import { SEED_FOODS } from '../src/data/foods.seed';
import { MICRONUTRIENTS_PER_100G } from '../src/data/micronutrientData';
import { inferPrep } from '../src/modules/food/prep';

const prisma = new PrismaClient();

async function main() {
  console.log(`Seeding ${SEED_FOODS.length} foods...`);
  let withMicros = 0;
  for (const f of SEED_FOODS) {
    // Measured micronutrients for this food (absent keys stay NULL — unknown, not zero).
    const micros = MICRONUTRIENTS_PER_100G[f.id] ?? {};
    if (Object.keys(micros).length) withMicros++;
    await prisma.food.upsert({
      where: { id: f.id },
      update: {
        ...micros,
        name: f.name,
        locale: f.locale,
        region: f.region ?? null,
        category: f.category,
        mealSlots: f.mealSlots,
        kcal: f.kcal,
        proteinG: f.proteinG,
        carbG: f.carbG,
        fatG: f.fatG,
        fiberG: f.fiberG,
        sugarG: f.sugarG,
        sodiumMg: f.sodiumMg,
        glycemicIndex: f.glycemicIndex ?? null,
        typicalServingG: f.typicalServingG,
        costTier: f.costTier,
        tags: f.tags,
        allergens: f.allergens,
        prep: inferPrep(f),
        source: 'seed',
      },
      create: {
        ...micros,
        id: f.id,
        name: f.name,
        locale: f.locale,
        region: f.region ?? null,
        category: f.category,
        mealSlots: f.mealSlots,
        kcal: f.kcal,
        proteinG: f.proteinG,
        carbG: f.carbG,
        fatG: f.fatG,
        fiberG: f.fiberG,
        sugarG: f.sugarG,
        sodiumMg: f.sodiumMg,
        glycemicIndex: f.glycemicIndex ?? null,
        typicalServingG: f.typicalServingG,
        costTier: f.costTier,
        tags: f.tags,
        allergens: f.allergens,
        prep: inferPrep(f),
        source: 'seed',
      },
    });
  }
  const count = await prisma.food.count();
  console.log(`Done. Foods in DB: ${count} (${withMicros} with measured micronutrients).`);
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(() => prisma.$disconnect());
