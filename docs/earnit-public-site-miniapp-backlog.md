# EarnIt Kids — Публичный сайт и Mini App: ссылки, скриншоты, действия родителя, оптимизация

## Цель

Привести публичный маркетинговый сайт и Telegram Mini App в актуальное состояние: ссылки должны вести на правильные места (корень публичного сайта и Telegram-бот), скриншоты — показывать реальный текущий интерфейс, родитель должен уметь сам выполнять задание или выдавать награду без заявки (с уведомлением ребёнку), а самые медленные флоу Mini App и бота — быть оптимизированы по принципу «20% усилий → 80% результата».

## Архитектурные решения

- **Источник истины для публичного origin** — `apps/web/src/lib/server/config.ts` (`loadAppConfig`): `publicOrigin = APP_URL || FRONTEND_URL || PUBLIC_BASE_URL || DEFAULT_PUBLIC_ORIGIN`. Это значение используется в футере Mini App, `sitemap.xml`, `robots.txt` и прокси-реферере. Оно обязано быть корнем сайта (без пути), а не URL конкретной страницы приложения.
- **Источник истины для ссылки на Telegram-бота** — `apps/web/scripts/proxy-context.mjs` (`resolveTelegramMiniAppUrl`), который генерирует `config.js` (`/public/config.js`) через `apps/web/scripts/preview.mjs` (`serveConfigJs`). Кнопки `data-miniapp-link` на статичных HTML-страницах заполняются из `window.EARNIT_CONFIG.telegramMiniAppUrl` в `apps/web/static/public/site.js`.
- **Границы слоёв**: публичный сайт — статичный HTML в `apps/web/static/public/` (вне SvelteKit-роутинга, редиректы через `hooks.server.ts` → `resolvePublicRedirect`). Mini App — SvelteKit-маршрут `/telegram` (русскоязычный, без locale-префикса). Telegram-бот — backend `service/telegram/*`.
- **Действия родителя без заявки** переиспользуют уже существующие эндпоинты `POST /api/tasks/{taskId}/complete` (`completeTask`) и `POST /api/shop/{itemId}/purchase` (`purchaseItem`), которые уже публикуют outbox-события `TASK_APPROVED` / `REWARD_PURCHASED`. Новые эндпоинты не создаются.
- **Уведомления ребёнку** идут через существующий outbox-пайплайн (`FamilyActionEventSupport` → `ApplicationEventPublisher` → `TelegramDeliveryPlanner` → `TelegramOutboxProcessor` → `TelegramNotificationComposer`). Доставка ребёнку уже настроена (`TelegramDeliveryPlanner.recipients` для не-родительских событий отдаёт активного ребёнка). Требуется только корректный русский текст для прямого действия родителя.
- **Явно отвергнуто**: дублирование DTO/эндпоинтов для «прямого» действия родителя; отдельный механизм уведомлений в обход outbox; хардкод URL в HTML вместо `config.js`/`publicOrigin`.
- **Совместимость**: изменения ссылок и уведомлений обратно совместимы — существующие флоу заявок (ребёнок → родитель → одобрение) не меняются.

## Рекомендуемый порядок реализации

| Порядок | Задача | Приоритет | Зависит от | Причина |
| ---: | --- | --- | --- | --- |
| 1 | P1-1 | P1 | - | Ссылка футера Mini App ведёт не туда — видимый дефект |
| 2 | P1-2 | P1 | - | Кнопки Telegram на публичном сайте ведут на web-URL вместо бота |
| 3 | P1-3 | P1 | - | Бэкенд-текст уведомления ребёнку для прямого действия родителя |
| 4 | P1-4 | P1 | P1-3 | Кнопки «Выполнить/Выдать» в Mini App родителя |
| 5 | P2-5 | P2 | - | Актуальные скриншоты Mini App |
| 6 | P2-6 | P2 | - | Оптимизация медленных флоу (после замера) |

---

## P1-1: Ссылка «Публичный сайт» в футере Mini App ведёт в корень публичного сайта

**Status:** ✅ Completed
**Priority:** P1
**Depends on:** -

### Outcome

