# EarnIt Kids I18n String Inventory

<a id="top"></a>

## Table of Contents

- [EarnIt Kids I18n String Inventory](#earnit-kids-i18n-string-inventory)
  - [Table of Contents](#table-of-contents)
  - [Purpose](#purpose)
  - [Domain Taxonomy](#domain-taxonomy)
  - [Hotspot Inventory](#hotspot-inventory)
  - [Dynamic Template Rules](#dynamic-template-rules)
  - [Pluralization Targets](#pluralization-targets)
  - [Non-Translatable Data](#non-translatable-data)
  - [Implementation Notes](#implementation-notes)

## 🎯 Purpose

This inventory turns the current hardcoded-string surfaces into implementation-ready domain ownership. It is the source document for extraction, translation key placement, and test coverage planning.

Rules for this inventory:

- keys are semantic and stable
- nesting depth stays at three levels or less
- domains reflect product meaning, not component names
- dynamic values are templates, not string concatenation

[↩ Back to toc](#table-of-contents)

## 🏷️ Domain Taxonomy

The approved domain groups are:

| Domain | Scope |
| --- | --- |
| `public` | home, about, faq, marketing feature pages, public CTA copy |
| `auth` | login, register, verify, reset-password, auth-related notices |
| `app` | shared authenticated shell, nav, toasts, generic in-app actions |
| `shop.parent` | parent reward management, approval, purchase-review language |
| `shop.child` | child reward browsing, request, purchase, balance-sensitive strings |
| `history` | history list, status text, audit-like user copy |
| `analytics` | achievements, charts, streaks, stats explanations |
| `tasks` | task list, task editor, completion, request flows |
| `admin` | parent-side family management, rules, limits, settings |
| `superadmin` | super-admin panel, family inspection, operational UI |
| `backend` | server-generated user-facing errors and response details |
| `blog` | blog list, article shell, article metadata, empty states |
| `email` | verification, recovery, and future outbound message templates |

Notes:

- The current runtime implementation already covers `public`, `auth`, `app`, `blog`, `errors`, and a first backend catalog.
- `shop.parent`, `shop.child`, `history`, `analytics`, `tasks`, `admin`, `superadmin`, and `email` remain separate inventory domains even where code is temporarily grouped under `app` today.

[↩ Back to toc](#table-of-contents)

## Hotspot Inventory

Each hotspot below is assigned to its target domain group and extraction status.

| Surface | Primary files | Domain | Dynamic templates | Status |
| --- | --- | --- | --- | --- |
| Public landing and marketing | `apps/web/src/routes/+page.svelte`, `about/+page.svelte`, `faq/+page.svelte`, `features/[slug]/+page.svelte`, `src/lib/components/PublicTopNav.svelte` | `public` | CTA labels, metrics, SEO text | extracted |
| Public/auth SEO and route metadata | `apps/web/src/routes/+page.svelte`, `about/+page.svelte`, `faq/+page.svelte`, `features/[slug]/+page.svelte`, `blog/+page.svelte`, `blog/[slug]/+page.svelte`, `login/+page.svelte`, `verify/+page.svelte`, `reset-password/+page.svelte` | `public`, `auth`, `blog` | canonical paths, alternates, titles, descriptions | extracted |
| Auth forms and recovery | `apps/web/src/routes/login/+page.svelte`, `verify/+page.svelte`, `reset-password/+page.svelte` | `auth` | success/error toasts, placeholders, async states | extracted |
| Authenticated shell and shared nav | `apps/web/src/lib/components/app/AppShell.svelte`, `AppHeader.svelte`, `AppNav.svelte`, `LocaleSwitcher.svelte`, `src/lib/app/routes.ts` | `app` | balance text, award prompts, logout copy | extracted |
| Blog shell and list/article metadata | `apps/web/src/routes/blog/+page.svelte`, `blog/[slug]/+page.svelte`, `src/lib/server/blog.ts` | `blog` | dates, fallback tags, article CTA | extracted |
| Locale routing and canonical redirects | `apps/web/src/hooks.server.ts`, `src/hooks.ts`, `src/lib/i18n/config.ts`, `src/lib/app/routes.ts` | `public`, `auth`, `app`, `blog` | route examples and alternate URLs | extracted |
| Shared formatters | `apps/web/src/lib/i18n/formatters.ts`, `tests/unit/formatters.test.ts` | `app`, `shop.parent`, `shop.child`, `history`, `analytics` | dates, numbers, coin pluralization | extracted |
| Analytics section UI and view-model | `apps/web/src/lib/components/app/sections/AnalyticsSection.svelte`, `analyticsViewModel.ts`, `tests/unit/analyticsViewModel.test.ts`, `tests/e2e/analytics.spec.ts` | `analytics` | chart labels, streak copy, empty states, locale-aware date formatting | extracted |
| Requests and history sections | `apps/web/src/lib/components/app/sections/RequestsSection.svelte`, `HistorySection.svelte`, `requestDetails.ts`, `historyDetails.ts`, `tests/unit/requestDetails.test.ts`, `tests/unit/historyDetails.test.ts` | `history` | status copy, empty states, helper fallbacks, budget summaries, locale-aware date/number formatting | extracted |
| Tasks and rewards sections with modals | `apps/web/src/lib/components/app/sections/TasksSection.svelte`, `ShopSection.svelte`, `GroupOrderEditor.svelte`, `modals/TaskModal.svelte`, `modals/ShopModal.svelte`, `tests/e2e/helpers.ts`, `tests/e2e/child-shop.spec.ts`, `tests/e2e/app-sections.spec.ts` | `tasks`, `shop.parent`, `shop.child` | task/reward actions, group ordering, modal labels, empty states, locale-aware counts and frequency copy | extracted |
| Admin utilities and shared app child controls | `apps/web/src/lib/components/app/sections/RulesSection.svelte`, `LimitsSection.svelte`, `SettingsSection.svelte`, `CatalogSection.svelte`, `FriendsSection.svelte`, `ChildSwitcher.svelte`, `modals/AddChildModal.svelte` | `admin`, `app` | child selection, add-child flow, settings/security labels, catalog filters, friend search, modal toasts | extracted |
| Auth backend messages | `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/AuthServiceImpl.java`, `resource/AuthResource.java`, `config/AuthFilter.java` | `backend`, `auth` | invalid credentials, blocked account, CSRF, verification errors | extracted |
| Transactional family action backend messages | `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyActionServiceImpl.java`, exception mappers | `backend`, `tasks`, `shop.parent`, `shop.child`, `history` | limit windows, balance errors, request status | extracted |
| Backend analytics recommendation labels | `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyServiceImpl.java` | `backend`, `analytics` | recommendation headings, stale-task nudges, fallback labels | extracted |
| Family management backend responses | `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyServiceImpl.java`, `resource/FamilyResource.java` | `backend`, `admin`, `tasks`, `shop.parent`, `shop.child`, `history` | validation errors, child settings, search/add friend results | extracted |
| Super-admin backend responses | `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/SuperAdminService.java`, `SuperAdminCredentialsService.java`, `resource/SuperAdminResource.java`, `resource/PushResource.java`, `resource/WsTokenResource.java` | `superadmin`, `backend` | family inspection, password update, operational feedback | extracted |
| Super-admin UI | `apps/web/src/routes/super-admin/+page.svelte` | `superadmin` | dashboard labels, tabs, family actions, database/system status | extracted |
| Email and async notifications | future outbound templates and async capture surfaces | `email`, `backend` | verification and reset flows | pending |

[↩ Back to toc](#table-of-contents)

## 📐 Dynamic Template Rules

Dynamic strings must be modeled as templates whenever they include:

- counts
- coin amounts or money-like values
- dates or times
- child names or family names
- route-dependent labels
- operation outcomes with variable payloads

Current template examples:

| Template type | Example | Domain |
| --- | --- | --- |
| coin delta | `+{amount} coins awarded` | `app` |
| request limit | `The request limit for {target} is already used for today. Next reset at {resetAt}.` | `backend` |
| status CTA | `Try it with your family` | `blog`, `public` |
| route metadata | alternate hrefs generated from current path | `public`, `auth`, `blog`, `app` |

String concatenation is not allowed for user-visible copy when template variables are involved.

[↩ Back to toc](#table-of-contents)

## 🔢 Pluralization Targets

Pluralization treatment is mandatory for:

- coin counts
- history entry counts
- request counts
- analytics counters and streak values
- shop quantities and rate-limit windows when humanized in UI

Rules:

- Use `Intl.PluralRules`.
- Do not hand-roll Russian plural logic.
- Keep English as the fallback source string.

[↩ Back to toc](#table-of-contents)

## 🚫 Non-Translatable Data

The following values must never be moved into dictionaries:

- user-generated blog/article titles stored as content data
- database identifiers, family ids, child ids, request ids
- slugs, URLs, JWT claims, CSRF tokens
- log messages and telemetry fields
- machine error codes such as `TASK_REQUEST_LIMIT_REACHED`
- raw backend entity field names

[↩ Back to toc](#table-of-contents)

## 📝 Implementation Notes

Sequencing notes for the remaining backlog:

1. Keep extracting remaining authenticated section strings into their target domains without collapsing everything into `app`.
2. Keep routing newly touched backend request/admin messages through `BackendMessages` so regressions do not reintroduce hardcoded copy.
3. Introduce `email` domain templates before localizing outbound mail and async notification jobs.

[↑ Back to top](#top)
