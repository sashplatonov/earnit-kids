# EarnIt Kids - Mini App & Telegram Bot Settings Follow-up Backlog v4

## Status and scope

This is the updated follow-up backlog for the current Mini App and Telegram Bot implementation.

Previous redesign backlogs are considered completed. This backlog adds/fixes the remaining product and UX work.

The source of truth for this iteration is:

1. the current product after the completed redesign;
2. the requirements in this document;
3. `earnit-miniapp-bot-settings-target-reference-v4.html` as the visual/composition reference.

## Goal

Add missing family/account/settings capabilities while preserving the compact mobile-first UX and restoring the earlier Mini App visual language.

The result must remain:

- clear for a parent;
- compact on 320-430px screens;
- role-aware;
- shallow in navigation;
- visually consistent with the earlier Mini App reference;
- Telegram-native in the Bot;
- entirely in Russian in the Mini App;
- free of oversized forms, nested cards and desktop-style website UI.

---

# Global visual rules

## Mini App visual language

Use the same visual language as the earlier Mini App reference:

- light background;
- compact flat sections;
- subtle borders;
- one consistent outline SVG icon family;
- soft icon containers only where they help scanning;
- semantic icons matching the earlier mapping;
- no decorative AI-style illustrations;
- no emoji used as Mini App UI graphics;
- no mixing unrelated icon styles;
- no filled blue selected-menu pill.

### Canonical semantic graphics

Keep these mappings stable:

- Главная → home
- Задания → checklist / clipboard
- Награды → gift
- Семья → users
- Сегодня → home/sun depending on context
- Активность → history
- Утро → sunrise
- Чтение → book
- Письмо → pencil-line
- Дом → house
- Порядок → box / sparkles
- Настольная игра → dice
- Время с семьёй → users / heart-family
- Наука → flask
- Монеты → dedicated coin-circle SVG
- Добавить → plus
- Изменить → pencil
- Импорт → upload
- Уведомления → bell
- Роли и доступы → shield/users
- Привязанный Telegram → send / telegram-style paper plane
- Email родителя → mail
- Лимиты → gauge
- Деактивировать → pause-circle
- Активировать → play-circle/check-circle

Do not substitute these with unrelated symbols.

## Bottom navigation

Selected bottom-navigation item:

- blue icon;
- blue text;
- optional slightly stronger font weight;
- transparent/white background;
- no blue/purple filled selected container.

## Telegram Bot

- Every button has exactly one semantic emoji.
- Keep Bot first-need only.
- Do not add complex account settings to Bot.
- Use `👧 Выбрать ребёнка`, not `Сменить ребёнка`.

---

# SFX-001 - Add child deactivation/reactivation

**Priority:** P0  
**Channels:** Mini App + Bot filtering

## Goal

A parent can deactivate a child without deleting history, balance, tasks, rewards or Telegram linkage.

Inactive child:

- disappears from normal Mini App child selectors;
- disappears from Bot child selector;
- cannot receive new first-need actions;
- remains visible under `Неактивные дети`;
- can be reactivated later.

## Mini App

Family → Child details:

```text
Aliska

Основное
Telegram
Лимиты

Статус
Активен

[Деактивировать ребёнка]
```

Confirmation bottom sheet:

```text
Деактивировать Aliska?

Ребёнок исчезнет из обычных списков и Telegram-бота.
История и данные сохранятся.

[Деактивировать]
[Отмена]
```

## Backend

Use explicit status:

```text
ACTIVE
INACTIVE
```

Do not delete the child record.

## Acceptance criteria

- Inactive child is absent from Bot selection.
- Inactive child is absent from normal Mini App selection.
- Historical data is preserved.
- Reactivation restores visibility.
- Forged inactive-child actions fail safely.

---

# SFX-002 - Show last completion/use date and time

**Priority:** P0  
**Channels:** Mini App + Bot

## Tasks

```text
Последнее: сегодня, 08:32
```

Never completed:

```text
Ещё не выполнялось
```

## Rewards

```text
Последнее: 12 авг., 19:40
```

Never used:

```text
Ещё не использовалось
```

## Mini App row example

```text
[sun SVG] Утренний старт                         ⋮
          1 монета · Утро
          Последнее: сегодня, 08:32
```