В футере Mini App (родитель и ребёнок) ссылка «Публичный сайт» открывает корень публичного сайта (`https://earnit-kids.igo.mywire.org/` → редирект на `/public/index.html`), а не страницу приложения `/en/app/tasks`.

### Architectural decision

`publicOrigin` в `loadAppConfig` обязан быть корнем origin (без пути и query). Футер Mini App (`TelegramParentShell.svelte` / `TelegramChildShell.svelte`) использует `publicOrigin` напрямую. Исправление — на уровне конфигурации (env) и/или нормализации origin, а не хардкод URL в компоненте.

### Files

- Modify `apps/web/src/lib/server/config.ts`.
- Modify `apps/web/src/lib/components/telegram/TelegramParentShell.svelte` (только если нужна нормализация на клиенте).
- Modify `apps/web/src/lib/components/telegram/TelegramChildShell.svelte` (аналогично).
- Modify `.env.example` (документировать, что `APP_URL`/`PUBLIC_BASE_URL` — корень без пути).

### Work

1. Убедиться, что `publicOrigin` нормализуется до корня origin: отбрасывать path и query из `APP_URL`/`FRONTEND_URL`/`PUBLIC_BASE_URL` (например, через `new URL(...).origin`), а не только `trimTrailingSlashes`.
2. Проверить, что футер Mini App использует именно это нормализованное значение (передаётся через `locals.appConfig.publicOrigin` → `+page.server.ts` → `TelegramRoleResolver` → shell).
3. Исправить значение env в деплое (`.env`, docker-compose, Dokploy), если `APP_URL` сейчас указывает на `/en/app/tasks`.
4. Добавить регрессионный тест на нормализацию origin (путь/query отбрасываются).

### Acceptance criteria

- При `APP_URL=https://earnit-kids.igo.mywire.org/en/app/tasks` футер Mini App ведёт на `https://earnit-kids.igo.mywire.org/`.
- `sitemap.xml` и `robots.txt` продолжают использовать корректный корень origin.
- Ссылка открывается в новой вкладке (`target="_blank" rel="noopener noreferrer"`), как сейчас.

### Verification

```bash
cd apps/web && npm run lint && npm run test && npm run build
```

### Commit

```bash
git add apps/web/src/lib/server/config.ts apps/web/src/lib/components/telegram/TelegramParentShell.svelte apps/web/src/lib/components/telegram/TelegramChildShell.svelte .env.example
git commit -m "fix(web): normalize publicOrigin to site root for Mini App footer link"
```

---

## P1-2: Кнопки «Открыть в Telegram» на публичном сайте ведут на Telegram-бота

**Status:** ✅ Completed
**Priority:** P1
**Depends on:** -

### Outcome

Все кнопки `data-miniapp-link` («Открыть в Telegram») на статичных страницах публичного сайта ведут на deep-link Telegram-бота (`https://t.me/<bot_username>?startapp=<miniapp-url>`), а не на web-URL `/ru/telegram`.

### Architectural decision

Ссылка формируется в `resolveTelegramMiniAppUrl` (`apps/web/scripts/proxy-context.mjs`) и попадает в `config.js` через `serveConfigJs` в `preview.mjs`. `site.js` подставляет её в `data-miniapp-link`. Исправление — на уровне генерации `config.js` и env (`TELEGRAM_BOT_USERNAME`, `TELEGRAM_MINI_APP_URL`), а не в HTML.

### Files

- Modify `apps/web/scripts/proxy-context.mjs`.
- Modify `apps/web/scripts/preview.mjs` (если меняется контракт `serveConfigJs`).
- Modify `.env.example` (документировать `TELEGRAM_BOT_USERNAME` и `TELEGRAM_MINI_APP_URL`).
- Modify `apps/web/tests/unit/previewProxyContext.test.ts` (регрессия).

### Work

