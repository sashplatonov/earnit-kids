# EarnIt Kids - Mini App Groups & Ready Catalog Follow-up Backlog

## Status and scope

This is a **new follow-up backlog** for the Parent Mini App.

Previous Mini App / Telegram Bot redesign work is considered completed.

The source of truth for this iteration is:

1. the current Mini App after the completed redesign;
2. the product requirements in this backlog;
3. `earnit-catalog-groups-target-reference.html` as the UX/composition reference.

This iteration covers two product areas:

1. Mini App-native management of Task and Reward groups;
2. a ready-made parent catalog of Tasks and Rewards that can be quickly added into the family's own lists.

Do not reopen unrelated Bot, auth, outbox, role-resolution or previous visual redesign tasks unless a compatibility adjustment is explicitly required.

---

# Product goals

## 1. Group management

Current problem:

- managing Task/Reward groups still opens a legacy website form;
- this breaks the Mini App experience;
- the visual language, spacing and navigation no longer match the rest of the Mini App.

Target:

- create/edit/delete/archive/reorder groups entirely inside Mini App;
- compact mobile-first controls;
- no website redirect or legacy form;
- Task and Reward groups can be managed separately;
- common actions take 1-2 transitions.

## 2. Ready catalog

Parent should be able to open a **ready catalog** and quickly add useful Tasks or Rewards to their family.

The catalog must not feel like a marketplace or content feed.

It is a practical starter library:

- simple categories;
- clear titles;
- useful filters;
- quick preview;
- add one item;
- select several and add in bulk;
- skip duplicates safely.

The goal is:

> "I do not want to invent 20 good tasks from scratch. Show me useful options and let me add the ones that fit our family."

---

# Global UX rules

## Mini App visual language

- Use the current Mini App visual system.
- Use one SVG icon family for controls and navigation.
- Do not open legacy website pages.
- Do not use desktop-style forms.
- Do not use nested large cards.
- Keep list rows compact and touch-friendly.
- Minimum touch target: 44x44px.
- All visible Mini App UI text is Russian.
- Parent UI stays information-dense and calm.

## Important content exception: emoji in Task/Reward titles

Previous visual rules prohibit emoji as UI icons.

This backlog introduces one intentional exception:

**catalog Task and Reward titles contain exactly one meaningful emoji at the beginning because the emoji is part of the content/title itself.**

Example:

```text
📖 Почитать книгу 15 минут
```

This is allowed.

Do not use emoji:
- for menu icons;
- for buttons;
- for navigation;
- as decorative UI graphics.

Those remain SVG.

---

# Content quality contract for ready catalog

The ready catalog serves children from **6 to 14 years old**.

## General rules

- Language: Russian.
- Simple, concrete wording.
- A child should understand the meaning from the title alone.
- No abstract, marketing-like or overly playful names.
- Every title starts with exactly one meaningful emoji.
- The title must remain understandable without `groupName` or `comment`.
- If time, quantity or completion criteria matter, include them in the title.
- Prefer a slightly longer but unambiguous title over a short vague title.
- `comment` may clarify, but must not be necessary to understand the core action/result.

Forbidden style examples:

```text
Королева настолки
Книжная искра
Командир вечера
Супер-цель
Домашний помощник
Стоп-кнопка
Большая победа дня
```

---

# Ready Task content rules

A Task answers:

> "Что мне нужно сделать, чтобы получить монеты?"

Recommended title formula:

```text
emoji + action verb + concrete object/result + criterion if needed
```

Good examples:

```text
🌅 Умыться, одеться и причесаться
🧽 Убрать свой стол или рабочее место
⏱️ Помогать по дому 10 минут
📖 Почитать книгу 15 минут
🔢 Позаниматься математикой 10 минут
🇷🇸 Выучить 1-3 слова по-сербски
📚 Прочитать главу и рассказать, что произошло
🧘 Сделать зарядку или растяжку 7 минут
🧯 Остановиться и успокоиться, когда злюсь
🗣️ Сказать словами, что чувствуешь и чего хочешь
🪄 Признать ошибку и помочь всё исправить
👍 Не сосать палец весь день с утра до вечера
```

`comment`:

- short;
- explains when the Task counts as completed;
- may include 2-4 examples;
- must not add a completely new mandatory requirement missing from the title.

Example:

```text
Title:
🧺 Разобрать одну зону со своими вещами

Comment:
Сама разобрала одну небольшую зону: стол, сумку, полку,
игрушки или одежду после прогулки. Не одну вещь, а именно зону.
```

---

# Ready Reward content rules

A Reward answers:

> "Что я получу, если куплю эту награду?"

Recommended title formula:

```text
emoji + concrete result / activity / purchase
```

Good examples:

```text
🎲 Выбрать настольную игру на вечер
🧸 Поиграть с мамой или папой 20 минут
🏰 Построить крепость из подушек и поиграть
🧁 Приготовить десерт вместе с родителем
🚶 Выбрать маршрут прогулки
🍽️ Выбрать ужин из вариантов
🛁 Принять ванну с пеной и игрушками
🎨 Порисовать или сделать поделку с родителем
🍦 Получить одно мороженое
🎡 Сходить в игровую зону или парк аттракционов
✨ Купить маленький аксессуар или детскую косметику
📦 Сделать один онлайн-заказ
🎧 Купить большую желанную вещь
```

`comment` may clarify:

- what is included;
- duration;
- restrictions;
- whether parent choice is needed;
- what purchase/use means in practice.

Example:

```text
Title:
🧸 Поиграть с мамой или папой 20 минут

Comment:
20 минут игры с мамой или папой без телефона.
Ребёнок выбирает игру.
```

---

# Coins / price rules

Tasks:

- `coins` = amount earned after parent approval;
- simple daily tasks usually cost less;
- harder, rarer or self-control tasks may cost more.

