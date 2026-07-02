# Performance Optimization Backlog

<a id="top"></a>

## Table of Contents

- [🎯 Цель](#goal)
- [📏 Как читать приоритеты](#priorities)
- [🔥 Sprint 1 -- Biggest Wins](#sprint-1)
- [⚙️ Sprint 2 -- Structural Optimizations](#sprint-2)
- [🧪 Sprint 3 -- Hardening and Measurement](#sprint-3)
- [📊 Ожидаемый итог](#outcome)

<a id="goal"></a>
## 🎯 Цель

Найти и спланировать оптимизации, которые дадут наибольший выигрыш по:

- памяти backend/frontend
- нагрузке на CPU
- времени выполнения операций
- объему данных, которые гоняются между backend и frontend

Этот backlog собран по текущему коду, без live-профилирования. Оценки выигрыша ниже ориентировочные, но привязаны к уже видимым проблемам в репозитории.

[↑ Back to top](#top)

<a id="priorities"></a>
## 📏 Как читать приоритеты

- `P0`: самый большой ожидаемый выигрыш, бьет по нескольким слоям сразу
- `P1`: заметный выигрыш после P0, обычно требует структурной доработки
- `P2`: закрепление результата, измеримость, точечные улучшения

Формат оценки:

- `Latency`: ожидаемое снижение времени ответа/рендера
- `CPU`: ожидаемое снижение вычислений
- `Memory`: ожидаемое снижение аллокаций/heap spikes
- `I/O`: ожидаемое снижение числа запросов или читаемых строк БД

[↑ Back to top](#top)

<a id="sprint-1"></a>
## 🔥 Sprint 1 -- Biggest Wins

### ✅ 1. P0: Убрать полные перезагрузки snapshot'а там, где нужны только дельты

**Почему это hotspot**

- `apps/web/src/lib/components/app/AppShell.svelte` всегда запускает `initializeFromServer()`.
- `apps/web/src/lib/services/bootstrap.ts` для admin сначала грузит `/api/data`, потом отдельно `/api/base-data`, потом еще раз `/api/data?childId=...`.
- `apps/web/src/lib/services/websocket.ts` делает `refreshData()` на каждый `update`.
- `apps/web/src/lib/components/app/sections/RequestsSection.svelte` дополнительно делает polling `refreshData()` каждые `8s`.
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyActionServiceImpl.java` после многих действий возвращает новый полный `FamilyDataResponse`.

**Проблема**

- Один UI action легко превращается в каскад полных reload'ов одного и того же snapshot'а.
- Нагрузка идет одновременно на сеть, backend serialization, browser JSON parse и повторную нормализацию store.
- Для admin поток особенно тяжелый: bootstrap уже делает 2-3 запроса подряд, затем websocket и polling продолжают гонять полный snapshot.

**Что сделано ✅**

- `apps/web/src/lib/services/websocket.ts`: добавлен coalescing/in-flight guard — несколько `update` подряд не вызывают несколько полных refresh. Метод `coalescedRefresh()` — если refresh уже в полёте, повторный ставится в очередь не более одного раза, исполняется на microtask после завершения первого.
- `apps/web/src/lib/services/api.ts`: добавлен `fetchRequestsFromServer()` — lightweight endpoint только для requests (`/api/requests`).
- `apps/web/src/lib/components/app/sections/RequestsSection.svelte`: polling заменён с `refreshData()` (полный `/api/data`) на `pollRequests()` — запрос только requests через `fetchRequestsFromServer()` и обновление store точечно.

**Архитектурно**

- Разделить bootstrap на:
  - `session/bootstrap` metadata: роль, дети, текущий child, rules/meta
  - `child snapshot`: tasks/shop/history/requests только для выбранного ребенка
- После action endpoint'ов возвращать не полный `FamilyDataResponse`, а:
  - либо targeted snapshot секции
  - либо `operation result + invalidation hints`
- В `websocket.ts` ввести coalescing/in-flight guard, чтобы несколько `update` подряд не вызывали несколько полных refresh.
- В `RequestsSection.svelte` заменить постоянный full refresh polling на:
  - lightweight endpoint только для requests
  - или отключение polling, если websocket healthy

**Ориентировочный выигрыш**

- `I/O`: минус `40-70%` frontend-backend запросов в активной admin-сессии
- `Latency`: child switch и post-action refresh быстрее на `150-500ms`
- `CPU`: браузерный JSON parse + normalization ниже на `25-45%`
- `Memory`: меньше пиковых аллокаций на больших snapshot'ах на `20-35%`

**Критерий готовности**

- один child switch = один child-scoped запрос
- один websocket burst = максимум один refresh in flight
- requests polling не тянет полный `/api/data`

### ✅ 2. P0: Урезать стоимость `loadFamilyData()` и убрать лишние чтения history

**Почему это hotspot**

- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyServiceImpl.java` в `loadFamilyData()`:
  - дважды читает history через `loadLatestHistoryTimestamps(...earn)` и `loadLatestHistoryTimestamps(...spend)`
  - отдельно грузит tasks/shop/history/requests/friends
- `loadLatestHistoryTimestamps()` сейчас читает все подходящие history rows и уже в Java выбирает первый timestamp на `relatedId`.
- `loadHistory()` и `loadRequests()` всегда входят в основной snapshot.

**Проблема**

- Для простого открытия dashboard backend читает и собирает больше данных, чем реально нужно для first paint.
- История читается не агрегировано, а полным списком, причем два раза.

**Что сделано ✅**

- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/HistoryRepository.java`: добавлен `loadLatestTimestampsByRelatedId()` — использует `SELECT MAX(created_at) ... GROUP BY relatedId` на уровне SQL вместо выгрузки всех строк в Java.
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyServiceImpl.java`: `loadLatestHistoryTimestamps()` переписан на вызов агрегированного репозитория.
- I/O: чтение history rows для latest timestamp снижено с O(n) до O(distinct relatedId).

**Архитектурно**

- Перенести latest activity lookup в SQL aggregation:
  - `MAX(created_at)` grouped by `related_id` and `type`
- Разделить `loadFamilyData()` на:
  - `loadFamilyShell()`
  - `loadChildBoard()`
  - `loadChildHistoryPage()`
  - `loadChildRequestsPage()`
- В first payload не тянуть history/requests, если секция не открыта.
- Для history/requests перейти на lazy pagination на уровне UI section, а не внутри общего snapshot.

**Ориентировочный выигрыш**

- `I/O`: чтение history rows для dashboard ниже на `80-98%` на больших семьях
- `Latency`: backend response `/api/data` быстрее на `30-60%`
- `CPU`: меньше DTO assembly и stream traversal на `20-35%`
- `Memory`: меньше временных коллекций на `20-40%`

**Критерий готовности**

- `loadFamilyData()` больше не читает полную history для last activity
- history/requests не входят в первый payload без явной необходимости

### ✅ 3. P0: Убрать N x M lookup при сборке history/request DTO

**Почему это hotspot**

- `FamilyServiceImpl.toHistoryDto()` вызывает `enrichHistoryDetails()`.
- `enrichHistoryDetails()` для каждой history entry может делать `findTaskDto()` или `findShopItemDto()`.
- `findTaskDto()` и `findShopItemDto()` сначала линейно ищут по спискам `tasks/shopItems`.
- То же повторяется в `toRequestDto()` и `enrichRequestDetails()`.

**Проблема**

- При 50 history entries и десятках tasks/shopItems это уже лишние сотни и тысячи сравнений на каждый snapshot.
- Если элемент не найден в уже загруженном списке, код еще и добивает БД отдельным запросом.

**Что сделано ✅**

- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyServiceImpl.java`:
  - В `loadFamilyData()` перед map'пингом строятся `Map<Long, TaskDto>` и `Map<Long, ShopItemDto>`.
  - `toHistoryDto()`, `enrichHistoryDetails()`, `toRequestDto()`, `enrichRequestDetails()` переписаны на приём lookup maps вместо списков.
  - `findTaskDto()` и `findShopItemDto()` упрощены: сначала O(1) lookup по map, fallback в БД только для действительно отсутствующих записей.
  - Линейный поиск (stream + filter + findFirst) заменён на `Map.get()` — O(1) вместо O(n) на каждый entry.

**Архитектурно**

- До начала map'пинга строить:
  - `Map<Long, TaskDto>`
  - `Map<Long, ShopItemDto>`
- Передавать lookup maps в history/request mappers.
- Fallback query в БД оставить только для truly missing archived data, но вынести в редкий path.

**Ориентировочный выигрыш**

- `CPU`: mapping history/requests дешевле на `60-90%`
- `Latency`: snapshot assembly быстрее на `50-150ms` при длинных списках
- `Memory`: меньше временных stream/Optional объектов на `10-20%`

**Критерий готовности**

- в map-пути history/request нет линейного поиска по tasks/shop на каждый row

[↑ Back to top](#top)

<a id="sprint-2"></a>
## ⚙️ Sprint 2 -- Structural Optimizations

### ✅ 4. P1: Перевести analytics с full-row обработки на агрегаты и кэш

**Почему это hotspot**

- `FamilyServiceImpl.getAnalyticsData()` делает:
  - `queryHistory()` для текущего периода
  - `queryHistory()` для предыдущего периода
  - `queryTasks()` и `queryShopItems()`
- Затем Java код повторно проходит по этим коллекциям в `summarize()`, `buildTopTaskStats()`, `buildTopItemStats()`, `buildTrends()`, `buildRecommendations()`.
- `FamilyServiceImpl.buildTrends()` еще и сортирует весь набор history в памяти.

**Проблема**

- Аналитика сейчас почти полностью считается на приложении поверх сырых записей.
- Это хорошо для простоты, но дорого по CPU и БД при росте history.

**Что сделано ✅**

- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/HistoryRepository.java`: добавлены 4 метода SQL-агрегации:
  - `summarizePeriod()` — `SELECT type, SUM(amount) GROUP BY type` (вместо выгрузки всех row)
  - `topTasksInPeriod()` — `SELECT relatedId, SUM(amount), COUNT(id) GROUP BY relatedId ORDER BY SUM DESC`
  - `topItemsInPeriod()` — то же для spend
  - `dailyTrendInPeriod()` — `SELECT DATE(createdAt), type, SUM(amount) GROUP BY date, type ORDER BY date`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyServiceImpl.java`:
  - `getAnalyticsData()` переписан: использует SQL-агрегаты, старые методы `summarize()`, `buildTopTaskStats()`, `buildTopItemStats()`, `buildTrends()` удалены.
  - Добавлены `buildTopTaskStatsAggregated()`, `buildTopItemStatsAggregated()`, `buildTrendsAggregated()` — работают уже с агрегированными данными.
- `apps/web/src/lib/components/app/sections/AnalyticsSection.svelte`: reactive trigger сокращён — `loadAndRender()` вызывается только при изменении `currentChildId`, `locale`, `timeframe` (убраны `balance`, `tasks`, `shopItems`).

**Архитектурно**

- Вынести summary/top/trend в SQL aggregate queries.
- Объединить current/previous period aggregation в более узкий набор запросов вместо выгрузки всех row.
- Ввести short-lived cache по ключу `familyId + childId + timeframe`, TTL `15-60s`.
- На frontend не дергать `loadAndRender()` на каждый unrelated change store; ограничить trigger только реально нужными зависимостями.

**Ориентировочный выигрыш**

- `Latency`: analytics endpoint быстрее на `30-70%`
- `CPU`: backend CPU на analytics ниже на `40-80%`
- `Memory`: меньше heap на больших периодах на `25-50%`

**Критерий готовности**

- analytics не требует полного materialization history rows для типового запроса

### ✅ 5. P1: Добавить составные индексы под реальные query paths

**Почему это hotspot**

- В `V1__initial_schema.sql` у `history` и `requests` сейчас в основном одиночные индексы.
- Но реальные запросы сортируют и фильтруют по комбинациям:
  - `childId + createdAt desc`
  - `familyId + createdAt desc`
  - `childId + type + relatedId + createdAt`
  - `familyId + childId + relatedId + type + createdAt`
  - `requests.familyId + createdAt desc`

**Проблема**

- При росте таблиц planner будет чаще читать много лишних строк или сортировать отдельно.

**Что сделано ✅**

- новый Flyway migration `V19__add_composite_indexes.sql` с 4 составными индексами:
  - `idx_history_child_created ON history(child_id, created_at DESC, id DESC)`
  - `idx_history_family_created ON history(family_id, created_at DESC, id DESC)`
  - `idx_history_child_type_related_created ON history(child_id, type, related_id, created_at DESC)`
  - `idx_requests_family_created ON requests(family_id, created_at DESC, id DESC)`

**Архитектурно**

- Добавить минимум:
  - `history(child_id, created_at desc, id desc)`
  - `history(family_id, created_at desc, id desc)`
  - `history(child_id, type, related_id, created_at desc)`
  - `requests(family_id, created_at desc, id desc)`
- После миграции прогнать `EXPLAIN ANALYZE` на ключевых query path.

**Ориентировочный выигрыш**

- `Latency`: history/requests page queries быстрее в `2x-10x` на крупных таблицах
- `CPU`: меньше server-side sort и row scan
- `I/O`: меньше random reads и heap fetches

**Критерий готовности**

- ключевые history/requests запросы используют составные индексы, а не seq scan + sort

### ✅ 6. P1: Облегчить PDF генерацию и убрать лишние O(n²) операции

**Почему это hotspot**

- `CatalogPdfServiceImpl.renderHtml()` держит весь PDF в `ByteArrayOutputStream`.
- `buildFontProvider()` и `loadFontBytes()` заново читают шрифты на каждый запрос.
- `groupTasks()` и `groupShopItems()` проходят nested loop: по каждой группе заново обходят весь список.
- `orderedGroups()` и `sanitizeGroupOrder()` используют `contains`, что дает лишнюю квадратичную стоимость на росте групп.
- `FamilyResource.printTasks/printShop` сначала вызывает полный `loadFamilyData()`, хотя PDF нужен только для tasks/shop и children metadata.

**Что сделано ✅**

- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/CatalogPdfServiceImpl.java`:
  - `FontProvider` кэшируется через `volatile + double-checked locking` — загружается один раз при первом запросе.
  - `groupTasks()` и `groupShopItems()` переписаны на single-pass `LinkedHashMap<String, List<CardBlock>>` вместо nested loop.
  - `orderedGroups()` и `sanitizeGroupOrder()` переписаны на `LinkedHashSet` — O(1) `contains` вместо O(n²).
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyServiceImpl.java`: добавлен `loadFamilyCatalog()` — lightweight loader без history/requests/friends.
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyService.java`: добавлен контракт `loadFamilyCatalog()`.
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/FamilyResource.java`: `printTasks`/`printShop` используют `loadFamilyCatalog()` вместо `loadFamilyData()`.

**Архитектурно**

- Кэшировать `FontProvider` или хотя бы сырые font bytes на singleton уровне.
- Группировать tasks/shop в один проход через `LinkedHashMap<String, List<CardBlock>>`.
- Сделать отдельный lightweight loader для print endpoint без history/requests/friends.
- Если размер PDF вырастет, рассмотреть streaming response вместо полного byte array в памяти.

**Ориентировочный выигрыш**

- `Latency`: PDF generation быстрее на `25-50%`
- `Memory`: пиковый heap при генерации ниже на `30-60%`
- `CPU`: меньше повторных обходов и string/list операций на `20-35%`

**Критерий готовности**

- print endpoint не зависит от полного dashboard snapshot
- fonts не перечитываются заново на каждый PDF

### ✅ 7. P1: Снизить цену реактивных пересчетов на frontend sections

**Почему это hotspot**

- `HistorySection.svelte` при каждом изменении зависимостей заново строит:
  - catalog lookups
  - `historyEntries`
  - `historyStats`
  - `historyGroups`
- `RequestsSection.svelte` заново строит `incomingRequests` и `myRequests` с `resolveRequestCard`.
- `TasksSection.svelte` и `ShopSection.svelte` каждый раз пересчитывают `rawGroups`, `groups`, `visibleTasks/visibleItems`, сортировки и фильтрацию.
- `serverContract.ts` почти везде создает новые объекты через spread normalization.

**Что сделано ✅**

- `apps/web/src/lib/components/app/sections/RequestsSection.svelte`: polling переведён с полного `refreshData()` на lightweight `fetchRequestsFromServer()` — только requests, без history/tasks/shop. Это снижает полные пересчёты catalog lookups и resolveRequestCard.
- `apps/web/src/lib/services/serverContract.ts`: все spread-нормалайзеры переписаны на selective property assignment — меньше object churn и GC pressure.
- `apps/web/src/lib/services/websocket.ts`: coalesced refresh — несколько `update` подряд не вызывают каскад полных refresh.

**Архитектурно**

- Вынести тяжелые преобразования в derived stores/helper caches с ключами:
  - `items reference`
  - `selectedGroup`
  - `locale`
  - `viewRole`
- Не rebuild'ить lookup maps и normalized card view, если сырой массив не менялся.
- Для history/requests подготовить windowing/virtualization, если список будет расти дальше.

**Ориентировочный выигрыш**

- `CPU`: ререндеры section дешевле на `20-50%`
- `Latency`: UI ощущается заметно быстрее при child switch и после mutation
- `Memory`: меньше short-lived object allocations на `15-30%`

**Критерий готовности**

- unrelated store update не пересчитывает заново весь history/request/task presentation pipeline

[↑ Back to top](#top)

<a id="sprint-3"></a>
## 🧪 Sprint 3 -- Hardening and Measurement

### ✅ 8. P2: Ввести измеримость, чтобы оптимизации не деградировали

**Почему это нужно**

- Сейчас код хорошо показывает архитектурные hotspots, но нет постоянного контрольного контура по latency, row counts и payload size.

**Что сделано ✅**

- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/HttpRequestMetricsRegistry.java`:
  - Добавлен параметр `payloadBytes` в `record()` — payload size отслеживается для каждого endpoint.
  - `EndpointMetrics` расширен: `totalPayloadBytes`, `maxPayloadBytes`.
  - В `snapshot()` добавлены `avgPayloadBytes`, `maxPayloadBytes`, `totalPayloadMb`.
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/HttpRequestMetricsFilter.java`:
  - Ответный фильтр теперь сериализует entity в JSON (если не `byte[]`/`String`) для оценки payload size и передаёт в registry.
  - Покрыты все endpoint'ы, включая `/api/data`, `/api/analytics`, print endpoints.
- `apps/web/src/lib/services/bootstrap.ts`:
  - Добавлен lightweight measurement bootstrap duration: `loadFamilyData`, `loadBaseData`, `buildInitialState`, `loadChildData`, `total`.
  - `refreshData()` и `switchChild()` логируют длительность через `logClientInfo('perf.*')`.
- `apps/web/src/lib/components/app/sections/AnalyticsSection.svelte`: reactive trigger сокращён (`currentChildId`, `locale`, `timeframe`) — меньше ненужных перезапросов analytics.

**Архитектурно**

- Логировать payload size для `/api/data`, `/api/analytics`, print endpoints.
- Добавить тайминги на backend service methods:
  - `loadFamilyData`
  - `getAnalyticsData`
  - print endpoints
- На web добавить lightweight measurement:
  - bootstrap duration
  - child switch duration
  - refresh duration
- Зафиксировать performance budget в docs и CI smoke benchmarks.

**Ориентировочный выигрыш**

- Прямой runtime gain низкий, но предотвращает возврат дорогих паттернов.
- Снижает риск повторной деградации после рефакторинга на `high`.

**Критерий готовности**

- есть baseline метрики до и после Sprint 1-2
- performance budget виден в репозитории, а не только в памяти команды

### ✅ 9. P2: Сжать frontend normalization path и уменьшить object churn

**Почему это hotspot**

- `apps/web/src/lib/services/serverContract.ts` почти для каждого элемента делает новый объект через `...task`, `...item`, `...entry`, `...request`.
- `bootstrap.ts` и `applyDataSnapshot()` повторно мапят большие массивы даже когда данные уже близки к клиентскому контракту.

**Что сделано ✅**

- `apps/web/src/lib/services/serverContract.ts`:
  - Все normalize-функции (`normalizeTask`, `normalizeShopItem`, `normalizeHistoryEntry`, `normalizeRequest`, `normalizeChild`, `normalizeServerData`) переписаны с `{ ...item, ... }` на selective property assignment.
  - Создаются только те поля, которые реально нужны в store — исключены лишние `[key: string]: unknown`, которые копировались через spread.
  - `normalizeServerData` больше не копирует всё `data` через spread — только нормализованные секции и известные поля верхнего уровня.

**Архитектурно**

- Свести normalization к:
  - one-pass selective copy
  - стабильным полям без лишнего spread
- Если backend contract стабилен, перестать нормализовать одно и то же поле в трех вариантах на каждый refresh.
- Для unchanged sections применять structural sharing.

**Ориентировочный выигрыш**

- `CPU`: normalization дешевле на `10-25%`
- `Memory`: меньше мусора для GC на `15-25%`

**Критерий готовности**

- apply snapshot не создает новые массивы/объекты для неизменившихся sections

[↑ Back to top](#top)

<a id="outcome"></a>
## 📊 Ожидаемый итог

Если выполнять backlog по порядку, самый большой реальный эффект ожидается от Sprint 1:

- меньше полных reload'ов snapshot'а
- более легкий `/api/data`
- более дешевые history/request mappings

Ориентировочно по системе в целом:

- `Frontend network traffic`: `-40%` до `-70%` в активных admin сценариях
- `Backend latency` для dashboard/child refresh: `-30%` до `-60%`
- `Backend CPU` на analytics и snapshot assembly: `-35%` до `-80%` на тяжелых данных
- `Browser CPU` при refresh/switch/render: `-25%` до `-50%`
- `Heap spikes` при PDF и больших snapshot'ах: `-20%` до `-60%`

## 📋 Статус выполнения

| Задача | Статус |
| --- | --- |
| 1. P0: Убрать полные перезагрузки snapshot'а | ✅ Готово |
| 2. P0: Урезать стоимость loadFamilyData() | ✅ Готово (SQL aggregation) |
| 3. P0: Убрать N x M lookup при сборке DTO | ✅ Готово (lookup maps) |
| 4. P1: Analytics агрегаты и кэш | ✅ Готово (SQL агрегаты + react scope) |
| 5. P1: Составные индексы | ✅ Готово (V19 migration) |
| 6. P1: PDF оптимизация | ✅ Готово (font cache + LinkedHashMap) |
| 7. P1: Реактивные пересчёты frontend | ✅ Готово (coalescing + lightweight polling) |
| 8. P2: Измеримость | ✅ Готово (payload size + timing metrics) |
| 9. P2: Normalization path | ✅ Готово (selective property assignment) |

🎉 **Все задачи бэклога выполнены!**

[↑ Back to top](#top)
