# EarnIt Kids - backlog v9 по конкретным текущим экранам Mini App

> Этот backlog описывает **не абстрактный целевой UX**, а конкретные изменения относительно текущих экранов, которые были показаны на скриншотах в чате.
>
> Для каждой задачи ниже есть:
> - **Текущий экран** - как он сейчас выглядит на присланном скриншоте;
> - **Что в нем не так**;
> - **Что именно изменить в текущем интерфейсе**;
> - **Reference** - прямой HTML-блок, который AI-агент должен повторить по структуре и визуальной иерархии.
>
> HTML reference: [`earnit_kids_reference_from_current_screens_v9.html`](./earnit_kids_reference_from_current_screens_v9.html)

---

# 1. Экран "Главная" родителя

## UI-001. Уменьшить карточку "Сейчас ничего не требует внимания"

**Текущий экран:** скрин главной, где сверху:
- Aliska + баланс;
- большая светлая карточка `Сейчас ничего не требует внимания`;
- ниже `Быстрые действия`.

**Проблема на текущем скрине**
- пустое состояние занимает слишком много вертикального места;
- из-за этого `Недавняя активность` начинается слишком низко;
- карточка визуально важнее, чем реальные действия.

**Изменить именно текущий экран**
- текущую большую карточку заменить на одну компактную status-row 48-52 px;
- сохранить смысл `Сейчас ничего не требует внимания`, но сократить визуальный объем;
- `Начислить` и `История` оставить в одной строке;
- поднять блок `Недавняя активность` выше;
- строки активности сделать плотнее, чтобы в первом viewport было видно минимум 4 записи.

