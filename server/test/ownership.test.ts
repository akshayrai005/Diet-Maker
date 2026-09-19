import { describe, it, expect } from 'vitest';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

/**
 * Static guard against IDOR regressions: any prisma call that targets a row by a bare `id` (no userId/ownerId in
 * the same where-clause) must be on the reviewed allowlist below - each entry is a call that runs only AFTER an
 * ownership lookup (`findFirst({ where: { id, userId } })`), or acts on a global/shared table.
 * Adding a new bare-id call fails this test until someone reviews it and adds it here on purpose.
 */
const ROOT = join(__dirname, '..', 'src');

function walk(dir: string): string[] {
  return readdirSync(dir).flatMap((n) => {
    const p = join(dir, n);
    return statSync(p).isDirectory() ? walk(p) : p.endsWith('.ts') ? [p] : [];
  });
}

const BARE_ID = /prisma\.(\w+)\.(update|delete|findUnique|deleteMany|updateMany)\(\{\s*where: \{ ?id[:,]?[^}]*\}/g;

/** file (relative, forward slashes) -> models whose bare-id calls were reviewed as safe. */
const REVIEWED: Record<string, string[]> = {
  'modules/auth/auth.service.ts': ['user', 'refreshToken'], // own record, looked up from a verified token
  'modules/coach/coach.routes.ts': ['user'], // the caller's own record (id taken from the verified token)
  'modules/body/body.service.ts': ['bodyMetric', 'bodyPhoto'], // preceded by findFirst({ id, userId })
  'modules/cycle/cycle.service.ts': ['periodLog'], // id comes from a userId-scoped query
  'modules/food/plan.service.ts': ['dietPlan'], // planRow fetched by userId
  'modules/logging/logging.service.ts': ['food'], // shared, read-only food catalogue
  'modules/medications/medications.service.ts': ['medication'], // preceded by findFirst({ id, userId })
  'modules/recipe/userRecipe.service.ts': ['userRecipe'], // followed by recipe.userId !== userId check
};

describe('ownership: no unreviewed bare-id database access', () => {
  it('every id-only prisma call is on the reviewed allowlist', () => {
    const offenders: string[] = [];
    for (const file of walk(ROOT)) {
      const rel = file.slice(ROOT.length + 1).replace(/\\/g, '/');
      const src = readFileSync(file, 'utf8');
      for (const m of src.matchAll(BARE_ID)) {
        const model = m[1]!;
        if (/userId|ownerId/.test(m[0])) continue;
        if (!(REVIEWED[rel] ?? []).includes(model)) offenders.push(`${rel}: prisma.${model}.${m[2]} by bare id`);
      }
    }
    expect(offenders, offenders.join('\n')).toEqual([]);
  });

  it('the scan actually finds calls (guards against the regex silently matching nothing)', () => {
    let hits = 0;
    for (const file of walk(ROOT)) hits += [...readFileSync(file, 'utf8').matchAll(BARE_ID)].length;
    expect(hits).toBeGreaterThan(5);
  });

  it('every reviewed entry that says "preceded by findFirst" really has the ownership lookup in that file', () => {
    for (const rel of ['modules/body/body.service.ts', 'modules/medications/medications.service.ts']) {
      const src = readFileSync(join(ROOT, rel), 'utf8');
      expect(/findFirst\(\{ where: \{ id, userId \} \}\)/.test(src), rel).toBe(true);
    }
  });
});
