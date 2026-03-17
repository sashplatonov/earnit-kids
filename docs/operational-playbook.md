# Operational Playbook
<a name="top"></a>

## Who, when, and why
- **Audience:** engineers taking over EarnIt Kids features, coaches, or operator responders who need a consolidated view of how the project is wired and what manual checks matter.
- **Goal:** make it easy to understand the stack, common workflows, and the “what to run now” checklist without hunting through scattered AGENTS/README fragments.
- **Now:** this guide sits alongside `docs/project-docs.md` and the design/backend rules; keep it updated whenever verification commands, manual flows, or deployment notes change so the team can point coworkers here.

[↩ Back to toc](#table-of-contents) | [↑ Back to top](#top)

## Table of Contents {#table-of-contents}
1. [🧭 Project overview](#project-overview)
2. [⚙️ Setup & local run](#setup-local-run)
3. [🛠️ Working workflows](#working-workflows)
4. [🧪 Testing & manual verification](#testing-manual-verification)
5. [📝 Documentation & maintenance](#documentation-maintenance)

[↑ Back to top](#top)

## 🧭 Project overview {#project-overview}
- EarnIt Kids pairs a Node.js HTTP backend (no heavy frameworks) with a vanilla ES module frontend that renders server-driven HTML in `views/` plus supporting assets under `public/`; everything is coordinated by PostgreSQL migrations and the scripts in `scripts/`.
- Key business flows:
  - **Admin/Parent:** manage children, tasks, shop items, and approval decisions via the portal.
  - **Child:** magic-link access to earn coins, submit requests or shop purchases, and watch the analytics dashboard that isolates per-child history.
  - **Shop & Notifications:** parents curate a catalog (global + per-child) and can purchase directly, honor frequency/money limits, or approve/deny child requests; Telegram alerts handle database backup notifications/errors (`docs/telegram-setup.md`).
- Deployment surfaces include `docker-compose.yml` (Docker/Colima), the optional `mobile/` Capacitor wrapper, and the baked-in Telegram bot (Java/Maven project) for notifications.
- Use `docs/design-concept.md` → `docs/rules-frontend.md` before touching UI/CSS, per the docs index in `docs/project-docs.md`; Backend rules, architecture, database, and API specs live in their respective files for deeper dives.

[↩ Back to toc](#table-of-contents) | [↑ Back to top](#top)

## ⚙️ Setup & local run {#setup-local-run}
- **Prerequisites:** Node.js 20+ (or 22 LTS), PostgreSQL, and Docker/Colima (`DOCKER_HOST=unix:///Users/sash/.colima/default/docker.sock`) when you want containers.
- **Initial boot:**
  1. Copy `.env.example` to `.env` at the repo root, keeping `DATABASE_URL` pointed at your local Postgres.
  2. If you need schema isolation, add `DEFAULT_DB_SCHEMA` and let the helpers in `src/db/schema.js` set `search_path=<schema>,public`.
  3. `npm install`, then `npm run migrate` to apply SQL migrations.
  4. Run `npm start` for production-mode rendering or `npm run dev` when iterating on frontend/back behavior.
- **Docker:** after `npm install`, use `docker compose --profile db up -d --build` when `.env` points at the bundled compose Postgres service `db`; use plain `docker compose up -d --build` only when `.env` targets an external database. For local dev where you need both `3000` and `3001` on the host, run `docker compose -f docker-compose.yml -f docker-compose.local.yml --profile db up -d --build`. Follow with `docker compose logs -f`, then stop with `docker compose down`.
- **Mobile shell:** `mobile/` holds the Capacitor project that points at `https://earnit-kids.igo.mywire.org`. Use `cd mobile && npm install`, `npx cap add android|ios`, and `npm run sync` before `npm run open:android` or `npm run open:ios`.

[↩ Back to toc](#table-of-contents) | [↑ Back to top](#top)

## 🛠️ Working workflows {#working-workflows}
- **Tasks & earning:** parents create/adjust tasks and assign children; tasks track reward value, limit per child, and whether they appear in analytics dashboards. Task persistence lives in `src/services/`;
- **Shop & spends:** `src/controllers/shop` enforces per-child balance, frequency limits, and optional real-money `money_limit` validation before persisting `spend` entries in the history.
- **Coin requests:** children submit requests (type `requestType: shop_purchase` or custom coin amounts), admins approve/reject through the dashboard, and the outcome is logged as either approved `spend` or a denial event.
- **Analytics & history:** dashboards break down earnings/spending by timeframe; filtering and charting helpers live in `src/views/blocks/analytics`.
- **Manual shop/purchase verification:** every time shop or purchase logic changes, do the following:
  1. Add, edit, and delete a shop item to exercise catalog CRUD, validation, and persistence.
  2. Run an admin direct purchase from the catalog to confirm immediate spend and history logging.
  3. Submit a child request, approve it as Admin, then repeat but reject it; check notification content/state updates after each decision.
  4. Tweak frequency/money limits on an item and verify both the request UI and backend enforcement keep the limits intact.

[↩ Back to toc](#table-of-contents) | [↑ Back to top](#top)

## 🧪 Testing & manual verification {#testing-manual-verification}
- **Automated suite (must pass):**
  - `npm run lint`
  - `npm test`
  - `npm run build`
- **Manual Playwright smoke:** always mention `npm run test:ui:e2e` when summarizing verification status; run it after a UI-affecting change or before merge if time allows.
- **Optional quality helpers:** `npm run check` bundles lint + coverage, and `npm run playwright:install` sets up browsers for `test:ui:e2e`.
- **Failure notes:** log inability to run a required command (lint/test/build) as a follow-up; do not skip them. If a manual shop check fails, capture the reproduction steps + logs so others can pick up the investigation.

[↩ Back to toc](#table-of-contents) | [↑ Back to top](#top)

## 📝 Documentation & maintenance {#documentation-maintenance}
- Keep this guide in sync whenever verification, manual flows, or repo structure change; reference AGENTS `Testing & verification` for command requirements and the docs index table for reading order.
- When writing new docs:
  - Pick a single audience (operators, frontend, etc.), lead with context, and keep actions command-driven.
  - Add a short TOC near the top and provide `[↩ Back to toc]`/`[↑ Back to top]` links after each big section.
  - Include assumptions, failure modes, and rollback guidance where relevant; drop emojis that clarify tone (📝 for tips, ⚠️ for cautions, ✅ for done).
- Update `README.md` and `docs/project-docs.md` whenever the doc list or reading order changes so future contributors can find this reference quickly.

[↩ Back to toc](#table-of-contents) | [↑ Back to top](#top)
