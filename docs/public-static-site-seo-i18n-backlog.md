# Public Static Site SEO and i18n - Implementation Backlog

## Goal

Make the public EarnIt Kids marketing site reliably discoverable as complete
English and Russian documents. Search engines must receive stable language URLs,
server-delivered localized HTML and metadata, explicit language relationships,
and a sitemap/robots contract that is valid in production. Move the
authenticated browser workspace from the technical `/workspace` path to `/app`
without breaking OAuth, invitations, push, or existing deep links.

## Architectural decisions

- English remains at the established canonical URLs: `/`, `/how.html`,
  `/tasks.html`, `/rewards.html`, `/parents.html`, and `/faq.html`. Russian uses
  the matching `/ru/` URL space: `/ru/`, `/ru/how.html`, `/ru/tasks.html`,
  `/ru/rewards.html`, `/ru/parents.html`, and `/ru/faq.html`.
- `apps/web/scripts/public-site/` remains the only public-document generator.
  It must produce complete localized HTML below `apps/web/static/public/`,
  including `static/public/ru/`; SvelteKit must not render a duplicate marketing
  surface and the authenticated application catalog remains separate.
- Locale is encoded in the public document path, not selected by client-only
  DOM replacement. Existing `?lang=en|ru` links are a compatibility input only:
  the edge redirects them to their language path while preserving unrelated
  query parameters and fragments where HTTP permits. Browser preference may
  choose that same path only through an edge redirect; it must never determine
  HTML after the response has been sent.
- Each localized document has a self-referencing absolute canonical URL and a
  complete `hreflang` cluster for `en`, `ru`, and `x-default` (English). The
  sitemap is the single discovery source for these canonical localized URLs;
  `/public/` stays an asset namespace and is never a marketing canonical.
- Public copy, titles, descriptions, ARIA labels, and navigation labels have
  one locale-keyed static source. Generator templates may contain structure and
  semantic placeholders but may not maintain a second page-level translation
  catalog. Runtime `site.js` retains CTA/OAuth enhancement only; it must not
  alter locale, body copy, `<html lang>`, title, description, canonical, or
  alternate links.
- Preserve the root Telegram launch handoff, protected application routes,
  strict static CSP, native OAuth fallback, and the rule that external Telegram
  and Google OAuth targets never acquire locale parameters. Do not reintroduce
  `/login` compatibility routes.
- `/app` and `/{locale}/app` replace `/workspace` and `/{locale}/workspace` as
  the authenticated browser application route segment. The old workspace URLs
  remain bounded compatibility redirects that preserve query strings; they must
  not render a second application page, enter sitemap/SEO metadata, or become
  a second service-worker scope.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | PSS-SEO-001 | P1 | - | Establishes one crawlable URL contract before generated output changes. |
| 2 | PSS-APP-001 | P1 | PSS-SEO-001 | Replaces the browser route while keeping old deep links safe. |
| 3 | PSS-APP-002 | P1 | PSS-APP-001 | Moves every authenticated entry and OAuth continuation to `/app`. |
| 4 | PSS-APP-003 | P1 | PSS-APP-001 | Migrates PWA scope and notification fallbacks without cached-data regression. |
| 5 | PSS-SEO-002 | P1 | PSS-SEO-001 | Produces complete server-visible language documents and metadata. |
| 6 | PSS-SEO-003 | P1 | PSS-SEO-001, PSS-SEO-002 | Publishes canonical language URLs through sitemap and production robots. |
| 7 | PSS-APP-004 | P2 | PSS-APP-001, PSS-APP-002, PSS-APP-003 | Proves redirects, authenticated entries, and PWA migration. |
| 8 | PSS-SEO-004 | P2 | PSS-SEO-001, PSS-SEO-002, PSS-SEO-003 | Proves crawler-visible output and browser behavior under the preview edge. |
| 9 | PSS-SEO-005 | P2 | PSS-SEO-004, PSS-APP-004 | Records live indexing and route-migration verification. |

## PSS-SEO-001: Define public language paths and edge canonicalization

**Status:** DONE
**Priority:** P1
**Depends on:** -

**Exact scope:**

Replace query-string locale ownership for public marketing pages with one
language-path resolver and map both English and Russian canonical documents at
the preview edge. Maintain a bounded compatibility redirect for existing
`?lang=` public links.

**Files:**

- Create `apps/web/scripts/public-site/urls.js` and
  `apps/web/scripts/public-site/urls.d.ts`.
- Modify `apps/web/scripts/preview.mjs`.
- Modify `apps/web/static/public/site.js` and `apps/web/static/public/site.d.ts`.
- Modify `apps/web/tests/unit/publicSiteI18n.test.ts`.
- Modify `apps/web/tests/unit/previewProxyContext.test.ts` or create
  `apps/web/tests/unit/publicSiteUrls.test.ts` for the extracted pure resolver.
