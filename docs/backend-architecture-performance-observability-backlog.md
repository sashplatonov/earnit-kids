# Бэклог архитектуры, производительности и наблюдаемости backend

<a id="top"></a>

Плановый снимок создан 2026-07-06.

Статус доставки: `Запланирован`.

Область: backend-first backlog для `apps/backend` с минимально необходимыми обновлениями конфигурации и документации в корне `docs/`, `.env.example` и Docker Compose.

Все пути к файлам ниже указаны относительно корня репозитория.

## Содержание

- [Цель](#goal)
- [Шкала приоритетов](#priority-scale)
- [Сигналы текущего состояния](#current-repo-signals)
- [Карта бэклога](#backlog-map)
- [1. Структура проекта](#project-structure)
- [2. Скорость и RAM сервиса](#service-speed-and-ram)
- [3. Оптимизация БД](#database-optimization)
- [4. Наблюдаемость](#observability)
- [5. Метрики New Relic](#new-relic-metrics)
- [Матрица проверки](#verification-matrix)

<a id="goal"></a>
## Цель

Подготовить конкретный backlog внедрения для пяти направлений улучшений backend:

1. улучшить структуру проекта без поломки контрактов;
2. снизить latency и давление на heap в основных сервисных путях;
3. оптимизировать паттерны доступа к БД и индексы;
4. улучшить observability для диагностики в runtime;
5. добавить метрики с низкой кардинальностью, которые можно отслеживать в New Relic dashboards.

Этот backlog опирается на текущее состояние репозитория:

- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyServiceImpl.java` уже стал тонким фасадом, а dashboard shell/detail, analytics и orchestration записи разнесены по узким сервисам;
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/SystemDashboardService.java` смешивает process stats, DB ping, парсинг логов и snapshot HTTP-метрик в одном сервисе;
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/HttpRequestMetricsFilter.java` считает payload на response path через injected `ObjectMapper`, предпочитает `Content-Length` и избегает полной сериализации для больших ответов;
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/TraceFilter.java` уже кладёт `traceId` в MDC, но propagation trace по-прежнему завязан на custom header и не покрывает более богатые поля request scope;
- wiring New Relic runtime уже есть в `.env.example`, `docker-compose.yml`, `docker-compose.native.yml` и `docs/monitoring/newrelic.md`, а backend metrics export path, KPI instrumentation и typed config mappings теперь добавлены.

[Наверх](#top)

<a id="priority-scale"></a>
## Шкала приоритетов

- `P0`: максимальный leverage; затрагивает hot paths, безопасность контрактов или production diagnostics
- `P1`: важное структурное или operability-доработка после `P0`
- `P2`: polish, hardening или более глубокая оптимизация после подтверждения измерениями

[Наверх](#top)

<a id="current-repo-signals"></a>
## Сигналы текущего состояния

- `FamilyServiceImpl.loadFamilyData(...)` всё ещё собирает большой snapshot и всегда тащит данные history и requests для активного child path, а отдельные history/requests endpoints уже paginated.
- `FamilyServiceImpl.getAnalyticsData(...)` уже использует aggregation helpers, но сервис остаётся владельцем слишком большого числа read models и cache candidates.
- `HistoryRepository` уже содержит aggregate JPQL, а `V19__add_composite_indexes.sql` уже добавил несколько составных индексов, поэтому следующий проход по БД должен расширять уже измеренные query paths, а не начинаться с нуля.
- `HttpRequestMetricsRegistry` живёт в памяти и полезен для super-admin dashboard, но это не долговечный telemetry pipeline.
- `SystemDashboardService` читает логи из локальных файлов, что полезно для текущей super-admin console, но не должно становиться основным production-источником observability.

[Наверх](#top)

<a id="backlog-map"></a>
## Карта бэклога

| ID | Приоритет | Тема | Основной результат |
| --- | --- | --- | --- |
| BAP-01 | P0 | Структура | Разделить `FamilyServiceImpl` на обязанности query, command и analytics |
| BAP-02 | P1 | Структура | Декомпозировать `SystemDashboardService` на узкие сервисы за стабильным фасадом |
| BAP-03 | P1 | Структура | Ввести типизированные config mappings для performance и observability настроек ✅ |
| BAP-04 | P0 | Скорость/RAM | Разделить shell payload dashboard и тяжёлые child detail payloads |
| BAP-05 | P0 | Скорость/RAM | Убрать двойную сериализацию из HTTP metrics collection |
| BAP-06 | P1 | Скорость/RAM | Добавить short-lived cache и явную invalidation для стабильных read-heavy путей |
| BAP-07 | P0 | БД | Добавить pagination contracts для history и requests |
| BAP-08 | P1 | БД | Заменить редкие fallback N+1 чтения батчевой hydration для missing IDs |
| BAP-09 | P1 | БД | Добавить следующие измеренные composite indexes и проверки query-plan |
| BAP-10 | P0 | Наблюдаемость | Обновить propagation trace и MDC scope fields |
| BAP-11 | P1 | Наблюдаемость | Добавить лёгкие readiness checks для критичных runtime dependency ✅ |
| BAP-12 | P1 | Наблюдаемость | Добавить slow-request и slow-query диагностику без лишнего шума ✅ |
| BAP-13 | P0 | New Relic | Добавить реальный metrics export path для New Relic dashboards |
| BAP-14 | P0 | New Relic | Инструментировать ключевые backend business и platform KPI ✅ |
| BAP-15 | P1 | New Relic | Описать widgets dashboard, NRQL queries и thresholds оповещений |

[Наверх](#top)

<a id="project-structure"></a>
## 1. Структура проекта

### BAP-01 - Разделить `FamilyServiceImpl` на query, command и analytics обязанности

Приоритет: P0

Статус: выполнено 2026-07-09.

Основные файлы:

- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyService.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyServiceImpl.java`
- новый `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyDashboardQueryService.java`
- новый `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyDashboardQueryServiceImpl.java`
- новый `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyCommandService.java`
- новый `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyCommandServiceImpl.java`
- новый `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/AnalyticsService.java`
- новый `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/AnalyticsServiceImpl.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/FamilyResource.java`

Архитектурное решение:

Сохранить текущий контракт `resource -> service -> repository`, но перестать использовать одну реализацию сервиса как владельца всех family read/write flows. Публичный API может остаться под `FamilyService`, а внутренняя ответственность должна перейти в более узкие сервисы, каждый из которых владеет своим типом нагрузки.

Делать:

- вынести загрузку dashboard в отдельный query service;
- вынести orchestration записи и решения о refresh snapshot в command service;
- вынести assembly analytics в отдельный analytics service;
- держать mapping DTO рядом с тем сервисом, который владеет use case, вместо того чтобы прятать бизнес-правила в repositories.

Критерии проверки:

- `FamilyResource` по-прежнему зависит только от service-layer contracts и не тянет repositories напрямую;
- ни один новый сервис не смешивает HTTP mapping и persistence;
- `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/FamilyServiceImplTest.java` либо заменён более узкими тестами, либо сведен к поведению фасада;
- `JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify` проходит в `apps/backend`.

### BAP-02 - Декомпозировать `SystemDashboardService` на узкие сервисы за стабильным фасадом

Приоритет: P1

Основные файлы:

- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/SystemDashboardService.java`
- новый `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/SystemOverviewService.java`
- новый `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/DatabaseHealthService.java`
- новый `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/ApplicationLogService.java`
- новый `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/HttpMetricsSnapshotService.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/SuperAdminResource.java`

Архитектурное решение:

Оставить `SystemDashboardService` только как фасад, если super-admin resource нужен стабильный entry point, но реальную логику перенести в узкие сервисы. Это уменьшит размер методов, изолирует IO-heavy обработку логов и сделает будущий export метрик независимым от shape DTO super-admin dashboard.

Критерии проверки:

- у каждого выделенного сервиса есть одна понятная причина для изменения;
- чтение хвоста логов изолировано от DB ping и process-stat кода;
- `apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/SuperAdminResourceTest.java` по-прежнему покрывает observable responses;
- в существующих `/api/super/*` endpoints нет регрессии поведения.

### BAP-03 - Ввести типизированные config mappings для performance и observability

Приоритет: P1

Статус: выполнено 2026-07-09.

Основные файлы:

- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/AppConfig.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/HttpRequestMetricsFilter.java`
- `apps/backend/src/test/java/com/sashplatonov/earnit/kids/config/InfrastructureFiltersTest.java`
- `apps/backend/src/test/java/com/sashplatonov/earnit/kids/config/NewRelicMetricsExportSmokeTest.java`
- `apps/backend/src/main/resources/application.properties`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/AppConfig.java`
- `.env.example`
- `docs/monitoring/newrelic.md`

Архитектурное решение:

Сгруппированные operational settings нужно вынести из разрозненных строковых lookup в типизированные `@ConfigMapping`-типы. Это сделает performance knobs и telemetry toggles тестируемыми, заметными и проще развиваемыми, чем ad hoc чтение properties.

Критерии проверки:

- новые группы config имеют безопасные local defaults и полностью описаны в `.env.example`;
- в коде и документации нет закоммиченных секретов;
- startup проходит с текущим local `.env` contract и с `.env.example`, пропущенным через Docker Compose;
- `docker compose --env-file .env.example config` остаётся валидным.

[Наверх](#top)

<a id="service-speed-and-ram"></a>
## 2. Скорость и RAM сервиса

### BAP-04 - Разделить shell payload dashboard и тяжёлые child detail payloads

Приоритет: P0

Статус: выполнено 2026-07-09.

Основные файлы:

- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyServiceImpl.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/FamilyResource.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/FamilyDataResponse.java`
- новые child-scoped response DTO в `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/HistoryRepository.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/PurchaseRequestRepository.java`

Архитектурное решение:

Оставить для app shell один тонкий bootstrap contract и загружать history или request-heavy sections только тогда, когда пользователь их открывает. Основной dashboard path не должен платить за самые тяжёлые коллекции, если UI явно их не требует.

Делать:

- разделить initial payload на shell metadata плюс child detail endpoints или child detail sub-queries;
- перестать возвращать full history и full requests при каждом открытии dashboard по умолчанию;
- сохранить один согласованный источник истины для текущего выбранного child.

Критерии проверки:

- открытие dashboard по умолчанию больше не требует full history и requests в первом payload;
- размер ответа начального family shell path заметно меньше текущего baseline;
- интеграционные тесты доказывают, что admin и child sessions по-прежнему получают корректный active child state;
- если меняется endpoint contract, backend tests и связанная frontend contract doc обновляются в том же изменении.

### BAP-05 - Убрать двойную сериализацию из HTTP metrics collection

Приоритет: P0

Статус: выполнено 2026-07-09.

Основные файлы:

- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/HttpRequestMetricsFilter.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/HttpRequestMetricsRegistry.java`
- `apps/backend/src/test/java/com/sashplatonov/earnit/kids/config/InfrastructureFiltersTest.java`

Архитектурное решение:

HTTP metrics не должны становиться bottleneck на hot path, который они должны измерять. Подсчёт размера payload должен использовать уже внедрённую инфраструктуру и деградировать мягко, когда точный размер слишком дорог или невозможен.

Делать:

- использовать уже существующий application `ObjectMapper` вместо создания нового на каждый response;
- предпочитать `Content-Length`, когда он доступен;
- включать estimate размера payload через config, sampling или max-size threshold;
- держать filter дешёвым для больших JSON payload и binary downloads.

Критерии проверки:

- в response filter больше нет `new ObjectMapper()`;
- тесты HTTP metrics по-прежнему покрывают счётчики запросов и поведение payload-size;
- большие response paths не требуют второй полной сериализации объекта только ради telemetry.

### BAP-06 - Добавить short-lived cache и явную invalidation для стабильных read-heavy путей

Приоритет: P1

Основные файлы:

- `apps/backend/pom.xml`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/BaseDataService.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyServiceImpl.java`
- `apps/backend/src/main/resources/application.properties`
- `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/BaseDataServiceTest.java`

Архитектурное решение:

Кешировать только те данные, которые стабильны, часто читаются и имеют понятную модель invalidation. Хорошие кандидаты: base data, analytics slices с коротким TTL и почти неизменяемые catalog metadata. Не кешировать mutable dashboard state без явного scope key.

Критерии проверки:

- у каждого cached метода зафиксированы TTL и правила invalidation в комментариях или config docs;
- cache key включает family и child scope, если данные scoped;
- тесты доказывают, что записи всё ещё видны свежими там, где нужна немедленная consistency;
- cache не добавлен в mutable request или balance mutation paths.

[Наверх](#top)

<a id="database-optimization"></a>
## 3. Оптимизация БД

### BAP-07 - Добавить pagination contracts для history и requests

Приоритет: P0

Статус: выполнено 2026-07-09.

Основные файлы:

- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/HistoryRepository.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/PurchaseRequestRepository.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyServiceImpl.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/FamilyResource.java`
- новые paged DTO в `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/`

Архитектурное решение:

Неограниченные коллекции history и requests не должны оставаться частью дефолтного backend contract. Их нужно явно пейджить, желательно по стабильным ключам сортировки вроде `createdAt` плюс `id`, чтобы PostgreSQL и H2 в тестах сохраняли детерминированный порядок.

Критерии проверки:

- repository methods явно принимают размер страницы и cursor или page параметры;
- ни один endpoint с потенциально неограниченным ростом не возвращает полный список по умолчанию;
- новые тесты покрывают первую страницу, следующую страницу и пустую страницу;
- DB query plans используют уже существующие или новые composite indexes вместо тяжёлых sort-heavy scan.

### BAP-08 - Заменить редкие fallback N+1 чтения батчевой hydration для missing IDs

Приоритет: P1

Основные файлы:

- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyServiceImpl.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TaskRepository.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/ShopItemRepository.java`
- `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/FamilyServiceImplTest.java`

Архитектурное решение:

Текущие lookup maps уже закрывают common path, но редкий fallback path всё ещё читает отсутствующие archived task или shop item строки по одной. Это нужно заменить одной batch lookup на каждый response assembly, если есть missing IDs.

Критерии проверки:

- mixed history/request payload с несколькими missing related IDs делает не более одного fallback query на тип entity;
- поведение ответа остаётся тем же для названий archived task или shop item;
- тесты проверяют archived fallback path, а не только happy path с in-memory lookup maps.

### BAP-09 - Добавить следующие измеренные composite indexes и проверки query-plan

Приоритет: P1

Основные файлы:

- новый `apps/backend/src/main/resources/db/migration/V20__add_history_request_paging_indexes.sql`
- mirrored H2-safe migration files в `apps/backend/src/test/resources/db/migration/`, только если репозиторий по-прежнему требует явного дублирования для test migrations
- `apps/backend/src/test/java/com/sashplatonov/earnit/kids/repository/RepositorySmokeTest.java`
- `apps/backend/docs/ARCHITECTURE.md`

Архитектурное решение:

Добавлять индексы только для измеренных query paths, появившихся из pagination, analytics filters или backup history access. Держать индексную работу в соответствии с реальными repository predicates, а не с абстрактными "может пригодиться" колонками.

Критерии проверки:

- каждый новый индекс соответствует именованному repository query path;
- комментарии миграции объясняют, зачем нужен индекс;
- PostgreSQL `EXPLAIN` для затронутых запросов показывает index-backed execution на representative filters;
- H2 test baseline по-прежнему мигрирует из пустой схемы.

[Наверх](#top)

<a id="observability"></a>
## 4. Наблюдаемость

### BAP-10 - Довести trace propagation до стандарта `traceparent` + MDC correlation

Приоритет: P0

Статус: выполнено 2026-07-09.

Основные файлы:

- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/TraceFilter.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/AuthContext.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/AuthFilter.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/FamilyResource.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/SuperAdminResource.java`
- `apps/backend/src/test/java/com/sashplatonov/earnit/kids/config/InfrastructureFiltersTest.java`

Архитектурное решение:

Считать W3C trace context предпочтительным входным форматом и оставить `X-Trace-Id` как совместимый fallback. Расширить MDC low-cardinality полями scope, такими как role, family ID и active child ID, там, где это реально помогает разбирать инциденты.

Критерии проверки:

- inbound `traceparent` принимается, если присутствует;
- fallback `X-Trace-Id` по-прежнему работает для существующих клиентов;
- MDC всегда очищается после response;
- логи для request-scoped операций включают `traceId` и стабильные scope identifiers без утечки секретов или high-cardinality данных.

### BAP-11 - Добавить лёгкие readiness checks для критичных runtime dependency ✅

Приоритет: P1

Основные файлы:

- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/NewRelicMetricsReadinessCheck.java`
- новые health checks в `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/`
- `apps/backend/src/main/resources/application.properties`
- `apps/backend/src/test/java/com/sashplatonov/earnit/kids/config/InfrastructureFiltersTest.java`
- `apps/backend/src/test/java/com/sashplatonov/earnit/kids/config/ReadinessChecksTest.java`
- `docs/monitoring/newrelic.md`

Архитектурное решение:

Agroal уже покрывает подключение к БД. Дополнительные readiness checks должны быть нацелены на backend-specific зависимости, которые могут тихо сломаться, например обязательная runtime-конфигурация для включённых интеграций. Проверки должны быть быстрыми и без side effects.

Критерии проверки:

- `/q/health/ready` показывает degraded state, когда недоступна включённая критичная зависимость;
- health checks не обращаются к тяжёлым сервисам или внешним сетям на каждый probe;
- тесты доказывают обе ветки: healthy и degraded readiness.

### BAP-12 - Добавить slow-request и slow-query диагностику без лишнего шума ✅

Приоритет: P1

Основные файлы:

- `apps/backend/src/main/resources/application.properties`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/HttpRequestMetricsFilter.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/SlowOperationDiagnostics.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/HttpRequestMetricsRegistry.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/FamilyDataRepository.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/FamilyRepository.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/ChildRepository.java`
- `apps/backend/src/test/java/com/sashplatonov/earnit/kids/config/InfrastructureFiltersTest.java`
- `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/SlowOperationDiagnosticsTest.java`
- `docs/monitoring/newrelic.md`

Архитектурное решение:

Не надо заливать логи per-request шумом. Вместо этого добавить threshold-driven diagnostics для медленных запросов и дорогих операций, завязанных на БД, используя существующий trace context, чтобы медленные пути можно было связать в логах и New Relic.

Критерии проверки:

- thresholds настраиваются;
- extra diagnostic entries пишутся только для медленных или падающих запросов;
- log statements содержат `traceId`, request path, duration и затронутые scope identifiers;
- при нормальной нагрузке не появляется дублирующий шум в логах.

[Наверх](#top)

<a id="new-relic-metrics"></a>
## 5. Метрики New Relic

### BAP-13 - Добавить реальный metrics export path для New Relic dashboards

Приоритет: P0

Статус: выполнено 2026-07-09.

Основные файлы:

- `apps/backend/pom.xml`
- `apps/backend/src/main/resources/application.properties`
- `.env.example`
- `docker-compose.yml`
- `docker-compose.native.yml`
- `docs/monitoring/newrelic.md`

Архитектурное решение:

Оставить текущий JVM agent path для APM и логов, но добавить явный backend metrics export path для dashboard-grade time series. Экспорт должен использовать low-cardinality tags и не должен зависеть от вычитывания JSON из super-admin HTTP metrics.

Критерии проверки:

- выбранный path метрик документирован в repo docs вместе с env vars и режимом rollout;
- метрики можно включать и выключать конфигурацией без изменения кода;
- `docker compose --env-file .env.example config` остаётся валидным после добавления env;
- хотя бы один test или startup smoke path доказывает, что backend поднимается с включённым metrics extension.

### BAP-14 - Инструментировать ключевые backend business и platform KPI

Приоритет: P0

Статус: выполнено 2026-07-09.

Основные файлы:

- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyServiceImpl.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/AuthServiceImpl.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/WebSocketNotificationService.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/FamilyWebSocket.java`

Архитектурное решение:

Инструментировать операции на уровне service layer, а не resource methods, в соответствии со стандартами репозитория. Сначала сосредоточиться на KPI, которые важны operationally:

- latency загрузки dashboard;
- latency загрузки analytics;
- количество auth success и auth failure;
- длительность критичных admin flows и количество operational failures;
- количество active sessions WebSocket или fan-out notification.

Критерии проверки:

- метрики эмитятся из границ service layer и используют только low-cardinality labels;
- ни одна метрика не использует raw email, token, free-text message или неограниченные path fragments в качестве tag;
- тесты или узкие smoke checks подтверждают, что метрики срабатывают на success и failure path хотя бы для одного ключевого сервиса.

### BAP-15 - Описать widgets dashboard, NRQL queries и alert thresholds

Приоритет: P1

Основные файлы:

- новый `docs/monitoring/newrelic-dashboard.md`
- `docs/monitoring/newrelic.md`

Архитектурное решение:

Не останавливаться на instrumentation. В репозитории должен быть минимальный operational contract о том, как читать backend в New Relic: какие widgets существуют, на какие вопросы они отвечают и какие thresholds должны warning или page.

Критерии проверки:

- docs содержат конкретные widget definitions и примеры NRQL queries;
- docs разделяют rollout-safe warning thresholds и более жёсткие alert thresholds на следующем этапе;
- dashboard docs ссылаются только на те метрики, которые backend реально эмитит после BAP-13 и BAP-14.

[Наверх](#top)

<a id="verification-matrix"></a>
## Матрица проверки

Использовать эту матрицу при выполнении backlog items:

| Область | Минимальная проверка |
| --- | --- |
| Изменения backend-кода | `cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify` |
| Wiring конфигурации или env | `docker compose --env-file .env.example config` |
| Изменения health/observability | проверить `/q/health`, `/q/health/ready`, поля логов и любое новое поведение metrics endpoint/export |
| Изменения DB query или migration | проверить PostgreSQL migration path плюс H2 test baseline |
| Wiring New Relic | проверить path запуска agent, arrival метрик и результаты dashboard query в целевом аккаунте New Relic |

Рекомендуемый порядок выполнения:

1. `BAP-01` ✅
2. `BAP-04` ✅
3. `BAP-05` ✅
4. `BAP-07` ✅
5. `BAP-10` ✅
6. `BAP-13` ✅
7. `BAP-14` ✅
8. все `P1` items после свежих измерений

Оптимальная очередь `P1` после свежих измерений для ИИ-агента:

1. `BAP-03` ✅
2. `BAP-11` ✅
3. `BAP-12` ✅
4. `BAP-02`
5. `BAP-06`
6. `BAP-08`
7. `BAP-09`
8. `BAP-15`

[Наверх](#top)
