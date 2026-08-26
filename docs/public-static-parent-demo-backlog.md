# Public Static Parent Demo - Implementation Backlog

## Goal

Let an unauthenticated visitor open a live, browser-based parent demo from the static public site. The demo must present a realistic child workspace with tasks, a rewards catalog, history, and the child's submitted requests, while rendering in the visitor's supported browser locale (`en` or `ru`) without accessing or changing family data.

## Architectural decisions

- `apps/web/static/public/` remains the owner of the public demo. It must not bootstrap `WorkspaceRoleResolver`, call `/api/data`, or reuse the authenticated Svelte store: those paths depend on a server-authorized family session.
- The demo's source of truth is a versioned, immutable static fixture plus a small locale resolver. It is sample data only; approve, reject, create, purchase, and delete operations must not be exposed as real mutations or API calls.
- For an anonymous static page, choose `ru` when a normalized value in `navigator.languages`/`navigator.language` is Russian; otherwise choose `en`. A valid explicit `?lang=en|ru` takes precedence and is preserved by in-demo navigation. Do not write the authenticated-family `locale` cookie or alter `FamilyEntity.locale`.
- Keep public-demo translations separate from the Svelte i18n payload because static HTML is intentionally outside the SvelteKit localization pipeline. Both locale dictionaries must have identical message keys and be covered by a contract test.
- Reuse the current public-site CSP, responsive tokens, public CTA conventions, and the compact list presentation established by the parent workspace. Do not add a compatibility `/login` route or make the demo a new authenticated workspace mode.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | PSD-001 | P1 | - | Defines the safe fixture and locale contract used by the UI. |
| 2 | PSD-002 | P1 | PSD-001 | Builds the isolated, usable parent-demo surface. |
| 3 | PSD-003 | P2 | PSD-002 | Makes the demo discoverable from every static marketing page. |
| 4 | PSD-004 | P2 | PSD-001, PSD-002, PSD-003 | Locks down locale selection, public isolation, accessibility, and mobile geometry. |

## PSD-001: Define the static demo data and guest-locale contract

**Status:** TODO
**Priority:** P1

**Depends on:** -

**Exact scope:**

Create the static-only data and localization boundary for the parent demo. It supplies one fixed child profile, assigned tasks, a reward catalog, wallet/history entries, and task/reward requests in English and Russian.

**Files:**

- Create `apps/web/static/public/demo-data.js`.
- Create `apps/web/static/public/demo-i18n.js`.
- Create `apps/web/tests/unit/publicDemoI18n.test.ts`.
- Search anchor: `normalizeLocale` in `apps/web/src/lib/i18n/config.ts` for the existing supported-language normalization rules to mirror, not import into the static runtime.

**Goal:**

The static demo has a deterministic, localized data model that can be rendered without a backend request or authenticated state.

### Outcome

The UI can obtain a complete read-only parent scenario for `en` and `ru`, with all user-facing labels and sample entity text translated before it renders.

### Architectural decision

`demo-data.js` owns only locale-neutral identifiers, quantities, statuses, timestamps, and scenario relations. `demo-i18n.js` owns display copy, localized fixture labels, supported-locale detection, the `?lang` override, and URL construction; it must not import Svelte modules or modify application/family locale storage.

### Required changes

1. Model a single named child with balance, several active tasks, several rewards, both task-completion and reward-purchase requests, and positive and negative history entries. Include at least one pending request and one resolved request so every required state is visible.
2. Provide complete English and Russian dictionaries for page chrome, tabs, task/reward metadata, request type/status labels, empty/error-free demo state, currency/coin labels, demo disclaimer, and return/sign-in CTAs. Keep entity IDs and status values locale-neutral.
3. Resolve a locale from a validated `lang` query value first, then from the first supported browser language, falling back to English. Preserve only the recognized `lang` value when generating tab and return links; ignore malformed or unsupported input.
4. Export pure helpers for locale resolution, translation lookup, locale-aware date/number formatting, and fixture projection so they can be unit-tested without a DOM. Define safe fallback text for an absent translation key rather than injecting HTML.
5. Add unit coverage for Russian and English browser-language resolution, explicit-query precedence, unsupported-value fallback, URL preservation, matching translation keys, and the fixture's required tasks/rewards/history/requests.

