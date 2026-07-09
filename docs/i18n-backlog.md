# Internationalization Backlog

<a id="top"></a>

Assumptions confirmed on 2026-04-21:

- Scope: the active SvelteKit app in `apps/web` plus user-facing backend responses that feed it.
- Migration type: full migration of the existing product, not only new screens.
- Locales: `en` and `ru`.
- Default and only fallback locale: `en`.
- URL policy: every locale has a prefix; English uses `/en/*`, Russian uses `/ru/*`; the bare root `/` redirects to `/en/`.
- Resolution order: URL prefix -> locale cookie -> `Accept-Language` -> `en`.
- Current state: no existing i18n foundation; strings are hardcoded across frontend and backend.

<a id="table-of-contents"></a>

## 📚 Table of Contents

- [Goal](#goal)
- [Confirmed Constraints](#confirmed-constraints)
- [Current Hotspots](#current-hotspots)
- [Recommended Order](#recommended-order)
- [Parallel Tracks](#parallel-tracks)
- [Backlog Tasks](#backlog-tasks)
- [Definition of Done](#definition-of-done)

## 🎯 Goal

Deliver a production-safe i18n migration for the SvelteKit coin shop with these hard rules:

- English is the default locale and the only fallback locale.
- Missing locale selection must render English.
- Missing Russian keys must render English.
- `/en/shop` means English, `/ru/shop` means Russian.
- The bare root `/` must redirect to `/en/` immediately on the server before any render.
- Locale must be resolved before render, not after hydration.
- The app must never crash because of a missing translation.
- All public pages, the parent and child shop flows, and the super-admin panel must be fully localized in both English and Russian.

[↑ Back to top](#top)

## 📌 Confirmed Constraints

- Keep English as the source-of-truth dictionary.
- Keep translation keys semantic, stable, and grouped by domain, not by component.
- Keep nesting depth at three levels or less.
- Use `Intl.DateTimeFormat`, `Intl.NumberFormat`, and `Intl.PluralRules` for formatting.
- Do not hand-roll Russian plural rules.
- Do not store translations inside components.
- Do not create one component per locale.
- Do not use `any` in translation APIs.
- Do not eagerly load all dictionaries for all roles.
- Test coverage must reach at least 80 percent across unit, integration, and end-to-end suites after all tasks are complete.

[↑ Back to top](#top)

## 🔎 Current Hotspots

- SSR and locale entry points: `apps/web/src/app.html`, `apps/web/src/hooks.server.ts`, `apps/web/src/app.d.ts`, `apps/web/src/lib/server/session.ts`, `apps/web/src/lib/types/session.ts`.
- Route and redirect policy: `apps/web/src/routes/[...path]/+page.server.ts`, `apps/web/src/routes/+page.server.ts`, `apps/web/src/routes/login/+page.server.ts`, `apps/web/src/routes/app/+layout.server.ts`, `apps/web/src/routes/app/[section]/+page.server.ts`, `apps/web/src/lib/app/routes.ts`.
- Public SEO and copy: `apps/web/src/routes/+page.svelte`, `apps/web/src/routes/about/+page.svelte`, `apps/web/src/routes/faq/+page.svelte`, `apps/web/src/routes/features/[slug]/+page.svelte`, `apps/web/src/routes/blog/+page.svelte`, `apps/web/src/routes/blog/[slug]/+page.svelte`, `apps/web/src/routes/login/+page.svelte`, `apps/web/src/routes/verify/+page.svelte`, `apps/web/src/routes/reset-password/+page.svelte`.
- Authenticated UI and shop flow: `apps/web/src/lib/components/app/**/*`, `apps/web/src/lib/app/routes.ts`, `apps/web/src/lib/services/api.ts`, `apps/web/src/lib/components/app/sections/analyticsViewModel.ts`.
- Blog content loader: `apps/web/src/lib/server/blog.ts`, `apps/web/data/blog/*`.
- Backend user-facing messages: `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/AuthServiceImpl.java`, `FamilyServiceImpl.java`, `FamilyActionServiceImpl.java`, `SuperAdminService.java`, `SuperAdminCredentialsService.java`, `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/AuthResource.java`, `FamilyResource.java`, `SuperAdminResource.java`, `apps/backend/src/main/java/com/sashplatonov/earnit/kids/exception/ConstraintViolationExceptionMapper.java`.
- Backend cookie and request context surfaces: `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/CookieBuilder.java`, `apps/web/src/lib/server/proxy.ts`.

[↑ Back to top](#top)

## 🧭 Recommended Order

1. Finish the locale contract and string inventory before touching runtime behavior.
2. Build the typed i18n foundation and SSR locale resolution before extracting page copy.
3. Fix routing, redirects, formatters, and SEO before migrating large UI zones.
4. Move frontend UI migration and backend message localization in parallel only after the contract is stable.
5. Keep shop, notifications, and super-admin behind the foundation because they depend on precise formatting, async locale capture, and bundle isolation.
6. Run SEO, fallback, and regression checks continuously, then gate release with a final multilingual pass.

[↑ Back to top](#top)

## 🔀 Parallel Tracks

- Sequential foundation: `I18N-01` -> `I18N-02` -> `I18N-03` -> `I18N-04` -> `I18N-05` -> `I18N-06`.
- After `I18N-04` and `I18N-05`, run `I18N-07`, `I18N-10`, and `I18N-13` in parallel.
- After `I18N-06`, run `I18N-08`, `I18N-11`, and `I18N-12` in parallel.
- Run `I18N-09` after `I18N-07` starts, because blog SEO and content structure must align.
- Run `I18N-14` after `I18N-04` and `I18N-05`, then use it to unlock `I18N-16`.
- Run `I18N-15` after `I18N-03`, `I18N-04`, and `I18N-13`.
- Start `I18N-17` once `I18N-03` exists, but keep the final release gate last.

[↑ Back to top](#top)

<a id="backlog-tasks"></a>

## 📝 Backlog Tasks

| ID | Priority | Depends on | Can run with | Main outcome |
| --- | --- | --- | --- | --- |
| I18N-01 | P0 | none | none | Frozen locale contract and URL policy |
| I18N-02 | P0 | I18N-01 | none | String inventory and key taxonomy |
| I18N-03 | P0 | I18N-01, I18N-02 | none | Typed i18n foundation |
| I18N-04 | P0 | I18N-03 | none | SSR locale resolution before render |
| I18N-05 | P0 | I18N-04 | none | Locale-aware routing and redirects |
| I18N-06 | P0 | I18N-03 | I18N-05 | Shared formatters and pluralization |
| I18N-07 | P1 | I18N-04, I18N-05 | I18N-10, I18N-13 | Public SEO localization |
| I18N-08 | P1 | I18N-03, I18N-04, I18N-06 | I18N-09 | Public and auth UI localization |
| I18N-09 | P1 | I18N-07 | I18N-08 | Bilingual blog content workflow |
| I18N-10 | P1 | I18N-03, I18N-04, I18N-05 | I18N-07, I18N-13 | App shell, nav, modals, toasts |
| I18N-11 | P1 | I18N-06, I18N-10 | I18N-12 | Shared authenticated sections |
| I18N-12 | P1 | I18N-06, I18N-10, I18N-13 | I18N-11 | Shop and money-sensitive flows |
| I18N-13 | P1 | I18N-01, I18N-03, I18N-04 | I18N-07, I18N-10 | Localized backend response catalog |
| I18N-14 | P1 | I18N-04, I18N-05 | I18N-10 | Locale switcher and preference UX |
| I18N-15 | P2 | I18N-03, I18N-04, I18N-13 | I18N-16 | Super-admin UI with bundle isolation |
| I18N-16 | P2 | I18N-13, I18N-14 | I18N-15 | Emails, notifications, async locale capture |
| I18N-17 | P0 | I18N-03 onward | all implementation tasks | Tests, SEO gates, rollout checks, 80 % coverage |

### I18N-01 - Freeze the locale contract and URL policy

Priority: P0

Primary files and surfaces: `apps/web/src/app.html`, `apps/web/src/routes/[...path]/+page.server.ts`, `apps/web/src/lib/app/routes.ts`, `apps/web/src/routes/app/+layout.server.ts`, `apps/web/src/routes/app/[section]/+page.server.ts`.

Description: lock the behavior that every other task depends on. Every locale, including English, is accessed through an explicit URL prefix. The bare root redirects to English immediately on the server.

Do:

- Define the supported locale list as `en` and `ru` only.
- Freeze the canonical policy: English uses `/en/*`, Russian uses `/ru/*`, and the bare root `/` issues a server-side redirect to `/en/` before any page renders.
- Freeze the resolution order: URL prefix, locale cookie, `Accept-Language`, English.
- Define how legacy aliases such as `/login.html`, `/verify.html`, `/reset-password.html`, `/about.html`, and `/faq.html` map to the prefixed canonical routes, for example `/en/login`, `/ru/login`.
- Define which surfaces are never translated: database content, logs, ids, machine error codes, and operational telemetry.
- Confirm that all of the following areas are in scope for full localization: all public pages, the parent shop flow, the child shop flow, and the super-admin panel.

Acceptance:

- The repo contains one written locale contract that matches these rules exactly.
- Example routes are written down for public, authenticated, parent, child, and super-admin flows in both `/en/*` and `/ru/*` forms.
- The contract explicitly states that the root `/` redirects to `/en/` and never serves content directly.
- The contract explicitly states that missing Russian keys fall back to English and never throw.
- The contract explicitly states that Russian is never used as fallback for any other locale.

### I18N-02 - Build a string inventory and key taxonomy

Priority: P0

Depends on: I18N-01

Primary files and surfaces: `apps/web/src/routes/**/*`, `apps/web/src/lib/components/app/**/*`, `apps/web/src/lib/services/api.ts`, `apps/backend/src/main/java/com/sashplatonov/earnit/kids/{resource,service}/**/*.java`.

Description: convert the current hardcoded-string sprawl into an implementation-ready inventory grouped by domain. The inventory must cover public pages, the full parent and child shop experience, and the super-admin panel without exception.

Do:

- Inventory all user-visible strings in public pages, authenticated pages, parent shop views, child shop views, super-admin panel, modals, toasts, placeholders, `title`, `alt`, and `aria-*` attributes.
- Inventory backend user-facing messages returned through `OperationResult.failure`, `ErrorResponse.of`, and `SimpleResponse.error`.
- Classify dynamic strings that contain counts, dates, amounts, or variables so they can become message templates instead of raw string concatenation.
- Group keys by domain: `public`, `auth`, `app`, `shop.parent`, `shop.child`, `history`, `analytics`, `tasks`, `admin`, `superadmin`, `backend`, `blog`, and `email`.
- Reject component-based key namespaces and reject unstable keys derived from the current text.
- Mark every string that contains a coin count or a money-like value for pluralization treatment.

Acceptance:

- Every hotspot named in this document is assigned to a domain group.
- The inventory separates translatable UI text from data, logs, and identifiers.
- Dynamic strings are marked for ICU-style or formatter-based interpolation.
- Key depth stays at three levels or less.
- Parent shop, child shop, and super-admin strings each have their own domain group in the inventory.

### I18N-03 - Add a typed i18n foundation

Priority: P0

Depends on: I18N-01, I18N-02

Primary files and surfaces: `apps/web/package.json`, `apps/web/src/app.d.ts`, `apps/web/src/lib/types/session.ts`, new `apps/web/src/lib/i18n/**/*`.

Description: create one typed translation layer for both server and client so the rest of the migration does not rely on ad-hoc string lookups.

Do:

- Create a dedicated `apps/web/src/lib/i18n/` module with locale constants, message loaders, type-safe translation accessors, and formatting wrappers.
- Make English the required base dictionary and Russian a partial overlay that can safely fall back to English.
- Split dictionaries by domain so public, app, shop parent, shop child, admin, super-admin, and backend-facing copies can be loaded independently.
- If an external library is introduced, prefer a typed solution and keep the app-level API inside `src/lib/i18n` so the rest of the codebase does not depend on vendor-specific calls.
- Add guardrails so missing keys fail in development but resolve to English at runtime.
- Expose a `LOCALES` constant, a `DEFAULT_LOCALE` constant, and a `Locale` type derived from the constant so the rest of the codebase never uses raw strings for locale values.

Acceptance:

- Both server and client code can request translations through one typed API.
- English dictionaries are complete enough to act as the only fallback source.
- Russian dictionaries can omit keys without causing runtime errors.
- Anonymous routes do not eagerly load admin or super-admin dictionaries.
- The `Locale` type is derived from the constant list and enforced at compile time.

### I18N-04 - Resolve locale in SSR before render and make `<html lang>` dynamic

Priority: P0

Depends on: I18N-03

Primary files and surfaces: `apps/web/src/hooks.server.ts`, `apps/web/src/app.html`, `apps/web/src/app.d.ts`, `apps/web/src/lib/server/session.ts`, `apps/web/src/lib/types/session.ts`, `apps/web/src/routes/+layout.server.ts`.

Description: make locale part of the request lifecycle so the first HTML response is already correct, and handle the bare-root redirect before any page renders.

Do:

- Add a locale resolver in `hooks.server.ts` that applies the contract from `I18N-01`.
- Issue a server-side redirect from `/` to `/en/` in `hooks.server.ts` before any route handler runs.
- Store the resolved locale in `event.locals.locale` and expose it through `PageData`.
- Replace the hardcoded `<html lang="ru">` in `app.html` with a request-aware value sourced from `event.locals.locale`.
- Ensure the same locale is available to root layouts and route `load` functions before components render.
- Make error responses default to English when no explicit locale can be resolved.

Acceptance:

- A request to `/` receives a server-side redirect to `/en/` with no page render.
- `/en/*` renders English on the first response without client-side correction.
- `/ru/*` renders Russian on the first response without client-side correction.
- `<html lang>` matches the resolved locale on normal and error responses.
- No locale flash occurs after hydration.

### I18N-05 - Make routing, redirects, and route helpers locale-aware

Priority: P0

Depends on: I18N-04

Primary files and surfaces: `apps/web/src/lib/app/routes.ts`, `apps/web/src/routes/[...path]/+page.server.ts`, `apps/web/src/routes/+page.server.ts`, `apps/web/src/routes/login/+page.server.ts`, `apps/web/src/routes/app/+layout.server.ts`, `apps/web/src/routes/app/[section]/+page.server.ts`.

Description: prevent the current route helpers and legacy redirects from stripping or breaking locale context. Every redirect must land on the correct prefixed route.

Do:

- Update route helper utilities so every path is built and parsed with an explicit locale prefix, both `/en/*` and `/ru/*`.
- Preserve locale during redirects to login, authenticated default sections, parent shop, child shop, and super-admin pages.
- Redirect legacy `.html` aliases to the localized canonical prefixed route, for example `/login.html` → `/en/login` or `/ru/login` depending on resolved locale.
- Preserve query strings during locale-aware redirects.
- Ensure the future language switcher can keep the current path or the closest equivalent path by swapping only the prefix segment.
- Route helpers must never produce a bare unprefixed path as a result.

Acceptance:

- Redirects never drop the locale prefix unexpectedly.
- Legacy aliases resolve to canonical prefixed localized routes without loops.
- Authenticated route helpers can build both `/en/*` and `/ru/*` paths correctly.
- Switching locale swaps the prefix segment and keeps the rest of the path intact.
- No route helper returns an unprefixed path.

### I18N-06 - Centralize formatting and pluralization

Priority: P0

Depends on: I18N-03

Can run with: I18N-05

Primary files and surfaces: `apps/web/src/lib/components/app/sections/HistorySection.svelte`, `apps/web/src/lib/components/app/sections/RequestsSection.svelte`, `apps/web/src/lib/components/app/sections/analyticsViewModel.ts`, `apps/web/src/routes/blog/+page.svelte`, `apps/web/src/routes/blog/[slug]/+page.svelte`, `apps/web/src/routes/super-admin/+page.svelte`, new `apps/web/src/lib/i18n/formatters.ts`.

Description: remove hardcoded `ru-RU` formatting and create one locale-aware formatting layer for dates, counts, and money-like values used across the shop, history, limits, and super-admin views.

Do:

- Add shared date, number, and currency helpers based on `Intl`.
- Add a shared coin plural helper backed by `Intl.PluralRules` that handles all Russian plural categories: `one`, `few`, `many`, and `other`.
- Replace direct `toLocaleDateString`, `toLocaleString`, and `new Intl.DateTimeFormat('ru-RU', ...)` usage outside the shared helper layer.
- Normalize money-limit and balance formatting so the same amount is rendered consistently across parent shop, child shop, history, limits, and super-admin views.
- Keep coin display semantics separate from currency display semantics.

Acceptance:

- No raw `ru-RU` formatting remains in UI code outside the shared formatter module.
- English coin forms use `one` and `other` correctly.
- Russian coin forms render correctly for the values `1`, `2`, `5`, `11`, `21`, and `24`.
- Date and number formatting changes when locale changes.
- The formatter module is the single source used by shop, history, analytics, and super-admin.

### I18N-07 - Localize public SEO metadata, canonical, hreflang, and Open Graph

Priority: P1

Depends on: I18N-04, I18N-05

Can run with: I18N-10, I18N-13

Primary files and surfaces: `apps/web/src/routes/+page.svelte`, `apps/web/src/routes/about/+page.svelte`, `apps/web/src/routes/faq/+page.svelte`, `apps/web/src/routes/features/[slug]/+page.svelte`, `apps/web/src/routes/blog/+page.svelte`, `apps/web/src/routes/blog/[slug]/+page.svelte`, `apps/web/src/routes/login/+page.svelte`, `apps/web/src/routes/verify/+page.svelte`, `apps/web/src/routes/reset-password/+page.svelte`.

Description: fix the most SEO-sensitive part of the migration first for public traffic and shareable pages. Because every locale now has a prefix, canonical and hreflang logic must reflect the `/en/*` and `/ru/*` structure.

Do:

- Replace hardcoded titles, descriptions, canonicals, and Open Graph copy with locale-aware values.
- Emit `hreflang` links for English and Russian, with `x-default` pointing to the `/en/` variant.
- Ensure canonicals point to the correct prefixed localized route and never produce an unprefixed URL.
- Localize `robots` and related meta only where text exists; keep technical directives unchanged.
- Build one reusable helper for public route head metadata instead of repeating ad-hoc head logic.

Acceptance:

- Each public page emits localized `title`, `meta description`, canonical, and OG values.
- `hreflang="en"` points to `/en/…`, `hreflang="ru"` points to `/ru/…`, and `hreflang="x-default"` points to `/en/…`.
- All canonicals use prefixed paths; no unprefixed canonical appears.
- Locale is reflected in `<html lang>` and head metadata together.

### I18N-08 - Localize public pages, auth pages, and attribute strings

Priority: P1

Depends on: I18N-03, I18N-04, I18N-06

Can run with: I18N-09

Primary files and surfaces: `apps/web/src/routes/+page.svelte`, `apps/web/src/routes/about/+page.svelte`, `apps/web/src/routes/faq/+page.svelte`, `apps/web/src/routes/features/[slug]/+page.svelte`, `apps/web/src/routes/blog/+page.svelte`, `apps/web/src/routes/blog/[slug]/+page.svelte`, `apps/web/src/routes/login/+page.svelte`, `apps/web/src/routes/verify/+page.svelte`, `apps/web/src/routes/reset-password/+page.svelte`, `apps/web/src/lib/components/PublicTopNav.svelte`.

Description: extract and translate the visible text and accessible attributes that form the public and auth experience. Every public page must be fully localized in both English and Russian.

Do:

- Replace hardcoded visible strings with translation keys.
- Replace translatable `placeholder`, `title`, `aria-label`, and `alt` values with locale-aware values.
- Convert dynamic auth status messages and validation hints into translated templates.
- Keep content coming from markdown or backend payloads separate from UI chrome.
- Remove mixed-language pages where English metadata and Russian body copy currently coexist.
- Ensure that every public page is fully readable and complete in both `/en/*` and `/ru/*` without any untranslated fragments.

Acceptance:

- Public and auth routes no longer contain user-facing hardcoded literals except content-source fields and technical tokens.
- Accessibility attributes change with locale.
- Auth status and error states are readable and correct in both locales.
- Missing Russian UI keys render English instead of blank text or runtime errors.
- No public page has untranslated fragments in either locale.

### I18N-09 - Define a bilingual blog content workflow

Priority: P1

Depends on: I18N-07

Can run with: I18N-08

Primary files and surfaces: `apps/web/src/lib/server/blog.ts`, `apps/web/src/routes/blog/+page.server.ts`, `apps/web/src/routes/blog/[slug]/+page.server.ts`, `apps/web/data/blog/*`.

Description: blog content needs a deliberate bilingual strategy instead of treating markdown like normal UI copy. Blog routes must follow the same `/en/*` and `/ru/*` prefix structure as the rest of the site.

Do:

- Split blog content by locale: `data/blog/en/*` and `data/blog/ru/*`.
- Keep the same slug for both language versions whenever possible so `/en/blog/my-post` and `/ru/blog/my-post` describe the same article.
- Localize blog title, summary, tags, and listing chrome separately from markdown body content.
- Decide how to handle untranslated Russian posts: the route may fall back to English content for product continuity, but it must not advertise a full Russian translation in `hreflang` unless Russian content actually exists.
- Update the blog loader to resolve content by locale first and English second.
- Blog listing and article routes must sit under `/en/blog/*` and `/ru/blog/*` respectively.

Acceptance:

- Blog list and article routes sit under `/en/blog/*` and `/ru/blog/*`.
- English remains available when Russian article content is missing.
- SEO metadata does not falsely claim a localized article when only English content exists.
- Date formatting on blog pages follows the active locale.

### I18N-10 - Localize the authenticated shell, navigation, modals, toasts, and client service errors

Priority: P1

Depends on: I18N-03, I18N-04, I18N-05

Can run with: I18N-07, I18N-13

Primary files and surfaces: `apps/web/src/lib/app/routes.ts`, `apps/web/src/lib/components/app/**/*`, `apps/web/src/lib/services/api.ts`.

Description: remove the app-wide hardcoded scaffolding so the rest of the authenticated screens can migrate incrementally. The shell must work correctly under both `/en/app/*` and `/ru/app/*`.

Do:

- Replace hardcoded section labels and titles in `apps/web/src/lib/app/routes.ts` with translatable metadata.
- Localize the app shell, headers, bottom navigation, shared buttons, and modal titles.
- Localize toast copy and confirmation prompts.
- Replace frontend service-layer fallback strings such as delete and network failures in `apps/web/src/lib/services/api.ts` with translation-backed messages.
- Keep route helpers and title builders locale-aware so document titles stay correct under both locale prefixes.

Acceptance:

- Shared authenticated chrome renders in English under `/en/app/*` and in Russian under `/ru/app/*`.
- Route titles are derived from translation keys, not literals.
- Client fallback errors no longer bypass the translation layer.
- Common modals and toasts do not mix languages.

### I18N-11 - Localize shared authenticated sections

Priority: P1

Depends on: I18N-06, I18N-10

Can run with: I18N-12

Primary files and surfaces: `apps/web/src/lib/components/app/sections/AnalyticsSection.svelte`, `TasksSection.svelte`, `RequestsSection.svelte`, `HistorySection.svelte`, `FriendsSection.svelte`, `RulesSection.svelte`, `SettingsSection.svelte`, related modals under `apps/web/src/lib/components/app/modals/*`.

Description: migrate the main role-shared pages after the shell and formatter layers exist. These sections must be fully localized in both English and Russian, including both parent and child role views.

Do:

- Replace hardcoded copy for empty states, action labels, status chips, and helper text.
- Convert dynamic strings that join dates, group names, and counts into translated templates.
- Replace history and request status label switches with translation-backed status maps.
- Localize modal form labels and placeholders for tasks, children, and shared settings.
- Keep user-generated names and comments raw.
- Verify that parent-role views and child-role views are each fully localized in both languages.

Acceptance:

- Shared sections no longer contain hardcoded user-visible strings outside non-translatable data.
- Status labels and empty states change correctly with locale.
- Grouped history and request displays use locale-aware dates and labels.
- Both parent and child paths are fully readable and consistent in both English and Russian.

### I18N-12 - Localize the shop, coins, limits, and money-sensitive flows

Priority: P1

Depends on: I18N-06, I18N-10, I18N-13

Can run with: I18N-11

Primary files and surfaces: `apps/web/src/lib/components/app/sections/ShopSection.svelte`, `HistorySection.svelte`, `LimitsSection.svelte`, `apps/web/src/routes/super-admin/+page.svelte`, `apps/web/src/lib/services/api.ts`, backend request and approval flows served by `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyActionServiceImpl.java`.

Description: migrate the most precision-sensitive user flows where coins, limits, statuses, and balances matter. Both the parent shop view and the child shop view must be fully localized in English and Russian.

Do:

- Localize buy, request, approve, reject, insufficient-balance, and limit-related messages for both parent and child roles.
- Use shared formatters for balances, monthly limits, and money-limit displays.
- Use `Intl.PluralRules` for coin labels and summaries in both parent and child contexts.
- Make transaction states explicit and unambiguous in both locales.
- Ensure any legal or purchase-related text is written and reviewed manually, not machine-generated.
- Confirm that the child shop experience is as fully localized as the parent shop experience.

Acceptance:

- Parent and child shop CTAs, statuses, and errors are clear in both locales.
- Coin and money-like values are formatted by locale rather than hardcoded Russian conventions.
- Russian plural forms for coins are correct in shop and history surfaces for both roles.
- English remains the fallback when a Russian shop key is missing.
- The child shop view contains no untranslated fragments in either locale.

### I18N-13 - Build a localized backend response catalog and locale propagation path

Priority: P1

Depends on: I18N-01, I18N-03, I18N-04

Can run with: I18N-07, I18N-10

Primary files and surfaces: `apps/web/src/lib/server/proxy.ts`, `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/AuthServiceImpl.java`, `FamilyServiceImpl.java`, `FamilyActionServiceImpl.java`, `SuperAdminService.java`, `SuperAdminCredentialsService.java`, `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/AuthResource.java`, `FamilyResource.java`, `SuperAdminResource.java`, `apps/backend/src/main/java/com/sashplatonov/earnit/kids/exception/ConstraintViolationExceptionMapper.java`.

Description: remove mixed-language and hardcoded backend messages so frontend users receive locale-correct responses from the server layer too.

Do:

- Forward the resolved locale from the web layer to the backend through `Accept-Language` and an explicit `X-App-Locale` header.
- Introduce a backend message catalog keyed by stable error codes rather than raw strings.
- Replace direct hardcoded message literals in user-facing `OperationResult.failure`, `ErrorResponse.of`, and `SimpleResponse.error` paths.
- Keep technical logs and purely operational alerts untranslated when they are not user-facing.
- Keep error codes stable so frontend logic does not depend on translated text.

Acceptance:

- User-facing API responses are localized by the resolved request locale.
- English is returned when no locale is available or a Russian message is missing.
- Error codes remain stable across locales.
- The existing English-only and Russian-only inconsistencies are removed.

### I18N-14 - Add locale switching and preference persistence

Priority: P1

Depends on: I18N-04, I18N-05

Can run with: I18N-10

Primary files and surfaces: `apps/web/src/lib/components/PublicTopNav.svelte`, `apps/web/src/lib/components/app/**/*`, `apps/web/src/hooks.server.ts`, `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/CookieBuilder.java` if backend cookie support is needed.

Description: give users a reliable way to switch languages without breaking path continuity or falling back to the wrong locale later. Switching locale must swap the prefix segment of the current URL and never produce an unprefixed path.

Do:

- Add a language switcher to the public shell and authenticated shell.
- Write the selected locale to a cookie that matches the resolution order from `I18N-01`.
- Update the URL by swapping the locale prefix segment, for example `/en/app/shop` becomes `/ru/app/shop` and vice versa.
- Avoid full-page reloads when the route can be updated client-side safely.
- Keep English as the fallback when the cookie is missing or invalid.
- Never produce an unprefixed URL as the output of a locale switch.

Acceptance:

- Switching language swaps the locale prefix and updates the cookie consistently.
- The rest of the current path is preserved exactly.
- Subsequent SSR responses honor the stored cookie when the URL prefix is unrecognized.
- Invalid locale cookies do not break rendering and fall back to English.
- The switcher is present and functional in both the public and authenticated shells.

### I18N-15 - Localize super-admin UI with bundle isolation

Priority: P2

Depends on: I18N-03, I18N-04, I18N-13

Can run with: I18N-16

Primary files and surfaces: `apps/web/src/routes/super-admin/+page.svelte`, admin-only sections under `apps/web/src/lib/components/app/sections/*`, role guards in `apps/web/src/routes/super-admin/+page.server.ts` and `apps/web/src/routes/app/+layout.server.ts`.

Description: migrate the full super-admin UI without leaking admin-only dictionaries into anonymous or child bundles. The super-admin panel must be fully localized in both English and Russian.

Do:

- Split admin and super-admin translations into role-gated dictionary chunks.
- Load those dictionaries only after role verification on the server.
- Localize all tabs, table labels, forms, save statuses, validation messages, and action confirmations in the super-admin interface.
- Keep user names, catalog content, logs, and other user-provided data untranslated.
- Verify that admin-only texts are not shipped to unauthenticated users.
- Confirm that the full super-admin panel has no untranslated fragments in either English or Russian.

Acceptance:

- Super-admin UI is fully localized in English under `/en/super-admin` and in Russian under `/ru/super-admin`.
- Anonymous and non-admin bundles do not preload super-admin translation data.
- User-generated content is rendered as-is.
- System logs and technical strings may stay English where appropriate.
- No super-admin UI fragment is left untranslated in either locale.

### I18N-16 - Localize emails, notifications, and async event-time language capture

Priority: P2

Depends on: I18N-13, I18N-14

Can run with: I18N-15

Primary files and surfaces: auth verification and password-reset flows in `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/AuthServiceImpl.java`, async notifications in `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/WebSocketNotificationService.java`, and any future email template or sender layer discovered during implementation.

Description: cover the async surfaces where language must be decided at event time, not from whatever locale the browser currently happens to use.

Do:

- Find or add the email template layer for verification and password reset flows.
- Add English and Russian templates for subjects and bodies.
- Store or resolve the user locale needed for transaction-time and async communication.
- Ensure websocket-driven UI messages are rendered from localized frontend message keys or send localized payloads intentionally if the backend must own the copy.
- Keep super-admin operational Telegram alerts outside the public translation bundle; translate them only if product wants admin-facing localized operations.

Acceptance:

- Verification and password reset emails exist in English and Russian.
- Async messages are not rendered in the wrong language because the browser session changed later.
- English remains the fallback when the preferred locale cannot be resolved.
- Admin locale and end-user locale can differ without corrupting user-facing communication.

### I18N-17 - Add test gates, SEO validation, rollout checks, and enforce 80 percent coverage

Priority: P0

Depends on: starts after I18N-03, finishes last

Primary files and surfaces: `apps/web/tests/unit/**/*`, `apps/web/tests/e2e/**/*`, backend tests under `apps/backend/src/test/**/*`, existing route tests such as `apps/web/tests/unit/appRoutes.test.ts`.

Description: make the migration safe to finish and safe to keep. Test coverage across unit, integration, and end-to-end suites must reach and maintain at least 80 percent by the time this task closes.

Do:

- Extend unit tests for locale-aware route helpers, locale resolution order, the bare-root redirect to `/en/`, and formatter behavior.
- Add unit tests for English fallback when Russian keys are missing.
- Add targeted tests for Russian plural rules covering the values `1`, `2`, `5`, `11`, `21`, and `24`, and for English singular-plural behavior.
- Add SEO tests or snapshot checks for canonical, `hreflang`, `x-default` pointing to `/en/`, OG tags, and `<html lang>`.
- Add Playwright end-to-end flows that cover English and Russian on: all public pages, all auth flows, parent shop flow, child shop flow, shared authenticated sections, and super-admin panel.
- Add a hardcoded-string regression gate for user-visible surfaces, excluding markdown content, generated files, logs, and technical data.
- Measure combined coverage after each implementation task closes and block the release gate if coverage falls below 80 percent.
- Include a final manual pass checklist that covers: all public pages in both locales, parent shop, child shop, super-admin bundle isolation, locale switcher behavior, and the bare-root redirect.

Acceptance:

- Combined unit, integration, and end-to-end coverage is at or above 80 percent.
- Automated tests prove URL prefix routing, the bare-root redirect to `/en/`, cookie fallback, header fallback, and English fallback behavior.
- SEO checks pass for all multilingual public routes with prefixed canonicals.
- Parent shop, child shop, and super-admin flows behave correctly in both locales.
- The release checklist includes a final manual pass for all localized surfaces and confirms super-admin bundle isolation.

[↑ Back to top](#top)

## ✅ Definition of Done

- The bare root `/` redirects to `/en/` on the server before any page renders.
- English is the default locale everywhere, including SSR, cookies, and unknown routes.
- Missing Russian strings render English without runtime errors.
- `/en/shop` and `/ru/shop` both work and render the correct locale.
- All public pages are fully localized in both English and Russian with no untranslated fragments.
- The parent shop flow is fully localized in both English and Russian.
- The child shop flow is fully localized in both English and Russian.
- The super-admin panel is fully localized in both English and Russian.
- Public pages emit localized metadata, prefixed canonical URLs, and `hreflang` tags with `x-default` pointing to `/en/`.
- `<html lang>` always matches the resolved locale.
- User-visible backend messages are localized while technical logs remain safe and stable.
- Coin pluralization and amount formatting are correct in English and Russian.
- Super-admin translations are not shipped to anonymous users.
- The app does not mix languages on the same page unless the source content itself is intentionally untranslated.
- Combined test coverage is at or above 80 percent across unit, integration, and end-to-end suites.
- Tests cover locale prefix routing, the bare-root redirect, fallback, pluralization, SEO, and the highest-risk flows.

[↑ Back to top](#top)
