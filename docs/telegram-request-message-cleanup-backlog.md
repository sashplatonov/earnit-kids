# Обновление статуса Telegram-сообщений с заявками - Final Minimal Backlog v4

## Goal

Когда заявка ребёнка перестаёт требовать решения родителя, исходное сообщение Telegram-бота **не удаляется**.

Вместо удаления:

1. у сообщения исчезают кнопки `Одобрить` / `Отклонить`;
2. сообщение остаётся в истории чата;
3. в сообщении показывается финальный статус заявки.

Финальные статусы:

- `approved` -> `✅ Одобрено`
- `rejected` -> `❌ Отклонено`
- `cancelled` -> `↩️ Отменено`
- физически удалённая заявка -> `🗑️ Удалено`

Это должно работать независимо от того, где заявка была обработана:

- Telegram Mini App;
- web;
- Telegram bot;
- отмена ребёнком;
- удаление родителем.

## Главный invariant

> Если заявка больше не требует решения, в Telegram-боте не должно оставаться сообщения с рабочими кнопками `Одобрить` / `Отклонить`.

---

# Терминология

В этом backlog НЕ используется слово `terminal` в смысле командной строки.

Ранее под `terminal state` имелось в виду:

> финальный статус заявки, после которого она больше не должна быть actionable.

В этом документе используется только:

```text
финальный статус заявки
```

или конкретные статусы:

```text
approved
rejected
cancelled
deleted
```

---

# Архитектурные решения

## 1. Source of truth

Источник истины:

`PurchaseRequestEntity.status`

Для физически удалённой заявки статус `deleted` должен быть записан в событие **до удаления entity**, потому что позже прочитать его уже нельзя.

---

## 2. Единый application outbox event

Добавить или сохранить:

```text
ApplicationOutboxEventType.REQUEST_RESOLVED
```

Event содержит минимум:

```text
requestId
resolutionStatus
```

где:

```text
approved
rejected
cancelled
deleted
```

Событие публикуется при:

- approve;
- reject;
- child cancel;
- parent delete, только если этот delete по текущей бизнес-логике действительно должен изменить Telegram representation.

Existing business events:

- `TASK_APPROVED`
- `REWARD_APPROVED`
- `TASK_REJECTED`
- `REWARD_REJECTED`

не заменяются.

### Schema gap (проверено)

`ApplicationOutboxEventEntity` сейчас содержит `requestId`, но **не содержит** `resolutionStatus` (проверено по `domain/model/ApplicationOutboxEventEntity.java`). Для `REQUEST_RESOLVED` требуется:

- новый nullable-столбец `resolution_status` в `application_outbox_events` (migration `V36__...` в `apps/backend/src/main/resources/db/migration/` + зеркально в `apps/backend/src/test/resources/db/migration/`);
- поле `resolutionStatus` в `ApplicationOutboxEventEntity`;
- для `deleted` — также минимальный контекст (title/coins) для восстановления текста, т.к. `PurchaseRequestEntity` уже удалён (см. раздел 7). Это можно хранить в том же событии (например, переиспользовать `coinDelta` + добавить `resolutionTitle`), либо принять, что для `deleted` текст восстанавливается из `resolutionStatus` + `requestId` без title.

Этот schema change — часть P0-1 (публикация события), а не отдельная задача.

---

## 3. Реальный entry point существующего outbox pipeline (проверено)

Проверено по коду — это **Вариант B**. Конкретный pipeline:

```text
FamilyActionRequestService.publish(...)
    ↓
FamilyActionEventSupport.publish(...)
    ↓
ApplicationEventPublisher.publish(...)   // создаёт ApplicationOutboxEventEntity
    ↓
TelegramDeliveryPlanner.planDueEvents()  // создаёт TelegramDeliveryEntity на каждого recipient
    ↓
TelegramOutboxProcessor.process()       // api.sendMessage(...) / api.editMessageText(...)
```

Ключевые факты:

- `TelegramOutboxProcessor` работает **только** через `TelegramDeliveryEntity` (`deliveries.findDue(...)` → `api.sendMessage(...)`). Напрямую `ApplicationOutboxEventEntity` он не обрабатывает.
- `TelegramDeliveryPlanner.recipients(...)` для parent-событий (`TASK_REQUEST_CREATED`, `REWARD_REQUEST_CREATED`) возвращает `identities.findActiveParents(familyId)`; для остальных — активного ребёнка.
- `TelegramDeliveryEntity` уже хранит `chat_id` и `message_id` отправленного сообщения — этого достаточно, чтобы найти и отредактировать исходное сообщение без новой схемы.

