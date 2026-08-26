# Public Static Site Root and i18n - Implementation Backlog

## Goal

Serve the existing static EarnIt Kids marketing site from the product root instead of `/public/*.html`, localize every visitor-facing public-site string in English and Russian, provide an accessible language choice, and remove the obsolete Mini App screenshot carousel together with its four source images.

## Architectural decisions

- The public marketing surface remains generated static HTML: edit `apps/web/scripts/public-site/` and regenerate the tracked deployable artifacts in `apps/web/static/public/`; do not replace it with an authenticated Svelte page or import the application i18n store into static runtime code.
- Canonical public URLs are `/` for the home page and `/how.html`, `/tasks.html`, `/rewards.html`, `/parents.html`, and `/faq.html` for the remaining pages. The edge server internally maps only those requests to their files below `/public/`, keeping the visible canonical URL stable; `/public/` remains the asset namespace, not a second marketing entry point. `/api`, `/workspace`, `/telegram`, `/login-child`, health checks, and a root request carrying `tgWebAppStartParam` must retain their current ownership.
- The static public i18n module is the sole owner of `en`/`ru` message dictionaries, supported-language resolution, and public-page URL creation. A valid `?lang=en|ru` selection takes precedence, is preserved in public navigation, and is what the language control writes; otherwise resolve Russian only for a Russian browser preference and use English as the fallback. Do not write the authenticated family `locale` cookie or alter family locale state.
- Static source markup must expose stable translation keys and semantic placeholders; runtime code may update public copy and document metadata only. Do not duplicate the same public-site dictionary in each generated page, in the Svelte i18n catalog, or in `site.js`.
- Remove only the carousel and the four files it references: `parent-home.png`, `parent-tasks.png`, `parent-family.png`, and `child-today.png`. Keep unrelated capture outputs and the screenshot-capture tool unless a remaining reference proves they are part of this deleted component.
- Preserve the existing CSP, Google OAuth native fallback, Telegram CTA configuration, responsive 44px controls, and no-legacy-`/login` rule. Local checks prove source/build/browser behavior only; deployment and Telegram-device confirmation remain separate.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | PSS-001 | P1 | - | Removes the obsolete UI and assets before localization contracts include them. |
| 2 | PSS-002 | P1 | PSS-001 | Establishes the canonical root URL and preserves protected route ownership. |
| 3 | PSS-003 | P1 | PSS-001, PSS-002 | Adds the one static translation and language-selection mechanism over the final page structure. |
| 4 | PSS-004 | P2 | PSS-001, PSS-002, PSS-003 | Locks down URL, locale, access, responsive, and generation regressions. |
| 5 | PSS-005 | P2 | PSS-004 | Updates the operations handoff after the public behavior is proven. |

## PSS-001: Remove the Mini App screenshot carousel and its owned assets

**Status:** DONE
**Priority:** P1
**Depends on:** -

**Exact scope:**

Delete the `Вот как это выглядит` section from the public “How it works” source and generated output, then remove its interaction and presentation code plus exactly the images owned by that section.

**Files:**

- Modify `apps/web/scripts/public-site/pages/how.html`.
- Modify `apps/web/scripts/public-site/generate.mjs` only if generation needs an explicit stale-artifact safeguard; otherwise leave it unchanged.
- Modify `apps/web/static/public/site.js`.
- Modify `apps/web/static/public/styles.css`.
- Regenerate `apps/web/static/public/how.html` using `npm run generate:public`.
- Delete `apps/web/static/public/assets/screenshots/parent-home.png`.
- Delete `apps/web/static/public/assets/screenshots/parent-tasks.png`.
- Delete `apps/web/static/public/assets/screenshots/parent-family.png`.
- Delete `apps/web/static/public/assets/screenshots/child-today.png`.
- Search anchor: `data-carousel` in `apps/web/static/public/site.js` and `miniapp-preview-section` in `apps/web/static/public/styles.css`.

**Goal:**

