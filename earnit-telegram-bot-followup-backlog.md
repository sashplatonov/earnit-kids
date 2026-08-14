# EarnIt Kids - Telegram Bot UX Follow-up Backlog

## Status and scope

This is a **new follow-up backlog** for the Telegram Bot after the previous Telegram UX backlog was completed.

Do **not** reopen completed identity, webhook, outbox, shared-domain, Mini App routing or role-resolution work unless a task below explicitly requires a small compatibility adjustment.

The source of truth for this backlog is:

1. the current Telegram Bot screenshots after the completed redesign;
2. the product rules below;
3. `earnit-telegram-bot-target-reference.html` as the visual/composition reference for Bot message structure and keyboards.

The current implementation is the baseline codebase, **not** the target UX.

## Goal

Make the Telegram Bot a fast, Telegram-native companion for first-need actions only.

Parent Bot:
- decision inbox;
- quick coin actions;
- switch child;
- short recent activity;
- open Mini App.

Child Bot:
- a few current tasks;
- Done;
- a few relevant rewards;
- request reward;
- recent results;
- open Mini App.

The Bot must not become a duplicate Mini App.

## Non-negotiable Bot rules

- Every inline/menu button has exactly one meaningful emoji.
- Emoji must be centralized in one semantic map, not scattered literals.
- Keep navigation shallow: normally Home → Action or Notification → Action.
- Prefer editing the active message after callbacks instead of sending a new navigation message every time.
- No separate Balance screen.
- No Parent Tasks/Rewards catalog browsing.
- No Parent catalog CRUD.
- No Child management/settings.
- No full history pagination in Bot.
- No raw IDs, backend terminology or technical request-state names in user-facing text.
- Bot actions reuse existing business/application actions and current authorization/scope checks.
- Duplicate/stale callbacks must never mutate twice.
- When a flow needs broad exploration, a form, or management, open the exact Mini App context.

---

# BUX-001 - Rebuild Parent Bot Home as a compact decision menu

**Status:** ✅ Implemented  
**Priority:** P0  
**Depends on:** -

## Problem visible in current screenshots

Current Parent Bot Home still behaves like a menu tree:

```text
Child · Aliska
Requests
Balance · 22
Coins
Recent
Open Mini App
```

Problems:

- child context is rendered as a button/submenu instead of context;
- Balance is duplicated as a separate route;
- Requests, Coins and Recent have equal weight even when requests need attention;
- menu depth is higher than needed;
- `Open Mini App` looks like another ordinary menu item;
- the layout does not strongly prioritize first-need parent actions.

## Target

Message:

```text
👧 Aliska
🪙 22 монеты

🎯 Требуют внимания: 2
```

Keyboard:

```text
🎯 Запросы        🪙 Монеты
📜 Последние      🔄 Сменить ребёнка

📱 Открыть приложение
```

When pending count is zero:

```text
👧 Aliska
🪙 22 монеты

✅ Сейчас ничего не требует внимания
```

Keyboard remains compact.

## Required changes

- Remove `Child · Aliska` as a navigation button.
- Remove separate Balance button/route.
- Show current child and balance in the message body.
- Put `🎯 Запросы` first when pending > 0.
- If pending = 0, keep the menu but do not create an empty Requests screen automatically.
- `📱 Открыть приложение` is always the last row and opens Parent Mini App Home.
- `🔄 Сменить ребёнка` is a direct action, not a Child submenu.

## Acceptance criteria

- Parent Home contains only first-need actions.
- No Tasks/Rewards catalog buttons.
- No Balance route.
- No Child submenu.
- Every button contains exactly one semantic emoji.
- Menu depth from Home is one level for Requests, Coins, Recent, Switch child.

---

# BUX-002 - Make Parent approval notification-first

**Status:** ✅ Implemented  
**Priority:** P0  
**Depends on:** BUX-001

## Problem

The strongest Telegram-native workflow is not the menu. Parent approvals should finish directly from the notification.

## Target task-completion notification

```text
👧 Aliska выполнила:

☀️ Утренний старт
🪙 +1 монета
```

Keyboard:

```text
👍 Одобрить
👎 Отклонить
```

After approval, edit the same message:

```text
✅ Одобрено

☀️ Утренний старт
🪙 +1 монета
Баланс: 23
```

Buttons disappear.

Reward request:

```text
👧 Aliska хочет награду:

🎲 Королева настолки
🪙 2 монеты
```

Keyboard:

```text
👍 Одобрить
👎 Отклонить
```