Следствие для `REQUEST_RESOLVED`:

- `TelegramOutboxProcessor.process(...)` итерирует только по `deliveries.findDue(...)`, поэтому для обработки `REQUEST_RESOLVED` нужна delivery. Но recipients должны определяться **по уже отправленным deliveries исходной заявки**, а не по `findActiveParents` (см. раздел 4).
- Конкретно: `TelegramDeliveryPlanner` для `REQUEST_RESOLVED` должен создать delivery на каждый `chat_id` из исходных request-created deliveries (`TASK_REQUEST_CREATED`/`REWARD_REQUEST_CREATED` со статусом `SENT`), а не на текущих активных родителей. Тогда `TelegramOutboxProcessor` в ветке `REQUEST_RESOLVED` находит исходный `message_id` (по `requestId`) и вызывает `editMessageText`.
- `REQUEST_RESOLVED` **не** должен попадать в ветку `isParentEvent(...)` планировщика (она строит recipients по текущему membership).

## Acceptance rule

Implementation должна явно использовать **существующую точку входа outbox pipeline** — конкретно `TelegramOutboxProcessor.process(...)` (Вариант B). Фраза `existing outbox processing` без определения конкретного класса/метода не считается выполнением задачи.

---

## 4. `REQUEST_RESOLVED` не является новым Telegram notification

Событие означает только:

```text
найти ранее отправленные сообщения этой заявки
+
обновить их
```

Новое Telegram message пользователю не отправляется.

Фактические recipients определяются по уже отправленным Telegram deliveries исходной заявки, а не по текущему membership семьи.

---

## 5. Не вводить новую state machine для Telegram UI

Не добавлять:

```text
renderedRequestStatus
desiredRequestStatus
TelegramDeliveryStatus.UPDATED
TelegramDeliveryStatus.DELETED
```

Повторное редактирование должно быть безопасным за счёт идемпотентной операции Telegram edit.

---

## 6. `TelegramDelivery.requestId` - только после проверки retention (проверено)

Существующая связь:

```text
TelegramDelivery.eventId
        ↓
ApplicationOutboxEventEntity.requestId
```

### Результат проверки retention (decision gate закрыт)

Проверено по коду:

- `TelegramRetentionService.cleanup(...)` вычищает `ApplicationOutboxEventEntity` через `ApplicationOutboxEventRepository.deleteEligible(cutoff, batchSize)`.
- `ApplicationOutboxEventRepository.deleteEligible(...)` удаляет события, у которых `planningCompletedAt < cutoff` **и** `id not in (select d.eventId from TelegramDeliveryEntity d where d.status = 'PENDING')`.
- `TelegramDeliveryRepository.deleteEligible(...)` удаляет доставки со статусом `SENT`/`SKIPPED`/`SKIPPED_DISABLED`/`FAILED` и `terminalAt < cutoff`.
- Retention-периоды по умолчанию: `outboxRetentionDays = 30`, `deliveryRetentionDays = 30` (`TelegramConfig`).

Вывод: **event может быть удалён retention-ом раньше, чем заявка будет решена**, если заявка остаётся `pending` дольше 30 дней. В этом случае связь `eventId → requestId` теряется, и исходное сообщение нельзя будет найти по `requestId`.

### Решение

Добавить минимальный nullable-столбец:

```text
TelegramDelivery.requestId
```

заполняемый для request-created deliveries (`TASK_REQUEST_CREATED`, `REWARD_REQUEST_CREATED`) в момент планирования. Это делает поиск исходного сообщения независимым от retention `ApplicationOutboxEventEntity`.

Legacy rows остаются nullable (для них fallback — связь через `eventId`, пока event ещё жив).

## Acceptance rule

PR **должен** содержать migration `TelegramDelivery.requestId` (nullable), потому что retention-проблема подтверждена существующим кодом (`outboxRetentionDays = 30` < возможный срок жизни pending-заявки).

---

## 7. Источник исторического текста (проверено)

