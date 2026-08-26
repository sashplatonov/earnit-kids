# Интерактивное демо магазина монет — исполнимый бэклог

## Цель

Заменить статическую read-only витрину /demo.html настоящим публичным live demo на /demo. Посетитель использует тот же детский магазин наград, что и в Mini App: видит баланс и прогресс, открывает награду, оставляет заметку, отправляет заявку, получает pending state и возвращает всё кнопкой Reset. Данные существуют только в памяти текущей вкладки: не достигают API, БД или persistent browser storage; reload начинает fixture заново.

## Архитектурные решения

- Единственный источник demo state — session-scoped fixture и mutation handler в apps/web/src/lib/features/live-demo. Он один инициализирует/reset existing appStore и shopItems; fixture immutable, действие возвращает snapshot shape, который уже принимает TelegramChildRewards.
- TelegramChildRewards остаётся единственным владельцем reward-list UI, grouping, sort, goal progress, request sheet и pending presentation. Он получает узкий reward-request action port через Svelte context; production fallback использует текущий shopApi.
- Static Parent demo удаляется без redirect и compatibility alias. /demo и /ru/demo — SvelteKit routes, не PUBLIC_PAGES artifacts. Static marketing pages после реализации могут ссылаться на route, но не должны снова генерировать demo.html.
- Mock action моделирует child request: добавляет один pending shop_purchase request и не списывает balance. Не создавать второй компонент магазина, test-only endpoint, global fetch monkey-patch или URL branch в production transport.

## Рекомендуемый порядок реализации

| Порядок | Задача | Приоритет | Зависит от | Причина |
| ---: | --- | --- | --- | --- |
| 1 | LCD-001 | P1 | — | Удалить неверный static contract. |
| 2 | LCD-002 | P1 | — | Изолировать production transport за action port. |
| 3 | LCD-003 | P1 | LCD-002 | Добавить и проверить memory source of truth. |
| 4 | LCD-004 | P1 | LCD-002, LCD-003 | Собрать anonymous route из реального компонента. |
| 5 | LCD-005 | P2 | LCD-001, LCD-004 | Вернуть discoverability через новую ссылку. |
| 6 | LCD-006 | P2 | LCD-004, LCD-005 | Дать browser proof и network isolation. |

## LCD-001: Удалить static Parent demo и старый URL-контракт

**Status:** TODO
**Priority:** P1
**Depends on:** —

**Точный scope:**

Удалить read-only Parent demo, static URL /demo.html и /ru/demo.html и всю цепочку генерации. Удалить SEO, tests и runbook assertions, объявляющие эти URL public pages.

**Файлы:**

- Modify apps/web/scripts/public-site/urls.js, urls.d.ts, generate.mjs, i18n.js, i18n.d.ts.
- Modify generated apps/web/static/public/styles.css и navigation/artifacts только через npm run generate:public.
- Delete apps/web/scripts/public-site/pages/demo.html, demo.js, demo.d.ts, demo-data.js, demo-data.d.ts.
- Delete apps/web/static/public/demo.html, ru/demo.html, demo.js, demo.d.ts, demo-data.js, demo-data.d.ts.
- Modify apps/web/src/routes/sitemap.xml/+server.ts, apps/web/tests/unit/publicSiteI18n.test.ts, publicSiteUrls.test.ts, sitemap.test.ts, apps/web/tests/e2e/workspace-entry.spec.ts, docs/operations/web-miniapp-access.md.
- Delete apps/web/tests/unit/publicParentDemo.test.ts. Search anchors: demo.html, Parent demo, Демо для родителя, demoData, DEMO_TABS.

**Цель:**

В production static site нет отдельной поддельной реализации магазина или старого demo HTML route.

### Outcome

Static generator создаёт шесть локализованных marketing pages; никакой artifact, sitemap, test или runbook не ожидает старый URL.

### Архитектурное решение

Static generator владеет только marketing documents; ему запрещено эмулировать Mini App state и сохранять compatibility к удалённому demo.

### Required changes