1. Убедиться, что `resolveTelegramMiniAppUrl` возвращает `https://t.me/<bot>?startapp=<miniapp-url>`: `startapp` должен указывать на Mini App (`/telegram`), а не на корень публичного сайта.
2. Проверить приоритет env: если `TELEGRAM_MINI_APP_URL` задан как web-URL `/ru/telegram`, он не должен подставляться в кнопки публичного сайта как ссылка на бота — либо исправить значение env, либо разделить «URL Mini App» и «deep-link бота».
3. Исправить env в деплое: задать `TELEGRAM_BOT_USERNAME` и корректный `TELEGRAM_MINI_APP_URL`.
4. Добавить/обновить юнит-тесты на формирование deep-link.

### Acceptance criteria

- При заданном `TELEGRAM_BOT_USERNAME` кнопки публичного сайта ведут на `https://t.me/<bot>?startapp=...`.
- `startapp` указывает на Mini App (`/telegram`), а не на `/ru/telegram` и не на корень.
- При пустом `TELEGRAM_BOT_USERNAME` кнопки остаются неактивными (текущее поведение `site.js`), без alert-ошибки в проде.

### Verification

```bash
cd apps/web && npm run lint && npm run test && npm run build
```

### Commit

```bash
git add apps/web/scripts/proxy-context.mjs apps/web/scripts/preview.mjs apps/web/tests/unit/previewProxyContext.test.ts .env.example
git commit -m "fix(web): point public site Telegram buttons to bot deep link"
```

---

## P1-3: Уведомление ребёнку при прямом выполнении задания / выдаче награды родителем

**Status:** ✅ Completed
**Priority:** P1
**Depends on:** -

### Outcome

Когда родитель напрямую выполняет задание (`completeTask`) или выдаёт награду (`purchaseItem`), ребёнок получает Telegram-уведомление на русском: «Родитель выполнил задание …» / «Родитель выдал награду …», отличное от уведомления об одобрении заявки.

### Architectural decision

События уже публикуются: `completeTask` → `TASK_APPROVED` (с `requestId=null`), `purchaseItem` → `REWARD_PURCHASED` (с `requestId=null`). `TelegramNotificationComposer.childOutcomeText` сейчас для `requestId=null` падает в `generic()` (английский текст). Исправление — в `TelegramNotificationComposer` и `TelegramCopy`: добавить русский текст для прямого действия родителя, различая «выполнил задание» и «выдал награду».

### Files

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramNotificationComposer.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramCopy.java`.
- Modify `apps/backend/src/test/java/.../TelegramNotificationComposerTest.java` (или существующий тест композера).

### Work

1. В `childOutcomeText` обработать случай `requestId == null` (прямое действие родителя): для `TASK_APPROVED` — текст «родитель выполнил задание», для `REWARD_PURCHASED` — «родитель выдал награду», с балансом/дельтой.
2. Добавить соответствующие методы в `TelegramCopy` (с эмодзи из `TelegramBotEmoji`, соблюдая правило «одна кнопка = один эмодзи»).
3. Убедиться, что `REWARD_PURCHASED` больше не попадает в `generic()` с английским текстом.
4. Добавить юнит-тест на текст для `requestId=null` (прямое действие) и `requestId!=null` (одобрение заявки) — тексты должны различаться.

### Acceptance criteria

- Прямое выполнение задания родителем → ребёнку приходит русское уведомление «родитель выполнил задание» с названием и монетами.
- Прямая выдача награды родителем → ребёнку приходит русское уведомление «родитель выдал награду».
- Одобрение заявки ребёнка по-прежнему даёт прежний текст («одобрен»), без регресса.
- `./mvnw verify` проходит (Checkstyle + PMD + SpotBugs + JaCoCo + тесты).

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramNotificationComposer.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramCopy.java
git commit -m "feat(backend): Russian child notification for direct parent task/reward actions"
```

---

## P1-4: Родитель в Mini App выполняет задание / выдаёт награду без заявки

**Status:** ✅ Completed
**Priority:** P1
**Depends on:** P1-3

### Outcome

В Mini App родителя на экранах «Задания» и «Награды» появляются действия «Выполнить» (начислить монеты за задание) и «Выдать» (списать монеты за награду) для выбранного ребёнка, без создания заявки. После действия ребёнок получает уведомление (см. P1-3).

### Architectural decision

