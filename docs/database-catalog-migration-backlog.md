# Database Catalog Migration - Implementation Backlog

## Goal

Make the ready task and reward catalog a database-owned, Flyway-seeded resource. The authenticated `GET /api/base-data` response must retain its localized catalog shape for the Telegram Mini App, but no runtime, build, or test path may depend on `catalog-seed-reference.json`, `baseData.json`, or the generator script.

## Architectural decisions

- The global ready catalog is application reference data, distinct from the user-editable family `tasks` and `shop_items` copies.
- Use one typed catalog-item table. `item_type` distinguishes tasks from rewards; SQL constraints enforce the appropriate amount column. This avoids duplicate localization and ordering implementations.
- Store English and Russian title, comment, and group name in the Flyway seed. A catalog service selects the family locale and returns scalar strings in the current `catalog.tasks` and `catalog.rewards` API contract.
- Keep `/api/base-data`, its authorization behavior, existing external IDs, all metadata, and `sourceCatalogItemId` copy semantics. Do not change web bootstrap, Telegram catalog UI, or `TelegramReplyKeyboardNavigator`.
- The migration must work on PostgreSQL and the H2 PostgreSQL-mode test database. Do not add a replacement JSON, Java, or frontend fixture source of truth.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | CAT-DB-001 | P1 | - | Establish the persisted source of truth and seed every current template. |
| 2 | CAT-DB-002 | P1 | CAT-DB-001 | Add a single localized read model for the existing API. |
| 3 | CAT-DB-003 | P1 | CAT-DB-002 | Switch the endpoint and delete the complete file-based chain. |

## CAT-DB-001: Create and seed the localized catalog migration

**Status:** DONE
**Priority:** P1  
**Depends on:** -

**Exact scope:**

Create the next Flyway migration after `V52__add_family_locale.sql`, migrate every Russian task/reward from `catalog-seed-reference.json`, and seed a complete reviewed English counterpart for each item. The attached seed is Russian-only; English strings must not be generated from generic category templates.

**Files:**