## Bot

```text
☀️ Утренний старт · 🪙 +1
🕘 Последнее: сегодня, 08:32
```

## Acceptance criteria

- Human localized date/time.
- Same event semantics in Mini App and Bot.
- No raw ISO values.

---

# SFX-003 - Fix selected bottom navigation state

**Priority:** P0  
**Channel:** Mini App

Selected item only changes icon/text color to blue.

Do not use:

- filled blue background;
- blue pill;
- filled tile.

## Acceptance criteria

- Parent and Child bottom nav use the same selected-state rule.
- Text remains readable at all times.

---

# SFX-004 - Localize all Mini App UI to Russian

**Priority:** P0  
**Channel:** Mini App

Audit all:

- navigation;
- buttons;
- settings;
- forms;
- validation;
- errors;
- empty states;
- loading states;
- import UI;
- account UI.

Use centralized localization keys.

---

# SFX-005 - Make Roles and Notifications functional

**Priority:** P0  
**Channel:** Mini App

## Family settings

```text
Роли и доступы >
Уведомления >
```

Both rows must open real Mini App pages.

### Roles & Access

```text
Роли и доступы

Родители
Sasha · Владелец
Maria · Родитель

Дети
Aliska
Lizka
```

### Notifications

```text
Уведомления

[on/off] Выполнено задание
[on/off] Запрошена награда
[on/off] Решение родителя
[on/off] Изменение баланса
```

Do not show unsupported controls.

---

# SFX-005A - Define separate Parent and Child notification settings

**Priority:** P0  
**Channel:** Mini App  
**Depends on:** SFX-005

## Goal

The Notifications screen must be role-aware because Parent and Child notification needs are different.

Do not show one universal list if a notification has no meaning for that role.

## Parent notifications

Path:

```text
Семья → Уведомления → Родитель
```

or, if current parent is implicit:

```text
Семья → Уведомления
```

Parent options:

```text
Уведомления родителя

Задания
[on/off] Ребёнок отметил задание выполненным

Награды
[on/off] Ребёнок запросил награду

Баланс
[on/off] Баланс ребёнка изменился

Аккаунт и семья
[on/off] Родитель принял приглашение
[on/off] Telegram ребёнка привязан
```

Optional channel selector only if supported:

```text
Куда присылать
Telegram
Email
```

Do not show Email channel when parent email is not linked.

## Child notifications

Child settings can expose a separate child notification page only if child notifications are actually supported.

Suggested options:

```text
Уведомления ребёнка

Решения
[on/off] Задание одобрено
[on/off] Задание отклонено
[on/off] Награда одобрена
[on/off] Награда отклонена

Напоминания
[on/off] Новые задания
[on/off] Доступна награда
```

For a Telegram-linked child, delivery is Telegram.

Do not show:
- parent approval-request notifications;
- parent account/family notifications;
- email delivery for child.

## Information architecture

Parent Family settings:

```text
Уведомления >
```

Inside:

```text
Уведомления

[Родитель] >
[Ребёнок: Aliska] >
[Ребёнок: Lizka] >
```

If there is only one configurable role/context, skip this extra level and open the relevant page directly.

## Acceptance criteria

- Parent and Child options differ where semantics differ.
- Child never sees parent-management notification options.
- Parent can configure parent notifications.
- Child notifications are shown only when supported by backend/delivery channel.
- Settings are compact and grouped by meaning.
- No dead toggles are rendered.

---

# SFX-006 - Replace website Task/Reward forms with Mini App-native forms

**Priority:** P0  
**Channel:** Mini App

`Добавить задание`, `Изменить задание`, `Добавить награду`, `Изменить награду` must stay inside Mini App visual language.

## Task form

```text
Новое задание

Название

Графика
[Утро / sunrise SVG ▾]

Монеты
Категория
Группа
Расписание

Дополнительные настройки >

[Сохранить]
```

## Reward form

```text
Новая награда

Название

Графика
[Настольная игра / dice SVG ▾]

Цена
Категория
Группа

Дополнительные настройки >

[Сохранить]
```

## Graphics dropdown

`Графика` is a dropdown/select of **predefined supported semantic graphics**, not free text and not an AI generator.

