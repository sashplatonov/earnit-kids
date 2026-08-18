# Remove Web Coin Shop — Implementation Backlog

## Goal

Remove the web (SvelteKit `apps/web`) implementation of the coin shop (магазин монет / rewards shop) and every piece of code that is used **only** by the web shop, so that no dead code remains. This includes the web admin (super-admin) coin-shop management and the legacy public marketing site, which are also no longer needed. The Telegram mini app and Telegram bot keep their own reward flows. Code that is **shared** between the web shop and the Telegram mini app / bot must **not** be deleted — it is listed in the "Shared code" appendix with exact file paths and locations, and is migrated into a unified Telegram structure (P1-1…P1-4).

## Architectural decisions

- **Source of truth for shop data** stays in the backend domain (`ShopItemEntity`, `PurchaseRequestEntity`, `ShopItemDto`, `ShopItemRepository`, `PurchaseRequestRepository`) and the shared frontend store `apps/web/src/lib/stores/app.ts` (`ShopItem`, `shopItems`, `baseData.products`, `catalog.rewards`). These are consumed by the Telegram mini app and bot and are **out of scope for deletion**.
- **Web-only UI** is the `ShopSection` + `ShopModal` + `RewardGoalProgress` component cluster plus the `shop` branch in `routes/app/[section]/+page.svelte`. These are deleted.
- **Web-only backend endpoints** are `POST /api/shop/bulk` and `POST /api/shop/reward-goal` (and their services/DTOs). `POST /api/shop/{id}/purchase`, `POST /api/shop/{id}/request`, and `POST /api/shop/import` are shared with Telegram and are kept.
- **Reward goal** (`rewardGoalItemId`, `setRewardGoal`, `RewardGoalProgress`, the `TodaySummary` goal block, the analytics "reward goal" card) is a web-only feature but is rendered in **both** `ShopSection` and `TasksSection` (via `TodaySummary`). It is removed as part of the web shop deletion, which requires touching `TasksSection` and the backend `ChildEntity.rewardGoalItemId`/`ChildDto.rewardGoalItemId`/`ChildRepository.updateRewardGoal`.
- **i18n `shop` domain** (`messages/{en,ru}/shop.ts`) is web-only in content, but the domain is registered in `config.ts`/`index.ts` and the `/telegram` route loads it. The `import.kindShop`/`import.kindTasks` keys are duplicated in `tasks.ts` and are used by the shared `CsvImportModal`/`TelegramImport`. The `shop.ts` file is deleted, but the `shop` domain registration and the `import.*` keys must be reconciled (see tasks).
- **Print catalog** (`routes/print/catalog/*`) renders both tasks and shop. Only the shop portion is removed; the tasks portion and `printCatalog.ts` are kept.
- **Rejected approach:** do not delete shared services (`shopPayload.ts`, `catalogFilter.ts`, `csvImport.ts`, `groupOrder.ts`, `save.ts`, `bootstrap.ts`, `serverContract.ts`, `stores/app.ts`, `stores/modal.ts`) or shared API functions (`buyItem`, `requestItem`, `requestItemWithNote`, `importShopItems`, `saveChildGroupOrder`) — they are used by Telegram. Only their web-only members are trimmed (P0-7), and the remaining shared members are migrated into a unified Telegram structure (P1-1…P1-4).
- **Web admin (super-admin) shop** is a separate web-only surface: the `catalog-products` tab, the base `products` catalog (`baseData.products`), and the shop/reward columns in the families table. These are removed in P0-10. The `catalog.rewards` ready catalog is **kept** (Telegram mini app).
- **Legacy public site** (`static/public/*.html` + `resolvePublicRedirect`/`PUBLIC_REDIRECT_MAP`/`LEGACY_ALIAS_MAP`) is no longer needed and is removed in P0-11. The `publicOrigin` config value is **kept** (mini app footer + `robots.txt`/`sitemap.xml`).

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | P0-1 | P0 | - | Remove the web shop UI entry point and section wiring first so the section is unreachable. |
| 2 | P0-2 | P0 | P0-1 | Remove `shop` from the section registry/nav after the UI is gone. |
| 3 | P0-3 | P0 | P0-2 | Remove web-only API client functions and backend bulk/reward-goal endpoints. |
| 4 | P0-4 | P0 | P0-3 | Remove the reward-goal feature (cross-cuts ShopSection, TasksSection, backend). |
| 5 | P0-5 | P0 | P0-4 | Remove the shop portion of the print catalog. |
| 6 | P0-6 | P0 | P0-4 | Remove the web-only `shop` i18n domain and reconcile shared `import.*` keys. |
| 7 | P0-7 | P0 | P0-4 | Trim web-only members from shared services (view-model, telemetry, card-view-mode). |
| 8 | P0-8 | P0 | P0-7 | Remove the web-only admin reward-shop analytics (backend + dashboard). |
| 9 | P0-9 | P0 | P0-8 | Final dead-code sweep and full verification gates. |
| 10 | P0-10 | P0 | P0-9 | Remove the web admin (super-admin) coin-shop management and base `products` catalog. |
| 11 | P0-11 | P0 | P0-10 | Remove the legacy public marketing site (static HTML + redirects + i18n). |
| 12 | P1-1 | P1 | P0-9 | Migrate shared shop services into a unified Telegram mini-app structure. |
| 13 | P1-2 | P1 | P1-1 | Migrate shared shop store/contract types into the Telegram structure. |
| 14 | P1-3 | P1 | P1-2 | Migrate shared shop API client functions into the Telegram structure. |
| 15 | P1-4 | P1 | P1-3 | Final shared-code migration sweep and verification. |

## P0-1: Remove the web shop UI and section wiring

**Status:** ✅ Completed
**Priority:** P0
**Depends on:** -

### Outcome

The `/app/shop` section no longer renders any shop UI; the `ShopSection`, `ShopModal`, and `RewardGoalProgress` components are removed and the `shop` branch in the section page is gone.

### Architectural decision

The web shop UI is a self-contained component cluster. `ShopModal` is opened only by `ShopSection` via `modalStore.open('shop-modal', …)`. `RewardGoalProgress` is also used by `TodaySummary` (TasksSection), so it is removed in P0-4, not here — in this task only the `ShopSection`/`ShopModal` files and the `shop` branch are removed, and `RewardGoalProgress` is left until P0-4.

### Files

- Remove `apps/web/src/lib/components/app/sections/ShopSection.svelte`.
- Remove `apps/web/src/lib/components/app/sections/ShopSection.svelte.bak`.
- Remove `apps/web/src/lib/components/app/modals/ShopModal.svelte`.
- Modify `apps/web/src/routes/app/[section]/+page.svelte` (remove `import ShopSection` and the `{:else if data.section === 'shop'}` branch).
- Modify `apps/web/src/lib/components/app/AppShell.svelte` (remove `import ShopModal` and `<ShopModal />`).

