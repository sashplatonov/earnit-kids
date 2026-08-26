# EarnIt Kids web architecture

<a name="top"></a>

`apps/web` is one SvelteKit application. It serves the public site, opens the
Telegram workspace, hosts the anonymous demo, and keeps browser API traffic on
the same origin.

## Table of contents

- [🧭 Routes](#-routes)
- [🧠 State and actions](#-state-and-actions)
- [🔌 Backend boundary](#-backend-boundary)
- [🌍 Localization](#-localization)
- [🎨 UI rules](#-ui-rules)
- [🛡️ Browser security](#️-browser-security)
- [🧪 Verification](#-verification)

## 🧭 Routes

| Route | Purpose |
| --- | --- |
| `/` and `/ru/` | Public website |
| `/demo` and `/ru/demo` | Public in-memory family workspace |
| `/telegram` | Telegram Mini App entrypoint |
| `/api/*` | Same-origin proxy to Quarkus |
| `/healthz` | Container health response |

Keep page composition in `src/routes`. Shared public components belong in
`src/lib/components`; Mini App components belong in
`src/lib/components/telegram`. Route-specific loading, redirects, and headers
belong in the route’s server files.

[↑ Back to top](#top)

## 🧠 State and actions

`src/lib/stores/app.ts` holds the current family, selected child, and loaded
catalog data. `modal.ts` and `toasts.ts` hold shared transient UI state.

Interactive family actions go through the focused services in
`src/lib/telegram/services`. They choose a production API action or the
in-memory demo action and return the same visible result. Components show busy,
error, confirmation, and focus-restoration states; they do not carry their own
copy of family business rules.

The demo owns its fixture for the current browser tab. It never sends `/api/`
requests, writes browser storage, or changes production data. Reload or Reset
starts it from a fresh fixture.

[↑ Back to top](#top)

## 🔌 Backend boundary

`src/lib/services/api.ts` is the shared browser fetch layer. It includes
credentials and CSRF handling. `serverContract.ts` normalizes backend payloads
once before they reach components or stores.

Keep new browser calls behind this boundary. A response change must update the
backend DTO, normalizer, and the test that proves the visible result. Do not
work around a contract mismatch with field aliases scattered across components.

[↑ Back to top](#top)

## 🌍 Localization

Public pages use the URL locale. The family locale controls authenticated
workspace and Telegram presentation. The typed catalogs under
`src/lib/i18n/messages/` own web copy; backend resource bundles own server and
bot copy.

When adding copy, update English and Russian together, retain every named
placeholder, and use `Intl` for dates and plural forms. User-created task and
reward text is data, never a translation key.

[↑ Back to top](#top)

## 🎨 UI rules

Use the existing compact Telegram-style shells and semantic controls. Icon-only
controls need a text label and a 44px touch target. Prefer component-scoped
styles for a local layout and `src/app.css` for shared tokens and layout rules.
Keep keyboard focus visible, use native buttons and labels where possible, and
announce asynchronous results with the existing status patterns.

[↑ Back to top](#top)

## 🛡️ Browser security

The edge sets the browser security-header contract and forwards trace IDs to
the backend. Diagnostics contain bounded event codes and safe metadata; never
log cookies, request bodies, raw query strings, or unfiltered error objects.

⚠️ HSTS belongs only on HTTPS deployments. A local browser check cannot prove
deployment headers, provider configuration, or Telegram-client behavior.

[↑ Back to top](#top)

## 🧪 Verification

```bash
cd apps/web
npm run lint
npm run test
npm run build
```

For UI changes, run the relevant Playwright spec. For the public demo:

```bash
npm run test:e2e -- --project=chromium tests/e2e/live-coin-shop-demo.spec.ts
```

[↑ Back to top](#top)