1. Удалить page key demo, special generator branch, demo i18n/data/CSS и generated files целиком.
2. Удалить demo из static nav/current CTA, metadata, sitemap и documentation; не добавлять redirect и не менять /app?context=rewards.
3. Обновить static expectations на шесть страниц и удалить read-only tabs scenario.
4. Регенерировать public output только generator command.

### Вне scope

Новый /demo route, mock session, production reward component, API, БД и auth.

### Критерии приёмки

- В generator, artifacts, sitemap, tests и runbook нет /demo.html, /ru/demo.html, Parent demo, Демо для родителя, demoData или DEMO_TABS.
- npm run generate:public создаёт 12 HTML files (6 × 2) с correct locale, canonical и hreflang metadata.
- Rewards marketing page сохраняет /app?context=rewards.

### Targeted validation

    cd apps/web && npm run generate:public
    cd apps/web && npm run test -- --run tests/unit/publicSiteI18n.test.ts tests/unit/publicSiteUrls.test.ts tests/unit/sitemap.test.ts
    cd apps/web && ! rg -n --hidden --glob '!.git' 'demo\\.html|Parent demo|Демо для родителя|demoData|DEMO_TABS' scripts/public-site static/public src/routes/sitemap.xml/+server.ts tests docs/operations/web-miniapp-access.md

### Commit

    git add apps/web/scripts/public-site apps/web/static/public apps/web/src/routes/sitemap.xml/+server.ts apps/web/tests/unit/publicSiteI18n.test.ts apps/web/tests/unit/publicSiteUrls.test.ts apps/web/tests/unit/sitemap.test.ts apps/web/tests/e2e/workspace-entry.spec.ts docs/operations/web-miniapp-access.md
    git rm apps/web/scripts/public-site/pages/demo.html apps/web/scripts/public-site/demo.js apps/web/scripts/public-site/demo.d.ts apps/web/scripts/public-site/demo-data.js apps/web/scripts/public-site/demo-data.d.ts apps/web/static/public/demo.html apps/web/static/public/ru/demo.html apps/web/static/public/demo.js apps/web/static/public/demo.d.ts apps/web/static/public/demo-data.js apps/web/static/public/demo-data.d.ts apps/web/tests/unit/publicParentDemo.test.ts
    git commit -m "refactor(web): remove static parent demo"

## LCD-002: Выделить action port заявки на награду

**Status:** TODO
**Priority:** P1
**Depends on:** —

**Точный scope:**

Убрать из TelegramChildRewards прямую зависимость от requestItem/requestItemWithNote, сохранив production behaviour. Ввести typed context contract с production fallback, чтобы demo подставлял action только для descendants.

**Файлы:**