The how-it-works page ends after its explanatory content and contains neither the removed heading/intro nor a carousel control, while the delivered static tree contains no image that component used.

### Outcome

Visitors no longer see reference Mini App screens or carousel controls; the runtime no longer initializes removed DOM and no request can target those deleted image URLs from public-site markup.

### Architectural decision

The generated source page owns the section’s existence. `site.js` and `styles.css` must remove only code that exclusively supports `[data-carousel]`; retain shared keyboard focus and reduced-motion rules.

### Required changes

1. Remove the complete preview section, including its heading, explanatory text, figures, arrows, dots, live status, and all four image references from the source fragment.
2. Remove the carousel initializer, touch/keyboard handlers, and carousel-only CSS selectors without deleting shared `sr-only`, focus, or responsive rules used elsewhere.
3. Run the generator and inspect the generated `how.html` to ensure the removed markup is not reintroduced by the tracked artifact.
4. Delete only the four identified assets after confirming no non-generated source or test refers to them; retain `miniapp-*.png`, `child-today-debug.png`, and `performance-report.json` unless independently found to be unused and explicitly scheduled separately.

### Out of scope

- Redesigning the remaining how-it-works page, replacing the carousel with another visual, or deleting the screenshot capture script.
- Translating the remaining content; that belongs to PSS-003.

### Acceptance criteria

- The generated `/how.html` content contains neither `Вот как это выглядит` nor `Это референсные экраны текущего Mini App`, `[data-carousel]`, carousel buttons, dots, or screenshot image references.
- `site.js` has no carousel event registration or inline `transform` manipulation, and public styles have no selectors that exist only to render the deleted carousel.
- The four deleted paths are absent from the repository; the remaining screenshot assets and capture script are unchanged.
- At a 320px viewport, the modified how-it-works page remains horizontally scroll-free and its remaining links retain visible focus.

### Targeted validation

```bash
cd apps/web && npm run generate:public && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/scripts/public-site/pages/how.html apps/web/static/public/how.html apps/web/static/public/site.js apps/web/static/public/styles.css apps/web/static/public/assets/screenshots/parent-home.png apps/web/static/public/assets/screenshots/parent-tasks.png apps/web/static/public/assets/screenshots/parent-family.png apps/web/static/public/assets/screenshots/child-today.png
git commit -m "refactor(web): remove public Mini App carousel"
```

## PSS-002: Serve canonical public pages at the root URL space

**Status:** DONE
**Priority:** P1
**Depends on:** PSS-001

**Exact scope:**

Expose the generated static marketing documents through the canonical root URL space while retaining `/public/` exclusively for static assets and preserving all application, OAuth, and Telegram entry contracts.

**Files:**

- Modify `apps/web/scripts/preview.mjs`.
- Modify `apps/web/scripts/public-site/template.html`.
- Modify `apps/web/scripts/public-site/generate.mjs`.
- Modify all six source fragments in `apps/web/scripts/public-site/pages/` where a public-page link is embedded.
- Regenerate `apps/web/static/public/index.html`, `how.html`, `tasks.html`, `rewards.html`, `parents.html`, and `faq.html`.
- Modify `apps/web/static/public/site.js` and `apps/web/static/public/site.d.ts`.
- Modify `apps/web/src/routes/+page.server.ts` and `apps/web/tests/unit/rootPageLoad.test.ts` only as required to prevent the Svelte root redirect from defeating the edge mapping.
- Modify `apps/web/src/routes/workspace/+page.server.ts`, `apps/web/src/lib/features/workspace/WorkspaceSessionActions.svelte`, `apps/web/src/routes/select-family/+page.svelte`, and `apps/web/src/routes/telegram/+page.svelte` to point return/public links to `/`.
- Modify `apps/web/src/routes/sitemap.xml/+server.ts`.
- Search anchor: `pathname.startsWith('/public/')` and `handler(req, res)` in `apps/web/scripts/preview.mjs`.

**Goal:**

