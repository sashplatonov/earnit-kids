# Полное live-демо магазина монет — исполнимый бэклог

## Цель

Превратить публичные `/demo` и `/ru/demo` из изолированной витрины одной детской вкладки «Награды» в работающее live-демо настоящего родительского рабочего пространства. Посетитель видит и использует те же верхний header и вкладки «Главная», «Задания», «Награды», «Семья», что в production (`TelegramParentShell`): выбирает ребёнка, выдаёт и тратит монеты, создаёт/редактирует/архивирует задания и награды, отправляет и одобряет/отклоняет заявки, работает с группами, каталогом и семейным языком. Доступен также переключаемый child-view, чтобы реально пройти путь «ребёнок запросил → родитель решил» в одной вкладке.

Все demo-данные изолированы от API, БД, Telegram, persistent browser storage и production session. Они живут только в памяти текущей вкладки; Reset и полная перезагрузка возвращают исходную fixture. Внешние интеграции (отправка e-mail/Telegram, push, фактический CSV-import) не должны имитировать успешную внешнюю доставку: их demo-UI объясняет ограничение, а не вызывает сеть.

## Зафиксированный эталон и граница parity

- Эталон — приложенный экран родительского интерфейса: header с выбранным ребёнком и балансом, tab bar «Главная / Задания / Награды / Семья», а во вкладке «Семья» — плитки, список детей и настройка «Язык семьи». Это уже соответствует production-компонентам `TelegramParentHeader`, `TelegramTabBar` и `TelegramParentFamily`; новый отдельный дизайн или копия разметки запрещены.
- Под «всеми функциями магазина монет» понимаются все интерактивные действия в production-экранах, доступных через перечисленные вкладки, которые изменяют семейные task/reward/coin/request/group/child данные. Они должны менять demo state и давать те же видимые success/error/disabled состояния.
- Доступные в production интеграционные настройки остаются видимыми, но вместо сети показывают локальное demo-сообщение. Это исключение не относится к языку: EN/RU переключается по настоящим локализованным маршрутам и полностью обновляет user-facing copy.

## Архитектурные решения

- Единственный источник demo state — новый session owner в `apps/web/src/lib/features/live-demo/`. Он хранит каноническую полную fixture, производит immutable snapshots, atomic mutations и reset. `appStore`, `shopItems` и `catalogRewards` остаются адаптерами представления, а не вторым источником истины.
- Production transport остаётся владельцем HTTP. Компоненты получают узкие typed action ports через scoped Svelte context: bootstrap/child switch, requests, tasks, rewards, groups, coins, history и family settings. Demo provider реализует те же result contracts в памяти. Нельзя добавлять `if (pathname === '/demo')` в API-клиент, global fetch mock, test endpoint или копировать production-компоненты.
- Demo composition повторно использует `TelegramParentShell`, `TelegramChildShell`, `TelegramParentHome`, `TelegramParentTasks`, `TelegramParentRewards`, `TelegramParentFamily` и child components. Shell получает action port/lifecycle вместо прямого `initializeFromServer`; данные и поведение не дублируются в новой `LiveCoinShopDemo`.
- Locale — presentation-level state, владелец которого остаётся SvelteKit i18n и `LocaleSwitcher`. Для `/demo` его нельзя сохранять через family API. Обычный non-family-managed switcher меняет cookie и переходит между `/demo` и `/ru/demo`; новая страница честно начинает fixture заново.
- Стабильные machine IDs не локализуются. Все видимые demo fixture names, notes, labels, errors и destructive-confirmation copy имеют явные EN/RU ключи/поля; dynamic `MessageKey` и fallback на русский текст недопустимы.
- Текущий `LCD-R-001` из `docs/live-coin-shop-demo-review-backlog.md` — обязательное предварительное исправление test runner, но не заменяет ни одну из задач parity: команды с `--project=chromium` должны быть исполнимы до финального E2E proof.

## Рекомендуемый порядок реализации

