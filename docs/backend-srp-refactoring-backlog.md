# Backend SRP Refactoring Backlog

## Scope

Подготовить и выполнить backend backlog для двух связанных направлений:

- найти крупные backend-классы, которые смешивают несколько обязанностей и нарушают SRP
- декомпозировать их без изменения публичного API-контракта
- усилить quality gates и линтеры в `apps/backend`, чтобы подобные class hotspots и complexity-регрессии не проходили через `verify` и CI

## Current Repo Signals

- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyActionServiceImpl.java` — `1171` строк; в одном классе смешаны task completion, request approval/reject, bulk actions, CSV import, balance adjustments, history building, frequency-limit validation.
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/FamilyResource.java` — `913` строк; один resource держит family save, task/item actions, imports, child settings, friends, token regeneration, parent membership management.
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyDashboardQueryServiceImpl.java` — `722` строки; один query service одновременно грузит scope, собирает shell/detail/full payloads, гидрирует history/requests, маппит DTO.
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/AuthServiceImpl.java` — `515` строк; один service держит admin/family/child auth, Google auth, family selection, password reset/change, email verification, password rehash.
- `apps/backend/pom.xml` уже включает `JaCoCo`, `Checkstyle`, `SpotBugs`, но PMD отсутствует.
- `apps/backend/config/checkstyle.xml` уже ограничивает `MethodLength` (`60`) и `ParameterNumber` (`10`), но не ограничивает размер класса, число методов и cyclomatic complexity.
- `apps/backend/pom.xml` запускает SpotBugs в `verify`, но сейчас там `failOnError=false`, поэтому часть quality-сигналов не режет сборку.
- `.github/workflows/quality.yml` запускает только backend `./mvnw -B -ntp verify`, значит все новые quality rules должны входить именно в Maven lifecycle без отдельного ручного шага.

## Assumptions

- Публичные HTTP endpoints и payload contract должны остаться обратно совместимыми.
- Рефакторинг идёт без изменения схемы БД, если в ходе декомпозиции не выявится реальная структурная проблема.
- Основной enforcement должен жить в `apps/backend/pom.xml` и `apps/backend/config/*`, а не только в локальных IDE-настройках.
- Приоритет у production source under `src/main/java`; тестовый код можно приводить в порядок отдельно, если новые правила начнут его затрагивать.

## Out Of Scope

- Переписывание frontend или mobile под новые backend contracts.
- Массовый rename пакетов без прямой пользы для SRP.
- Введение новой архитектурной платформы или смена Quarkus stack.
- Общий cleanup всех backend-классов без приоритизации по hotspot-ам.
- Ослабление coverage threshold в JaCoCo ради прохождения рефакторинга.

## Execution Rules For AI Agent

- Начинать с измеримого baseline: `wc -l`, число публичных методов, текущее состояние `./mvnw verify`.
- Не смешивать рефакторинг hotspot-ов и усиление линтеров в один большой commit без промежуточной валидации.
- Сначала декомпозировать class responsibilities, затем включать более жёсткие rules; иначе backlog быстро упрётся в существующий долг без пути выхода.
- Не переносить business logic в `resource/` ради уменьшения размера service-классов.
- Все новые quality rules должны быть объяснимы текущими проблемами в коде и сопровождаться remediation-путём.
- После каждого заметного разбиения класса прогонять хотя бы targeted tests, а финально всегда `apps/backend ./mvnw verify`.
- Если rule даёт шумные false positive, сначала сузить scope или настроить rule, а не отключать весь quality gate.

## Tasks

