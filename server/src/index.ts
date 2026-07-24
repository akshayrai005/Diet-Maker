import { createApp } from './app';
import { env } from './lib/env';
import { logger } from './lib/logger';
import { prisma } from './lib/prisma';
import { purgeExpiredAccounts } from './modules/account/account.service';

const app = createApp();

const server = app.listen(env.PORT, () => {
  logger.info(
    { port: env.PORT, env: env.NODE_ENV, aiProvider: env.AI_PROVIDER },
    'nutriai-api listening',
  );
});

// Hard-delete accounts whose soft-delete grace window has elapsed. Best-effort daily sweep
// (the free tier may sleep between runs). Runs shortly after boot, then every 24h.
function sweepExpiredAccounts(): void {
  purgeExpiredAccounts()
    .then((n) => {
      if (n > 0) logger.info({ purged: n }, 'purged accounts past the delete grace window');
    })
    .catch((err) => logger.error({ err }, 'account purge failed'));
}
setTimeout(sweepExpiredAccounts, 60_000).unref();
setInterval(sweepExpiredAccounts, 24 * 60 * 60 * 1000).unref();

async function shutdown(signal: string): Promise<void> {
  logger.info({ signal }, 'shutting down');
  server.close(async () => {
    await prisma.$disconnect().catch(() => undefined);
    process.exit(0);
  });
  // Force-exit if graceful shutdown stalls.
  setTimeout(() => process.exit(1), 10_000).unref();
}

process.on('SIGTERM', () => void shutdown('SIGTERM'));
process.on('SIGINT', () => void shutdown('SIGINT'));