Opening `https://earnit-kids.igo.mywire.org/` renders the public static home without navigating the browser to `/public/index.html`, and every public navigation destination uses the root URL space.

### Outcome

The static site is publicly accessible at `/`, `/how.html`, `/tasks.html`, `/rewards.html`, `/parents.html`, and `/faq.html`; its CSS, scripts, images, config endpoint, OAuth fallback, sitemap, and internal navigation resolve correctly from those visible URLs.

### Architectural decision

`preview.mjs` is the production edge owner of the URL-to-static-file mapping. It must rewrite internally before handing the request to the Svelte handler so the address bar remains canonical; it must not broadly rewrite unknown paths or proxy/application routes. Generated marketing markup must use root-canonical document links and `/public/...` asset/config URLs, rather than relying on path-relative URLs that break at `/`.

### Required changes

1. Define one explicit mapping for the six canonical marketing paths to the existing generated `/public/*.html` files, preserving query strings where relevant. Ensure the mapping is evaluated after health/API/login-child/WebSocket exclusions and before `handler(req, res)`.
2. Keep the root `tgWebAppStartParam` handoff to `/ru/telegram` intact. A normal browser root request must no longer receive a redirect to `/public/index.html`; adjust the root load contract only as needed for that separation.
3. Update the generator/template/navigation and page-local links so public documents link to the canonical root URLs, while stylesheets, JavaScript, icons, screenshots, and `/public/config.js` use stable absolute asset URLs.
4. Change static fallback constants and application links that intentionally return visitors to the marketing home from `/public/index.html` to `/`; retain the real Google continuation `/api/login-google/start?continue=%2Fworkspace` and do not add `/login` compatibility routes.
5. Make the sitemap list only canonical root public URLs. Regenerate all committed static artifacts from the changed sources; do not hand-edit generated HTML.
6. Extend focused unit coverage for the root/TG split and public access fallback to assert canonical `/` behavior.

### Out of scope

- Pretty URL migration beyond the existing `.html` secondary page names, redirects for legacy `/public/*.html` links, reverse-proxy/DNS changes, or changes to authenticated workspace/Telegram page layouts.
- Language selection and translating copy; those belong to PSS-003.

### Acceptance criteria

- A normal `GET /` returns the static public home content without a client-visible redirect to `/public/index.html`; `/?tgWebAppStartParam=...` still reaches the Russian Telegram entry with the full query preserved.
- `/how.html`, `/tasks.html`, `/rewards.html`, `/parents.html`, and `/faq.html` serve their matching generated pages and their header/footer/page links remain inside the canonical public URL set.
- No canonical page has broken styles, script, icon, image, or `/public/config.js` requests due to a relative URL; `/api`, `/workspace`, `/telegram`, `/login-child`, `/healthz`, and WebSocket routing keep their present behavior.
- Public OAuth failure still leaves a clickable same-origin Google-start anchor, workspace unauthenticated redirects and sign-out return to `/`, and `/login`, `/ru/login`, and `/login.html` remain 404.
- The sitemap’s highest-priority URL is the root URL and it contains no `/public/` marketing page URLs.

### Targeted validation

```bash
cd apps/web && npm run generate:public && npm run test -- tests/unit/rootPageLoad.test.ts tests/unit/publicSiteAccess.test.ts && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/scripts/preview.mjs apps/web/scripts/public-site/template.html apps/web/scripts/public-site/generate.mjs apps/web/scripts/public-site/pages apps/web/static/public apps/web/src/routes/+page.server.ts apps/web/src/routes/workspace/+page.server.ts apps/web/src/lib/features/workspace/WorkspaceSessionActions.svelte apps/web/src/routes/select-family/+page.svelte apps/web/src/routes/telegram/+page.svelte apps/web/src/routes/sitemap.xml/+server.ts apps/web/tests/unit/rootPageLoad.test.ts apps/web/tests/unit/publicSiteAccess.test.ts
git commit -m "feat(web): serve public site from root URLs"
```

