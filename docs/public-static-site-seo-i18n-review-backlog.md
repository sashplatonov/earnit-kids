# Public Static Site SEO and i18n Review - Remediation Backlog

## Goal

Close the confirmed gaps in the completed public-site SEO and `/app` migration
work: make locale routing and metadata match the documented canonical contract,
keep failed browser entry flows outside the `/public/` asset namespace, and
make the language control accurately announce the selected document language.

## Architectural decisions

- `apps/web/scripts/public-site/urls.js` and `apps/web/scripts/preview.mjs`
  own public-document path routing. Russian browser-language negotiation must
  redirect at the edge before serving HTML; client code must not choose or
  rewrite the document locale.
- Generated public HTML owns canonical and `hreflang` elements. Their absolute
  origin must come from one verified deployment configuration source, reused by
  the existing `APP_URL` public-origin contract rather than hard-coding a host
  or introducing a second URL catalog.
- `/public/` is an asset/artifact namespace only. Backend OAuth and child
  magic-link failure redirects must target the rooted canonical marketing page;
  successful authenticated flows retain their existing `/app` targets.
- The static document's `<html lang>` is the sole locale source for dynamic
  CTA/status copy. The language selector uses real path anchors and
  `aria-current="page"`; it is not a client-side locale switcher.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | PSS-REV-001 | P1 | - | Restores the documented path-owned browser-language redirect before document delivery. |
| 2 | PSS-REV-002 | P1 | - | Stops backend error entry points from exposing a public artifact URL. |
| 3 | PSS-REV-003 | P1 | - | Makes crawler metadata conform to the required absolute canonical URL contract. |
| 4 | PSS-REV-004 | P2 | PSS-REV-001 | Completes static language-control semantics after the path contract is fixed. |

## PSS-REV-001: Redirect Russian browser preferences at the public edge

**Status:** DONE
**Priority:** P1
**Depends on:** -

**Exact scope:**

Implement the `Accept-Language` part of the public language-path contract for
unprefixed marketing documents. The current edge only normalizes `?lang=ru`;
a request such as `GET /how.html` with `Accept-Language: ru-RU` still returns
English HTML, contradicting the completed release runbook.

**Files:**

- Modify `apps/web/scripts/public-site/urls.js` and
  `apps/web/scripts/public-site/urls.d.ts`.
- Modify `apps/web/scripts/preview.mjs`.
- Modify `apps/web/tests/unit/publicSiteUrls.test.ts`.
- Modify `apps/web/tests/e2e/workspace-entry.spec.ts`.
- Search anchor: `normalizePublicRequest` in
  `apps/web/scripts/public-site/urls.js`.

**Goal:**

A first request for a canonical English marketing path from a Russian-preferred
browser reaches its `/ru/` counterpart through one edge redirect, before any
public HTML is served.

### Outcome

`/`, `/how.html`, and the other English marketing paths redirect to their
Russian canonical counterparts when Russian is the highest supported
`Accept-Language` preference. Explicit valid `?lang=` compatibility input
continues to take precedence and protected, Telegram, API, asset, and already
localized paths remain untouched.

### Architectural decision

Extend the existing pure public URL resolver; do not route this behavior
through Svelte session/cookie locale state or `site.js`. The preview edge must
emit an appropriate `Vary: Accept-Language` response header for a
language-dependent redirect and must not create a redirect loop for `/ru/...`.

### Required changes

1. Add supported `Accept-Language` parsing to the existing public URL resolver
   with ordered quality values, recognizing Russian variants and falling back
   to English without redirecting canonical English paths.
2. Make `preview.mjs` apply that resolver only to unprefixed public documents,
   after preserving the existing Telegram-start and valid query-locale rules.
3. Preserve unrelated query pairs and fragments where HTTP permits, and do not
   apply browser-language routing to `/public/`, `/api/`, `/app`, `/workspace`,
   `/telegram`, health, login-child, or WebSocket paths.
4. Add unit and browser regression coverage for Russian negotiation, explicit
   query precedence, `Vary`, no-loop localized requests, and protected-route
   exclusion.