## Required changes

- Reuse the current request/outbox notification delivery.
- Attach semantic action buttons directly to the notification.
- One callback completes approval/rejection.
- Edit original message after decision.
- Stale/duplicate result edits message to a safe resolved state instead of sending a stack of new messages.

## Acceptance criteria

- Parent can approve/reject without `/start`.
- Duplicate callback never changes balance twice.
- Resolved notification has no active approve/reject buttons.
- User-facing text contains no raw request type/id.
- Exactly one emoji per button.

---

# BUX-003 - Reduce Requests screen to a bounded decision queue

**Status:** ✅ Implemented  
**Priority:** P0  
**Depends on:** BUX-002

## Problem visible in current screenshots

Current Requests screen can become another submenu with `Back`. When there are no requests, opening a dedicated screen adds no value.

## Target

If requests exist:

```text
🎯 Запрос 1 из 2

👧 Aliska

☀️ Утренний старт
🪙 +1 монета
```

Keyboard:

```text
👍 Одобрить      👎 Отклонить
➡️ Следующий
🏠 Главное меню
```

After decision, automatically render the next pending request if one exists.

If no requests:

```text
✅ Нет запросов, ожидающих решения
```

Keyboard:

```text
🏠 Главное меню
📱 Открыть приложение
```

## Required changes

- No long request list.
- Process one request at a time.
- Auto-advance after decision.
- No full request history in Bot.
- No approved/rejected items in this queue.

## Acceptance criteria

- Queue only includes pending requests.
- User never needs Requests → list → request → action.
- One action resolves one request.
- After the last request, render completion state.

---

# BUX-004 - Rebuild Parent Coins as a true quick action

**Status:** ✅ Implemented  
**Priority:** P0  
**Depends on:** BUX-001

## Problem visible in current screenshots

The Coins screen is functional but still menu-like and uses a separate Back route.

## Target

```text
👧 Aliska
🪙 Баланс: 22

Добавить монеты
```

Keyboard:

```text
➕ +1      ➕ +2
➕ +5      ➕ +10

Снять монеты
➖ -1      ➖ -2
➖ -5      ➖ -10

🔢 Другая сумма
🏠 Главное меню
```

Rules:
- +1/+2/+5/+10 execute immediately;
- -1/-2 execute immediately;
- -5/-10 require confirmation;
- custom amount opens exact Mini App coin-adjustment context.

After immediate action edit same message:

```text
✅ Добавлено 5 монет
🪙 Баланс: 27
```

Keep keyboard available for another quick action.

## Acceptance criteria

- No separate Balance screen.
- No extra confirmation for positive fixed amounts.
- Negative high-value adjustments are protected.
- Same message is edited after action.
- Failed mutation restores/refetches real balance and shows retry-safe feedback.

---

# BUX-005 - Simplify Parent Recent

**Status:** ✅ Implemented  
**Priority:** P1  
**Depends on:** BUX-001

## Problem

Current Recent is useful but should remain a preview, not full history.

## Target

```text
📜 Последние события · Aliska

☀️ +1 · Утренний старт
Сегодня, 08:32

📖 +2 · Книжная искра
Вчера, 18:40

🎁 -2 · Королева настолки
13 августа, 20:15
```

Keyboard:

```text
📱 Полная история
🏠 Главное меню
```

## Required changes

- Max 5 rows.
- Human-readable event labels only.
- Full history opens Parent Mini App Activity/History context.
- Do not paginate in Bot.

## Acceptance criteria

- Max five presentation-safe events.
- No raw event/request names from backend.
- Full history is a Mini App deep link.

---

# BUX-006 - Flatten Parent child switching

**Status:** ✅ Implemented  
**Priority:** P0  
**Depends on:** BUX-001

## Problem visible in current screenshots

Current child context is a submenu:

```text
Child · Aliska
Tasks
Rewards
Switch child
Back
```

This whole branch should not exist for Parent Bot.

## Target

```text
🔄 Выберите ребёнка
```

Keyboard:

```text
👧 Aliska · 22
👧 Lizka · 5

🏠 Главное меню
```

After selection, immediately render Parent Home for that child.

## Required changes

- Remove Parent `Child` submenu.
- Remove Parent Tasks and Rewards navigation from Bot.
- Switching child updates only validated local Bot context.
- Current child must be server-authorized.

## Acceptance criteria

- No Parent child submenu remains.
- No Parent task/reward catalog callback route is reachable.
- Selected child returns directly to Home.