### Out of scope

- Persisting an anonymous visitor's language preference, adding locales beyond English and Russian, or changing `LocaleSwitcher`.
- Copying real family data, calling any API, schema migrations, or adding sample data to the database.

### Acceptance criteria

- A Russian browser resolves the Russian demo and an English or unsupported browser resolves English unless a valid `?lang=` explicitly selects the other supported locale.
- Every fixture entry needed by Tasks, Rewards, History, and Requests exists in both locales with stable cross-locale IDs, amounts, statuses, and timestamps.
- The static modules do not import `$lib`, access cookies/localStorage, issue `fetch`, or contain real account/family identifiers.
- English and Russian dictionaries expose the same keys; dates and numeric balances use the selected locale.

### Targeted validation

```bash
cd apps/web && npm run test -- tests/unit/publicDemoI18n.test.ts
```

### Commit

```bash
git add apps/web/static/public/demo-data.js apps/web/static/public/demo-i18n.js apps/web/tests/unit/publicDemoI18n.test.ts
git commit -m "feat(web): add localized public demo fixture"
```

## PSD-002: Render the read-only parent workspace demo

**Status:** TODO
**Priority:** P1

**Depends on:** PSD-001

**Exact scope:**

Create a standalone static demo page that renders the supplied parent scenario and lets a visitor switch among Tasks, Rewards, History, and Requests without leaving the demo or touching real application data.

**Files:**

- Create `apps/web/static/public/demo.html`.
- Create `apps/web/static/public/demo.js`.
- Modify `apps/web/static/public/styles.css`.
- Search anchor: `TelegramParentShell` in `apps/web/src/lib/components/telegram/TelegramParentShell.svelte` for the parent information architecture and tab semantics to mirror visually, without importing it.
- Search anchor: `TelegramRequestRow` in `apps/web/src/lib/components/telegram/TelegramRequestRow.svelte` for request kind, status, and positive/negative amount presentation.

**Goal:**

Visitors can explore a credible parent web workspace in the static site and understand what they will see after registration, without mistaking it for their own account.

### Outcome

`/public/demo.html` has a localized parent header, child context, balance summary, four keyboard-accessible tabs, and compact lists for tasks, rewards, history, and submitted child requests.

### Architectural decision

The page is a self-contained static renderer over the PSD-001 modules. Its tab state is an in-page presentation state reflected in the query string (`tab=tasks|rewards|history|requests`) and not an application route, session, or writable store. It must retain the public site's CSP-compatible external module scripts and avoid inline event handlers/styles.

### Required changes

1. Build semantic static markup with a skip link, localized document language/title/description, a labelled demo notice, a parent/child summary, and links back to the public site and to the existing real parent sign-in flow.
2. Render a tablist with `role="tab"`, `aria-selected`, `aria-controls`, and distinct `role="tabpanel"` containers. Support mouse, Enter/Space, ArrowLeft/ArrowRight, Home, and End; update `tab` in the current URL without a navigation that loses `lang`.
3. In the Tasks tab, show assigned task title, group, repeat/availability metadata, and coin amount. In Rewards, show the catalogue title, group, price, and the selected child's available balance. In History, show time, title, and visually plus programmatically distinguish earned from spent coins. In Requests, show child name, task/reward type, submitted time, amount, and pending/approved/rejected status.
4. Render all text through the static locale layer and use `Intl` formatting supplied by PSD-001. If data is unexpectedly absent, show a localized non-interactive empty state rather than a broken or blank panel.
5. Add scoped public CSS that matches the compact existing family UI: no nested card stacks, lists remain readable at 320px, long entity names truncate/wrap before actions, and interactive controls have at least 44 by 44px targets. Respect reduced-motion preferences and retain a clearly visible focus indicator.
6. Make the demo explicitly read-only: do not render approve/reject, add, edit, buy, or delete controls; do not issue `fetch`, mutate the fixture, write local state, or register a service worker from the page.

