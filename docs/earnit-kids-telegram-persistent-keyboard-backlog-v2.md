# EarnIt Kids Telegram Bot - Persistent Navigation Keyboard

## Goal
Move global bot navigation out of chat history into a persistent Telegram reply keyboard.

The chat history should contain events and contextual actions only. Global navigation must stay available near the composer and must not be pushed upward by new child requests.

## Mandatory reference
Use `earnit-kids-telegram-persistent-keyboard-reference.html`.

Reference blocks:
- `Reference A - Persistent navigation`
- `Reference B - Pending child request`
- `Reference C - Resolved child request`

Telegram native rendering is the source of truth for exact styling. The HTML defines information architecture and button placement.

## UX-01 - Replace inline main menu with persistent reply keyboard ✅

Current global menu:
- Запросы
- Монеты
- Последние
- Выбрать ребёнка
- Открыть приложение

Stop sending this navigation as an inline-keyboard message.

Persistent layout:

```text
[ 🎯 Запросы ] [ 🌕 Монеты ]
[ 📜 Последние ] [ 👧 Выбрать ребёнка ]
[ 📱 Приложение ] [ 🌐 Сайт ]
```

Важно: `🌐 Сайт` не должен занимать отдельную строку на всю ширину.
Расположить его рядом с `📱 Приложение` в нижнем ряду как две кнопки примерно равной ширины.

Use ReplyKeyboardMarkup with intent:
- `is_persistent = true`
- `resize_keyboard = true`
- `one_time_keyboard = false`

`/start` must install/restore the keyboard.

Do not regenerate a global-menu message after child events or navigation responses.

## UX-02 - Keep contextual request actions inline ✅

Persistent keyboard = global navigation only.

Keep request-specific actions attached to the request message:

```text
👧 Aliska выполнила:
🏠 Сделать одно полезное дело по дому
🌕 +2

[ 👍 Одобрить ] [ 👎 Отклонить ]
```

Do not move approve/reject to the persistent keyboard.

Callbacks must remain tied to the concrete request ID.

## UX-03 - Resolved requests remain in history ✅

When pending changes to approved/rejected/cancelled/deleted:
- do not delete the historical Telegram message;
- edit its text/status;
- remove its inline action buttons;
- never leave stale approve/reject buttons.

Example:

```text
✅ Одобрено

🏠 Сделать одно полезное дело по дому
🌕 +2
Баланс: 27
```

No inline buttons remain.

## UX-04 - Open Mini App from persistent navigation ✅

`📱 Приложение` should open the existing Mini App.

Prefer a Telegram `KeyboardButton` with Mini App/Web App capability in the private bot chat.

Add a second bottom-row navigation button:

`🌐 Сайт`

It should open the public EarnIt Kids website.

Target bottom row:

```text
[ 📱 Приложение ] [ 🌐 Сайт ]
```

Do not make `🌐 Сайт` a full-width row unless Telegram/client limitations make the two-button row impossible.

For `🌐 Сайт`, prefer a direct URL-capable Telegram button where supported by the chosen keyboard type. If a persistent reply-keyboard button cannot directly open an external URL in the current Telegram Bot API/library, handle `🌐 Сайт` with the smallest possible fallback:
- one tap on `🌐 Сайт`;
- bot responds with one compact message containing a single URL button;
- do not rebuild or duplicate the global menu.

Do not post another global-menu card just to expose either the Mini App or website.

## UX-05 - Centralize navigation routing ✅

Handle these stable actions:
- `🎯 Запросы`
- `🌕 Монеты`
- `📜 Последние`
- `👧 Выбрать ребёнка`
- `📱 Приложение`
- `🌐 Сайт`

Do not scatter raw string comparisons across handlers.

Recommended internal model:

```text
BotNavAction
- REQUESTS
- COINS
- RECENT
- SELECT_CHILD
- OPEN_APP
- OPEN_SITE
```

Keep visible label -> action mapping in one place.

## UX-06 - Stop posting the old home/menu card ✅

After `Монеты`, `Последние`, `Запросы`, etc.:
- send only the requested content;
- do not append the old `Aliska / 25 монет / Сейчас ничего...` global-menu card;
- do not duplicate navigation buttons in chat.

The persistent keyboard is now the navigation shell.

## UX-07 - Child selection ✅

`👧 Выбрать ребёнка` may open a contextual inline selector.

Preferred flow:
1. send/select child in a temporary contextual message;
2. select child using inline buttons;
3. update active child;
4. edit/resolve that selector message where practical;
5. leave persistent main keyboard unchanged.

Do not replace the main persistent keyboard with a child list.

## UX-08 - State/concurrency safety ✅

For every request callback:
1. load request by request ID;
2. verify status is still pending;
3. apply transition atomically/idempotently;
4. edit the Telegram message to the resulting state;
5. remove inline action buttons.

A stale callback must not apply the operation twice.

Do not infer the target request from the last chat message or currently selected child.

## UX-09 - Migration from old inline navigation ✅

If a stored main-menu `message_id` exists:
- on `/start` or next normal interaction, remove its old inline navigation markup where safe;
- do not delete historical event messages.

If the old menu cannot be identified safely:
- leave history untouched;
- simply stop creating new copies.

Do not scan/delete chat history.

## UX-10 - Role-aware keyboard factory ✅

Build the persistent keyboard from user role/capabilities.

For parent use the reference layout.

Do not expose parent-only navigation to child users.

Recommended structure:

```text
BotKeyboardFactory
  parentMainKeyboard(...)
  childMainKeyboard(...)
```

Do not construct ReplyKeyboardMarkup ad hoc in unrelated handlers.

## Tests

### Unit
- parent keyboard rows/order are correct;
- text maps to correct internal nav action;
- request approval/rejection remains request-ID based;
- terminal requests cannot transition again;
- resolved request markup has no approve/reject buttons.

### Integration
1. `/start` shows persistent navigation.
2. Receive 3 child requests.
3. Navigation remains available without scrolling.
4. Approve request #2.
5. Only request #2 changes.
6. Its inline buttons disappear.
7. Other pending requests stay actionable.
8. `Монеты` returns balance without a global-menu card.
9. `Приложение` opens the Mini App.
10. `Сайт` opens the public website, or uses the defined one-tap fallback if direct URL launch is not supported by the persistent keyboard.
11. Re-enter chat and verify keyboard persists/restores.

## Definition of Done

### Persistent global navigation
```text
[ 🎯 Запросы ] [ 🌕 Монеты ]
[ 📜 Последние ] [ 👧 Выбрать ребёнка ]
[ 📱 Приложение ] [ 🌐 Сайт ]
```

### Chat history
Contains:
- task/reward events;
- request-specific inline actions;
- status/balance updates;
- resolved historical requests.

Does not contain repeatedly generated global navigation cards.

Core rule:

**global navigation = persistent reply keyboard**

**contextual request action = inline keyboard attached to request**


## UX-11 - Bottom row sizing for `Приложение` and `Сайт` ✅

The bottom row must contain two compact navigation buttons:

```text
[ 📱 Приложение ] [ 🌐 Сайт ]
```

Requirements:
- both buttons share the row;
- neither button spans the full keyboard width;
- use short labels to avoid oversized buttons;
- prefer approximately equal width;
- keep the row visually consistent with the two rows above;
- do not use `Открыть приложение` if `Приложение` is sufficient, because the shorter label improves balance with `Сайт`.

Acceptance criteria:
- the third row visually reads as a normal two-column navigation row;
- `Сайт` does not appear as a dominant CTA;
- no extra fourth row is created just for the website link.
