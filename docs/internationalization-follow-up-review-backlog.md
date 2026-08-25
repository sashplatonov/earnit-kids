# Internationalization Follow-up Review - Catalog Locale Backlog

## Goal

Make the parent-facing ready catalogs for Tasks and Rewards display their
built-in catalog content and built-in group names in the active family's
persisted locale. The Tasks and Rewards group-management UI must retain its
existing localized controls and must not display a Russian built-in catalog
group in an English family. A user-created group name remains user data and is
never machine-translated when the family changes language.

## Review basis

- `apps/web/src/routes/telegram/+page.svelte` receives the persisted family
  locale from Telegram authentication and installs the corresponding i18n
  runtime before `TelegramRoleResolver` mounts the parent UI.
- UI labels for `TelegramParentCatalog` and `TelegramGroupManager` already use
  `app.telegram.readyCatalog` and `app.telegram.groupManager` translation
  keys. The visible leak is catalog data: `baseData.json` currently contains
  Russian `title`, `comment`, and `groupName` values for every catalog entry.
- `FamilyReadResource#getBaseData()` currently returns one locale-neutral raw
  `BaseDataService` map. `TelegramReadyCatalog` renders its `title`, `comment`,
  and `groupName` verbatim, while `mapGroupKeyToFamily()` contains a second
  hard-coded Russian mapping of catalog group keys to family group names.
- The selected family locale is stored in `FamilyEntity.locale`, and
  `FamilyReadResource` already uses `FamilyRepository` to resolve it for
  `/api/family/locale`. It is therefore the authoritative source for the
  authenticated `/api/base-data` response; browser language and locale cookies
  must not choose catalog copy.

## Architectural decisions

- Keep `groupKey`, catalog IDs, prices/coins, filters, age limits, frequency,
  semantic graphics, and telemetry keys locale-neutral and stable. Only
  catalog presentation fields (`title`, optional `comment`, and `groupName`)
  are selected by the family locale.
- Add locale variants to the static catalog source and project them at the
  authenticated backend boundary. The REST response keeps the existing flat
  template shape so `serverContract.ts`, catalog sorting/filtering, and the
  parent catalog components do not grow a client-side translation resolver.
- Resolve a missing persisted family locale as the established English default.
  If a locale variant is missing in a hand-edited persisted catalog file, use
  the base/default presentation field deterministically and emit no partially
  localized object. Validation must make missing EN/RU variants a test failure
  for the bundled catalog.
- Built-in catalog groups are identified only by `groupKey`. Reuse one
  locale-aware group-label resolver for automatic destination matching and
  creation. Existing and newly typed family group names are persisted verbatim;
  do not add a migration or attempt to infer/rename custom groups, historical
  items, group order, or hidden-group preferences.
- Do not add query locale parameters, duplicate catalog endpoints, browser
  `Intl`/translation tables for catalog records, or a second catalog source.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | I18N-FOLLOWUP-004 | P1 | - | The server must provide one family-scoped localized catalog contract before the Mini App can render or map its groups consistently. |
| 2 | I18N-FOLLOWUP-005 | P1 | I18N-FOLLOWUP-004 | The parent catalog and group flows can consume and protect the localized contract without duplicating locale logic. |

## I18N-FOLLOWUP-004: Project the ready catalog in the persisted family locale

**Status:** DONE
**Priority:** P1
**Depends on:** -

**Exact scope:** Localize the authenticated `/api/base-data` catalog response
for its requesting family. Cover both `catalog.tasks` and `catalog.rewards`,
including each entry's title, optional explanatory comment, and built-in group
label, without changing catalog identity or business metadata.

**Files:**

- Modify `apps/backend/src/main/resources/baseData.json`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/application/database/BaseDataService.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/api/resource/FamilyReadResource.java`.
- Create a focused locale-projection type beside `BaseDataService` if a
  top-level mapper is needed; do not use a nested Java type.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/platform/application/database/BaseDataServiceTest.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/api/resource/FamilyResourceTest.java`.
- Modify `apps/web/tests/unit/catalogContent.test.ts` only to validate the new
  static catalog-source shape and EN/RU completeness; it remains the
  cross-repository content gate.

**Goal:**

An authenticated English family receives English task/reward catalog titles,
comments, and built-in group names; a Russian family receives their Russian
equivalents from the same catalog IDs and ordering.

