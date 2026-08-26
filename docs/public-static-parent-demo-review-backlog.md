# Public Static Parent Demo - Review Remediation Backlog

## Goal

Bring the completed public parent demo into compliance with its localized static-document and canonical tab-state contracts. The fixes keep the demo anonymous, read-only, and generated through the existing public-site pipeline.

## Review scope and evidence

- Reviewed the completed `PSD-001` through `PSD-004` tasks in `docs/public-static-parent-demo-backlog.md` and commits `aec15678` through `81c9838f`.
- Traced the public document flow through `apps/web/scripts/public-site/urls.js`, `generate.mjs`, generated static documents, the preview edge mapping, the sitemap, and the browser E2E coverage. The demo has no backend API, domain, persistence, or mutation path; no backend change is needed for either finding.
- Local checks passed before this backlog was written: focused public-site unit tests (19 tests), `npm run lint`, `APP_URL=https://example.test npm run build`, and the three `parent demo` Playwright cases. These checks do not cover the two confirmed requirement gaps below.

## Architectural decisions

- `apps/web/scripts/public-site/i18n.js` remains the sole catalog for all human-readable demo fixture content. `demo-data.js` retains only stable fictional identifiers, quantities, status values, timestamps, and locale-neutral references; do not add a second translation catalog or use authenticated Svelte i18n.
- `tab` is the demo's only state parameter. The URL and selected tab must agree: an unsupported incoming value is canonicalized with `history.replaceState` without a navigation, preserving the served `/demo.html` or `/ru/demo.html` path and unrelated query parameters.
- Preserve the demo's read-only boundary: no API call, storage write, session change, or mutation control is introduced.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | PSD-RVW-001 | P1 | - | Restores correctly localized Russian demo content and the single static i18n source of truth. |
| 2 | PSD-RVW-002 | P1 | - | Restores canonical URL state for invalid demo tabs and protects direct links. |

## PSD-RVW-001: Localize every displayed demo fixture value through the static catalog

**Status:** TODO

**Priority:** P1

**Depends on:** -

**Exact scope:**

Correct the localized parent demo projection so Russian documents do not render English task/reward metadata or availability values.

**Files:**

- Modify `apps/web/scripts/public-site/demo-data.js` and `apps/web/scripts/public-site/demo-data.d.ts`.
- Modify `apps/web/scripts/public-site/demo.js` and `apps/web/scripts/public-site/demo.d.ts` if its fixture lookup contract changes.
- Modify `apps/web/scripts/public-site/i18n.js` and `apps/web/scripts/public-site/i18n.d.ts`.
- Regenerate `apps/web/static/public/demo-data.js`, `apps/web/static/public/demo.js`, `apps/web/static/public/i18n.js`, `apps/web/static/public/demo.html`, and `apps/web/static/public/ru/demo.html` through `npm run generate:public`.
- Modify `apps/web/tests/unit/publicParentDemo.test.ts` and `apps/web/tests/e2e/workspace-entry.spec.ts`.
- Search anchors: `renderTasks`, `renderRewards`, and `copy.reward.available` in `apps/web/scripts/public-site/demo.js`.

**Goal:**

Every visible demo value is presented in the language declared by the served document.

### Outcome

At `/ru/demo.html`, task groups/repeats, reward groups, availability, names, history labels, and request labels are Russian; the English document remains English. No display string is selected from browser preferences or authenticated-app translations.

### Architectural decision

The violated PSD-002 decision requires localized entity names and display strings to be projected through the static catalog. `renderTasks` and `renderRewards` currently emit language-neutral English fixture fields (`task.group`, `task.repeat`, `reward.group`) and literal `Yes`/`No`, so the Russian document violates its document-owned locale contract. Move human-readable fixture values behind stable catalog keys or localized catalog records, while keeping the fixture data immutable and language-neutral.

### Required changes

1. Replace the hard-coded English fixture display fields and the `Yes`/`No` branch with static-catalog lookups selected from `resolveDocumentLocale(document)`.
2. Keep English/Russian catalog shapes recursively equal and retain the existing i18n module as the only static copy source; do not import `$lib` or duplicate translations in `demo-data.js`.
3. Preserve stable fictional IDs, balances, amounts, dates, status tokens, read-only rendering, and the existing real `/app` destinations.
4. Add focused unit coverage for the localization projection and browser coverage that verifies representative Russian task/reward metadata and availability, not merely a Russian heading or entity name.

### Out of scope

- New locales, language switching, browser-locale detection, changes to the family locale model, authenticated Svelte i18n, or demo mutations.

### Acceptance criteria

- `/ru/demo.html` renders Russian values for a task group and repeat schedule, a reward group, and both available/unavailable state; no English `Yes`, `No`, `Learning`, `Home`, `Daily`, `Weekdays`, `Weekly`, `Family time`, or `Small joys` is visible in its demo panels.
- `/demo.html` retains correct English values for the same fields.
- All demo display copy continues to come from the served document locale, with equal EN/RU static-catalog shape and no `$lib`, network, storage, or mutation behavior.
- Existing immutable fixture data and all four tabs still render successfully.