---

# BUX-007 - Rebuild Child Bot Home around first-need actions

**Status:** ✅ Implemented  
**Priority:** P0  
**Depends on:** -

## Problem visible in current screenshots

Child Bot currently starts with another menu branch:

```text
Child · Aliska
Tasks
Rewards
Switch child
Back
```

This exposes parent-like navigation and unnecessary depth.

## Target

Message:

```text
👋 Aliska
🪙 22 монеты
```

Keyboard:

```text
✅ Мои задания
🎁 Награды
📜 Последние

📱 Открыть приложение
```

No:
- switch child;
- coins adjustment;
- requests management;
- settings;
- management.

## Acceptance criteria

- Child Home has exactly the bounded first-need feature set.
- Balance is context, not a route.
- Every button has exactly one meaningful emoji.
- No parent-only callback can be reached manually.

---

# BUX-008 - Make Child Tasks action-first and capped

**Status:** ✅ Implemented  
**Priority:** P0  
**Depends on:** BUX-007

## Problem visible in current screenshots

Current Child Tasks shows a full-looking list and Back. It should be a short action companion, not a catalog.

## Target

```text
✅ Мои задания

☀️ Утренний старт
🪙 +1

📖 Книжная искра - 15 минут
🪙 +2

✏️ Красивые 5 строк
🪙 +1
```

Keyboard can be rendered per item or as bounded action buttons, for example:

```text
✅ Готово: Утренний старт
✅ Готово: Книжная искра
✅ Готово: Красивые 5 строк

📱 Все задания
🏠 Главное меню
```

Rules:
- show max 5 current actionable tasks;
- prioritize Available, then Pending;
- do not show a long completed catalog;
- more opens Child Mini App Today.

After Done:

```text
⏳ Утренний старт
Ждём решения родителя
```

## Acceptance criteria

- One callback creates one current request.
- Replay cannot create another request/effect.
- No full catalog pagination.
- Pending task no longer shows active Done action.

---

# BUX-009 - Make Child Rewards bounded and motivating

**Priority:** P0  
**Depends on:** BUX-007

## Problem visible in current screenshots

Rewards currently look like another full list. The Bot should show only relevant choices.

## Target

```text
🎁 Награды
🪙 Баланс: 22

🎲 Королева настолки · 2
👨‍👧 20 минут только со мной · 4

🧪 Следующая цель:
Домашняя лаборатория · 30
Не хватает 8 монет
```

Keyboard:

```text
🎁 Получить: Королева настолки
🎁 Получить: 20 минут только со мной

📱 Все награды
🏠 Главное меню
```

Rules:
- max 3 affordable/requestable rewards;
- optionally show exactly one nearest unavailable reward as motivation;
- unavailable reward is not an active claim button;
- full catalog opens Child Mini App Rewards.

## Acceptance criteria

- Affordable rewards come first.
- Only requestable rewards have claim buttons.
- No reward catalog pagination in Bot.
- One nearest goal maximum.

---

# BUX-010 - Improve Child approval/rejection feedback

**Priority:** P0  
**Depends on:** BUX-008, BUX-009

## Target approval message

```text
🎉 Утренний старт одобрен

🪙 +1 монета
Баланс: 23
```

Keyboard:

```text
✅ Мои задания
🎁 Награды
```

Reward approved:

```text
🎉 Награда одобрена

🎲 Королева настолки
```

Rejected task:

```text
❌ Утренний старт не одобрен
```

Keyboard:

```text
✅ Мои задания
🏠 Главное меню
```

## Rules

- No backend terminology.
- No raw request state.
- Feedback should explain the outcome and next action.
- Do not spam multiple follow-up messages for one outcome if editing/reusing is possible.

---

# BUX-011 - Normalize Bot navigation and message editing

**Priority:** P0  
**Depends on:** BUX-001, BUX-007

## Goal

Stop the Bot from behaving like a web navigation tree.

## Rules

Prefer:

```text
Home → Action
Notification → Action
```

Avoid:

```text
Home → Child → Tasks → Task → Action → Back → Back
```

Use:
- `editMessageText`;
- `editMessageReplyMarkup`;

where Telegram semantics allow.

Send a new message only for real notifications/events, not routine menu navigation.

## Acceptance criteria

- Routine navigation does not create a long stack of Bot messages.
- Back is replaced by `🏠 Главное меню` only where a return control is actually needed.
- No screen has Back solely because the old tree required it.

---