- Search anchor: `PUBLIC_DOCUMENTS` and `publicDocumentPath` in
  `apps/web/scripts/preview.mjs`.

**Goal:**

Each public language has exactly one visible path, and legacy `lang` query
URLs cannot become competing indexable documents.

### Outcome

`/how.html` serves English and `/ru/how.html` serves Russian without changing
the address bar. A request such as `/how.html?lang=ru&utm_source=mail` receives
a permanent redirect to `/ru/how.html?utm_source=mail`; `lang` is removed.

### Architectural decision

The new pure URL module owns supported public paths, language parsing, path
creation, and compatibility normalization. `preview.mjs` consumes that module
for request routing and redirects; the static runtime consumes it only to form
same-origin language-switcher destinations. Do not duplicate path maps in the
generator, sitemap, edge server, and tests.

### Required changes

1. Define the twelve canonical public document paths and their mapping to the
   English and Russian generated artifacts. Reject unknown paths rather than
   broadly rewriting `/ru/*`, `/api/*`, `/app`, `/workspace`, `/telegram`,
   `/login-child`, health checks, or WebSocket requests.
2. For canonical public documents, resolve a valid `lang` query to the matching
   path using a permanent redirect, remove only `lang`, and retain all unrelated
   query pairs. Treat unsupported `lang` as non-locale input and choose a
   documented canonical behavior rather than emitting a localized duplicate.
3. Preserve `/?tgWebAppStartParam=...` ownership by the existing Telegram
   handoff. Do not apply marketing locale routing to that request.
4. Change the language control from a JavaScript-only state switch to real
   same-origin anchors for the paired language path. Keep its localized label,
   `aria-current` or equivalent selected-state semantics, visible focus, and
   44px targets; retain no-JavaScript usability.
5. Remove only the public-site browser-language/content rewriting that conflicts
   with path-owned documents. CTA configuration and OAuth fallback behavior
   remain in `site.js`.
6. Add focused tests for path construction, valid/invalid query handling,
   protected-route exclusion, root Telegram handoff, and the invariant that
   OAuth/Telegram targets are never locale-rewritten.

### Out of scope

- Generating translated document bodies or SEO tags; those belong to
  PSS-SEO-002.
- Changing authenticated family locale, Svelte locale routes, Google OAuth
  contracts, or public URL slugs beyond adding the `/ru/` prefix.

### Acceptance criteria

- All twelve canonical public URLs resolve to the intended static document
  without a client-visible `/public/` URL.
- Valid legacy `?lang=en|ru` requests redirect to their matching language path
  with unrelated query data intact and no redirect loop.
- Public request routing does not capture the Telegram start parameter flow or
  any existing protected/API/health/WebSocket route, including `/app` and the
  legacy `/workspace` redirect.
- The language selector works with JavaScript disabled and does not append a
  locale to configured Telegram or OAuth destinations.

### Targeted validation

```bash
cd apps/web && npm run test -- tests/unit/publicSiteI18n.test.ts tests/unit/previewProxyContext.test.ts && npm run lint
```

### Commit

```bash
git add apps/web/scripts/public-site/urls.js apps/web/scripts/public-site/urls.d.ts apps/web/scripts/preview.mjs apps/web/static/public/site.js apps/web/static/public/site.d.ts apps/web/tests/unit/publicSiteI18n.test.ts apps/web/tests/unit/previewProxyContext.test.ts apps/web/tests/unit/publicSiteUrls.test.ts
git commit -m "feat(web): canonicalize public locale paths"
```

## PSS-SEO-002: Generate complete localized public documents and page metadata

**Status:** TODO
**Priority:** P1
**Depends on:** PSS-SEO-001

**Exact scope:**

Make the static generator emit complete English and Russian documents with
localized body copy and document metadata before JavaScript runs.

**Files:**

- Modify `apps/web/scripts/public-site/i18n.js` and
  `apps/web/scripts/public-site/i18n.d.ts`.
- Modify `apps/web/scripts/public-site/generate.mjs`.
- Modify `apps/web/scripts/public-site/template.html`.
- Modify all six files in `apps/web/scripts/public-site/pages/`.
- Modify `apps/web/static/public/site.js` and `apps/web/static/public/site.d.ts`.
- Regenerate `apps/web/static/public/index.html`, `how.html`, `tasks.html`,
  `rewards.html`, `parents.html`, and `faq.html`.
- Generate and commit `apps/web/static/public/ru/index.html`, `how.html`,
  `tasks.html`, `rewards.html`, `parents.html`, and `faq.html`.
- Modify `apps/web/tests/unit/publicSiteI18n.test.ts`.
- Search anchor: `applyLocale` in `apps/web/scripts/public-site/i18n.js` and
  `{{CONTENT}}` in `apps/web/scripts/public-site/generate.mjs`.

