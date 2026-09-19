/**
 * Loads the exercise library into the `exercise_library` table (idempotent - safe to re-run).
 *
 *   node scripts/import-exercise-library.js <media-repo-dir> [instructions.json]
 *
 * <media-repo-dir>  a checkout of akshayrai005/kaizen-exercise-media (needs api/en/exercises.json)
 * instructions.json defaults to ../android/app/src/main/assets/exercise_instructions.json (id -> steps)
 *
 * Connects with DATABASE_URL like the rest of the server. Never prints the connection string.
 */
const fs = require('node:fs');
const path = require('node:path');
const { PrismaClient } = require('@prisma/client');

const mediaDir = process.argv[2];
if (!mediaDir) {
  console.error('usage: node scripts/import-exercise-library.js <media-repo-dir> [instructions.json]');
  process.exit(1);
}
const instrFile = process.argv[3] || path.join(__dirname, '..', '..', 'android', 'app', 'src', 'main', 'assets', 'exercise_instructions.json');

const dataset = JSON.parse(fs.readFileSync(path.join(mediaDir, 'api', 'en', 'exercises.json'), 'utf8'));
const rows = Array.isArray(dataset) ? dataset : dataset.exercises || Object.values(dataset)[0];
const instructions = fs.existsSync(instrFile) ? JSON.parse(fs.readFileSync(instrFile, 'utf8')) : {};

const prisma = new PrismaClient();

(async () => {
  let n = 0;
  let withSteps = 0;
  for (const r of rows) {
    const steps = instructions[r.id] || [];
    if (steps.length) withSteps++;
    const data = {
      name: r.name,
      muscle: r.muscle,
      bodyPart: r.bodyPart,
      equipment: r.equipment,
      category: r.category,
      secondaryMuscles: r.secondaryMuscles || [],
      instructions: steps,
      gifPath: r.file || `${r.id}.gif`,
      thumbPath: r.id ? `${r.id}.thumb.webp` : null,
    };
    await prisma.exerciseLibrary.upsert({ where: { id: r.id }, create: { id: r.id, ...data }, update: data });
    n++;
  }
  console.log(`exercise_library: ${n} exercises upserted, ${withSteps} with step-by-step instructions`);
  await prisma.$disconnect();
})().catch((e) => {
  console.error('import failed:', String(e.message || e).slice(0, 300));
  process.exit(1);
});