### Outcome

The current raw `BaseDataService#getBaseData()` response makes the catalog
Russian regardless of the `FamilyEntity.locale` that the Mini App has already
selected. The endpoint will instead return a per-request presentation
projection while retaining one cached locale-neutral source catalog.

### Architectural decision

`BaseDataService` owns loading, persistence, cache refresh, and projection of
the static catalog; `FamilyReadResource` owns authentication and obtains the
family locale from its existing repository dependency. The database continues
to own only the family locale, not a copied catalog or a translation cache.
The response remains backward-compatible for consumers expecting flat
`title`, `comment`, `groupName`, and `groupKey` fields.

### Required changes

1. Convert every built-in `catalog.tasks` and `catalog.rewards` presentation
   record in `baseData.json` to hold complete EN and RU variants while keeping
   its stable ID, `groupKey`, numeric limits, filters, tags, and graphics
   unchanged. Retain the non-catalog legacy `tasks` data shape unless it is
   consumed by the same ready-catalog response.
2. Extend the base-data service with a locale-aware read that projects only
   the visible catalog fields for `FamilyLocale.en` or `FamilyLocale.ru`; keep
   raw-file persistence and its five-minute cache locale-neutral. Define and
   test one deterministic fallback for old persisted files that contain only
   the pre-existing flat fields.
3. In `getBaseData`, resolve the authenticated family through the existing
   `FamilyRepository`, default a null legacy locale to EN, and return the
   projected map. Preserve the current 401 behavior and return the existing
   appropriate family-not-found result rather than accepting a client locale.
4. Add focused tests for EN and RU projection across one task and one reward,
   proving equal IDs/group keys/numeric metadata and different localized
   presentation fields. Cover null-locale EN fallback and a legacy flat
   persisted source without locale variants.
5. Update the static-content test so every catalog entry has both locale
   variants, non-blank localized titles and group names, matching structural
   metadata, and the existing age/content/unique-ID invariants.

### Out of scope

- Translating arbitrary tasks, rewards, comments, or group names created by a
  family.
- Changing family-locale update authorization, Telegram Bot copy, public SSR
  catalogs, catalog CRUD, pricing, ordering, or database schema.
- Introducing a locale query parameter or changing the endpoint path.

### Acceptance criteria

- `/api/base-data` for an EN family contains English title/comment/group-name
  values for both a task and a reward; the same request for RU returns their
  Russian variants.
- The EN and RU payloads have identical catalog IDs, `groupKey` values,
  numeric/frequency/age metadata, and list order.
- A legacy family with no saved locale receives EN, independently of browser
  locale or request headers.
- A hand-edited legacy persisted `baseData.json` without locale variants stays
  readable through the documented fallback and does not corrupt/cache a
  localized response.
- Unauthorized callers remain rejected and no caller can request another
  locale through an API parameter.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME=/Users/sash/.sdkman/candidates/java/25.0.2-amzn ./mvnw -B -ntp -Dtest=BaseDataServiceTest,FamilyResourceTest test