**Goal:**

The HTML response for every canonical language URL is complete, internally
consistent, and understandable to crawlers and users without JavaScript.

### Outcome

English documents contain English body copy, `lang="en"`, localized title and
description. Russian documents contain Russian body copy, `lang="ru"`,
localized title and description. Neither version relies on browser locale or
post-load DOM translation.

### Architectural decision

The public static catalog is the one owner of all visitor-facing strings and
structured page content. The generator renders that catalog into shared
semantic templates; it may escape text but must not inject untrusted HTML.
Source fragments retain layout/semantic structure and reference catalog keys or
generator-provided localized content rather than becoming independent English
and Russian copy stores.

### Required changes

1. Inventory every public string in the shared template and six page fragments:
   navigation, headings, paragraphs, CTA labels, cards, FAQ content, skip link,
   ARIA/title text, status text, document title, and description. Give English
   and Russian catalogs identical keys and test their parity recursively.
2. Refactor generation so it renders one English artifact tree and one Russian
   artifact tree from the catalog. All internal navigation, brand/home links,
   language-control links, and page-specific titles must use PSS-SEO-001’s
   shared URL resolver.
3. Render `<html lang>`, `<title>`, and `<meta name="description">` directly
   in generated output. Remove runtime writes to body copy and SEO metadata;
   leaving runtime CTA/status localization is allowed only when the text comes
   from the same catalog.
4. Add self-referencing canonical and complete `alternate`/`hreflang` link
   elements to each generated document. Use the production origin at request
   time only where a static build has a verified configuration source; otherwise
   generate path-based metadata and complete it at the edge through one tested
   mechanism. Do not hard-code an environment-specific host.
5. Preserve absolute `/public/...` asset/config paths, strict CSP, static
   fallback anchors, and the existing carousel removal. Do not hand-edit any
   generated HTML.
6. Extend unit coverage to verify generated files have no unresolved keys,
   contain full same-language representative content, and do not depend on JS
   to set `lang`, title, description, canonical, or alternates.

### Out of scope

- Sitemap and robots publication; that belongs to PSS-SEO-003.
- New languages, localization of authenticated Svelte views, dynamic CMS
  content, or visual redesign of the public pages.

### Acceptance criteria

- A no-JavaScript request to each of the twelve canonical public URLs exposes
  readable content in its path’s language and matching title/description.
- Generated English pages contain no Russian-only default body copy; generated
  Russian pages contain no English-only fallback body copy except brand names,
  technical product names, or deliberately shared external labels.
- Every generated page has exactly one canonical and alternate links for `en`,
  `ru`, and `x-default`, all pointing to canonical language paths.
- Public links remain inside the intended language URL space while assets stay
  under `/public/`; external Telegram and OAuth URLs are unchanged.

### Targeted validation

```bash
cd apps/web && npm run generate:public && npm run test -- tests/unit/publicSiteI18n.test.ts && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/scripts/public-site/i18n.js apps/web/scripts/public-site/i18n.d.ts apps/web/scripts/public-site/generate.mjs apps/web/scripts/public-site/template.html apps/web/scripts/public-site/pages apps/web/static/public apps/web/tests/unit/publicSiteI18n.test.ts
git commit -m "feat(web): generate localized public documents"
```

## PSS-SEO-003: Publish language-aware sitemap and production robots policy

**Status:** TODO
**Priority:** P1
**Depends on:** PSS-SEO-001, PSS-SEO-002

**Exact scope:**

Expose every canonical localized public document through sitemap metadata and
ensure the production robots policy is enabled by the actual deployed web
environment contract.

**Files:**

- Modify `apps/web/src/routes/sitemap.xml/+server.ts`.
- Modify `apps/web/src/routes/robots.txt/+server.ts`.
- Create `apps/web/tests/unit/sitemap.test.ts`.
- Create `apps/web/tests/unit/robots.test.ts`.
- Modify `.env.example` only if a documented environment contract must be
  clarified.
- Modify `docs/operations/web-miniapp-access.md` only if its environment table
  needs the corrected production variable description; otherwise defer to
  PSS-SEO-005.
- Search anchor: `PUBLIC_PAGES` in `apps/web/src/routes/sitemap.xml/+server.ts`
  and `isProduction` in `apps/web/src/routes/robots.txt/+server.ts`.

**Goal:**

Search engines can discover both language variants and production never
accidentally returns a site-wide indexing block because two environment flags
disagree.

### Outcome

`/sitemap.xml` lists twelve canonical URLs and associates every English/Russian
pair with reciprocal `xhtml:link hreflang` entries. Production `/robots.txt`
allows crawling and points to the same public-origin sitemap; non-production
environments remain blocked.

### Architectural decision