- Create `apps/backend/src/main/resources/db/migration/V53__seed_localized_catalog.sql`.
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/infrastructure/persistence/LocalizedCatalogMigrationTest.java`.
- Read-only source while executing: `catalog-seed-reference.json` is the canonical Russian task/reward list; `apps/backend/src/main/resources/baseData.json` supplies current IDs and metadata only. Both are deleted by CAT-DB-003.

**Goal:**

The database contains a deterministic complete task and reward catalog with English and Russian copy immediately after Flyway runs.

### Outcome

Fresh PostgreSQL and H2 databases contain every current `ct-*` and `cr-*` item with the same external ID, order, and business metadata, plus meaningful Russian and English copy.

### Architectural decision

Create a catalog reference table with a surrogate primary key, a unique existing external ID, item type, localized text columns, and the current group/graphic/frequency/age/difficulty/active/sort metadata. Enforce type, positive frequency, valid age range, and task-versus-reward amount invariants in SQL. Derive `tags` from `group_key` later unless the source data proves it requires independent storage.

### Required changes

1. Define portable schema and ordering indexes for active rows by `item_type` and `sort_order`; do not seed family `tasks` or `shop_items`.
2. Translate every Russian title, comment, and group label into natural English with the same action/result meaning, age appropriateness, emoji, and frequency intent. Do not reuse current generic strings such as `Complete a ... goal` with an ID suffix as catalog copy.
3. Insert all task and reward templates with stable IDs, reviewed English/Russian text, group/graphic keys, frequency, age range, difficulty, active state, and sort order.
4. Add an H2 migration test that asserts schema constraints, unique IDs, complete locale fields, task/reward counts for ages 6–8, 9–11, and 12–14, and representative English/Russian items of each kind.

### Out of scope

- Reading the new table from HTTP.
- Altering family catalog copies, history, or `source_catalog_item_id`.
- Catalog copy editing or an admin catalog editor.

### Acceptance criteria

- A clean migration creates every current catalog template with its stable external ID.
- Each row has non-blank English and Russian title and group name; each English title is a meaningful translation of its paired Russian task/reward, not an ID-bearing or category-only template.
- A task has positive `coins` and no price; a reward has positive `price` and no coins.
- Each supported age bucket yields at least 20 task and 20 reward templates under the current overlap rule.
- The migration executes in PostgreSQL and H2 PostgreSQL mode.

### Targeted validation

```bash
cd apps/backend && ./mvnw -B -ntp -Dtest=LocalizedCatalogMigrationTest test
```

### Commit

```bash
git add apps/backend/src/main/resources/db/migration/V53__seed_localized_catalog.sql apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/infrastructure/persistence/LocalizedCatalogMigrationTest.java
git commit -m "feat(backend): seed localized catalog in database"
```

## CAT-DB-002: Add a localized database catalog read model

**Status:** TODO
**Priority:** P1  
**Depends on:** CAT-DB-001

**Exact scope:**

Add the entity, repository, service, and mapping that read the reference table and emit the current catalog object for a requested `FamilyLocale`.

**Files:**

- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/domain/model/catalog/CatalogItemEntity.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/domain/model/catalog/CatalogItemType.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/infrastructure/persistence/catalog/CatalogItemRepository.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/application/catalog/LocalizedCatalogService.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/application/catalog/LocalizedCatalogServiceImpl.java`.
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/application/catalog/LocalizedCatalogServiceImplTest.java`.
- Search anchor: `normalizeCatalogTemplate` in `apps/web/src/lib/services/serverContract.ts` defines the existing response field names.

**Goal:**

The backend returns deterministic, locale-projected catalog templates from the database.

### Outcome

For a requested locale, the service produces `catalog.tasks` and `catalog.rewards` with the current scalar frontend fields.

### Architectural decision

Persistence belongs in `family.infrastructure.persistence.catalog`; locale projection belongs in one family application service. Neither the resource nor web code may reconstruct catalog localization or metadata independently.

### Required changes

1. Map the new reference table with dedicated entity and enum; do not overload `TaskEntity` or `ShopItemEntity`.
2. Query active rows only in deterministic order by type, `sort_order`, and external-ID tie-breaker.
3. Map the existing fields: `id`, `title`, `comment`, `coins` or `price`, `groupKey`, `groupName`, `semanticGraphicKey`, frequency, age, difficulty, `tags`, `active`, and `sortOrder`.
4. Select the requested English/Russian columns and derive `tags` from `group_key`, retaining the UI duplicate-detection and family-copy flow.
5. Cover both locales, kind separation, inactive filtering, stable order, null comment handling, amount mapping, and string external IDs.

### Out of scope

- New REST routes, public catalog access, CRUD, or cache-policy changes.
- Frontend type changes or changes to family task/reward repositories.

### Acceptance criteria

- English and Russian outputs have identical IDs and metadata but selected localized title, comment, and group name.
- Inactive rows are absent; task and reward collections contain only their matching kind in source order.
- Task templates expose positive `coins`; reward templates expose positive `price`.
- The emitted shape is accepted unchanged by `normalizeCatalog` and has deterministic tags.

### Targeted validation

```bash
cd apps/backend && ./mvnw -B -ntp -Dtest=LocalizedCatalogServiceImplTest test
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/domain/model/catalog/CatalogItemEntity.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/domain/model/catalog/CatalogItemType.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/infrastructure/persistence/catalog/CatalogItemRepository.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/application/catalog/LocalizedCatalogService.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/application/catalog/LocalizedCatalogServiceImpl.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/application/catalog/LocalizedCatalogServiceImplTest.java
git commit -m "feat(backend): read localized catalog from database"
```

## CAT-DB-003: Switch the base-data endpoint and remove file catalog sources

**Status:** TODO
**Priority:** P1  
**Depends on:** CAT-DB-002

**Exact scope:**

Replace `BaseDataService` in `GET /api/base-data`, retain its HTTP contract, then delete the JSON source chain and tests that read it directly.

**Files:**

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/api/resource/FamilyReadResource.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/api/resource/FamilyResourceTest.java`.
- Modify `apps/web/tests/unit/catalogContent.test.ts` to validate an API fixture, or delete it only after its substantive assertions are covered by CAT-DB-001 and CAT-DB-002 tests.
- Delete `apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/application/database/BaseDataService.java`.
- Delete `apps/backend/src/test/java/com/sashplatonov/earnit/kids/platform/application/database/BaseDataServiceTest.java`.
- Delete `apps/backend/src/main/resources/baseData.json`.
- Delete `apps/backend/scripts/generate_catalog.py`.
- Delete `catalog-seed-reference.json`.
- Search anchors: `BaseDataService`, `baseData.json`, `generate_catalog`, and `catalog-seed-reference`.