| Порядок | Задача | Приоритет | Зависит от | Причина |
| ---: | --- | --- | --- | --- |
| 0 | LCD-R-001 | P2 | — | Сделать уже заявленный Chromium E2E command исполнимым. |
| 1 | LCDP-001 | P1 | LCD-R-001 | Убрать прямой bootstrap/transport из shell и создать один injection boundary. |
| 2 | LCDP-002 | P1 | LCDP-001 | Построить полную fixture и транзакционные in-memory операции. |
| 3 | LCDP-003 | P1 | LCDP-001, LCDP-002 | Перевести production-компоненты на ports без изменения production behaviour. |
| 4 | LCDP-004 | P1 | LCDP-002, LCDP-003 | Собрать parent shell и все видимые вкладки из реальных компонентов. |
| 5 | LCDP-005 | P1 | LCDP-003, LCDP-004 | Дать полную live parity задачам, монетам, наградам и заявкам. |
| 6 | LCDP-006 | P1 | LCDP-003, LCDP-004 | Дать parity семье, нескольким детям и реальному EN/RU переходу. |
| 7 | LCDP-007 | P2 | LCDP-004, LCDP-005, LCDP-006 | Закрыть доступность и browser proof всех tab flows. |
| 8 | LCDP-008 | P2 | LCDP-007 | Зафиксировать public discoverability и финальные регрессионные границы. |

## LCDP-001: Выделить контекст жизненного цикла workspace и demo action ports

**Status:** DONE
**Priority:** P1
**Depends on:** LCD-R-001

**Точный scope:**

Создать typed scoped contract, через который parent/child shell и дочерние экраны получают loading, refresh, child switching и domain mutations. Production provider обязан делегировать существующим `bootstrap.ts`, `api.ts`, `shopApi.ts` и `telegramActivity.ts` без изменения endpoint, auth или snapshot normalization.

**Файлы:**

- Create `apps/web/src/lib/features/workspace/workspaceActions.ts`.
- Modify `apps/web/src/lib/components/telegram/TelegramParentShell.svelte`, `TelegramChildShell.svelte` и `TelegramParentHeader.svelte`.
- Modify `apps/web/src/lib/services/bootstrap.ts` только для экспорта уже существующей production delegation, если это необходимо для typed port; не переносить туда demo code.
- Create `apps/web/tests/unit/workspaceActions.test.ts`.
- Search anchors для следующей миграции: `initializeFromServer`, `refreshData`, `switchChild`, `applyDataSnapshot`, `loadTelegramHistory` в `apps/web/src/lib/components/telegram/`.

### Outcome

Обычные `/app` и `/telegram` используют прежний HTTP lifecycle. Demo может смонтировать тот же shell с provider, который не выполняет сетевых вызовов.

### Required changes

1. Описать минимальные result/input types для lifecycle, snapshot application, history pagination и child selection; результат должен сохранять текущие success/error/stale branches компонентов.
2. Ввести production fallback и scoped provider по образцу существующего `rewardRequestActions.ts`; provider влияет только на descendants.
3. Перевести shell/header с прямых imports на port. Не менять структуру tabs, URL context parsing, loading/error copy или production auth.
4. Unit tests доказывают production delegation и scoped override, включая отсутствие влияния на sibling tree.

### Вне scope

Fixture, demo route, операции создания/редактирования, локализация, изменение API contracts.

### Критерии приёмки

- Без provider authenticated parent и child flow сохраняют прежние запросы, childId, refresh semantics и error states.
- Scoped demo provider может инициализировать, сбросить и переключить ребёнка без `fetch`, API import или `localStorage`.
- У shell нет URL-based demo branch и нет второго набора tab markup.

### Targeted validation

```bash
cd apps/web && npm run test -- --run tests/unit/workspaceActions.test.ts tests/unit/rewardRequestActions.test.ts
cd apps/web && npm run lint
```

### Commit

```bash
git add apps/web/src/lib/features/workspace/workspaceActions.ts apps/web/src/lib/components/telegram/TelegramParentShell.svelte apps/web/src/lib/components/telegram/TelegramChildShell.svelte apps/web/src/lib/components/telegram/TelegramParentHeader.svelte apps/web/src/lib/services/bootstrap.ts apps/web/tests/unit/workspaceActions.test.ts
git commit -m "refactor(web): isolate workspace actions"
```

## LCDP-002: Создать полную каноническую fixture и in-memory семейный движок

**Status:** DONE
**Priority:** P1
**Depends on:** LCDP-001

**Точный scope:**

Заменить ограниченную fixture одного ребёнка в `liveCoinShopDemoSession.ts` полной детерминированной demo-семьёй и pure mutation engine. Fixture должна покрывать оба состояния покупки (доступна/недоступна), несколько групп, активные/архивные записи, pending/approved/rejected request, историю, два ребёнка и каталог.

**Файлы:**