Each option shows:

```text
[SVG preview] Human-readable Russian name
```

The dropdown must contain a broader curated library so the parent can choose a meaningful graphic without resorting to `Другое`.

### Recommended predefined graphics

General:
- Другое
- Звезда
- Цель
- Галочка
- Календарь
- Часы

Routine / self-care:
- Утро
- Вечер
- Сон
- Зубы
- Душ / гигиена
- Одежда
- Еда
- Вода

Learning:
- Книга
- Чтение
- Письмо
- Карандаш
- Математика
- Калькулятор
- Школа
- Язык
- Музыка
- Наука
- Лаборатория

Home / responsibility:
- Дом
- Уборка
- Порядок
- Коробка / вещи
- Кровать
- Посуда
- Стол
- Растения
- Питомец

Activity:
- Спорт
- Бег
- Велосипед
- Прогулка
- Мяч
- Танцы

Rewards / family / fun:
- Подарок
- Настольная игра
- Семья
- Совместное время
- Кино
- Игры
- Творчество
- Рисование
- Конструктор
- Парк
- Мороженое
- Десерт
- Поездка

Money / progress:
- Монета
- Копилка
- Награда
- Кубок
- Медаль

### Dropdown UX

Do not render 40+ options as one unstructured wall.

Use a native/accessible select or compact picker grouped by category:

```text
Графика
[☀ Утро ▾]

Недавние
- Утро
- Книга
- Дом

Распорядок
- Утро
- Вечер
- Сон
...

Учёба
...

Дом
...

Награды и досуг
...
```

If the current UI component cannot show SVG inside a native `<select>`, use a compact Mini App picker/bottom sheet with:
- SVG preview;
- Russian label;
- category grouping;
- optional search;
- recent graphics first.

Do not use emoji in the actual Mini App picker.

Internally store the stable semantic graphic key, not a display label.

Requirements:

- only graphics from the centralized Mini App semantic icon map;
- same dropdown component for Task and Reward forms, with allowed options filtered by entity type if needed;
- preview selected SVG directly in the field;
- include a deterministic fallback `Другое`;
- do not allow arbitrary emoji/icon strings from this form;
- no upload or AI-generation UI in this iteration.

No website form reuse in visible UX.

---

# SFX-007 - Child settings: Telegram linkage only

**Priority:** P0  
**Channel:** Mini App

## Critical rule

A child does **not** have an email-linked account in this product flow.

Remove Email from child linked accounts/settings.

Child settings must show only Telegram linkage.

## Child page

```text
Aliska

Основное
[Изменить] Имя / аватар

Telegram
[paper-plane SVG] @alice
Статус: привязан

[Перепривязать Telegram]
[Отвязать Telegram]

Лимиты
>

Статус
Активен
[Деактивировать]
```

If Telegram is not linked:

```text
Telegram
Не привязан

[Привязать Telegram]
```

## Acceptance criteria

- No Email row appears in child settings.
- No child email link/unlink action exists.
- Existing child Telegram account can be inspected and safely unlinked/relinked.

---

# SFX-008 - Child invite must link the child's Telegram account

**Priority:** P0  
**Channels:** Mini App + Telegram

## Problem

Child invite must not be a generic family invite. It must create a link used specifically to bind a Telegram account to the selected child.

## Mini App flow

Family → Add child / Child → Telegram:

```text
Привязать Telegram ребёнка

Создайте ссылку и отправьте её ребёнку.
После открытия Telegram-аккаунт будет привязан к Aliska.

[Создать Telegram-ссылку]
```

After creation:

```text
Ссылка готова

[Копировать ссылку]
[Поделиться]
[Создать новую]
```

Status:

```text
Ожидает привязки
```

After child opens link:

```text
Telegram привязан
@alice
```

## Security/backend

Invite/token must be:

- scoped to exact child;
- scoped to family;
- expiring;
- single-use;
- invalid after successful binding;
- safe against foreign-family use.

## Acceptance criteria

- Opening the invite binds the Telegram account to that child.
- It does not create an email account.
- It does not implicitly switch roles for another child.
- Parent sees binding status.

---

# SFX-009 - Parent "My Account": nest email actions inside Email settings

**Priority:** P0  
**Channel:** Mini App

