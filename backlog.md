# Backlog: Родительские bulk-операции, кастомное подтверждение удаления и CSV import

Статус: `Planned`  
Контекст: `apps/web` + `apps/backend`  
Дата: `2026-06-28`

## Цель

Реализовать три родительских сценария без расползания логики по UI:

1. Групповые операции для заданий и наград: выбрать несколько элементов и массово `удалить` / `заблокировать` / `сменить группу`.
2. Заменить нативный `confirm(...)` при удалении задания на кастомное подтверждение.
3. Добавить быстрый импорт заданий и наград из CSV из буфера обмена с явным описанием обязательных и необязательных колонок в форме импорта.

## Обязательные quality gates

- Backend: `cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify`
- Web lint: `cd apps/web && npm run lint`
- Web unit: `cd apps/web && npm run test`
- Web build: `cd apps/web && npm run build`
- Web coverage: `cd apps/web && npm run test:coverage`
- Web E2E: `cd apps/web && npm run test:e2e`  
  Обязательно, потому что меняется UI родителя и модальные сценарии.

Требование по покрытию:

- новый backend-функционал должен быть покрыт тестами не менее чем на `80%` line coverage;
- новый web-функционал должен быть покрыт unit/e2e тестами не менее чем на `80%` line coverage для добавленных модулей и критических веток сценария;
- нельзя считать задачу завершенной без прогона `build` и `test`.

## Приоритеты и спринты

| Sprint | Priority | Тема | Результат спринта |
| --- | --- | --- | --- |
| Sprint 1 | P0 | Общий контракт bulk-actions + custom confirm | Есть единая модель массовых операций и переиспользуемое кастомное подтверждение удаления |
| Sprint 2 | P0 | Bulk-операции для заданий и наград | Родитель может выбрать несколько записей и массово удалить, заблокировать, сменить группу |
| Sprint 3 | P1 | CSV import из буфера обмена | Родитель может вставить CSV, увидеть схему колонок, ошибки валидации и применить импорт |
| Sprint 4 | P0 | Тесты, покрытие, стабилизация | Все проверки зелёные, регрессии закрыты, покрытие нового кода >= 80% |

## Архитектурная рекомендация

Текущая проблемная точка:

- сейчас задания и награды в web-части меняются через локальный `appStore` и затем целиком отправляются через `scheduleSave()` в `POST /api/data`;
- в backend `FamilyServiceImpl` синхронизирует `tasks/shop` по snapshot-модели: сначала помечает всё удалённым, потом заново upsert-ит пришедший список;
- для одиночного CRUD это ещё терпимо, но для bulk-операций и CSV import такая схема опасна: легко потерять данные при гонках, получить конфликт child scope, дубли, или плохо локализованные ошибки.

Целевое архитектурное решение:

- не раздувать ещё сильнее snapshot-save для bulk/import;
- ввести явные доменные команды для родителя:
  - `bulk update tasks`
  - `bulk update shop items`
  - `import tasks from csv`
  - `import shop items from csv`
- backend должен валидировать payload, выполнять операции транзакционно и возвращать нормализованный `FamilyDataResponse`;
- web после успешной bulk/import операции должен обновляться из серверного snapshot-ответа, а не рассчитывать только на локальные optimistic-мутации;
- кастомное подтверждение удаления должно быть общим компонентом/паттерном, а не копией логики в каждой модалке.

## Backlog задач