## PSS-003: Localize the complete static public site and add language selection

**Status:** DONE
**Priority:** P1
**Depends on:** PSS-001, PSS-002

**Exact scope:**

Add an isolated English/Russian localization layer to the generated public site, translate all public marketing copy and metadata, and provide a compact accessible language control whose selection survives public-page navigation.

**Files:**

- Create `apps/web/scripts/public-site/i18n.js`.
- Modify `apps/web/scripts/public-site/template.html`.
- Modify `apps/web/scripts/public-site/generate.mjs`.
- Modify all six files in `apps/web/scripts/public-site/pages/`.
- Modify `apps/web/static/public/site.js`, `apps/web/static/public/site.d.ts`, and `apps/web/static/public/styles.css`.
- Regenerate `apps/web/static/public/index.html`, `how.html`, `tasks.html`, `rewards.html`, `parents.html`, and `faq.html`.
- Create `apps/web/tests/unit/publicSiteI18n.test.ts`.
- Search anchor: `LOCALES`, `DEFAULT_LOCALE`, and `normalizeLocale` in `apps/web/src/lib/i18n/config.ts` for the supported-language contract to mirror without importing Svelte code.

**Goal:**

Every public-site text, accessible label, title, description, and status is available in English and Russian; the site selects Russian only for a Russian browser preference and otherwise opens in English, with an explicit user choice always winning.

### Outcome

The root public page and every secondary public page contain a keyboard-operable EN/RU selector. A valid `?lang=en|ru` renders the selected language and is carried to public navigation; without it, a Russian browser sees Russian and all other/missing browser preferences use English.

### Architectural decision

`i18n.js` is a static, DOM-safe module with one equal-key dictionary per locale and pure functions for locale normalization/detection, URL construction, and lookup. The generator produces stable `data-i18n`/attribute keys and English-safe initial markup; `site.js` composes that module with existing CTA behavior rather than adding independent locale rules or localized string literals.

### Required changes

1. Inventory and key every visitor-facing string in the shared template and all six source fragments: navigation, CTA labels, footer, headings, paragraphs, cards, FAQ questions/answers, visible/ARIA labels, skip link, document `lang`, title, description, and public access error/status strings. Translate the complete inventory to idiomatic English and Russian.
2. Implement pure locale helpers that accept a valid `lang` query value first, otherwise examine `navigator.languages` in order and then `navigator.language`; normalize supported Russian variants to `ru`, supported English variants to `en`, and return `en` for missing/unsupported input. Do not access cookies, the Svelte store, family APIs, or `localStorage`.
3. Render the resolved dictionary into semantic text and attributes without injecting untrusted HTML. Update `<html lang>`, `<title>`, and description after resolution, and leave an understandable English initial document if JavaScript is unavailable.
4. Add an EN/RU button group in the shared header with a localized group name, `aria-pressed` state, visible focus, and at least 44 by 44px touch targets. Choosing a language updates only the validated `lang` query on the current canonical public URL, then all public page/navigation links preserve it; external Telegram and OAuth URLs must not gain `lang`.
5. Make existing static access messaging and unavailable-Mini-App labels use the same public dictionary. Retain native anchors when JavaScript/OAuth initialization is unavailable and preserve the strict static CSP (external module scripts only, no inline handlers or inline style attributes).
6. Generate and commit the deployable HTML from the sources, with no direct edits to generated files.
7. Unit-test browser-language resolution, explicit-query precedence, unsupported fallback to English, translated-key parity, safe lookup fallback, public link language preservation, and the rule that OAuth/Telegram targets are not rewritten.

### Out of scope

- Localizing authenticated Svelte workspace/Telegram views, adding locales beyond `en` and `ru`, persisting a preference across devices, browser locale detection on the server, or SEO hreflang/canonical redesign.
- Altering the Google OAuth API contract, static demo backlog work, or product/domain translations.

### Acceptance criteria