### Out of scope

- Making requests approvable, simulating purchases, dashboard analytics, child switching, catalog administration, or loading/error retry flows for an API.
- Changing the authenticated `/workspace` or `/telegram` layout, stores, data contracts, or CSS.

### Acceptance criteria

- Direct visits to `/public/demo.html`, `/public/demo.html?lang=ru`, and `/public/demo.html?lang=en&tab=rewards` render the chosen localized scenario without authentication or network requests to `/api/`.
- The four required sections are all reachable through keyboard and pointer interaction; the active tab and panel semantics stay synchronized and the selected `lang` persists across tab changes.
- The Requests panel visibly includes child-submitted task and reward requests with both pending and resolved statuses; History includes both earned and spent entries.
- At 320px wide, no horizontal page overflow occurs; every actionable link/tab meets the 44px target and keyboard focus is visible.
- The demo notice states that the scenario is sample data, and real parent sign-in still targets `/api/login-google/start?continue=%2Fworkspace`.

### Targeted validation

```bash
cd apps/web && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/static/public/demo.html apps/web/static/public/demo.js apps/web/static/public/styles.css
git commit -m "feat(web): add read-only parent workspace demo"
```

## PSD-003: Expose the demo from the static public site

**Status:** TODO
**Priority:** P2

**Depends on:** PSD-002

**Exact scope:**

Add a clearly labelled demo entry to each existing static public page, without weakening the existing Telegram and real Google sign-in paths.

**Files:**

- Modify `apps/web/static/public/index.html`.
- Modify `apps/web/static/public/how.html`.
- Modify `apps/web/static/public/tasks.html`.
- Modify `apps/web/static/public/rewards.html`.
- Modify `apps/web/static/public/parents.html`.
- Modify `apps/web/static/public/faq.html`.
- Modify `apps/web/static/public/site.js`.
- Search anchor: `data-browser-workspace-link` in `apps/web/static/public/site.js` for the existing real-parent access behavior that must remain unchanged.

**Goal:**

A visitor can find and open the safe demo from every public marketing page, in the same language selected for the demo, while still having a separate route for real parent sign-in.

### Outcome

Every static public page offers a compact “View parent demo” / “Посмотреть демо родителя” link that resolves the browser locale and points to the localized demo URL; “Sign in as parent” keeps its existing OAuth behavior and label.

### Architectural decision

`site.js` may reuse the pure locale/link helper exported by `demo-i18n.js`, but it must not duplicate locale detection or take over Google CTA handling. The demo entry remains a regular anchor for JavaScript-off navigation, with an English fallback URL; enhancement can refine it to the detected language.

### Required changes

1. Add a visible, semantically named demo link to the shared header or other equivalent consistent placement on all six static pages, with a 44px target and compact-mobile treatment. Do not replace either existing access choice.
2. Give the link a stable data attribute and a no-JavaScript `href` to `/public/demo.html?lang=en`; on enhancement, resolve the visitor locale and replace only the demo URL while retaining any existing safe query values.
3. Localize the link label and its accessible name using the static locale helper. Keep marketing-page content otherwise unchanged; this task does not translate the existing Russian marketing pages.
4. Confirm the added module dependency and anchors comply with the static CSP and do not introduce inline scripts, third-party connections, or an OAuth interception regression.

### Out of scope

- Translating the whole marketing site, adding a global language switcher, changing public SEO/canonical URLs, or changing the login fallback contract.

### Acceptance criteria