- Modify apps/web/src/lib/components/telegram/TelegramChildRewards.svelte. Search anchors: requestItem, requestItemWithNote, submit(note, applyDataSnapshot, refreshData.
- Create apps/web/src/lib/telegram/services/rewardRequestActions.ts.
- Create apps/web/tests/unit/rewardRequestActions.test.ts.

**Цель:**

Production child rewards UI посылает те же shop API calls, но transport boundary становится точкой безопасной demo injection.

### Outcome

Без provider component получает production action, включая optional note и текущие result branches. Provider может передать action с тем же typed result contract без markup или URL changes.

### Архитектурное решение

Новый module владеет type и Svelte context key; shopApi остаётся HTTP owner. TelegramChildRewards не знает о demo, fixture или storage.

### Required changes

1. Описать action с item ID, child ID и nullable note; result покрывает existing ok, data, error и errorCode branches.
2. Реализовать production fallback через current shopApi functions, сохраняя distinction note/no-note.
3. В component заменить только выбор/вызов action; не менять sorting, sheet, accessibility, optimistic status или stale refresh.
4. Unit test доказывает production delegation и scoped provider override; не использовать global singleton override.

### Вне scope

Demo fixture, route, shopApi endpoints, parent rewards, task requests и persistent state.

### Критерии приёмки

- Existing authenticated child flow сохраняет endpoint, query childId, optional note и error handling.
- Без context provider behaviour равно production behaviour.
- Scoped override не влияет на sibling/production component tree и не требует URL condition.
- Focused test и npm run lint проходят без suppression.

### Targeted validation

    cd apps/web && npm run test -- --run tests/unit/rewardRequestActions.test.ts
    cd apps/web && npm run lint

### Commit

    git add apps/web/src/lib/components/telegram/TelegramChildRewards.svelte apps/web/src/lib/telegram/services/rewardRequestActions.ts apps/web/tests/unit/rewardRequestActions.test.ts
    git commit -m "refactor(web): isolate reward request actions"

## LCD-003: Создать memory-only session и fixture магазина

**Status:** TODO
**Priority:** P1
**Depends on:** LCD-002

**Точный scope:**

Добавить typed session owner: immutable initial child/reward fixture, initialise/reset existing stores и in-memory request action. Route и visible page сюда не входят.

**Файлы:**

- Create apps/web/src/lib/features/live-demo/liveCoinShopDemoSession.ts.
- Create apps/web/tests/unit/liveCoinShopDemoSession.test.ts.
- Read-only contracts: apps/web/src/lib/stores/app.ts, apps/web/src/lib/telegram/stores/shopItems.ts, apps/web/src/lib/telegram/stores/types.ts, apps/web/src/lib/services/bootstrap.ts anchor applyDataSnapshot.

**Цель:**

Одна детерминированная session реализует business state demo и гарантирует отсутствие transport/persistent storage.

### Outcome

Fixture содержит selected child, balance, at least one affordable reward, unaffordable next goal, more than one visible group и empty requests. Valid action создаёт pending shop_purchase с itemId, itemName, note, childId и stable in-memory ID; Reset возвращает original values без shared mutation references.

### Архитектурное решение

Module owns fixture cloning and demo mutation. Он обновляет только existing stores и выдаёт LCD-002 action contract; component и route не строят copies state.

### Required changes

1. Определить fixture через existing AppState, ShopItem и Request shapes с valid group/frequency fields.
2. Initialise/reset устанавливают appStore и shopItems вместе; teardown очищает demo-owned state, чтобы navigation не оставила fixture в global stores.
3. Action отвергает absent/inactive/unaffordable item и duplicate pending request; valid action не списывает balance, добавляет request и возвращает snapshot.
4. Exclude mutation initial template между actions/resets; не использовать fetch, shopApi, bootstrap load/refresh или browser persistence.
5. Unit tests: initial state, request c/без note, duplicate/unaffordable rejection, reset, fresh session и отсутствие storage/network dependency.

### Вне scope

Svelte rendering, i18n copy, route, E2E, parent approval и purchase.

### Критерии приёмки

- Reset возвращает тот же balance, reward set, group order и requests, что новая session.
- Pending item распознаётся current pendingIds logic; balance не меняется.
- Invalid item, недостаток монет и duplicate request не меняют stores.
- Module не импортирует API, shopApi, bootstrap load/refresh или browser persistence API.

### Targeted validation

    cd apps/web && npm run test -- --run tests/unit/liveCoinShopDemoSession.test.ts

### Commit

    git add apps/web/src/lib/features/live-demo/liveCoinShopDemoSession.ts apps/web/tests/unit/liveCoinShopDemoSession.test.ts
    git commit -m "feat(web): add in-memory coin shop demo session"

## LCD-004: Собрать public live demo из реального rewards component

**Status:** TODO
**Priority:** P1
**Depends on:** LCD-002, LCD-003

**Точный scope:**

Создать anonymous SvelteKit route /demo и demo shell, который on client mount owns session, installs provider и renders TelegramChildRewards exactly once с temporary-data notice и Reset. Добавить app-level EN/RU copy.

**Файлы:**

- Create apps/web/src/routes/demo/+page.svelte.
- Create apps/web/src/lib/features/live-demo/LiveCoinShopDemo.svelte.
- Modify actual web i18n message modules/types owning app.telegram child copy. Search anchor: app.telegram.childRewards.rewardRequestSent.
- Create apps/web/tests/unit/liveCoinShopDemo.test.ts if component renderer already exists; otherwise browser rendering proof belongs to LCD-006.
- Read-only layout contract: apps/web/src/routes/+layout.svelte и +layout.server.ts.

**Цель:**

Anonymous visitor получает usable live coin shop without login, Telegram bootstrap или server data.

### Outcome

/demo uses normal locale resolution; /ru/demo exposes Russian title/notice/reset. List, group subnav, sort toolbar, progress, sheet и pending feedback originate from TelegramChildRewards.

### Архитектурное решение

LiveCoinShopDemo owns composition/lifecycle, not reward UI logic. Route не loads server data, не calls initializeFromServer и не добавляет static artifact.

### Required changes

1. On mount create session, initialise stores и install provider; on destroy release/reset demo-owned state.
2. Render heading, explicit temporary/no-server notice, Reset и single TelegramChildRewards. Reset announces completion through polite live feedback.
3. Use app i18n, not static public dictionaries; EN/RU shape parity must pass.
4. Reset is semantic, keyboard reachable, visible focus and minimum 44×44px; preserve current request-sheet focus trap.
5. Route не требует session/cookie/Telegram globals и не добавляется в PUBLIC_PAGES/sitemap.

### Вне scope

Marketing navigation, static generator, server load/auth, PWA caching и duplicate reward markup.

### Критерии приёмки

- Direct /demo и /ru/demo render anonymous without loading/retry API state.
- Demo files не содержат copied reward rows/sheet; visible shop features come from shared component.
- Reset restores fixture and announces result; controls work at 320px without horizontal overflow.
- Russian page has Russian user copy; i18n parity/type validation passes.

### Targeted validation

    cd apps/web && npm run test -- --run tests/unit/liveCoinShopDemoSession.test.ts tests/unit/liveCoinShopDemo.test.ts
    cd apps/web && npm run lint
    cd apps/web && npm run build

### Commit

    git add apps/web/src/routes/demo/+page.svelte apps/web/src/lib/features/live-demo/LiveCoinShopDemo.svelte apps/web/src/lib/i18n apps/web/tests/unit/liveCoinShopDemo.test.ts
    git commit -m "feat(web): render public live coin shop demo"

## LCD-005: Сделать новый demo доступным с marketing site

**Status:** TODO
**Priority:** P2
**Depends on:** LCD-001, LCD-004

**Точный scope:**

После появления route добавить локализованную ссылку на /demo в static marketing navigation/rewards CTA, не возвращая demo в PUBLIC_PAGES и не создавая demo.html.

**Файлы:**

- Modify apps/web/scripts/public-site/generate.mjs, i18n.js, i18n.d.ts и template.html, если navigation должен поддержать non-PUBLIC_PAGES link.
- Modify apps/web/tests/unit/publicSiteI18n.test.ts и publicSiteUrls.test.ts.
- Regenerate apps/web/static/public/* и apps/web/static/public/ru/*.

**Цель:**

Посетитель public site находит настоящий live demo, но static site не становится его implementation owner.

### Outcome

English pages link to /demo, Russian to /ru/demo, with localized accessible label. Static language/canonical tests не считают /demo artifact и не ждут demo.html.

### Архитектурное решение

Navigation entry — external-to-static route link; он не входит в PUBLIC_PAGES, sitemap loop или generator page list. Localization uses explicit local paths.

### Required changes

1. Добавить localized link/CTA только после LCD-004; rewards copy должна правдиво описывать interactive temporary demo.
2. Сохранить /app?context=rewards как separate sign-in destination с distinct label.
3. Update generated artifact tests to assert new live links и explicit absence legacy artifacts.
4. Run generator; не помещать live demo script/state в static/public.

### Вне scope

Изменение route/session, SEO sitemap listing, static demo recreation и login flow.

### Критерии приёмки

- Every intended navigation surface exposes one localized /demo or /ru/demo link with accessible text.
- Rewards page distinguishes Try live demo from Sign in to your own shop.
- PUBLIC_PAGES still has six entries, demo.html artifacts absent и sitemap excludes live demo.
- Generated HTML has no filesystem/public demo URL.

### Targeted validation

    cd apps/web && npm run generate:public
    cd apps/web && npm run test -- --run tests/unit/publicSiteI18n.test.ts tests/unit/publicSiteUrls.test.ts tests/unit/sitemap.test.ts
    cd apps/web && ! test -e static/public/demo.html && ! test -e static/public/ru/demo.html

### Commit

    git add apps/web/scripts/public-site apps/web/static/public apps/web/tests/unit/publicSiteI18n.test.ts apps/web/tests/unit/publicSiteUrls.test.ts
    git commit -m "feat(web): link marketing site to live coin shop demo"

## LCD-006: Добавить browser proof demo и network isolation

**Status:** TODO
**Priority:** P2
**Depends on:** LCD-004, LCD-005

**Точный scope:**

Добавить focused Playwright scenario public /demo. Test не устанавливает Telegram globals/cookies/API fulfills; захватывает network до navigation и считает любой /api/ request failure.

**Файлы:**

- Create apps/web/tests/e2e/live-coin-shop-demo.spec.ts.
- Modify apps/web/tests/e2e/workspace-entry.spec.ts только для удаления obsolete static-demo assertions, если LCD-001 не удалил их полностью.
- Modify static unit tests только если LCD-005 оставляет link assertions.

**Цель:**

Независимо доказать route rendering, shared component interaction, reset/reload semantics, locale/mobile accessibility и отсутствие server traffic.

### Outcome

E2E opens production preview anonymous, submits note through existing sheet, sees pending/disabled repeat action, resets, reloads и confirms initial fixture. Captured /api/ list empty throughout.

### Архитектурное решение

Browser test owns proof focus, modal behaviour, viewport geometry и network isolation. It must never stub API; unit tests do not replace it.

### Required changes

1. Capture every /api/ request before page.goto and fail when collection is non-empty.
2. Find controls by role/name; submit note, assert pending/duplicate prevention, Reset restored state и reload fixture.
3. Verify /ru/demo Russian labels and direct anonymous entry.
4. At 320px verify no document horizontal overflow, 44px reset/request targets и visible keyboard focus.
5. Keep suite separate from authenticated Telegram tests; do not execute parent approval/purchase.

### Вне scope

Remote deployment/CI, native Telegram client, PWA offline cache и physical-device validation.

### Критерии приёмки

- Chromium E2E passes without auth, Telegram SDK, API mock или API traffic.
- Request with note, pending state, duplicate prevention, Reset и reload have expected states.
- /ru/demo и 320px meet locale, focus, touch target/no-overflow requirements.
- Static suite no longer contains read-only Parent demo scenario.

### Targeted validation

    cd apps/web && npm run test:e2e -- --project=chromium tests/e2e/live-coin-shop-demo.spec.ts
    cd apps/web && npm run test -- --run tests/unit/rewardRequestActions.test.ts tests/unit/liveCoinShopDemoSession.test.ts tests/unit/publicSiteI18n.test.ts tests/unit/publicSiteUrls.test.ts tests/unit/sitemap.test.ts

### Commit

    git add apps/web/tests/e2e/live-coin-shop-demo.spec.ts apps/web/tests/e2e/workspace-entry.spec.ts apps/web/tests/unit/publicSiteI18n.test.ts apps/web/tests/unit/publicSiteUrls.test.ts apps/web/tests/unit/sitemap.test.ts
    git commit -m "test(web): verify isolated live coin shop demo"

## Финальные quality gates

    cd apps/web && npm run generate:public
    cd apps/web && npm run lint && npm run test && npm run build
    cd apps/web && npm run test:e2e -- --project=chromium tests/e2e/live-coin-shop-demo.spec.ts
    git diff --check
    git status --short

Локальные checks доказывают source, build и browser behavior. Они не являются proof deployment/CI, Telegram Mini App client или физического устройства; эти уровни проверяются отдельно после deploy.
