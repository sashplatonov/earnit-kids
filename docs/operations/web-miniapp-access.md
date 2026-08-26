# Web and Telegram access runbook

<a name="top"></a>

Use this runbook when a parent or child cannot enter the web workspace or the
Telegram Mini App. Start with the smallest safe check and avoid changing
accounts, memberships, or provider settings until the failed boundary is clear.

## Table of contents

- [🧭 Know the entrypoints](#-know-the-entrypoints)
- [🔍 Diagnose access](#-diagnose-access)
- [🔐 Parent and child rules](#-parent-and-child-rules)
- [🤖 Diagnose Telegram](#-diagnose-telegram)
- [🧪 Prove the fix](#-prove-the-fix)

## 🧭 Know the entrypoints

| Entry | What it does |
| --- | --- |
| Public site | Starts a parent sign-in or opens product information |
| Child link | Creates a child-scoped browser session |
| Telegram Mini App | Verifies Telegram `initData`, then creates a scoped session |
| Telegram bot | Performs quick actions against the same family services |

The web edge owns browser cookies and same-origin proxying. The backend owns
the session, membership permission, family selection, and child scope.

[↑ Back to top](#top)

## 🔍 Diagnose access

1. Check the public origin and API health:

   ```bash
   curl -fsS https://YOUR_ORIGIN/healthz
   curl -fsS https://YOUR_BACKEND/q/health
   ```

2. Confirm the deployed `APP_URL`, backend origin, Google redirect URL, and
   Telegram Mini App URL match the public HTTPS origin.
3. Inspect structured deployment logs using the request trace ID. Do not copy
   cookies, authorization headers, pairing tokens, or Telegram `initData` into
   tickets or chat.
4. Reproduce in a fresh private browser window. Old cookies can represent a
   previous membership or session state.

⚠️ A health response proves that a process is reachable. It does not prove a
valid session, an OAuth callback, or Telegram identity verification.

[↑ Back to top](#top)

## 🔐 Parent and child rules

Parent access is membership-based. A parent can choose a family when their
account belongs to more than one. `viewer` is read-only, `editor` can change
family data, and `family_admin` can also manage access and language settings.

Child links and child sessions are server-scoped to one child. When a child
cannot see expected data, verify the selected family and child ownership on the
server before changing UI state. Do not solve an authorization failure by
passing a different child ID from the browser.

[↑ Back to top](#top)

## 🤖 Diagnose Telegram

1. Open the Mini App from Telegram, not a copied browser URL.
2. Confirm the bot token, public HTTPS Mini App URL, webhook secret, and
   feature flags are configured in the deployment secret manager.
3. Look for the backend verification result and trace ID in structured logs.
4. Confirm the Telegram account is linked to the intended family and that the
   selected family has a locale.

Telegram `initData` is signed and short-lived. Treat a failed signature or age
check as an authentication failure, not as a frontend retry problem.

[↑ Back to top](#top)

## 🧪 Prove the fix

Use a real, non-production test account and record the exact result:

- parent sign-in or family selection succeeds;
- child link opens only the intended child;
- Telegram Mini App opens from the bot and reaches the intended role;
- a parent and child see the same approved request or balance change;
- logs show no unexpected authentication or authorization errors.

✅ Keep evidence separate: local tests prove source behavior; deployment checks
prove deployed configuration; a Telegram client check proves Telegram delivery.

[↑ Back to top](#top)
