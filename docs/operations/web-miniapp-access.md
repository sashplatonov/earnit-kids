# Web and Telegram Mini App operations

This runbook is the operational source of truth for the browser workspace and
the Telegram Mini App. It describes the configuration shape implemented by the
current backend and web runtime. It does not contain credentials, invitation
tokens, or production links.

## Access boundaries

- `/workspace` is the authenticated browser workspace. The web server checks
  the session before rendering it and redirects an anonymous request to the
  local, localized `/login` path with a safe continuation back to `/workspace`.
- `/telegram` is the Telegram-hosted workspace. The Telegram adapter exchanges
  verified Telegram `initData`; a normal browser visit must not create a
  session. The Telegram Mini App URL defaults to ${APP_URL}/telegram and may
  be overridden by `TELEGRAM_MINI_APP_URL`.
- Parent email invitations are created by an authorized family admin through
  `/api/parents/invitations`, delivered by the configured mail adapter, and
  accepted through `/invite/parent/{token}` plus the server-bound Google OAuth
  continuation. The raw token must not be copied into support tickets or logs.
- Child access uses the one-time `/login-child/{token}` flow and redirects to
  token-free `/workspace` after successful server-side consumption.
- Browser push is owned by the workspace. The service worker is registered for
  `/workspace/`; authenticated subscriptions use `/api/push/register` and
  `/api/push/unregister`.
- `/app` and `/ru/app` are not compatibility routes. Do not add links,
  redirects, proxy rules, or deployment health checks for them.

Family and permission selection is server-authorized. Browser state, a request
body, a supplied `familyId`, or another person's email is presentation/input
data only and cannot broaden the active family context.

The web edge and backend apply the shared browser security baseline: CSP,
Permissions Policy, frame protection, MIME sniffing protection, restrictive
referrer handling, and HTTPS-gated HSTS. Verify these headers at the deployed
origin because local source and build checks do not prove the public edge.

## Configuration

Set the values in the deployment environment; use `.env.example` as the
non-secret inventory. Keep `APP_URL` equal to the externally reachable HTTPS
origin, without a trailing slash. In Compose, backend-to-database and
backend-to-web traffic uses service DNS; do not replace `db` or `backend` with
host loopback addresses.

### Shared security and public-origin settings

| Variable | Purpose and production requirement |
| --- | --- |
| `APP_URL` | Canonical public origin for OAuth, invitation links, and Telegram defaults. Must be HTTPS in production. |
| `CORS_ORIGINS` | Comma-separated exact browser origins allowed by the backend. Include the deployed web origin and no untrusted origins. Verify credentials-enabled preflight after deployment. |
| `JWT_SECRET` | Session and signed continuation material. Use a unique secret, store it in the secret manager, and rotate according to the incident/secret-rotation policy. |
| `PARENT_INVITATION_PEPPER` | Secret digest pepper for parent invitation tokens. Keep separate from `JWT_SECRET`; rotation invalidates existing invitation digests unless a migration strategy is provided. |
| `PRODUCTION` | Enables production cookie behavior. Set `true` for the production runtime. |
| `SESSION_TTL_SECONDS`, `REFRESH_TOKEN_TTL_SECONDS` | Session and refresh-token lifetimes. Confirm the values match the organization's account-security policy. |

### Google OAuth and parent email invitations

| Variable | Purpose and failure behavior |
| --- | --- |
| `ENABLE_GOOGLE_AUTH` | Enables Google sign-in. If false or credentials are absent, the login UI reports Google as unavailable and the API refuses to build an authorization URL. |
| `GOOGLE_AUTH_CLIENT_ID`, `GOOGLE_AUTH_CLIENT_SECRET` | Google OAuth client credentials. Store as secrets; never put them in browser configuration. |
| `GOOGLE_AUTH_REDIRECT_URI` | Optional exact callback override; otherwise the backend uses ${APP_URL}/api/login-google/callback. Register the exact HTTPS callback in Google Cloud Console. |
| `PARENT_INVITATION_MAIL_PROVIDER` | Selects the mail adapter. `disabled` is safe for local development but invitations cannot be delivered. A production provider must be explicitly implemented/configured and monitored for send failure. |

The sender/domain, SPF, DKIM, DMARC, bounce handling, rate limits, and provider
credentials belong to the selected mail provider. A successful API response is
not proof that a message reached the recipient.

### Telegram Mini App and bot