- Every public page has a visible demo link and retains both Telegram and “Sign in as parent” access choices.
- With JavaScript disabled, the link opens the English demo; with JavaScript enabled, a Russian browser opens `demo.html?lang=ru` and an English browser opens `demo.html?lang=en`.
- Clicking the demo link does not call `/api/auth-config`, `/api/login-google/url`, or any data API; clicking the real parent sign-in link retains its current OAuth/fallback behavior.
- The new link is reachable via keyboard, has visible focus, and does not create horizontal overflow at 320px.

### Targeted validation

```bash
cd apps/web && npm run test -- tests/unit/publicSiteAccess.test.ts && npm run build
```

### Commit

```bash
git add apps/web/static/public/index.html apps/web/static/public/how.html apps/web/static/public/tasks.html apps/web/static/public/rewards.html apps/web/static/public/parents.html apps/web/static/public/faq.html apps/web/static/public/site.js
git commit -m "feat(web): link public site to parent demo"
```

## PSD-004: Prove the static demo contract in browser coverage

**Status:** TODO
**Priority:** P2

**Depends on:** PSD-001, PSD-002, PSD-003

**Exact scope:**

Extend focused public-site browser coverage for the live demo and its locale/accessibility guarantees. Keep it independent of authenticated workspace fixtures.

**Files:**

- Modify `apps/web/tests/e2e/workspace-entry.spec.ts`.
- Modify `apps/web/tests/unit/publicDemoI18n.test.ts` if a focused regression case is missing after browser coverage is authored.
- Search anchor: `public pages keep both access choices usable at the compact mobile width` in `apps/web/tests/e2e/workspace-entry.spec.ts` for the existing static-site viewport and CTA assertions to extend.

**Goal:**

Regression coverage proves that the demo remains static, localized, navigable, discoverable, and usable on a narrow screen.

### Outcome

Focused unit and Playwright tests verify the two guest locales, all four parent-demo sections, the direct and marketing-page entry paths, public isolation, and compact accessibility behavior.

### Architectural decision

Use the existing preview E2E backend only to serve the production web build; do not add API routes or authenticated cookies for demo coverage. Network assertions must specifically detect accidental `/api/data`, `/api/history`, or request-mutation traffic instead of treating a successful page load as proof of isolation.

### Required changes

1. Add browser cases for direct English and Russian demo URLs, browser-language detection from the public entry, and valid `lang` precedence over browser preference.
2. Assert each tab exposes its required localized heading/content and correct ARIA tab/panel relationship; drive the complete keyboard tab sequence including Home/End and verify URL state preserves `lang`.
3. Assert the sample-data notice, real parent sign-in href, task and reward request distinction, and earn/spend history distinction. Fail the test if the demo requests any API data or mutation endpoint.
4. At 320px, verify no horizontal overflow, a visible focusable demo entry, and 44px-or-larger tabs/links. Retain existing public OAuth fallback cases unchanged.
5. Run the focused unit, browser, lint, test, and production-build gates; report browser verification separately if the local Chromium environment blocks execution.

### Out of scope

- End-to-end testing against production, Telegram client/device validation, or changing unrelated public-page snapshots.

### Acceptance criteria

- Automated coverage fails if either guest locale loses required content, a tab becomes unreachable by keyboard, the explicit locale is discarded, the demo becomes API-backed, or the real OAuth entry changes.
- The focused browser run proves both demo desktop behavior and 320px geometry; `npm run build` separately proves static assets are included in the production build.
- Existing public access tests continue to pass without requiring an authenticated session for any demo assertion.

### Targeted validation

```bash
cd apps/web && npm run test -- tests/unit/publicDemoI18n.test.ts tests/unit/publicSiteAccess.test.ts && npm run test:e2e -- tests/e2e/workspace-entry.spec.ts && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/tests/e2e/workspace-entry.spec.ts apps/web/tests/unit/publicDemoI18n.test.ts
git commit -m "test(web): cover public parent demo"
```