- Modify `apps/web/src/lib/features/live-demo/liveCoinShopDemoSession.ts`.
- Create `apps/web/src/lib/features/live-demo/liveCoinShopDemoFixture.ts` и `liveCoinShopDemoActions.ts`.
- Modify `apps/web/src/lib/telegram/stores/shopItems.ts` и `apps/web/src/lib/telegram/stores/rewards.ts` только если текущему store не хватает явного reset API.
- Create `apps/web/tests/unit/liveCoinShopDemoActions.test.ts`.
- Read-only shapes: `apps/web/src/lib/stores/app.ts`, `apps/web/src/lib/telegram/stores/types.ts`, `apps/web/src/lib/services/serverContract.ts`.

### Outcome

Любая demo mutation меняет только canonical in-memory state, затем атомарно публикует согласованный snapshot в существующие stores. Переключение ребёнка показывает его собственные balance/tasks/rewards/requests/history; Reset/reload всегда возвращают неизменённую fixture.

### Required changes

1. В fixture задать стабильные IDs, EN/RU presentation data, два активных ребёнка, реалистичные лимиты/period progress, groups/order/hidden groups, parent permission `family_admin`, reward/task catalog и события истории.
2. Реализовать transaction-safe operations: request/cancel/approve/reject, award/spend coins, complete task, create/edit/archive/delete task or reward, catalog-to-family add, group order/visibility/delete, child add/activate/select.
3. Проверять ownership/current child, inactive/unknown item, affordability, period limits и duplicate pending requests до mutation; rejected action не меняет snapshot.
4. Синхронизировать balance ребёнка и header balance, запросы, history, item/task last-used/period progress и badges одним commit snapshot.
5. Не импортировать production HTTP clients, browser storage, Telegram SDK или time-dependent random data. Clock/ID generator должны быть deterministic для tests.

### Вне scope

Svelte layout, production database/API, фактические invitations/notifications/import.

### Критерии приёмки

- Parent может последовательно выдать монеты, одобрить child request и увидеть верные balance/history/request status без reload и сети.
- Child request после parent approval/rejection имеет тот же pending/terminal presentation, что production; повторный request obeys availability/limit rules.
- Переключение между двумя детьми не смешивает данные; reset устраняет все созданные записи и возвращает исходные order/balances.
- Unit suite покрывает successful и rejected каждой категории mutation и подтверждает, что engine не имеет network/storage dependency.

### Targeted validation

```bash
cd apps/web && npm run test -- --run tests/unit/liveCoinShopDemoSession.test.ts tests/unit/liveCoinShopDemoActions.test.ts
```

### Commit

```bash
git add apps/web/src/lib/features/live-demo/liveCoinShopDemoSession.ts apps/web/src/lib/features/live-demo/liveCoinShopDemoFixture.ts apps/web/src/lib/features/live-demo/liveCoinShopDemoActions.ts apps/web/src/lib/telegram/stores/shopItems.ts apps/web/src/lib/telegram/stores/rewards.ts apps/web/tests/unit/liveCoinShopDemoSession.test.ts apps/web/tests/unit/liveCoinShopDemoActions.test.ts
git commit -m "feat(web): add complete in-memory demo family"
```

## LCDP-003: Перевести production mutation components на узкие ports

**Status:** DONE
**Priority:** P1
**Depends on:** LCDP-001, LCDP-002

**Точный scope:**

Вынести из production UI все прямые calls, которые меняют/загружают demo-visible family data, в typed scoped ports. Это расширяет LCD-002 reward-request port; не создаёт monolithic `demoApi` и не изменяет backend.

**Файлы:**

- Create `apps/web/src/lib/telegram/services/taskActions.ts`, `rewardActions.ts`, `requestActions.ts`, `familyActions.ts`, `historyActions.ts` или эквивалентные узкие modules по существующему `rewardRequestActions.ts` pattern.
- Modify `apps/web/src/lib/components/telegram/TelegramChildTasks.svelte`, `TelegramChildRequestList.svelte`, `TelegramRequestList.svelte`, `TelegramParentHome.svelte`, `TelegramParentTasks.svelte`, `TelegramParentRewards.svelte`, `TelegramParentFamily.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramParentCatalog.svelte`, `TelegramTaskForm.svelte`, `TelegramRewardForm.svelte`, `TelegramGroupManager.svelte` только в точках существующих save/import calls.
- Create focused tests under `apps/web/tests/unit/` for each new port.
- Search anchors: imports from `$lib/services/api`, `$lib/telegram/services/shopApi`, `$lib/services/bootstrap`, `$lib/services/telegramActivity`, `$lib/services/save` in the listed components.

