# EarnIt Kids web app

<a name="top"></a>

`apps/web` is the SvelteKit edge for public pages, the Telegram Mini App, and
the anonymous live demo. It serves the browser, keeps API calls same-origin,
and proxies them to the backend.

## Table of contents

- [🚀 Work locally](#-work-locally)
- [⚙️ Configuration](#️-configuration)
- [🧪 Tests](#-tests)
- [🧭 Important routes](#-important-routes)

## 🚀 Work locally

```bash
cd apps/web
npm ci
npm run dev
```

The dev server listens on `http://localhost:4173`. Build and run the production
preview on `http://localhost:4174` with:

```bash
npm run build
npm run preview
```

[↑ Back to top](#top)

## ⚙️ Configuration

Use the root `.env.example` as the configuration shape. The web runtime reads:

| Variable | Purpose | Default |
| --- | --- | --- |
| `BACKEND_ORIGIN` | Quarkus base URL | `http://localhost:8080` |
| `APP_URL` | Public site origin used in generated links | `http://localhost:4174` |
| `TELEGRAM_MINI_APP_URL` | Optional public Mini App deep link | unset |
| `SESSION_PATH` | Backend session snapshot path | `/api/page-data/session` |
| `WS_PATH` | Backend WebSocket path | `/ws` |
| `DEV_PORT` | Vite development port | `4173` |
| `PREVIEW_PORT` | Preview and Playwright port | `4174` |

Docker Compose provides the service-to-service backend URL. Do not put secrets
in frontend variables or commit local configuration.

[↑ Back to top](#top)

## 🧪 Tests

```bash
npm run lint
npm run test
npm run build
npm run test:e2e
```

Use the focused demo check when changing the public demo:

```bash
npm run test:e2e -- --project=chromium tests/e2e/live-coin-shop-demo.spec.ts
```

⚠️ Playwright needs a working preview server and its mock backend. A connection
refusal is an environment problem, not evidence about the UI.

[↑ Back to top](#top)

## 🧭 Important routes

- `/` and `/ru/`: public site
- `/demo` and `/ru/demo`: anonymous in-memory workspace demo
- `/telegram`: Telegram Mini App entrypoint
- `/api/*`: same-origin backend proxy
- `/healthz`: container health endpoint

Read [the web architecture guide](docs/ARCHITECTURE.md) before changing routes,
stores, or the proxy boundary.

[↑ Back to top](#top)