## Goal

`Мой аккаунт` stays compact. Email-specific operations are not shown as separate top-level buttons.

## My Account

```text
Мой аккаунт

Привязанные аккаунты

[Telegram icon] Telegram
                @parentname · Привязан                  >

[mail icon] Email
            s***@example.com · Привязан                 >

Безопасность
...
```

Tap `Email` opens a dedicated nested page.

## Email settings - linked state

```text
Email

s***@example.com
Привязан

[edit icon] Изменить email
[unlink icon] Отвязать email

Безопасность
[key icon] Сменить пароль
```

## Email settings - not linked

```text
Email

Не привязан

[link icon] Привязать email
```

After linking, password/security controls appear only if the actual authentication method supports email/password.

## Change email

```text
Изменить email

Новый email
[________________]

[Продолжить]
```

Use verification according to existing auth/security architecture.

## Unlink email

Require confirmation if Telegram remains a valid login method.

If unlinking email would leave the parent with no valid authentication method, block and explain.

## UX rule

Do **not** place:

- `Изменить email`;
- `Отвязать email`;
- `Сменить пароль`

as sibling actions directly on `Мой аккаунт`.

They belong to:

```text
Мой аккаунт → Email
```

This keeps account settings compact and groups related operations correctly.

## Acceptance criteria

- My Account shows one Email row, not three email-management buttons.
- Email row opens nested Email settings.
- Parent can link email.
- Parent can change linked email.
- Parent can unlink email only when account remains safely accessible.
- Password control only appears inside Email settings when applicable.
- Child settings remain Telegram-only.

---

# SFX-010 - Invite another parent by Email or Telegram

**Priority:** P0  
**Channel:** Mini App

Family → Parents:

```text
Родители

Sasha · Владелец
Maria · Родитель

[Добавить родителя]
```

Flow:

```text
Добавить родителя

Как пригласить?

[mail SVG] По email
[paper-plane SVG] Через Telegram
```

These are two equal first-step options. Do not hide Telegram invite under an overflow menu.

### По email

```text
Email родителя
[________________]

[Отправить приглашение]
```

### Через Telegram

```text
Telegram-приглашение

Создайте одноразовую ссылку и отправьте её второму родителю.

[Создать ссылку]
```

After creation:

```text
Ссылка готова

[Копировать ссылку]
[Поделиться]
[Создать новую]
```

The invite must bind/join the invited account as a **parent**, not as a child.

Email invite:

```text
Email
[________________]

[Отправить приглашение]
```

Telegram invite:

```text
[Создать Telegram-приглашение]
```

Pending status shown compactly.

---

# SFX-011 - CSV import for both Tasks and Rewards

**Priority:** P0  
**Channel:** Mini App

## Entry

Settings → Import, or relevant catalog overflow.

Main import screen:

```text
Импорт из CSV

Что импортировать?

[Задания]
[Награды]
```

These are two distinct import modes.

---

## Task CSV import

```text
Импорт заданий

[Выбрать CSV]

Формат CSV
[Посмотреть формат]
[Копировать описание формата]

Проверка
10 готово
2 ошибки

[Посмотреть ошибки]
[Импортировать 10]
```

## Reward CSV import

Same UX, but reward schema.

---

## Format viewer

Must show all accepted columns and rules.

Example Task format section:

```text
Формат CSV для заданий

Допустимые поля:

name - обязательное, название задания
coins - обязательное, целое число > 0
category - необязательное
group - необязательное
icon - необязательный semantic key
schedule - необязательное
active - необязательное true/false

Пример:
"name","coins","category","group","icon","schedule","active"
"Утренний старт","1","Утро","Ежедневные","sunrise","daily","true"
```

Reward example:

```text
name
price
category
group
icon
active
```

## Copy format

`Копировать описание формата` copies the **full current format description**, including:

- accepted fields;
- required/optional flags;
- allowed values;
- CSV header example;
- one sample row;
- escaping/quote rules if relevant.

Show small inline feedback:

```text
Скопировано
```

No success modal.

## Acceptance criteria

- User chooses Task or Reward import explicitly.
- Format descriptions differ correctly.
- Every accepted field is documented.
- Copy copies the complete description.
- Validation occurs before import.
- No website import UI is opened.

