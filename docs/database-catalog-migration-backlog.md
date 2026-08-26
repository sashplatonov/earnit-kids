# Normalized Database Catalog - Remediation Backlog

## Goal

Make catalog localization extensible without adding language-specific database columns or fields to `CatalogItemEntity`. A catalog item owns language-neutral metadata; every display string lives in normalized translation rows. The existing `GET /api/base-data` response remains compatible with the Telegram Mini App.

## Confirmed current state

- `V53__seed_localized_catalog.sql` and its `catalog_items.title_en`, `title_ru`, `comment_en`, `comment_ru`, `group_name_en`, and `group_name_ru` columns are committed in `a3e7a85c`.
- `CatalogItemEntity`, `CatalogItemRepository`, and `LocalizedCatalogServiceImpl` using those columns are committed in `f553ec32`.
- This design is not extensible: every new locale would require a schema migration, entity fields, mapping branches, seed columns, and tests. The tasks below supersede it.

## Architectural decisions

- `catalog_items` owns only language-neutral data: external ID, kind, group key, graphics, frequency, age limits, difficulty, active state, ordering, and task/reward amount.
- `catalog_item_translations` owns display text: `catalog_item_id`, `locale_code`, `title`, nullable `comment`, and `group_name`. Its unique key is `(catalog_item_id, locale_code)`; locale is data, not a Java field or item-table column.
- `CatalogItemTranslationEntity` has one `localeCode` field. Adding catalog copy for a future language changes translation rows only, never the catalog-item entity or schema.
- The existing `FamilyLocale` enum and `families.locale` constraint still limit selectable family languages to `en`/ `ru`. Enabling a third selected family locale is separate central locale-policy work; it must not recreate per-language catalog columns.
- Do not modify committed V53. V54 creates and backfills translations while retaining V53 columns for old deployed binaries. V55 removes obsolete columns only after the application reads translations in production.
- `FamilyReadResource` remains the authorization and family-locale boundary. `LocalizedCatalogService` remains the single projection path; retain `GET /api/base-data`, its `tasks: []` compatibility field, IDs, ordering, and `sourceCatalogItemId` semantics.
- `TelegramReplyKeyboardNavigator`, web bootstrap, and the ready-catalog UI need no route, resolver, or fallback implementation.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | CAT-DB-001 | P1 | - | Completed but superseded V53 seed; retain immutable Flyway history. |
| 2 | CAT-DB-002 | P1 | CAT-DB-001 | Completed but superseded denormalized read model. |
| 3 | CAT-DB-004 | P0 | CAT-DB-001 | Add normalized translations without breaking an old deployed binary. |
| 4 | CAT-DB-005 | P0 | CAT-DB-004 | Make reads use normalized rows and prove no new entity field is needed. |
| 5 | CAT-DB-006 | P1 | CAT-DB-005 | Switch the endpoint and remove JSON sources. |
| 6 | CAT-DB-007 | P2 | CAT-DB-006 | Drop legacy V53 columns after rollout proof. |

## CAT-DB-001: Seed the initial database catalog

**Status:** DONE
**Priority:** P1
**Depends on:** -

Completed in `a3e7a85c`. Its language-specific V53 columns are superseded; do not edit or delete this committed migration.

## CAT-DB-002: Add the initial database catalog read model

**Status:** DONE
**Priority:** P1
**Depends on:** CAT-DB-001

Completed in `f553ec32`. Its `en`/ `ru` entity fields are superseded; CAT-DB-005 replaces them.

## CAT-DB-004: Normalize catalog translations and backfill V53 data

**Status:** DONE
**Priority:** P0
**Depends on:** CAT-DB-001

**Exact scope:**

Create forward-only V54, normalizing every V53 catalog item into translation rows. Keep V53 locale columns so an existing deployed binary stays compatible during rollout.

**Files:**