Для `editMessageText` нужен текст сообщения, который был отправлен изначально.

Проверено по коду:

- Исходный текст родительского уведомления формируется в `TelegramNotificationComposer.requestText(...)` → `TelegramCopy.requestNotification(childName, title, coins, task)`.
- `title` берётся из `PurchaseRequestEntity`: `getTaskName()` (для task) или `ShopItemEntity.getName()` через `getItemId()` (для reward). `coins` — из `PurchaseRequestEntity.getCoins()`.
- `PurchaseRequestEntity` **сохраняет** `taskName`, `itemId`, `coins` на момент создания заявки (см. `FamilyActionRequestService.buildTaskRequest`/`buildPurchaseRequest`). Это и есть persisted snapshot: title/value не меняются при последующем изменении Task/Reward.

Вывод: **данных достаточно**. Исторический текст восстанавливается из `PurchaseRequestEntity` (persisted snapshot) + существующий formatter `TelegramCopy.requestNotification(...)`. Новая snapshot subsystem не нужна.

Важно: для `deleted` (физически удалённая заявка) `PurchaseRequestEntity` уже недоступен, поэтому `resolutionStatus` и минимальный контекст (title/coins) должны быть записаны в `REQUEST_RESOLVED` **до** удаления entity (см. раздел 1).

---

## 8. Status + удаление кнопок должны выполняться одной Telegram mutation

Предпочтительный путь:

```text
editMessageText(
    newText,
    noInlineKeyboard
)
```

Один Telegram API вызов должен одновременно:

- показать новый статус;
- убрать `Одобрить / Отклонить`.

Нельзя по умолчанию делать:

```text
edit text
↓
отдельно remove keyboard
```

потому что partial failure может оставить противоречивый UI.

Fallback из двух операций разрешён только если существующий Telegram client/API реально не позволяет сделать это одной mutation.

---

## 9. Использовать существующий formatter

Не копировать Telegram copy в processor.

Переиспользовать существующий request formatter.

Минимально добавить поддержку финального статуса:

```text
pending
approved
rejected
cancelled
deleted
```

Для:

```text
approved
rejected
cancelled
deleted
```

action buttons отсутствуют.

### Emoji gap (проверено)

`TelegramBotEmoji` уже содержит `SUCCESS = "✅"` и `DECLINE = "❌"`, но **не содержит** эмодзи для `cancelled` (`↩️`) и `deleted` (`🗑️`). Их нужно добавить в `TelegramBotEmoji` (например `CANCEL = "↩️"`, `DELETE = "🗑️"`), чтобы соблюсти правило «эмодзи только из `TelegramBotEmoji`» и не сломать `TelegramEmojiCoverageTest`. Это часть P0-3.

---

# Recommended implementation order

| Order | Task | Priority | Depends on |
|---:|---|---|---|
| 1 | P0-1 | P0 | - |
| 2 | P0-2 | P0 | P0-1 |
| 3 | P0-3 | P0 | P0-1, P0-2 |
| 4 | P0-4 | P0 | P0-3 |

---

# P0-1: Публиковать `REQUEST_RESOLVED`

**Status:** ✅ Completed  
**Priority:** P0

## Outcome

Любой реальный переход заявки в финальный статус создаёт один `REQUEST_RESOLVED`.