### Work

1. Delete the three shop component files.
2. Remove the `ShopSection` import and its `{:else if data.section === 'shop'}` branch from the section page.
3. Remove the `ShopModal` import and `<ShopModal />` render from `AppShell.svelte`.
4. Leave `RewardGoalProgress` and `TodaySummary` in place (handled in P0-4).

### Acceptance criteria

- Navigating to `/app/shop` no longer renders shop cards; the section is unreachable (redirected or 404 after P0-2).
- No remaining imports reference `ShopSection` or `ShopModal`.
- `npm run lint` and `npm run build` pass.

### Verification

```bash
cd apps/web && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/src/lib/components/app/sections/ShopSection.svelte apps/web/src/lib/components/app/sections/ShopSection.svelte.bak apps/web/src/lib/components/app/modals/ShopModal.svelte apps/web/src/routes/app/[section]/+page.svelte apps/web/src/lib/components/app/AppShell.svelte
git commit -m "refactor(web): remove web coin shop UI and section wiring"
```

## P0-2: Remove `shop` from the section registry and navigation

**Status:** ✅ Completed
**Priority:** P0
**Depends on:** P0-1

### Outcome

`shop` is no longer a valid app section: it is removed from `SHARED_APP_SECTIONS`, `APP_SECTION_META`, `ADMIN_PRIMARY_SECTIONS`, and `CHILD_PRIMARY_SECTIONS`, so the nav no longer offers a shop entry and `/app/shop` returns 404.

### Architectural decision

`apps/web/src/lib/app/routes.ts` is the single source of truth for section identity. Removing `shop` from the section lists makes the route invalid via `isAppSection` and removes it from `AppNav` primary sections.

### Files

- Modify `apps/web/src/lib/app/routes.ts` (remove `'shop'` from `SHARED_APP_SECTIONS`, the `shop` entry in `APP_SECTION_META`, and `'shop'` from `ADMIN_PRIMARY_SECTIONS` and `CHILD_PRIMARY_SECTIONS`).

### Work

1. Remove `'shop'` from `SHARED_APP_SECTIONS`.
2. Remove the `shop: { labelKey, titleKey, iconClass }` entry from `APP_SECTION_META`.
3. Remove `'shop'` from `ADMIN_PRIMARY_SECTIONS` and `CHILD_PRIMARY_SECTIONS`.
4. Confirm no other code references `APP_SECTION_META.shop` or the removed section.

### Acceptance criteria

- `isAppSection('shop')` returns `false`; `/app/shop` returns 404.
- The app nav no longer shows a shop entry for admin or child roles.
- `npm run lint` and `npm run build` pass.

### Verification

```bash
cd apps/web && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/src/lib/app/routes.ts
git commit -m "refactor(web): remove shop from app section registry and nav"
```

## P0-3: Remove web-only shop API functions and backend bulk/reward-goal endpoints

**Status:** ✅ Completed
**Priority:** P0
**Depends on:** P0-2

### Outcome

The web-only API client functions `setRewardGoal` and `bulkShopAction` are removed, and the backend `POST /api/shop/bulk` and `POST /api/shop/reward-goal` endpoints plus their services and DTOs are removed. Shared endpoints (`purchase`, `request`, `import`) remain.

### Architectural decision

`POST /api/shop/bulk` and `POST /api/shop/reward-goal` are consumed only by the web `ShopSection`. `purchase`/`request`/`import` are shared with Telegram and are kept. The reward-goal backend removal is split: the endpoint/service/DTO are removed here, but the `ChildEntity.rewardGoalItemId` persistence is removed in P0-4 (it is also referenced by `FamilyCommandMutationService.clearInvalidRewardGoal`).

### Files

- Modify `apps/web/src/lib/services/api.ts` (remove `setRewardGoal` and `bulkShopAction`; remove `BulkShopActionPayload` type if unused elsewhere).
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/family/FamilyResource.java` (remove `bulkShopItemAction` and `setRewardGoal` methods and their imports).
- Remove `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/request/BulkShopItemActionRequest.java`.
- Remove `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/request/RewardGoalRequest.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/action/FamilyActionBulkService.java` (remove `bulkShopItemAction`; keep `bulkTaskAction`).
- Remove `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/action/FamilyActionRewardGoalService.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/action/FamilyActionService.java` and `FamilyActionServiceImpl.java` (remove `setRewardGoal` and `bulkShopItemAction` declarations/impls).
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/action/FamilyActionSupportService.java` (remove `clearRewardGoal` if only used by reward-goal flow — verify against `FamilyCommandMutationService`).

### Work

1. Remove the two API client functions and the `BulkShopActionPayload` type from `api.ts`.
2. Remove the two resource methods and their DTO imports from `FamilyResource.java`.
3. Delete the two DTO records.
4. Remove `bulkShopItemAction` from `FamilyActionBulkService` (keep `bulkTaskAction` and the shared `applyBulkAction` helper).
5. Delete `FamilyActionRewardGoalService` and remove its wiring from `FamilyActionServiceImpl`.
6. Remove `setRewardGoal`/`bulkShopItemAction` from the `FamilyActionService` interface.
7. Update backend tests that reference `setRewardGoal`/`bulkShopItemAction` (`FamilyResourceTest`, `FamilyActionServiceImplTest`) — remove or repurpose those cases.

### Acceptance criteria