**Goal:**

Every authenticated client receives the existing catalog contract from the database, with no file-based catalog dependency.

### Outcome

`GET /api/base-data` remains 401 without authentication, 404 for a missing family, and 200 with locale-specific catalogs for an existing family. It retains the legacy empty `tasks` list that `buildInitialState` currently normalizes.

### Architectural decision

`FamilyReadResource` remains the authentication and family-locale boundary and delegates all catalog construction to `LocalizedCatalogService`. Retain `/api/base-data`; do not add a compatibility route or a parallel resolver.

### Required changes

1. Replace the `BaseDataService` dependency/call while retaining English fallback for null locale and current status behavior.
2. Update resource tests to mock the new service and assert locale selection and the full response wrapper (`tasks: []`, `catalog.tasks`, `catalog.rewards`).
3. Relocate every substantive content assertion from the web test to migration/service coverage. Do not add a second static catalog fixture only for tests.
4. Delete the service, service test, bundled JSON, generator, and seed JSON; remove dead imports, configuration, and references.
5. Search tracked source after the switch. Keep existing web E2E route mocks because the API path and normalized contract are unchanged.

### Out of scope

- Renaming `/api/base-data`, deleting the legacy `tasks` response field, or changing SvelteKit bootstrap.
- Modifying family data, `source_catalog_item_id`, Telegram reply keyboards, public demo HTML, or locale UI.
- Editing already-applied Flyway migrations or deploying the change.

### Acceptance criteria

- `en`, `ru`, and null family locales return English, Russian, and English-fallback catalog copy respectively; unauthorized and missing-family behavior is unchanged.
- Web normalization accepts the response with no code change, and ready-catalog copy flows retain their IDs and metadata.
- No live source reference remains to `BaseDataService`, `baseData.json`, `generate_catalog`, or `catalog-seed-reference`.
- The deleted files are absent from the worktree and no generated JSON replacement is introduced.
- Backend migration/static analysis and web contract tests pass without suppressions or exclusions.

### Targeted validation

```bash
cd apps/backend && ./mvnw -B -ntp -Dtest=FamilyCommandResourceTest,LocalizedCatalogMigrationTest,LocalizedCatalogServiceImplTest test
cd apps/web && npm run test -- --run tests/unit/catalogContent.test.ts
rg -n --hidden --glob '!.git' 'BaseDataService|baseData\.json|generate_catalog|catalog-seed-reference' .
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/api/resource/FamilyReadResource.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/api/resource/FamilyResourceTest.java apps/web/tests/unit/catalogContent.test.ts
git rm apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/application/database/BaseDataService.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/platform/application/database/BaseDataServiceTest.java apps/backend/src/main/resources/baseData.json apps/backend/scripts/generate_catalog.py catalog-seed-reference.json
git commit -m "refactor(backend): load catalog from database"
```

## Final quality gates

```bash
cd apps/backend && ./mvnw -B -ntp verify
cd apps/web && npm run lint && npm run test && npm run build
git diff --check
git status --short
```

Local checks prove source, migration, and build behavior only. PostgreSQL deployment, Telegram Mini App client behavior, and physical-device behavior require separate post-deployment validation.