### Outcome

Каждая интерактивная control на реальных parent/child tabs обращается к одному production fallback либо demo override с одинаковой visible result contract. Никакая control не «успешна» только потому, что локально поменяла компонентный array.

### Required changes

1. Разделить actions по ownership: child request/cancel; parent decision/coins; task/reward CRUD and direct complete/grant; group/catalog; child/family/integration settings; history read.
2. Сохранить existing confirm modal, busy, retry, focus restoration, permission checks и `applyDataSnapshot` semantics; demo provider возвращает snapshot из LCDP-002.
3. Для integrations (Telegram/email/push/import) port возвращает explicit `DEMO_UNAVAILABLE` result. Компонент показывает локализованную правдивую notice, не network error и не generated real invitation/link.
4. Production fallbacks должны пройти их текущий call path unchanged; не переносить action business rules в Svelte components.

### Вне scope

Новый UI, fixture contents, фактические сторонние интеграции, любые backend endpoints.

### Критерии приёмки

- `rg` по перечисленным component files не находит прямого mutation/read transport import после миграции, кроме typed port modules.
- Все production branches сохраняют endpoint/error semantics; demo branches не отправляют `/api/` request.
- Permission `viewer` не получает edit/grant controls; `family_admin` demo получает тот же набор controls, что production.
- API error, stale state, cancel, duplicate и unavailable integration имеют distinguishable localized feedback.

### Targeted validation

```bash
cd apps/web && npm run test -- --run tests/unit/taskActions.test.ts tests/unit/rewardActions.test.ts tests/unit/requestActions.test.ts tests/unit/familyActions.test.ts tests/unit/historyActions.test.ts
cd apps/web && npm run lint
```

### Commit

```bash
git add apps/web/src/lib/telegram/services apps/web/src/lib/components/telegram apps/web/tests/unit
git commit -m "refactor(web): route workspace mutations through action ports"
```

## LCDP-004: Собрать public demo из настоящего родительского workspace shell

**Status:** DONE
**Priority:** P1
**Depends on:** LCDP-002, LCDP-003

**Точный scope:**

Сделать `LiveCoinShopDemo` composition/lifecycle owner полной demo-семьи и отрендерить настоящий `TelegramParentShell`, включая header и четыре верхние tabs. Добавить «Посмотреть как ребёнок» с существующим child shell, возвратом в parent view и одним Reset для всей семьи.

**Файлы:**

- Modify `apps/web/src/lib/features/live-demo/LiveCoinShopDemo.svelte`.
- Modify `apps/web/src/routes/demo/+page.svelte`, `apps/web/src/routes/demo/+page.server.ts` только при необходимости сохранить anonymous public route contract.
- Modify `apps/web/src/lib/components/telegram/TelegramParentShell.svelte`, `TelegramChildShell.svelte`, `TelegramParentHeader.svelte` только для injectable lifecycle/on-view props, если LCDP-001 их ещё не добавила.
- Create `apps/web/tests/unit/liveCoinShopDemoWorkspace.test.ts`.

### Outcome

Первый экран `/demo` повторяет production parent workspace, а не второй дизайн страницы наград: selected-child header, balance, tab bar and panels визуально и семантически происходят из тех же components. Reset остаётся заметным, но не меняет tab hierarchy и не скрывает product navigation.

### Required changes

1. На mount создать session/provider, initialize canonical state, render parent shell once; on destroy очистить только demo-owned stores.
2. Передать `family_admin` и demo callbacks, чтобы header child selector и eye/view-as-child control работали; не показывать login/logout/public-site side effects.
3. Сохранить anonymous direct EN/RU route, normal head metadata, temporary-data notice and aria-live reset feedback. Удалить styling/layout, который подменяет workspace своим card/page design.
4. Reset закрывает open demo sheets/modals via normal component lifecycle, возвращает parent view and selected child to fixture defaults, не вызывает page navigation/network.

### Вне scope

Изменение public marketing generator/sitemap, auth, PWA/offline, новая навигационная система.

### Критерии приёмки