### Out of scope

- Family locale persistence, Svelte authenticated-route locale behavior, new
  supported languages, or changing Google/Telegram destinations.

### Acceptance criteria

- A Russian-preferred request to each rooted English marketing document gets
  exactly one redirect to its canonical `/ru/...` path before HTML is served.
- An English/unsupported preference keeps the established English canonical
  URL; explicit valid `?lang=en|ru` remains deterministic and preserves
  unrelated query data.
- `/ru/...` URLs, Telegram root handoff, public assets, backend proxy routes,
  and `/app`/`/workspace` do not gain this redirect behavior.
- The redirect is cache-safe for language negotiation and local browser E2E
  proves the final document language and URL separately from deployment proof.

### Targeted validation

```bash
cd apps/web && npm run test -- tests/unit/publicSiteUrls.test.ts && npm run test:e2e -- tests/e2e/workspace-entry.spec.ts && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/scripts/public-site/urls.js apps/web/scripts/public-site/urls.d.ts apps/web/scripts/preview.mjs apps/web/tests/unit/publicSiteUrls.test.ts apps/web/tests/e2e/workspace-entry.spec.ts
git commit -m "fix(web): route public locale from browser preference"
```

## PSS-REV-002: Keep failed auth entry flows on the canonical public page

**Status:** DONE
**Priority:** P1
**Depends on:** -

**Exact scope:**

Replace the real backend failure redirects that currently expose
`/public/index.html` with the rooted public canonical page. This applies to an
invalid child magic link and a Google-start failure; authenticated success
redirects to `/app` are not part of this task.

**Files:**

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/identity/api/resource/auth/ChildMagicLinkResource.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/identity/api/resource/auth/AuthGoogleResource.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/identity/api/resource/auth/ChildMagicLinkResourceTest.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/identity/api/resource/auth/AuthResourceTest.java`.
- Search anchors: `invalid_token` in `ChildMagicLinkResource` and
  `google_start_failed` in `AuthGoogleResource`.

**Goal:**

Failure feedback returns users to the one canonical public site URL rather than
to a generated artifact path that is explicitly non-canonical.

### Outcome

Invalid child-token and unavailable-Google-start responses are still `303`,
retain their error query value and cache protection, and resolve to
`/?error=...` (or the configured absolute equivalent). No backend response
uses `/public/index.html` as a user-facing fallback.

### Architectural decision

`PublicOriginResolver` remains the sole authority for absolute redirect
construction. Change only fallback paths and their tests; do not relax OAuth
continuation validation or remove the bounded `/workspace` compatibility
redirect.

### Required changes

1. Replace the two reachable `/public/index.html` error destinations with the
   rooted canonical public path while preserving HTTP status, error codes, and
   no-cache headers.
2. Remove or update any now-dead fallback helper whose only behavior is to
   reintroduce that artifact path, without changing callback success or family
   selection behavior.
3. Add focused tests for both failure responses with and without configured
   `APP_URL`, including error query preservation and absence of `/public/`.
4. Retain tests that prove successful child and OAuth flows reach `/app` and
   that unsafe continuations remain rejected.

### Out of scope

- Google provider registration, callback protocol, session cookie format,
  public page redesign, or removal of existing legacy `/workspace` redirects.

### Acceptance criteria

- An invalid child magic link redirects once to the canonical public root with
  `error=invalid_token`, never to `/public/index.html`.
- A failed Google start redirects once to the canonical public root with
  `error=google_start_failed`, both for relative and configured-origin cases.
- Successful child magic links and valid OAuth continuations retain their
  current `/app` behavior and invalid continuations remain rejected.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -B -ntp test -Dtest=ChildMagicLinkResourceTest,AuthResourceTest,PublicOriginResolverTest
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/identity/api/resource/auth/ChildMagicLinkResource.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/identity/api/resource/auth/AuthGoogleResource.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/identity/api/resource/auth/ChildMagicLinkResourceTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/identity/api/resource/auth/AuthResourceTest.java
git commit -m "fix(auth): keep failure redirects canonical"
```