Rewards:

- `price` reflects value and realistic purchase frequency;
- avoid near-identical rewards with wildly different prices without a reason.

---

# Frequency rules

Frequency is represented through:

```text
frequencyLimit
frequencyPeriod
```

Examples:

```text
1 раз в день
2 раза в неделю
1 раз в неделю
2 раза в месяц
1 раз в месяц
```

Do not put technical frequency into title unless duration/quantity is itself part of the action.

Good:

```text
📖 Почитать книгу 15 минут
🇷🇸 Выучить 1-3 слова по-сербски
```

---

# Quality gate for catalog content

Before catalog content is accepted, validate:

1. Can a child aged 6-14 understand the title without help?
2. Is it clear exactly what must be done / what will be received?
3. If `groupName` and `comment` are removed, does the title still make sense?
4. Is there a concrete verb or concrete result?
5. Is there unnecessary game-like metaphor instead of meaning?
6. Is time/quantity/criterion included when required?
7. Does the emoji match the meaning?

If any of questions 1-3 is "no", rewrite the title.

Core rule:

```text
Task = "что мне нужно сделать"
Reward = "что я получу"
```

---

# GRP-001 - Replace legacy Task group management with Mini App-native flow

**Priority:** P0  
**Channel:** Parent Mini App
**Status:** ✅ Done

## Problem

Task group management opens a legacy website form.

## Entry points

Parent Tasks:

```text
Задания
[+ Добавить] [⋮]
```

Overflow:

```text
Импорт из CSV
Управление группами
```

Also allow opening group management from the group filter `Ещё`.

## Target screen

```text
Группы заданий

[+ Новая группа]

Утро                      8 заданий   ⋮
Учёба                    12 заданий   ⋮
Дом и порядок             7 заданий   ⋮
Самостоятельность         4 задания   ⋮
```

## Row actions

```text
Изменить
Переместить выше
Переместить ниже
Архивировать
Удалить
```

Only show actions supported by domain semantics.

## Group form

```text
Новая группа

Название
[________________]

Графика
[Дом / house SVG ▾]

[Сохранить]
```

Edit uses the same form prefilled.

## Delete rules

If group contains Tasks, never silently delete children.

Possible supported behavior:

```text
Группа содержит 7 заданий.

Что сделать с заданиями?

[Перенести в другую группу]
[Оставить без группы]
[Отмена]
```

Only offer "leave without group" if the domain allows ungrouped Tasks.

## Acceptance criteria

- No website or legacy form opens.
- Create/Edit/Delete/Archive happen in Mini App.
- Deleting a non-empty group cannot orphan data unexpectedly.
- Group changes immediately update the Task filter submenu.
- All visible UI is Russian.
- Mobile 320px width works without horizontal overflow.

---

# GRP-002 - Replace legacy Reward group management with Mini App-native flow

**Priority:** P0  
**Channel:** Parent Mini App
**Status:** ✅ Done

Same principles as GRP-001, but independent Reward groups.

Target:

```text
Группы наград

[+ Новая группа]

Время с семьёй           6 наград   ⋮
Развлечения              5 наград   ⋮
Покупки                  4 награды  ⋮
Большие цели             2 награды  ⋮
```

## Acceptance criteria

- Reward groups are not accidentally mixed with Task groups.
- No legacy website flow.
- Current Reward filter updates immediately.

---

# GRP-003 - Group ordering and usage ranking compatibility

**Priority:** P1  
**Depends on:** GRP-001, GRP-002
**Status:** ✅ Done

Existing catalog filter behavior:

```text
Все → frequently selected groups → Ещё
```

Manual group ordering and frequency ranking are separate concepts.

Rules:

- group management controls canonical/default ordering;
- frequency ranking determines which groups surface before `Ещё`;
- do not overwrite user-created ordering simply because a group was selected often;
- `Ещё` should display remaining groups using canonical/default ordering.

---

# CAT-001 - Add Parent Ready Catalog entry points

**Priority:** P0  
**Channel:** Parent Mini App

## Goal

Parent can open ready Task or Reward catalog without confusing it with their own current list.

## Parent Tasks header

```text
Задания

[+ Добавить]
[Каталог]
```

If horizontal space is tight:

```text
[+ Добавить] [⋮]

⋮:
Каталог заданий
Импорт из CSV
Управление группами
```

## Parent Rewards header

Same pattern:

```text
Каталог наград
```

## Important distinction

Use clear labels:

```text
Мои задания
Каталог заданий
```

and:

```text
Мои награды
Каталог наград
```

Do not call catalog items "магазин" in parent UI. "Магазин" is the child's Reward shop, not the parent's template library.

---

# CAT-002 - Ready Task Catalog screen

**Priority:** P0  
**Depends on:** CAT-001

## Header

```text
Каталог заданий

Найдите готовые задания и добавьте подходящие в свою семью.
```

## Search

Compact search:

```text
[Поиск по заданиям________________]
```

Search fields:

- title;
- comment;
- catalog group/tags.

## Search and filters

Search stays at the top of the catalog.

Group selection is **not** placed here. It lives in the fixed Mini App-style group submenu directly above bottom navigation, as defined in CAT-002A.

Immediately below search, show two non-scrolling compact filter controls:

```text
[Возраст] [Фильтры]
```

Age and advanced filters are defined in CAT-002B.

Do not display:

- a permanent filter sidebar;
- horizontally scrolling filter chips;
- a horizontally scrolling group row under search.

## Item row/card

Compact catalog row:

```text
[SVG semantic graphic]  📖 Почитать книгу 15 минут
                        2 монеты · Учёба
                        1 раз в день

                    [+ Добавить]
```

Important:

- the title's initial emoji is content and stays visible;
- SVG graphic is the UI/entity visual from the app icon system;
- do not replace one with the other.