The sitemap route owns XML discovery output and reuses the shared public URL
contract rather than maintaining a third list of paths. Robots production
detection must share the web runtime’s documented deployment flag
(`DEPLOYMENT_ENV`) or one narrowly defined compatibility predicate tested for
both current environment forms. Do not infer production from hostname.

### Required changes

1. Generate sitemap entries for each canonical language path using
   `publicOrigin`, XML-escape values, and include the `xhtml` namespace plus
   alternate links for `en`, `ru`, and `x-default` on each URL entry.
2. Keep only canonical paths in `<loc>`: never list `/public/*.html`, legacy
   `?lang=` URLs, authenticated routes, or unrecognized locale variants.
3. Align robots production detection with the actual Compose/runtime variable
   contract. Preserve `Disallow: /` outside production and include exactly one
   absolute sitemap pointer in production.
4. Add unit tests for all twelve sitemap locations, reciprocal alternate
   relationships, correct origin normalization, no query/asset URLs, production
   allow policy, and non-production deny policy.
5. Update `.env.example` or the operations table if the canonical production
   variable needs explicit operator guidance; do not change unrelated deploy
   configuration.

### Out of scope

- Search Console account operations, reverse-proxy configuration, redirect
  implementation, and generated page content.

### Acceptance criteria

- `sitemap.xml` is valid XML with twelve absolute canonical locations and a
  complete language-alternate cluster for each public page pair.
- `robots.txt` permits crawling and points at the configured public sitemap in
  production, while local/preview/staging responses block indexing.
- Changing `APP_URL` to a path-bearing URL still produces origin-based sitemap
  locations, never a path-prefixed or doubled URL.

### Targeted validation

```bash
cd apps/web && npm run test -- tests/unit/sitemap.test.ts tests/unit/robots.test.ts tests/unit/config.test.ts && npm run lint
```

### Commit

```bash
git add apps/web/src/routes/sitemap.xml/+server.ts apps/web/src/routes/robots.txt/+server.ts apps/web/tests/unit/sitemap.test.ts apps/web/tests/unit/robots.test.ts .env.example docs/operations/web-miniapp-access.md
git commit -m "feat(web): publish localized public sitemap"
```

## PSS-SEO-004: Prove crawler-visible and interactive public locale behavior

**Status:** TODO
**Priority:** P2
**Depends on:** PSS-SEO-001, PSS-SEO-002, PSS-SEO-003

**Exact scope:**

Extend production-preview browser coverage to distinguish server-delivered SEO
output from enhanced browser behavior and to prevent locale-query regression.

**Files:**

- Modify `apps/web/tests/e2e/workspace-entry.spec.ts`.
- Modify `apps/web/tests/unit/publicSiteI18n.test.ts` only for missing generated
  output assertions.
- Modify `apps/web/tests/unit/publicSiteAccess.test.ts` only for public CTA
  regressions affected by the path change.
- Search anchor: `public pages stay in the canonical URL set` in
  `apps/web/tests/e2e/workspace-entry.spec.ts`.

**Goal:**

Automated tests fail when crawlers would see mismatched language content,
duplicate query URLs, absent language metadata, or broken localized navigation.

### Outcome

The preview edge proves the raw response and a JavaScript-disabled browser for
both language paths, then separately proves the accessible language selector
and preserved public/auth/Telegram contracts.

### Architectural decision

Use the existing production-preview Playwright setup. Raw-response assertions
and JavaScript-disabled contexts prove server HTML; normal-page tests prove
enhancement and interaction. Do not use a successful JavaScript-rendered test
as evidence for crawler-visible content.

### Required changes

1. Cover all English and Russian canonical URLs, response status, visible URL,
   HTML `lang`, title, description, canonical, and `hreflang` metadata before
   script execution or with JavaScript disabled.
2. Cover valid legacy query redirects, unknown locale behavior, query retention,
   and the absence of redirect loops. Confirm canonical requests do not expose
   `/public/` in the address bar.
3. In a normal browser, verify EN/RU controls use real hrefs, selected state,
   keyboard access, visible focus, 44px targets, and no horizontal overflow at
   320px. Confirm language navigation changes only public-document links.
4. Retain focused checks for OAuth native fallback, configured Telegram CTA,
   root `tgWebAppStartParam` handoff, and excluded authenticated routes.
5. Run the existing static generator before browser tests and report Chromium
   startup/permission failures as infrastructure blockers rather than SEO
   proof.

### Out of scope

- Live crawler requests, Search Console indexing reports, real Google sign-in,
  or physical Telegram-device validation.

### Acceptance criteria

- Tests fail if the raw `/ru/...` response carries English metadata/body content
  or depends on `site.js` to become Russian.
- Tests fail if `?lang=` remains indexable at 200, canonical/hreflang tags are
  missing or point to `/public/`, or a language switch uses client-only state.
- Compact public pages retain keyboard-accessible controls and working OAuth/
  Telegram fallbacks in both language versions.

