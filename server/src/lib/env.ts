import { config as loadDotenv } from 'dotenv';
import { z } from 'zod';

loadDotenv();

/**
 * Validated environment. Missing/invalid values fail fast at startup with a clear message,
 * except that optional free-tier integrations degrade gracefully (empty string allowed).
 */
const EnvSchema = z.object({
  NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
  PORT: z.coerce.number().int().positive().default(8080),
  LOG_LEVEL: z
    .enum(['fatal', 'error', 'warn', 'info', 'debug', 'trace', 'silent'])
    .default('info'),

  DATABASE_URL: z.string().min(1, 'DATABASE_URL is required'),
  DIRECT_URL: z.string().min(1).optional(),

  JWT_ACCESS_SECRET: z.string().min(16, 'JWT_ACCESS_SECRET must be at least 16 chars'),
  JWT_REFRESH_SECRET: z.string().min(16, 'JWT_REFRESH_SECRET must be at least 16 chars'),
  HEALTH_DATA_ENCRYPTION_KEY: z.string().min(1, 'HEALTH_DATA_ENCRYPTION_KEY is required'),

  AI_PROVIDER: z.enum(['rules', 'ollama', 'gemini', 'groq']).default('rules'),
  OLLAMA_URL: z.string().optional(),
  GEMINI_API_KEY: z.string().optional(),
  /** Override the Gemini model (e.g. 'gemini-2.0-flash-lite' for a separate/higher free quota). */
  GEMINI_MODEL: z.string().optional(),
  GROQ_API_KEY: z.string().optional(),

  USDA_FDC_API_KEY: z.string().optional().default(''),
  REDIS_URL: z.string().optional().default(''),
  GOOGLE_CLIENT_ID: z.string().optional().default(''),
  FCM_SERVER_KEY: z.string().optional().default(''),
  STORAGE_DRIVER: z.enum(['local', 's3']).default('local'),
  CORS_ORIGINS: z.string().default('*'),
});

export type Env = z.infer<typeof EnvSchema>;

function loadEnv(): Env {
  const parsed = EnvSchema.safeParse(process.env);
  if (!parsed.success) {
    const issues = parsed.error.issues
      .map((i) => `  - ${i.path.join('.')}: ${i.message}`)
      .join('\n');
    // Do not print values - only which keys are wrong.
    throw new Error(`Invalid environment configuration:\n${issues}`);
  }
  return parsed.data;
}

export const env = loadEnv();

export const isProd = env.NODE_ENV === 'production';
export const corsOrigins =
  env.CORS_ORIGINS === '*'
    ? '*'
    : env.CORS_ORIGINS.split(',').map((o) => o.trim()).filter(Boolean);