- Create `apps/backend/src/main/resources/db/migration/V54__normalize_catalog_item_translations.sql`.
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/infrastructure/persistence/CatalogItemTranslationMigrationTest.java`.
- Read-only source: `apps/backend/src/main/resources/db/migration/V53__seed_localized_catalog.sql`.

**Goal:**

Every catalog item has normalized `en` and `ru` rows, while V53 remains compatible.

### Outcome

`catalog_item_translations` contains complete English and Russian catalog copy, a foreign key to language-neutral items, item/locale uniqueness, and an index for batched locale reads.

### Architectural decision

Create `catalog_item_translations(catalog_item_id, locale_code, title, comment, group_name)`. Do not add a locale enum/check limited to `en` and `ru`: a valid `de` or `sr` row must need no schema change. Preserve V53 columns until CAT-DB-007.

### Required changes

1. Create the normalized table with foreign-key ownership, unique `(catalog_item_id, locale_code)`, non-blank locale/title/group checks, and an index for item-ID plus locale lookup.
2. Backfill every `en` and `ru` V53 value. Replace generic English placeholder copy with natural translations of paired Russian tasks/rewards.
3. Test completeness and constraints: 138 items have two translations, duplicates/orphans fail, and representative English/Russian copy is meaningful.
4. Assert V53 columns remain present and unchanged after V54; do not drop or rename them.

### Out of scope

- JPA mapping or HTTP reads from translations.
- Changing `FamilyLocale` or adding a user-selectable third language.
- Rewriting V53, family item records, or UI behavior.

### Acceptance criteria

- Every item has exactly one `en` and one `ru` translation.
- A valid third-locale translation (`de` or `sr`) can be inserted without changing catalog schema, migration, or `CatalogItemEntity`.
- Title/group name are non-blank, comment is nullable, and orphan translations are rejected.
- V54 works on PostgreSQL and H2 PostgreSQL mode while V53 columns remain available.

### Targeted validation

```bash
cd apps/backend && ./mvnw -B -ntp -Dtest=CatalogItemTranslationMigrationTest test
```

### Commit

```bash
git add apps/backend/src/main/resources/db/migration/V54__normalize_catalog_item_translations.sql apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/infrastructure/persistence/CatalogItemTranslationMigrationTest.java
git commit -m "fix(backend): normalize catalog translations"
```

## CAT-DB-005: Replace language-specific catalog mappings

**Status:** DONE
**Priority:** P0
**Depends on:** CAT-DB-004

**Exact scope:**

Replace all `titleEn`, `titleRu`, `commentEn`, `commentRu`, `groupNameEn`, and `groupNameRu` access with batched normalized translation lookup. Make the catalog entity language-neutral.

**Files:**

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/domain/model/catalog/CatalogItemEntity.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/domain/model/catalog/CatalogItemTranslationEntity.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/infrastructure/persistence/catalog/CatalogItemRepository.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/infrastructure/persistence/catalog/CatalogItemTranslationRepository.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/application/catalog/LocalizedCatalogServiceImpl.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/application/catalog/LocalizedCatalogServiceImplTest.java`.

**Goal:**

The read model selects catalog text by `locale_code`; adding a language never changes `CatalogItemEntity` or its schema.

### Outcome

The service returns the same `catalog.tasks` and `catalog.rewards` shape, resolving requested locale then English fallback.

### Architectural decision

`CatalogItemEntity` maps only language-neutral columns. `CatalogItemTranslationEntity` maps one translation row. Load active items and all requested/fallback translations in two bounded repository queries, then resolve them in the service; do not produce N+1 queries or localization branches in resources/web code.

### Required changes

1. Remove every language-specific member from `CatalogItemEntity`; no `*_en` or `*_ru` Java field may remain.
2. Add a translation entity with one `localeCode`, `title`, nullable `comment`, and `groupName`; it must not contain per-locale fields.
3. Query active items in existing stable order and translations for requested plus `en` fallback, then preserve IDs, kinds, amounts, metadata, tags, and the `tasks: []` wrapper.
4. Remove the existing `@SuppressWarnings("unchecked")` test helper by using typed extraction.
5. Test `en`, `ru`, fallback, and a third locale code passed to the translation resolver. The third-locale test must require no catalog entity source change.

### Out of scope

- Dropping V53 columns; CAT-DB-007 owns that.
- User-facing third-language selection or changes to `FamilyLocale`.
- New REST routes, cache changes, or frontend rewrites.

### Acceptance criteria

- `CatalogItemEntity` contains no language-specific field; one translation entity represents all locales.
- Existing English/Russian clients retain identical response field shape; only corrected English wording may change.
- Missing requested copy falls back deterministically to English; missing English produces an explicit server-side data-integrity result rather than mislabeled output.
- Reads use a bounded item query plus bounded translation query.
- A third locale is selected by code without catalog entity/schema changes.

### Targeted validation

```bash
cd apps/backend && ./mvnw -B -ntp -Dtest=LocalizedCatalogServiceImplTest test
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/domain/model/catalog/CatalogItemEntity.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/domain/model/catalog/CatalogItemTranslationEntity.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/infrastructure/persistence/catalog/CatalogItemRepository.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/infrastructure/persistence/catalog/CatalogItemTranslationRepository.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/application/catalog/LocalizedCatalogServiceImpl.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/application/catalog/LocalizedCatalogServiceImplTest.java
git commit -m "refactor(backend): resolve catalog text by locale"
```

## CAT-DB-006: Serve normalized catalog data and remove file sources

**Status:** DONE
**Priority:** P1  
**Depends on:** CAT-DB-005