## Files

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/ApplicationOutboxEventType.java` (добавить `REQUEST_RESOLVED`).
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/ApplicationOutboxEventEntity.java` (добавить `resolutionStatus`, при необходимости `resolutionTitle`).
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/event/ApplicationEventPublisher.java` (расширить `publish(...)` для `resolutionStatus`).
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/action/FamilyActionEventSupport.java` (пробросить `resolutionStatus`).
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/action/FamilyActionRequestService.java` (публиковать `REQUEST_RESOLVED` в `approveRequest`/`rejectRequest`/`deleteRequest`).
- Create `apps/backend/src/main/resources/db/migration/V36__add_request_resolution_status.sql`.
- Create `apps/backend/src/test/resources/db/migration/V36__add_request_resolution_status.sql` (зеркально для H2).

## Work

### approve

После:

```text
pending -> approved
```

publish:

```text
REQUEST_RESOLVED
requestId
resolutionStatus = approved
```

### reject

После:

```text
pending -> rejected
```

publish:

```text
resolutionStatus = rejected
```

### child cancel

После:

```text
pending -> cancelled
```

publish:

```text
resolutionStatus = cancelled
```

### parent delete

Перед physical delete сохранить:

```text
requestId
```

и publish:

```text
resolutionStatus = deleted
```

только если этот delete по текущей business logic должен изменить уже существующее Telegram message.

Не расширять существующую delete semantics.

### Не публиковать

- `alreadyProcessed`;
- повторный approve/reject уже решённой заявки;
- path без реального state transition;
- delete-path, который по текущей business logic не должен менять historical Telegram status.

## Concurrency verification

Проверить существующую защиту:

```text
approve vs reject
approve vs cancel
reject vs delete
```

Если уже есть:

- optimistic locking;
- pessimistic locking;
- version field;
- conditional state transition;

ничего не менять.

Если обнаружен отдельный reproducible concurrency bug, зафиксировать его отдельно и не раздувать эту feature-задачу большим locking refactor.

## Acceptance criteria

- На один реальный переход в финальный статус создаётся один `REQUEST_RESOLVED`.
- Event содержит корректный `requestId` и `resolutionStatus`.
- `deleted` не требует позднее читать удалённый entity.
- Rollback domain transaction откатывает и state change/delete, и outbox event.
- Existing approve/reject business events продолжают работать.

---

# P0-2: Найти все фактически отправленные Telegram messages заявки

**Status:** ✅ Completed  
**Priority:** P0  
**Depends on:** P0-1

## Outcome

Repository умеет найти все уже отправленные Telegram messages одной заявки.

Искать только исходные request-created events:

```text
TASK_REQUEST_CREATED
REWARD_REQUEST_CREATED
```

## Files

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/TelegramDeliveryRepository.java` (добавить query поиска исходных deliveries по `requestId`).
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/TelegramDeliveryEntity.java` (добавить nullable `requestId`, если принято решение из раздела 6).
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramDeliveryPlanner.java` (заполнять `requestId` для request-created deliveries).
- Create `apps/backend/src/main/resources/db/migration/V37__add_telegram_delivery_request_id.sql` (nullable `request_id`).
- Create `apps/backend/src/test/resources/db/migration/V37__add_telegram_delivery_request_id.sql` (зеркально для H2).

## Work

1. Relation подтверждён: `TelegramDelivery.eventId → ApplicationOutboxEventEntity.requestId` (см. раздел 6). Для независимости от retention добавить nullable `TelegramDelivery.requestId`.
2. Retention проверен (раздел 6): `outboxRetentionDays = 30` — миграция `request_id` нужна.
3. Добавить repository query: найти deliveries со статусом `SENT`, непустым `chatId`/`messageId`, чей `eventId` ссылается на событие `TASK_REQUEST_CREATED`/`REWARD_REQUEST_CREATED` с данным `requestId` (или напрямую по `requestId`, если столбец добавлен).
4. Query должен возвращать только:

```text
delivery.status == SENT
AND chatId IS NOT NULL
AND messageId IS NOT NULL
```

5. Не фильтровать по current active parent.
6. Не выбирать только первый message.
7. Duplicate historical deliveries возвращать все.
8. Если сообщений нет - это нормальный no-op.

## Historical content verification (проверено)

Источник исходного message content подтверждён: `PurchaseRequestEntity` (persisted snapshot: `taskName`/`itemId`/`coins`) + `TelegramCopy.requestNotification(...)`. См. раздел 7.

## Schema fallback (решено)

Retention-проблема подтверждена (раздел 6): добавить nullable `TelegramDelivery.requestId` для request-created deliveries. Это часть P0-2.

## Acceptance criteria

- Два recipients -> находятся оба messages.
- Inactive/unlinked recipient не исчезает.
- Duplicate delivery возвращается.
- PENDING/FAILED delivery без реального `messageId` не попадает в результат.
- Отсутствие messages -> no-op.
- Источник historical message content подтверждён существующим кодом.
- Migration `TelegramDelivery.requestId` (nullable) добавлена, т.к. retention-проблема подтверждена.

---

# P0-3: Одной операцией обновлять существующее сообщение и закрыть late-send race

**Status:** ✅ Completed  
**Priority:** P0  
**Depends on:** P0-1, P0-2