| ID | Priority | Area | Task | Files | Acceptance Criteria | Verification | Risk |
|---|---:|---|---|---|---|---|---|
| SRP-01 | P0 | Analysis | Собрать baseline по backend hotspot-ам: размер классов, число публичных методов, текущие `verify`/Checkstyle/SpotBugs ограничения, и зафиксировать стартовый список SRP-нарушений. | `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyActionServiceImpl.java`, `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/FamilyResource.java`, `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyDashboardQueryServiceImpl.java`, `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/AuthServiceImpl.java`, `apps/backend/pom.xml`, `apps/backend/config/checkstyle.xml` | Есть подтверждённый inventory классов-кандидатов и список причин, почему они считаются multi-responsibility hotspots. | `find apps/backend/src/main/java -name '*.java' -print0 | xargs -0 wc -l | sort -nr | head`, `./mvnw -q help:effective-pom`, `./mvnw verify` | Можно ошибочно выбрать большие, но допустимо cohesive классы; это создаст лишнюю работу. |
| SRP-02 | P0 | Service | Разбить `FamilyActionServiceImpl` на отдельные service-компоненты по обязанностям: actions, request workflow, bulk actions, imports, balance/history helpers. | `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyActionServiceImpl.java`, новые классы в `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/` | `FamilyActionServiceImpl` перестаёт быть orchestration god-class; import/bulk/request/history logic вынесены в отдельные типы с ясными границами ответственности. | `./mvnw test`, `./mvnw verify` | Высокий риск регрессии в child/admin flows и snapshot refresh после команд. |
| SRP-03 | P0 | Resource | Разделить `FamilyResource` на несколько resource-классов по bounded context без изменения URL и auth contract: family commands, imports, child settings/friends, parent membership management. | `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/FamilyResource.java`, новые resource-классы в `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/` | Endpoint surface сохраняется, но responsibilities resource-слоя распределены по тематическим классам с более короткими методами и меньшей связностью. | `./mvnw test`, `./mvnw verify`, smoke-check OpenAPI `/api/openapi.yaml` | Возможен accidental contract drift по путям, аннотациям или security scope. |
| SRP-04 | P1 | Query | Декомпозировать `FamilyDashboardQueryServiceImpl` на loader/assembler/hydrator mapper-компоненты, чтобы query-path не держал scope loading, DTO assembly и enrichment в одном типе. | `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyDashboardQueryServiceImpl.java`, новые query/helper классы | Shell/detail/full response assembly разделены по ответственности; DTO mapping и enrichment больше не живут в одном большом классе. | `./mvnw test`, `./mvnw verify` | Есть риск деградации dashboard payload parity и порядка гидрации данных. |
| SRP-05 | P1 | Auth | Разделить `AuthServiceImpl` на отдельные auth flows: membership resolution, admin/family auth, child auth, password lifecycle, email verification, Google auth. | `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/AuthServiceImpl.java`, новые auth service/helper классы | `AuthServiceImpl` остаётся thin facade или исчезает; password reset/rehash/verification и membership selection изолированы по отдельным обязанностям. | `./mvnw test`, `./mvnw verify` | Риск скрытых регрессий в login и recovery flows, особенно для multi-family parent access. |
| SRP-06 | P0 | Lint | Усилить Checkstyle rules под реальные найденные проблемы: ввести ограничения на размер класса, cyclomatic complexity, число методов или эквивалентные structural guards. | `apps/backend/config/checkstyle.xml`, `apps/backend/pom.xml` | Крупные монолитные классы и чрезмерно сложные методы начинают падать на `verify`, а правила не конфликтуют с текущей кодовой базой после рефакторинга. | `./mvnw verify` | Слишком жёсткие thresholds могут дать большой шум и затормозить delivery. |
| SRP-07 | P0 | Lint | Добавить PMD в backend `verify` с ruleset-ами, которые ловят class-level design debt: `GodClass`, `TooManyMethods`, `ExcessiveClassLength`, `CyclomaticComplexity`, `NPathComplexity`. | `apps/backend/pom.xml`, новый PMD config в `apps/backend/config/` | PMD встроен в `verify`, правила воспроизводимы локально и в CI, а текущие SRP-hotspot-ы больше не могут вернуться незаметно. | `./mvnw -B -ntp pmd:check`, `./mvnw verify`, GitHub Actions `Quality` | PMD может потребовать точной настройки excludes/thresholds, иначе появятся noisy violations. |
| SRP-08 | P1 | Quality Gate | Перевести SpotBugs на fail-fast режим после очистки baseline и проверить, что quality gate действительно блокирует новые backend-нарушения. | `apps/backend/pom.xml`, `apps/backend/config/spotbugs-exclude.xml`, `.github/workflows/quality.yml` | `SpotBugs` больше не работает в report-only режиме; `verify` и CI падают при новых реальных нарушениях. | `./mvnw verify`, локальная проверка артефактов SpotBugs, GitHub Actions `Quality` | Если baseline не дочищен, перевод на fail-fast может временно заблокировать ветку. |
| SRP-09 | P1 | Docs | Обновить backend architecture/quality docs после завершения рефакторинга и описать новые rules, thresholds и expected package boundaries. | `apps/backend/docs/ARCHITECTURE.md`, `docs/testing.md`, этот backlog | Документация отражает новую декомпозицию и объясняет, какими правилами она теперь защищена в CI. | doc review + `./mvnw verify` | Если не обновить docs, следующие изменения снова начнут размывать границы слоёв. |