---

# SFX-012 - Add frequency-ranked task/reward group submenu above bottom navigation

**Priority:** P0  
**Channel:** Mini App

## Goal

Keep group filtering compact while making the most useful groups reachable with one tap.

The submenu order is dynamic per current user/context:

```text
[Все] [most used #1] [most used #2] [most used #3] [Ещё]
```

Example Tasks:

```text
[Все] [Утро] [Учёба] [Дом] [Ещё]
```

Example Rewards:

```text
[Все] [Семья] [Развлечения] [Учёба] [Ещё]
```

## Ranking rule

- `Все` is always first.
- After `Все`, show the **most frequently selected groups**.
- Keep at most 3 directly visible ranked groups by default on a 375px viewport.
- `Ещё` opens the remaining less frequently selected groups.
- Do not reorder groups while the submenu is currently open or immediately after every tap. Recalculate ranking at a stable boundary such as next screen entry/session, to avoid UI jumping.
- Persist usage counts per parent/user where feasible. If the current architecture has no user-level preference storage, use a lightweight local preference/read model rather than domain state.
- New/unseen groups with no selection history go under `Ещё` until they gain enough usage, unless there are fewer than 3 ranked groups.

## What counts as "frequently selected"

Count an explicit group filter selection, not passive rendering.

Recommended ranking:

```text
selectionCount DESC
lastSelectedAt DESC
groupName ASC
```

This gives deterministic order.

## `Ещё` UX

Tap:

```text
Ещё
```

opens a compact bottom sheet:

```text
Все группы

[Спорт]
[Творчество]
[Самостоятельность]
[Вечер]
...
```

Selecting a group:

- closes the sheet;
- applies the filter;
- records usage;
- does not navigate away from Tasks/Rewards.

## UX rules

- submenu height approximately 34-40px;
- horizontal overflow should normally not be necessary because only `Все` + 3 ranked groups + `Ещё` are shown;
- selected group uses subtle blue text/border or very light tint;
- no heavy filled blue pill;
- preserve bottom safe area;
- submenu appears only on Tasks/Rewards;
- frequency ranking for Tasks and Rewards is tracked separately;
- group names must not cause the bottom navigation to shift vertically.

## Empty group

```text
В этой группе пока ничего нет
```

Keep compact.

## Acceptance criteria

- `Все` is always first.
- Next items are the most frequently selected groups.
- Less-used groups are available through `Ещё`.
- Ranking is deterministic and does not visibly jump during the current interaction.
- Tasks and Rewards maintain separate usage ranking.
- Filter remains one-tap for common groups.
- Submenu does not cover list content or bottom nav.

---

# SFX-013 - Child coin earning and reward spending limits with quick steppers

**Priority:** P0  
**Channel:** Mini App, enforced across Mini App + Bot

## Goal

Limits must be easy to understand and adjust with one hand.

Child → Limits:

```text
Лимиты

[summary card]
Заработок
Максимум 15 монет в день

Заработок монет
[on/off] Ограничить

Период
[День ▾]

Максимум
[-5] [-1]   15 монет   [+1] [+5]

Награды
[on/off] Ограничить траты

Период
[День ▾]

Максимум
[-5] [-1]   20 монет   [+1] [+5]

[Сохранить]
```

## Summary card

At the top show a compact summary card such as:

```text
Лимиты Aliska

Заработок
Максимум 15 монет / день

Награды
Максимум 20 монет / день
```

This card is read-only and gives the parent an immediate overview.

Do not create a large dashboard card. Keep it compact.

## Stepper behavior

For both earning and reward-spend maximum:

- `-1` decreases by 1 coin;
- `+1` increases by 1 coin;
- `-5` decreases by 5 coins;
- `+5` increases by 5 coins;
- value can also be edited directly as a numeric input if the current component supports it cleanly;
- never allow a negative maximum;
- choose a reasonable technical upper bound and validate it in the shared business layer;
- hold-to-repeat is optional, not required.

Recommended control:

```text
[-5] [-1] [ 15 ] [+1] [+5]
```

Buttons keep minimum 44x44 touch targets.

## Bot

Bot only displays the block reason when relevant. No settings controls in Bot.

