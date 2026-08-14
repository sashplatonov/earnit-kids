# Prompt for AI coding agent - EarnIt Kids settings/family follow-up v4

The previous Mini App and Telegram Bot redesign backlogs are completed.

Implement the updated backlog:

`earnit-miniapp-bot-settings-followup-backlog-v4.md`

Before changing UI, open:

`earnit-miniapp-bot-settings-target-reference-v4.html`

Treat this HTML as the visual/composition reference for this iteration.

## Critical visual rule

The new settings screens must use the **same Mini App graphic language as the earlier Mini App redesign**.

Do not invent another icon style.

Use:
- one outline SVG family;
- the canonical semantic mappings from the backlog;
- the same stroke weight and icon-container treatment;
- no emoji as Mini App graphics;
- no AI-style decorative illustrations.

If a new icon in the HTML conflicts with the earlier Mini App semantic mapping, follow the backlog's canonical mapping.

## Required product corrections

1. Child settings are Telegram-only. Remove child Email linkage.
2. Child invite specifically binds the selected child's Telegram account.
3. Parent `Мой аккаунт` shows one nested `Email` row. Inside `Мой аккаунт → Email` support:
   - link email;
   - change email;
   - unlink email;
   - change password when email/password auth applies.
   Do not show these as top-level sibling buttons on My Account.
4. CSV import has two explicit modes:
   - Задания;
   - Награды.
5. CSV import includes:
   - `Посмотреть формат`;
   - complete list of accepted fields;
   - required/optional rules;
   - example header and row;
   - `Копировать описание формата` copying the full description.
6. Tasks and Rewards get a compact horizontal group-filter submenu immediately above bottom navigation.
   It must be ordered: `Все` → up to 3 most frequently selected groups → `Ещё`.
   Less-used groups live in `Ещё`; Tasks and Rewards maintain separate rankings.
   Recalculate at a stable boundary so chips do not visibly jump after each tap.
7. The group submenu is not a new full navigation layer.
8. Selected bottom navigation uses blue icon/text only, never a filled selected background.
9. All Mini App visible UI is Russian.
10. Inactive children are hidden from normal Mini App/Bot selectors.
11. Bot uses `👧 Выбрать ребёнка`.
12. Preserve existing shared business logic, authorization, Telegram signing/outbox, role scope and idempotency.
13. Do not add complex account administration to the Telegram Bot.
14. Do not open website forms from Mini App.
15. Task/Reward `Графика` is a dropdown of predefined semantic SVG graphics from the centralized icon map. No free-text icon entry, emoji, upload, or AI-generated graphics.

## UX compactness

Typical settings depth:

`Семья → раздел → форма`

Avoid:
- giant cards;
- nested cards;
- long explanatory blocks on primary screens;
- desktop-style settings pages.

Implement in the order defined by the backlog.


## Additional v4 requirements

16. Expand the predefined Task/Reward graphics picker substantially. Group semantic SVG choices by category and keep recently used graphics easy to reach. No emoji or arbitrary icon strings.
17. Notifications must have role-aware content. Parent and Child notification options differ; do not render a universal meaningless list.
18. Parent invitation must visibly offer two first-step choices: `По email` and `Через Telegram`.
19. Child Limits screen must include a compact summary card, e.g. `Максимум 15 монет / день`.
20. Both earning and reward-spending maximum fields must have touch-friendly `-5`, `-1`, `+1`, `+5` controls, with shared-layer validation.