## Expand/details

Tap row:

```text
📖 Почитать книгу 15 минут

2 монеты
1 раз в день
Возраст: 6-14

Почитать любую подходящую книгу 15 минут.
Если книга закончилась раньше - выбрать следующую.

[Добавить в мои задания]
```

Do not force detail opening before adding.

## Added state

After add:

```text
✓ Уже добавлено
```

The button becomes non-destructive and does not duplicate.

---


# CAT-002A - Catalog group submenu must match the main Mini App pattern

**Priority:** P0  
**Depends on:** CAT-002, CAT-003, existing Mini App group submenu behavior

The ready catalog must use the **same group-navigation principle as the main Tasks/Rewards Mini App screens**.

Do not put catalog groups under the search field.

Do not use a long horizontally scrolling category row in the main catalog content area.

## Placement

The catalog group submenu sits directly **above the bottom navigation**.

Layout:

```text
catalog content
...
────────────────────────

[Все] [Учёба] [Дом] [Самостоятельность] [Ещё]

[Главная] [Задания] [Награды] [Семья]
```

On Reward catalog:

```text
[Все] [Семья] [Привилегии] [Развлечения] [Ещё]
```

The group submenu:

- remains visually separate from the catalog filters;
- uses the same height/density as the existing main Mini App group submenu;
- stays immediately above the bottom nav;
- preserves safe area;
- does not overlap the bulk-action bar;
- does not horizontally scroll.

## Dynamic group order

Use the same ranking logic as the main Mini App:

```text
Все
→ up to 3 most frequently explicitly selected catalog groups
→ Ещё
```

Recommended deterministic ranking:

```text
selectionCount DESC,
lastSelectedAt DESC,
groupName ASC
```

Task Catalog and Reward Catalog ranking are separate.

Do not reorder while the submenu is open or immediately after every tap.
Recalculate at a stable boundary such as:

- next catalog entry;
- next Mini App session;
- another existing stable boundary used by the main Mini App.

This prevents chips from jumping while the parent is using them.

## `Ещё`

`Ещё` opens a compact bottom sheet.

Example:

```text
Все группы

Утро и вечер
Учёба
Дом и порядок
Самостоятельность
Движение и здоровье
Общение и эмоции
Полезные привычки
```

For Rewards:

```text
Время с семьёй
Выбор и привилегии
Творчество и игры
Маленькие радости
Прогулки и развлечения
Покупки
Большие цели
```

The bottom sheet may scroll internally if there are more groups than fit vertically.

The fixed submenu itself must not scroll horizontally.

## Active state

Use the established Mini App style:

- blue text;
- subtle border;
- very light tint if needed;
- no large filled blue/purple pill.

## Acceptance criteria

- Catalog group submenu is directly above bottom nav.
- `Все` is always first.
- At most 3 group chips are shown before `Ещё`.
- There is no horizontal scrolling in this submenu.
- `Ещё` contains the remaining groups.
- Tasks and Rewards maintain independent group ranking.
- Bulk action bar does not cover the group submenu or bottom nav.
- Layout works at 320, 375 and 430px.


# CAT-002B - Catalog filters must fit without horizontal scrolling and use SVG graphics

**Priority:** P0  
**Depends on:** CAT-002, CAT-003

Catalog filters are separate from the group submenu.

Do not implement filters as a horizontally scrolling chip strip.

## Compact filter control

At the top of the catalog, under search, use one compact control row that fits the screen without horizontal scrolling.

Recommended:

```text
[👤 SVG Возраст] [⚙ SVG Ещё фильтры]
```

Actual UI uses SVG icons, not emoji.

For example:

```text
[person/age SVG  9-11]
[sliders SVG     Фильтры]
```

If no age is selected:

```text
[person/age SVG  Возраст]
[sliders SVG     Фильтры]
```

The row must fit at 320px.

Do not display all age/difficulty/frequency values simultaneously as top-level chips.

## Filter sheet

Tap `Возраст`:

```text
Возраст

[child SVG]  6-8 лет
[junior SVG] 9-11 лет
[teen SVG]   12-14 лет

[Сбросить]
```

Tap `Фильтры`:

```text
Фильтры

Сложность
[spark/simple SVG] Простые
[target SVG]       Обычные
[mountain SVG]     Посложнее

Частота
[sun/calendar SVG] Каждый день
[calendar-week SVG] Раз в неделю
[infinity SVG]     Без лимита

[Применить]
[Сбросить]
```

The filter sheet may be a compact bottom sheet.

## Graphics

Every filter option has one semantic SVG graphic from the existing Mini App icon family.

Do not use:

- emoji as filter graphics;
- decorative AI illustrations;
- mixed filled/outline icon families;
- large colored category artwork.

Recommended semantic keys:

```text
AGE_6_8
AGE_9_11
AGE_12_14
DIFFICULTY_SIMPLE
DIFFICULTY_NORMAL
DIFFICULTY_ADVANCED
FREQUENCY_DAILY
FREQUENCY_WEEKLY
FREQUENCY_UNLIMITED
FILTERS
```

Reuse existing SVG assets where semantic meaning already exists.

## Selected filter summary

The top compact row reflects active filters without expanding into a scrollable list.

Examples:

```text
[person SVG  6-8]
[sliders SVG  Фильтры · 2]
```

or:

```text
[person SVG  12-14]
[sliders SVG  Фильтры]
```

`Фильтры · 2` means two non-age filters are active.

## Search + filters composition

Target vertical structure:

```text
Каталог заданий
description

[Поиск________________________]

[Возраст]       [Фильтры]

catalog item
catalog item
catalog item
...

[group submenu: Все / top groups / Ещё]
[bottom navigation]
```

Do not place group chips immediately under search.

## Acceptance criteria