Переиспользуются существующие клиентские функции `earnCoins` (`POST /api/tasks/{id}/complete`) и `buyItem` (`POST /api/shop/{id}/purchase`) из `apps/web/src/lib/services/api.ts`, которые уже вызывают `completeTask`/`purchaseItem` и возвращают `FamilyDataResponse` для `applyDataSnapshot`. Кнопки добавляются в `TelegramParentTasks.svelte` и `TelegramParentRewards.svelte` (родительский контекст, `canEdit`/`isAdmin`), аналогично web-приложению (`TasksSection.svelte`/`ShopSection.svelte`).

### Files

- Modify `apps/web/src/lib/components/telegram/TelegramParentTasks.svelte`.
- Modify `apps/web/src/lib/components/telegram/TelegramParentRewards.svelte`.
- Modify `apps/web/src/lib/i18n/messages/ru/app.ts` (ключи для «Выполнить»/«Выдать» и тостов).
- Modify `apps/web/src/lib/i18n/messages/en/app.ts` (паритет ключей).

### Work

1. В `TelegramParentTasks.svelte` добавить действие «Выполнить» для активного задания (вызов `earnCoins(task.id, currentChildId)` + `applyDataSnapshot` + тост), с защитой от повторного нажатия (busy-состояние).
2. В `TelegramParentRewards.svelte` добавить действие «Выдать» для активной награды (вызов `buyItem(item.id, currentChildId)` + `applyDataSnapshot` + тост), с проверкой достаточности баланса и busy-состоянием.
3. Добавить i18n-ключи (ru + en) для меток и тостов; соблюсти паритет ключей (существующий `i18nCatalog.test.ts`).
4. Убедиться, что действие доступно только родителю (не viewer), и что после действия список/баланс обновляются через `applyDataSnapshot`.

### Acceptance criteria

- Родитель видит «Выполнить» у задания и «Выдать» у награды; нажатие начисляет/списывает монеты сразу, без заявки.
- Баланс и история обновляются после действия (через `applyDataSnapshot`).
- Повторное нажатие во время выполнения заблокировано (busy), нет двойного начисления.
- При недостатке монет для награды показывается ошибка, действие не выполняется.
- Кнопки имеют touch-target ≥ 44×44px и видимый focus-стиль.
- Ребёнок получает уведомление (P1-3).

### Verification

```bash
cd apps/web && npm run lint && npm run test && npm run build
```

### Commit

```bash
git add apps/web/src/lib/components/telegram/TelegramParentTasks.svelte apps/web/src/lib/components/telegram/TelegramParentRewards.svelte apps/web/src/lib/i18n/messages/ru/app.ts apps/web/src/lib/i18n/messages/en/app.ts
git commit -m "feat(web): parent completes task or grants reward directly in Mini App"
```

---

## P2-5: Актуальные скриншоты Mini App на публичном сайте

**Status:** ⬜ Not started
**Priority:** P2
**Depends on:** -

### Outcome

Карусель «Вот как это выглядит» на `how.html` показывает реальные скриншоты текущего Mini App (родитель: главная, задания, семья; ребёнок: сегодня), а не устаревшие/тестовые изображения.

### Architectural decision

Скриншоты — статичные PNG в `apps/web/static/public/assets/screenshots/`. `how.html` ссылается на `parent-home.png`, `parent-tasks.png`, `parent-family.png`, `child-today.png`. Замена — на уровне ассетов (перезапись PNG), без изменения разметки, если размеры/имена сохраняются.

### Files

- Modify `apps/web/static/public/assets/screenshots/parent-home.png`.
- Modify `apps/web/static/public/assets/screenshots/parent-tasks.png`.
- Modify `apps/web/static/public/assets/screenshots/parent-family.png`.
- Modify `apps/web/static/public/assets/screenshots/child-today.png`.
- Modify `apps/web/static/public/how.html` (только если меняются имена/подписи).

### Work

1. Снять актуальные скриншоты Mini App (родитель: главная/задания/семья; ребёнок: сегодня) в реальном окружении, в мобильном viewport (375×667, как указано в `width`/`height`).
2. Перезаписать соответствующие PNG, сохранив имена и пропорции (или обновить `src`/`alt`/`figcaption` в `how.html` при изменении имён).
3. Убедиться, что неиспользуемые `miniapp-*.png` не остаются в карусели (либо удалить, либо оставить вне разметки — по решению).
4. Проверить, что изображения оптимизированы (разумный размер, `loading="lazy" decoding="async"` уже есть).

