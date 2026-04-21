# EarnIt Kids I18n Contract

<a id="top"></a>

## Table of Contents

- [Goal](#goal)
- [Supported Locales](#supported-locales)
- [Resolution Order](#resolution-order)
- [Canonical URL Policy](#canonical-url-policy)
- [Route Examples](#route-examples)
- [Fallback Rules](#fallback-rules)
- [Non-Translated Surfaces](#non-translated-surfaces)
- [Backend Contract](#backend-contract)
- [Failure Modes](#failure-modes)
- [Rollback Notes](#rollback-notes)

## Goal

This document freezes the production i18n contract for EarnIt Kids across the SvelteKit web app in `apps/web` and the user-facing backend responses in `apps/backend`.

- Scope includes all public pages, authenticated parent and child flows, shop flows, and the super-admin panel.
- English is the default locale and the only fallback locale.
- Locale must be known before render on the server.
- The bare root `/` never serves content directly.

[↩ Back to toc](#table-of-contents)

## Supported Locales

Supported locales are fixed to:

- `en`
- `ru`

No other locale code is valid in URLs, cookies, or server-side rendering state.

[↩ Back to toc](#table-of-contents)

## Resolution Order

Locale resolution order is fixed and must not be changed without a new ADR:

1. URL prefix
2. Locale cookie
3. `Accept-Language`
4. `en`

Operational notes:

- URL prefix always wins over every other signal.
- Cookie and `Accept-Language` are only used when the incoming path is not already prefixed.
- Error responses use `en` when no explicit locale can be resolved.

[↩ Back to toc](#table-of-contents)

## Canonical URL Policy

Canonical routing rules:

- English pages use `/en/*`.
- Russian pages use `/ru/*`.
- `/` issues a server-side redirect to `/en/` before page rendering.
- Unprefixed browser-facing routes redirect to their prefixed canonical equivalent.
- Legacy aliases redirect to the localized canonical route for the resolved locale.

Legacy aliases currently covered:

- `/index.html` -> `/{locale}/`
- `/login.html` -> `/{locale}/login`
- `/verify.html` -> `/{locale}/verify`
- `/reset-password.html` -> `/{locale}/reset-password`
- `/about.html` -> `/{locale}/about`
- `/faq.html` -> `/{locale}/faq`
- `/features` -> `/{locale}/features/tasks`
- `/super-admin.html` -> `/{locale}/super-admin`

Bypassed paths that are not locale-prefixed:

- `/api/*`
- `/healthz`
- `/login-child/*`
- static assets under `/css`, `/img`, `/fonts`, `/manifest.json`, `/robots.txt`, `/sitemap.xml`, `/sw.js`, `/favicon.ico`, `/apple-touch-icon`, `/.well-known/*`

[↩ Back to toc](#table-of-contents)

## Route Examples

Representative canonical examples:

| Area | English | Russian |
| --- | --- | --- |
| Root | `/en/` | `/ru/` |
| About | `/en/about` | `/ru/about` |
| FAQ | `/en/faq` | `/ru/faq` |
| Blog list | `/en/blog` | `/ru/blog` |
| Blog article | `/en/blog/how-to-start` | `/ru/blog/how-to-start` |
| Login | `/en/login` | `/ru/login` |
| Reset password | `/en/reset-password` | `/ru/reset-password` |
| Verify email | `/en/verify` | `/ru/verify` |
| Parent dashboard default | `/en/app/analytics` | `/ru/app/analytics` |
| Child dashboard default | `/en/app/tasks` | `/ru/app/tasks` |
| Shop | `/en/app/shop` | `/ru/app/shop` |
| Requests | `/en/app/requests` | `/ru/app/requests` |
| Super-admin | `/en/super-admin` | `/ru/super-admin` |

Route helper rules:

- Route builders must always return a prefixed path.
- Locale switching swaps only the leading locale segment and keeps the rest of the path, query string, and hash intact.
- Redirects from auth guards must preserve locale.

[↩ Back to toc](#table-of-contents)

## Fallback Rules

Runtime fallback rules are fixed:

- Missing locale selection resolves to English.
- Missing Russian keys resolve to English.
- Missing Russian keys must never throw at runtime.
- Russian is never used as fallback for any other locale.
- English dictionaries are the source of truth for key shape and completeness.

Development behavior:

- Missing keys should fail loudly in development when the English source key is missing.
- Missing Russian overlay keys are allowed and should log only in development when useful.

[↩ Back to toc](#table-of-contents)

## Non-Translated Surfaces

The following surfaces are explicitly out of scope for translation:

- database content created by users or admins
- ids, slugs, and tokens
- machine error codes
- logs and operational telemetry
- internal metric names
- raw repository/entity field names
- SQL, migrations, and backup artifact filenames

Translated surfaces include:

- page copy
- navigation labels
- form labels, placeholders, and validation messages
- `title`, `alt`, and `aria-*` attributes
- toasts and modal copy
- backend user-facing error/detail messages that feed the web app

[↩ Back to toc](#table-of-contents)

## Backend Contract

Backend-specific rules:

- The web edge forwards locale using `X-App-Locale` and `Accept-Language`.
- Backend locale resolution uses the same `en` / `ru` contract.
- `detail` values in error payloads are user-facing and may be localized.
- `errorCode` values remain stable machine-readable identifiers and are never translated.
- Generic problem `title` values follow the resolved locale when available.

[↩ Back to toc](#table-of-contents)

## Failure Modes

Known failure modes and expected behavior:

- Unknown locale prefix: treat as an unprefixed route and resolve using cookie, header, then `en`.
- Missing locale cookie: continue with header, then `en`.
- Missing or malformed `Accept-Language`: continue with `en`.
- Missing Russian translation key: render the English source string.
- Missing English source key: fail in development, never crash production rendering.
- Request to `/`: always return a server redirect to `/en/`.

[↩ Back to toc](#table-of-contents)

## Rollback Notes

If a release introduces a locale-routing regression:

1. Keep prefixed routes live.
2. Disable only the affected translated copy or route guards, not the prefix contract.
3. Preserve `/` -> `/en/` redirect even during rollback.
4. Preserve backend fallback to English so the app never crashes on missing strings.

[↑ Back to top](#top)