- No horizontal scrolling for filter controls.
- Age selection is visible without opening the filter sheet.
- Non-age active filter count is visible.
- Filter options use semantic SVG graphics.
- Filter sheet works at 320px without horizontal overflow.
- Group selection and filters are visually/behaviorally separate.

# CAT-003 - Ready Reward Catalog screen

**Priority:** P0  
**Depends on:** CAT-001

Same interaction model as Task catalog.

Example rows:

```text
[SVG] 🎲 Выбрать настольную игру на вечер
      2 монеты · Время с семьёй
      1 раз в день

      [+ Добавить]
```

```text
[SVG] 🧸 Поиграть с мамой или папой 20 минут
      4 монеты · Время с семьёй
      2 раза в неделю

      [+ Добавить]
```

Reward group navigation follows CAT-002A and sits directly above bottom navigation.

Advanced Reward filters follow CAT-002B and stay separate from groups.

Useful Reward-specific filter options may include semantic filters such as:

```text
Без покупки
Покупка
Совместное время
```

If implemented, they belong inside the compact `Фильтры` bottom sheet, not in a horizontal scrolling row.

Do not make purchase-based Rewards dominate the catalog.

---

# CAT-004 - Bulk selection and quick add

**Priority:** P0  
**Depends on:** CAT-002, CAT-003

## Goal

Parent should be able to create a useful starter set quickly.

## Interaction

Top action:

```text
Выбрать несколько
```

Selection mode:

```text
[✓] 🌅 Умыться, одеться и причесаться
[ ] 🧽 Убрать свой стол или рабочее место
[✓] 📖 Почитать книгу 15 минут
[✓] 🔢 Позаниматься математикой 10 минут
```

Sticky compact action bar above bottom nav:

```text
Выбрано: 3

[Добавить 3 задания]
```

Rewards use the same pattern.

## Acceptance criteria

- Parent can add 3-10 templates without opening each one.
- Selection survives scrolling/filtering within the current catalog session.
- Added duplicates are handled safely.
- No full-screen desktop selection table.

---

# CAT-005 - Duplicate detection and safe copying

**Priority:** P0  
**Backend + Mini App**

## Goal

Catalog add is a **copy/template operation**, not a shared mutable catalog entity.

When adding:

- create family-owned Task/Reward from template;
- family can later edit it freely;
- future changes to global catalog do not overwrite family content.

## Duplicate detection

Prefer stable catalog source id:

```text
sourceCatalogItemId
```

Family item may store:

```text
origin = CATALOG
sourceCatalogItemId = ...
```

Adding the same template twice should normally be prevented.

If the family already has a manually created semantically similar item, do not attempt unreliable fuzzy auto-blocking.

You may show:

```text
Похожее задание уже есть
```

only if backed by deterministic known mapping.

## Acceptance criteria

- Repeated taps cannot create duplicate copies.
- Family editing does not mutate global catalog.
- Catalog update does not silently change family copy.

---

# CAT-006 - Add preview-before-bulk-import summary

**Priority:** P1  
**Depends on:** CAT-004

Before bulk adding:

```text
Добавить 5 заданий?

Будет добавлено: 4
Уже есть: 1

Группы:
Утро · 1
Учёба · 2
Дом · 1

[Добавить 4]
[Назад]
```

For small single-item add, do not show confirmation.

---

# CAT-007 - Group mapping when adding catalog items

**Priority:** P0  
**Depends on:** GRP-001, GRP-002

Catalog groups and family groups are not assumed to share database IDs.

When adding an item:

1. try to map by stable semantic group key;
2. if family has mapped group, use it;
3. if not, create the family group automatically only if product policy allows;
4. otherwise ask parent once:

```text
Куда добавить?

[Учёба]
[Без группы]
[Создать группу "Учёба"]
```

Recommended behavior:

- built-in semantic groups may auto-create on first use;
- custom catalog groups must not create unexpected family groups silently.

---

# CAT-008 - Catalog content schema

**Priority:** P0  
**Backend/read model**

Suggested Task template:

```text
CatalogTaskTemplate
- id
- title
- comment
- coins
- groupKey
- groupName
- semanticGraphicKey
- frequencyLimit
- frequencyPeriod
- minAge
- maxAge
- difficulty
- tags
- active
- sortOrder
```

Suggested Reward template:

```text
CatalogRewardTemplate
- id
- title
- comment
- price
- groupKey
- groupName
- semanticGraphicKey
- frequencyLimit
- frequencyPeriod
- minAge
- maxAge
- tags
- active
- sortOrder
```

Do not encode catalog content directly inside frontend components.

---

# CAT-009 - Initial ready Task catalog

**Priority:** P0  
**Depends on:** CAT-008

Seed a practical starter catalog.

Recommended initial size:

```text
40-60 Tasks
```

Recommended groups:

```text
Утро и вечер
Учёба
Дом и порядок
Самостоятельность
Движение и здоровье
Общение и эмоции
Полезные привычки
```

## Example starter items

### Утро и вечер

```text
🌅 Умыться, одеться и причесаться
🪥 Почистить зубы утром и вечером
🎒 Самостоятельно собрать вещи на завтра
🛏️ Заправить кровать утром
🌙 Подготовиться ко сну без напоминаний
```

### Учёба

```text
📖 Почитать книгу 15 минут
📚 Прочитать главу и рассказать, что произошло
🔢 Позаниматься математикой 10 минут
✍️ Написать 5 аккуратных строк
🇷🇸 Выучить 1-3 слова по-сербски
🎒 Самостоятельно проверить школьный рюкзак
```

### Дом и порядок

```text
🧽 Убрать свой стол или рабочее место
🧺 Разобрать одну зону со своими вещами
⏱️ Помогать по дому 10 минут
🍽️ Убрать за собой посуду после еды
🧸 Убрать игрушки или свои вещи на место
🗑️ Вынести мусор
```

