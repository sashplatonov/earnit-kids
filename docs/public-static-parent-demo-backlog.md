# Public Static Parent Demo - Implementation Backlog

## Goal

Let an unauthenticated visitor open a live, browser-based parent demo from the canonical static public site. The demo presents tasks, the rewards shop, history, and a child's submitted requests in the already-served English or Russian document locale, without accessing or changing family data.

## Current route and i18n baseline

- `apps/web/scripts/public-site/` is the source of public documents. `npm run generate:public` writes artifacts under `apps/web/static/public/`; the latter is an artifact namespace, not a visitor-facing URL prefix.
- Canonical English marketing paths are rooted (`/`, `/how.html`, `/tasks.html`, `/rewards.html`, `/parents.html`, `/faq.html`) and their Russian pairs are under `/ru/`. The public edge negotiates the first English document from `Accept-Language` and `?lang=` is a compatibility redirect, not client-side state.
- The static marketing rewards page is `/rewards.html` or `/ru/rewards.html`. The authenticated browser application is `/app` or `/{locale}/app`; its parent rewards/shop context is `/app?context=rewards` or `/{locale}/app?context=rewards`. `/workspace` is legacy and must not become a new demo or shop target.

## Architectural decisions

- Add the demo as one generated, canonical public document pair: `/demo.html` and `/ru/demo.html`. Register it in the shared `PUBLIC_PAGES` URL catalog so edge routing, canonical/hreflang metadata, sitemap generation, and language links all use the same paths.
- The edge and the served document path own the anonymous locale. Demo JavaScript derives display language only from `<html lang>` through the existing static `resolveDocumentLocale`; it must not inspect `navigator.languages`, write a locale cookie, or retain a `?lang` query.
- `apps/web/scripts/public-site/i18n.js` is the one static translation catalog. Add demo copy and localized fixture labels there, retain English/Russian structural parity, and copy the module into the generated artifact as the existing generator does. Do not create a parallel demo locale resolver or reuse authenticated Svelte i18n.
- Demo data is immutable, invented, and browser-local. It may have in-page tab state in `?tab=tasks|rewards|history|requests`, but makes no API call, session change, local-storage write, or request/reward mutation. It must visibly identify itself as a demo.
- The public demo is an informational route, not an alias for an authenticated page. Its real sign-in CTA keeps `/api/login-google/start?continue=%2Fapp`; a contextual shop CTA uses `/app?context=rewards`, not `/rewards.html`, `/workspace`, or `/public/...`.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | PSD-001 | P1 | - | Establishes the generated route, locale, metadata, and sitemap contract. |
| 2 | PSD-002 | P1 | PSD-001 | Builds the safe, localized static demo experience. |
| 3 | PSD-003 | P2 | PSD-001, PSD-002 | Makes the demo discoverable from all generated marketing pages. |
| 4 | PSD-004 | P2 | PSD-001, PSD-002, PSD-003 | Proves public routing, locale, isolation, accessibility, and compact layout. |

## PSD-001: Register the demo in the generated public URL and locale contract

**Status:** DONE

**Priority:** P1

**Depends on:** -

**Exact scope:**

Extend the source public-site catalog with a generated English/Russian demo document and its localized metadata. Keep its delivery path inside the current canonical public-site mechanism.

**Files:**

- Modify `apps/web/scripts/public-site/urls.js` and `apps/web/scripts/public-site/urls.d.ts`.
- Modify `apps/web/scripts/public-site/generate.mjs`.
- Modify `apps/web/scripts/public-site/template.html` only for a page-specific external demo module hook; do not weaken the CSP.
- Modify `apps/web/scripts/public-site/i18n.js` and `apps/web/scripts/public-site/i18n.d.ts`.
- Create `apps/web/scripts/public-site/pages/demo.html`.
- Modify `apps/web/src/routes/sitemap.xml/+server.ts`.
- Modify `docs/operations/web-miniapp-access.md`.
- Regenerate `apps/web/static/public/demo.html`, `apps/web/static/public/ru/demo.html`, and affected generated runtime/static-document artifacts.
- Modify `apps/web/tests/unit/publicSiteUrls.test.ts` and `apps/web/tests/unit/publicSiteI18n.test.ts`.
- Search anchors: `PUBLIC_PAGES` in `apps/web/scripts/public-site/urls.js`, `navigationFor` in `apps/web/scripts/public-site/generate.mjs`, and `PAGE_METADATA` in `apps/web/src/routes/sitemap.xml/+server.ts`.