### Targeted validation

```bash
cd apps/web && APP_URL=https://example.test npm run generate:public && npm run test -- tests/unit/publicSiteI18n.test.ts tests/unit/publicParentDemo.test.ts && npm run test:e2e -- tests/e2e/workspace-entry.spec.ts --grep "parent demo" && npm run lint
```

### Commit

```bash
git add apps/web/scripts/public-site/demo-data.js apps/web/scripts/public-site/demo-data.d.ts apps/web/scripts/public-site/demo.js apps/web/scripts/public-site/demo.d.ts apps/web/scripts/public-site/i18n.js apps/web/scripts/public-site/i18n.d.ts apps/web/static/public/demo-data.js apps/web/static/public/demo.js apps/web/static/public/i18n.js apps/web/static/public/demo.html apps/web/static/public/ru/demo.html apps/web/tests/unit/publicParentDemo.test.ts apps/web/tests/e2e/workspace-entry.spec.ts
git commit -m "fix(web): localize public demo fixtures"
```

## PSD-RVW-002: Canonicalize invalid public demo tab parameters

**Status:** TODO

**Priority:** P1

**Depends on:** -

**Exact scope:**

Make a direct public-demo URL with an unsupported `tab` value converge on the same canonical tab state displayed by the page.

**Files:**

- Modify `apps/web/scripts/public-site/demo.js`.
- Regenerate `apps/web/static/public/demo.js` through `npm run generate:public`.
- Modify `apps/web/tests/unit/publicParentDemo.test.ts` and `apps/web/tests/e2e/workspace-entry.spec.ts`.
- Search anchors: `normalizeDemoTab(new URL(windowRef.location.href).searchParams.get('tab'))` in `apps/web/scripts/public-site/demo.js` and `tab=not-a-tab` in `apps/web/tests/e2e/workspace-entry.spec.ts`.

**Goal:**

The URL shown or copied after direct navigation always names the tab that is actually selected.

### Outcome

`/demo.html?tab=not-a-tab` renders Tasks and immediately becomes `/demo.html?tab=tasks` without a reload. The equivalent Russian path stays `/ru/demo.html?tab=tasks`; valid tabs and other query parameters remain intact.

### Architectural decision

PSD-002 requires invalid tab values to normalize to `tasks` and update the current history URL. The current initial render normalizes only the visual selection, while leaving `tab=not-a-tab` in the address bar; the present E2E test asserts that inconsistency. Canonicalize once during initial state resolution with the existing History API, rather than adding a router, redirect, or another state source.

### Required changes

1. Detect an unsupported explicit `tab` during `renderDemo`, replace it with the normalized value before rendering tab semantics, and avoid an event/render loop.
2. Preserve the document locale path, same-origin URL, unrelated query/hash values, and `history.replaceState` no-navigation behavior.
3. Keep valid `tasks|rewards|history|requests` URLs and keyboard/pointer tab selection behavior unchanged.
4. Replace the E2E expectation that currently accepts `tab=not-a-tab` with canonical EN and RU assertions; add a focused pure test for the state-resolution helper if one is extracted.

### Out of scope

- New query parameters, server redirects, app-route changes, browser locale detection, persistence, API requests, or changes to static marketing URL normalization.

### Acceptance criteria

- Direct EN and RU URLs with `tab=not-a-tab` select Tasks and end with `?tab=tasks` while retaining their respective canonical document paths.
- A valid tab remains selected and represented by the same URL after load, click, and keyboard activation.
- The correction is an in-page history replacement: it produces no API/OAuth request, reload, storage write, or mutation control.
- Existing four-tab ARIA relationships, Home/End navigation, 320px layout, and read-only isolation remain intact.

### Targeted validation

```bash
cd apps/web && npm run test -- tests/unit/publicParentDemo.test.ts && npm run test:e2e -- tests/e2e/workspace-entry.spec.ts --grep "parent demo" && npm run lint
```

### Commit

```bash
git add apps/web/scripts/public-site/demo.js apps/web/static/public/demo.js apps/web/tests/unit/publicParentDemo.test.ts apps/web/tests/e2e/workspace-entry.spec.ts
git commit -m "fix(web): canonicalize public demo tab state"
```

## Rejected observations

- The demo data is deeply frozen: each nested object and array is passed through the local `freeze` helper. An attempted `demoData.tasks.push(...)` throws, so it is not a confirmed mutability defect.
- The four latest PSD commits do not change `apps/backend`; tracing confirms the demo intentionally makes no backend request or persistence mutation. No backend remediation task is warranted.
- Focused browser coverage passed at 320px, and the demo's direct EN/RU access, keyboard tabs, static-isolation guard, sitemap entries, and generated-document metadata are covered locally. These results are not deployment, edge/CDN, crawler, Telegram-client, or physical-device proof.
