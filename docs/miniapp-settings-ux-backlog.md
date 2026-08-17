# Мини-апп: переключатель уведомлений, быстрые действия, блокировка лимитов, вёрстка «Мои заявки» — бэклог реализации

## Цель

Привести мини-апп (Telegram Mini App) в соответствие с ожиданиями пользователя:

1. В настройках уведомлений заменить сложный переключатель на обычный «рычажок» нормальной высоты и ширины (старый вид удалить).
2. Убрать избыточность в «Быстрых действиях» родителя: кнопка «История» и блок «Недавняя активность» с кнопкой «Показать все» дублируют друг друга.
3. Блокировать кнопку задания/награды на фронтенде, когда лимит частоты (день/неделя) уже исчерпан, вместо того чтобы молча не создавать заявку.
4. Привести вёрстку блока «Мои заявки» у ребёнка к виду блока заявок у родителя.

## Архитектурные решения

- **Источник истины по лимитам — бэкенд.** `FamilyActionFrequencyService` уже считает использованный лимит (`validateTaskRequestLimit` / `validateItemRequestLimit`) и возвращает коды `TASK_REQUEST_LIMIT_REACHED` / `ITEM_REQUEST_LIMIT_REACHED`. Для заданий прогресс уже отдаётся в `TaskDto.periodProgress` (`TaskPeriodProgressDto.available`). Для наград (`ShopItemDto`) аналога `periodProgress` нет — его нужно добавить, а не дублировать логику подсчёта на фронте.
- **Не дублировать подсчёт лимита на фронте.** Фронт должен опираться на уже вычисленный бэкендом флаг доступности (`available`), а не пересчитывать `completed + pending >= limit` самостоятельно.
- **Переключатель — единый паттерн.** В `TelegramLimits.svelte` уже есть простой «рычажок» (`.switch` 2.6rem × 1.5rem через `::after`). Переключатель уведомлений должен использовать тот же простой паттерн, а не собственный `switch-track`/`switch-thumb`.
- **Вёрстка заявок — единый компонент-карточка.** Родитель использует `TelegramRequestList.svelte` (карточки `article` с `.request-top`/`.entity-icon`/`.entity-text`/`.decision-actions`). Ребёнок использует `TelegramChildRequestList.svelte` + `TelegramRequestRow.svelte` (строки). Привести ребёнка к карточному виду родителя, сохранив статус-чип и кнопку «Отменить».
- **Копирайт отделён от идентификаторов.** Все новые строки — через i18n-ключи (`ru`/`en`), без хардкода текста в разметке.

## Рекомендуемый порядок реализации

| № | Задача | Приоритет | Зависит от | Причина |
| ---: | --- | --- | --- | --- |
| 1 | P1-1 Переключатель уведомлений | P1 | — | Изолированная правка вёрстки, не зависит от других задач |
| 2 | P1-2 Блокировка кнопки по лимиту (бэкенд-контракт) | P1 | — | Контракт `available` для наград нужен до фронта |
| 3 | P1-3 Блокировка кнопки по лимиту (фронт) | P1 | P1-2 | Опирается на новый контракт |
| 4 | P1-4 Быстрые действия родителя | P1 | — | Независимая UX-правка |
| 5 | P2-1 Вёрстка «Мои заявки» ребёнка | P2 | — | Независимая вёрстка |

---

## P1-1: Заменить переключатель уведомлений на обычный «рычажок»

**Статус:** ✅ Выполнено
**Приоритет:** P1
**Зависит от:** —

### Результат

В настройках уведомлений (`TelegramNotifications.svelte`) каждый переключатель выглядит как обычный компактный «рычажок» нормальной высоты и ширины (как в `TelegramLimits.svelte`), без растянутого трека и крупного бегунка. Старый вид (`.switch-track` / `.switch-thumb`) удалён.

### Архитектурное решение

Использовать тот же простой паттерн переключателя, что уже есть в `TelegramLimits.svelte` (`.switch` с `::after`-бегунком, 2.6rem × 1.5rem). Не вводить третий вариант переключателя. Сохранить `role="switch"`, `aria-checked` и `aria-label` для доступности.