**Goal:**

The demo is a real localized public document rather than a hand-maintained `/public/` file or an untracked query-driven view.

### Outcome

The source generator emits `/demo.html` and `/ru/demo.html`; each has the same strict static document contract as the existing marketing pages and appears consistently in the sitemap and operations URL map.

### Architectural decision

Add `demo` to `PUBLIC_PAGES` with the English canonical path `/demo.html` and generated artifact `demo.html`. The generator owns title, description, body root, navigation, canonical URL, reciprocal EN/RU/x-default alternates, and any conditional external module tag. Reuse `publicLanguageHref` and `resolvePublicOrigin`; do not introduce a second public-path list or hard-code a deployment host.

### Required changes

1. Add the `demo` page to the shared URL catalog and typed surface so `normalizePublicRequest`, `publicDocumentPath`, language links, edge `Accept-Language` negotiation, and generated artifacts recognize both canonical paths. Preserve existing protected `/app`, `/workspace`, `/telegram`, API, and `/public/` artifact exclusions.
2. Add matching English and Russian static-catalog entries for the demo title, description, body-root/loading or JavaScript-required copy, demo-notice copy, and every display key consumed by PSD-002. Retain recursive message-shape parity and document-language fallback behavior.
3. Extend the generator/template only as needed to render the demo root and load a same-origin external module on demo documents. All other pages must retain one `site.js` module, existing strict CSP, and unchanged document structure.
4. Add sitemap metadata for `demo`, include both locale URLs and reciprocal alternates, and update the public canonical URL table plus deployment curl loop to enumerate fourteen documents. Set the demo's priority/change frequency deliberately and keep `/public/` out of canonical/alternate/sitemap URLs.
5. Regenerate committed static output using the existing `APP_URL` build contract. Add unit coverage for path/artifact mapping, canonical language URLs, generated EN/RU metadata, dictionary parity, and sitemap entries.

### Out of scope

- Client-side locale switching, new languages, changing the family locale model, modifying SvelteKit authenticated i18n, or redesigning existing marketing copy.
- Adding an authenticated `/app` subroute or any backend/API contract.

### Acceptance criteria

- `/demo.html` is a canonical English document and `/ru/demo.html` is a canonical Russian document; a Russian-preferred first request to `/demo.html` gets the existing one-hop edge redirect to `/ru/demo.html`.
- Each generated demo document has the correct `<html lang>`, one absolute self-canonical URL, and absolute reciprocal `en`, `ru`, and `x-default` alternates; none points to `/public/` or a locale query.
- `/sitemap.xml` contains both demo locale URLs and the same alternate cluster as the generated document, while all existing twelve entries remain unchanged.
- All static public copy used by the demo is available in both locales with matching catalog shape, and static dynamic copy is selected from the served document language.

### Targeted validation

```bash
cd apps/web && APP_URL=https://example.test npm run generate:public && npm run test -- tests/unit/publicSiteUrls.test.ts tests/unit/publicSiteI18n.test.ts tests/unit/sitemap.test.ts
```

### Commit

```bash
git add apps/web/scripts/public-site/urls.js apps/web/scripts/public-site/urls.d.ts apps/web/scripts/public-site/generate.mjs apps/web/scripts/public-site/template.html apps/web/scripts/public-site/i18n.js apps/web/scripts/public-site/i18n.d.ts apps/web/scripts/public-site/pages/demo.html apps/web/src/routes/sitemap.xml/+server.ts docs/operations/web-miniapp-access.md apps/web/static/public apps/web/tests/unit/publicSiteUrls.test.ts apps/web/tests/unit/publicSiteI18n.test.ts
git commit -m "feat(web): add localized public demo route"
```

## PSD-002: Render the read-only parent demo from generated static fixtures

**Status:** DONE

**Priority:** P1

**Depends on:** PSD-001

**Exact scope:**

Implement the same-origin static modules and scoped styles that turn the generated demo document into an interactive, read-only parent workspace scenario.

**Files:**