## Outcome

После перехода заявки в финальный статус все найденные Telegram messages:

- остаются в истории;
- получают правильный статус;
- теряют кнопки `Одобрить / Отклонить`.

Новое Telegram message не создаётся.

## Files

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramOutboxProcessor.java` (ветка `REQUEST_RESOLVED`: найти исходные deliveries → `editMessageText`).
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramNotificationComposer.java` (или новый formatter-хелпер) — добавить финальный статус-текст.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramCopy.java` (добавить `approved`/`rejected`/`cancelled`/`deleted` финальные статусы с эмодзи из `TelegramBotEmoji`).
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramBotEmoji.java` (добавить `CANCEL = "↩️"`, `DELETE = "🗑️"`).
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramBotApiClient.java` (при необходимости: классификация «message is not modified» / «message not found» как no-op).
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramParentRequestHandler.java` (убрать/свести к best-effort отдельный direct terminal edit — см. раздел G).

## Work

## A. Resolution edit

При:

```text
REQUEST_RESOLVED(requestId, resolutionStatus)
```

найти все исходные `SENT + messageId` messages.

Для каждого построить final representation:

```text
approved  -> ✅ Одобрено
rejected  -> ❌ Отклонено
cancelled -> ↩️ Отменено
deleted   -> 🗑️ Удалено
```

и выполнить одну Telegram mutation:

```text
editMessageText(
    historicalText + finalStatus,
    noInlineKeyboard
)
```

---

## B. Idempotency

Повторный `REQUEST_RESOLVED` безопасен.

Если Telegram отвечает:

```text
message is not modified
```

это success/no-op.

Не вводить дополнительное DB-state поле только ради определения, выполнялся ли edit раньше.

---

## C. Telegram error classification

### Retryable

```text
network
timeout
429
5xx
```

-> existing retry/backoff.

### Message already absent

Если сообщение уже удалено пользователем или Telegram сообщает, что его больше нет:

```text
success/no-op
```

Business invariant уже выполнен - actionable message больше не существует.

### Permanent inaccessible

Например chat/message больше недоступен и retry не изменит ситуацию:

```text
existing permanent terminal handling
```

Не retry forever.

Не считать неизвестный transient failure success.

---

## D. Multiple recipients / partial failure

Пример:

```text
Parent A edit -> success
Parent B edit -> transient failure
```

На retry допустимо повторить edit A:

```text
A -> message not modified -> success
B -> retry -> success
```

Не добавлять отдельный per-delivery convergence state.

Не создавать distributed transaction вокруг нескольких Telegram API calls.

---

## E. Pre-send guard

Перед первоначальным:

```text
sendMessage()
```

для:

```text
TASK_REQUEST_CREATED
REWARD_REQUEST_CREATED
```

прочитать актуальное состояние request.

Различать три результата.

### Request найден и `pending`

```text
send
```

### Request найден и уже имеет финальный статус

```text
skip actionable send
```

### Request действительно не найден

```text
skip actionable send
```

Но только если repository вернул нормальный `Optional.empty` / not-found result.

### DB/query failure

```text
fail current processing
-> existing retry
```

Нельзя трактовать exception/timeout как `request not found`.

---

## F. Post-send recheck

После:

```text
sendMessage()
persist messageId
```

повторно прочитать request.

Если он всё ещё `pending`:

```text
done
```

Если уже в финальном статусе:

```text
вызвать тот же final edit helper
```

Если request действительно удалён:

использовать уже доступный resolution context, если он есть; если его нет, не придумывать статус - authoritative `REQUEST_RESOLVED(deleted)` должен обработать message.

Если DB read падает transient error:

```text
не трактовать как deleted
```

использовать existing retry/error path.

---

## G. Telegram quick action

Чтобы не иметь две разные реализации final UI:

предпочтительный вариант:

```text
Telegram quick action
↓
FamilyActionRequestService
↓
REQUEST_RESOLVED
↓
единый final edit path
```

Удалить отдельный direct terminal edit из `TelegramParentRequestHandler`, если existing outbox latency приемлема.

Если direct edit технически нужен для мгновенного UX:

- он вызывает тот же helper/formatter;
- только best-effort;
- не содержит отдельной business logic;
- ошибка edit не откатывает successful domain operation;
- authoritative outbox path всё равно выполняется.