- На desktop и 320px `/demo` содержит те же tab roles/labels «Главная», «Задания», «Награды», «Семья», что production `TelegramParentShell`, и каждый tab переключает свой real panel.
- Header selector меняет current child и balance; eye control открывает настоящий child workspace и имеет accessible возврат к parent.
- `/demo` и `/ru/demo` не требуют cookie/session/Telegram globals и не вызывают API при initial render/reset/tab switch.
- Demo files не содержат copied rows/forms/tab markup из parent or child components.

### Targeted validation

```bash
cd apps/web && npm run test -- --run tests/unit/liveCoinShopDemoWorkspace.test.ts tests/unit/liveCoinShopDemoActions.test.ts
cd apps/web && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/src/lib/features/live-demo/LiveCoinShopDemo.svelte apps/web/src/routes/demo apps/web/src/lib/components/telegram/TelegramParentShell.svelte apps/web/src/lib/components/telegram/TelegramChildShell.svelte apps/web/src/lib/components/telegram/TelegramParentHeader.svelte apps/web/tests/unit/liveCoinShopDemoWorkspace.test.ts
git commit -m "feat(web): render full workspace in live demo"
```

## LCDP-005: Реализовать live parity жизненного цикла монет, заданий, наград и заявок

**Status:** DONE
**Priority:** P1
**Depends on:** LCDP-003, LCDP-004

**Точный scope:**

Завершить integration существующих реальных task/reward/request components с in-memory actions так, чтобы каждый UI flow во вкладках «Главная», «Задания», «Награды» работал от начала до результата и отражался в связанных экранах.

**Файлы:**

- Modify только компоненты, перечисленные в LCDP-003, и их focused tests.
- Create `apps/web/tests/e2e/live-coin-shop-demo-workflows.spec.ts`.
- Search anchors: `adminAwardCoins`, `earnCoins`, `buyItem`, `requestItem`, `approveRequest`, `rejectRequest`, `scheduleSave`, `saveChildGroupOrder`.

### Outcome

Посетитель проходит связанный сценарий: создаёт/редактирует награду или задание, ребёнок оставляет note request, parent его решает, монеты/лимиты/history/list/badges обновляются сразу; direct parent grant/complete также работают и отклоняются при boundary errors.

### Required changes

1. Покрыть forms, overflow menus, archive/delete confirmations, group manager/order/hide/delete, sort/filter, ready catalog add и direct actions без local-only обхода port.
2. Покрыть coin adjust positive/negative (если production form разрешает), task complete, reward grant, insufficient funds, inactive record, frequency limit, duplicate pending и stale/error message contract.
3. Обновить child request list cancel flow и parent request list approve/reject так, чтобы `appStore`/history are synchronized once and no stale UI remains after tab changes.
4. Добавить E2E сценарии по roles/names, не CSS internals: parent action, child request with note, approve/reject, result in history and reset.

### Вне scope

Family invitations, e-mail/push delivery, data import, deployment proof.

### Критерии приёмки

- Каждая mutable control в «Главная / Задания / Награды» либо завершается observable in-memory domain change, либо показывает проверяемую localized error; ни одна не делает silent no-op.
- Balance never becomes inconsistent between header, current child, next goal and history; request decision is idempotent after first decision.
- At least one E2E test proves cross-tab parent↔child lifecycle and another proves CRUD/group/limit boundary.
- Во всём flow captured `/api/` requests остаются пустыми.

### Targeted validation

```bash
cd apps/web && npm run test -- --run tests/unit/liveCoinShopDemoActions.test.ts
cd apps/web && npm run test:e2e -- --project=chromium tests/e2e/live-coin-shop-demo-workflows.spec.ts
```

### Commit

```bash
git add apps/web/src/lib/components/telegram apps/web/src/lib/features/live-demo apps/web/tests/unit apps/web/tests/e2e/live-coin-shop-demo-workflows.spec.ts
git commit -m "feat(web): support live demo coin shop workflows"
```

## LCDP-006: Реализовать parity вкладки «Семья» и работающий EN/RU switcher

**Status:** DONE
**Priority:** P1
**Depends on:** LCDP-003, LCDP-004

**Точный scope:**

Подключить `TelegramParentFamily` к demo actions, чтобы родительские child-management paths и «Язык семьи» были видимы и работали в public demo, не вызывая production APIs. Установить в demo non-family-managed `LocaleSwitcher` contract, который меняет locale route/cookie, а не family locale on server.

**Файлы:**

