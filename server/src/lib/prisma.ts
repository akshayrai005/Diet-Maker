import { PrismaClient } from '@prisma/client';
import { env } from './env';

/**
 * Single shared PrismaClient. In dev we stash it on globalThis so hot-reload (tsx watch)
 * doesn't exhaust the connection pool by creating a new client on every reload.
 */
const globalForPrisma = globalThis as unknown as { prisma?: PrismaClient };

export const prisma =
  globalForPrisma.prisma ??
  new PrismaClient({
    log: env.NODE_ENV === 'development' ? ['warn', 'error'] : ['error'],
  });

// Resilience for serverless Postgres (Neon): its compute can drop idle connections, so the first
// query after an idle period — and often a burst of parallel queries (e.g. the dashboard's ~8) —
// can fail with "connection Closed" / P1001 / P1017. Retry such transient connection errors a couple
// of times with a short backoff; Prisma reconnects on the retry. Real query errors still surface.
const TRANSIENT = /Closed|P1001|P1017|Timed out|ECONNRESET|Connection reset|terminating connection/i;
if (!globalForPrisma.prisma) {
  prisma.$use(async (params, next) => {
    let lastErr: unknown;
    for (let attempt = 0; attempt < 3; attempt++) {
      try {
        return await next(params);
      } catch (e) {
        lastErr = e;
        if (!TRANSIENT.test(String((e as { message?: string })?.message ?? ''))) throw e;
        await new Promise((r) => setTimeout(r, 150 * (attempt + 1)));
      }
    }
    throw lastErr;
  });
}

if (env.NODE_ENV !== 'production') {
  globalForPrisma.prisma = prisma;
}