---

## H. Known residual risk

Сценарий:

```text
Telegram send succeeds
process crashes
before messageId is persisted
```

является существующим ограничением Telegram delivery pipeline.

Не решать его в рамках этой задачи:

- distributed transaction;
- exactly-once redesign;
- новая delivery subsystem.

Зафиксировать как out-of-scope residual risk.

## Acceptance criteria

- Approve -> `✅ Одобрено`, кнопок нет.
- Reject -> `❌ Отклонено`, кнопок нет.
- Cancel -> `↩️ Отменено`, кнопок нет.
- Delete -> `🗑️ Удалено`, кнопок нет.
- Status + keyboard изменяются одной Telegram mutation, если existing client/API это поддерживает.
- Message сохраняет тот же `chatId/messageId`.
- Resolution не отправляет новое message.
- Все фактические recipients обновляются.
- Inactive recipient обновляется.
- Duplicate historical messages обновляются.
- Duplicate `REQUEST_RESOLVED` безопасен.
- Partial failure безопасно retry-ится.
- `message absent` является no-op success.
- Permanent unrecoverable error не retry-ится forever.
- DB read failure не трактуется как request deletion.
- Request в финальном статусе до initial send не создаёт stale actionable message.
- Resolution между pre-check и send исправляется post-send recheck.

---

# P0-4: Минимальный regression и integration test set

**Status:** ✅ Completed  
**Priority:** P0  
**Depends on:** P0-3

## Files

- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramOutboxProcessorTest.java` (final-status rendering, idempotent retry, partial failure, message absent, transient error).
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramBotServiceImplTest.java` (cross-channel quick approve/reject, pre-send guard, post-send recheck).
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/integration/TelegramCrossChannelIntegrationTest.java` (сквозной сценарий: заявка → решение → `REQUEST_RESOLVED` → edit).
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramEmojiCoverageTest.java` (если добавляются новые кнопки/эмодзи — проверить правило «одна кнопка = один эмодзи»).

## Required tests

### 1. Final status rendering

Parameterized test:

| resolutionStatus | expected |
|---|---|
| approved | `✅ Одобрено` |
| rejected | `❌ Отклонено` |
| cancelled | `↩️ Отменено` |
| deleted | `🗑️ Удалено` |

Для каждого:

```text
buttons absent
same chatId/messageId
no new message
```

---

### 2. Cross-channel

Минимум:

```text
Mini App approve
Telegram bot quick approve/reject
```

Оба должны приводить к тому же `REQUEST_RESOLVED` processing path.

Web отдельно тестировать только если использует другой service path.

---

### 3. Multiple recipients

Два фактически отправленных messages одной заявки:

```text
A
B
```

оба редактируются.

---

### 4. Idempotent retry

Повторный `REQUEST_RESOLVED`:

```text
message is not modified
```

-> success/no-op.

---

### 5. Partial failure

```text
A success
B transient failure
retry
A no-op
B success
```

---

### 6. Resolution before initial send

```text
request already final
-> pending message not sent
```

---

### 7. Resolution between pre-check and send

```text
pre-check = pending
request becomes final
send succeeds
post-send recheck
-> buttons removed + final status
```

---

### 8. DB read failure

```text
request lookup throws transient DB error
```

-> processing retry/failure path.

Не трактовать как deleted/not-found.

---

### 9. Message absent

Telegram сообщает, что message уже отсутствует:

```text
-> success/no-op
```

---

### 10. Telegram transient error

```text
network / 429 / 5xx
```

-> existing retry/backoff.

---

## Optional verification only

Проверить existing concurrency protection для conflicting domain transitions.

Не добавлять новый locking/refactor, если отдельный bug не воспроизводится.

## Acceptance criteria

- Все ключевые сценарии проходят.
- Existing Telegram delivery tests не ломаются.
- `./mvnw verify` проходит без новых suppressions/exclusions.

---

# Definition of Done