## PSS-REV-003: Emit absolute canonical and alternate public metadata

**Status:** TODO
**Priority:** P1
**Depends on:** -

**Exact scope:**

Make every generated public document emit absolute self-canonical and
`hreflang` URLs. Current artifacts contain path-only values such as
`<link rel="canonical" href="/">`, despite the completed SEO contract requiring
absolute URLs.

**Files:**

- Modify `apps/web/scripts/public-site/generate.mjs`.
- Modify `apps/web/scripts/public-site/template.html`.
- Modify `apps/web/scripts/public-site/urls.js` and
  `apps/web/scripts/public-site/urls.d.ts` only if the existing shared URL
  helper needs a pure absolute-document function.
- Modify `apps/web/Dockerfile` and `docker-compose.yml` if required to pass
  the existing `APP_URL` contract safely into the build stage.
- Modify `apps/web/tests/unit/publicSiteI18n.test.ts`.
- Modify `apps/web/tests/e2e/workspace-entry.spec.ts`.
- Modify `docs/operations/web-miniapp-access.md` only if the deployment input
  or verification command changes.
- Search anchors: `CANONICAL` in
  `apps/web/scripts/public-site/generate.mjs` and `APP_URL` in
  `docker-compose.yml`.

**Goal:**

Crawler-visible metadata has one absolute production-origin URL per localized
document and exactly the same `en`, `ru`, and `x-default` cluster as the
sitemap.

### Outcome

All twelve generated files have an absolute canonical href matching their
served path and absolute alternate hrefs sharing one configured origin. The
image build cannot silently bake an example/development host into production
HTML.

### Architectural decision

Use the existing `APP_URL` public-origin configuration as the only host source.
Because public documents are generated during the Docker build while `APP_URL`
is currently injected at runtime, explicitly carry and validate that same
configuration across the build boundary (or implement one tested edge rewrite
that produces equivalent HTML). Do not hard-code a deployment hostname or
maintain a second origin catalog in templates, sitemap, and generated files.

### Required changes

1. Extend the generator/template input so canonical and alternate values are
   absolute, normalized, and derived from a single configured public origin.
2. Make the deployment build receive that existing origin deliberately and
   fail safely for a production build if it is missing or invalid; retain a
   deterministic non-production test input without embedding it in shipped
   artifacts.
3. Regenerate and commit all English and Russian public documents; preserve
   their path-owned content, strict CSP, asset paths, and root URL map.
4. Expand unit and no-JavaScript browser assertions to require absolute
   canonical/alternate hrefs, reciprocal clusters, origin normalization, and
   no `/public/`, query, `/app`, or `/workspace` metadata target.

### Out of scope

- Adding languages, changing sitemap URL inventory, Search Console operations,
  content redesign, or changing the OAuth/Telegram URL contract.

### Acceptance criteria

- Each of the twelve generated documents has one absolute self-canonical URL
  and three absolute alternate links for `en`, `ru`, and `x-default`.
- Public-document metadata and the runtime sitemap use the same normalized
  configured origin and language paths, with no build-environment host leak.
- A production image build cannot ship relative canonical metadata or silently
  fall back to an unverified host; local unit/browser proof remains distinct
  from deployed crawler verification.

### Targeted validation

```bash
cd apps/web && APP_URL=https://example.test npm run generate:public && npm run test -- tests/unit/publicSiteI18n.test.ts tests/unit/sitemap.test.ts && npm run test:e2e -- tests/e2e/workspace-entry.spec.ts && npm run lint && APP_URL=https://example.test npm run build
docker compose config
```

### Commit

```bash
git add apps/web/scripts/public-site/generate.mjs apps/web/scripts/public-site/template.html apps/web/scripts/public-site/urls.js apps/web/scripts/public-site/urls.d.ts apps/web/static/public apps/web/tests/unit/publicSiteI18n.test.ts apps/web/tests/e2e/workspace-entry.spec.ts apps/web/Dockerfile docker-compose.yml docs/operations/web-miniapp-access.md
git commit -m "fix(web): emit absolute public seo metadata"
```