## Task Details

### SRP-01 — Baseline and hotspot inventory

**Goal**

Зафиксировать измеримый baseline до рефакторинга, чтобы дальнейшие изменения были привязаны к фактам, а не к субъективному ощущению, что класс “слишком большой”.

**Architecture / how to analyze**

- Считать отдельно:
- размер файла в строках
- число публичных endpoint/service entrypoint methods
- набор обязанностей внутри класса
- внешние зависимости класса по слоям
- Для каждого hotspot-класса составлять mini responsibility map:
- входные use-case методы
- orchestration logic
- validation logic
- mapping/DTO assembly
- persistence access helpers
- utility/date/format helpers
- Признаком SRP-нарушения считать не только размер, но и смешение разных change vectors в одном файле.

**Expected artifacts**

- Обновлённый этот backlog с подтверждёнными hotspot-ами.
- При необходимости отдельный working note в PR/commit description с baseline-метриками.

**Verification**

1. `cd apps/backend && find src/main/java -name '*.java' -print0 | xargs -0 wc -l | sort -nr | head -n 20`
2. `cd apps/backend && rg -n "^[[:space:]]*public .*\\(" src/main/java/com/sashplatonov/earnit/kids/service/FamilyActionServiceImpl.java src/main/java/com/sashplatonov/earnit/kids/service/FamilyDashboardQueryServiceImpl.java src/main/java/com/sashplatonov/earnit/kids/service/AuthServiceImpl.java src/main/java/com/sashplatonov/earnit/kids/resource/FamilyResource.java`
3. `cd apps/backend && ./mvnw verify`

**Done when**

- Для каждого кандидата явно зафиксировано, какие ответственности в нём смешаны.
- Есть стартовый список классов, которые реально идут в рефакторинг первой волной.

### SRP-02 — Split `FamilyActionServiceImpl`

**Goal**

Разбить `FamilyActionServiceImpl` по command-oriented обязанностям, чтобы orchestration не смешивался с import, request workflow, history building и frequency-limit logic.

**Architecture / how to split files**

- Оставить в `FamilyActionServiceImpl` только thin orchestration facade или coordination-level entrypoints.
- Вынести в отдельные классы:
- `FamilyTaskActionService` или эквивалент для task completion и task request flows
- `FamilyShopActionService` для purchase/item request flows
- `FamilyRequestWorkflowService` для approve/reject/delete request
- `FamilyBulkActionService` для bulk task/shop operations
- `FamilyImportService` для CSV import и import validation
- `FamilyHistoryFactory` или `FamilyHistoryService` для построения history entities/messages
- `FamilyActionLimitPolicy` для frequency-limit и pending-request checks
- Не выносить raw repository access в `resource`; все extracted collaborators должны оставаться в service-layer.
- Общие utility-функции переносить только если у них есть доменная ответственность; иначе удалять дублирование после split.

**File boundaries**

- Один extracted file = одна доменная обязанность или один тесно связанный workflow.
- Не создавать helper-файлы “misc”, “utils”, “common” без предметной роли.

**Verification**

1. `cd apps/backend && ./mvnw test`
2. `cd apps/backend && ./mvnw verify`
3. Проверить, что команды `completeTask`, `purchaseItem`, `approveRequest`, `bulkTaskAction`, `importTasks`, `importShopItems`, `adjustBalance` по-прежнему собирают валидный family snapshot.

**Done when**

- Основной файл заметно уменьшился.
- Import, bulk, request workflow и history construction больше не живут в одном классе.
- Все entrypoint методы по-прежнему проходят backend verify gate.

### SRP-03 — Split `FamilyResource`

**Goal**

Разделить перегруженный HTTP resource по bounded context, не ломая маршруты, авторизацию и OpenAPI surface.

**Architecture / how to split files**

- Делить по API-группам, а не по HTTP verbs.
- Предпочтительная декомпозиция:
- `FamilyCommandResource` для save, task/item actions, balance adjustments
- `FamilyImportResource` для import endpoints
- `FamilyChildSettingsResource` для nickname/theme/group-order/settings
- `FamilyFriendResource` для friends/search/link/token flows
- `FamilyParentAccessResource` для list/add/update/remove parent memberships
- Общие auth/context extraction helpers можно вынести в отдельный `resource`-level support type, если это не тянет business logic вверх.
- При делении сохранять текущие `@Path` и payload contracts; если используется единый root path, несколько resource-классов должны продолжать обслуживать тот же API surface через под-path и общие conventions.