### Движение и здоровье

```text
🧘 Сделать зарядку или растяжку 7 минут
🚶 Погулять на улице 30 минут
💧 Выпить воду утром после пробуждения
🥕 Съесть овощи или фрукт во время еды
```

### Общение и эмоции

```text
🗣️ Сказать словами, что чувствуешь и чего хочешь
🧯 Остановиться и успокоиться, когда злюсь
🪄 Признать ошибку и помочь всё исправить
🤝 Решить спор словами без крика и драки
```

## Content review

Every seeded item must pass the quality gate in this backlog.

---

# CAT-010 - Initial ready Reward catalog

**Priority:** P0  
**Depends on:** CAT-008

Recommended initial size:

```text
30-45 Rewards
```

Recommended groups:

```text
Время с семьёй
Выбор и привилегии
Творчество и игры
Еда и маленькие радости
Прогулки и развлечения
Покупки
Большие цели
```

## Example starter items

### Время с семьёй

```text
🎲 Выбрать настольную игру на вечер
🧸 Поиграть с мамой или папой 20 минут
🏰 Построить крепость из подушек и поиграть
🧁 Приготовить десерт вместе с родителем
🎨 Порисовать или сделать поделку с родителем
```

### Выбор и привилегии

```text
🚶 Выбрать маршрут прогулки
🍽️ Выбрать ужин из вариантов
🎬 Выбрать семейный фильм на вечер
🎵 Выбрать музыку в машине
🛁 Принять ванну с пеной и игрушками
```

### Еда и маленькие радости

```text
🍦 Получить одно мороженое
🧁 Выбрать небольшой десерт
☕ Сходить с родителем в кафе за напитком или десертом
```

### Прогулки и развлечения

```text
🎡 Сходить в игровую зону или парк аттракционов
🎳 Сходить в боулинг с семьёй
🎬 Сходить в кино
🏊 Сходить в бассейн
```

### Покупки / цели

```text
✨ Купить маленький аксессуар или детскую косметику
📦 Сделать один онлайн-заказ
🎧 Купить большую желанную вещь
```

Do not let purchase Rewards become the majority.

---


# CAT-009A - Full age-specific starter catalog content

**Priority:** P0  
**Depends on:** CAT-008, CAT-009, CAT-010

The catalog must include enough useful content for each age filter.

Minimum content per age filter:

```text
6-8: at least 20 Tasks + 20 Rewards
9-11: at least 20 Tasks + 20 Rewards
12-14: at least 20 Tasks + 20 Rewards
```

Items may support more than one age group in the backend, but the seeded catalog must still produce at least the minimum number of visible relevant items when each age filter is selected independently.

The following tables are the required initial reference content. Values are starter defaults and may later be tuned globally, but should be seeded consistently.


## Возраст 6-8

### Готовые задания - 24 шт.

| Title | Coins | Group | Frequency |
|---|---:|---|---|
| 🌅 Умыться, одеться и причесаться | 1 | Утро и вечер | 1 раз в день |
| 🪥 Почистить зубы утром и вечером | 1 | Утро и вечер | 1 раз в день |
| 🛏️ Заправить кровать утром | 1 | Утро и вечер | 1 раз в день |
| 🎒 Собрать вещи на завтра вместе со списком | 1 | Самостоятельность | 1 раз в день |
| 👕 Убрать свою одежду на место | 1 | Дом и порядок | 1 раз в день |
| 🧸 Убрать игрушки после игры | 1 | Дом и порядок | 1 раз в день |
| 🧽 Убрать свой стол или рабочее место | 1 | Дом и порядок | 1 раз в день |
| 🍽️ Убрать за собой посуду после еды | 1 | Дом и порядок | 2 раза в день |
| 🌱 Полить одно домашнее растение | 1 | Дом и порядок | 1 раз в день |
| 📖 Почитать книгу 10 минут | 2 | Учёба | 1 раз в день |
| 🔤 Повторить буквы или слова 10 минут | 2 | Учёба | 1 раз в день |
| 🔢 Позаниматься математикой 10 минут | 2 | Учёба | 1 раз в день |
| ✍️ Написать 5 аккуратных строк | 2 | Учёба | 1 раз в день |
| 🇷🇸 Выучить 1-3 слова по-сербски | 2 | Учёба | 1 раз в день |
| 🎨 Порисовать или сделать поделку 15 минут | 2 | Творчество | 1 раз в день |
| 🧘 Сделать зарядку или растяжку 5 минут | 2 | Движение и здоровье | 1 раз в день |
| 🚶 Погулять на улице 20 минут | 2 | Движение и здоровье | 1 раз в день |
| 💧 Выпить стакан воды утром | 1 | Полезные привычки | 1 раз в день |
| 🗣️ Сказать словами, что чувствуешь | 2 | Общение и эмоции | 1 раз в день |
| 🤝 Попросить прощения и помочь исправить ошибку | 2 | Общение и эмоции | 1 раз в день |
| ⏱️ Помогать по дому 10 минут | 2 | Дом и порядок | 1 раз в день |
| 📚 Прочитать короткую историю и рассказать, что произошло | 3 | Учёба | 3 раза в неделю |
| 👟 Самостоятельно подготовиться к прогулке | 1 | Самостоятельность | 1 раз в день |
| 🧺 Разобрать одну небольшую зону со своими вещами | 2 | Дом и порядок | 3 раза в неделю |

### Готовые награды - 22 шт.