**Reference:** [REF Home](./earnit_kids_reference_from_current_screens_v9.html#ref-home)

**Acceptance**
- в первом viewport видны status + 2 quick actions + 4 activity rows;
- нет пустой карточки высотой ~90+ px.

---

# 2. Экран "Задания"

## UI-002. Убрать перенос `Каталог заданий` в 2 строки ✅

**Текущий экран:** скрин `Задания`, где справа от `+ Добавить` ссылка `Каталог заданий` переносится на 2 строки.

**Проблема на текущем скрине**
- header разваливается на несколько строк;
- `+ Добавить` и `Каталог заданий` конкурируют;
- верх экрана съедает слишком много места.

**Изменить именно текущий экран**
- оставить заголовок `Задания`;
- `+ Добавить` заменить на компактное `＋`;
- `Каталог заданий` сократить до `Каталог`;
- все действия оставить в одной строке;
- не добавлять новый второй header.

**Reference:** [REF Tasks](./earnit_kids_reference_from_current_screens_v9.html#ref-tasks)

---

## UI-003. В текущем списке заданий вынести выполнение из `⋯` ✅

**Текущий экран:** скрин задания с открытым menu:
- `Выполнить за ребёнка`;
- `Изменить`;
- `В архив`;
- `Удалить`.

**Проблема на текущем скрине**
- `Выполнить за ребёнка` переносится на 2 строки;
- самое частое действие спрятано в management menu;
- completion смешан с edit/archive/delete.

**Изменить именно текущий экран**
- в каждой task-row добавить справа отдельный quick action `✓`;
- `✓` открывает confirmation выполнения;
- убрать `Выполнить за ребёнка` из `⋯`;
- в `⋯` оставить:
  - `Изменить`
  - `Архивировать`
  - divider
  - `Удалить`.

**Reference:** [REF Tasks](./earnit_kids_reference_from_current_screens_v9.html#ref-tasks), [REF Task Menu](./earnit_kids_reference_from_current_screens_v9.html#ref-task-menu), [REF Task Completion](./earnit_kids_reference_from_current_screens_v9.html#ref-task-completion)

---

## UI-004. Уплотнить текущие строки заданий ✅

**Текущий экран:** длинный список заданий, где одна строка занимает много высоты и metadata часто переносится.

**Изменить именно текущий экран**
- сохранить существующий визуальный стиль карточки/списка;
- icon tile оставить ~34-36 px;
- title максимум 2 строки;
- metadata собрать в одну строку;
- `⋯` и `✓` справа;
- уменьшить vertical paddings.

**Reference:** [REF Tasks](./earnit_kids_reference_from_current_screens_v9.html#ref-tasks)

---

# 3. Каталог заданий

## UI-005. Убрать лишний уровень `← Мои задания` ✅

**Текущий экран:** скрины каталога заданий, где сверху одновременно:
- `Задания`;
- `+ Добавить`;
- `Каталог заданий`;
- `← Мои задания`;
- снова `Каталог заданий`;
- пояснение;
- затем search/filter.

**Проблема на текущем скрине**
- два уровня заголовков;
- отдельная back-link row съедает высоту;
- каталог выглядит как вложенный сайт внутри Mini App.

**Изменить именно текущий экран**
- после ContextBar показать сразу один header `Каталог заданий`;
- убрать строку `← Мои задания`;
- убрать повторный верхний `Задания + Добавить + Каталог заданий` внутри catalog screen;
- оставить search, age, filters, group tabs и список.

**Reference:** [REF Task Catalog](./earnit_kids_reference_from_current_screens_v9.html#ref-task-catalog)

---

## UI-006. Перенести group tabs каталога сверху списка и сделать как в основных заданиях ✅

**Текущий экран:** присланный скрин, где group tabs:
- `Все`
- `Движение и...`
- `Дом и поря...`
- `Ещё`
находятся **внизу после списка** и выглядят иначе, чем tabs в основном `Задания`.

**Проблема на текущем скрине**
- навигация появляется после контента;
- другой visual style;
- два разных паттерна групп в одном приложении.

**Изменить именно текущий экран**
- убрать текущую нижнюю полоску group tabs;
- вставить group tabs **перед списком**, сразу после `Возраст | Фильтры`;
- использовать тот же component/style/order, что на основном `Задания`;
- `Все`, 2-3 коротких group labels, `Ещё`;
- без horizontal scroll.

**Reference:** [REF Task Catalog](./earnit_kids_reference_from_current_screens_v9.html#ref-task-catalog)

---

## UI-007. Сделать rows каталога компактнее ✅

**Текущий экран:** каталог с большими кнопками `+ Добавить` справа и высокой строкой.

**Изменить именно текущий экран**
- уменьшить высоту row;
- у уже добавленного элемента вместо большой зеленой `Уже добавлено` использовать компактный status `Добавлено`;
- для добавления использовать compact `＋` или маленькую однострочную кнопку;
- metadata в 1 строку.

**Reference:** [REF Task Catalog](./earnit_kids_reference_from_current_screens_v9.html#ref-task-catalog)

---

# 4. Каталог наград

## UI-008. Применить к каталогу наград тот же layout, что к каталогу заданий ✅

**Текущий экран:** скрин `Каталог наград`:
- отдельный header;
- search;
- age/filter;
- карточки;
- текущие group tabs реализованы иначе.

**Изменить именно текущий экран**
- повторить структуру нового Task Catalog;
- group tabs перед списком;
- одинаковые размеры Search / Filter / GroupSubnav;
- reward-specific metadata оставить.

**Reference:** [REF Reward Catalog](./earnit_kids_reference_from_current_screens_v9.html#ref-reward-catalog)

---

# 5. Multi-select в каталоге

## UI-009. Не менять layout списка при включении "Выбрать несколько" ✅

**Текущий экран:** скрин каталога заданий в режиме выбора с крупными checkbox слева.

**Проблема**
- checkbox съедает много ширины;
- rows визуально отличаются от обычного catalog mode;
- action `Готово` не показывает, что будет дальше.

**Изменить именно текущий экран**
- checkbox сделать 22-24 px;
- сохранить те же rows, icon/title/meta;
- добавить sticky bar `Выбрано: N | Добавить N` над bottom nav.

**Reference:** [REF Bulk](./earnit_kids_reference_from_current_screens_v9.html#ref-bulk)

---

# 6. Фильтры каталога

## UI-010. Уплотнить текущий Age sheet ✅

**Текущий экран:** скрин `Возраст`, где:
- 6-8 лет
- 9-11 лет
- 12-14 лет
идут большими отдельными rows;
- ниже отдельные большие `Сбросить` и `Готово`.

**Изменить именно текущий экран**
- заменить три большие rows на 3 compact pills/grid;
- `Сбросить` оставить secondary;
- `Применить` primary;
- выбранный возраст показывать на trigger `Возраст · 6-8`.

**Reference:** [REF Filters](./earnit_kids_reference_from_current_screens_v9.html#ref-filters)

---

## UI-011. Уплотнить текущий sheet "Фильтры" ✅

**Текущий экран:** скрин с большими rows:
- Простые
- Обычные
- Посложнее
- Каждый день
- Раз в неделю
- Без лимита.

**Изменить именно текущий экран**
- сделать compact pills;
- `Посложнее` -> `Сложнее`;
- `Каждый день` -> `Ежедневно`;
- `Раз в неделю` -> `Еженедельно`;
- весь sheet должен помещаться без внутреннего scroll.

**Reference:** [REF Filters](./earnit_kids_reference_from_current_screens_v9.html#ref-filters)

---

# 7. Управление группами

## UI-012. Переделать текущее popup-menu группы ✅

**Текущий экран:** скрин `Группы заданий`, где popup:
- `Изменить`;
- `Переместить выше`;
- `Переместить ниже`;
- `Архивировать`;
- `Удалить`;
- отдельная кнопка `Закрыть`.

**Проблема на текущем скрине**
- `Переместить выше/ниже` переносятся на 2 строки;
- popup слишком высокий;
- отдельная кнопка `Закрыть` занимает еще одну строку;
- reorder по одному шагу неудобен.

**Изменить именно текущий экран**
- popup заменить на compact action sheet:
  - `Изменить`
  - `Изменить порядок`
  - `Архивировать`
  - divider
  - `Удалить`;
- удалить `Закрыть`;
- закрытие outside/back/swipe;
- `Изменить порядок` открывает отдельный reorder mode.

**Reference:** [REF Group Menu](./earnit_kids_reference_from_current_screens_v9.html#ref-group-menu)

---

## UI-013. Добавить отдельный reorder screen вместо `выше/ниже` ✅

**Текущий экран:** сейчас отдельного reorder screen нет.

**Добавить к существующему flow**
- после `Изменить порядок` открыть список с `≡`;
- группы двигаются drag-and-drop;
- `Готово` сохраняет весь порядок.

**Reference:** [REF Group Reorder](./earnit_kids_reference_from_current_screens_v9.html#ref-group-reorder)

---

# 8. Экран "Семья"

## UI-014. Исправить текущую верхнюю часть Family ✅

**Текущий экран:** на одном из последних скринов:
- `Семья` находится слева;
- `Aliska` и баланс стоят **в той же строке справа**.

**Сравнение с присланным экраном `Группы`**
На `Группы` правильно:
- первая строка `Aliska + баланс`;
- ниже отдельный `Группы + Новая`.

**Изменить именно текущий Family**
Сделать **точно такую же двухстрочную структуру**, как в `Группы`:

```text
Aliska⌄                              🪙24
Семья
```

Не:

```text
Семья                    Aliska⌄ 🪙24
```

**Reference:** [REF Family](./earnit_kids_reference_from_current_screens_v9.html#ref-family), [REF Groups](./earnit_kids_reference_from_current_screens_v9.html#ref-groups)

---

## UI-015. Убрать лишний отдельный заголовок "Дети" ✅

**Текущий экран:** более ранний скрин Family:
- `Семья`
- ниже `Дети`
- потом карточка Aliska.

**Изменить именно текущий экран**
- после `Семья` сразу current child card;
- `Дети` убрать;
- `Неактивные · 1` и `Настройки семьи` оставить section labels.

**Reference:** [REF Family](./earnit_kids_reference_from_current_screens_v9.html#ref-family)

---

## UI-016. Не дублировать баланс Aliska в карточке ребенка ✅

**Текущий экран:** на раннем Family screen balance `24` был и в ContextBar, и внутри current child card.

**Изменить**
- balance оставить в ContextBar;
- в карточке current child оставить name + current status + `⋯`.

**Reference:** [REF Family](./earnit_kids_reference_from_current_screens_v9.html#ref-family)

---

# 9. Настройки уведомлений

## UI-017. Переделать текущие switch controls ✅

**Текущий экран:** скрин `Уведомления`, где switches:
- крупные;
- knob визуально выступает;
- ON/OFF выглядят не как единый Mini App component.

**Изменить именно текущий экран**
- заменить все текущие switches на единый 46x28 style;
- blue ON;
- neutral OFF;
- knob внутри track;
- tap target >=44 px.

**Reference:** [REF Notifications](./earnit_kids_reference_from_current_screens_v9.html#ref-notifications)

---

## UI-018. Добавить icon tile в каждую текущую notification row ✅

**Текущий экран:** сейчас строки в основном текст + switch.

**Изменить именно текущий экран**
Добавить слева графику по смыслу:
- запрос награды -> gift;
- выполненное задание -> checklist;
- баланс -> coin;
- приглашение -> users;
- Telegram -> paper plane;
- parent fulfilled reward -> gift/check;
- parent completed task -> user/check;
- message -> chat.

**Reference:** [REF Notifications](./earnit_kids_reference_from_current_screens_v9.html#ref-notifications)

---

# 10. Форма "Новая награда" / "Новое задание"

## UI-019. Заменить текущий browser dropdown группы ✅

**Текущий экран:** скрин `Новая награда`, где после поля `Группа` открывается browser-like список:
- emoji + names;
- системный scrollbar;
- desktop-looking dropdown.

**Проблема**
- визуально не Mini App;
- dropdown может выйти за viewport;
- маленький scrollbar;
- другой style, чем management groups.

**Изменить именно текущий экран**
- поле `Группа` оставить input-like row;
- tap открывает Mini App bottom sheet;
- каждая group row: icon + full name + selected check;
- использовать тот же список groups, что в management;
- search только если групп > 8;
- после tap sheet закрывается и значение обновляется.

**Reference:** [REF Group Picker](./earnit_kids_reference_from_current_screens_v9.html#ref-group-picker), [REF Reward Form](./earnit_kids_reference_from_current_screens_v9.html#ref-reward-form), [REF Task Form](./earnit_kids_reference_from_current_screens_v9.html#ref-task-form)

---

# 11. Меню `⋯` задания и награды

## UI-020. Добавить reference и реализацию нового task `⋯` ✅

**Текущий экран:** task menu сейчас показан на скрине с `Выполнить за ребёнка`.

**Изменить**
- использовать отдельный `✓` в row;
- `⋯`:
  - `Изменить`
  - `Архивировать`
  - divider
  - `Удалить`.

**Reference:** [REF Task Menu](./earnit_kids_reference_from_current_screens_v9.html#ref-task-menu)

---

## UI-021. Добавить такой же management pattern для reward `⋯` ✅

**Текущий экран:** на присланных reward screens отдельный новый `⋯` reference отсутствовал.

**Изменить текущий rewards list**
- `Выдать` вынести отдельной кнопкой;
- `⋯`:
  - `Изменить`
  - `Архивировать`
  - divider
  - `Удалить`.

**Reference:** [REF Reward Menu](./earnit_kids_reference_from_current_screens_v9.html#ref-reward-menu)

---

# 12. Выполнение задания родителем

## UI-022. Confirmation вместо немедленного "Выполнить за ребенка" ✅

**Текущий экран:** действие сейчас спрятано в popup и формулируется `Выполнить за ребёнка`.

**Изменить flow**
После `✓` показать:
- task;
- Aliska;
- `+N монет`;
- `Отмена`;
- `Выполнить`.

**Reference:** [REF Task Completion](./earnit_kids_reference_from_current_screens_v9.html#ref-task-completion)

---

# 13. Выдача награды

## UI-023. Развести "куплена" и "выдана" ✅

**Текущий интерфейс:** отдельного ясного визуального flow на присланных экранах нет.

**Добавить в текущие rewards**
Если ребенок уже купил:
- `Куплено · ожидает выдачи`;
- `Выдать`;
- confirmation прямо пишет, что монеты уже списаны.

Если parent выдает напрямую:
- показать `Баланс: 24 -> 18`;
- `Выдать за 6`.

**Reference:** [REF Reward Fulfillment](./earnit_kids_reference_from_current_screens_v9.html#ref-reward-fulfillment)

---

# 14. Child UI

## UI-024. Не копировать parent UI ребенку ✅

**Текущие присланные скрины в основном parent UI.**

**Добавить отдельные child screens**
- Home: `Сегодня`, progress, задания, `Готово`;
- Shop: price + `Можно купить` / `Не хватает N`;
- без add/edit/archive/delete.

**Reference:** [REF Child Home](./earnit_kids_reference_from_current_screens_v9.html#ref-child-home), [REF Child Shop](./earnit_kids_reference_from_current_screens_v9.html#ref-child-shop)

---

# 15. Архитектурные задачи, которые нужны именно из-за найденных расхождений экранов

## ARCH-001. Один component для header, потому что Family и Groups уже разошлись ✅

**Причина из скриншотов**
Family и Groups сейчас используют разную геометрию верхней части.

**Изменение**
Один:
- `ContextBar`;
- `ScreenHeader`.

**Reference:** [REF Family](./earnit_kids_reference_from_current_screens_v9.html#ref-family), [REF Groups](./earnit_kids_reference_from_current_screens_v9.html#ref-groups)

---

## ARCH-002. Один `GroupSubnav`, потому что main и catalog уже разошлись ✅

**Причина из скриншотов**
В main groups сверху, в catalog groups были снизу и другого вида.

**Изменение**
Один component + один order source.

**Reference:** [REF Tasks](./earnit_kids_reference_from_current_screens_v9.html#ref-tasks), [REF Task Catalog](./earnit_kids_reference_from_current_screens_v9.html#ref-task-catalog)

---

## ARCH-003. Один `GroupPicker`, потому что форма сейчас использует browser select ✅

**Причина из скриншота**
Награда открывает системный dropdown.

**Изменение**
Один Mini App picker для task/reward create/edit.

**Reference:** [REF Group Picker](./earnit_kids_reference_from_current_screens_v9.html#ref-group-picker)

---

## ARCH-004. Один `OverflowActionSheet`, потому что group/task/reward menus должны выглядеть одинаково ✅

**Изменение**
Общий visual primitive, но actions приходят из domain capabilities.

**Reference:** [REF Group Menu](./earnit_kids_reference_from_current_screens_v9.html#ref-group-menu), [REF Task Menu](./earnit_kids_reference_from_current_screens_v9.html#ref-task-menu), [REF Reward Menu](./earnit_kids_reference_from_current_screens_v9.html#ref-reward-menu)

---

## ARCH-005. Capability model с backend

UI не должен сам угадывать, какие actions показывать.

Например:
```json
{
  "canComplete": true,
  "canEdit": true,
  "canArchive": true,
  "canDelete": false,
  "canFulfill": false
}
```

---

## ARCH-006. Ledger + idempotency для coin operations

Нужно из-за новых quick actions:
- completion task;
- direct reward issue;
- reward purchase.

Double tap / retry не должны создавать повторное начисление/списание.

---

# 16. Порядок реализации по текущим экранам

1. **Family + Groups header** - UI-014, ARCH-001
2. **Tasks header/list/menu** - UI-002, UI-003, UI-004, UI-020
3. **Task Catalog** - UI-005, UI-006, UI-007, ARCH-002
4. **Reward Catalog** - UI-008
5. **Filters / Bulk** - UI-009, UI-010, UI-011
6. **Groups menu / reorder** - UI-012, UI-013
7. **Forms / GroupPicker** - UI-019, ARCH-003
8. **Notifications** - UI-017, UI-018
9. **Completion / fulfillment** - UI-022, UI-023, ARCH-005, ARCH-006
10. **Child UI** - UI-024

---

# 17. Definition of Done

Для каждой задачи AI-агент должен:

- [ ] открыть указанный `Reference`;
- [ ] найти соответствующий текущий screen/component в коде;
- [ ] изменить **существующий screen**, а не создавать параллельную новую версию;
- [ ] удалить старый UI pattern, если он заменен;
- [ ] проверить 320 / 360 / 390 / 430 px;
- [ ] сравнить screenshot после изменения с HTML reference;
- [ ] убедиться, что controls/menu/chips не wrap;
- [ ] проверить, что нет legacy/browser UI;
- [ ] проверить, что task/reward/group variants используют общие primitives там, где backlog этого требует.

---

# 18. Неочевидные проблемы, найденные именно по присланным скриншотам

1. **У тебя уже начался visual drift между одинаковыми сущностями.**  
   Самый наглядный пример: groups на main screen и groups в catalog. Поэтому задачи должны править не только конкретный экран, но и устранять источник расхождения.

2. **Family header ломался не из-за spacing, а из-за отдельного layout.**  
   Поэтому исправление должно удалить special Family header, а не просто поменять margin.

3. **Browser select в форме награды - сигнал, что Mini App flow местами все еще падает обратно в legacy/web semantics.**  
   Такие места надо искать и в task form, а не чинить только один reward screenshot.

4. **`Выполнить за ребёнка` и `Выдать награду` - не просто пункты меню.**  
   Это операции с балансом. UX-исправление без idempotency оставит скрытый production bug.

5. **Не надо переписывать весь UI под reference.**  
   В этом backlog reference показывает целевой pattern для конкретно изменяемой части текущего screen. Остальное на экране сохраняется, если задача явно не говорит обратного.