- Create `apps/web/scripts/public-site/demo-data.js`.
- Create `apps/web/scripts/public-site/demo.js`.
- Modify `apps/web/scripts/public-site/generate.mjs` to copy these source modules into the public artifact directory.
- Modify `apps/web/static/public/styles.css`, the existing static stylesheet served by all generated public documents.
- Regenerate `apps/web/static/public/demo-data.js`, `apps/web/static/public/demo.js`, `apps/web/static/public/demo.html`, and `apps/web/static/public/ru/demo.html`.
- Create `apps/web/tests/unit/publicParentDemo.test.ts`.
- Search anchors: `resolveDocumentLocale` in `apps/web/scripts/public-site/i18n.js`, `TelegramParentShell` in `apps/web/src/lib/components/telegram/TelegramParentShell.svelte`, and `TelegramRequestRow` in `apps/web/src/lib/components/telegram/TelegramRequestRow.svelte`.

**Goal:**

Visitors can explore a credible parent workspace while unambiguously seeing sample data and without crossing the authenticated application boundary.

### Outcome

The generated demo displays a named sample child, balance, assigned tasks, rewards shop, history, and child-submitted task/reward requests through four keyboard-accessible tabs.

### Architectural decision

`demo-data.js` owns stable fictitious IDs, quantities, statuses, and timestamps; localized entity names and display strings are projected through the PSD-001 static catalog. `demo.js` owns DOM rendering and URL tab state, reads the locale only through `resolveDocumentLocale(document)`, and is loaded only by the demo document. It must not import `$lib`, call `fetch`, or share the authenticated app store.

### Required changes

1. Define one immutable fictional child scenario with multiple active tasks, several rewards, positive and negative history entries, and both task-completion and reward-purchase submissions. Include pending, approved, and rejected request states so each required parent view is demonstrable.
2. Render semantic demo markup with a skip link target, clear sample-data notice, child/balance summary, and a tablist. Provide `role="tab"`, `aria-selected`, `aria-controls`, and one labelled `role="tabpanel"`; support pointer activation plus Enter/Space, ArrowLeft/ArrowRight, Home, and End.
3. Make `tab=tasks|rewards|history|requests` the only in-demo state. Normalize invalid values to `tasks`, update the current history URL without removing its locale path, and never add `lang` to a canonical URL.
4. Show task title/group/repeat metadata/coins; shop reward title/group/price and available balance; timestamped earn/spend history with non-colour sign/label distinction; and child request type, amount, time, and state. Format dates/numbers using `Intl` for the served `en` or `ru` document language.
5. Provide localized empty/fallback rendering if a fixture section is absent, but expose no approve, reject, add, edit, buy, delete, retry, API, storage, or service-worker behavior. Include a real parent-sign-in link to `/api/login-google/start?continue=%2Fapp` and a contextual shop link to `/app?context=rewards`.
6. Add compact scoped CSS consistent with the current parent reward-list reference: one list surface per panel, no nested card stacks, `min-width:0` for long content, 44px tab/link targets, visible focus, and reduced-motion support. At 320px, the page must not overflow horizontally.
7. Add pure unit coverage for fixture completeness, tab normalization, locale-derived formatting, request/historical amount presentation, and a static-source guard against `$lib`, `fetch`, browser storage, and mutation endpoints.

### Out of scope

- Simulating approval/purchase, persisting anonymous changes, child switching, dashboard analytics, catalog administration, or an API loading/error state.
- Altering `WorkspaceRoleResolver`, `/app`, `/telegram`, authenticated stores, API routes, family data, or permission checks.

### Acceptance criteria

- Both canonical demo paths render the correct language and complete four-panel sample scenario without authentication or requests to `/api/`.
- Requests visibly distinguish task completion from shop purchase and include pending plus resolved statuses; History distinguishes positive earnings from negative reward spending without relying on colour alone.
- Tabs work with keyboard and pointer, preserve the canonical locale path when `tab` changes, and invalid/missing `tab` resolves deterministically to Tasks.
- The page tells visitors it is sample data, contains the real `/app` sign-in/shop destinations, and makes no mutation, API, storage, or service-worker side effect.
- At 320px, every interactive target is at least 44px, visible focus remains, and `document.documentElement.scrollWidth <= window.innerWidth`.

### Targeted validation

```bash
cd apps/web && npm run test -- tests/unit/publicParentDemo.test.ts && npm run lint && APP_URL=https://example.test npm run build
```

### Commit