### Файлы

- Изменить `apps/web/src/lib/components/telegram/TelegramNotifications.svelte`.

### Работа

1. Убрать из разметки вложенные `<span class="switch-track">` и `<span class="switch-thumb">` во всех четырёх местах (родительские задачи, родительская семья, решения ребёнка, напоминания ребёнка).
2. Оставить `<button class="switch" class:on={...} role="switch" aria-checked={...} aria-label={...}>` без дочерних элементов.
3. Заменить CSS `.switch`/`.switch-track`/`.switch-thumb` на простой вариант: фиксированные `width`/`height` (например 2.6rem × 1.5rem), `border-radius:999px`, фон меняется по `.on`, бегунок через `::after` с `transition:left`.
4. Убедиться, что `on:click`-обработчики (`toggleParent` / `toggleChild`) не меняются.

### Критерии приёмки

- Переключатель имеет обычную высоту/ширину (не растянут), бегунок круглый и компактный.
- Состояние «вкл/выкл» визуально различимо (цвет фона и позиция бегунка).
- `role="switch"` и `aria-checked` сохраняются; переключение по клику и с клавиатуры работает.
- На мобильной ширине (320px) переключатель не выходит за границы строки.

### Проверка

```bash
cd apps/web && npm run lint && npm run test && npm run build
```

### Коммит

```bash
git add apps/web/src/lib/components/telegram/TelegramNotifications.svelte
git commit -m "fix(web): replace notification switch with simple lever toggle"
```

---

## P1-2: Добавить контракт доступности лимита для наград (бэкенд)

**Статус:** ⬜ Не начато
**Приоритет:** P1
**Зависит от:** —

### Результат

`ShopItemDto` отдаёт флаг доступности по лимиту частоты (аналог `TaskPeriodProgressDto.available`), чтобы фронт мог заблокировать кнопку награды, когда лимит исчерпан.

### Архитектурное решение

Расширить существующий контракт, а не создавать новый эндпоинт. Для заданий уже есть `TaskDto.periodProgress` с полем `available`. Для наград добавить минимальный аналог (например `periodProgress` с `available`/`limit`/`remaining`/`resetAt`, либо отдельное поле `limitReached`). Подсчёт переиспользовать из `FamilyActionFrequencyService.validateItemRequestLimit` / `FrequencyWindowService.resolveCurrentWindow`, чтобы не дублировать логику окна.

### Файлы

- Изменить `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/ShopItemDto.java`.
- Изменить `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/dashboard/FamilyDashboardCatalogLoader.java` (заполнение нового поля для `loadShopItems`).
- Изменить `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/dashboard/FamilyDashboardMapper.java` (маппинг нового поля).
- Изменить `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/system/SuperAdminService.java` (конструктор `ShopItemDto`, если используется).

### Работа

1. Добавить в `ShopItemDto` поле доступности по лимиту (рекомендуется переиспользовать `TaskPeriodProgressDto` или ввести `ShopItemPeriodProgressDto` с теми же полями).
2. В `FamilyDashboardCatalogLoader.loadShopItems` вычислить для каждого активного предмета текущее окно через `frequencyWindowService.resolveCurrentWindow` и заполнить `available = used < limit && item.isActive()`, используя `countPendingItemRequestsInWindow` + `countShopPurchasesInWindow` (как в `validateItemRequestLimit`).
3. Обновить маппер и все конструкторы `ShopItemDto`, чтобы не сломать компиляцию и тесты.
4. Сохранить обратную совместимость: при отсутствии лимита (`window.isEmpty()`) поле должно быть `null`/отсутствовать (как у заданий).

### Критерии приёмки

- `ShopItemDto` содержит поле доступности, сериализуемое как `null` при отсутствии лимита.
- Для предмета с исчерпанным лимитом `available === false`; с неисчерпанным — `true`.
- Существующие тесты (`FamilyDashboardQueryServiceImplTest`, `TelegramMenuBuilderTest`, `TelegramEmojiCoverageTest`) проходят без изменений сигнатур, где это возможно.