# BUX-012 - Centralize Bot emoji and copy vocabulary

**Status:** ✅ Implemented  
**Priority:** P0  
**Depends on:** -

## Goal

Every Bot button uses one consistent semantic emoji.

## Suggested map

```text
HOME = 🏠
TASKS = ✅
REWARDS = 🎁
REQUESTS = 🎯
APPROVE = 👍
REJECT = 👎
COINS = 🪙
RECENT = 📜
CHILD = 👧
SWITCH = 🔄
MINI_APP = 📱
ADD = ➕
REMOVE = ➖
CUSTOM = 🔢
WAITING = ⏳
SUCCESS = ✅
NEXT = ➡️
```

## Required changes

- No raw emoji literals spread across handlers/menu builders.
- Centralize user-facing button labels or label composition.
- Keep callback payloads unchanged unless required by a specific task.

## Acceptance criteria

- Every inline/menu button contains exactly one emoji.
- Semantic mapping is deterministic.
- Regression test fails for a button without mapped emoji.

---

# BUX-013 - Add exact Mini App deep links from Bot

**Priority:** P1  
**Depends on:** BUX-001, BUX-007

## Parent

- `📱 Полная история` → Parent Activity/History context
- `🔢 Другая сумма` → Parent coin adjustment context
- `📱 Открыть приложение` → Parent Home

## Child

- `📱 Все задания` → Child Today
- `📱 Все награды` → Child Rewards
- `📱 Открыть приложение` → Child Today/Home

## Rules

- Role and child scope are server validated.
- URL/start parameters never grant authorization by themselves.

---

# BUX-014 - Bot empty/error/stale states

**Priority:** P1  
**Depends on:** BUX-002..BUX-010

## Empty

```text
✅ Нет запросов, ожидающих решения
```

```text
✅ На сегодня активных заданий нет
```

```text
🎁 Сейчас нет доступных наград
```

## Stale

```text
ℹ️ Этот запрос уже обработан
```

Then reconcile current state.

## Error

```text
⚠️ Не удалось выполнить действие
Попробуйте ещё раз
```

Keyboard:

```text
🔄 Повторить
🏠 Главное меню
```

## Acceptance criteria

- No raw exception/HTTP/backend text.
- Empty state does not open an unnecessary submenu.
- Stale callback is safe and non-mutating.

---

# BUX-015 - Bot regression and boundary tests

**Priority:** P1  
**Depends on:** BUX-001..BUX-014

## Required test assertions

Parent Bot must not expose:
- Tasks catalog;
- Rewards catalog;
- catalog CRUD;
- Balance screen;
- Child submenu;
- full history pagination;
- settings/invitations/categories.

Child Bot must not expose:
- coins adjustment;
- child switching;
- requests management;
- parent actions;
- CRUD;
- full catalogs.

Test:
- notification → decision;
- duplicate callback;
- stale callback;
- foreign child/request scope;
- fixed coin adjustment;
- high negative confirmation;
- child Done;
- child reward claim;
- child feedback;
- exact Mini App deep link context;
- centralized semantic emoji coverage.

---

# Implementation order

1. BUX-012 - centralized emoji/copy vocabulary
2. BUX-001 - Parent Home
3. BUX-006 - Parent child switch
4. BUX-002 - notification-first approvals
5. BUX-003 - bounded Requests queue
6. BUX-004 - quick Coins
7. BUX-005 - Recent
8. BUX-007 - Child Home
9. BUX-008 - Child Tasks
10. BUX-009 - Child Rewards
11. BUX-010 - Child outcome feedback
12. BUX-011 - navigation/message-edit normalization
13. BUX-013 - Mini App deep links
14. BUX-014 - empty/error/stale states
15. BUX-015 - regression/boundary tests

---

# Definition of Done

This follow-up backlog is complete when:

- Parent Bot is a decision inbox, not a Mini App copy.
- Child Bot is a short action companion, not a catalog.
- Parent approval works directly from notification.
- Balance is never a separate Bot screen.
- Parent Tasks/Rewards catalogs are gone.
- Parent Child submenu is gone.
- Child has no parent-only controls.
- Bot catalogs are capped and hand off to Mini App.
- Every Bot button has exactly one semantic emoji.
- Bot navigation is shallow.
- Routine callbacks edit the current message instead of creating navigation spam.
- duplicate/stale callbacks are safe.
- complex or broad flows deep-link into the correct Mini App context.
- resulting message/keyboards visually follow `earnit-telegram-bot-target-reference.html`.