**Verification**

1. `cd apps/backend && ./mvnw test`
2. `cd apps/backend && ./mvnw verify`
3. Поднять app локально и проверить, что `/api/openapi.yaml` генерируется без пропавших операций.
4. Сверить, что старые route patterns не изменились без отдельного решения.

**Done when**

- В каждом resource-классе endpoints относятся к одной тематической группе.
- Нет переноса доменной логики из service в resource ради механического уменьшения строк.

### SRP-04 — Split `FamilyDashboardQueryServiceImpl`

**Goal**

Убрать из одного query service одновременное владение family scope loading, hydration, mapping и response assembly.

**Architecture / how to split files**

- Разделить query path как минимум на четыре роли:
- `FamilyScopeLoader` для family/child visibility и active child resolution
- `FamilyDashboardDataLoader` для history/requests/friends/tasks/shop-items fetch and hydration
- `FamilyDashboardMapper` для DTO conversion
- `FamilyDashboardResponseAssembler` для shell/detail/full response composition
- Если shell/detail/full имеют разный composition path, допустимы отдельные assembler-классы.
- Внутренние `record`-типы оставлять только если они truly local; shared query models выносить в отдельные top-level files.

**Verification**

1. `cd apps/backend && ./mvnw test`
2. `cd apps/backend && ./mvnw verify`
3. Проверить parity для shell/detail/full payloads по набору полей и правилам выбора active child.

**Done when**

- DTO mapping больше не смешан с repository hydration.
- Response assembly не содержит прямых persistence helper-веток.

### SRP-05 — Split `AuthServiceImpl`

**Goal**

Разделить auth service по самостоятельным auth flows, чтобы изменение password lifecycle не требовало правки family selection, child auth и Google login в одном месте.

**Architecture / how to split files**

- Предпочтительная декомпозиция:
- `ParentAuthenticationService` для admin/family membership auth
- `ChildAuthenticationService` для child token auth
- `GoogleAuthenticationService` для Google credential flow
- `PasswordLifecycleService` для change/reset/rehash/password policy
- `EmailVerificationService` для verify/forgot/reset token handling
- `FamilySelectionService` для multi-family membership resolution
- Оставить общий фасад только если он нужен текущим resource-слоям как стабильный integration point.
- Password hashing/validation logic должен жить рядом с password lifecycle, а не размазываться по нескольким auth services.

**Verification**

1. `cd apps/backend && ./mvnw test`
2. `cd apps/backend && ./mvnw verify`
3. Проверить сценарии:
- admin login
- child login
- Google login
- family selection
- forgot/reset password
- email verification

**Done when**

- Каждый auth flow редактируется в изолированном классе.
- `AuthServiceImpl` перестаёт быть центральным местом для всех видов аутентификации и recovery.

### SRP-06 — Strengthen Checkstyle structural rules

**Goal**

Добавить structural guards на уровне Checkstyle, чтобы будущие oversized classes и excessive methods ловились рано.

**Architecture / how to split config**

- Все Checkstyle-изменения держать в `apps/backend/config/checkstyle.xml`.
- Если rule невозможно выразить в текущем config cleanly, допускается подключение дополнительного suppression/config file, но он должен жить рядом в `apps/backend/config/`.
- Правила подбирать после первой волны split, чтобы thresholds были реалистичными.
- Приоритетные ограничения:
- class size
- method complexity
- method count или эквивалентный size guard
- nested branching pressure в публичных orchestration methods

**Verification**

1. `cd apps/backend && ./mvnw verify`
2. Намеренно проверить, что до рефакторинга hotspot-класс бы падал по новым ограничениям, а после рефакторинга baseline остаётся зелёным.

**Done when**

- Checkstyle реально режет structural regressions, а не только форматирование и naming.

### SRP-07 — Add PMD design rules to `verify`

**Goal**

Подключить PMD как основной detector class-level design debt, который Checkstyle покрывает неполно.

**Architecture / how to split config**

- Подключить `maven-pmd-plugin` в `apps/backend/pom.xml`.
- Вынести PMD ruleset в отдельный файл, например `apps/backend/config/pmd-ruleset.xml`.
- Настраивать ruleset явно, а не полагаться на дефолты Maven.
- Стартовый набор правил:
- `GodClass`
- `TooManyMethods`
- `ExcessiveClassLength`
- `CyclomaticComplexity`
- `NPathComplexity`
- При необходимости отдельно настроить thresholds для `resource` и `service`, но не скрывать реальные hotspots broad exclude-ами.