## PSS-REV-004: Make static language selection path-owned and announced

**Status:** TODO
**Priority:** P2
**Depends on:** PSS-REV-001

**Exact scope:**

Fix the static language control so it announces the active language without
JavaScript and ensure dynamic status/CTA copy follows the already-served
document language. Today neither EN nor RU gets `aria-current`, even though
the stylesheet expects it, and `site.js` can choose English-page status text
from browser preferences.

**Files:**

- Modify `apps/web/scripts/public-site/generate.mjs`.
- Modify `apps/web/scripts/public-site/template.html`.
- Modify `apps/web/scripts/public-site/i18n.js`.
- Regenerate `apps/web/static/public/index.html`, `how.html`, `tasks.html`,
  `rewards.html`, `parents.html`, `faq.html`, and their `ru/` counterparts,
  plus generated public runtime modules when changed.
- Modify `apps/web/tests/unit/publicSiteI18n.test.ts`.
- Modify `apps/web/tests/unit/publicSiteAccess.test.ts`.
- Modify `apps/web/tests/e2e/workspace-entry.spec.ts`.
- Search anchors: `language-switcher` in
  `apps/web/scripts/public-site/template.html` and `resolveLocale` in
  `apps/web/static/public/site.js`.

**Goal:**

The language selector is usable without JavaScript, visibly and semantically
marks the current document language, and never lets browser preference create
mixed-language dynamic feedback on a path-owned document.

### Outcome

Each English page marks EN and each Russian page marks RU with
`aria-current="page"`; both retain real 44px language-path links. Runtime
unavailable/OAuth status copy uses the generated document's `<html lang>`, not
query or navigator language.

### Architectural decision

The generator, not runtime DOM rewriting, owns selected-state markup. Keep the
static catalog only for messages still used by CTA enhancement; remove obsolete
public client-locale helpers only after confirming no remaining imports, rather
than retaining a second locale-selection path.

### Required changes

1. Generate `aria-current="page"` for exactly one active language anchor per
   document and retain the existing visible selected style and keyboard focus.
2. Derive CTA/status localization strictly from validated `<html lang>` and
   remove runtime browser/query locale selection from the public enhancement
   path.
3. Delete or narrow unused legacy browser-language/content-rewrite helpers in
   the static i18n module only when their call sites and tests are migrated.
4. Add unit and E2E coverage for selected-state semantics, no-JavaScript
   language links, keyboard focus/44px targets, and an English document under
   Russian browser preferences retaining English dynamic feedback.

### Out of scope

- Family locale settings, a client-side language toggle, translated
  authenticated Svelte pages, or changes to the browser-language edge redirect
  beyond PSS-REV-001.

### Acceptance criteria

- Every public document has exactly one language link with
  `aria-current="page"`, matching `<html lang>`, before JavaScript runs.
- Both links retain the paired canonical path and a visible keyboard focus with
  at least a 44px touch target at 320px and desktop widths.
- Runtime public-site status text cannot differ from the path document’s
  language because of `navigator.languages` or a locale query.

### Targeted validation

```bash
cd apps/web && npm run generate:public && npm run test -- tests/unit/publicSiteI18n.test.ts tests/unit/publicSiteAccess.test.ts && npm run test:e2e -- tests/e2e/workspace-entry.spec.ts && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/scripts/public-site/generate.mjs apps/web/scripts/public-site/template.html apps/web/scripts/public-site/i18n.js apps/web/static/public apps/web/tests/unit/publicSiteI18n.test.ts apps/web/tests/unit/publicSiteAccess.test.ts apps/web/tests/e2e/workspace-entry.spec.ts
git commit -m "fix(web): announce selected public language"
```

## Rejected observations

- Focused web unit tests (35 tests) and focused backend tests (11 tests) pass,
  but the reviewed tests encode several of the above incomplete contracts; a
  passing suite is therefore not evidence against these findings.
- The reviewed `/app` compatibility redirect, PWA scope/cache contract,
  Web Push `/app` fallback, sitemap URL inventory, and OAuth continuation
  validation had no separate confirmed regression in this review.
