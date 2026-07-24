import express, { type Express, type Request, type Response } from 'express';
import cors from 'cors';
import helmet from 'helmet';
import { randomUUID } from 'node:crypto';
import { corsOrigins } from './lib/env';
import { errorHandler, notFound } from './middleware/error';
import { createRateLimit } from './middleware/rateLimit';
import { healthRouter, API_VERSION } from './modules/health/health.routes';
import { authRouter } from './modules/auth/auth.routes';
import { profileRouter } from './modules/profile/profile.routes';
import { planRouter } from './modules/food/plan.routes';
import { loggingRouter } from './modules/logging/logging.routes';
import { chatRouter } from './modules/chat/chat.routes';
import { reportsRouter } from './modules/reports/reports.routes';
import { accountRouter } from './modules/account/account.routes';
import { familyRouter } from './modules/family/family.routes';
import { exerciseRouter } from './modules/exercise/exercise.routes';
import { guidanceRouter } from './modules/guidance/guidance.routes';
import { cycleRouter } from './modules/cycle/cycle.routes';
import { wellnessRouter } from './modules/wellness/wellness.routes';
import { visionRouter } from './modules/vision/vision.routes';
import { recipeRouter } from './modules/recipe/recipe.routes';
import { savedFoodRouter } from './modules/food/savedFood.routes';
import { riskRouter } from './modules/risk/risk.routes';
import { ratingRouter } from './modules/rating/rating.routes';
import { coachRouter } from './modules/coach/coach.routes';
import { disciplineRouter } from './modules/discipline/discipline.routes';
import { remindersRouter } from './modules/reminders/reminders.routes';

/**
 * Builds the Express app. Kept free of `listen()` so tests can import it directly
 * (supertest) without binding a port.
 */
export function createApp(): Express {
  const app = express();

  // Behind Render's proxy — trust it so req.ip is the real client for rate limiting.
  app.set('trust proxy', 1);
  app.disable('x-powered-by');
  app.use(helmet());
  app.use(cors({ origin: corsOrigins, credentials: true }));

  // Correlation id on every request/response.
  app.use((req: Request, res: Response, next) => {
    const id = req.header('x-request-id') || randomUUID();
    res.setHeader('x-request-id', id);
    next();
  });

  // 6mb accommodates base64 photo uploads (body/meal vision); downscaled on-device.
  app.use(express.json({ limit: '6mb' }));
  app.use(express.urlencoded({ extended: true }));

  // Liveness/readiness at the root (Render + pingers hit /health) — not rate limited.
  app.use('/', healthRouter);

  // Global + stricter auth rate limits.
  const globalLimit = createRateLimit({ windowMs: 15 * 60_000, max: 600 });
  const authLimit = createRateLimit({ windowMs: 15 * 60_000, max: 30, keyPrefix: 'auth:' });

  const api = express.Router();
  api.use(globalLimit);
  api.get('/', (_req: Request, res: Response) => {
    res.json({ name: 'nutriai-api', version: API_VERSION });
  });
  api.use('/auth', authLimit, authRouter);
  api.use('/', profileRouter);
  api.use('/', planRouter);
  api.use('/', loggingRouter);
  api.use('/', chatRouter);
  api.use('/', reportsRouter);
  api.use('/', accountRouter);
  api.use('/', familyRouter);
  api.use('/', exerciseRouter);
  api.use('/', guidanceRouter);
  api.use('/', cycleRouter);
  api.use('/', wellnessRouter);
  api.use('/', visionRouter);
  api.use('/', recipeRouter);
  api.use('/', savedFoodRouter);
  api.use('/', riskRouter);
  api.use('/', ratingRouter);
  api.use('/', coachRouter);
  api.use('/', disciplineRouter);
  api.use('/', remindersRouter);
  app.use('/api/v1', api);

  app.use(notFound);
  app.use(errorHandler);

  return app;
}