- [ ] Telegram request messages не удаляются автоматически.
- [ ] После решения заявки message остаётся в истории.
- [ ] Кнопки `Одобрить / Отклонить` исчезают.
- [ ] Отображается корректный финальный статус.
- [ ] Status + удаление keyboard выполняются одной Telegram mutation, где возможно.
- [ ] `REQUEST_RESOLVED` использует конкретный existing outbox entry point.
- [ ] Resolution не создаёт новое пользовательское Telegram message.
- [ ] Обновляются все фактически отправленные copies.
- [ ] Current active/inactive parent membership не влияет на historical message update.
- [ ] Query работает только с `SENT + chatId + messageId`.
- [ ] Historical message source подтверждён существующим кодом.
- [ ] Повторный edit идемпотентен.
- [ ] Existing retry/backoff переиспользуется.
- [ ] Message already absent -> no-op success.
- [ ] Permanent error не retry-ится forever.
- [ ] DB lookup failure не трактуется как request deleted.
- [ ] Late-send race закрыт pre-send guard + post-send recheck.
- [ ] `renderedRequestStatus` не добавляется.
- [ ] Новая reconciliation subsystem не создаётся.
- [ ] `TelegramDelivery.requestId` (nullable) добавлен — retention-проблема подтверждена (`outboxRetentionDays = 30`).
- [ ] `resolutionStatus` (nullable) добавлен в `application_outbox_events` + `ApplicationOutboxEventEntity`.
- [ ] Exactly-once redesign остаётся вне scope.
- [ ] `./mvnw verify` проходит.

---

# Итоговая минимальная архитектура

```text
Telegram Mini App / Web / Telegram Bot
                │
                ▼
       FamilyActionRequestService
                │
       request получает финальный статус
                │
                ▼
        REQUEST_RESOLVED
   (requestId, resolutionStatus)
                │
                ▼
   ApplicationEventPublisher → ApplicationOutboxEventEntity
                │
                ▼
   TelegramOutboxProcessor.process()   (existing entry point, Вариант B)
                │
                ▼
 find all SENT Telegram messages request
   (TelegramDeliveryRepository по requestId)
                │
                ▼
        ONE editMessageText
       ├─ update status
       └─ remove buttons
```

Защита первоначальной отправки:

```text
before send
    ↓
request pending?
 ├─ no -> skip
 └─ yes
      ↓
     send
      ↓
 persist messageId
      ↓
    recheck
      ↓
request final now?
 ├─ no -> done
 └─ yes -> same final edit
```

---

# Что намеренно НЕ входит в задачу

- удаление Telegram messages;
- `renderedRequestStatus`;
- новая Telegram UI state machine;
- отдельный reconciliation framework/service;
- scheduler;
- distributed lock;
- exactly-once Telegram delivery redesign;
- generic historical snapshot subsystem;
- большой refactor domain concurrency;
- отдельная реализация final UI для Telegram bot quick actions.

> Примечание: «обязательная schema migration» из исходного списка заменена на **две подтверждённые** миграции — `application_outbox_events.resolution_status` (P0-1) и `telegram_deliveries.request_id` (P0-2), обе nullable. Они не «обязательные по умолчанию», а выведены из проверки кода (разделы 2 и 6).

---

# Неочевидные инсайты

## 1. Самый важный implementation check - existing outbox entry point

Проверено: `ApplicationOutboxEventEntity` обрабатывается через `TelegramDeliveryPlanner` → `TelegramOutboxProcessor.process(...)` (Вариант B). Параллельный механизм не нужен — добавляется ветка `REQUEST_RESOLVED` в существующий processor.

---

## 2. Один Telegram edit лучше двух

Если status и keyboard меняются одной mutation, исчезает целый класс partial UI states.

---

## 3. Historical text нельзя предполагать доступным

Telegram message нельзя рассматривать как datastore.

Проверено: исходный текст восстанавливается из `PurchaseRequestEntity` (persisted snapshot `taskName`/`itemId`/`coins`) + `TelegramCopy.requestNotification(...)`. Для `deleted` контекст должен быть записан в `REQUEST_RESOLVED` до удаления entity.

---

## 4. `request not found` и `request lookup failed` принципиально разные случаи

Первое может означать физическое удаление заявки.

Второе означает, что backend временно не знает состояние и должен retry.

---

## 5. Простая идемпотентность остаётся основным reliability mechanism

Для этой feature достаточно:

```text
REQUEST_RESOLVED можно безопасно выполнить повторно
```

Это проще и надёжнее, чем добавлять дополнительное DB-state только для Telegram representation.