- Modify `apps/web/src/lib/components/telegram/TelegramParentFamily.svelte` и `apps/web/src/lib/components/LocaleSwitcher.svelte` только для injected locale/action behaviour.
- Modify `apps/web/src/lib/features/live-demo/LiveCoinShopDemo.svelte` and action provider.
- Modify `apps/web/src/lib/i18n/messages/en/app.ts`, `apps/web/src/lib/i18n/messages/ru/app.ts`, and strict message types as required.
- Create `apps/web/tests/unit/demoLocaleSwitcher.test.ts` and `apps/web/tests/e2e/live-coin-shop-demo-family.spec.ts`.

### Outcome

Family tab matches the supplied production layout and interactions: summary tiles, children/current child, child management and settings are real components. Russian/English toggle changes `/demo` ↔ `/ru/demo`, `<html lang>`, title and all displayed demo/prod copy; it never attempts `PUT /api/family/locale`.

### Required changes

1. Implement demo actions for select/add/activate/archive child and the non-external family changes represented in fixture; update header, tabs and child view atomically.
2. For external panels (Telegram/e-mail/notifications/import), render existing entry control where safe but return `DEMO_UNAVAILABLE` local explanation; do not fabricate invitation tokens, mutate clipboard or initiate browser permission.
3. Add an explicit mode/handler to `LocaleSwitcher` rather than infer demo from URL. Production `familyManaged` must retain family API semantics; demo handler must use existing `swapLocale`/cookie navigation mechanism.
4. Translate every new demo-unavailable/state string with EN/RU shape parity; fixture-visible content must have locale-specific presentation.

### Вне scope

Изменение `FamilyLocale` backend persistence, actual Telegram/e-mail/push integration, user account changes outside demo.

### Критерии приёмки

- Family tab contains the same production component structure and accessible child/settings controls as `/app`; child changes immediately affect header and all scoped tab data.
- Clicking RU/EN in demo changes path and page language without an API request; reload starts default fixture as promised, in selected route locale.
- In an authenticated production workspace `familyManaged` switcher still calls the existing family-locale endpoint and does not become a cookie-only switch.
- All external demo controls are explicitly unavailable with localized feedback, never falsely successful.

### Targeted validation

```bash
cd apps/web && npm run test -- --run tests/unit/demoLocaleSwitcher.test.ts tests/unit/liveCoinShopDemoActions.test.ts
cd apps/web && npm run test:e2e -- --project=chromium tests/e2e/live-coin-shop-demo-family.spec.ts
cd apps/web && npm run lint && npm run build
```

### Commit

```bash
git add apps/web/src/lib/components/telegram/TelegramParentFamily.svelte apps/web/src/lib/components/LocaleSwitcher.svelte apps/web/src/lib/features/live-demo apps/web/src/lib/i18n apps/web/tests/unit/demoLocaleSwitcher.test.ts apps/web/tests/e2e/live-coin-shop-demo-family.spec.ts
git commit -m "feat(i18n): localize live demo family workspace"
```

## LCDP-007: Доказать UI parity, accessibility и полную network isolation в браузере

**Status:** TODO
**Priority:** P2
**Depends on:** LCDP-004, LCDP-005, LCDP-006

**Точный scope:**

Расширить focused public E2E в доказательство всех верхних tabs и основных mutation flows. Это regression proof, не новый demo implementation.

**Файлы:**

- Modify `apps/web/tests/e2e/live-coin-shop-demo.spec.ts`.
- Modify/Create `apps/web/tests/e2e/live-coin-shop-demo-workflows.spec.ts`, `live-coin-shop-demo-family.spec.ts`.
- Modify `apps/web/playwright.config.ts` только если LCD-R-001 ещё не добавил named Chromium project.

### Outcome

Браузерные tests открывают anonymous demo без stubs/cookies/Telegram globals, проходят parent and child tabs, EN/RU switch and reset/reload, а capture заранее ловит любой `/api/` call.

### Required changes

1. Вынести общий request capture/target-size helper, включить его до `page.goto` в каждом suite, fail с полным списком unexpected requests.
2. Assert role/tab semantics, keyboard Arrow/Home/End navigation where supported by `TelegramTabBar`, visible focus, dialog focus return, 44×44px targets and no horizontal overflow at 320px.
3. На 1280px сверить screenshot-level structural parity через roles/names/content: header child/balance, four parent tabs, family language setting, real reward rows/actions; не snapshot entire page against a brittle bitmap.
4. Test reset from a mutated state and reload in both locales; assert fixture restoration and no cross-test state leakage.

