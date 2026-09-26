import dotenv from 'dotenv';
dotenv.config();

const required = ['SUPABASE_URL', 'SUPABASE_ANON_KEY', 'SUPABASE_SERVICE_ROLE_KEY', 'JWT_SECRET', 'ENCRYPTION_KEY'];
const missing = required.filter((key) => !process.env[key]);
if (missing.length && process.env.NODE_ENV !== 'test') {
  throw new Error(`Missing required environment variables: ${missing.join(', ')}`);
}

const number = (key: string, fallback: number) => {
  const value = Number(process.env[key] ?? fallback);
  if (!Number.isFinite(value)) throw new Error(`${key} must be a number`);
  return value;
};

export const config = {
  env: process.env.NODE_ENV ?? 'development',
  port: number('PORT', 3000),
  apiVersion: process.env.API_VERSION ?? 'v1',
  supabase: { url: process.env.SUPABASE_URL ?? '', anonKey: process.env.SUPABASE_ANON_KEY ?? '', serviceRoleKey: process.env.SUPABASE_SERVICE_ROLE_KEY ?? '' },
  auth: { jwtSecret: process.env.JWT_SECRET ?? '', jwtRefreshSecret: process.env.JWT_REFRESH_SECRET ?? process.env.JWT_SECRET ?? '' },
  encryption: { key: process.env.ENCRYPTION_KEY ?? '', algorithm: process.env.ENCRYPTION_ALGORITHM ?? 'aes-256-cbc' },
  logging: { level: process.env.LOG_LEVEL ?? 'info' },
  corsOrigins: (process.env.CORS_ORIGINS ?? 'http://localhost:3001,http://localhost:3002').split(',').map((v) => v.trim()).filter(Boolean),
  rateLimit: { windowMs: number('RATE_LIMIT_WINDOW_MS', 900000), max: number('RATE_LIMIT_MAX_REQUESTS', 100) },
};
export default config;
