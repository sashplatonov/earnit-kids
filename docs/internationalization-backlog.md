# Internationalization - Implementation Backlog

<a id="top"></a>

## Table of contents

- [Goal](#goal)
- [Audit basis](#audit-basis)
- [Architectural decisions](#architectural-decisions)
- [Recommended implementation order](#recommended-implementation-order)
- [I18N-001: Record the internationalization architecture decision](#i18n-001-record-the-internationalization-architecture-decision)
- [I18N-002: Configure one family language at administrator onboarding](#i18n-002-configure-one-family-language-at-administrator-onboarding)
- [I18N-003: Normalize REST errors to stable codes and parameters](#i18n-003-normalize-rest-errors-to-stable-codes-and-parameters)
- [I18N-004: Resolve the family language across web and Telegram bootstrap](#i18n-004-resolve-the-family-language-across-web-and-telegram-bootstrap)
- [I18N-005: Harden the SvelteKit translation platform](#i18n-005-harden-the-sveltekit-translation-platform)
- [I18N-006: Localize authenticated workspace and responsive web UI](#i18n-006-localize-authenticated-workspace-and-responsive-web-ui)
- [I18N-007: Localize public pages and multilingual SEO](#i18n-007-localize-public-pages-and-multilingual-seo)
- [I18N-008: Localize the Telegram Mini App](#i18n-008-localize-the-telegram-mini-app)
- [I18N-009: Localize Telegram Bot menus and recipient delivery](#i18n-009-localize-telegram-bot-menus-and-recipient-delivery)
- [I18N-010: Enforce localization regression coverage and contributor workflow](#i18n-010-enforce-localization-regression-coverage-and-contributor-workflow)

## Goal

Deliver a production-ready, SSR-safe English and Russian experience for the public site, web workspace, Telegram Mini App, and Telegram Bot. The public visitor selects their own language; each family uses one language selected by a family administrator during their first entry and inherited by every parent and child. API/domain contracts remain language-neutral, and user-created task and reward names remain user data rather than translation keys. The design must admit `sr` and further locales without a business-logic rewrite.

[↑ Back to top](#top)

## Audit basis

- The web app already has a custom typed catalog in `apps/web/src/lib/i18n/`, URL/cookie/`Accept-Language` resolution, SSR payloads, and `Intl` formatting. It currently imports all catalogs eagerly, does not validate locale parity, and leaves user-facing strings in workspace, invitation, and Telegram routes/components.
- `apps/web/src/hooks.server.ts` forces the bare Telegram Mini App to Russian. `apps/web/src/lib/services/telegram.ts` does not expose `initDataUnsafe.user.language_code`, although authenticated Telegram init data contains a signed `user` payload that the backend already parses.
- The backend has `messages.properties`, `messages_ru.properties`, request-locale filtering, localized Bean Validation, and `ErrorResponse.errorCode`. Several application paths still pass localized prose through `OperationResult`, while bot copy is Russian text embedded in Java classes.
- Neither `FamilyEntity`, `ParentAccountEntity`, `ChildEntity`, nor `TelegramIdentityEntity` currently persists a locale. `TelegramDeliveryPlanner` records the recipient identity; it must derive the recipient's family locale at delivery time.
- `apps/web/static/public/` is a static English marketing site with a single-language sitemap; no email sender or email-template subsystem was found. Existing task/reward/catalog fields are family-owned user content, not system presets, and no translation-table-backed catalog was found.

## Architectural decisions

- Keep the existing SvelteKit catalog rather than introduce an i18n library. It already supplies SSR-safe, typed keys and route domain selection; extend it with dynamic domain loading, parity checks, typed interpolation contracts, and `Intl.PluralRules`/`Intl.DateTimeFormat`. This is lower-risk than a framework migration for two locales and still supports future languages.
- Public pages use `/{locale}/...` as their canonical, indexed URLs. Public language choice is local to the visitor (URL, then cookie, then `Accept-Language`, then `en`) and is presented with a flag graphic plus a text label. The bare `/telegram` entry point remains necessary for Telegram launch parameters.
- Persist one explicit, normalized BCP-47 language tag (`en` or `ru` initially) on `FamilyEntity`. It is configured by a family administrator when the family is first opened, applies to every family parent and child, and can later be changed only by a family administrator. It is not stored on `ParentAccountEntity`, `ChildEntity`, or `TelegramIdentityEntity`; Telegram language is neither a source of truth nor a fallback for a configured family.
- For a family without a configured language, the first authorized family administrator is sent to a blocking onboarding choice. The choice writes `FamilyEntity.locale`; all other family members then receive it on the next session/bootstrap. Until configuration completes, non-administrator members receive the documented safe fallback (`en`) and cannot change it. Existing families are treated as unconfigured and prompt their administrator on the next entry rather than silently assigning a personal language.
- All visual language selection uses the same accessible flag-based control: flag graphic, localized language name, and an accessible name that does not rely on the flag/color alone. It is reused by the public site and by the family-administrator setting in workspace/Mini App. The bot has no independent selector and always follows its family language.
- API/domain errors are stable codes plus machine-readable parameters and trace ID. A temporary localized `detail` remains only for backward-compatible REST clients during migration; web maps codes to frontend catalog copy, while bot and other server-generated messages use backend bundles. Logs stay developer-facing English and never derive from localized presentation text.
- UI translations live in the web catalog; Telegram Bot messages and server-generated delivery text live in backend resource bundles. No shared frontend/backend translation package, translation microservice, database translation table, runtime translation service, or automatic translation of user content is introduced. System-owned presets, if introduced later, use stable keys until a real editable-content requirement justifies translation tables.
- Catalogs must not contain untrusted HTML. Interpolate user values as text; escape Telegram HTML/Markdown at the API boundary and keep callback data stable identifiers independent of translated labels.
- Java locale decisions and internal DTOs use FamilyLocale exclusively; do not introduce "en"/"ru" string literals for locale selection. Convert the enum to its wire representation only at the HTTP/JSON boundary, and parse external language tags into the enum at the input boundary.

[↑ Back to top](#top)

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | I18N-001 | P1 | - | Freeze one locale, URL, ownership, and error-contract decision before changing clients. |
| 2 | I18N-002 | P1 | I18N-001 | Create one family-wide language and administrator-first onboarding contract. |
| 3 | I18N-003 | P1 | I18N-002 | Make backend errors language-neutral without breaking existing consumers. |
| 4 | I18N-004 | P1 | I18N-002 | Resolve the configured family language consistently in web and Telegram. |
| 5 | I18N-005 | P1 | I18N-001 | Make the existing web catalog complete, safe, and verifiable. |
| 6 | I18N-006 | P1 | I18N-004, I18N-005 | Localize authenticated desktop/mobile workspace behavior and metadata. |
| 7 | I18N-007 | P1 | I18N-005 | Localize the indexable public site and its SEO representation. |
| 8 | I18N-008 | P1 | I18N-003, I18N-004, I18N-005 | Replace the Mini App's forced-Russian and hard-coded presentation paths. |
| 9 | I18N-009 | P1 | I18N-002, I18N-005 | Localize bot menus and delivery messages from the recipient family language. |
| 10 | I18N-010 | P2 | I18N-003, I18N-006, I18N-007, I18N-008, I18N-009 | Add cross-channel regression coverage, catalog CI checks, and contributor documentation. |

## I18N-001: Record the internationalization architecture decision

**Status:** DONE
**Priority:** P1
**Depends on:** -

**Exact scope:** Document the confirmed current state and the decisions above as the implementation contract. This task does not change product behavior.

**Files:**

- Create `docs/adr/0001-internationalization-strategy.md`.
- Modify `README.md` under a new `Internationalization` section.
- Modify `apps/web/docs/ARCHITECTURE.md` and `apps/backend/docs/ARCHITECTURE.md` at their localization/API-boundary sections.

**Goal:** A contributor can determine locale precedence, URL ownership, translation ownership, API error semantics, and the supported-locale extension workflow without reading implementation code.

### Required changes

1. Write an ADR with Context, Decision, Alternatives, Consequences, locale resolution matrix, API error strategy, and public URL/SEO strategy.
2. Document why the existing typed SvelteKit catalog plus native `Intl` is retained instead of adding a separate i18n library; note its required lazy-domain and validation improvements.
3. State that email localization is not applicable until an email delivery/template producer exists, and that user-created task/reward/catalog values must never become translation keys.

### Out of scope

- Implementing locale storage, catalog changes, routes, or bot messages.

### Acceptance criteria

- The ADR distinguishes public visitor selection from the administrator-controlled family language for workspace, Mini App, and bot.
- It names `en` as fallback and describes normalization of `en-US`, `ru-RU`, and unsupported values.
- It explicitly rejects localized API enums/display labels and database translation tables for current user-owned catalog data.

[↑ Back to top](#top)

### Targeted validation

```bash
git diff --check -- docs/adr/0001-internationalization-strategy.md README.md apps/web/docs/ARCHITECTURE.md apps/backend/docs/ARCHITECTURE.md
```

### Commit

```bash
git add docs/adr/0001-internationalization-strategy.md README.md apps/web/docs/ARCHITECTURE.md apps/backend/docs/ARCHITECTURE.md
git commit -m "docs(i18n): Record localization strategy"
```

## I18N-002: Configure one family language at administrator onboarding

**Status:** DONE
**Priority:** P1
**Depends on:** I18N-001

**Exact scope:** Add one family-scoped locale, require its choice on the first family-administrator entry, and expose a family-administrator-only update/read contract for every family member's clients.

**Files:**

- Create `apps/backend/src/main/resources/db/migration/NNN_add_family_locale.sql` using the next verified migration number.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/domain/model/FamilyEntity.java` and its repository/mapping path.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/api/resource/FamilyReadResource.java` and the existing family-admin settings resource identified by `rg 'family_admin|Family.*Settings|permission' apps/backend/src/main/java`.
- Search anchors: `FamilyParentMembershipEntity`, `SessionPageDataResponse`, and `AuthPayload` for authorization and session/bootstrap contracts.
- Create focused tests beside the affected family, membership, migration, and session tests.

**Goal:** The first family administrator chooses `en` or `ru` before using the family workspace; the saved family language is then the same for every parent, child, Mini App session, and bot message in that family.

### Required changes

1. Add a nullable normalized locale column to `families` with a database constraint limited to currently supported values. `NULL` means the administrator has not yet completed required language setup.
2. Add a read/update API contract with `locale`, `languageSetupRequired`, and family-admin authorization. Only a membership with the existing family-admin permission can set or change the language; parents and children have no individual locale write path.
3. Return the configured family locale and setup-required state through the established session/family response paths so SSR and client bootstrap avoid a second source of truth.
4. Treat regional input according to the ADR normalization rule and reject unsupported values. Use shared backend locale support; do not persist arbitrary strings.
5. Backfill no personal values. Existing families remain `NULL` and require their family administrator to choose at the next entry; non-administrators receive the safe fallback until then.

### Out of scope

- Translating UI or bot strings.
- A bot language command, individual parent/child preferences, a generic preferences table, or any translation table.

### Acceptance criteria

- A first-entering family administrator cannot reach the workspace until they select a language.
- After the administrator selects `ru` or `en`, every parent and child in that family receives the same saved locale after fresh authentication.
- Invalid values such as `xx-YY` cannot be saved; `en-US`/`ru-RU` follow the ADR normalization rule.
- A non-admin parent and a child cannot update the family language.
- Existing families without a locale create no personal migration data and follow the documented setup/fallback behavior; existing API consumers remain compatible.

### Targeted validation

```bash
cd apps/backend && ./mvnw -B -ntp -Dtest=FamilyReadResourceTest,FamilyParentAccessResourceTest,SessionPageDataResourceTest test
```

### Commit

```bash
git add apps/backend/src/main/resources/db/migration/NNN_add_family_locale.sql apps/backend/src/main/java/com/sashplatonov/earnit/kids/family apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto apps/backend/src/test/java/com/sashplatonov/earnit/kids/family apps/backend/src/test/java/com/sashplatonov/earnit/kids/identity/api/resource/auth
git commit -m "feat(backend): Configure family language"
```

[↑ Back to top](#top)

## I18N-003: Normalize REST errors to stable codes and parameters

**Status:** DONE
**Priority:** P1
**Depends on:** I18N-002

**Exact scope:** Make REST errors consistently machine-readable while retaining a bounded compatibility detail during client migration. Domain/application services must signal codes and parameters, not pre-localized prose.

**Files:**

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/shared/api/response/ErrorResponse.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/exception/ConstraintViolationExceptionMapper.java` and `apps/backend/src/main/java/com/sashplatonov/earnit/kids/exception/GlobalExceptionMapper.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/util/OperationResult.java`, `apps/backend/src/main/java/com/sashplatonov/earnit/kids/util/OperationResultResponses.java`, and their callers found by `rg 'OperationResult.failure|ErrorResponse.of' apps/backend/src/main/java`.
- Modify `apps/backend/src/main/resources/messages.properties` and `apps/backend/src/main/resources/messages_ru.properties` only for server-owned fallback presentation.
- Modify `apps/web/src/lib/services/serverContract.ts` at its error normalization boundary.
- Create focused mapper/contract tests under existing `exception`, `i18n`, and web service test directories.

**Goal:** API clients receive a stable code, HTTP status, optional safe parameters, and trace ID regardless of request locale; web and server delivery resolve their own display strings.

### Required changes

1. Define one backward-compatible problem payload shape with `errorCode`, `params`, `traceId`, and the existing RFC-7807 fields; document whether `detail` is present during the compatibility window and prohibit it as frontend logic input.
2. Map Bean Validation, authorization, business conflicts, and unknown failures deterministically. Do not expose exception class names, SQL values, raw headers, or user-provided HTML in parameters.
3. Replace domain/application localization calls and prose-only `OperationResult.failure` paths with named codes and typed parameters; keep backend bundle lookup only in REST adapters and server-generated channels.
4. Map codes to web catalog keys with a generic safe fallback for an unknown newer code, so independently deployed frontend/backend versions remain usable.

### Out of scope

- Removing all existing localized `detail` fields before the documented compatibility window.
- Changing successful DTO names, task/reward user content, or log language.

### Acceptance criteria

- The same failing request in `en` and `ru` has identical `errorCode`, parameters, status, type, and trace ID.
- Validation responses identify safe field/code information without relying on localized concatenated strings.
- A frontend encountering an unknown code shows a generic localized error rather than a raw code/key.
- Existing clients that read `detail` continue to receive the documented compatibility behavior.

### Targeted validation

```bash
cd apps/backend && ./mvnw -B -ntp -Dtest=ConstraintViolationExceptionMapperTest,GlobalExceptionMapperTest,BackendMessagesTest test
cd apps/web && npm run test -- --run tests/unit/serverContract.test.ts
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/{exception,shared,util,i18n} apps/backend/src/main/resources/messages.properties apps/backend/src/main/resources/messages_ru.properties apps/backend/src/test/java/com/sashplatonov/earnit/kids/{exception,i18n} apps/web/src/lib/services/serverContract.ts apps/web/tests/unit/serverContract.test.ts
git commit -m "refactor(backend): Standardize localized error contracts"
```

[↑ Back to top](#top)

## I18N-004: Resolve the family language across web and Telegram bootstrap

**Status:** DONE
**Priority:** P1
**Depends on:** I18N-002

**Exact scope:** Resolve the configured `FamilyEntity.locale` for authenticated web, SSR, Mini App, and backend calls; direct unconfigured family administrators to onboarding before protected family content renders.

**Files:**

- Modify `apps/web/src/hooks.server.ts`, `apps/web/src/lib/server/session.ts`, and `apps/web/src/lib/server/proxy.ts`.
- Modify `apps/web/src/lib/components/LocaleSwitcher.svelte` or replace it with the shared flag-based language selector, and modify `apps/web/src/lib/features/telegram/TelegramWorkspaceBootstrap.ts`.
- Modify the backend family/session authorization flow identified by `FamilyEntity`, `SessionPageDataResponse`, and `AuthPayload`.
- Modify the session/auth DTOs identified by `SessionPageDataResponse` and `AuthPayload`.
- Modify existing locale, Telegram auth, workspace entry, and Mini App E2E tests.

**Goal:** Browser, SSR, Mini App, and backend requests for a configured family use its one saved language with no redirect loop or hydration mismatch; the first family administrator is routed to language setup.

### Required changes

1. Preserve `/telegram` as a bare launch URL, remove its unconditional Russian selection, and apply the configured family language before rendering user-facing Mini App copy.
2. Make the family language win for every authenticated family route over URL, cookie, `Accept-Language`, and Telegram language hints. Anonymous public web retains URL/cookie/header/default precedence.
3. Route an administrator whose family locale is `NULL` to a blocking language-setup view; return a deterministic fallback to non-admin family members until setup completes without allowing them to choose a different language.
4. Forward the effective family language through existing proxy headers and update an already open web/Mini App session after an administrator changes it. Do not add locale to JWT claims unless the session refresh model cannot carry the state safely.

### Out of scope

- URL-prefixed Telegram deep links, a bot language command, or translating Mini App components.

### Acceptance criteria

- A configured `ru` family wins over an `en-US` browser header and Telegram language hint; anonymous public requests still honor locale URL/cookie/header precedence.
- An unconfigured family administrator is presented with language setup before family content, and a non-admin cannot bypass or mutate that setup.
- Changing the family language updates parent and child sessions after refresh, including Mini App bootstrap.
- SSR HTML `lang`, hydration state, backend request locale, and rendered catalog locale agree.

### Targeted validation

```bash
cd apps/backend && ./mvnw -B -ntp -Dtest=FamilyReadResourceTest,TelegramMiniAppAuthServiceTest,RequestLocaleFilterTest test
cd apps/web && npm run test -- --run tests/e2e/workspace-entry.spec.ts tests/e2e/telegram-auth.spec.ts
```

### Commit

```bash
git add apps/web/src/{hooks.server.ts,lib/server/session.ts,lib/server/proxy.ts,lib/components/LocaleSwitcher.svelte,lib/features/telegram/TelegramWorkspaceBootstrap.ts} apps/web/tests/e2e/{workspace-entry.spec.ts,telegram-auth.spec.ts} apps/backend/src/main/java/com/sashplatonov/earnit/kids/{family,telegram,dto} apps/backend/src/test/java/com/sashplatonov/earnit/kids/{family,telegram,config}
git commit -m "feat(i18n): Resolve family language"
```

[↑ Back to top](#top)

## I18N-005: Harden the SvelteKit translation platform

**Status:** DONE
**Priority:** P1
**Depends on:** I18N-001

**Exact scope:** Complete the existing frontend i18n infrastructure: catalog loading, message-key/interpolation safety, plural/formatting helpers, missing-key behavior, deterministic catalog validation, and one reusable accessible flag-based language selector.

**Files:**

- Modify `apps/web/src/lib/i18n/config.ts`, `apps/web/src/lib/i18n/index.ts`, `apps/web/src/lib/i18n/context.ts`, and `apps/web/src/lib/i18n/formatters.ts`.
- Modify `apps/web/src/lib/i18n/messages/{en,ru}/*.ts`.
- Modify `apps/web/src/lib/components/LocaleSwitcher.svelte` or create a replacement shared component and its icon/flag assets.
- Create `apps/web/tests/unit/i18n/*.test.ts` and an npm script if a dedicated catalog check is warranted.
- Modify `apps/web/package.json` and `.github/workflows/quality.yml` only if the validation cannot run through the existing test/lint commands.

**Goal:** A missing key, mismatched interpolation variable, or locale catalog drift is caught before release, while each SSR route receives only its required message domains.

### Required changes

1. Preserve domain-oriented keys such as `common.actions.save` and `tasks.create.success`; avoid an oversized catch-all `common` namespace and positional/numeric keys.
2. Replace eager all-domain catalog assembly with compatible per-domain loading/caching so SSR and client navigation load only requested domains. Maintain a safe default-locale fallback for missing localized entries without displaying raw keys to users.
3. Define typed interpolation contracts for dynamic messages and use `Intl.PluralRules` for Russian/English plural forms; route date, number, relative time, percentage, and coin formatting through one locale-aware layer.
4. Add CI-verifiable checks for exact locale key parity, valid message shapes/interpolation placeholders, supported locale list, and controlled development diagnostics for missing translations. Do not add unreliable global hard-coded-string linting.
5. Keep translation values plain text by default; expose no raw HTML rendering helper.
6. Build a shared selector that presents a recognizable RU/EN flag graphic with localized language text and `aria-label`; active state must not rely on the flag/color alone. It must work as a keyboard-accessible control and provide a 44px target on touch surfaces.

### Out of scope

- Replacing the current catalog with a third-party framework.
- Translating all consumers in this task.

### Acceptance criteria

- `ru` cannot silently omit a key present in `en`, and a new locale has a documented validation path.
- Russian plural categories produce correct forms for 1, 2, 5, 11, and 21.
- An unknown key never becomes visible in production and is detectable in development/CI.
- Route payload tests show that unrelated domains are not serialized for a route.
- The shared selector exposes both flag graphic and localized text, has a programmatic name, and is reusable without separate language-control implementations.

### Targeted validation

```bash
cd apps/web && npm run lint && npm run test -- --run tests/unit/i18n
```

### Commit

```bash
git add apps/web/src/lib/{i18n,components} apps/web/tests/unit/i18n apps/web/package.json .github/workflows/quality.yml
git commit -m "feat(web): Harden translation catalogs"
```

[↑ Back to top](#top)

## I18N-006: Localize authenticated workspace and responsive web UI

**Status:** DONE
**Priority:** P1
**Depends on:** I18N-004, I18N-005

**Exact scope:** Replace remaining presentation strings in authenticated workspace, login/invitation/access flows, components, dialogs, notifications, accessibility attributes, titles, and formatting with the shared web catalog and error-code presentation layer.

**Files:**

- Modify `apps/web/src/routes/workspace/+page.svelte`, `apps/web/src/routes/select-family/+page.svelte`, `apps/web/src/routes/invite/parent/+page.svelte`, and `apps/web/src/routes/+error.svelte`.
- Modify user-facing files under `apps/web/src/lib/features/workspace/`, `apps/web/src/lib/components/`, `apps/web/src/lib/stores/`, and `apps/web/src/lib/services/` found by `rg --glob '*.{svelte,ts}' 'title>|aria-|placeholder|confirm\(|toast|alert\(' apps/web/src`.
- Modify the corresponding `apps/web/src/lib/i18n/messages/{en,ru}/` domains.
- Create or modify focused unit/component/E2E tests in `apps/web/tests/`.

**Goal:** The logged-in web app presents the configured family language consistently on desktop and mobile, including screen-reader copy, validation, errors, dialogs, and document metadata. Only a family administrator sees the shared flag-based family-language setting.

### Required changes

1. Localize visible labels, empty/loading/error/retry states, tooltips, placeholders, aria labels/descriptions, confirmations, toasts, titles, and non-indexed route metadata.
2. Route API `errorCode` and params through the catalog, while retaining user-entered names/descriptions unchanged and safely interpolated.
3. Replace manual dates/numbers/coin strings with the shared formatter; do not translate stable status/API identifiers.
4. Keep controls keyboard-accessible with visible focus and 44px touch targets after RU/EN expansion; check narrow 320px layout for wrapping/overflow.
5. Place the shared flag-based selector in the family administrator settings surface. Show the chosen flag and localized language name as family-wide state to all parents/children; do not render a personal language control for them.

### Out of scope

- Telegram Mini App components and static public marketing pages.
- Translation of family-created catalog/task/reward names.

### Acceptance criteria

- A family administrator changes the language with the shared flag-based selector; after reload, an ordinary parent and child in the same family receive that language and cannot choose a different one.
- UI, error, and accessibility strings appear in the selected language; no raw API codes or translation keys are shown.
- At 320px and desktop widths, expanded English/Russian labels do not hide primary actions or cause horizontal page overflow.
- Keyboard navigation and accessible names remain present for icon-only or modal controls.

### Targeted validation

```bash
cd apps/web && npm run lint && npm run test && npm run build && npm run test:e2e -- tests/e2e/workspace-entry.spec.ts tests/e2e/workspace-access.spec.ts tests/e2e/workspace-invitations.spec.ts
```

### Commit

```bash
git add apps/web/src/routes apps/web/src/lib/{features/workspace,components,stores,services,i18n} apps/web/tests
git commit -m "feat(web): Localize workspace experience"
```

[↑ Back to top](#top)

## I18N-007: Localize public pages and multilingual SEO

**Status:** DONE
**Priority:** P1
**Depends on:** I18N-005

**Exact scope:** Replace the static single-language public-site delivery with locale-aware canonical pages and SEO metadata, while preserving public page content and URL continuity through explicit redirects where needed.

**Files:**

- Modify `apps/web/src/routes/+page.server.ts`, `apps/web/src/hooks.server.ts`, and `apps/web/src/routes/sitemap.xml/+server.ts`.
- Create locale-aware public routes/components under `apps/web/src/routes/[locale]/` or the smallest compatible route structure after inspecting SvelteKit route matching.
- Migrate content from `apps/web/static/public/{index,how,tasks,rewards,parents,faq}.html` into the chosen catalog/components; retain static assets under `apps/web/static/public/assets/`.
- Modify `apps/web/src/routes/robots.txt/+server.ts` and create public-route E2E/response tests.

**Goal:** Public content is available at canonical English and Russian URLs with localized title, description, OpenGraph, canonical, `hreflang`, and sitemap entries, plus a clear flag-based language selector.

### Required changes

1. Serve `/en/...` and `/ru/...` public pages through SSR using the existing locale path utilities; canonicalize anonymous legacy/bare URLs according to the ADR without intercepting Telegram launch parameters.
2. Add per-locale title, meta description, OpenGraph, canonical, alternate (`hreflang`, including `x-default`), and sitemap output. Keep authenticated and invitation pages `noindex`.
3. Preserve equivalent visual/assets content; migration must not convert image alt text or accessibility copy into unlocalized literals.
4. Test old `/public/*.html` behavior explicitly: either retain a permanent compatible path or redirect it safely with no duplicate indexed content.
5. Use the shared flag-based selector in the public header/navigation. It changes the locale URL, shows a flag and localized language name, and remains accessible to keyboard and screen-reader users.

### Out of scope

- Translating screenshots, changing marketing claims, or adding CMS-managed content.
- Localizing the Telegram Mini App.

### Acceptance criteria

- Each public page has distinct EN/RU canonical and alternate links, localized metadata, and a correct `lang` attribute.
- Sitemap contains both locale variants and no stale static-only canonical URLs.
- Direct, cookie/header-selected, and shared locale URLs are stable; Telegram `tgWebAppStartParam` still reaches the Mini App.
- The public RU/EN selector shows a meaningful flag graphic with a text/programmatic language name; it is not a text-only dropdown or a flag-only icon.
- Public page keyboard navigation, images, and responsive layout remain functional in both locales.

### Targeted validation

```bash
cd apps/web && npm run lint && npm run test && npm run build && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- tests/e2e/public-i18n.spec.ts
```

**Execution note (2026-08-25):** The Public E2E command owns its preview server via `PLAYWRIGHT_USE_PREVIEW=true`. A macOS `bootstrap_check_in ... Permission denied` failure occurs before Playwright starts and cannot be fixed in application code; it requires the test process to run outside the Codex macOS sandbox.

### Commit

```bash
git add apps/web/src/routes apps/web/src/lib/i18n apps/web/static/public apps/web/tests/e2e/public-i18n.spec.ts
git commit -m "feat(web): Localize public site routes"
```

[↑ Back to top](#top)

## I18N-008: Localize the Telegram Mini App

**Status:** DONE
**Priority:** P1
**Depends on:** I18N-003, I18N-004, I18N-005

**Exact scope:** Remove Russian-only routing and presentation assumptions from the Telegram Mini App, then migrate its dashboard, catalog, request, account, and notification components to the shared catalog and formatters.

**Files:**

- Modify `apps/web/src/routes/telegram/+page.svelte`, `apps/web/src/routes/telegram/+layout.svelte`, and `apps/web/src/routes/telegram/dashboard/+page.svelte`.
- Modify user-facing files in `apps/web/src/lib/components/telegram/`, `apps/web/src/lib/features/telegram/`, `apps/web/src/lib/telegram/`, and `apps/web/src/lib/services/telegram*.ts` found by the task's hard-coded-string inventory.
- Modify `apps/web/src/lib/i18n/messages/{en,ru}/{app,tasks,admin,errors}.ts`.
- Modify Telegram Mini App E2E and visual-regression fixtures under `apps/web/tests/e2e/`.

**Goal:** Bot → Mini App opens in its family language. A family administrator changes that common language through the shared flag-based selector, and later bot messages for every family member use the new value.

### Required changes

1. Use the configured family language from authenticated bootstrap, not a URL prefix forced to RU; preserve Mini App SDK initialization, theme, signed init data, deep links, and existing API contracts.
2. Translate all user-facing Mini App copy: tabs, forms, filters, sort controls, empty/error/retry states, confirmations, requests, limits, analytics, toasts, screen-reader labels, and title metadata.
3. Use the shared formatting/plural helpers; retain task/reward/child names as user data and do not manufacture translations for them.
4. Render the shared flag-based selector only where the existing Mini App exposes family-admin settings; ordinary parents and children see the common selected language but cannot change it. Ensure error-code rendering handles a newer backend and stale cached frontend bundle safely.

### Out of scope

- Bot message localization or Telegram callback protocol changes.
- A visual redesign unrelated to label expansion/accessibility repairs.

### Acceptance criteria

- An EN and RU Telegram fixture renders translated primary flows with the same underlying callback/API identifiers.
- A family-admin flag selection in the Mini App survives reload/auth exchange, becomes the common language for all family roles, and is used for later bot delivery.
- Desktop Telegram, mobile Telegram, and a narrow 320px viewport keep actionable controls reachable with 44px touch targets.
- No signed init data, auth, or theme behavior regresses.

### Targeted validation

```bash
cd apps/web && npm run lint && npm run test && npm run build && npm run test:e2e -- tests/e2e/telegram-auth.spec.ts tests/e2e/telegram-consistency.spec.ts tests/e2e/telegram-parent.spec.ts tests/e2e/telegram-child.spec.ts tests/e2e/visual-regression-miniapp.spec.ts
```

### Commit

```bash
git add apps/web/src/routes/telegram apps/web/src/lib/{components/telegram,features/telegram,telegram,services,i18n} apps/web/tests/e2e
git commit -m "feat(web): Localize Telegram Mini App"
```

### CHECKPOINT (2026-08-25)

- Completed: family-locale-aware Telegram metadata, localized confirmation and CSV error presentation, shared date/frequency formatting, neutral group-navigation defaults, and EN/RU-compatible Mini App E2E expectations.
- Completed: moved semantic-graphic categories and labels from Telegram helpers into the EN/RU catalog, localized graphic picker/form output, localized catalog-group icon labels, and aligned `hooksServer.test.ts` with the current bounded JSON diagnostic contract.
- Changed files: Telegram semantic-graphic helper/forms/picker/group manager, EN/RU app catalogs, semantic-graphic unit test, and `tests/unit/hooksServer.test.ts`.
- Verification: lint passed with 46 pre-existing warnings; full Vitest passed 212/212; production build passed; `git diff --check` passed. Existing focused Telegram E2E evidence remains 19/19 from the previous checkpoint.
- Resolved blockers: no remaining hard-coded semantic-graphic/group presentation labels in the changed Telegram surfaces; the server-diagnostic expectation mismatch is resolved.

[↑ Back to top](#top)

## I18N-009: Localize Telegram Bot menus and recipient delivery

**Status:** DONE
**Priority:** P1
**Depends on:** I18N-002, I18N-005

**Exact scope:** Move Telegram Bot presentation from Russian Java concatenation into backend locale bundles/resolvers and resolve every outbound message and keyboard label for the delivery recipient.

**Files:**

- Create `apps/backend/src/main/resources/telegram_messages.properties` and `apps/backend/src/main/resources/telegram_messages_ru.properties`, or extend the existing backend message resources with a clearly bounded `telegram.*` namespace.
- Create a backend Telegram message resolver in `apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/`.
- Modify `TelegramCopy.java`, `TelegramOutcomeCopy.java`, `TelegramCoinCopy.java`, `TelegramRecent.java`, `TelegramMenuText.java`, `BotKeyboardFactory.java`, `TelegramMessageUpdateHandler.java`, and other copy owners found by `rg --glob '*.java' '[А-Яа-яЁё]' apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/notification/TelegramNotificationComposer.java`, `TelegramDeliveryPlanner.java`, and the delivery processor that sends composed messages.
- Modify/add tests under `apps/backend/src/test/java/com/sashplatonov/earnit/kids/telegram/` and `integration/TelegramCrossChannelIntegrationTest.java`.

**Goal:** Bot command responses, keyboards, callbacks, onboarding, request outcomes, and notifications render in the recipient family's configured language without leaking user data or changing callback semantics.

### Required changes

1. Introduce a message resolver accepting a normalized locale, typed key, and parameters. Use named placeholders and `MessageFormat`/locale-aware number/date formatting, including Russian plural forms; avoid string concatenation for grammar-sensitive copy.
2. Resolve the delivery recipient's family locale at send/edit time, not from a request header, Telegram hint, event creator, or current request thread. Keep outbox events language-neutral and idempotency keys unchanged.
3. Keep callback data/commands as stable ASCII identifiers; translate only labels and message text. Reply-keyboard matching must continue to recognize the translated labels or, preferably, use callback navigation where available.
4. Escape all interpolated user content according to the Telegram parse mode in `TelegramBotApiClient`; do not add raw HTML/Markdown translation support.
5. Update `/start`, no-child, deep-link, inline/reply-keyboard, cancellation, error, empty-state, and scheduled/outbox copy. Do not add a bot `/language` command: language is the administrator-controlled family setting.

### Out of scope

- Sending email, adding a translation service, or translating user-created task/reward titles.
- Changing bot authorization or callback signing.

### Acceptance criteria

- Every parent and child in a RU family receives RU notifications; every parent and child in an EN family receives EN notifications for the same event type.
- Bot → Mini App and Mini App → Bot language consistency is covered after a family administrator changes the shared language.
- Callback data remains stable across locales and translated reply keyboards do not break navigation.
- Names/descriptions containing markup characters are rendered as text and cannot alter Telegram message markup.

### Targeted validation

```bash
cd apps/backend && ./mvnw -B -ntp -Dtest=TelegramBotServiceImplTest,TelegramMenuBuilderTest,TelegramDeliveryPlannerTest,TelegramOutboxProcessorTest,TelegramCrossChannelIntegrationTest test
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram apps/backend/src/main/resources/telegram_messages*.properties apps/backend/src/test/java/com/sashplatonov/earnit/kids/telegram apps/backend/src/test/java/com/sashplatonov/earnit/kids/integration/TelegramCrossChannelIntegrationTest.java docs/internationalization-backlog.md docs/.backlog-execution-state.md
git commit -m "feat(i18n): Localize Telegram bot delivery"
```

[↑ Back to top](#top)

## I18N-010: Enforce localization regression coverage and contributor workflow

**Status:** DONE
**Priority:** P2
**Depends on:** I18N-003, I18N-006, I18N-007, I18N-008, I18N-009

**Exact scope:** Make localization checks repeatable in CI and document the contributor/release workflow. This task verifies implementation outcomes; it must not become a broad refactor.

**Files:**

- Modify `.github/workflows/quality.yml` only to run the established focused catalog/cross-channel checks.
- Modify `README.md`, `apps/web/docs/ARCHITECTURE.md`, and `apps/backend/docs/ARCHITECTURE.md` if I18N-001 documentation needs implementation-level commands.
- Create/update focused tests under `apps/web/tests/{unit,e2e}/` and `apps/backend/src/test/java/com/sashplatonov/earnit/kids/{i18n,integration,telegram}/`.

**Goal:** A change to a translation or locale flow is reviewed and validated with deterministic checks, and contributors can add a normal string or a new locale without rediscovering cross-channel rules.

### Required changes

1. Add CI coverage for catalog parity/placeholder validation, locale normalization/fallback, API stable-code behavior, public EN/RU metadata and flag selector accessibility, administrator-first family-language setup, Mini App bootstrap, and recipient-family bot delivery.
2. Document the normal string workflow, naming/review rules, formatters, adding a locale, controlled fallback, and cache-compatible deployment behavior. State that frontend/backend skew must show a safe generic error rather than a raw key/code.
3. Keep checks high-signal: do not add a generic hard-coded string scanner that flags content, tests, comments, IDs, or user data without reliable ownership.
4. Record test evidence boundaries: local checks do not prove deployment cache invalidation, Telegram client rendering, or physical-device behavior; list those manual release checks separately.

### Out of scope

- CI/CD redesign, deployment, or a new translation-management system.
- Any code feature not needed to make checks/documentation executable.

### Acceptance criteria

- CI fails for a missing locale key or invalid placeholder contract and passes for valid fallback behavior.
- Focused E2E proves EN/RU public pages, the accessible public flag selector, administrator-controlled family language inherited by parents/children, and Bot ↔ Mini App consistency.
- Documentation tells a new contributor where frontend, bot, and server-generated strings belong and how to add `sr` later.
- The final full gates pass; manually verified release/client/device checks are reported separately rather than implied by local tests.

### Targeted validation

```bash
cd apps/backend && ./mvnw -B -ntp verify
cd apps/web && npm run lint && npm run test && npm run build && npm run test:e2e
git diff --check
```

### Commit

```bash
git add .github/workflows/quality.yml README.md apps/web/docs/ARCHITECTURE.md apps/backend/docs/ARCHITECTURE.md apps/web/tests apps/backend/src/test
git commit -m "test(i18n): Cover cross-channel localization"
```

[↑ Back to top](#top)