- `POST /api/shop/bulk` and `POST /api/shop/reward-goal` return 404/405 (no longer registered).
- `POST /api/shop/{id}/purchase`, `POST /api/shop/{id}/request`, `POST /api/shop/import` still work (Telegram flows unaffected).
- Backend `./mvnw verify` passes (including Checkstyle/SpotBugs/PMD).

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
cd apps/web && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/src/lib/services/api.ts apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/family/FamilyResource.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/request/BulkShopItemActionRequest.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/request/RewardGoalRequest.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/action/ apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/family/FamilyResourceTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/family/action/FamilyActionServiceImplTest.java
git commit -m "refactor(backend): remove web-only shop bulk and reward-goal endpoints"
```

## P0-4: Remove the reward-goal feature (web + backend persistence)

**Status:** ✅ Completed
**Priority:** P0
**Depends on:** P0-3

### Outcome

The reward-goal feature is fully removed: `RewardGoalProgress`, the `TodaySummary` goal block, the `rewardGoalItemId` field on the child, and the backend `ChildEntity.rewardGoalItemId`/`ChildRepository.updateRewardGoal`/`clearInvalidRewardGoal` logic.

### Architectural decision

The reward goal is web-only (not present in Telegram), but it is rendered in both `ShopSection` (removed in P0-1) and `TasksSection` via `TodaySummary`. Removing it requires editing `TasksSection` and `TodaySummary`, and removing the persisted `rewardGoalItemId` from the backend child model. The analytics "reward goal" card in `analyticsDailyQuests.ts` is also removed.

> **Note:** The analytics "reward goal" card (`buildRewardGoalCard`, `chooseTargetShopItem`, `AnalyticsQuestShopItemContext`, and the `shopItems` analytics option) was already removed in P0-2, because its `actionTarget: 'shop'` blocked the section-type change. This task now only covers `RewardGoalProgress`, `TodaySummary`, `TasksSection`, and the backend `rewardGoalItemId` persistence.

### Files

- Remove `apps/web/src/lib/components/app/catalog/RewardGoalProgress.svelte`.
- Modify `apps/web/src/lib/components/app/catalog/TodaySummary.svelte` (remove `RewardGoalProgress` import, the `rewardGoal`/`rewardGoalSelected`/`balance`/`goal*` props, and the `<RewardGoalProgress …/>` render).
- Modify `apps/web/src/lib/components/app/sections/TasksSection.svelte` (remove `rewardGoal`/`rewardGoalSelected` reactive bindings and the props passed to `TodaySummary`).
- Modify `apps/web/src/lib/components/app/sections/analyticsDailyQuests.ts` (remove `buildRewardGoalCard`, `chooseTargetShopItem`, `AnalyticsQuestShopItemContext`, and the `shopItems` input if now unused).
- Modify `apps/web/src/lib/components/app/sections/analyticsViewModel.ts` (remove the reward-goal card wiring and `AnalyticsQuestShopItemContext` import).
- Modify `apps/web/src/lib/stores/app.ts` (remove `rewardGoalItemId` from `Child`).
- Modify `apps/web/src/lib/services/serverContract.ts` (remove `rewardGoalItemId` normalization).
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/ChildEntity.java` (remove `rewardGoalItemId`).
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/ChildDto.java` (remove `rewardGoalItemId`).
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/ChildRepository.java` (remove `updateRewardGoal`).
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/command/FamilyCommandMutationService.java` (remove `clearInvalidRewardGoal` and its call).
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/action/FamilyActionSupportService.java` (remove `clearRewardGoal` if still present).

### Work

1. Delete `RewardGoalProgress.svelte` and strip its usage from `TodaySummary` and `TasksSection`.
2. Remove the reward-goal card from `analyticsDailyQuests.ts`/`analyticsViewModel.ts`.
3. Remove `rewardGoalItemId` from the frontend `Child` type and `serverContract.ts`.
4. Remove `rewardGoalItemId` from `ChildEntity`/`ChildDto` and `updateRewardGoal` from `ChildRepository`.
5. Remove `clearInvalidRewardGoal` from `FamilyCommandMutationService` and `clearRewardGoal` from `FamilyActionSupportService`.
6. Update backend tests referencing `rewardGoalItemId`/`updateRewardGoal`/`clearRewardGoal` (`FamilyCommandServiceImplTest`, `FamilyActionServiceImplTest`).

### Acceptance criteria

