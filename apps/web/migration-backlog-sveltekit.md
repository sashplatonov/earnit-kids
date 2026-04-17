<a id="top"></a>

# Migration Backlog: Legacy Web Edge -> SvelteKit

> **Project:** EarnIt Kids web frontend  
> **Current stack:** Node.js web edge + HTML templates + vanilla JS modules + vanilla CSS + Chart.js CDN + Service Worker + Playwright  
> **Target stack:** SvelteKit 2 + Svelte 5 + adapter-node + existing Quarkus backend APIs + preserved legacy UI language  
> **Created:** 2026-04-16

<a id="table-of-contents"></a>

## Table of Contents
- [1. Executive Summary](#1-executive-summary)
- [2. Migration Principles](#2-migration-principles)
- [3. Library and Platform Mapping](#3-library-and-platform-mapping)
- [4. Architecture Mapping](#4-architecture-mapping)
- [5. File-by-File Backlog](#5-file-by-file-backlog)
- [6. Dependency Waves](#6-dependency-waves)
- [7. Critical Path](#7-critical-path)
- [8. High-Risk Items](#8-high-risk-items)
- [9. Parity Checklist](#9-parity-checklist)
- [10. SvelteKit Target File Structure](#10-sveltekit-target-file-structure)
- [11. Summary Table](#11-summary-table)

<a id="1-executive-summary"></a>

## 1. 🧭 Executive Summary

### Migration Scale
- **~51 browser ES module files** in `web/public/js/modules/`
- **3 top-level browser scripts** in `web/public/js/`
- **12 standalone HTML views** in `web/views/`
- **18 authenticated-shell partials** in `web/views/components/`
- **35 automated tests** in `web/tests/` (`18` unit, `2` UI smoke, `15` Playwright E2E)
- **2 distinct frontend surfaces**: family app shell and public marketing/auth pages
- **1 separate super-admin workspace** with its own route, styles, tabs, dialogs, and API surface

### Main System Blocks
1. **Edge routing and auth gate** — `src/server.js`, `src/rendering.js`, `src/sessionClient.js`
2. **Public pages** — landing, features, about, faq, blog, article, login, reset-password, verify, 404
3. **Authenticated family shell** — one assembled HTML shell with role-based sections and internal tab state
4. **Client runtime** — global mutable state, imperative renderers, modal helpers, CSRF fetch helpers, save scheduling
5. **Platform features** — WebSocket refresh, push notifications, service worker, pull-to-refresh, PWA install UI, offline states
6. **Super-admin console** — family management, catalog CRUD, DB operations, system dashboard, logs

### Key Migration Challenges
- **Conditional root route**: `/` renders landing for guests, app shell for authenticated users, and redirects super-admin users to a dedicated page.
- **Section state is internal, not URL-driven**: the main family experience is a single shell with tab activation rather than multi-route navigation.
- **Imperative DOM rendering**: `ui.js` and `ui-*` modules mutate live DOM and assume stable ids, class names, and modal markup.
- **Global mutable state**: `state.js` plus manual `notify()` orchestration must become Svelte stores without behavioral drift.
- **Platform coupling**: PWA install, service worker updates, push registration, WebSocket reconnect, and offline banners are spread across modules.
- **Mixed rendering styles**: some pages are static HTML templates, some are assembled from partials, some contain large inline scripts, and analytics mixes HTML chart rendering with Chart.js.

### Migration Strategy
Perform the migration **side-by-side** into a new `web-svelte/` workspace while keeping `web/` as the live legacy source of truth. Preserve current URLs, current API contracts, current visual language, and current role behavior. Do not begin parity sign-off until the new frontend already passes its own lint and build gates. The final release gate is a dedicated parity phase using the `ui-migration-parity-validator` skill until **100% UI and functional parity** is reached.

[↑ Back to top](#top)
[↩ Back to toc](#table-of-contents)

<a id="2-migration-principles"></a>

## 2. 📏 Migration Principles

1. **Preserve user-facing URLs** unless an explicit redirect plan is documented.
2. **Preserve the current visual system first**; do not redesign while migrating.
3. **Keep the Quarkus backend as the source of truth** for auth, family data, analytics, push, and admin actions.
4. **Preserve the root-route behavior**: guest landing, authenticated shell, and super-admin separation must match legacy behavior.
5. **Preserve request contracts**: same HTTP methods, paths, cookies, CSRF flow, and payload shapes unless the backend is intentionally updated.
6. **Preserve accessibility**: skip-link, focus behavior, dialog behavior, tab order, and aria usage must not regress.
7. **Preserve platform behavior**: WebSocket refresh, service worker caching, push registration, PWA install cues, and pull-to-refresh must behave the same.
8. **Keep legacy `web/` untouched during migration** except for explicit bridge points required to run legacy and new frontends in parallel.
9. **Treat the live legacy app as the source of truth** during validation, not the source code diff.
10. **Do not start parity closure until the new frontend passes lint and build**.

### Assumptions
- Backend APIs under `/api/*`, `/login-child/*`, and `/ws` remain authoritative and are not rewritten as part of this migration.
- The new frontend will run in a parallel workspace `web-svelte/` during migration so legacy and new UIs can be compared side-by-side.
- Existing static assets, icons, manifest, and images should be reused first, then optimized only after parity is proven.

### Non-Goals
- Rewriting Quarkus backend business logic
- Reworking mobile Capacitor packaging during the first migration pass
- Introducing a new design system, new IA, or new route taxonomy before parity is complete
- Removing the legacy `web/` app before parity and rollback checks are complete

[↑ Back to top](#top)
[↩ Back to toc](#table-of-contents)

<a id="3-library-and-platform-mapping"></a>

## 3. 🧩 Library and Platform Mapping

| Legacy Surface | SvelteKit Target | Why | Parity Risk |
|---|---|---|---|
| `src/server.js` Node edge | SvelteKit `adapter-node` + `hooks.server.ts` + deployment proxy | Preserve SSR and cookie-aware routing | High |
| `src/rendering.js` route dispatcher | SvelteKit file-based routes and server loads | Replace manual pathname routing | Medium |
| `views/*.html` templates | `src/routes/**/+page.svelte` | Replace static HTML templates with components | Medium |
| `views/components/*.html` partial assembly | `src/lib/components/**` + layouts | Replace string assembly with real components | Medium |
| `state.js` event bus | Svelte writable and derived stores | Replace mutable singleton with reactive state | High |
| `ui.js` and `ui-*` DOM renderers | Svelte components per section | Remove imperative DOM writes | High |
| `api.js` + CSRF helpers | Shared API service in `src/lib/services/api.ts` | Preserve backend contract | Low |
| `action-*.js` modules | Domain action services and store methods | Keep behavior, move orchestration out of DOM | Medium |
| `main-tabs.js` | Svelte tab store + actions | Preserve internal section switching | High |
| `child-switcher-ui.js` | Svelte component + portal/popover action | Preserve positioning and role flows | High |
| Chart.js CDN in `head.html` | NPM `chart.js` consumed inside Svelte components | Deterministic builds, same chart engine | Medium |
| `pull-to-refresh.js` | Svelte action | Preserve mobile gesture behavior | Medium |
| `push.js` | Framework-agnostic push service + Svelte subscriptions | Same Web Push / Capacitor bridge | Medium |
| `websocket.js` | Framework-agnostic WS service + store callbacks | Same backend WS token flow | Medium |
| `public/sw.js` | `src/service-worker.ts` | Native SvelteKit service worker entry | Medium |
| `client-error-reporter.js` | `hooks.client.ts` + browser listeners | Preserve runtime error capture | Low |
| `blogController.js` + markdown files | `+page.server.ts` loaders over same markdown directory | Preserve blog content and SEO | Low |
| Node test runner + Playwright | Playwright + node:test for server helpers + optional Vitest for Svelte units | Keep repo-friendly verification surface | Low |

[↑ Back to top](#top)
[↩ Back to toc](#table-of-contents)

<a id="4-architecture-mapping"></a>

## 4. 🗺️ Architecture Mapping

### Route Mapping

| Legacy Route | Current Implementation | SvelteKit Target |
|---|---|---|
| `/` | `rendering.js -> handleRootRoute()` | `src/routes/+page.server.ts` + `src/routes/+page.svelte` with session-aware landing/app rendering |
| `/login.html` | `rendering.js -> serveLogin()` | `src/routes/login.html/+page.svelte` |
| `/about` | `viewController.serveAbout()` | `src/routes/about/+page.svelte` |
| `/faq` | `viewController.serveFaq()` | `src/routes/faq/+page.svelte` |
| `/features/tasks` | `viewController.serveFeaturePage()` | `src/routes/features/[slug]/+page.svelte` |
| `/features/shop` | `viewController.serveFeaturePage()` | `src/routes/features/[slug]/+page.svelte` |
| `/blog` | `blogController.serveBlogIndex()` | `src/routes/blog/+page.svelte` |
| `/blog/:slug` | `blogController.serveArticle()` | `src/routes/blog/[slug]/+page.svelte` |
| `/reset-password` | `viewController.serveResetPassword()` | `src/routes/reset-password/+page.svelte` |
| `/verify` | `viewController.serveVerify()` | `src/routes/verify/+page.svelte` |
| `/super-admin` | `rendering.js -> handleSuperAdminRoute()` | `src/routes/super-admin/+page.svelte` |
| `/super-admin.html` | `rendering.js -> handleSuperAdminRoute()` | compatibility redirect to `/super-admin` or dedicated alias route |
| unknown path | `serveNotFound()` | `src/routes/[...path]/+page.svelte` or `+page.server.ts` |

### Backend Proxy Mapping

| Legacy Path | Current Behavior | SvelteKit Strategy |
|---|---|---|
| `/api/*` | proxied by Node edge to Quarkus backend | preserve through deployment proxy or SvelteKit server proxy helper |
| `/login-child/*` | proxied to backend | preserve unchanged |
| `/ws` | WebSocket upgrade proxied by Node edge | preserve via deployment proxy or adapter-node custom integration |
| `/api/page-data/session` | cookie-aware session snapshot | consume from server load / hooks with forwarded cookies |

### Authenticated Shell Mapping

| Legacy Partial / Section | SvelteKit Target |
|---|---|
| `views/components/head.html` | `src/app.html` + `src/routes/+layout.server.ts` + `svelte:head` |
| `views/components/header.html` | `src/lib/components/app/AppHeader.svelte` |
| `views/components/nav.html` | `src/lib/components/app/AppNav.svelte` |
| `views/components/main_start.html` | `src/lib/components/app/AppShellStart.svelte` or root shell layout |
| `views/components/section_analytics.html` | `src/lib/components/app/sections/AnalyticsSection.svelte` |
| `views/components/section_tasks.html` | `src/lib/components/app/sections/TasksSection.svelte` |
| `views/components/section_shop.html` | `src/lib/components/app/sections/ShopSection.svelte` |
| `views/components/section_requests.html` | `src/lib/components/app/sections/RequestsSection.svelte` |
| `views/components/section_catalog.html` | `src/lib/components/app/sections/CatalogSection.svelte` |
| `views/components/section_history.html` | `src/lib/components/app/sections/HistorySection.svelte` |
| `views/components/section_rules.html` | `src/lib/components/app/sections/RulesSection.svelte` |
| `views/components/section_friends.html` | `src/lib/components/app/sections/FriendsSection.svelte` |
| `views/components/section_settings.html` | `src/lib/components/app/sections/SettingsSection.svelte` |
| `views/components/section_limits.html` | `src/lib/components/app/sections/LimitsSection.svelte` |
| `views/components/modals.html` | `src/lib/components/modals/*` |
| `views/components/scripts.html` | root layout bootstrapping and client entry wiring |

### State and Runtime Mapping

| Legacy Module | SvelteKit Pattern |
|---|---|
| `state.js` | writable app store |
| `main-init.js` | bootstrap service + `load` + root layout effects |
| `main.js` | app shell composition + startup effects |
| `ui.js` | declarative child components |
| `main-tabs.js` | tab store + Svelte actions |
| `api.js` | typed API service |
| `action-*.js` | store methods / domain services |
| `utils.js` | focused utility modules and modal helpers |

[↑ Back to top](#top)
[↩ Back to toc](#table-of-contents)

<a id="5-file-by-file-backlog"></a>

## 5. 📝 File-by-File Backlog

### W-001. `web/` -> parallel `web-svelte/` workspace

**Type:** foundation  
**Current role:** legacy Node edge frontend workspace  
**Target analog:** new side-by-side `web-svelte/` SvelteKit application  
**Strategy:** create a parallel workspace instead of rewriting `web/` in place so legacy and new frontends can run together during parity validation.  
**Dependencies:** package manager, adapter-node, environment wiring, deployment scripts  
**UI-critical:** no  
**Functionality-critical:** yes  
**Risk:** medium  
**Complexity:** M  
**Estimate:** 3 SP  
**Status:** ⬜ planned

#### Subtasks
- [ ] Scaffold `web-svelte/` with SvelteKit, TypeScript, and adapter-node.
- [ ] Add scripts for `dev`, `build`, `preview`, `lint`, `test`, and `test:e2e`.
- [ ] Ensure legacy `web/` and new `web-svelte/` can run simultaneously on different ports.
- [ ] Add environment documentation for backend base URL, session endpoint, and WS target.

#### Acceptance Criteria
- New workspace boots independently.
- Legacy workspace remains untouched and runnable.
- Both apps can be launched in parallel for side-by-side checks.

### W-002. `web/src/server.js` + `web/src/sessionClient.js`

**Type:** runtime edge integration  
**Current role:** handles security headers, backend proxying, WebSocket upgrade, session bootstrap forwarding, and `/healthz`  
**Target analog:** `src/hooks.server.ts`, server utilities, deployment proxy rules, and optional adapter-node customization  
**Strategy:** preserve cookie-aware auth gating and proxy behavior while removing custom manual route dispatch from the legacy edge.  
**Dependencies:** backend cookies, forwarded headers, WS token flow, deployment reverse proxy  
**UI-critical:** no  
**Functionality-critical:** yes  
**Risk:** high  
**Complexity:** L  
**Estimate:** 8 SP  
**Status:** ⬜ planned

#### Subtasks
- [ ] Rebuild session snapshot resolution against `/api/page-data/session` using server-side loads.
- [ ] Preserve header forwarding semantics from `sessionClient.js`.
- [ ] Decide whether `/api/*`, `/login-child/*`, and `/ws` stay at infrastructure-proxy level or need a lightweight SvelteKit server bridge.
- [ ] Preserve `/healthz` behavior for deployment probes.
- [ ] Document local development topology for legacy/new side-by-side parity work.

#### Acceptance Criteria
- Authenticated and guest requests resolve the same session state as legacy.
- API and WebSocket traffic still reach the backend without behavioral drift.
- The new app can be used in parity sessions without cookie or origin surprises.

### W-003. `web/src/rendering.js` + `web/src/controllers/viewController.js` + `web/src/controllers/blogController.js`

**Type:** route dispatcher and SSR page composition  
**Current role:** maps pathnames to views, public pages, feature pages, blog routes, and not-found handling  
**Target analog:** SvelteKit routes and server loads  
**Strategy:** convert manual pathname routing into file-based routes while preserving every public URL and root-route branch.  
**Dependencies:** session snapshot, SEO metadata, markdown blog content, public assets  
**UI-critical:** yes  
**Functionality-critical:** yes  
**Risk:** high  
**Complexity:** L  
**Estimate:** 5 SP  
**Status:** ⬜ planned

#### Subtasks
- [ ] Split public route decisions into dedicated `+page.server.ts` and `+page.svelte` files.
- [ ] Preserve `/`, `/login.html`, `/about`, `/faq`, `/features/:slug`, `/blog`, `/blog/:slug`, `/reset-password`, `/verify`, and `404`.
- [ ] Replace string-based SEO replacements with `svelte:head` data.
- [ ] Preserve super-admin route branching and unknown-path handling.

#### Acceptance Criteria
- Public route matrix matches legacy behavior.
- No route loses metadata, canonical URLs, or content.
- Unknown paths still resolve to a not-found experience instead of a broken shell.

### W-004. `web/views/*.html` public/auth/blog pages

**Type:** page migration  
**Current role:** standalone templates for landing, about, faq, login, reset password, verify, blog, article, feature pages, and 404  
**Target analog:** route-level Svelte pages and shared public components  
**Strategy:** port each page as a Svelte route while extracting inline scripts into typed component logic.  
**Dependencies:** public nav, SEO, backend auth endpoints, markdown blog loader  
**UI-critical:** yes  
**Functionality-critical:** yes  
**Risk:** medium  
**Complexity:** L  
**Estimate:** 8 SP  
**Status:** ⬜ planned

#### Related Legacy Files

| Legacy File | Target |
|---|---|
| `landing.html` | `src/routes/+page.svelte` guest branch or `src/routes/(public)/landing/+page.svelte` |
| `about.html` | `src/routes/about/+page.svelte` |
| `faq.html` | `src/routes/faq/+page.svelte` |
| `feature-page.html` | `src/routes/features/[slug]/+page.svelte` |
| `blog-index.html` | `src/routes/blog/+page.svelte` |
| `article.html` | `src/routes/blog/[slug]/+page.svelte` |
| `login.html` | `src/routes/login.html/+page.svelte` |
| `reset-password.html` | `src/routes/reset-password/+page.svelte` |
| `verify.html` | `src/routes/verify/+page.svelte` |
| `404.html` | `src/routes/[...path]/+page.svelte` |

#### Subtasks
- [ ] Recreate public page layouts with shared components instead of string insertion.
- [ ] Extract login/register toggle logic from inline page scripts.
- [ ] Extract reset password and verify flows into component logic with the same validation and redirect timing.
- [ ] Preserve blog rendering over the same markdown source files.
- [ ] Preserve public CTA hierarchy and top-nav behavior.

#### Acceptance Criteria
- Public and auth pages are visually equivalent.
- Login, reset-password, and verify flows behave identically.
- Blog routes and feature pages keep their SEO and content structure.

### W-005. Authenticated root shell for `/`

**Type:** app shell route  
**Current role:** assembled HTML shell rendered at `/` for authenticated users with role-aware behavior  
**Target analog:** session-aware SvelteKit root page and layout  
**Strategy:** keep `/` as the authenticated family shell route to preserve behavior, and keep tab switching internal during the initial migration instead of exploding the app into many URLs.  
**Dependencies:** session load, child role/admin role, shell partials, app stores  
**UI-critical:** yes  
**Functionality-critical:** yes  
**Risk:** high  
**Complexity:** L  
**Estimate:** 8 SP  
**Status:** ⬜ planned

#### Subtasks
- [ ] Preserve guest landing vs authenticated shell branching at `/`.
- [ ] Preserve super-admin redirect behavior from root.
- [ ] Render one family shell route with internal section state for parity.
- [ ] Preserve default active section logic and child/admin branching.

#### Acceptance Criteria
- `/` behaves exactly like legacy for guest, child, admin, and super-admin sessions.
- The family shell opens without console errors.
- No current role flow requires a new URL to work.

### W-006. `web/views/components/*.html` authenticated shell partials

**Type:** layout and shell components  
**Current role:** header, nav, section containers, offline banner, pull-to-refresh indicator, scripts placeholder, and modal mount points  
**Target analog:** reusable Svelte components under `src/lib/components/app/`  
**Strategy:** port the shell partials into components while preserving ids, classes, aria attributes, and responsive layout hooks used by the existing logic and tests.  
**Dependencies:** app state, mobile layout budgets, toasts, dialogs  
**UI-critical:** yes  
**Functionality-critical:** yes  
**Risk:** medium  
**Complexity:** L  
**Estimate:** 8 SP  
**Status:** ⬜ planned

#### Related Legacy Partials

| Partial | Target Component |
|---|---|
| `head.html` | `app.html`, root layout head helpers |
| `header.html` | `AppHeader.svelte` |
| `nav.html` | `AppNav.svelte` |
| `main_start.html` | `AppShell.svelte` start block |
| `main_end.html` | `AppShell.svelte` end block |
| `scripts.html` | root startup wiring + toast mount |

#### Acceptance Criteria
- Header, nav, shell spacing, and section wrappers visually match legacy.
- Existing E2E selectors remain stable where practical.
- Offline banner and pull-to-refresh indicator remain in the same semantic positions.

### W-007. `web/public/js/modules/main.js` + `web/public/js/modules/main-init.js` + `web/public/js/modules/state.js`

**Type:** bootstrap and root client model  
**Current role:** app startup, initial data load, background services, PWA registration, skeletons, and mutable singleton state  
**Target analog:** root Svelte layout effects, app stores, and bootstrap service  
**Strategy:** move startup orchestration into explicit stores and effects, keeping the same boot order and same background services.  
**Dependencies:** session route, API service, push, WebSocket, service worker, role state  
**UI-critical:** no  
**Functionality-critical:** yes  
**Risk:** high  
**Complexity:** M  
**Estimate:** 5 SP  
**Status:** ⬜ planned

#### Subtasks
- [ ] Replace `state.js` singleton with Svelte stores.
- [ ] Rebuild bootstrap order from `main.js` and `main-init.js`.
- [ ] Preserve current child auto-selection rules and `earnit-last-child-id` localStorage behavior.
- [ ] Preserve initial skeleton rendering and background service startup order.
- [ ] Keep separate admin and child initialization behavior.

#### Acceptance Criteria
- Initial payload load matches legacy.
- Current child selection and persistence behave identically.
- The new app starts all background integrations in the same scenarios as legacy.

### W-008. `web/public/js/modules/api.js` + `server-contract.js` + `actions.js` + `action-*.js`

**Type:** API and mutation layer  
**Current role:** CSRF-aware fetch helpers, save scheduling, task/shop/request/history/admin mutations, and server payload normalization  
**Target analog:** typed API services and domain action modules under `src/lib/services/`  
**Strategy:** preserve the backend contract exactly while decoupling request logic from direct DOM event handlers.  
**Dependencies:** backend API surface, CSRF cookie, optimistic/local state updates, analytics refresh, request counters  
**UI-critical:** no  
**Functionality-critical:** yes  
**Risk:** high  
**Complexity:** M  
**Estimate:** 5 SP  
**Status:** ⬜ planned

#### Related Legacy Files

| File Group | Purpose |
|---|---|
| `api.js` | CSRF fetch wrapper and backend endpoints |
| `server-contract.js` | normalize server payload shape |
| `actions.js` | consolidated re-exports |
| `action-helpers.js` | save scheduling and shared mutation helpers |
| `action-tasks.js` | earn/request task flows |
| `action-shop.js` | purchase flow |
| `action-requests.js` | request approval and deletion |
| `action-history.js` | history deletion |
| `action-admin.js` | admin award flow |

#### Acceptance Criteria
- Same API endpoints, methods, headers, and payloads are used.
- Save scheduling and mutation timing remain compatible with legacy UX.
- No backend contract changes are required just to support the migration.

### W-009. `web/public/js/modules/main-tabs.js` + `mobile-layout.js` + `group-nav.js` + `motion-feedback.js`

**Type:** navigation and interaction shell  
**Current role:** internal tab activation, swipe gestures, dropdown positioning, view transition hooks, mobile layout budgets, and motion cues  
**Target analog:** tab store, Svelte actions, floating dropdown component, and responsive layout helpers  
**Strategy:** preserve the current one-shell navigation model and responsive mechanics before considering any route-level decomposition.  
**Dependencies:** shell markup, active role, visible sections, mobile viewport metrics  
**UI-critical:** yes  
**Functionality-critical:** yes  
**Risk:** high  
**Complexity:** M  
**Estimate:** 5 SP  
**Status:** ⬜ planned

#### Subtasks
- [ ] Preserve active-tab state and section visibility behavior.
- [ ] Preserve mobile swipe navigation and scroll-reset behavior.
- [ ] Preserve the floating more-menu portal and outside-click behavior.
- [ ] Preserve viewport-derived CSS variables from `mobile-layout.js`.
- [ ] Preserve motion cues that users currently depend on for feedback.

#### Acceptance Criteria
- Desktop and mobile navigation behave identically.
- No dropdown positioning or active-state regressions appear.
- Mobile viewport budgets still prevent overflow and clipped content.

### W-010. `web/public/js/modules/ui.js` + `ui-*` renderers + section partials

**Type:** core section rendering  
**Current role:** imperative DOM rendering for tasks, shop, requests, history, friends, and shared budget and list fragments  
**Target analog:** Svelte section components under `src/lib/components/app/sections/`  
**Strategy:** replace innerHTML-based renderers with declarative components while preserving visual hierarchy, empty states, counters, and section-specific semantics.  
**Dependencies:** app state store, action services, admin visibility, modal system  
**UI-critical:** yes  
**Functionality-critical:** yes  
**Risk:** high  
**Complexity:** L  
**Estimate:** 8 SP  
**Status:** ⬜ planned

#### Related Legacy Files

| Legacy File | Target Component |
|---|---|
| `ui.js` | section composition + shared shell rendering |
| `ui-tasks.js` | `TasksSection.svelte` |
| `ui-shop.js` | `ShopSection.svelte` |
| `ui-requests.js` | `RequestsSection.svelte` |
| `ui-history.js` | `HistorySection.svelte` |
| `ui-friends.js` | `FriendsSection.svelte` |
| `budget-ui.js` | shared budget and progress components |
| `section_tasks.html` | tasks section layout |
| `section_shop.html` | shop section layout |
| `section_requests.html` | requests section layout |
| `section_history.html` | history section layout |
| `section_friends.html` | friends section layout |

#### Acceptance Criteria
- All section states render without imperative patching.
- Empty states, counters, progress bars, and card states match legacy.
- Existing flows remain functional for both child and admin roles.

### W-011. `web/public/js/modules/analytics-ui.js` + `analytics-chart-config.js` + `analytics-empty-state.js`

**Type:** analytics feature  
**Current role:** KPI cards, mini progress bars, trends, recommendations, and chart rendering  
**Target analog:** Svelte analytics components backed by packaged Chart.js  
**Strategy:** keep the current chart engine to minimize visual drift, but move it from CDN/global usage to module-based integration inside Svelte components.  
**Dependencies:** analytics API, current child selection, chart config, no-children state  
**UI-critical:** yes  
**Functionality-critical:** yes  
**Risk:** medium  
**Complexity:** M  
**Estimate:** 5 SP  
**Status:** ⬜ planned

#### Subtasks
- [ ] Preserve current summary cards and mini progress widgets.
- [ ] Preserve trend chart labels, axes, and empty-state behavior.
- [ ] Preserve admin no-child handling.
- [ ] Remove Chart.js CDN dependency from `head.html` in the new app.

#### Acceptance Criteria
- Analytics visuals are materially identical.
- Chart data and labels match legacy across supported timeframes.
- No-children admin state matches the current UX.

### W-012. `web/public/js/modules/admin.js` + `admin-*.js` + `age-theme.js`

**Type:** family admin and child management flows  
**Current role:** task/shop CRUD, child switching, child settings, limits, password changes, child links, theme selection  
**Target analog:** Svelte admin components, stores, and modal forms  
**Strategy:** preserve current admin workflows and local persistence keys before any cleanup or API reshaping.  
**Dependencies:** app state, API service, modal system, child switcher, settings and limits sections  
**UI-critical:** yes  
**Functionality-critical:** yes  
**Risk:** high  
**Complexity:** L  
**Estimate:** 8 SP  
**Status:** ⬜ planned

#### Related Legacy Files

| File | Purpose |
|---|---|
| `admin.js` | admin API surface re-exports |
| `admin-tasks.js` | task CRUD modal flow |
| `admin-shop.js` | shop CRUD modal flow |
| `admin-settings.js` | child settings, limits, links |
| `admin-children.js` | child selection and add-child flow |
| `admin-passwords.js` | change password flow |
| `age-theme.js` | per-child theme selection and persistence |

#### Acceptance Criteria
- Admin CRUD flows remain functionally identical.
- Theme switching still uses the same persisted keys and updates the same UI areas.
- Child switch, add-child, and link-management flows remain stable.

### W-013. `web/public/js/modules/child-switcher-ui.js` + `utils.js` + dialog helpers

**Type:** UI infrastructure  
**Current role:** child-switcher dropdown, custom confirm flows, modal open/close, clipboard helpers, toasts, and shared UI utilities  
**Target analog:** Svelte components for child switcher, modal host, toast host, and focused utility modules  
**Strategy:** preserve current ids and interaction semantics while removing imperative document-wide handlers where possible.  
**Dependencies:** app shell, admin flows, modal suite, tests covering regressions  
**UI-critical:** yes  
**Functionality-critical:** yes  
**Risk:** high  
**Complexity:** M  
**Estimate:** 5 SP  
**Status:** ⬜ planned

#### Subtasks
- [ ] Preserve child-switcher dropdown positioning and refresh behavior.
- [ ] Preserve confirm dialog and alert semantics.
- [ ] Preserve toast behavior and timing.
- [ ] Preserve clipboard and share-link flows.
- [ ] Carry over modal regression coverage before refactoring implementation details.

#### Acceptance Criteria
- No dialog or dropdown positioning regressions appear.
- Modal backdrop, close, and confirm behavior match current flows.
- Existing UI-regression tests can be mapped directly to new behavior.

### W-014. `web/public/js/modules/pull-to-refresh.js` + `push.js` + `websocket.js` + `pwa-install.js` + `ios-dev-fallback.js` + `client-error-reporter.js` + `public/sw.js`

**Type:** platform and runtime integration  
**Current role:** mobile gesture refresh, Web Push registration, WebSocket refresh, service worker lifecycle, offline handling, PWA install prompts, runtime error reporting, and iOS development fallback  
**Target analog:** Svelte actions, browser-side services, `hooks.client.ts`, and `src/service-worker.ts`  
**Strategy:** keep the feature set intact and migrate implementation mechanics, not behavior.  
**Dependencies:** service worker registration, API service, app bootstrap, backend WS token endpoint, backend push endpoints  
**UI-critical:** yes  
**Functionality-critical:** yes  
**Risk:** high  
**Complexity:** L  
**Estimate:** 8 SP  
**Status:** ⬜ planned

#### Subtasks
- [ ] Rebuild pull-to-refresh as a Svelte action with the same threshold behavior.
- [ ] Preserve push registration for both browser and Capacitor paths.
- [ ] Preserve WebSocket token fetch and reconnect logic.
- [ ] Preserve service worker update and cache semantics.
- [ ] Preserve install prompt and offline/online UI behavior.
- [ ] Preserve client error reporting and deduplication logic.

#### Acceptance Criteria
- PWA, push, WebSocket, offline, and runtime error flows all keep working.
- Mobile gesture behavior matches legacy.
- No new platform-specific regressions appear on desktop or mobile.

### W-015. `web/views/super-admin.html` + `web/public/js/super-admin.js` + `super-admin-*.js`

**Type:** super-admin console migration  
**Current role:** dedicated privileged UI for family management, base catalog CRUD, DB operations, system metrics, logs, and dialogs  
**Target analog:** `src/routes/super-admin/+page.svelte` with dedicated component tree and internal tabs  
**Strategy:** migrate super-admin as a separate surface with its own parity scope instead of folding it into the family shell.  
**Dependencies:** privileged backend APIs, dialogs, system polling, filters, tables, upload flows  
**UI-critical:** yes  
**Functionality-critical:** yes  
**Risk:** high  
**Complexity:** L  
**Estimate:** 8 SP  
**Status:** ⬜ planned

#### Related Legacy Files

| File | Purpose |
|---|---|
| `super-admin.html` | privileged shell and tabs |
| `super-admin.js` | root super-admin bootstrap |
| `super-admin-base.js` | catalog CRUD |
| `super-admin-db.js` | backup and restore |
| `super-admin-dialogs.js` | privileged confirm and alert flows |
| `super-admin-families.js` | family search, filter, detail modal |
| `super-admin-family-details.js` | family detail rendering |
| `super-admin-filters.js` | filter logic |
| `super-admin-system.js` | metrics, logs, and polling |

#### Acceptance Criteria
- Super-admin routes and tabs match legacy.
- Family list, filters, and detail modal remain stable.
- Catalog, DB, and system dashboards preserve all current capabilities.

### W-016. `web/public/css/**/*` + assets + SEO templates

**Type:** styling and static assets  
**Current role:** legacy visual language, route-specific CSS, CSS partial assembly, icons, manifest, images, and SEO token insertion  
**Target analog:** `src/app.css`, route-specific styles, reusable CSS partial imports, `static/` assets, and `svelte:head` metadata  
**Strategy:** port CSS with minimal semantic change first, then optimize only after parity is signed off.  
**Dependencies:** all routes, all shell components, manifest, service worker, build pipeline  
**UI-critical:** yes  
**Functionality-critical:** yes  
**Risk:** medium  
**Complexity:** M  
**Estimate:** 5 SP  
**Status:** ⬜ planned

#### Subtasks
- [ ] Preserve `style.css`, CSS partials, `public-top-nav.css`, and `super-admin.css` behavior.
- [ ] Reuse current icons, images, manifest, and favicon surface.
- [ ] Rebuild canonical/meta/schema output with `svelte:head` instead of string placeholders.
- [ ] Keep class names stable where Playwright and smoke tests depend on them.

#### Acceptance Criteria
- The new frontend visually matches legacy before any cleanup pass.
- All public assets resolve correctly in dev and production.
- SEO output remains equivalent on public routes and blog routes.

### W-017. Build, lint, test, and release-quality gates

**Type:** quality gate  
**Current role:** legacy uses `lint`, `build`, `test`, `test:ui`, and Playwright smoke/E2E flows  
**Target analog:** SvelteKit quality gates that must pass before parity work starts  
**Strategy:** define explicit gates for the new frontend and make them blocking for parity and cutover.  
**Dependencies:** package scripts, Playwright, ESLint, build output, CI wiring, Docker  
**UI-critical:** no  
**Functionality-critical:** yes  
**Risk:** medium  
**Complexity:** M  
**Estimate:** 8 SP  
**Status:** ⬜ planned

#### Subtasks
- [ ] Add `lint`, `build`, `test`, and `test:e2e` commands for `web-svelte/`.
- [ ] Recreate key smoke coverage from current UI and E2E suites.
- [ ] Ensure local and CI execution both work.
- [ ] Document which commands are required before parity validation can begin.

#### Acceptance Criteria
- New frontend lint passes.
- New frontend build passes.
- Smoke and targeted E2E tests pass.
- This item is complete before any parity-closure work is started.

### W-018. Final parity closure with `ui-migration-parity-validator`

**Type:** migration release gate  
**Current role:** no equivalent in legacy; this is the non-optional closure phase  
**Target analog:** structured parity workflow executed only after W-017 passes  
**Strategy:** use the `ui-migration-parity-validator` skill as the formal workflow for comparing legacy and new frontends until the result is **100% UI and functional parity**.  
**Dependencies:** W-001 through W-017 complete enough for side-by-side execution  
**UI-critical:** yes  
**Functionality-critical:** yes  
**Risk:** critical  
**Complexity:** XL  
**Estimate:** 13 SP  
**Status:** ⬜ planned

#### Entry Gate
- [ ] `web-svelte` lint is green.
- [ ] `web-svelte` build is green.
- [ ] baseline smoke tests are green.
- [ ] legacy `web/` and new `web-svelte/` both start locally on separate ports.

#### Required Validator Tasks
- [ ] Run the validator discovery pass and produce a route matrix for legacy vs new frontend.
- [ ] Start both apps and ensure they use the same backend and comparable user accounts/state.
- [ ] Walk every public, auth, family-shell, and super-admin route in browser.
- [ ] Validate desktop `1440px`, tablet `768px`, and mobile `375px` for every route.
- [ ] Validate all interactive scenarios: forms, modals, tab switching, child switcher, admin CRUD, child request lifecycle, no-children admin flow, limits edge cases, public CTA flows, blog/article navigation.
- [ ] Compare network requests, response handling, cookies, localStorage keys, and service worker behavior.
- [ ] Compare WebSocket, push, offline, and recovery behavior.
- [ ] Run accessibility checks and ensure no new serious or critical violations appear.
- [ ] Produce a defect list with route, difference, severity, and target file to fix in the new frontend.
- [ ] Apply only minimal fixes in the new frontend, then re-run the validator.
- [ ] Add failing-first tests for every critical parity defect that required a fix.
- [ ] Repeat until no open parity defects remain.

#### Acceptance Criteria
- Legacy and new frontend reach **100% UI parity**.
- Legacy and new frontend reach **100% functional parity**.
- No open critical or minor parity defects remain.
- Storage keys, network contracts, and accessibility outcomes match legacy expectations.
- Release cutover is blocked until this item is complete.

[↑ Back to top](#top)
[↩ Back to toc](#table-of-contents)

<a id="6-dependency-waves"></a>

## 6. 🌊 Dependency Waves

### Wave 1: Foundation and Runtime Seam
**Tasks:** W-001, W-002, W-016, W-017  
**Goal:** new workspace, server/runtime bridge, CSS/assets baseline, and quality gates exist  
**Blocks:** every functional migration wave  
**Estimate:** 24 SP

### Wave 2: Public Routing and Content
**Tasks:** W-003, W-004  
**Goal:** public, auth, blog, and route mapping are live in SvelteKit  
**Blocks:** parity route walkthrough  
**Estimate:** 13 SP

### Wave 3: Authenticated Shell and State
**Tasks:** W-005, W-006, W-007, W-008, W-009  
**Goal:** root app shell, stores, tab model, and mutation layer are functional  
**Blocks:** all family-shell sections  
**Estimate:** 31 SP

### Wave 4: Feature Surfaces
**Tasks:** W-010, W-011, W-012, W-013, W-014, W-015  
**Goal:** every family-shell and super-admin feature exists in the new frontend  
**Blocks:** full parity validation  
**Estimate:** 42 SP

### Wave 5: Parity Closure
**Tasks:** W-018  
**Goal:** achieve 100% UI and functional parity after lint/build/test gates are green  
**Blocks:** release cutover and legacy decommissioning  
**Estimate:** 13 SP

### Total Estimate: ~123 SP

[↑ Back to top](#top)
[↩ Back to toc](#table-of-contents)

<a id="7-critical-path"></a>

## 7. ⛓️ Critical Path

```text
W-001 Workspace scaffold
  -> W-002 Runtime seam and session forwarding
    -> W-003 Public route decomposition
    -> W-005 Root authenticated shell
      -> W-007 Bootstrap store model
      -> W-008 API and mutation layer
      -> W-009 Tabs and navigation model
        -> W-010 Section renderers
        -> W-011 Analytics
        -> W-012 Admin flows
        -> W-013 Modal and child switcher infra
        -> W-014 Platform integrations
        -> W-015 Super-admin console
          -> W-017 Lint/build/test gates
            -> W-018 Parity closure to 100%
```

### Explicit Release Blockers
- W-002 — if session forwarding and backend proxy behavior drift, nothing else can be trusted.
- W-007 + W-008 — if store/bootstrap/mutation behavior drifts, every app section diverges.
- W-015 — super-admin is a separate privileged surface and cannot be left for manual cleanup.
- W-017 — parity work does not start before lint and build are green.
- W-018 — release does not happen before 100% parity is verified.

[↑ Back to top](#top)
[↩ Back to toc](#table-of-contents)

<a id="8-high-risk-items"></a>

## 8. ⚠️ High-Risk Items

| ID | Item | Risk Level | Why |
|---|---|---|---|
| W-002 | Runtime seam and proxy behavior | Critical | Cookie-aware root behavior, API proxying, and WS upgrade behavior are foundational |
| W-005 | Root authenticated shell | High | `/` is conditional and must preserve guest vs child vs admin vs super-admin behavior |
| W-007 | Bootstrap and state model | High | The legacy app relies on global mutable state and implicit startup order |
| W-009 | Tab and navigation model | High | Current UX depends on internal section switching, swipe gestures, and floating menus |
| W-010 | Section renderer suite | High | Large surface area with imperative DOM rendering and role-specific states |
| W-012 | Admin flows | High | CRUD, child switching, theme persistence, links, and limits must all remain stable |
| W-013 | Child switcher and modal infra | High | Dropdown positioning and dialog behavior already have regression coverage |
| W-014 | Platform integrations | High | PWA, push, WS, offline, and pull-to-refresh are easy to partially break |
| W-015 | Super-admin console | High | Separate privileged UX with many tabs, filters, uploads, and polling states |
| W-018 | Parity closure | Critical | Final acceptance criterion is explicitly 100% UI and functional parity |

### Largest Legacy Client Modules Worth Front-Loading
- `child-switcher-ui.js`
- `analytics-ui.js`
- `super-admin-families.js`
- `api.js`
- `main.js`
- `super-admin-system.js`
- `utils.js`
- `main-tabs.js`

[↑ Back to top](#top)
[↩ Back to toc](#table-of-contents)

<a id="9-parity-checklist"></a>

## 9. ✅ Parity Checklist

### Public and Auth Routes
- [ ] `/` guest landing matches legacy
- [ ] `/about` matches legacy
- [ ] `/faq` matches legacy
- [ ] `/features/tasks` matches legacy
- [ ] `/features/shop` matches legacy
- [ ] `/blog` matches legacy
- [ ] `/blog/:slug` matches legacy
- [ ] `/login.html` matches legacy
- [ ] `/reset-password` matches legacy
- [ ] `/verify` matches legacy
- [ ] 404 route matches legacy

### Authenticated Family Shell
- [ ] `/` authenticated child shell matches legacy
- [ ] `/` authenticated admin shell matches legacy
- [ ] no-children admin state matches legacy
- [ ] section switching and default active section match legacy
- [ ] header balance, delta animation, and counters match legacy
- [ ] role-based visibility (`admin-only`, `child-only`, `requires-child`) matches legacy

### Family Sections
- [ ] analytics section matches legacy
- [ ] tasks section matches legacy
- [ ] shop section matches legacy
- [ ] requests section matches legacy
- [ ] history section matches legacy
- [ ] friends section matches legacy
- [ ] rules section matches legacy
- [ ] settings section matches legacy
- [ ] limits section matches legacy
- [ ] catalog section matches legacy

### Admin and Modal Flows
- [ ] task add/edit/delete matches legacy
- [ ] shop add/edit/delete matches legacy
- [ ] child switcher matches legacy
- [ ] add-child modal matches legacy
- [ ] change-password flow matches legacy
- [ ] child-link copy/regenerate flow matches legacy
- [ ] confirm and alert dialogs match legacy

### Super-Admin Surface
- [ ] families tab matches legacy
- [ ] family search and filters match legacy
- [ ] family detail modal matches legacy
- [ ] base catalog task CRUD matches legacy
- [ ] base catalog product CRUD matches legacy
- [ ] DB backup/restore panel matches legacy
- [ ] system dashboard, logs, and HTTP metrics match legacy

### Platform and Runtime
- [ ] pull-to-refresh matches legacy
- [ ] WebSocket refresh and reconnect behavior match legacy
- [ ] push registration and notification handling match legacy
- [ ] service worker install/update/cache behavior matches legacy
- [ ] PWA install prompt and offline banner match legacy
- [ ] runtime error reporting behavior matches legacy

### Responsive and Accessibility
- [ ] desktop `1440px` matches legacy
- [ ] tablet `768px` matches legacy
- [ ] mobile `375px` matches legacy
- [ ] no new overlap, clipping, or viewport-budget regressions
- [ ] no new serious or critical accessibility violations
- [ ] keyboard navigation, focus order, and dialog close behavior match legacy

### Network and Storage
- [ ] same API routes are called with the same methods
- [ ] same payload shapes are sent to the backend
- [ ] same cookies are relied on for auth and CSRF
- [ ] same localStorage keys are used (`earnit-last-child-id`, theme keys, and related UI state)
- [ ] service worker and cache behavior do not introduce contract drift

### Release Gate
- [ ] lint is green
- [ ] build is green
- [ ] smoke tests are green
- [ ] parity validator reports 100% UI and functional parity

[↑ Back to top](#top)
[↩ Back to toc](#table-of-contents)

<a id="10-sveltekit-target-file-structure"></a>

## 10. 🏗️ SvelteKit Target File Structure

```text
web-svelte/
├── src/
│   ├── app.html
│   ├── app.css
│   ├── hooks.server.ts
│   ├── hooks.client.ts
│   ├── service-worker.ts
│   ├── routes/
│   │   ├── +layout.server.ts
│   │   ├── +layout.svelte
│   │   ├── +page.server.ts
│   │   ├── +page.svelte
│   │   ├── login.html/
│   │   │   └── +page.svelte
│   │   ├── about/
│   │   │   └── +page.svelte
│   │   ├── faq/
│   │   │   └── +page.svelte
│   │   ├── features/
│   │   │   └── [slug]/
│   │   │       └── +page.svelte
│   │   ├── blog/
│   │   │   ├── +page.server.ts
│   │   │   ├── +page.svelte
│   │   │   └── [slug]/
│   │   │       ├── +page.server.ts
│   │   │       └── +page.svelte
│   │   ├── reset-password/
│   │   │   └── +page.svelte
│   │   ├── verify/
│   │   │   └── +page.svelte
│   │   ├── super-admin/
│   │   │   ├── +page.server.ts
│   │   │   └── +page.svelte
│   │   └── [...path]/
│   │       └── +page.svelte
│   ├── lib/
│   │   ├── components/
│   │   │   ├── public/
│   │   │   ├── app/
│   │   │   ├── superAdmin/
│   │   │   └── modals/
│   │   ├── stores/
│   │   │   ├── appState.ts
│   │   │   ├── session.ts
│   │   │   ├── tabs.ts
│   │   │   ├── notifications.ts
│   │   │   └── platform.ts
│   │   ├── services/
│   │   │   ├── api.ts
│   │   │   ├── bootstrap.ts
│   │   │   ├── websocket.ts
│   │   │   ├── push.ts
│   │   │   ├── pwa.ts
│   │   │   ├── analytics.ts
│   │   │   └── errors.ts
│   │   ├── server/
│   │   │   ├── session.ts
│   │   │   ├── blog.ts
│   │   │   ├── seo.ts
│   │   │   └── backend.ts
│   │   ├── actions/
│   │   │   ├── pullToRefresh.ts
│   │   │   ├── swipeTabs.ts
│   │   │   └── floatingMenu.ts
│   │   └── styles/
│   │       └── partials/
├── static/
│   ├── img/
│   ├── manifest.json
│   ├── favicon.ico
│   └── ...existing static assets
├── package.json
├── svelte.config.js
├── vite.config.ts
├── tsconfig.json
├── eslint.config.js
└── playwright.config.ts
```

[↑ Back to top](#top)
[↩ Back to toc](#table-of-contents)

<a id="11-summary-table"></a>

## 11. 📊 Summary Table

| Wave | Tasks | SP | Blocking? |
|---|---|---|---|
| 1. Foundation and Runtime Seam | W-001, W-002, W-016, W-017 | 24 | Yes |
| 2. Public Routing and Content | W-003, W-004 | 13 | Yes |
| 3. Authenticated Shell and State | W-005, W-006, W-007, W-008, W-009 | 31 | Yes |
| 4. Feature Surfaces | W-010, W-011, W-012, W-013, W-014, W-015 | 42 | Yes |
| 5. Parity Closure | W-018 | 13 | Release blocker |
| **Total** | **18 core backlog items** | **~123 SP** | |

### Final Rule

The migration is **not complete** when the SvelteKit app merely builds or looks close enough. The migration is complete only when:

1. Lint is green.
2. Build is green.
3. Smoke and targeted E2E tests are green.
4. The `ui-migration-parity-validator` workflow has been executed.
5. Legacy and new frontend have reached **100% UI and functional parity**.

[↑ Back to top](#top)
[↩ Back to toc](#table-of-contents)