### Targeted validation

```bash
cd apps/web && npm run generate:public && npm run test -- tests/unit/publicSiteI18n.test.ts tests/unit/publicSiteAccess.test.ts tests/unit/sitemap.test.ts tests/unit/robots.test.ts && npm run test:e2e -- tests/e2e/workspace-entry.spec.ts && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/tests/e2e/workspace-entry.spec.ts apps/web/tests/unit/publicSiteI18n.test.ts apps/web/tests/unit/publicSiteAccess.test.ts
git commit -m "test(web): cover crawlable public locales"
```

## PSS-SEO-005: Document deployment and search-engine verification

**Status:** TODO
**Priority:** P2
**Depends on:** PSS-SEO-004, PSS-APP-004

**Exact scope:**

Update the public-site release runbook with the localized canonical URL map,
the `/app` browser-route migration, and post-deployment checks that local tests
cannot prove.

**Files:**

- Modify `docs/operations/web-miniapp-access.md`.
- Search anchor: `Release and deployment checklist` in
  `docs/operations/web-miniapp-access.md`.

**Goal:**

An operator can verify indexability and language discovery on the real public
origin without mistaking CI or local Playwright output for crawler evidence.

### Outcome

The runbook names the twelve language URLs, query and legacy-app redirect
expectations, robots/sitemap checks, and Search Console inspection/submission
steps.

### Architectural decision

This task documents observable deployed behavior only. It does not assert that
an indexing request has succeeded until Search Console or the chosen search
engine reports it, and it does not make deployment/provider changes.

### Required changes

1. List the English and Russian canonical URL pairs and state that `/public/`
   documents and `?lang=` URLs are not canonical marketing entries.
2. Add curl/browser checks for response status, final URL, `robots.txt`,
   `sitemap.xml`, canonical tags, reciprocal `hreflang`, and static asset
   loading on the deployed HTTPS origin.
3. Add a Search Console procedure: verify the property, submit the sitemap,
   inspect one English and one Russian URL, request indexing after material
   content changes, and record any coverage/canonicalization report.
4. Retain separate checks for public page accessibility, OAuth, Telegram root
   handoff, and a physical Telegram client. Explicitly label local unit/E2E and
   production/browser/Search Console evidence as different proof tiers.
5. Document `/app` and `/{locale}/app` as the browser application entry paths;
   verify that deployed `/workspace` and `/{locale}/workspace` URLs redirect
   once with query strings intact. Include PWA manifest/scope and notification
   fallback checks for `/app`, while keeping Telegram at `/telegram`.

### Out of scope

- Registering Search Console, changing DNS/CDN/reverse-proxy settings, adding
  analytics, or modifying application code.

### Acceptance criteria

- The runbook gives an operator enough exact URLs and observable checks to
  distinguish canonical English/Russian pages from legacy query/asset URLs and
  the browser `/app` route from legacy `/workspace` links.
- It requires production robots/sitemap and Search Console confirmation while
  clearly stating that local tests do not prove indexing, CDN behavior, or PWA
  migration on already-installed devices.

### Targeted validation

```bash
git diff --check -- docs/operations/web-miniapp-access.md
```

### Commit

```bash
git add docs/operations/web-miniapp-access.md
git commit -m "docs(web): document public locale indexing checks"
```

## PSS-APP-001: Move the authenticated browser route to `/app`

**Status:** DONE
**Priority:** P1
**Depends on:** PSS-SEO-001

**Exact scope:**

Replace the SvelteKit browser workspace route segment with `/app`, retain a
single bounded redirect surface for `/workspace`, and update locale-path rules
so application bootstrap remains outside public static and marketing routing.

**Files:**

- Create `apps/web/src/routes/app/+page.server.ts` and
  `apps/web/src/routes/app/+page.svelte` by moving the current browser page
  implementation from `apps/web/src/routes/workspace/`.
- Replace `apps/web/src/routes/workspace/+page.server.ts` and
  `apps/web/src/routes/workspace/+page.svelte` with a redirect-only
  compatibility route, or use the repository's verified SvelteKit route alias
  mechanism if it provides identical server redirects.
- Modify `apps/web/src/lib/i18n/config.ts`.
- Modify `apps/web/tests/unit/config.test.ts` and create
  `apps/web/tests/unit/appRoute.test.ts` if a focused route/redirect test does
  not fit the existing test file.
- Search anchor: `BYPASS_PREFIXES` and `resolveDomainsForPath` in
  `apps/web/src/lib/i18n/config.ts`.

**Goal:**

The browser app opens at `/app` (and its existing locale-prefixed equivalent),
while every `/workspace` deep link reaches that route through one safe redirect
instead of rendering duplicate application content.

### Outcome