| Variable | Purpose |
| --- | --- |
| `ENABLE_TELEGRAM_MINI_APP`, `ENABLE_TELEGRAM_BOT`, `ENABLE_TELEGRAM_NOTIFICATIONS` | Independently gate Mini App, bot, and notification behavior. Keep disabled until the corresponding public configuration is ready. |
| `TELEGRAM_ROLLOUT_FAMILY_ID` | Optional family rollout gate for staged enablement. |
| `TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME` | Bot API credential and username. Keep the token secret. |
| `TELEGRAM_MINI_APP_URL` | Public HTTPS Mini App URL; defaults to ${APP_URL}/telegram. Configure this exact URL in BotFather. |
| `TELEGRAM_INIT_DATA_MAX_AGE_SECONDS` | Maximum age accepted for signed Mini App init data. Keep it short enough for the threat model and account for clock skew. |
| `TELEGRAM_ADMIN_USER_IDS` | Comma-separated Telegram IDs allowed to use dashboard administration. Treat this as an access-control list. |
| `TELEGRAM_WEBHOOK_SECRET` | Secret used to authenticate webhook traffic when webhook registration is enabled. |
| `TELEGRAM_WEBHOOK_REGISTRATION_INTERVAL` | Reconciliation interval for the public webhook; set only when the deployment owns Bot API registration. |
| `TELEGRAM_CALLBACK_SIGNING_SECRET`, `TELEGRAM_CALLBACK_TTL_SECONDS`, `TELEGRAM_CALLBACK_MENU_VERSION`, `TELEGRAM_REPLY_KEYBOARD_VERSION` | Callback integrity, expiry, menu compatibility, and stale-keyboard cleanup controls. |
| `ENABLE_TELEGRAM_OUTBOX`, `TELEGRAM_OUTBOX_POLL_INTERVAL`, `TELEGRAM_OUTBOX_MAX_ATTEMPTS` | Durable bot-delivery worker controls. Monitor retries and dead/failed delivery records. |
| `ENABLE_TELEGRAM_RETENTION`, `TELEGRAM_RETENTION_POLL_INTERVAL`, `TELEGRAM_INVITE_RETENTION_DAYS`, `TELEGRAM_CALLBACK_RETENTION_DAYS`, `TELEGRAM_WEBHOOK_UPDATE_RETENTION_DAYS`, `TELEGRAM_DELIVERY_RETENTION_DAYS`, `TELEGRAM_OUTBOX_RETENTION_DAYS`, `TELEGRAM_AUDIT_RETENTION_DAYS`, `TELEGRAM_RETENTION_BATCH_SIZE` | Retention worker switch, schedule, per-record retention windows, and batch size. Set windows to meet the privacy and audit policy. |

The official Telegram client must launch the configured BotFather URL and send
fresh signed init data. A browser or a mocked SDK does not prove this.

### Browser push and PWA

| Variable | Purpose and failure behavior |
| --- | --- |
| `ENABLE_WEB_PUSH` | Enables backend browser-push processing. Keep false until VAPID material and HTTPS are ready. |
| `WEB_PUSH_VAPID_PUBLIC_KEY` | VAPID public identity used by the push integration. It is not a secret. |
| `WEB_PUSH_VAPID_PRIVATE_KEY` | VAPID private key. Store only in the secret manager; never expose it to the web bundle or logs. |
| `WEB_PUSH_VAPID_SUBJECT` | VAPID contact URI, normally a monitored `mailto:` address or approved HTTPS contact URL. |
| `WEB_PUSH_MAX_ATTEMPTS` | Maximum delivery attempts before the outbox item is treated as failed. |

The web manifest starts at `/workspace` with scope `/workspace/`; the service
worker provides an offline protected-data response rather than caching API
responses. Push requires a secure deployed origin, a supported browser, user
permission, an authenticated session, and a reachable push provider.

## Release and deployment checklist

1. Open every public page at `/public/index.html`, `/public/how.html`,
   `/public/tasks.html`, `/public/rewards.html`, `/public/parents.html`, and
   `/public/faq.html`. Confirm that both the configured Telegram Mini App URL
   and the browser workspace fallback are visible and usable at a 320px-wide
   viewport, with no horizontal overflow. Confirm the browser action starts the
   same-origin Google URL endpoint and that a failed startup leaves the local
   login link available.
2. Confirm the deployed web origin, backend origin, HTTPS certificate, DNS, and
   exact `CORS_ORIGINS` values. Send an authenticated and an unauthenticated
   `OPTIONS` preflight to a representative `/api/*` endpoint and inspect the
   returned origin and credentials headers.
3. Confirm Google Cloud OAuth consent-screen publishing status and register
   exactly ${APP_URL}/api/login-google/callback. Test login and cancellation
   with a non-production account; verify the callback does not accept a foreign
   redirect or email for an invitation.
4. Sign in to the browser workspace with a test account, confirm the browser
   sign-out control is present, and verify a successful sign-out clears the
   session before returning to the static public site. Also verify that a
   failed logout remains on the workspace and presents a generic error.
5. Confirm the mail provider sender/domain, SPF/DKIM/DMARC, bounce/complaint
   path, provider credentials, and rate limits. Send a controlled test invite,
   verify provider acceptance and mailbox delivery, then revoke the test
   invitation. Never paste the raw URL into logs or monitoring.
6. Generate or load the VAPID key pair through the approved secret-management
   process. Set the public key, private key, subject, and retry limit; deploy
   with `ENABLE_WEB_PUSH=true`; enable push in an HTTPS browser and verify the
   subscription and a delivered notification, including unsubscribe behavior.
7. In BotFather, configure the Mini App URL to the deployed
   `TELEGRAM_MINI_APP_URL` (or its ${APP_URL}/telegram default). Verify the
   bot webhook ownership, secret, rollout gate, and callback settings. Launch
   from the official Telegram client with a test identity and check both parent
   and child authorization boundaries.
8. Confirm database migrations, outbox/retention worker health, structured
   backend errors, bounded web diagnostics, security headers, and alerting for
   failed mail, Telegram, and push delivery.
9. Run the local quality gates below, then run the deployment smoke checks
   above. Record provider responses, client/device, timestamp, and deployment
   revision separately from local test output.

## Verification boundaries

The local gates prove source-level compilation, tests, lint, and a production
web build only:

```bash
git diff --check
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
cd ../web && npm run lint && npm run test && npm run build
```

They do not prove remote CI, deployed secrets, Google consent configuration,
DNS/HTTPS, CORS at the public edge, email delivery, push delivery on a real
browser/device, or an official Telegram client launch. Those require the
deployment checklist and evidence from the relevant provider or client.
