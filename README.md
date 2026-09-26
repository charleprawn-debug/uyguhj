# Proxy Platform

Proxy Platform is a production-oriented MVP foundation for a proxy marketplace. It contains a TypeScript/Express API, Supabase schema, and an Android Compose client with real authentication and API networking. Payment fulfillment, provider allocation, and device-wide VPN transport remain explicitly gated until their providers and security requirements are configured.

## Implemented client flow

The Android client now includes registration, login, persisted session state, logout, real product loading from the API, featured filtering, product detail pages, profile, email verification status, and subscription reads. It never receives the Supabase service-role key. It uses `BuildConfig.API_BASE_URL` and sends the user access token to the backend.

## Railway deployment

Create a Railway service from this repository and set the service **Root Directory** to `/backend`. Railway will then use `backend/Dockerfile`. Add these variables to the Railway service:

```env
NODE_ENV=production
PORT=3000
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-anon-key
SUPABASE_SERVICE_ROLE_KEY=your-service-role-key
JWT_SECRET=long-random-secret
JWT_REFRESH_SECRET=different-long-random-secret
ENCRYPTION_KEY=long-random-secret
CORS_ORIGINS=https://your-allowed-web-origin.example
RATE_LIMIT_WINDOW_MS=900000
RATE_LIMIT_MAX_REQUESTS=100
LOG_LEVEL=info
```

Do not put `SUPABASE_SERVICE_ROLE_KEY`, JWT secrets, or `ENCRYPTION_KEY` into Android or GitHub source. The current verified Railway API URL is `https://proxy-production-58ff.up.railway.app`. Verify it with:

```bash
curl -fsS https://proxy-production-58ff.up.railway.app/livez
curl -fsS https://proxy-production-58ff.up.railway.app/readyz
curl -fsS https://proxy-production-58ff.up.railway.app/api/v1/products
```

## Android build against Railway

Set `API_BASE_URL` to the Railway API URL including `/api/v1`:

```bash
cd android
./gradlew assembleDebug -PAPI_BASE_URL=https://proxy-production-58ff.up.railway.app/api/v1
```

GitHub Actions now verifies the Railway API before building Android and uses `https://proxy-production-58ff.up.railway.app/api/v1` by default. You can override it with a repository variable named `API_BASE_URL`. The workflow uploads `app-debug.apk` as `proxy-platform-debug-apk`.

## Local backend

```bash
cd backend
cp .env.example .env
npm ci
npm run lint
npm run type-check
npm test
npm run build
npm run dev
```

The minimum required values are `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY`, `JWT_SECRET`, and `ENCRYPTION_KEY`. Generate secrets locally with:

```bash
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
```

## Database

Run `database/01_schema.sql` and then the corrected `database/02_seed_data.sql` in a disposable Supabase project before production. The schema uses Supabase Auth's `auth.users` table and requires Supabase rather than plain PostgreSQL.

## Current release boundary

The Android client includes a device-wide `VpnService` path backed by the official Sing-box `libbox.aar` runtime. It consumes a generated `Config.json` with DoH, strict routing, and auto routing. The transport audit is documented in [`docs/NETWORK_SECURITY_AUDIT.md`](docs/NETWORK_SECURITY_AUDIT.md). Multiplexing is enabled only for Sing-box outbounds that support it; Sing-box rejects Mux on SOCKS and HTTP. Android's system VPN indicator remains OS-controlled. The checked-in debug artifact is arm64-v8a and still requires physical-device validation before release.

## Proxy-matched Mock GPS

The Android app includes an optional, user-controlled Mock Location mode. Automatic mode looks up the proxy exit location through the configured proxy; manual mode accepts validated latitude and longitude. Before enabling it, Android requires the user to select **Proxy Platform** under Developer options → Select mock location app. This feature does not change hardware GPS or bypass mock-location detection, and some apps may reject mock locations.