| Title | Price | Group | Frequency |
|---|---:|---|---|
| 🎲 Выбрать настольную игру на вечер | 4 | Время с семьёй | 1 раз в день |
| 🧸 Поиграть с мамой или папой 20 минут | 5 | Время с семьёй | 2 раза в неделю |
| 🏰 Построить крепость из подушек и поиграть | 5 | Творчество и игры | 1 раз в неделю |
| 🧁 Приготовить простой десерт вместе с родителем | 6 | Время с семьёй | 1 раз в неделю |
| 🚶 Выбрать маршрут прогулки | 3 | Выбор и привилегии | 2 раза в неделю |
| 🍽️ Выбрать ужин из вариантов | 4 | Выбор и привилегии | 1 раз в неделю |
| 🎬 Выбрать семейный мультфильм или фильм | 5 | Выбор и привилегии | 1 раз в неделю |
| 🎵 Выбрать музыку в машине | 2 | Выбор и привилегии | 2 раза в неделю |
| 🛁 Принять ванну с пеной и игрушками | 4 | Маленькие радости | 1 раз в неделю |
| 🎨 Порисовать с родителем 20 минут | 4 | Время с семьёй | 2 раза в неделю |
| 🍦 Получить одно мороженое | 6 | Маленькие радости | 1 раз в неделю |
| 🧁 Выбрать небольшой десерт | 5 | Маленькие радости | 1 раз в неделю |
| 🛝 Сходить на новую детскую площадку | 7 | Прогулки и развлечения | 2 раза в месяц |
| 🎡 Сходить в игровую зону | 12 | Прогулки и развлечения | 1 раз в месяц |
| 🎳 Сходить в боулинг с семьёй | 14 | Прогулки и развлечения | 1 раз в месяц |
| 🎬 Сходить в кино | 12 | Прогулки и развлечения | 1 раз в месяц |
| 🏊 Сходить в бассейн | 10 | Прогулки и развлечения | 2 раза в месяц |
| 📚 Выбрать новую книгу | 10 | Покупки | 1 раз в месяц |
| 🧩 Получить небольшой набор для творчества или пазл | 12 | Покупки | 1 раз в месяц |
| 🧸 Купить маленькую игрушку | 15 | Покупки | 1 раз в месяц |
| 🍓 Выбрать любимые ягоды или фрукты в магазине | 6 | Маленькие радости | 1 раз в неделю |
| 🎈 Устроить семейный мини-пикник | 8 | Время с семьёй | 2 раза в месяц |


## Возраст 9-11

### Готовые задания - 24 шт.

| Title | Coins | Group | Frequency |
|---|---:|---|---|
| 🌅 Собраться утром без повторных напоминаний | 2 | Утро и вечер | 1 раз в день |
| 🪥 Почистить зубы утром и вечером | 1 | Утро и вечер | 1 раз в день |
| 🎒 Самостоятельно проверить школьный рюкзак | 2 | Самостоятельность | 1 раз в день |
| 🛏️ Заправить кровать утром | 1 | Утро и вечер | 1 раз в день |
| 👕 Разобрать одежду после прогулки или школы | 1 | Дом и порядок | 1 раз в день |
| 🧽 Убрать свой стол или рабочее место | 2 | Дом и порядок | 1 раз в день |
| 🍽️ Убрать за собой посуду и протереть место после еды | 2 | Дом и порядок | 1 раз в день |
| 🧺 Разобрать одну зону со своими вещами | 2 | Дом и порядок | 3 раза в неделю |
| ⏱️ Помогать по дому 15 минут | 3 | Дом и порядок | 1 раз в день |
| 🗑️ Вынести мусор | 2 | Дом и порядок | 3 раза в неделю |
| 📖 Почитать книгу 20 минут | 3 | Учёба | 1 раз в день |
| 📚 Прочитать главу и рассказать, что произошло | 4 | Учёба | 3 раза в неделю |
| 🔢 Позаниматься математикой 15 минут | 3 | Учёба | 1 раз в день |
| ✍️ Сделать письменное задание аккуратно без спешки | 3 | Учёба | 1 раз в день |
| 🇷🇸 Выучить 3-5 слов по-сербски | 3 | Учёба | 1 раз в день |
| 🧠 Повторить школьную тему 15 минут | 3 | Учёба | 1 раз в день |
| 🧘 Сделать зарядку или растяжку 7 минут | 2 | Движение и здоровье | 1 раз в день |
| 🚶 Погулять или активно подвигаться 30 минут | 3 | Движение и здоровье | 1 раз в день |
| 🗣️ Сказать словами, что чувствуешь и чего хочешь | 3 | Общение и эмоции | 1 раз в день |
| 🧯 Остановиться и успокоиться, когда злюсь | 4 | Общение и эмоции | 1 раз в день |
| 🪄 Признать ошибку и помочь всё исправить | 4 | Общение и эмоции | 1 раз в день |
| 🤝 Решить спор словами без крика и драки | 4 | Общение и эмоции | 1 раз в день |
| 📵 Провести 30 минут без телефона или планшета | 3 | Полезные привычки | 1 раз в день |
| 💧 Выпить достаточно воды в течение дня | 2 | Полезные привычки | 1 раз в день |

### Готовые награды - 22 шт.