Authenticated and unauthenticated navigation use `/app` or `/{locale}/app`.
`/workspace?tab=tasks` redirects to `/app?tab=tasks`; `/ru/workspace?tab=tasks`
redirects to `/ru/app?tab=tasks`. The redirect does not create a public page,
affect the Telegram host, or drop query parameters.

### Architectural decision

`apps/web/src/routes/app/` becomes the sole SvelteKit implementation owner for
the browser workspace. The retained `workspace` route contains no shell,
session loading, or UI state; it only derives the matching `/app` destination
from the current path and redirects. The i18n path module owns locale stripping
and reconstruction, so the redirect must not manually concatenate locale
segments in multiple components.

### Required changes

1. Move the existing page server-load and Svelte page implementation to the
   `/app` route without changing role resolution, authenticated-session checks,
   page CSS, or Telegram-host behavior.
2. Add a server-side permanent compatibility redirect for bare and locale-
   prefixed `/workspace` requests. Preserve safe query strings and do not
   redirect `/workspace/anything` to an unrelated application subroute unless
   the existing route actually supports that path.
3. Treat both `/app` and legacy `/workspace` as locale-canonicalization bypass
   surfaces while compatibility remains. Ensure domain payload selection gives
   `/app` the existing `common`, `app`, `tasks`, `admin`, and `errors` catalogs.
4. Update the public-edge exclusion contract from PSS-SEO-001 so static
   marketing rewrites never capture `/app` or `/workspace`.
5. Add route-focused tests for bare/localized redirect status and location,
   query preservation, authenticated `/app` rendering, unauthenticated
   continuation behavior, and Telegram/API route non-interference.

### Out of scope

- Changing component/module names containing “workspace”, user-facing copy,
  permissions, API endpoints, family locale state, or Telegram URLs.
- Removing the `/workspace` redirect before measured compatibility retirement.

### Acceptance criteria

- `/app` and `/{locale}/app` render the same authenticated browser experience
  previously available at their workspace counterparts.
- `/workspace` and `/{locale}/workspace` return redirects to the matching app
  path with safe query strings preserved; neither URL renders an application
  shell at HTTP 200.
- An unauthenticated `/app` request retains a safe continuation to the matching
  localized `/app` route and does not send the user to a restored `/login`.
- Static public mapping, Telegram launch, API, health, and WebSocket routes are
  unaffected.

### Targeted validation

```bash
cd apps/web && npm run test -- tests/unit/config.test.ts tests/unit/rootPageLoad.test.ts tests/unit/appRoute.test.ts && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/src/routes/app apps/web/src/routes/workspace apps/web/src/lib/i18n/config.ts apps/web/scripts/preview.mjs apps/web/tests/unit/config.test.ts apps/web/tests/unit/rootPageLoad.test.ts apps/web/tests/unit/appRoute.test.ts
git commit -m "feat(web): move browser workspace to app route"
```

## PSS-APP-002: Move authenticated entry and OAuth continuations to `/app`

**Status:** DONE
**Priority:** P1
**Depends on:** PSS-APP-001

**Exact scope:**

Update every first-party browser entry, OAuth continuation, invitation success,
child magic-link target, and push deep-link fallback to use the canonical app
route while retaining strict local-continuation validation.

**Files:**

- Modify `apps/web/static/public/site.js`, `apps/web/static/public/site.d.ts`,
  and the source template or generator that owns its OAuth fallback anchor.
- Modify `apps/web/src/routes/select-family/+page.svelte` and
  `apps/web/src/routes/invite/parent/+page.svelte`.
