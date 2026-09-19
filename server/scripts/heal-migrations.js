/**
 * Runs during the Render build, right before `prisma migrate deploy`.
 * 20260919000000_exercise_log_rir first shipped with the wrong table name, so it failed on the live database and left
 * a "failed" record that makes Prisma refuse every later migration (P3009). Marking it rolled back lets the corrected
 * SQL run. On a database where it never failed (or a fresh one) this is a harmless no-op, so errors are ignored.
 */
const { spawnSync } = require('node:child_process');

const NAME = '20260919000000_exercise_log_rir';
if (!process.env.DATABASE_URL) {
  console.log('[heal-migrations] no DATABASE_URL - skipping');
  process.exit(0);
}
const r = spawnSync('npx', ['prisma', 'migrate', 'resolve', '--rolled-back', NAME], { stdio: 'inherit', shell: process.platform === 'win32' });
console.log(`[heal-migrations] resolve exited ${r.status} (non-zero is fine when the migration was not in a failed state)`);
process.exit(0);