- With `?lang=ru`, every public page displays Russian copy and `lang="ru"`; with `?lang=en`, every page displays English copy and `lang="en"`, regardless of browser preference.
- Without `lang`, a browser whose first supported preference is Russian renders Russian; an English, unsupported, empty, or unavailable browser preference renders English.
- The language control is available on all six pages, supports mouse, keyboard, and 44px touch targets, exposes a localized accessible name and current state, has visible focus, and causes no horizontal overflow at 320px.
- Every header/nav/page link stays in the canonical public URL set and carries the selected `lang`; the configured Telegram URL and `/api/login-google/start?continue=%2Fworkspace` remain unchanged.
- English and Russian dictionaries have identical message keys; generated public files contain no unresolved keys, Russian-only default markup, inline script, or inline event handler.

### Targeted validation

```bash
cd apps/web && npm run generate:public && npm run test -- tests/unit/publicSiteI18n.test.ts tests/unit/publicSiteAccess.test.ts && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/scripts/public-site/i18n.js apps/web/scripts/public-site/template.html apps/web/scripts/public-site/generate.mjs apps/web/scripts/public-site/pages apps/web/static/public/index.html apps/web/static/public/how.html apps/web/static/public/tasks.html apps/web/static/public/rewards.html apps/web/static/public/parents.html apps/web/static/public/faq.html apps/web/static/public/site.js apps/web/static/public/site.d.ts apps/web/static/public/styles.css apps/web/tests/unit/publicSiteI18n.test.ts apps/web/tests/unit/publicSiteAccess.test.ts
git commit -m "feat(web): localize static public site"
```

## PSS-004: Cover canonical public entry, locale, and removal regressions in the browser

**Status:** DONE
**Priority:** P2
**Depends on:** PSS-001, PSS-002, PSS-003

**Exact scope:**

Update focused public-site E2E coverage to exercise canonical root URLs, locale detection and selection, preserved access controls, and compact responsive behavior.

**Files:**

- Modify `apps/web/tests/e2e/workspace-entry.spec.ts`.
- Modify `apps/web/tests/e2e/workspace-access.spec.ts`.
- Modify `apps/web/tests/e2e/telegram-auth.spec.ts` only for explicit public-home link assertions.
- Modify `apps/web/tests/e2e/helpers.ts` only if its cookie-bootstrap landing URL must become `/`.
- Modify `apps/web/tests/unit/publicSiteAccess.test.ts` and `apps/web/tests/unit/rootPageLoad.test.ts` only for missing root-contract regressions.
- Search anchor: `public pages keep both access choices usable at the compact mobile width` in `apps/web/tests/e2e/workspace-entry.spec.ts`.

**Goal:**

Automated checks fail if the public site leaves the root URL space, loses English fallback/Russian detection, breaks CTA fallbacks, reintroduces the carousel, or becomes unusable on a compact viewport.

### Outcome

Browser coverage distinguishes static public behavior from authenticated workspace and Telegram behavior, while proving canonical navigation and language behavior under the production preview server.

### Architectural decision

Use the existing Playwright production-preview setup and its `/public/config.js` request stub. Test visible canonical document URLs and static assets separately: the page URL must be root-canonical while config/assets can still be served from `/public/`.

### Required changes

1. Replace public-page fixtures and expectations that use `/public/index.html` with the canonical root set, while retaining only intentional `/public/config.js` interception.
2. Assert normal root content/headers with no `/public/index.html` navigation, the secondary canonical pages, and the preserved `tgWebAppStartParam` handoff.
3. Cover `?lang=en`, `?lang=ru`, Russian browser preference, unsupported/missing browser preference, language-control interaction, and language preservation across public navigation. Verify the document language, representative translated content, button state, and metadata.
4. Preserve and rerun public Telegram/OAuth cases: configured CTA replacement, same-origin successful Google startup, unavailable-start native anchor fallback, and no-JavaScript anchor behavior. Update workspace sign-out, unauthenticated continuation, select-family, and Telegram unlinked-account assertions to return to `/`.
5. Keep `/how.html` free of carousel role/controls/reference heading.
6. At 320px assert no horizontal document overflow, visible focusable language/public access controls, and 44px minimum targets. Report a local Chromium permission/startup failure as an infrastructure limitation rather than browser proof.