| Title | Price | Group | Frequency |
|---|---:|---|---|
| 🎲 Выбрать настольную игру на вечер | 5 | Время с семьёй | 1 раз в день |
| 🧸 Поиграть с мамой или папой 30 минут | 6 | Время с семьёй | 2 раза в неделю |
| 🎬 Выбрать семейный фильм на вечер | 6 | Выбор и привилегии | 1 раз в неделю |
| 🍽️ Выбрать ужин из вариантов | 5 | Выбор и привилегии | 1 раз в неделю |
| 🚶 Выбрать маршрут прогулки или место для прогулки | 4 | Выбор и привилегии | 2 раза в неделю |
| 🎵 Выбрать музыку в машине или дома | 3 | Выбор и привилегии | 2 раза в неделю |
| 🧁 Приготовить десерт вместе с родителем | 7 | Время с семьёй | 1 раз в неделю |
| 🎨 Сделать творческий проект вместе с родителем | 7 | Время с семьёй | 1 раз в неделю |
| 🍦 Получить одно мороженое | 7 | Маленькие радости | 1 раз в неделю |
| ☕ Сходить с родителем в кафе за напитком или десертом | 9 | Маленькие радости | 2 раза в месяц |
| 🎬 Сходить в кино | 14 | Прогулки и развлечения | 1 раз в месяц |
| 🎳 Сходить в боулинг | 15 | Прогулки и развлечения | 1 раз в месяц |
| 🏊 Сходить в бассейн | 12 | Прогулки и развлечения | 2 раза в месяц |
| 🎡 Сходить в игровую зону или парк аттракционов | 16 | Прогулки и развлечения | 1 раз в месяц |
| 📚 Выбрать новую книгу или комикс | 12 | Покупки | 1 раз в месяц |
| 🎧 Купить недорогой аксессуар для хобби | 18 | Покупки | 1 раз в месяц |
| ✨ Купить маленький аксессуар | 14 | Покупки | 1 раз в месяц |
| 📦 Сделать один небольшой онлайн-заказ | 20 | Покупки | 1 раз в месяц |
| 🎮 Получить 30 дополнительных минут игры | 8 | Выбор и привилегии | 2 раза в неделю |
| 🛌 Лечь спать на 30 минут позже в выходной | 8 | Выбор и привилегии | 1 раз в неделю |
| 👫 Пригласить друга домой или на прогулку | 10 | Прогулки и развлечения | 2 раза в месяц |
| 🍕 Выбрать семейную доставку из согласованных вариантов | 12 | Выбор и привилегии | 1 раз в месяц |


## Возраст 12-14

### Готовые задания - 24 шт.

| Title | Coins | Group | Frequency |
|---|---:|---|---|
| 🌅 Собраться утром и выйти вовремя без напоминаний | 3 | Утро и вечер | 1 раз в день |
| 🎒 Самостоятельно подготовить всё для школы на завтра | 3 | Самостоятельность | 1 раз в день |
| 🛏️ Поддерживать порядок в своей комнате 15 минут | 3 | Дом и порядок | 1 раз в день |
| 👕 Разобрать чистую одежду и убрать её по местам | 3 | Дом и порядок | 2 раза в неделю |
| 🧺 Разобрать одну зону со своими вещами | 3 | Дом и порядок | 3 раза в неделю |
| ⏱️ Помогать по дому 20 минут | 4 | Дом и порядок | 1 раз в день |
| 🍽️ Помочь накрыть на стол и убрать после еды | 3 | Дом и порядок | 1 раз в день |
| 🗑️ Вынести мусор без напоминания | 2 | Дом и порядок | 3 раза в неделю |
| 🧹 Пропылесосить одну комнату | 4 | Дом и порядок | 2 раза в неделю |
| 📖 Почитать книгу 25 минут | 4 | Учёба | 1 раз в день |
| 📚 Прочитать главу и кратко пересказать главное | 5 | Учёба | 3 раза в неделю |
| 🔢 Позаниматься математикой 20 минут | 4 | Учёба | 1 раз в день |
| 🧠 Повторить сложную школьную тему 20 минут | 4 | Учёба | 1 раз в день |
| 🇷🇸 Выучить 5 новых слов по-сербски | 4 | Учёба | 1 раз в день |
| 📝 Спланировать домашние задания и отметить выполненное | 3 | Самостоятельность | 1 раз в день |
| 📵 Провести 1 час без телефона или соцсетей | 4 | Полезные привычки | 1 раз в день |
| 🧘 Сделать тренировку, зарядку или растяжку 15 минут | 4 | Движение и здоровье | 1 раз в день |
| 🚶 Погулять или активно подвигаться 45 минут | 4 | Движение и здоровье | 1 раз в день |
| 🗣️ Спокойно объяснить, что не устраивает и чего хочешь | 4 | Общение и эмоции | 1 раз в день |
| 🧯 Взять паузу и успокоиться вместо крика | 5 | Общение и эмоции | 1 раз в день |
| 🪄 Признать свою ошибку и предложить, как её исправить | 5 | Общение и эмоции | 1 раз в день |
| 🤝 Договориться о спорном вопросе без оскорблений | 5 | Общение и эмоции | 1 раз в день |
| 💳 Записать свои расходы за день | 4 | Самостоятельность | 1 раз в день |
| 🍳 Приготовить простой завтрак или перекус | 5 | Самостоятельность | 2 раза в неделю |

### Готовые награды - 22 шт.

