import type { NextFunction, Request, Response } from 'express';
import { ZodError } from 'zod';
import { logger } from '../lib/logger';
import { isProd } from '../lib/env';

/** Thrown by app code to produce a specific HTTP status. */
export class HttpError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly details?: unknown,
  ) {
    super(message);
    this.name = 'HttpError';
  }
}

/** 404 for unmatched routes. */
export function notFound(_req: Request, res: Response): void {
  res.status(404).json({ error: 'Not Found' });
}

/** Central error handler - must have 4 args for Express to treat it as such. */
export function errorHandler(
  err: unknown,
  _req: Request,
  res: Response,
  _next: NextFunction,
): void {
  if (err instanceof ZodError) {
    res.status(422).json({ error: 'Validation failed', issues: err.issues });
    return;
  }

  if (err instanceof HttpError) {
    res.status(err.status).json({ error: err.message, details: err.details });
    return;
  }

  logger.error({ err }, 'Unhandled error');
  res.status(500).json({
    error: 'Internal Server Error',
    ...(isProd ? {} : { message: err instanceof Error ? err.message : String(err) }),
  });
}