**Exact scope:**

Wire the normalized service into `GET /api/base-data`, then remove the obsolete file catalog chain without changing endpoint or frontend contract.

**Files:**

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/api/resource/FamilyReadResource.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/api/resource/FamilyResourceTest.java`.
- Modify or remove `apps/web/tests/unit/catalogContent.test.ts` only after migration/service tests retain its substantive content coverage.
- Delete `apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/application/database/BaseDataService.java`.
- Delete `apps/backend/src/test/java/com/sashplatonov/earnit/kids/platform/application/database/BaseDataServiceTest.java`.
- Delete `apps/backend/src/main/resources/baseData.json`.
- Delete `apps/backend/scripts/generate_catalog.py`.
- Delete `catalog-seed-reference.json`.

**Goal:**

The existing endpoint is database-backed and obsolete JSON catalog sources are gone.

### Required changes

1. Replace `BaseDataService` in `FamilyReadResource`, retaining authorization, missing-family 404, null-locale English fallback, `/api/base-data`, and `tasks: []`.
2. Update resource tests for English/Russian selection and the unchanged wrapper.
3. Move substantive static-content checks to migration/service tests; do not introduce a replacement JSON fixture.
4. Delete sources and prove no live reference to `BaseDataService`, `baseData.json`, `generate_catalog`, or `catalog-seed-reference` remains.

### Out of scope

- Dropping V53 columns, changing `FamilyLocale`, or modifying Telegram navigation/UI.

### Acceptance criteria

- Existing authenticated web and Telegram clients receive database catalog data through the same URL and contract.
- Unauthorized and missing-family responses remain 401 and 404.
- No runtime, build, or test source depends on catalog JSON.

### Targeted validation

```bash
cd apps/backend && ./mvnw -B -ntp -Dtest=FamilyCommandResourceTest,LocalizedCatalogServiceImplTest,CatalogItemTranslationMigrationTest test
cd apps/web && npm run test -- --run tests/unit/catalogContent.test.ts
rg -n --hidden --glob '!.git' 'BaseDataService|baseData\.json|generate_catalog|catalog-seed-reference' .
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/api/resource/FamilyReadResource.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/api/resource/FamilyResourceTest.java apps/web/tests/unit/catalogContent.test.ts
git rm apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/application/database/BaseDataService.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/platform/application/database/BaseDataServiceTest.java apps/backend/src/main/resources/baseData.json apps/backend/scripts/generate_catalog.py catalog-seed-reference.json
git commit -m "refactor(backend): serve catalog translations from database"
```

## CAT-DB-007: Retire V53 language columns after rollout

**Status:** TODO
**Priority:** P2
**Depends on:** CAT-DB-006

**Exact scope:**

After CAT-DB-006 is deployed and verified against PostgreSQL, drop obsolete language-specific V53 columns with a forward-only migration.

**Files:**

- Create `apps/backend/src/main/resources/db/migration/V55__drop_legacy_catalog_locale_columns.sql`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/infrastructure/persistence/CatalogItemTranslationMigrationTest.java` or create `LegacyCatalogLocaleColumnsRetirementMigrationTest.java`.

**Goal:**

The schema contains no `title_en`, `title_ru`, `comment_en`, `comment_ru`, `group_name_en`, or `group_name_ru` catalog column.

### Required changes

1. Record production rollout proof that no running binary reads V53 locale columns before applying V55.
2. Drop only six obsolete columns; retain language-neutral data and all translation rows.
3. Verify V55 on PostgreSQL and H2 and assert complete translations remain.

### Out of scope

- Dropping items, translations, or the V53 Flyway history row.
- Adding a third UI language selector.

### Acceptance criteria

- Post-V55 has a normalized translation table and no per-language `catalog_items` columns.
- English/Russian `/api/base-data` output is unchanged after migration.
- A future locale remains translation data plus central locale-policy work, not a catalog schema/entity change.

### Targeted validation

```bash
cd apps/backend && ./mvnw -B -ntp -Dtest=CatalogItemTranslationMigrationTest,LocalizedCatalogServiceImplTest test
```

### Commit

```bash
git add apps/backend/src/main/resources/db/migration/V55__drop_legacy_catalog_locale_columns.sql apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/infrastructure/persistence/CatalogItemTranslationMigrationTest.java
git commit -m "refactor(backend): remove legacy catalog locale columns"
```

## Final quality gates

```bash
cd apps/backend && ./mvnw -B -ntp verify
cd apps/web && npm run lint && npm run test && npm run build
git diff --check
git status --short
```

Local checks prove source, migration, and build behavior only. PostgreSQL rollout proof is required before CAT-DB-007; Telegram Mini App client and physical-device checks remain separate validation levels.