```bash
git add apps/web/scripts/public-site/demo-data.js apps/web/scripts/public-site/demo.js apps/web/scripts/public-site/generate.mjs apps/web/static/public/styles.css apps/web/static/public/demo-data.js apps/web/static/public/demo.js apps/web/static/public/demo.html apps/web/static/public/ru/demo.html apps/web/tests/unit/publicParentDemo.test.ts
git commit -m "feat(web): render read-only public parent demo"
```

## PSD-003: Link the localized public site to the demo and shop context

**Status:** TODO

**Priority:** P2

**Depends on:** PSD-001, PSD-002

**Exact scope:**

Make the generated marketing pages discover the parent demo without hand-editing output files or confusing the marketing rewards page with the authenticated rewards shop.

**Files:**

- Modify `apps/web/scripts/public-site/template.html`.
- Modify `apps/web/scripts/public-site/generate.mjs`.
- Modify `apps/web/scripts/public-site/i18n.js` and `apps/web/scripts/public-site/i18n.d.ts`.
- Modify `apps/web/scripts/public-site/pages/index.html` and `apps/web/scripts/public-site/pages/rewards.html` only where a page-local demo/shop CTA is needed beyond shared navigation.
- Regenerate `apps/web/static/public/index.html`, `how.html`, `tasks.html`, `rewards.html`, `parents.html`, `faq.html`, their `ru/` counterparts, and the demo pair.
- Modify `apps/web/tests/unit/publicSiteI18n.test.ts`.
- Search anchors: `navigationFor` in `apps/web/scripts/public-site/generate.mjs` and `data-browser-workspace-link` in `apps/web/scripts/public-site/template.html`.

**Goal:**

Every public visitor can find the correct localized demo, while labels and destinations clearly distinguish demo, public rewards explanation, and the authenticated rewards shop.

### Outcome

Generated pages include a localized “View parent demo” link to the matching canonical demo path. Where a user is sent to the actual parent rewards interface, the link is explicitly labelled as sign-in/open shop and uses `/app?context=rewards` in English or `/ru/app?context=rewards` in Russian after the existing authentication boundary.

### Architectural decision

Generate all demo links from `PUBLIC_PAGES`/`publicLanguageHref` rather than writing `/public/demo.html`, a `?lang` value, or duplicated locale mapping in `site.js`. Keep Google CTA enhancement exclusively on `data-browser-workspace-link`; the demo link is an ordinary no-JavaScript anchor and does not invoke OAuth or API requests itself.

### Required changes

1. Add a shared, visible demo link in the generated public header or equivalent consistent page chrome. It must resolve to `/demo.html` on English pages and `/ru/demo.html` on Russian pages before JavaScript runs, and retain the current document's query/hash only where the URL helper permits it.
2. Add matching English/Russian labels and accessible names. Preserve the existing EN/RU language switcher, Telegram CTA, and real Google sign-in CTA unchanged.
3. On the marketing rewards page, distinguish copy/link targets: `/rewards.html` remains explanatory content, the demo link remains `/demo.html` or `/ru/demo.html`, and the contextual authenticated shop link is `/app?context=rewards` on English documents or `/ru/app?context=rewards` on Russian documents. Do not point any CTA to legacy `/workspace` or artifact `/public/` URLs.
4. Regenerate artifacts and add focused source/output assertions that every English/Russian public document exposes the correctly localized demo target, and that the real sign-in CTA remains `/api/login-google/start?continue=%2Fapp`.

### Out of scope

- Rewriting existing public-page body copy, creating a public checkout, adding login interception to the demo link, or changing OAuth continuation validation.

### Acceptance criteria

- Each generated English document has a keyboard-accessible demo link to `/demo.html`; each Russian document has the equivalent `/ru/demo.html` link, including with JavaScript disabled.
- At 320px, the link has a 44px target, visible focus, and does not cause horizontal overflow; it coexists with language, Telegram, and real sign-in controls.
- The marketing rewards page never labels `/rewards.html` as the actual shop, and any authentic shop CTA uses the `/app?context=rewards` contract rather than legacy or artifact paths.
- Clicking a demo link causes neither OAuth startup nor API traffic; the existing sign-in link keeps its current same-origin OAuth/fallback behavior.

### Targeted validation

```bash
cd apps/web && APP_URL=https://example.test npm run generate:public && npm run test -- tests/unit/publicSiteI18n.test.ts tests/unit/publicSiteAccess.test.ts && npm run build
```

### Commit