- No `rewardGoalItemId`, `RewardGoalProgress`, or `setRewardGoal` references remain in `apps/web` or `apps/backend`.
- The web Tasks section renders its today-summary without a reward-goal block.
- Backend `./mvnw verify` and web `npm run lint && npm run build` pass.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
cd apps/web && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/src/lib/components/app/catalog/RewardGoalProgress.svelte apps/web/src/lib/components/app/catalog/TodaySummary.svelte apps/web/src/lib/components/app/sections/TasksSection.svelte apps/web/src/lib/components/app/sections/analyticsDailyQuests.ts apps/web/src/lib/components/app/sections/analyticsViewModel.ts apps/web/src/lib/stores/app.ts apps/web/src/lib/services/serverContract.ts apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/ChildEntity.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/ChildDto.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/ChildRepository.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/command/FamilyCommandMutationService.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/action/FamilyActionSupportService.java
git commit -m "refactor(web,backend): remove reward-goal feature"
```

## P0-5: Remove the shop portion of the print catalog

**Status:** ✅ Completed
**Priority:** P0
**Depends on:** P0-4

### Outcome

The print catalog (`/print/catalog`) still prints tasks but no longer prints shop items; the shop-specific types, grouping, chips, and server-side `shopItems` load are removed.

### Architectural decision

`routes/print/catalog/*` renders both tasks and shop. Only the shop half is removed; the tasks half and `printCatalog.ts` (generic URL builder) are kept.

### Files

- Modify `apps/web/src/routes/print/catalog/+page.svelte` (remove `PrintShopItem` type, `shopItems`/`shopGroups` bindings, `shopChips`, and the shop render block).
- Modify `apps/web/src/routes/print/catalog/+page.server.ts` (remove `shopItems: normalized.shop` from the returned data).

### Work

1. Remove the `PrintShopItem` type and `shopItems`/`shopGroups` reactive bindings.
2. Remove the `shopChips` function and the shop section of the template.
3. Remove `shopItems` from the server load return.

### Acceptance criteria

- `/print/catalog` renders task cards only; no shop cards or shop group headings.
- `npm run lint` and `npm run build` pass.

### Verification

```bash
cd apps/web && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/src/routes/print/catalog/+page.svelte apps/web/src/routes/print/catalog/+page.server.ts
git commit -m "refactor(web): remove shop portion of print catalog"
```

## P0-6: Remove the web-only `shop` i18n domain and reconcile shared import keys

**Status:** ✅ Completed
**Priority:** P0
**Depends on:** P0-4

### Outcome

The `shop` i18n domain files are removed, and the `shop` domain registration is dropped from the `/app/shop` path mapping. The shared `import.kindShop`/`import.kindTasks` keys remain available (they live in `tasks.ts` and are used by the shared CSV import).

### Architectural decision

`messages/{en,ru}/shop.ts` are web-only in content. The `import.kindShop`/`import.kindTasks` keys are duplicated in `tasks.ts` and are consumed by the shared `CsvImportModal`/`TelegramImport`, so they must remain. The `/telegram` route currently loads the `shop` domain but its UI uses `app.telegram.*` keys, so the `shop` domain can be dropped from the `/telegram` domain list after confirming no Telegram component reads `shop.*` keys.

### Files

- Remove `apps/web/src/lib/i18n/messages/en/shop.ts`.
- Remove `apps/web/src/lib/i18n/messages/ru/shop.ts`.
- Modify `apps/web/src/lib/i18n/index.ts` (remove `shopMessages` imports and the `shop` domain registration).
- Modify `apps/web/src/lib/i18n/config.ts` (remove `'shop'` from the `/app/shop` domain list; remove `'shop'` from the `/telegram` domain list; remove `'shop'` from `MessageDomain` if no longer referenced).

### Work

1. Delete the two `shop.ts` message files.
2. Remove the `shopMessages` imports and `shop` registration from `index.ts`.
3. Remove `'shop'` from the `/app/shop` and `/telegram` domain lists in `config.ts`.
4. Confirm `import.kindShop`/`import.kindTasks` are still resolvable via the `tasks` domain (they are defined in `tasks.ts`).
5. Remove `'shop'` from the `MessageDomain` union only if no remaining reference exists.

### Acceptance criteria

- No `shop.` message key is referenced anywhere in `apps/web/src`.
- `import.kindShop`/`import.kindTasks` still resolve (CSV import UI intact in web and Telegram).
- `npm run lint` and `npm run build` pass.

### Verification

```bash
cd apps/web && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/src/lib/i18n/messages/en/shop.ts apps/web/src/lib/i18n/messages/ru/shop.ts apps/web/src/lib/i18n/index.ts apps/web/src/lib/i18n/config.ts
git commit -m "refactor(web): remove web-only shop i18n domain"
```

## P0-7: Trim web-only members from shared services

**Status:** ⬜ Not started
**Priority:** P0
**Depends on:** P0-4

### Outcome

Web-only members are removed from shared service files while the Telegram-shared members remain: `buildShopCatalogItemViewModel`, the `shop_action`/`reward_goal_action` telemetry names, and the `'shop'` card-view section.

### Architectural decision

These files are shared with Telegram, so only the web-only members are trimmed, not the files themselves.

### Files

- Modify `apps/web/src/lib/services/catalogItemViewModel.ts` (remove `ShopCatalogItemViewModel` and `buildShopCatalogItemViewModel`; keep `buildTaskCatalogItemViewModel`).
- Modify `apps/web/src/lib/services/catalogTelemetry.ts` (remove `'shop_action'` and `'reward_goal_action'` from `CatalogTelemetryName` and the `allowedNames` set; keep `'task_action'`).
- Modify `apps/web/src/lib/services/cardViewMode.ts` (remove `'shop'` from `CardViewSection`; keep `'tasks' | 'requests' | 'history'`).
- Modify `apps/web/src/routes/super-admin/+page.svelte` (remove the `'shop'` branch in `loadCardViewMode`/`saveCardViewMode` calls if it referenced the shop catalog tab).

### Work

1. Remove the shop view-model function and its interface from `catalogItemViewModel.ts`.
2. Remove the shop/reward-goal telemetry names from `catalogTelemetry.ts`.
3. Remove `'shop'` from `CardViewSection` in `cardViewMode.ts`.
4. Update `super-admin/+page.svelte` so it no longer passes `'shop'` to `loadCardViewMode`/`saveCardViewMode`.

### Acceptance criteria

- No references to `buildShopCatalogItemViewModel`, `shop_action`, `reward_goal_action`, or `CardViewSection 'shop'` remain.
- Telegram catalog/reward flows still compile (they use `catalogFilter`, `groupOrder`, `csvImport`, `shopPayload` — untouched).
- `npm run lint` and `npm run build` pass.

### Verification

```bash
cd apps/web && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/src/lib/services/catalogItemViewModel.ts apps/web/src/lib/services/catalogTelemetry.ts apps/web/src/lib/services/cardViewMode.ts apps/web/src/routes/super-admin/+page.svelte
git commit -m "refactor(web): trim web-only shop members from shared services"
```

## P0-8: Remove web-only admin reward-shop analytics

**Status:** ⬜ Not started
**Priority:** P0
**Depends on:** P0-7

### Outcome

The admin dashboard reward-shop analytics (backend resource/service/DTO and the dashboard `rewardShop` block) are removed, since they describe the web shop and are not used by Telegram.

### Architectural decision

`AdminRewardShopResource`/`AdminRewardShopService`/`AdminRewardsResponse` and `AdminAnalyticsRepository.getRewardShopMetrics` are web-admin-only. The super-admin shop counts (`shopCount`) are a separate concern and are left unless confirmed unused.

### Files

- Remove `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/admin/AdminRewardShopResource.java`.
- Remove `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/admin/AdminRewardShopService.java`.
- Remove `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/AdminRewardsResponse.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/AdminDashboardResponse.java` (remove `rewardShop`).
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/admin/AdminDashboardService.java` (remove `rewardShopService` wiring and the `rewardShop` field).
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/AdminAnalyticsRepository.java` (remove `getRewardShopMetrics`).
- Modify `apps/web/src/routes/app/dashboard/+page.svelte` (remove the `rewardShop` binding and the reward-shop metrics block).

### Work

1. Delete the resource, service, and DTO files.
2. Remove `rewardShop` from `AdminDashboardResponse` and `AdminDashboardService`.
3. Remove `getRewardShopMetrics` from `AdminAnalyticsRepository`.
4. Remove the `rewardShop` usage from the web dashboard page.
5. Update any backend tests referencing `AdminRewardShopService`/`getRewardShopMetrics`.

### Acceptance criteria

- `GET /api/admin/analytics/reward-shop` is no longer registered.
- The admin dashboard no longer renders reward-shop metrics.
- Backend `./mvnw verify` and web `npm run lint && npm run build` pass.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
cd apps/web && npm run lint && npm run build
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/admin/AdminRewardShopResource.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/admin/AdminRewardShopService.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/AdminRewardsResponse.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/AdminDashboardResponse.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/admin/AdminDashboardService.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/AdminAnalyticsRepository.java apps/web/src/routes/app/dashboard/+page.svelte
git commit -m "refactor(backend,web): remove web-only admin reward-shop analytics"
```

## P0-9: Final dead-code sweep and full verification

**Status:** ⬜ Not started
**Priority:** P0
**Depends on:** P0-8

### Outcome

No dead code referencing the web shop remains; all quality gates pass end-to-end.

### Architectural decision

This is a verification-only task: grep for residual shop references, confirm the shared-code appendix is intact, and run the full test/build matrix.

### Files

- No new files. Any residual references found are cleaned up in this task.

### Work

1. Grep `apps/web/src` and `apps/backend/src` for `ShopSection`, `ShopModal`, `RewardGoalProgress`, `setRewardGoal`, `bulkShopAction`, `rewardGoalItemId`, `reward-goal`, `shop-modal`, `buildShopCatalogItemViewModel`, `shop_action`, `reward_goal_action`.
2. Confirm the shared-code appendix (below) is still present and used by Telegram.
3. Run the full verification matrix.

### Acceptance criteria

- The grep returns no web-only shop references.
- Backend `./mvnw verify`, web `npm run lint`, `npm run test`, and `npm run build` all pass.
- Telegram reward flows (purchase/request/import/group-order) still compile and are unaffected.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
cd apps/web && npm run lint && npm run test && npm run build
```

### Commit

```bash
git add -A
git commit -m "chore(web,backend): final dead-code sweep after web shop removal"
```

## P0-10: Remove the web admin (super-admin) coin-shop management and base `products` catalog

**Status:** ⬜ Not started
**Priority:** P0
**Depends on:** P0-9

### Outcome

The super-admin web page no longer manages the coin shop: the `catalog-products` tab, the base `products` catalog editor, and the shop/reward columns in the families table are removed. The backend base `products` catalog (`baseData.products`) is removed, while the `catalog.rewards` (ready catalog) used by the Telegram mini app is kept.

### Architectural decision

There are **two** reward catalogs in the backend `baseData.json`:
- `baseData.products` — the legacy "base products" catalog, consumed only by the web `CatalogSection` (admin "catalog" section) and the super-admin `catalog-products` tab. This is web-only and is removed.
- `baseData.catalog.rewards` — the "ready catalog" consumed by the Telegram mini app (`TelegramReadyCatalog` → `$appStore.catalog.rewards`). This is **kept**.

The super-admin page (`routes/super-admin/+page.svelte`) has a `catalog-products` tab and a `catalog-tasks` tab that edit `baseData.products`/`baseData.tasks` via `POST /api/super/base-data`. Only the **products** half is removed; the **tasks** half stays. The families table also shows `shopCount`/reward columns and a per-family `shop` list — these are removed.

### Files

- Modify `apps/web/src/routes/super-admin/+page.svelte` (remove the `catalog-products` tab, `catalogProducts` state, `CatalogType = 'products'` branches, `addProductFromCatalog`-equivalent edit/delete for products, the `shopCount`/`withShop`/`avgShop` dashboard stats, the `shop` column in the families table, and the per-family `shopItems`/rewards detail block).
- Modify `apps/web/src/lib/components/app/sections/CatalogSection.svelte` (remove the `catalogProducts`/`filteredProducts` bindings, `addProductFromCatalog`, and the products column; keep the tasks column).
- Modify `apps/web/src/lib/i18n/messages/en/superadmin.ts` and `ru/superadmin.ts` (remove `catalogProducts`, `baseProducts`, `addProduct`, `loadingProducts`, `emptyProducts`, `useShop`, `averageRewardsPerFamily`, `totalRewards`, `rewards`, `rewardsChip`, `tasksAndRewards`, `rewardsHeading`, `rewardsEmpty`).
- Modify `apps/web/src/lib/i18n/messages/en/admin.ts` and `ru/admin.ts` (remove `catalog.productAdded`, `catalog.productsCount`, `catalog.productsEyebrow`, `catalog.productsTitle`, `catalog.defaultProductGroup`, `catalog.addProduct`, `catalog.noProducts`).
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/database/BaseDataService.java` (remove `products` from `EMPTY_BASE_DATA`, `normalizeBaseData`, and `getBaseData`/`saveBaseData` normalization).
- Modify `apps/backend/src/main/resources/baseData.json` (remove the top-level `products` array; keep `tasks` and `catalog`).
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/SuperAdminFamiliesResponse.java` (remove `shopCount` from `FamilySummary`).
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/SuperAdminFamilyDetailsResponse.java` (remove the `shop` field).
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/system/SuperAdminService.java` (remove `shopItemRepository` usage for family summaries/details and the `toShopPayload` helper if now unused).
- Modify `apps/web/src/lib/services/serverContract.ts` (remove `products` from `normalizeBaseData`).
- Modify `apps/web/src/lib/stores/app.ts` (remove `baseData.products` from `AppState` and `initialState`).

### Work

1. Remove the `catalog-products` tab and all `products`-typed branches from `super-admin/+page.svelte`; keep `catalog-tasks`.
2. Remove the shop/reward columns and per-family reward detail from the super-admin families view.
3. Remove the products column from `CatalogSection.svelte` (keep the tasks column).
4. Remove the now-unused i18n keys from `superadmin.ts` and `admin.ts`.
5. Remove `products` from `BaseDataService` and `baseData.json`.
6. Remove `shopCount`/`shop` from the super-admin DTOs and `SuperAdminService`.
7. Remove `products` from `serverContract.ts` and `stores/app.ts`.
8. Update backend tests referencing `baseData.products`/`shopCount`/`SuperAdminService`.

### Acceptance criteria

- The super-admin page has no `catalog-products` tab and no shop/reward columns.
- `GET /api/super/base-data` no longer returns a `products` array; `catalog.rewards` is still returned.
- The Telegram mini app ready catalog (`$appStore.catalog.rewards`) still loads and renders.
- Backend `./mvnw verify` and web `npm run lint && npm run build` pass.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
cd apps/web && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/src/routes/super-admin/+page.svelte apps/web/src/lib/components/app/sections/CatalogSection.svelte apps/web/src/lib/i18n/messages/en/superadmin.ts apps/web/src/lib/i18n/messages/ru/superadmin.ts apps/web/src/lib/i18n/messages/en/admin.ts apps/web/src/lib/i18n/messages/ru/admin.ts apps/web/src/lib/services/serverContract.ts apps/web/src/lib/stores/app.ts apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/database/BaseDataService.java apps/backend/src/main/resources/baseData.json apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/SuperAdminFamiliesResponse.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/SuperAdminFamilyDetailsResponse.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/system/SuperAdminService.java
git commit -m "refactor(web,backend): remove web admin coin-shop management and base products catalog"
```

## P0-11: Remove the legacy public marketing site

**Status:** ⬜ Not started
**Priority:** P0
**Depends on:** P0-10

### Outcome

The legacy static public marketing site (`static/public/*.html`) and all its redirect/alias/i18n wiring are removed. The root URL no longer redirects to `/public/index.html`; instead it redirects to the app shell (or login) for unauthenticated users.

### Architectural decision

The public site is a static HTML site in `apps/web/static/public/` served outside SvelteKit routing, reached via `resolvePublicRedirect`/`PUBLIC_REDIRECT_MAP`/`LEGACY_ALIAS_MAP` in `config.ts`, `hooks.server.ts`, and `routes/+page.server.ts`. It is no longer needed. The `publicOrigin` config value is **kept** because the Telegram mini app footer still links to it (`TelegramParentShell`/`TelegramChildShell` → `publicSite`), and `sitemap.xml`/`robots.txt` reference it — but the static HTML pages themselves are removed.

### Files

- Remove `apps/web/static/public/` (all `*.html`, `site.js`, `styles.css`, `config.js`, `assets/`, favicons).
- Modify `apps/web/src/routes/+page.server.ts` (replace the `redirect(302, '/public/index.html')` fallback with a redirect to `/login` or the app shell).
- Modify `apps/web/src/lib/i18n/config.ts` (remove `PUBLIC_BARE_PATHS`, `PUBLIC_REDIRECT_MAP`, `resolvePublicRedirect`, and the `/public` entry in `BYPASS_PREFIXES`; remove the `LEGACY_ALIAS_MAP` entries that point to `/public/*`).
- Modify `apps/web/src/hooks.server.ts` (remove the `resolvePublicRedirect` import and the public-redirect block).
- Modify `apps/web/src/lib/i18n/index.ts` (remove the `resolvePublicRedirect` re-export if present).
- Modify `apps/web/src/routes/sitemap.xml/+server.ts` (remove the `/public/*.html` entries; keep app routes or emit an empty sitemap).
- Modify `apps/web/src/lib/components/PublicTopNav.svelte` (remove the `/features/shop`, `/features/tasks`, `/about` links that pointed at the public site, or remove the component if only used by the login page).
- Modify `apps/web/src/routes/login/+page.svelte` (remove `PublicTopNav` usage if the component is removed).
- Modify `apps/web/src/lib/i18n/messages/en/public.ts` and `ru/public.ts` (remove the `public` domain if no longer referenced; keep `common`/`auth` keys used by login).
- Modify `apps/web/src/lib/i18n/config.ts` (remove `'public'` from the `MessageDomain` union and the `/login` domain list if the domain is removed).

### Work

1. Delete `static/public/`.
2. Change the root redirect to point at `/login` (or the app shell) instead of `/public/index.html`.
3. Remove the public redirect/alias maps and the `resolvePublicRedirect` function.
4. Remove the `/public/*` sitemap entries.
5. Remove `PublicTopNav` (or its public-site links) and its usage in `login/+page.svelte`.
6. Remove the `public` i18n domain if it is no longer consumed.
7. Keep `publicOrigin` in `config.ts` (still used by the mini app footer and `robots.txt`).

### Acceptance criteria

- `/` no longer redirects to `/public/index.html`; it redirects to `/login` (or the app shell) for unauthenticated users.
- `/how`, `/tasks`, `/rewards`, `/parents`, `/faq`, `/features/*` no longer resolve to static HTML.
- The Telegram mini app footer "public site" link still points at `publicOrigin` (unchanged).
- `npm run lint` and `npm run build` pass.

### Verification

```bash
cd apps/web && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/static/public apps/web/src/routes/+page.server.ts apps/web/src/lib/i18n/config.ts apps/web/src/hooks.server.ts apps/web/src/lib/i18n/index.ts apps/web/src/routes/sitemap.xml/+server.ts apps/web/src/lib/components/PublicTopNav.svelte apps/web/src/routes/login/+page.svelte apps/web/src/lib/i18n/messages/en/public.ts apps/web/src/lib/i18n/messages/ru/public.ts
git commit -m "refactor(web): remove legacy public marketing site"
```

## P1-1: Migrate shared shop services into a unified Telegram mini-app structure

**Status:** ⬜ Not started
**Priority:** P1
**Depends on:** P0-9

### Outcome

The shared shop services currently living in the generic `apps/web/src/lib/services/` are moved into a Telegram-scoped structure so the mini app and bot code is uniform. The web app no longer imports them.

### Architectural decision

The shared services (`shopPayload.ts`, `catalogFilter.ts`, `csvImport.ts`, `groupOrder.ts`, `save.ts`, `bootstrap.ts`, `serverContract.ts`, `catalogItemViewModel.ts`, `catalogTelemetry.ts`, `catalogViewState.ts`, `groupPrompt.ts`, `confirm.ts`) are consumed by both the web app and the Telegram mini app. After the web shop removal (P0-1…P0-9), the remaining consumers are the Telegram mini app plus the web `TasksSection`/`HistorySection`/`RequestsSection` (which still use `groupOrder`, `catalogItemViewModel`, `catalogTelemetry`, `catalogViewState`, `groupPrompt`, `confirm`, `save`, `bootstrap`, `serverContract`). The migration target is a new `apps/web/src/lib/telegram/` tree (or `apps/web/src/lib/components/telegram/`-adjacent `services/`), with the web-only consumers re-pointed to the new location. This is a **move + re-import** refactor, not a behavior change.

### Files

- Create `apps/web/src/lib/telegram/services/` (new directory).
- Move `apps/web/src/lib/services/shopPayload.ts` → `apps/web/src/lib/telegram/services/shopPayload.ts`.
- Move `apps/web/src/lib/services/catalogFilter.ts` → `apps/web/src/lib/telegram/services/catalogFilter.ts`.
- Move `apps/web/src/lib/services/csvImport.ts` → `apps/web/src/lib/telegram/services/csvImport.ts`.
- Move `apps/web/src/lib/services/groupOrder.ts` → `apps/web/src/lib/telegram/services/groupOrder.ts`.
- Move `apps/web/src/lib/services/readyCatalogTelemetry.ts` → `apps/web/src/lib/telegram/services/readyCatalogTelemetry.ts`.
- Update all import paths in `apps/web/src/lib/components/telegram/*.svelte` and the web sections that still use these services.

### Work

1. Create the `telegram/services/` directory.
2. Move the five shop/catalog services into it (preserve content; only relocate).
3. Update every `import ... from '$lib/services/<name>'` to the new path in Telegram components and remaining web consumers.
4. Keep `save.ts`, `bootstrap.ts`, `serverContract.ts`, `confirm.ts`, `catalogItemViewModel.ts`, `catalogTelemetry.ts`, `catalogViewState.ts`, `groupPrompt.ts` in place for now (they are shared with the web tasks/history/requests sections and are handled in P1-2/P1-3).

### Acceptance criteria

- The five services live under `apps/web/src/lib/telegram/services/`.
- No import references the old `$lib/services/shopPayload`/`catalogFilter`/`csvImport`/`groupOrder`/`readyCatalogTelemetry` paths.
- `npm run lint` and `npm run build` pass.

### Verification

```bash
cd apps/web && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/src/lib/telegram/services apps/web/src/lib/services/shopPayload.ts apps/web/src/lib/services/catalogFilter.ts apps/web/src/lib/services/csvImport.ts apps/web/src/lib/services/groupOrder.ts apps/web/src/lib/services/readyCatalogTelemetry.ts apps/web/src/lib/components/telegram
git commit -m "refactor(web): move shared shop services into telegram structure"
```

## P1-2: Migrate shared shop store/contract types into the Telegram structure

**Status:** ⬜ Not started
**Priority:** P1
**Depends on:** P1-1

### Outcome

The shop-specific types and normalization (`ShopItem`, `CatalogRewardTemplate`, `normalizeShopItem`, `normalizeCatalog`, the `shop`/`shopItems`/`catalog.rewards` fields) are extracted into a Telegram-scoped module, leaving the web store/contract free of shop concerns.

### Architectural decision

`stores/app.ts` and `services/serverContract.ts` are shared. The shop-specific members (`ShopItem`, `CatalogRewardTemplate`, `shopItems`, `baseData.products` (already removed in P0-10), `catalog.rewards`, `normalizeShopItem`, `normalizeCatalog`, `shop` field) are moved to a new `apps/web/src/lib/telegram/` module. The web `TasksSection`/`HistorySection`/`RequestsSection` still reference `shopItems`/`baseData.products` for history/request catalog lookups — those lookups are re-pointed to the Telegram module or trimmed if the web history/requests no longer need shop context.

### Files

- Create `apps/web/src/lib/telegram/types.ts` (or `apps/web/src/lib/telegram/shopTypes.ts`).
- Modify `apps/web/src/lib/stores/app.ts` (remove `ShopItem`, `CatalogRewardTemplate`, `shopItems`, `catalog.rewards`; keep `Task`, `CatalogTaskTemplate`, `tasks`, `catalog.tasks`).
- Modify `apps/web/src/lib/services/serverContract.ts` (remove `normalizeShopItem`, `normalizeCatalog` reward half, `shop` field, `shopItems`).
- Modify `apps/web/src/lib/components/app/sections/HistorySection.svelte` and `RequestsSection.svelte` (re-point or trim `shopItems`/`baseProducts` usage).
- Update Telegram components to import the new types.

### Work

1. Create the Telegram types module with `ShopItem`, `CatalogRewardTemplate`, and the shop normalization helpers.
2. Remove shop members from `stores/app.ts` and `serverContract.ts`.
3. Re-point Telegram components and the web history/requests sections to the new module.
4. Confirm the web `TasksSection` no longer depends on shop types.

### Acceptance criteria

- `stores/app.ts` and `serverContract.ts` contain no `ShopItem`/`shopItems`/`catalog.rewards`/`normalizeShopItem` references.
- Telegram components import shop types from the new Telegram module.
- `npm run lint` and `npm run build` pass.

### Verification

```bash
cd apps/web && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/src/lib/telegram apps/web/src/lib/stores/app.ts apps/web/src/lib/services/serverContract.ts apps/web/src/lib/components/app/sections/HistorySection.svelte apps/web/src/lib/components/app/sections/RequestsSection.svelte apps/web/src/lib/components/telegram
git commit -m "refactor(web): extract shop types into telegram structure"
```

## P1-3: Migrate shared shop API client functions into the Telegram structure

**Status:** ⬜ Not started
**Priority:** P1
**Depends on:** P1-2

### Outcome

The shop API client functions (`buyItem`, `requestItem`, `requestItemWithNote`, `importShopItems`, `saveChildGroupOrder`) are moved into a Telegram-scoped API module, leaving `api.ts` free of shop endpoints.

### Architectural decision

`services/api.ts` is shared. The shop functions are consumed only by Telegram components after the web shop removal. They are moved to `apps/web/src/lib/telegram/api.ts` (or `telegram/services/api.ts`). `saveChildGroupOrder` is also used by the web `TasksSection` (task group order) — it is kept in `api.ts` or split so the `tasks`/`shop` section parameter remains generic.

### Files

- Create `apps/web/src/lib/telegram/api.ts`.
- Modify `apps/web/src/lib/services/api.ts` (remove `buyItem`, `requestItem`, `requestItemWithNote`, `importShopItems`; keep `saveChildGroupOrder` if still used by web tasks).
- Update Telegram components (`TelegramParentRewards`, `TelegramChildRewards`, `TelegramImport`) to import from the new module.

### Work

1. Create the Telegram API module and move the four shop functions.
2. Remove them from `api.ts`.
3. Re-point the Telegram importers.
4. Keep `saveChildGroupOrder` in `api.ts` (shared with web tasks) or move it and re-point both consumers.

### Acceptance criteria

- `api.ts` no longer exports `buyItem`/`requestItem`/`requestItemWithNote`/`importShopItems`.
- Telegram components import shop API functions from the Telegram module.
- `npm run lint` and `npm run build` pass.

### Verification

```bash
cd apps/web && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/src/lib/telegram/api.ts apps/web/src/lib/services/api.ts apps/web/src/lib/components/telegram
git commit -m "refactor(web): move shop api client into telegram structure"
```

## P1-4: Final shared-code migration sweep and verification

**Status:** ⬜ Not started
**Priority:** P1
**Depends on:** P1-3

### Outcome

The shared-code migration is complete: no shop-specific code remains in the generic `services/`/`stores/` layer, and the Telegram mini app / bot code is in a uniform structure. All quality gates pass.

### Architectural decision

Verification-only task: confirm the generic layer is free of shop concerns, the Telegram structure is self-contained, and the full test/build matrix passes.

### Files

- No new files. Any residual references found are cleaned up in this task.

### Work

1. Grep `apps/web/src/lib/services` and `apps/web/src/lib/stores` for `ShopItem`, `shopItems`, `shopPayload`, `catalogFilter`, `csvImport`, `groupOrder`, `buyItem`, `requestItem`, `importShopItems`, `catalog.rewards`.
2. Confirm the Telegram structure (`apps/web/src/lib/telegram/`) is self-contained.
3. Run the full verification matrix.

### Acceptance criteria

- The generic `services/`/`stores/` layer contains no shop-specific code.
- Backend `./mvnw verify`, web `npm run lint`, `npm run test`, and `npm run build` all pass.
- Telegram reward flows (purchase/request/import/group-order) still work.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
cd apps/web && npm run lint && npm run test && npm run build
```

### Commit

```bash
git add -A
git commit -m "chore(web): final shared-code migration sweep"
```

---

# Appendix — Shared code (do NOT delete; decide separately)

The following code is used by **both** the web shop and the Telegram mini app / bot. It must **not** be deleted as part of this backlog. Each entry lists the file and the specific location(s) so a follow-up decision can be made.

## Frontend shared services

| File | Shared symbol(s) | Telegram consumer (file:line) |
| --- | --- | --- |
| `apps/web/src/lib/services/shopPayload.ts` | `buildShopPayload`, `ShopPayload`, `ShopPayloadInput` | `apps/web/src/lib/components/telegram/TelegramRewardForm.svelte:5,46` |
| `apps/web/src/lib/services/catalogFilter.ts` | `templateToReward`, `templateToTask`, `mapGroupKeyToFamily`, `isAlreadyAdded`, `stripEmoji`, `formatFrequency`, `CatalogFilters`, `EMPTY_FILTERS` | `TelegramParentCatalog.svelte:5`, `TelegramReadyCatalog.svelte:10-15`, `TelegramCatalogDetails.svelte:4`, `TelegramCatalogFilters.svelte:4` |
| `apps/web/src/lib/services/csvImport.ts` | `CsvImportKind = 'tasks' \| 'shop'`, `SHOP_COLUMNS`, `CSV_IMPORT_SCHEMAS.shop`, `parseCsvImport`, `CsvImportValidationError` | `TelegramImport.svelte:9-12` |
| `apps/web/src/lib/services/groupOrder.ts` | `GroupOrderSection = 'tasks' \| 'shop'`, `orderGroups`, `getEffectiveGroupOrder`, `applyGroupOrderToChildren`, `moveGroup` | `TelegramParentRewards.svelte:8`, `TelegramParentTasks.svelte:8`, `TelegramGroupManager.svelte:7`, `TelegramParentCatalog.svelte:6` |
| `apps/web/src/lib/services/save.ts` | `scheduleSave`, `buildPayload` (includes `shop: s.shopItems`) | `TelegramGroupManager.svelte:5`, `TelegramParentCatalog.svelte:4`, `TelegramParentRewards.svelte:7`, `TelegramParentTasks.svelte:7`, `TelegramReadyCatalog.svelte:5`, `TelegramRewardForm.svelte:4`, `TelegramTaskForm.svelte:4` |
| `apps/web/src/lib/services/bootstrap.ts` | `applyDataSnapshot`, `refreshData`, `initializeFromServer`, `switchChild` (handle `normalized.shop`) | `TelegramChildRewards.svelte:3`, `TelegramChildShell.svelte:5`, `TelegramParentShell.svelte:5`, `TelegramParentRewards.svelte:5`, `TelegramParentTasks.svelte:5`, `TelegramImport.svelte:5`, `TelegramParentFamily.svelte:5`, `TelegramParentHeader.svelte:4`, `TelegramParentHome.svelte:6` |
| `apps/web/src/lib/services/serverContract.ts` | `normalizeShopItem`, `shop` field, `shopGroupOrder`/`childShopGroupOrder`/`hiddenShopGroupOrder` normalization | (indirect, via `bootstrap.ts`/`save.ts`) |
| `apps/web/src/lib/services/catalogItemViewModel.ts` | `buildTaskCatalogItemViewModel` (kept); `buildShopCatalogItemViewModel` (removed in P0-7) | web `TasksSection.svelte:34` only |
| `apps/web/src/lib/services/catalogViewState.ts` | `readCatalogViewState`, `writeCatalogViewState` (generic group/view state) | web `ShopSection`/`TasksSection` only (generic, keep) |
| `apps/web/src/lib/services/groupPrompt.ts` | `requestGroupName` (generic) | web `ShopSection`/`TasksSection` only (generic, keep) |
| `apps/web/src/lib/services/confirm.ts` | `confirmAction` | `TelegramChildShell.svelte:7`, `TelegramGroupManager.svelte:6`, `TelegramParentAccess.svelte:13`, `TelegramParentRewards.svelte:6`, `TelegramParentTasks.svelte:6` |

## Frontend shared stores

| File | Shared symbol(s) | Telegram consumer (file:line) |
| --- | --- | --- |
| `apps/web/src/lib/stores/app.ts` | `ShopItem` interface, `shopItems`, `baseData.products`, `catalog.rewards`, `CatalogRewardTemplate`, `Child.shopGroupOrder`/`childShopGroupOrder`/`hiddenShopGroupOrder` | `TelegramChildRewards.svelte:4`, `TelegramParentRewards.svelte:2`, `TelegramGroupManager.svelte:4`, `TelegramRewardForm.svelte:3`, `TelegramCatalogDetails.svelte:3`, `TelegramParentCatalog.svelte:3`, `TelegramReadyCatalog.svelte:4` |
| `apps/web/src/lib/stores/modal.ts` | `'shop-modal'` name (removed in P0-1 after `ShopModal` deletion) | web-only (remove the `'shop-modal'` literal in P0-1) |

## Frontend shared API client functions

| Function | Endpoint | Telegram consumer (file:line) |
| --- | --- | --- |
| `buyItem` | `POST /api/shop/{id}/purchase` | `TelegramParentRewards.svelte:4,91` |
| `requestItem` | `POST /api/shop/{id}/request` | `TelegramChildRewards.svelte:2,38` |
| `requestItemWithNote` | `POST /api/shop/{id}/request` | `TelegramChildRewards.svelte:2,38` |
| `importShopItems` | `POST /api/shop/import` | `TelegramImport.svelte:4,184` |
| `saveChildGroupOrder` | `POST /api/children/{id}/group-order` | `TelegramParentRewards.svelte:4,103`, `TelegramParentTasks.svelte:4,98` |

## Backend shared domain / API (web + Telegram + bot)

| File | Shared symbol(s) | Notes |
| --- | --- | --- |
| `apps/backend/.../resource/family/FamilyResource.java` | `purchaseItem` (`POST /api/shop/{itemId}/purchase`), `requestItemPurchase` (`POST /api/shop/{itemId}/request`), `importShopItems` (`POST /api/shop/import`) | Kept. `bulkShopItemAction`/`setRewardGoal` removed in P0-3. |
| `apps/backend/.../domain/model/ShopItemEntity.java` | shop item entity | Telegram bot + web + mini app |
| `apps/backend/.../domain/model/PurchaseRequestEntity.java` | purchase request entity | Telegram bot + web + mini app |
| `apps/backend/.../domain/model/PurchaseRequestStatus.java`, `PurchaseRequestType.java`, `RequestResolutionStatus.java` | request status enums | Telegram bot |
| `apps/backend/.../dto/response/ShopItemDto.java` | shop item DTO | `TelegramMenuText.java:4`, `TelegramQuickActionResponse` |
| `apps/backend/.../dto/response/TelegramQuickActionResponse.java` | `rewards` field | Telegram bot |
| `apps/backend/.../dto/response/FamilyDataResponse.java`, `FamilyDashboardShellResponse.java` | `shop` field | web + mini app |
| `apps/backend/.../dto/response/ChildDto.java` | `shopGroupOrder`, `childShopGroupOrder`, `hiddenShopGroupOrder` | web + mini app |
| `apps/backend/.../repository/ShopItemRepository.java`, `PurchaseRequestRepository.java` | shop repositories | Telegram bot + web |
| `apps/backend/.../service/telegram/TelegramQuickActionServiceImpl.java` | `requestReward` → `requestItemPurchase` | Telegram bot |
| `apps/backend/.../service/telegram/TelegramMenuBuilder.java`, `TelegramMenuText.java`, `TelegramChildOutcomeText.java`, `TelegramNotificationComposer.java`, `TelegramOutboxProcessor.java`, `TelegramRequestResolutionText.java`, `TelegramViewSupport.java` | shop/`PurchaseRequestEntity`/`ShopItemEntity` references | Telegram bot |

## Backend web-only (admin dashboard) — removed in P0-8

| File | Symbol | Notes |
| --- | --- | --- |
| `apps/backend/.../resource/admin/AdminRewardShopResource.java` | `GET /api/admin/analytics/reward-shop` | web admin only |
| `apps/backend/.../service/admin/AdminRewardShopService.java` | `getRewardShop` | web admin only |
| `apps/backend/.../dto/response/AdminRewardsResponse.java` | `RewardShopMetrics` | web admin only |
| `apps/backend/.../repository/AdminAnalyticsRepository.java` | `getRewardShopMetrics` | web admin only |
| `apps/backend/.../dto/response/AdminDashboardResponse.java` | `rewardShop` | web admin only |
| `apps/backend/.../dto/response/SuperAdminFamiliesResponse.java` | `shopCount` | super-admin (decide separately) |
