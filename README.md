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
ADMIN_EMAILS=owner@example.com
```

Do not put `SUPABASE_SERVICE_ROLE_KEY`, JWT secrets, or `ENCRYPTION_KEY` into Android or GitHub source. The current verified Railway API URL is `https://uyguhj-production.up.railway.app`. Verify it with:

```bash
curl -fsS https://uyguhj-production.up.railway.app/livez
curl -fsS https://uyguhj-production.up.railway.app/readyz
curl -fsS https://uyguhj-production.up.railway.app/api/v1/products
```

## Android build against Railway

Set `API_BASE_URL` to the Railway API URL including `/api/v1`:

```bash
cd android
./gradlew assembleDebug -PAPI_BASE_URL=https://uyguhj-production.up.railway.app/api/v1
```

GitHub Actions now verifies the Railway API before building Android and uses `https://uyguhj-production.up.railway.app/api/v1` by default. You can override it with a repository variable named `API_BASE_URL`. The workflow uploads `app-debug.apk` as `proxy-platform-debug-apk`.

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

### Admin dashboard

Open `/admin` on the deployed server to access the protected dashboard. Set `ADMIN_EMAILS` to a comma-separated allowlist before registering the owner account; only those accounts receive the `admin` role. The dashboard provides overview metrics, users, and plan creation.

The minimum required values are `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY`, `JWT_SECRET`, and `ENCRYPTION_KEY`. Generate secrets locally with:

```bash
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
```

## Database

Run `database/01_schema.sql`, the corrected `database/02_seed_data.sql`, then `database/03_wallet_plans.sql`, `database/04_user_feature_grants.sql`, and `database/05_subscription_plans.sql` in the Supabase SQL editor. The migrations add wallet balances, transaction history, independent subscription packages, atomic wallet purchases, and individual admin feature grants. Subscription packages are separate from marketplace proxy products. Wallet top-up controls remain intentionally unavailable until payment methods are added. The schema uses Supabase Auth's `auth.users` table and requires Supabase rather than plain PostgreSQL.

## Current release boundary

The Android client includes a device-wide `VpnService` path backed by the official Sing-box `libbox.aar` runtime. It consumes a generated `Config.json` with DoH, strict routing, and auto routing. The transport audit is documented in [`docs/NETWORK_SECURITY_AUDIT.md`](docs/NETWORK_SECURITY_AUDIT.md). Multiplexing is enabled only for Sing-box outbounds that support it; Sing-box rejects Mux on SOCKS and HTTP. Android's system VPN indicator remains OS-controlled. The checked-in debug artifact is arm64-v8a and still requires physical-device validation before release.

## Saved local proxy profiles and connection safety

Android proxy profiles are stored in a private on-device SQLite database. Each username and password is encrypted using AES-256-GCM with a non-exportable Android Keystore key. Profiles are not sent to the API and can be selected in either VPN or advanced mode. The advanced-mode stop flow restores prior Android proxy settings, explicitly switches Android to its direct (`:0`) state when no prior proxy existed, verifies the result, and then closes the local tunnel. Before either connection mode starts, the app checks that the upstream proxy TCP endpoint is reachable so an unavailable proxy does not silently capture device traffic.

## Proxy-matched Mock GPS

The Android app includes an optional, user-controlled Mock Location mode. Automatic mode fetches the proxy exit location through a loopback inbound on the active sing-box instance, so upstream SOCKS5 authentication is reused; manual mode accepts latitude/longitude values only within the valid geographic ranges. The mock point is continuously republished every two seconds to GPS, network (where supported), the Android fused provider, and Google Play Services' `FusedLocationProviderClient`; enabling FLP mock mode clears its cached real fixes so Maps clients using FLP can receive the proxy point. Automatic proxy GeoIP coordinates are refreshed every 15 minutes, and the UI shows the coordinates successfully applied to FLP. On stop, the app turns FLP mock mode off to clear the mock cache, and a persisted recovery marker lets the next app launch retry cleanup after an abrupt process exit. Before a connection starts with Mock Location enabled, the app checks Android's mock-location AppOp and blocks startup with a clear next step if the user has not selected **Proxy Platform** under Developer options → Select mock location app. After returning from Developer options, the app verifies the selection; provider, FLP, and GeoIP failures are reported to the UI instead of silently marking Mock Location active. To appear in Android's picker, the app declares `ACCESS_MOCK_LOCATION`, a signature-only marker that ordinary apps cannot receive; actual use remains gated by the user's selection/AppOp. Because Google's FLP mock API requires an Android location permission, the app asks for **approximate location only** when this feature is used; its code does not read the device's real location, and it does not request precise location. This feature does not change hardware GPS or bypass mock-location detection, and some apps may reject mock locations. Android does not expose a public intent for opening the internal app-picker preference directly, so the app opens Developer options and explains the exact item to select.

## Optional proxy-matched language and timezone (advanced mode)

Advanced mode has an opt-in **“تعديل المنطقة الزمنية بما يناسب البروكسي”** setting. When enabled, the app queries the selected proxy's exit via the existing `ipwho.is` request through the local sing-box inbound, which handles the selected upstream protocol and authentication, then applies the returned IANA timezone through Android's alarm-manager shell command. The phone language is not changed. Before changing settings, it stores a private recovery snapshot; on disconnect or recovery it restores the original timezone and automatic-timezone setting, while preserving values that the user changed independently during the session. The setting is off by default and does not affect VPN mode.

Advanced mode also offers **“تعديل لغة الهاتف بما يناسب البروكسي”**. When enabled, the app derives a locale from the proxy exit country and applies the Android system locale through the embedded Wireless ADB shell. It saves the complete original locale list, verifies the applied locale, and restores the original list when the advanced session stops or is recovered after an interrupted session. This option is off by default, requires Wireless debugging, and may cause Android applications to recreate their interfaces.

Wireless ADB pairing supports both six-digit code entry in the app and a notification action with inline reply. On Android 13 and later, the notification permission is requested only when the user asks to display the pairing notification; if denied or notifications are disabled, manual code entry remains available. Android exposes a public action for Developer options but not a portable public intent for the individual Wireless debugging toggle, so the app opens Developer options and the user selects Wireless debugging there. No floating overlay or draw-over-other-apps permission is used.

## WebRTC and UDP in normal VPN mode

The normal Android `VpnService` mode routes both IPv4 and IPv6 into the sing-box TUN and uses the selected proxy as its final outbound. With SOCKS5, sing-box can send UDP through the upstream SOCKS5 UDP relay; before starting this mode, the app verifies that the server accepts an authenticated `UDP ASSOCIATE` request. If it does not, the app refuses to start rather than claiming WebRTC UDP is proxied. This handshake confirms relay availability, but a provider can still filter particular UDP destinations. HTTP CONNECT proxies do not carry arbitrary UDP, so WebRTC UDP requires a SOCKS5 profile that supports UDP. Advanced mode remains an Android system HTTP proxy and does not capture WebRTC/UDP traffic.