**Verification**

1. `cd apps/backend && ./mvnw -B -ntp pmd:check`
2. `cd apps/backend && ./mvnw verify`
3. Убедиться, что `Quality` workflow в GitHub Actions использует тот же Maven lifecycle без отдельного кастомного шага.

**Done when**

- PMD встроен в локальный и CI gate.
- Возврат god-class patterns теперь гарантированно виден в `verify`.

### SRP-08 — Make SpotBugs fail the build

**Goal**

Перевести SpotBugs из advisory режима в blocking gate после очистки baseline.

**Architecture / how to change quality gate**

- Менять только то, что относится к enforcement:
- `failOnError`
- filter file
- при необходимости точечные excludes с явным объяснением
- Не расширять exclude-файл под найденные реальные дефекты, если их можно исправить в коде.
- Проверить, что SpotBugs по-прежнему публикует артефакты в CI даже при падении build.

**Verification**

1. `cd apps/backend && ./mvnw verify`
2. Проверить, что SpotBugs violations действительно приводят к неуспешному `verify`.
3. Проверить `.github/workflows/quality.yml` на сохранение upload step с `if: always()`.

**Done when**

- SpotBugs больше не report-only.
- Реальные backend issues блокируют merge через тот же quality path.

### SRP-09 — Update architecture and quality docs

**Goal**

Синхронизировать документацию с новым разбиением файлов и обновлёнными quality gates.

**Architecture / what to document**

- В `apps/backend/docs/ARCHITECTURE.md` описать новые service/resource/query/auth boundaries.
- В `docs/testing.md` описать обязательный backend verification path с PMD/Checkstyle/SpotBugs.
- В этом backlog отметить закрытые задачи, фактические file splits и финальные thresholds.
- Документация должна объяснять не только “что изменилось”, но и “как дальше не сломать границы”.

**Verification**

1. Проверить, что docs ссылаются на актуальные file names и реальные quality tools.
2. `cd apps/backend && ./mvnw verify`

**Done when**

- Новый contributor может понять целевое разделение backend-классов и обязательные gates без чтения истории PR.

## Implementation Notes

- Предпочтительная стратегия для service-hotspot-ов: thin facade + extracted collaborators, а не один новый helper-класс на 400 строк.
- Для resource-слоя допустимо деление по use-case группам, если остаются прежние `@Path`, HTTP verbs и security expectations.
- Для query-path лучше разделить `scope loading`, `catalog/history/request hydration`, `DTO mapping`, `response assembly`.
- Для auth-path лучше отделять credential verification, membership resolution и token/email lifecycle.
- Для линтеров сначала подобрать thresholds по уже отрефакторенной кодовой базе, затем зафиксировать их как baseline.
- Новые config-файлы желательно держать в `apps/backend/config/` рядом с `checkstyle.xml` и `spotbugs-exclude.xml`.

## Testing Plan

- Основной backend gate: `cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify`
- Быстрый промежуточный gate после каждого этапа разбиения: `cd apps/backend && ./mvnw test`
- Для новых PMD rules отдельно прогонять `cd apps/backend && ./mvnw -B -ntp pmd:check`
- После разбиения resource/auth/query flows проверять, что OpenAPI generation и Quarkus wiring не сломались.
- Если рефакторинг затрагивает auth или family snapshot assembly, добавить/обновить unit tests на соответствующие extracted services.

## Observability Plan

- Сохранить существующие metric names и labels; SRP-рефакторинг не должен менять telemetry contract без отдельного решения.
- После разбиения `FamilyAction` и dashboard/query flows перепроверить, что backend продолжает эмитить `earnit.backend.service.operation.*` и `earnit.backend.http.request.*` без cardinality drift.
- Если для extracted collaborators добавляются новые log lines, они должны оставаться на текущем operational уровне и не засорять production logs.
- После rollout сверить New Relic dashboard на отсутствие аномалий по `family_action`, `dashboard`, `auth`.

## Open Questions

- Какие thresholds для class length и method count считаются реалистичными для текущего backend после первой волны рефакторинга?
- Нужен ли отдельный PMD ruleset для `resource/` и `service/`, или один baseline достаточно точен?
- Должен ли `FamilyResource` сохранять единый root path при делении на несколько resource-классов, или лучше вводить более узкие под-path сегменты без изменения клиентского контракта?
- Нужен ли отдельный backlog follow-up для repository/query SRP, если после service split обнаружатся крупные persistence hotspots?