### Вне scope

Remote CI/deployment, Telegram Mini App, offline/PWA, physical-device proof.

### Критерии приёмки

- Focused Chromium suite covers all four parent tabs, child view, major money lifecycle, group/catalog, family child flow, EN/RU and Reset/reload.
- All tests use user-facing roles/names and contain no API fulfill/route mock; captured API list is empty.
- Desktop and 320px prove geometry/accessibility rather than treating a passing build as UI proof.

### Targeted validation

```bash
cd apps/web && npm run test:e2e -- --project=chromium tests/e2e/live-coin-shop-demo.spec.ts tests/e2e/live-coin-shop-demo-workflows.spec.ts tests/e2e/live-coin-shop-demo-family.spec.ts
```

### Commit

```bash
git add apps/web/playwright.config.ts apps/web/tests/e2e/live-coin-shop-demo.spec.ts apps/web/tests/e2e/live-coin-shop-demo-workflows.spec.ts apps/web/tests/e2e/live-coin-shop-demo-family.spec.ts
git commit -m "test(web): cover full live demo workspace"
```

## LCDP-008: Обновить discoverability, контракт и финальные quality gates

**Status:** TODO
**Priority:** P2
**Depends on:** LCDP-007

**Точный scope:**

Обновить public copy/tests и документацию так, чтобы ссылка на `/demo` честно обещала интерактивное demo-рабочее пространство, а не только детский rewards list. Сохранить отсутствие static `demo.html` и sitemap listing.

**Файлы:**

- Modify `apps/web/scripts/public-site/i18n.js`, `generate.mjs`, `template.html` only where existing demo CTA is defined.
- Regenerate matching `apps/web/static/public/*` artifacts through `npm run generate:public`.
- Modify `apps/web/tests/unit/publicSiteI18n.test.ts`, `publicSiteUrls.test.ts`, `sitemap.test.ts`.
- Modify `docs/live-coin-shop-demo-backlog.md` only to append actual completion evidence; do not rewrite its DONE history.
- Modify this file statuses/evidence only after each task is accepted and committed.

### Outcome

Публичная ссылка ведёт к full live workspace in proper locale; documentation accurately declares memory-only/reset/external-integration boundary and proof tiers.

### Required changes

1. Localize CTA and explanatory copy; distinguish live demo from sign-in to a real family and do not add demo to `PUBLIC_PAGES` or sitemap.
2. Update exact test counts/expectations only from generator output; no hand-edited generated artifacts.
3. Run full web gates, exact named project E2E and diff checks; report local source/browser result separately from CI/deploy/Telegram/device evidence.

### Вне scope

Backend changes, SEO indexation of demo, legacy `/demo.html` restoration, deployment.

### Критерии приёмки

- EN public surfaces link to `/demo`, RU to `/ru/demo`, with copy that describes full interactive temporary workspace.
- No `demo.html` artifact or sitemap entry returns; `PUBLIC_PAGES` remains six entries.
- All final local commands pass on the resulting diff; the backlog records no unearned CI/deployment/client/device claim.

### Targeted validation

```bash
cd apps/web && npm run generate:public
cd apps/web && npm run lint && npm run test && npm run build
cd apps/web && npm run test:e2e -- --project=chromium tests/e2e/live-coin-shop-demo.spec.ts tests/e2e/live-coin-shop-demo-workflows.spec.ts tests/e2e/live-coin-shop-demo-family.spec.ts
cd apps/web && ! test -e static/public/demo.html && ! test -e static/public/ru/demo.html
git diff --check
git status --short
```

### Commit

```bash
git add apps/web/scripts/public-site apps/web/static/public apps/web/tests/unit/publicSiteI18n.test.ts apps/web/tests/unit/publicSiteUrls.test.ts apps/web/tests/unit/sitemap.test.ts docs/live-coin-shop-demo-backlog.md docs/live-coin-shop-demo-parity-backlog.md
git commit -m "docs(web): document full live demo parity"
```

## Финальные границы проверки

Локальные unit/lint/build и Playwright проверки доказывают source contract, browser behaviour, layout и отсутствие API traffic. Они не доказывают GitHub CI, deploy, Telegram Mini App client или физическое устройство. После merge/deploy эти уровни проверяются отдельно, без изменения статусов задач выше до появления соответствующего evidence.