cd apps/web && npm run test -- --run tests/unit/catalogContent.test.ts
```

### Commit

```bash
git add apps/backend/src/main/resources/baseData.json apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/application/database/BaseDataService.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/application/database/<localized-catalog-projector>.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/api/resource/FamilyReadResource.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/platform/application/database/BaseDataServiceTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/api/resource/FamilyResourceTest.java apps/web/tests/unit/catalogContent.test.ts
git commit -m "feat(i18n): Localize ready catalog content"
```

## I18N-FOLLOWUP-005: Use localized catalog groups throughout the parent Mini App

**Status:** TODO
**Priority:** P1
**Depends on:** I18N-FOLLOWUP-004

**Exact scope:** Make the parent Tasks and Rewards ready-catalog flows use the
localized server templates for rows, search, details, bulk summaries, and
automatic built-in group selection. Keep the existing localized group-manager
labels, 44px controls, ordering, and custom-group behavior intact.

**Files:**

- Modify `apps/web/src/lib/telegram/services/catalogFilter.ts`.
- Modify `apps/web/src/lib/components/telegram/TelegramParentCatalog.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramReadyCatalog.svelte`
  only if its current props/search pipeline needs the locale-aware group
  resolver passed explicitly.
- Modify `apps/web/src/lib/components/telegram/TelegramCatalogGroupMap.svelte`
  only if its create/match display requires a resolved built-in label.
- Modify `apps/web/tests/unit/catalogFilter.test.ts`.
- Modify `apps/web/tests/e2e/telegram-parent.spec.ts`.

**Goal:**

After an EN or RU parent opens either ready catalog, all built-in catalog text
and built-in group labels use the selected family locale; adding a template
reuses the matching built-in group in that locale or offers to create its
localized label, without changing a custom group name.

### Outcome

`TelegramReadyCatalog` already renders response fields directly, but
`mapGroupKeyToFamily()` duplicates a Russian `groupKey -> groupName` table.
That makes automatic mapping and the "create group" choice disagree with an
English localized catalog. The UI needs one locale-aware canonical resolver,
not another presentation dictionary.

### Architectural decision

The API payload remains the visible-copy source. `groupKey` is used only to
recognize built-in semantic groups; the shared client helper resolves the
current localized label from the template/payload and may recognize the known
legacy Russian label for matching existing pre-localization groups. It must
never relabel, rename, or translate a free-form persisted group.

### Required changes

1. Replace the hard-coded Russian mapping in `mapGroupKeyToFamily()` with a
   contract that accepts the localized catalog group label (and, where needed,
   known legacy built-in aliases) from the current template data. Keep
   matching deterministic and scoped by task versus reward catalog flows.
2. Ensure add-one, add-many, bulk summaries, the group map sheet, catalog row
   search, and details all use the localized fields returned by the server;
   no component may keep a Russian fallback for a built-in key.
3. Preserve duplicate detection by `sourceCatalogItemId` and current catalog
   item IDs, group ordering, hidden groups, telemetry `catalogGroupKey`, and
   add/save payload shape. Adding an EN template must store the localized
   selected/default group name exactly as chosen; a manually named group must
   remain unchanged.
4. Extend unit tests for group matching and catalog search with EN and RU
   template data, including a legacy Russian built-in group in an existing
   family. Extend the parent Mini App E2E fixture to run both locales and
   assert localized Task/Reward catalog rows, group-map/create labels, and
   no page-level horizontal overflow at 320px.
5. Keep `TelegramGroupManager`'s existing `$i18n` labels as the only owner of
   its system UI copy. Add no migration, renaming pass, or component-local
   translation table for saved groups.

### Out of scope

- Retroactively translating custom family tasks, rewards, comments, group
  names, history, request data, group order, or hidden-group settings.
- Redesigning parent/child catalogs, changing filters/sort order, or changing
  catalog telemetry and endpoint contracts beyond task 004's presentation
  projection.
- Adding languages beyond EN and RU.

### Acceptance criteria

- With a persisted EN family locale, parent Task and Reward catalog headings,
  rows, search matches, details, bulk-group summary, and built-in create-group
  choice are English; the corresponding RU session is Russian.
- The same template ID/group key produces the appropriate localized built-in
  group label without a hard-coded Russian mapping in client code.
- An existing custom group remains byte-for-byte unchanged after opening the
  catalog, adding a template, and reloading; an existing legacy Russian
  built-in group is reused rather than duplicated for the documented matching
  case.
- On a 320px viewport, the localized catalog/group sheet has no horizontal
  overflow; visible buttons remain keyboard reachable with a visible focus
  indicator and at least 44px target dimensions.
- Existing source-ID duplicate prevention, filter/sort behavior, save payloads,
  and task/reward separation continue to pass their focused tests.

### Targeted validation

```bash
cd apps/web && npm run test -- --run tests/unit/catalogFilter.test.ts tests/unit/catalogContent.test.ts
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- --grep "parent Mini App"
cd apps/web && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/src/lib/telegram/services/catalogFilter.ts apps/web/src/lib/components/telegram/TelegramParentCatalog.svelte apps/web/src/lib/components/telegram/TelegramReadyCatalog.svelte apps/web/src/lib/components/telegram/TelegramCatalogGroupMap.svelte apps/web/tests/unit/catalogFilter.test.ts apps/web/tests/e2e/telegram-parent.spec.ts
git commit -m "fix(web): Localize parent catalog groups"
```
