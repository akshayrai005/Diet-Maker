import type { NextFunction, Request, Response } from 'express';
import type Redis from 'ioredis';
import { env } from '../lib/env';

interface Bucket {
  count: number;
  resetAt: number;
}

export interface RateLimitOpts {
  windowMs: number;
  max: number;
  keyPrefix?: string;
}

/**
 * Simple fixed-window rate limiter. In-memory (per process). This is the default AND the automatic
 * fallback whenever Redis is unavailable, so limits always apply even if Redis is down.
 */
export function createRateLimit(opts: RateLimitOpts) {
  const buckets = new Map<string, Bucket>();

  function sweep(now: number) {
    if (buckets.size < 5000) return;
    for (const [k, b] of buckets) if (b.resetAt <= now) buckets.delete(k);
  }

  return function rateLimit(req: Request, res: Response, next: NextFunction): void {
    const now = Date.now();
    const ip = req.ip || req.socket.remoteAddress || 'unknown';
    const key = `${opts.keyPrefix ?? ''}${ip}`;

    let b = buckets.get(key);
    if (!b || b.resetAt <= now) {
      b = { count: 0, resetAt: now + opts.windowMs };
      buckets.set(key, b);
    }
    b.count += 1;

    applyHeaders(res, opts.max, Math.max(0, opts.max - b.count), b.resetAt);
    if (b.count > opts.max) {
      reject(res, b.resetAt - now);
      return;
    }
    sweep(now);
    next();
  };
}

function applyHeaders(res: Response, max: number, remaining: number, resetAt: number) {
  res.setHeader('X-RateLimit-Limit', max);
  res.setHeader('X-RateLimit-Remaining', remaining);
  res.setHeader('X-RateLimit-Reset', Math.ceil(resetAt / 1000));
}

function reject(res: Response, msLeft: number) {
  const retryAfter = Math.ceil(msLeft / 1000);
  res.setHeader('Retry-After', retryAfter);
  res.status(429).json({ error: 'Too many requests, please slow down', retryAfter });
}

// ---- Optional Redis backend (for horizontal scaling) with graceful degradation ----

let redisClient: Redis | null = null;
let redisTried = false;

/** Lazily create a shared Redis client if REDIS_URL is configured; null otherwise / on failure. */
function getRedis(): Redis | null {
  if (redisTried) return redisClient;
  redisTried = true;
  if (!env.REDIS_URL) return null;
  try {
    // Require lazily so environments without Redis never pay for it.
    const IORedis = require('ioredis') as typeof import('ioredis').default;
    redisClient = new IORedis(env.REDIS_URL, {
      maxRetriesPerRequest: 1,
      enableOfflineQueue: false,
      lazyConnect: false,
    });
    redisClient.on('error', () => {
      /* swallow — the middleware falls back to in-memory per request */
    });
  } catch {
    redisClient = null;
  }
  return redisClient;
}

/**
 * Rate limiter that uses Redis (shared across instances) when REDIS_URL is set, and transparently
 * falls back to the in-memory limiter when Redis is absent or errors on a request. Same fixed-window
 * semantics either way.
 */
export function createDistributedRateLimit(opts: RateLimitOpts) {
  const fallback = createRateLimit(opts);
  const redis = getRedis();
  if (!redis) return fallback; // no Redis configured → pure in-memory (free tier + tests)

  return async function distributedRateLimit(req: Request, res: Response, next: NextFunction): Promise<void> {
    const now = Date.now();
    const ip = req.ip || req.socket.remoteAddress || 'unknown';
    const key = `rl:${opts.keyPrefix ?? ''}${ip}`;
    try {
      const count = await redis.incr(key);
      if (count === 1) await redis.pexpire(key, opts.windowMs);
      const ttl = await redis.pttl(key);
      const resetAt = now + (ttl > 0 ? ttl : opts.windowMs);
      applyHeaders(res, opts.max, Math.max(0, opts.max - count), resetAt);
      if (count > opts.max) {
        reject(res, resetAt - now);
        return;
      }
      next();
    } catch {
      // Redis hiccup → never fail open silently on limits; use the in-memory limiter instead.
      fallback(req, res, next);
    }
  };
}