| Title | Price | Group | Frequency |
|---|---:|---|---|
| 🎬 Выбрать семейный фильм на вечер | 6 | Выбор и привилегии | 1 раз в неделю |
| 🎲 Выбрать настольную игру или карточную игру на вечер | 6 | Время с семьёй | 1 раз в неделю |
| ☕ Сходить с родителем в кафе и поговорить вдвоём | 9 | Время с семьёй | 2 раза в месяц |
| 🍽️ Выбрать ужин или доставку из согласованных вариантов | 8 | Выбор и привилегии | 1 раз в неделю |
| 🚶 Выбрать место для семейной прогулки | 5 | Выбор и привилегии | 2 раза в неделю |
| 🎵 Выбрать музыку в машине или дома | 3 | Выбор и привилегии | 2 раза в неделю |
| 🛌 Лечь спать на 45 минут позже в выходной | 10 | Выбор и привилегии | 1 раз в неделю |
| 🎮 Получить 45 дополнительных минут игры | 10 | Выбор и привилегии | 2 раза в неделю |
| 📱 Получить 45 дополнительных минут экранного времени | 10 | Выбор и привилегии | 2 раза в неделю |
| 👫 Пригласить друга домой или на совместную прогулку | 12 | Прогулки и развлечения | 2 раза в месяц |
| 🎬 Сходить в кино | 16 | Прогулки и развлечения | 1 раз в месяц |
| 🎳 Сходить в боулинг | 17 | Прогулки и развлечения | 1 раз в месяц |
| 🏊 Сходить в бассейн | 14 | Прогулки и развлечения | 2 раза в месяц |
| 🎧 Купить недорогой аксессуар для хобби или техники | 20 | Покупки | 1 раз в месяц |
| 📚 Купить книгу, комикс или мангу | 15 | Покупки | 1 раз в месяц |
| ✨ Купить небольшой аксессуар или косметику | 18 | Покупки | 1 раз в месяц |
| 👕 Купить одну вещь из одежды в согласованном бюджете | 24 | Покупки | 1 раз в 2 месяца |
| 📦 Сделать один онлайн-заказ в согласованном бюджете | 24 | Покупки | 1 раз в месяц |
| 🍔 Выбрать кафе или фастфуд для семейного выхода | 16 | Прогулки и развлечения | 1 раз в месяц |
| 🎟️ Сходить на концерт, выставку или мероприятие по интересам | 30 | Большие цели | 1 раз в 2 месяца |
| 🎧 Купить большую желанную вещь | 45 | Большие цели | 1 раз в 3 месяца |
| 🎯 Получить бюджет на небольшую самостоятельную покупку | 20 | Выбор и привилегии | 1 раз в месяц |


# CAT-011 - Catalog age handling

**Priority:** P1

Catalog is for children 6-14.

Use age as a recommendation/filter signal, not as a hard truth.

Example:

```text
6-8
9-11
12-14
```

One template may support multiple/all age ranges.

Do not hide a template merely because age metadata is missing.

Parent can always search and view the full catalog.

---

# CAT-012 - Search, filters and zero states

**Priority:** P1  
**Depends on:** CAT-002, CAT-003

## No results

```text
Ничего не нашли

Попробуйте убрать часть фильтров или изменить запрос.

[Сбросить фильтры]
```

## Empty catalog error

```text
Не удалось загрузить каталог

[Повторить]
```

Do not replace existing family list with an empty state if catalog API fails.

---

# CAT-013 - Analytics for catalog usefulness

**Priority:** P2

Track non-PII events:

```text
catalog_opened
catalog_search_used
catalog_filter_selected
catalog_item_added
catalog_bulk_add
catalog_duplicate_skipped
catalog_details_opened
```

Useful dimensions:

```text
type = TASK | REWARD
catalogGroupKey
bulkCount
```

Do not send Task/Reward free text as analytics payload.

---

# CAT-014 - Catalog administration boundary

**Priority:** P1

This backlog covers parent consumption of a curated catalog.

Do not add catalog authoring/admin CRUD into Parent Mini App.

Global catalog content should be managed by:

- seed/configuration;
- internal admin tooling;
- migration/content files;

according to existing project architecture.

Parent only:

```text
browse
search
filter
preview
copy into family
```

---

# CAT-015 - Regression tests

**Priority:** P1  
**Depends on:** CAT-001..CAT-014, GRP-001..GRP-003

## Group management

Test:

- Task group create/edit/delete/archive;
- Reward group create/edit/delete/archive;
- non-empty group deletion;
- no legacy form URL;
- group filter refresh;
- Task/Reward separation.

## Catalog

Test:

- open Task catalog;
- open Reward catalog;
- search;
- filters;
- age filters;
- details;
- single add;
- bulk add;
- duplicate protection;
- group mapping;
- family copy independence;
- catalog API failure;
- already-added state;
- 320px/375px/430px layouts.

## Content validation tests

At minimum:

- title begins with exactly one emoji;
- title is not blank;
- Task has action-like title;
- Reward has concrete result title;
- age range valid;
- coins/price > 0;
- frequency pair valid;
- no duplicate catalog ids.

Automated tests cannot fully validate "human clarity"; seeded content requires manual content review against the quality gate.

---

# Recommended implementation order

1. GRP-001 - Task group Mini App management
2. GRP-002 - Reward group Mini App management
3. GRP-003 - ordering/ranking compatibility
4. CAT-008 - catalog schema/read model
5. CAT-009 - Task catalog seed
6. CAT-010 - Reward catalog seed
7. CAT-001 - catalog entry points
8. CAT-002 - Task catalog UI
9. CAT-003 - Reward catalog UI
10. CAT-005 - safe template copying / duplicate protection
11. CAT-007 - group mapping
12. CAT-004 - bulk selection
13. CAT-006 - bulk summary
14. CAT-011 - age handling
15. CAT-012 - search/filter zero states
16. CAT-014 - admin boundary
17. CAT-013 - analytics
18. CAT-015 - regression/content tests

---

# Definition of Done

This iteration is complete when:

- Task group management no longer opens legacy website forms;
- Reward group management no longer opens legacy website forms;
- group CRUD is Mini App-native;
- parent has separate ready catalogs for Tasks and Rewards;
- parent can find useful templates quickly;
- titles follow the child-readable content rules;
- catalog Tasks answer "что мне нужно сделать";
- catalog Rewards answer "что я получу";
- one meaningful emoji begins each catalog title;
- parent can add one template in one action;
- parent can bulk-add several templates;
- duplicate catalog copies are prevented;
- copied items become independent family-owned Tasks/Rewards;
- catalog group mapping does not create confusing duplicate family groups;
- each age filter (6-8, 9-11, 12-14) returns at least 20 useful Tasks and 20 useful Rewards;
- each seeded item includes title, coins/price, group and frequency;
- catalog group submenu matches the main Mini App and sits above bottom nav with `Ещё`;
- catalog filters do not horizontally scroll and use semantic SVG graphics;
- catalog works at 320-430px;
- no catalog admin functionality leaks into Parent Mini App.