```bash
git add apps/web/scripts/public-site/template.html apps/web/scripts/public-site/generate.mjs apps/web/scripts/public-site/i18n.js apps/web/scripts/public-site/i18n.d.ts apps/web/scripts/public-site/pages/index.html apps/web/scripts/public-site/pages/rewards.html apps/web/static/public apps/web/tests/unit/publicSiteI18n.test.ts
git commit -m "feat(web): link public site to localized parent demo"
```

## PSD-004: Prove the generated demo contract in browser coverage

**Status:** TODO

**Priority:** P2

**Depends on:** PSD-001, PSD-002, PSD-003

**Exact scope:**

Extend focused unit and browser coverage for canonical public demo delivery, document-owned locale, public isolation, the authenticated shop destination, and mobile accessibility.

**Files:**

- Modify `apps/web/tests/e2e/workspace-entry.spec.ts`.
- Modify `apps/web/tests/unit/publicSiteUrls.test.ts`.
- Modify `apps/web/tests/unit/publicSiteI18n.test.ts`.
- Modify `apps/web/tests/unit/publicParentDemo.test.ts` if an uncovered pure regression is found.
- Search anchors: `publicPages` and `expectRawPublicDocument` in `apps/web/tests/e2e/workspace-entry.spec.ts`.

**Goal:**

Tests make the new demo a first-class member of the public URL contract and catch regressions that would expose stale `/public/`, browser-chosen locale, API-backed demo data, or an incorrect shop path.

### Outcome

Focused regression coverage proves direct and discovered EN/RU demo access, edge locale negotiation, metadata/sitemap parity, tab semantics, static isolation, real-shop targeting, and 320px geometry.

### Architectural decision

Reuse the existing preview E2E server and its public-document helpers. Keep demo tests unauthenticated and assert that no `/api/data`, `/api/history`, request mutation, or OAuth endpoint is contacted on demo navigation; do not add an E2E fixture session merely to prove a static page.

### Required changes

1. Extend the canonical public-page matrix, raw JavaScript-disabled metadata checks, Russian browser-language redirect test, sitemap assertions, and compact-link loops to include `demo.html` and its Russian pair.
2. Add direct-demo browser cases for both document locales, localized sample copy, the demo notice, four required tabs/panels, ARIA relationships, keyboard navigation including Home/End, `tab` URL state, and invalid-tab fallback.
3. Intercept/record network activity and fail a demo test when it requests `/api/data`, `/api/history`, `/api/requests`, `/api/shop`, or any mutation endpoint. Separately assert that the real sign-in and shop links retain `/api/login-google/start?continue=%2Fapp` and `/app?context=rewards`.
4. At 320px, assert no horizontal overflow, at least 44px demo/tab/link targets, and visible keyboard focus for the demo entry and tablist. Keep existing OAuth and language-switching coverage intact.
5. Run unit, E2E, lint, and production build gates. Report local browser/preview outcomes separately from CI, deployed edge, crawler/indexing, Telegram-client, and physical-device proof.

### Out of scope

- Production SEO submission/verification, Search Console indexing proof, official Telegram-client testing, or PWA/device migration validation.

### Acceptance criteria

- Automated tests fail if a demo route leaves the canonical EN/RU URL set, has incomplete metadata/sitemap alternates, chooses copy from browser preference rather than its document path, or reintroduces `/public/` as a visitor-facing URL.
- Automated tests fail if the demo loses any required panel/keyboard semantics, makes API or mutation traffic, or points an authenticated shop CTA at `/rewards.html` or `/workspace`.
- The focused E2E run proves desktop interactions and 320px geometry; the production build separately proves generated assets are shipped. Neither result is reported as deployment or indexation proof.

### Targeted validation

```bash
cd apps/web && APP_URL=https://example.test npm run generate:public && npm run test -- tests/unit/publicSiteUrls.test.ts tests/unit/publicSiteI18n.test.ts tests/unit/publicParentDemo.test.ts tests/unit/sitemap.test.ts && npm run test:e2e -- tests/e2e/workspace-entry.spec.ts && npm run lint && APP_URL=https://example.test npm run build
```

### Commit

```bash
git add apps/web/tests/e2e/workspace-entry.spec.ts apps/web/tests/unit/publicSiteUrls.test.ts apps/web/tests/unit/publicSiteI18n.test.ts apps/web/tests/unit/publicParentDemo.test.ts
git commit -m "test(web): cover generated public parent demo"
```