- Modify `apps/web/src/lib/auth/googleOAuth.ts` and related unit tests if the
  shared default continuation still names `/workspace`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/identity/api/resource/auth/ChildMagicLinkResource.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/webpush/WebPushService.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/identity/api/resource/auth/ChildMagicLinkResourceTest.java`,
  `apps/backend/src/test/java/com/sashplatonov/earnit/kids/util/PublicOriginResolverTest.java`,
  and the existing Web Push service tests that assert deep links.
- Modify `apps/web/tests/unit/publicSiteAccess.test.ts` and
  `apps/web/tests/unit/googleOAuth.test.ts`.
- Search anchors: `GOOGLE_WORKSPACE_START` in `apps/web/static/public/site.js`,
  `toAbsoluteRedirect("/workspace"` in `ChildMagicLinkResource.java`, and
  `.deepLink("/workspace")` in `WebPushService.java`.

**Goal:**

No new browser authentication, invitation, magic-link, or notification flow
creates a `/workspace` URL after the route migration.

### Outcome

The public Google anchor and enhanced OAuth request use
`continue=/app`; successful child links, selected-family and accepted-invitation
flows arrive at `/app`; default browser push notifications open `/app`.

### Architectural decision

The server remains the authority that validates OAuth continuations through
`PublicOriginResolver`; this task changes only the first-party default target,
not the validation model. Legacy `/workspace` continuations remain safe and
accepted only because PSS-APP-001 redirects them, so old signed links/sessions
do not break. Do not add a second OAuth endpoint or a client-side absolute-URL
validator.

### Required changes

1. Replace first-party `/workspace` defaults in public static OAuth links,
   dynamic OAuth URL requests, family selection, invitation acceptance, child
   magic links, and Web Push fallback payloads with `/app`.
2. Keep Google OAuth continuation syntax local-only. Test `/app` and localized
   app continuations as accepted, and continue rejecting absolute,
   protocol-relative, encoded-bypass, malformed, backslash, fragment, and
   control-character targets.
3. Preserve invite-flow cookies, signed OAuth state, multi-family selection,
   same-origin fallback anchors, and the rule that Telegram deep links are not
   decorated with public-site locale state.
4. Update focused web and backend tests to assert `/app` as the newly emitted
   target while retaining one explicit legacy-continuation compatibility case.

### Out of scope

- Changing Google Cloud callback registration, authentication cookies, API
  route names, notification payload schema, or the compatibility redirect
  itself.

### Acceptance criteria

- New public OAuth starts and browser fallback anchors encode `/app`, never
  `/workspace`.
- A successful child magic link, invitation acceptance, and family selection
  navigate to `/app`; legacy signed `/workspace` continuations remain local and
  safely land on `/app` through the compatibility redirect.
- Default Web Push data opens `/app`, while a valid event-specific same-origin
  deep link continues to be honored.
- Invalid OAuth continuations remain rejected with the current safe error
  contract.

### Targeted validation

```bash
cd apps/backend && ./mvnw -B -ntp test -Dtest=ChildMagicLinkResourceTest,PublicOriginResolverTest,WebPushServiceTest
cd apps/web && npm run test -- tests/unit/publicSiteAccess.test.ts tests/unit/googleOAuth.test.ts && npm run lint
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/identity/api/resource/auth/ChildMagicLinkResource.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/webpush/WebPushService.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/identity/api/resource/auth/ChildMagicLinkResourceTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/util/PublicOriginResolverTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/platform/webpush/WebPushServiceTest.java apps/web/static/public/site.js apps/web/static/public/site.d.ts apps/web/scripts/public-site/template.html apps/web/scripts/public-site/generate.mjs apps/web/src/lib/auth/googleOAuth.ts apps/web/src/routes/select-family/+page.svelte apps/web/src/routes/invite/parent/+page.svelte apps/web/tests/unit/publicSiteAccess.test.ts apps/web/tests/unit/googleOAuth.test.ts
git commit -m "fix(auth): target browser app route"
```

## PSS-APP-003: Migrate the PWA scope and notification fallback to `/app`

**Status:** DONE
**Priority:** P1
**Depends on:** PSS-APP-001

**Exact scope:**

Move the installed browser PWA from the `/workspace` scope to `/app` without
caching protected data and with an explicit cleanup path for stale registrations.

**Files:**

- Modify `apps/web/static/manifest.json`.
- Modify `apps/web/static/sw.js`.
- Modify `apps/web/src/lib/features/workspace/pwa/registerServiceWorker.ts`.
- Modify `apps/web/src/lib/features/workspace/pwa/WorkspacePwaUpdate.svelte`.
- Modify `apps/web/tests/e2e/pwa-browser-push.spec.ts`.
- Modify `apps/web/tests/unit/browserPush.test.ts` if its registration/deep-link
  contract names the old scope.
- Search anchor: `WORKSPACE_PWA_SCOPE` in
  `apps/web/src/lib/features/workspace/pwa/registerServiceWorker.ts` and
  `start_url` in `apps/web/static/manifest.json`.

**Goal:**

New installs and notifications use `/app`, while an existing `/workspace`
service-worker registration cannot retain ownership or cache protected routes.

### Outcome

The manifest has `start_url` and `scope` at `/app`; the browser registers
`/sw.js` for `/app`; notification fallback opens `/app`. On an upgraded browser,
the legacy `/workspace` registration is explicitly unregistered after the new
scope is successfully active.

### Architectural decision

The service worker remains root-hosted at `/sw.js`, but its controlled scope is
limited to `/app`. Migration cleanup belongs in the existing registration module
and happens only after a usable `/app` registration is available; do not widen
the worker to `/` and do not cache navigation/API responses as a shortcut.

### Required changes

1. Change manifest start URL and scope, registration scope constant, update
   banner route guard, service-worker notification fallback, and PWA tests to
   `/app`.
2. Add a bounded migration that finds and unregisters only the exact legacy
   same-origin `/workspace` scope after `/app` is active. It must tolerate
   browsers without a registration, registration failures, and multiple tabs.
3. Keep the worker cache policy unchanged: cache static style/script/image/font
   assets only, leave `/app`, `/workspace`, API, invitation, login-child, and
   OAuth navigations/data uncached, and retain the offline protected-data 5xx
   response.
4. Verify push clicks honor valid same-origin event deep links and use `/app`
   only as the safe fallback.

### Out of scope

- Broadening the worker scope to `/`, adding offline application data, changing
  cache version policy, or modifying Web Push subscription APIs.

### Acceptance criteria

- A fresh authenticated browser registers an active `/app` service-worker scope
  and the installed manifest opens `/app`.
- A pre-existing `/workspace` registration is removed only after the replacement
  registration is active; unrelated registrations are untouched.
- Offline `/app` navigation remains a protected-data failure and neither `/app`
  nor `/workspace` is cached as application content.
- Notification fallback navigates to `/app` and no PWA code emits `/workspace`
  except the precise legacy-cleanup identifier.

### Targeted validation

```bash
cd apps/web && npm run test -- tests/unit/browserPush.test.ts && npm run test:e2e -- tests/e2e/pwa-browser-push.spec.ts && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/static/manifest.json apps/web/static/sw.js apps/web/src/lib/features/workspace/pwa/registerServiceWorker.ts apps/web/src/lib/features/workspace/pwa/WorkspacePwaUpdate.svelte apps/web/tests/e2e/pwa-browser-push.spec.ts apps/web/tests/unit/browserPush.test.ts
git commit -m "feat(web): migrate PWA scope to app"
```

## PSS-APP-004: Cover browser app-route migration regressions

**Status:** TODO
**Priority:** P2
**Depends on:** PSS-APP-001, PSS-APP-002, PSS-APP-003

**Exact scope:**

Update focused browser tests for the canonical `/app` route, legacy redirects,
OAuth continuation, invitation/selection completion, logout, and the PWA scope
without treating a local test as production deployment proof.

**Files:**

- Modify `apps/web/tests/e2e/workspace-entry.spec.ts`.
- Modify `apps/web/tests/e2e/workspace-access.spec.ts`.
- Modify `apps/web/tests/e2e/workspace-invitations.spec.ts`.
- Modify `apps/web/tests/e2e/pwa-browser-push.spec.ts` only for missing
  cross-flow assertions beyond PSS-APP-003.
- Modify `apps/web/tests/e2e/helpers.ts` if its authenticated landing path is
  coupled to `/workspace`.
- Search anchor: `normal browser workspace access` in
  `apps/web/tests/e2e/workspace-access.spec.ts`.

**Goal:**

Regression coverage proves that new browser flows use `/app` and old links
remain safe redirects rather than silently becoming a second application host.

### Outcome

The focused E2E suite exercises authenticated parent/child access, logout,
unauthenticated OAuth continuation, multi-family selection, invitation flow,
legacy deep-link forwarding, compact layout, and PWA behavior from `/app`.

### Architectural decision

Keep test filenames and feature-module names unless a separate refactor is
scheduled; their naming does not define the route contract. Reuse the persistent
mock backend and existing cookie bootstrap. Test both bare and locale-prefixed
paths where the route contract supports them.

### Required changes

1. Change canonical navigation expectations and setup from `/workspace` to
   `/app`, including the encoded continuation expected for unauthenticated
   browser access.
2. Add explicit assertions that bare and localized legacy workspace links
   redirect once to the corresponding app route, preserve query strings, and
   do not display the app before redirecting.
3. Exercise successful OAuth/select-family/invitation/child-link routes and
   browser logout with `/app` as the final application path. Keep Telegram
   assertions distinct from browser workspace assertions.
4. Retain 320px no-horizontal-overflow, visible logout/control behavior, and
   service-worker offline/cache assertions. Report local Chromium startup or
   permission failures as infrastructure blockers, not functional proof.

### Out of scope

- Real Google sign-in, deployed reverse-proxy redirects, Search Console, or
  physical Telegram/PWA-device validation.

### Acceptance criteria

- Focused E2E fails if any new first-party browser entry resolves to
  `/workspace`, if a legacy URL is served at 200, or if OAuth continuation
  returns to the old route.
- Parent and child sessions retain their existing UI/authorization behavior at
  `/app`, and browser logout still returns to the public site.
- Mobile accessibility and the protected PWA cache/offline contract remain
  covered after the route migration.

### Targeted validation

```bash
cd apps/web && npm run test:e2e -- tests/e2e/workspace-entry.spec.ts tests/e2e/workspace-access.spec.ts tests/e2e/workspace-invitations.spec.ts tests/e2e/pwa-browser-push.spec.ts && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/tests/e2e/workspace-entry.spec.ts apps/web/tests/e2e/workspace-access.spec.ts apps/web/tests/e2e/workspace-invitations.spec.ts apps/web/tests/e2e/pwa-browser-push.spec.ts apps/web/tests/e2e/helpers.ts
git commit -m "test(web): cover app route migration"
```