### Acceptance criteria

- Карусель на `how.html` показывает актуальный интерфейс Mini App (не тестовые/устаревшие экраны).
- `alt`-тексты соответствуют содержимому скриншотов.
- Изображения корректно отображаются на десктопе и мобильном (375px), без горизонтального скролла.

### Verification

```bash
cd apps/web && npm run build
# визуальная проверка: открыть /public/how.html и пролистать карусель
```

### Commit

```bash
git add apps/web/static/public/assets/screenshots/ apps/web/static/public/how.html
git commit -m "docs(web): refresh Mini App screenshots on public site"
```

---

## P2-6: Оптимизация медленных флоу Mini App и Telegram-бота (20/80)

**Status:** ⬜ Not started
**Priority:** P2
**Depends on:** -

### Outcome

Устранить наиболее заметные задержки в Mini App и боте, дающие наибольший выигрыш при минимальных усилиях. Сначала замерить, затем оптимизировать только реально медленные флоу.

### Architectural decision

Кандидаты (требуют подтверждения замером, не оптимизировать вслепую):
- `apps/web/src/lib/services/bootstrap.ts` `initializeFromServer` — три последовательных round-trip (`loadDataFromServer` → `loadBaseData` (только admin) → `loadDataDetailsFromServer`), каждый ждёт предыдущий.
- `refreshData` после каждого действия в Mini App (полная перезагрузка снапшота).
- Backend outbox-пайплайн: интервал `app.telegram.outbox-poll-interval` и пакетная обработка в `TelegramOutboxProcessor`.

Оптимизация должна переиспользовать существующие механизмы (`loadDataFromServer`, `applyDataSnapshot`, outbox), не вводя параллельных источников состояния.

### Files

- Modify `apps/web/src/lib/services/bootstrap.ts` (если оптимизируем загрузку).
- Modify `apps/web/src/lib/services/api.ts` (если объединяем запросы).
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramOutboxProcessor.java` (если оптимизируем outbox).
- Modify `apps/backend/src/main/resources/application.properties` (если меняем интервалы).

### Work

1. Замерить реальные задержки (логи `perf.bootstrap` уже есть в `bootstrap.ts`; добавить замеры при необходимости) для: старта Mini App, переключения ребёнка, действия родителя, доставки уведомления бота.
2. Выбрать 1–2 флоу с наибольшим эффектом (например, параллелизация/объединение запросов в `initializeFromServer`, либо уменьшение лишних `refreshData`).
3. Реализовать оптимизацию, сохранив корректность состояния (единый `applyDataSnapshot`, без рассинхрона).
4. Добавить/обновить юнит-тесты на изменённую логику загрузки.

### Acceptance criteria

- Выбранный флоу стал заметно быстрее (зафиксировать «до/после» в описании коммита/PR).
- Состояние Mini App остаётся корректным после оптимизации (нет рассинхрона баланса/заявок).
- `npm run lint && npm run test && npm run build` и `./mvnw verify` проходят.
- Если реально медленных флоу не обнаружено — задача закрывается с фиксацией результатов замера (без изменений кода).

### Verification

```bash
cd apps/web && npm run lint && npm run test && npm run build
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

### Commit

```bash
git add <изменённые файлы>
git commit -m "perf(web): reduce Mini App bootstrap round-trips"
```

---

## Финальная проверка бэклога

- Каждая задача ссылается на реальные файлы (проверены в репозитории) или явно новые.
- Критерии приёмки — наблюдаемое поведение (не «обнови компонент»).
- Команды верификации — точные (`npm run lint/test/build`, `./mvnw verify` из `apps/backend`).
- Каждая задача — один атомарный коммит.
- Порядок определяется зависимостями (P1-4 зависит от P1-3).
- Нет параллельных источников истины: ссылки — через `publicOrigin`/`config.js`, уведомления — через outbox, действия — через существующие эндпоинты.