### Out of scope

- Real Google sign-in, deployed DNS/reverse-proxy validation, Telegram client/device validation, or expanding broad visual-regression snapshots.

### Acceptance criteria

- The focused E2E suite fails if `/` redirects to `/public/index.html`, a public navigation URL leaves the canonical set, or a root resource is unresolved.
- It fails if explicit language selection is ignored, unsupported browser language does not choose English, a selected language is lost on public navigation, or the static OAuth/Telegram link contracts change.
- It fails if canonical public pages overflow at a 320px viewport.
- Existing authenticated workspace and Telegram entry tests keep their coverage without treating a public static page as an authenticated route.

### Targeted validation

```bash
cd apps/web && npm run test -- tests/unit/rootPageLoad.test.ts tests/unit/publicSiteI18n.test.ts tests/unit/publicSiteAccess.test.ts && npm run test:e2e -- tests/e2e/workspace-entry.spec.ts tests/e2e/workspace-access.spec.ts tests/e2e/telegram-auth.spec.ts && npm run lint && npm run test && npm run build
```

### Commit

```bash
git add apps/web/tests/e2e/workspace-entry.spec.ts apps/web/tests/e2e/workspace-access.spec.ts apps/web/tests/e2e/telegram-auth.spec.ts apps/web/tests/e2e/helpers.ts apps/web/tests/unit/rootPageLoad.test.ts apps/web/tests/unit/publicSiteAccess.test.ts
git commit -m "test(web): cover rooted localized public site"
```

## PSS-005: Update the public-site release runbook

**Status:** TODO
**Priority:** P2
**Depends on:** PSS-004

**Exact scope:**

Replace outdated `/public/*.html` operational instructions with the canonical public URL set and add clear deployment/browser verification boundaries for the static language selector.

**Files:**

- Modify `docs/operations/web-miniapp-access.md`.
- Search anchor: `Open every public page at` in `docs/operations/web-miniapp-access.md`.

**Goal:**

The release checklist tells an operator exactly which public URLs to open and what must be confirmed after deployment, without representing local automated checks as production/Telegram-client proof.

### Outcome

Operators validate the root marketing site, all canonical secondary pages, English fallback/Russian browser detection, explicit language selection, public access CTA fallback, and the absence of the former screenshot carousel.

### Architectural decision

The runbook documents observable deployed behavior only. It must not prescribe editing generated files or claim that CI/browser automation proves the live reverse proxy or Telegram client.

### Required changes

1. Replace the legacy public URL list with `/`, `/how.html`, `/tasks.html`, `/rewards.html`, `/parents.html`, and `/faq.html`.
2. Add deployment checks for a default-English unsupported browser context, Russian browser preference, explicit EN/RU selection and persisted public navigation, static assets/config loading, compact layout, and both public access choices.
3. State that root Telegram launch parameters still enter the Mini App; distinguish successful local tests from live OAuth, reverse-proxy, and physical Telegram-device confirmation.
4. State that the Mini App reference carousel/screenshots are intentionally absent, so their reappearance is a deployment regression rather than an expected visual variation.

### Out of scope

- Changing deployment infrastructure, adding monitoring, modifying application code, or documenting unrelated authenticated workflow operations.

### Acceptance criteria

- The runbook names only canonical public entry URLs and no longer tells operators to open `/public/*.html` marketing pages.
- It describes observable language, CTA, mobile, and Telegram-root handoff checks and clearly separates local/CI from deployed/device evidence.

### Targeted validation

```bash
git diff --check -- docs/operations/web-miniapp-access.md
```

### Commit

```bash
git add docs/operations/web-miniapp-access.md
git commit -m "docs(web): document rooted public site checks"
```