### Проверка

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

### Коммит

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/ShopItemDto.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/dashboard/FamilyDashboardCatalogLoader.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/family/dashboard/FamilyDashboardMapper.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/system/SuperAdminService.java
git commit -m "feat(backend): expose reward frequency-limit availability in ShopItemDto"
```

---

## P1-3: Блокировать кнопку задания/награды при исчерпанном лимите (фронт)

**Статус:** ⬜ Не начато
**Приоритет:** P1
**Зависит от:** P1-2

### Результат

Кнопка запроса задания/награды у ребёнка становится неактивной (`disabled`), когда лимит частоты уже исчерпан, вместо того чтобы заявка молча не создавалась.

### Архитектурное решение

Опираться на флаг доступности из бэкенда: для заданий — `task.periodProgress.available`, для наград — новое поле из P1-2. Не пересчитывать лимит на фронте. Кнопка блокируется по `disabled`, с сохранением `aria-label` и визуального состояния.

### Файлы

- Изменить `apps/web/src/lib/components/telegram/TelegramChildTasks.svelte`.
- Изменить `apps/web/src/lib/components/telegram/TelegramChildRewards.svelte`.
- Изменить `apps/web/src/lib/stores/app.ts` (тип `ShopItem` — добавить поле доступности, если нужно).
- Изменить `apps/web/src/lib/services/serverContract.ts` (нормализация нового поля награды).
- Изменить `apps/web/src/lib/i18n/messages/ru/app.ts` и `apps/web/src/lib/i18n/messages/en/app.ts` (подпись/aria для заблокированного состояния, при необходимости).

### Работа

1. В `serverContract.ts` нормализовать новое поле доступности награды в `ShopItem` (по аналогии с `normalizeTaskPeriodProgress`).
2. В `TelegramChildTasks.svelte` добавить условие `disabled={isPending(task.id) || task.periodProgress?.available === false}` для кнопки `.check`.
3. В `TelegramChildRewards.svelte` добавить условие `disabled={isPending(item.id) || !isAffordable(item) || item.<поле доступности> === false}`.
4. Добавить i18n-подпись (например «Лимит исчерпан») для `aria-label`/подсказки заблокированной кнопки, если это улучшает понятность.

### Критерии приёмки

- При исчерпанном лимите кнопка задания/награды неактивна и не открывает лист запроса.
- При неисчерпанном лимите кнопка активна, поведение не меняется.
- Состояние `pending` (уже есть заявка) по-прежнему блокирует кнопку.
- На мобильной ширине заблокированная кнопка визуально отличима (opacity/cursor).

### Проверка

```bash
cd apps/web && npm run lint && npm run test && npm run build
```

### Коммит

```bash
git add apps/web/src/lib/components/telegram/TelegramChildTasks.svelte apps/web/src/lib/components/telegram/TelegramChildRewards.svelte apps/web/src/lib/stores/app.ts apps/web/src/lib/services/serverContract.ts apps/web/src/lib/i18n/messages/ru/app.ts apps/web/src/lib/i18n/messages/en/app.ts
git commit -m "feat(web): disable task/reward request button when frequency limit reached"
```

---

## P1-4: Убрать избыточность «История» / «Недавняя активность» у родителя

**Статус:** ⬜ Не начато
**Приоритет:** P1
**Зависит от:** —

### Результат

В «Быстрых действиях» родителя нет дублирующей кнопки «История», а блок «Недавняя активность» показывается один раз без лишней кнопки «Показать все», которая ничего не меняет.

### Архитектурное решение

Блок активности (`history`) уже всегда отображается на главной родителя. Кнопка «История» в быстрых действиях лишь переключает заголовок и кнопку «Показать все/Показать недавние», не меняя видимый список. Убрать кнопку «История» из быстрых действий и кнопку «Показать все» из заголовка блока, оставив один понятный блок «Недавняя активность» с пагинацией «Показать ещё» (если есть). Сохранить deep-link `?context=history` (он должен по-прежнему открывать полную историю).

### Файлы

- Изменить `apps/web/src/lib/components/telegram/TelegramParentHome.svelte`.
- Изменить `apps/web/src/lib/i18n/messages/ru/app.ts` и `apps/web/src/lib/i18n/messages/en/app.ts` (удалить/переиспользовать ключи `home.history`, `home.viewAll`, `home.showRecent`, если они больше не нужны).

### Работа

1. Убрать кнопку «История» из блока `.quick-actions` (оставить «Начислить»).
2. Убрать кнопку «Показать все»/«Показать недавние» из `.section-heading` блока активности; заголовок сделать статичным «Недавняя активность».
3. Сохранить логику `loadHistory` и кнопку «Показать ещё» (`load-more`) для пагинации.
4. Убедиться, что `initialContext === 'history'` (deep-link) по-прежнему загружает полную историю без ошибок.
5. Удалить неиспользуемые i18n-ключи или оставить их, если на них есть ссылки в других местах (проверить grep).

### Критерии приёмки

- В быстрых действиях осталась только кнопка «Начислить» (или иной осмысленный набор без дублирующей «Истории»).
- Блок «Недавняя активность» показывается один раз, без кнопки «Показать все».
- Пагинация «Показать ещё» работает при наличии большего числа записей.
- Deep-link `?context=history` открывает полную историю.

### Проверка

```bash
cd apps/web && npm run lint && npm run test && npm run build
```

### Коммит

```bash
git add apps/web/src/lib/components/telegram/TelegramParentHome.svelte apps/web/src/lib/i18n/messages/ru/app.ts apps/web/src/lib/i18n/messages/en/app.ts
git commit -m "refactor(web): remove redundant history quick action and view-all button"
```

---

## P2-1: Привести вёрстку «Мои заявки» ребёнка к виду родителя

**Статус:** ⬜ Не начато
**Приоритет:** P2
**Зависит от:** —

### Результат

Блок «Мои заявки» у ребёнка использует ту же карточную вёрстку, что и блок заявок у родителя (`TelegramRequestList.svelte`): карточки с иконкой, заголовком, мета-строкой и суммой, с сохранением статус-чипа и кнопки «Отменить».

### Архитектурное решение

Переиспользовать карточный паттерн родителя, а не изобретать новый. Сохранить специфику ребёнка: статус-чип (`pending/approved/rejected/cancelled`), кнопку «Отменить» для pending-заявок, пагинацию «Показать ещё». Не менять логику сортировки и отмены.

### Файлы

- Изменить `apps/web/src/lib/components/telegram/TelegramChildRequestList.svelte`.
- Изменить `apps/web/src/lib/components/telegram/TelegramRequestRow.svelte` (или заменить его использование карточным шаблоном).

### Работа

1. Привести разметку строки заявки ребёнка к карточному виду родителя: `article` с `.request-top` (иконка + `.entity-text` с `h3`, `.meta`, `.amount`).
2. Разместить статус-чип и время в правой части карточки (как в `TelegramRequestRow.row-side`), сохранив их отображение.
3. Сохранить кнопку «Отменить» для pending-заявок и её `disabled`-состояние.
4. Сохранить пагинацию «Показать ещё» и пустое/ошибочное состояния.

### Критерии приёмки

- Визуально блок «Мои заявки» ребёнка совпадает по структуре с блоком заявок родителя (карточки, иконка, заголовок, мета, сумма).
- Статус-чип и кнопка «Отменить» отображаются корректно для всех статусов.
- На мобильной ширине (320px) карточки не выходят за границы, кнопка «Отменить» достижима.
- Пустое состояние и состояние ошибки сохраняются.

### Проверка

```bash
cd apps/web && npm run lint && npm run test && npm run build
```

### Коммит

```bash
git add apps/web/src/lib/components/telegram/TelegramChildRequestList.svelte apps/web/src/lib/components/telegram/TelegramRequestRow.svelte
git commit -m "refactor(web): align child request list layout with parent card style"
```