| ID | Sprint | Priority | Задача | Depends on |
| --- | --- | --- | --- | --- |
| PB-01 | 1 | P0 | Зафиксировать контракт bulk-actions и import API | none | ✅ |
| PB-02 | 1 | P0 | Добавить общий confirm modal для destructive actions | PB-01 | ✅ |
| PB-03 | 2 | P0 | Добавить режим множественного выбора в заданиях | PB-01 | ✅ |
| PB-04 | 2 | P0 | Добавить режим множественного выбора в наградах | PB-01 | ✅ |
| PB-05 | 2 | P0 | Реализовать backend bulk API для заданий | PB-01 | ✅ |
| PB-06 | 2 | P0 | Реализовать backend bulk API для наград | PB-01 | ✅ |
| PB-07 | 2 | P0 | Связать bulk UI с backend и серверным refresh | PB-03, PB-04, PB-05, PB-06 | ✅ |
| PB-08 | 3 | P1 | Добавить CSV import modal и парсер из clipboard | PB-01 | ✅ |
| PB-09 | 3 | P1 | Реализовать backend import API для заданий и наград | PB-08 | ✅ |
| PB-10 | 3 | P1 | Показ схемы CSV, ошибок валидации и preview импортируемых строк | PB-08, PB-09 | ✅ |
| PB-11 | 4 | P0 | Unit/E2E/backend coverage и стабилизация | all | ✅ |

### PB-01. Зафиксировать контракт bulk-actions и import API

Приоритет: `P0`

Что менять:

- `apps/backend/docs/ARCHITECTURE.md`
- `apps/web/docs/ARCHITECTURE.md`
- этот файл `backlog.md`

Как менять:

- описать единый payload для массовых операций отдельно для `tasks` и `shop`;
- определить, что bulk-операции работают только в родительской сессии с `canEditFamilyData`;
- определить semantics:
  - `delete` = soft delete через существующую серверную модель;
  - `block` = `isActive = false`;
  - `unblock` = `isActive = true`;
  - `changeGroup` = массовый апдейт `groupName`;
- определить поведение import:
  - режим `replace existing by id` не нужен на первом этапе;
  - на первом этапе импорт создаёт новые записи;
  - дубликаты внутри CSV валидируются на клиенте и сервере;
  - ошибки валидации должны возвращаться построчно.

Где могут быть проблемы:

- если не зафиксировать контракт заранее, bulk для `tasks` и `shop` быстро разойдутся по поведению;
- если оставить только snapshot-save, невозможно нормально показать частичные ошибки импорта.

Архитектурно решать так:

- отдельные DTO и endpoints под командную модель, а не скрытая магия внутри `POST /api/data`.

### PB-02. Добавить общий confirm modal для destructive actions

Приоритет: `P0`

Основные файлы:

- `apps/web/src/lib/stores/modal.ts`
- новый `apps/web/src/lib/components/app/modals/ConfirmModal.svelte`
- `apps/web/src/routes/app/+layout.svelte` или текущий общий контейнер модалок
- `apps/web/src/lib/i18n/messages/ru/tasks.ts`
- `apps/web/src/lib/i18n/messages/en/tasks.ts`
- при необходимости `apps/web/src/lib/i18n/messages/ru/shop.ts`
- при необходимости `apps/web/src/lib/i18n/messages/en/shop.ts`

Как менять:

- использовать уже существующий `confirm-modal` ключ в `modalStore`;
- сделать переиспользуемый modal-компонент с `title`, `description`, `confirmLabel`, `cancelLabel`, `tone`, `onConfirm`;
- перевести удаление в `TaskModal.svelte` с нативного `confirm(...)` на этот modal;
- сразу заложить reuse для `ShopModal.svelte` и будущих bulk-delete действий.

Где могут быть проблемы:

- если modal будет принимать только текст, потом придётся дублировать кнопку/стили под одиночное удаление и bulk-delete;
- если `onConfirm` не будет защищён от двойного клика, возможны повторные удаления.

Архитектурно решать так:

- один confirm modal с controlled state и `pending`-состоянием;
- destructive-потоки открывают modal через store, а не через локальные `let showConfirm`.

### PB-03. Добавить режим множественного выбора в заданиях

Приоритет: `P0`

Основные файлы:

- `apps/web/src/lib/components/app/sections/TasksSection.svelte`
- `apps/web/src/lib/components/app/SectionHeaderControls.svelte`
- возможно новый `apps/web/src/lib/components/app/BulkActionToolbar.svelte`
- `apps/web/src/lib/stores/app.ts`
- `apps/web/src/lib/i18n/messages/ru/tasks.ts`
- `apps/web/src/lib/i18n/messages/en/tasks.ts`

Как менять:

- добавить для родителя режим `selection mode`;
- чекбоксы должны появляться только для `isAdmin`;
- хранить `selectedTaskIds` локально в секции или в специализированном UI-store, но не в глобальном domain-store `tasks`;
- добавить toolbar: `Удалить`, `Заблокировать`, `Разблокировать`, `Сменить группу`, `Снять выбор`;
- при смене группы открывать отдельный modal/inline-form на ввод нового `groupName`.

Где могут быть проблемы:

- текущая сортировка/фильтрация по группам может сбрасывать selection при реактивном пересчёте массива;
- режим list/grid и group tabs могут ломать UX выделения;
- child-switch может оставить selection от другого ребёнка.

Архитектурно решать так:

- selection хранить отдельно от данных и сбрасывать при смене `currentChildId`, `selectedGroup`, reload snapshot;
- ключ selection строить по `childId + entityType`.

### PB-04. Добавить режим множественного выбора в наградах

Приоритет: `P0`

Основные файлы:

- `apps/web/src/lib/components/app/sections/ShopSection.svelte`
- `apps/web/src/lib/components/app/SectionHeaderControls.svelte`
- возможно новый `apps/web/src/lib/components/app/BulkActionToolbar.svelte`
- `apps/web/src/lib/i18n/messages/ru/shop.ts`
- `apps/web/src/lib/i18n/messages/en/shop.ts`

Как менять:

- повторить тот же UI-паттерн, что и для задач;
- не дублировать разметку bulk-toolbar, а вынести общий компонент;
- сохранить одинаковые тексты и поведение кнопок между `tasks` и `shop`.

Где могут быть проблемы:

- `ShopSection` уже синхронизирует `selectedGroup` с URL query, и selection mode может конфликтовать с `popstate`;
- визуально bulk mode может сломать компактные карточки на mobile.

Архитектурно решать так:

- bulk-toolbar и selection-checkbox размещать в общей композиции карточки;
- mobile-first проверить отдельно в Playwright.

### PB-05. Реализовать backend bulk API для заданий

Приоритет: `P0`

Основные файлы:

- новый `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/request/BulkTaskActionRequest.java`
- новый `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/request/BulkChangeGroupRequest.java` или task-specific DTO
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/FamilyResource.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyActionService.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyActionServiceImpl.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TaskRepository.java`
- при необходимости `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/FamilyDataRepository.java`

Как менять:

- добавить endpoint вроде `POST /api/tasks/bulk`;
- request должен содержать `childId`, `taskIds[]`, `action`, `groupName?`;
- операции:
  - `delete`
  - `block`
  - `unblock`
  - `change_group`
- backend обязан проверить принадлежность всех `taskIds` к `familyId` и нужному `childId`;
- результатом должен быть свежий `FamilyDataResponse`.

Где могут быть проблемы:

- сейчас persistence-path заточен под полную пересборку списка через `/api/data`;
- может не хватить точечных repository-операций для bulk update/delete;
- легко допустить cross-child update, если валидировать только `familyId`.

Архитектурно решать так:

- bulk делать транзакционно в service-слое;
- все repository-методы должны фильтровать и по `family_id`, и по `child_id`;
- не прокидывать UI-структуры напрямую в repository, только явные DTO.

### PB-06. Реализовать backend bulk API для наград

Приоритет: `P0`

Основные файлы:

- новый `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/request/BulkShopItemActionRequest.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/FamilyResource.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyActionService.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyActionServiceImpl.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/ShopItemRepository.java`
- при необходимости `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/FamilyDataRepository.java`

Как менять:

- симметричный endpoint вроде `POST /api/shop/bulk`;
- контракт и ответ должны совпадать по стилю с `tasks bulk`;
- поддержать `delete`, `block`, `unblock`, `change_group`.

Где могут быть проблемы:

- расхождение контракта `tasks` и `shop`;
- отличия между `name/title`, `price/coins`, `itemType/type` в web/store и server payload.

Архитектурно решать так:

- bulk API не должен зависеть от несовпадающих UI-полей;
- использовать только server-source-of-truth поля сущности.

### PB-07. Связать bulk UI с backend и серверным refresh

Приоритет: `P0`

Основные файлы:

- `apps/web/src/lib/services/api.ts`
- `apps/web/src/lib/services/bootstrap.ts`
- `apps/web/src/lib/components/app/sections/TasksSection.svelte`
- `apps/web/src/lib/components/app/sections/ShopSection.svelte`
- `apps/web/src/lib/stores/toasts.ts`

Как менять:

- добавить typed API-методы:
  - `bulkUpdateTasks(...)`
  - `bulkUpdateShopItems(...)`
- после успеха применять `applyDataSnapshot(...)` из server response;
- на bulk-delete использовать confirm modal;
- на ошибках показывать точный toast/message из server problem details.

Где могут быть проблемы:

- если одновременно оставить local optimistic mutation и server snapshot apply, можно получить двойное изменение;
- `flushPendingSave()` может конфликтовать с bulk endpoint, если в очереди уже висит локальный snapshot.

Архитектурно решать так:

- перед bulk operation принудительно `flushPendingSave()`;
- сам bulk не должен сначала мутировать `appStore`, а потом синхронизироваться; источник истины после операции должен быть серверный snapshot.

### PB-08. Добавить CSV import modal и парсер из clipboard

Приоритет: `P1`

Основные файлы:

- новый `apps/web/src/lib/components/app/modals/CsvImportModal.svelte`
- `apps/web/src/lib/stores/modal.ts`
- `apps/web/src/lib/components/app/sections/TasksSection.svelte`
- `apps/web/src/lib/components/app/sections/ShopSection.svelte`
- новый `apps/web/src/lib/services/csvImport.ts`
- `apps/web/src/lib/i18n/messages/ru/tasks.ts`
- `apps/web/src/lib/i18n/messages/en/tasks.ts`
- `apps/web/src/lib/i18n/messages/ru/shop.ts`
- `apps/web/src/lib/i18n/messages/en/shop.ts`

Как менять:

- добавить кнопку быстрого импорта для родителя;
- modal должен иметь:
  - textarea для вставки CSV;
  - кнопку `Вставить из буфера`;
  - переключатель типа импорта: `Задания` / `Награды`;
  - блок "обязательные / необязательные колонки";
  - preview первых строк;
  - список ошибок по строкам.

Минимальная схема CSV для задач:

- обязательные: `title`, `coins`
- необязательные: `groupName`, `comment`, `frequencyLimit`, `frequencyPeriod`, `moneyLimit`, `isActive`

Минимальная схема CSV для наград:

- обязательные: `name`, `price`
- необязательные: `groupName`, `comment`, `moneyLimit`, `type`, `isActive`

Где могут быть проблемы:

- CSV из Excel/Google Sheets может приходить с `;` вместо `,`;
- поля с запятыми и переводами строк сломают наивный split;
- `navigator.clipboard.readText()` может не сработать без user gesture/permissions.

Архитектурно решать так:

- вынести parsing/normalization в отдельный service `csvImport.ts`;
- поддержать хотя бы `,` и `;` детект;
- не делать parsing прямо в Svelte-компоненте.

### PB-09. Реализовать backend import API для заданий и наград

Приоритет: `P1`

Основные файлы:

- новый `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/request/ImportTasksRequest.java`
- новый `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/request/ImportShopItemsRequest.java`
- новый `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/ImportValidationErrorResponse.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/FamilyResource.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyActionService.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyActionServiceImpl.java`

Как менять:

- добавить endpoints вида:
  - `POST /api/tasks/import`
  - `POST /api/shop/import`
- принимать уже распарсенные и нормализованные строки, а не сырой CSV text;
- валидировать обязательные поля и бизнес-ограничения;
- возвращать:
  - либо `FamilyDataResponse` при success;
  - либо structured validation errors с номерами строк.

Где могут быть проблемы:

- если backend будет молча пропускать плохие строки, пользователь не поймёт что реально импортировалось;
- если import будет работать через старый snapshot путь, возрастает риск потери параллельных изменений.

Архитектурно решать так:

- import должен быть отдельной транзакционной командой;
- response contract должен поддерживать полную отмену при ошибке первой версии, без partial success.

### PB-10. Показ схемы CSV, ошибок валидации и preview импортируемых строк

Приоритет: `P1`

Основные файлы:

- новый `apps/web/src/lib/components/app/modals/CsvImportModal.svelte`
- новый `apps/web/src/lib/services/csvImport.ts`
- `apps/web/src/lib/services/api.ts`

Как менять:

- до отправки на сервер показать preview таблицы;
- подсветить missing required columns;
- серверные ошибки выводить списком по строкам;
- запретить submit, если локальная схема CSV невалидна.

Где могут быть проблемы:

- разные названия колонок (`title` vs `name`) для задач и наград;
- пустые строки и пробелы часто дадут "ложные" ошибки.

Архитектурно решать так:

- держать конфигурацию колонок как декларативную схему, а не разбросанные `if`.

### PB-11. Unit/E2E/backend coverage и стабилизация

Приоритет: `P0`

Основные файлы:

- `apps/web/tests/unit/api.test.ts`
- новый `apps/web/tests/unit/csvImport.test.ts`
- новый `apps/web/tests/unit/confirmModal.test.ts` или тест на store/flow
- новый `apps/web/tests/e2e/parent-bulk-actions.spec.ts`
- расширение `apps/web/tests/e2e/app-sections.spec.ts`
- `apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/FamilyResourceTest.java`
- `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/FamilyActionServiceImplTest.java`
- при необходимости `apps/backend/src/test/java/com/sashplatonov/earnit/kids/repository/RepositorySmokeTest.java`

Что покрыть тестами:

- custom confirm открывается и подтверждает удаление без нативного `confirm`;
- bulk-selection не протекает между детьми и между секциями;
- bulk delete/block/unblock/change group работает для `tasks` и `shop`;
- CSV parser корректно понимает `,` и `;`;
- import валидирует обязательные колонки;
- import ошибки показываются по строкам;
- backend отклоняет чужие `taskIds/itemIds`, неверный `childId`, пустой selection, пустой `groupName` для `change_group`;
- backend возвращает свежий snapshot после операций.

Definition of done:

- все команды из блока quality gates выполнены успешно;
- новый функционал покрыт тестами не менее чем на `80%`;
- в UI нет нативного `confirm(...)` для удаления задания;
- bulk-операции работают только у родителя;
- CSV import показывает пользователю обязательные и необязательные колонки до применения.

## Порядок реализации

1. PB-01 и PB-02: сначала контракт и общий confirm modal.
2. PB-03..PB-07: потом bulk API и UI для заданий и наград.
3. PB-08..PB-10: затем CSV import поверх уже существующего command API.
4. PB-11: в конце обязательная стабилизация, покрытие, build/test/e2e.

## Что не делать в первой итерации

- не делать частичный import с "успешно 7 из 10";
- не смешивать bulk/import с legacy-логикой одиночного локального редактирования сильнее, чем это нужно;
- не хранить selection прямо внутри сущностей `tasks` / `shopItems`;
- не расширять `POST /api/data` ещё одним неявным режимом для bulk/import.