## Enforcement

Enforce limits in shared business logic so Mini App, Bot and direct API paths cannot bypass them.

## Acceptance criteria

- Summary shows current earning and reward limits.
- Both maximum fields support ±1 and ±5 controls.
- Buttons are touch-friendly.
- Value never becomes negative.
- Saved value is authoritative across Mini App and Bot.
- Parent receives a clear reason when a limit blocks an action.

---

# SFX-014 - Fix Bot child selection and wording

**Priority:** P0  
**Channel:** Bot

Replace:

```text
Сменить ребёнка
```

with:

```text
👧 Выбрать ребёнка
```

Flow:

```text
👧 Кого показывать?
```

Only ACTIVE children listed.

After selection:

- validate family scope;
- update current Bot child context;
- edit same message back to Parent Home;
- refresh balance/pending count.

---

# SFX-015 ✅ - Preserve earlier Mini App graphics in all new screens

**Priority:** P0  
**Channel:** Mini App

## Problem

New settings mockups introduced several graphics that differ from the earlier Mini App design system.

## Required fix

All new screens in this backlog must reuse the earlier Mini App icon language, not introduce a new graphic style.

Do not introduce:

- filled random icons;
- unrelated icon families;
- emoji in Mini App controls;
- oversized decorative glyphs;
- different stroke weights.

Use the canonical semantic graphics listed at the beginning of this backlog.

## Acceptance criteria

- Visual regression review shows the same icon family across old and new Mini App screens.
- Task/reward/entity icons match the earlier reference semantics.
- New settings screens look like part of the same product.

---

# SFX-016 - Regression and integration tests

**Priority:** P1  
**Depends on:** SFX-001..SFX-015

## Required Mini App tests

- inactive child filtering/reactivation;
- child Telegram-only account settings;
- child Telegram invite binding;
- parent email link/change/unlink;
- parent invite email/Telegram;
- last completion/use timestamp;
- Russian UI;
- selected nav text/icon-only blue state;
- Roles screen;
- Parent Notifications screen;
- Child Notifications screen where supported;
- Mini App-native Task form;
- Mini App-native Reward form;
- dual CSV import;
- format viewer;
- copy complete format description;
- Task group submenu;
- Reward group submenu;
- limits summary card and ±1/±5 steppers;
- visual icon-family consistency.

## Required Bot tests

- `👧 Выбрать ребёнка` works;
- inactive children hidden;
- last-use metadata shown;
- limits enforced;
- no complex account/settings controls added.

---

# Recommended implementation order

1. SFX-015 - restore/canonicalize Mini App graphics
2. SFX-003 - selected bottom-nav styling
3. SFX-004 - Russian localization
4. SFX-001 - deactivate/reactivate child
5. SFX-007 - child Telegram-only settings
6. SFX-008 - child Telegram invite binding
7. SFX-014 - Bot child selection
8. SFX-002 - last completion/use date-time
9. SFX-005 - Roles and Notifications entry points
10. SFX-005A - Parent/Child notification settings
11. SFX-006 - Mini App-native Task/Reward forms
12. SFX-009 - parent email management
13. SFX-010 - parent invites
14. SFX-013 - limits with ±1/±5 steppers
15. SFX-011 - dual CSV import + format/copy
16. SFX-012 - Task/Reward group submenu
17. SFX-016 - regression/integration tests

---

# Definition of Done

This iteration is complete when:

- Mini App graphics match the earlier visual system;
- child settings are Telegram-only;
- child invite binds the child's Telegram account;
- parent can manage email through `Мой аккаунт → Email`, including link/change/unlink and password when applicable;
- CSV import explicitly supports Tasks and Rewards as separate modes;
- CSV format can be viewed;
- all accepted CSV fields are documented;
- full format description can be copied;
- Tasks and Rewards have compact frequency-ranked group filter submenus: `Все`, top-used groups, then `Ещё`;
- child deactivation works;
- last completion/use timestamps work;
- Roles and Notifications work;
- Task/Reward forms are Mini App-native and use predefined semantic-graphics dropdowns;
- limits are enforced across Mini App and Bot;
- Bot child selection works;
- `Выбрать ребёнка` wording is used consistently;
- UX remains compact and readable.
