# Family Language Settings and Telegram Bot - Implementation Backlog

## Goal

Make the family language an explicit family-admin setting in both the desktop workspace and Telegram Mini App, instead of a workspace-level control. Let a linked family administrator change the same persisted family language from the Telegram bot menu, with the bot immediately continuing in the newly selected language.

## Architectural decisions

- `FamilyEntity.locale` remains the sole persisted source of truth. Reuse `PUT /api/family/locale` for web clients and `FamilyRepository.updateLocale` only behind the verified Telegram parent identity for bot updates; do not introduce a per-device, per-chat, or duplicate locale preference.
- `LocaleSwitcher.svelte` remains the web control that owns the existing update/retry/reload behavior. It is rendered only as an administrator-owned item of `TelegramParentFamily.svelte`; the first-time language-setup gate remains in place and child/read-only surfaces do not gain an editable control.
- The bot remains a bounded quick-action surface. Add a reply-keyboard entry that opens a compact language choice and processes the choice only for a linked parent with an active family; update the existing reply keyboard in the selected locale rather than adding a new callback protocol or making the Mini App the source of truth.
- Keep all user-facing web copy in web i18n catalogs and bot copy in `telegram_messages*.properties`. Existing `en` and `ru` are the only selectable values.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | TASK-FLANG-001 | P1 | - | Places the existing persisted family-language control at its correct shared desktop/Mini App ownership point. |
| 2 | TASK-FLANG-002 | P1 | - | Extends the bot's verified parent quick-action flow to mutate that same existing source of truth. |

## TASK-FLANG-001: Move the family language control into Family settings

**Status:** TODO
**Priority:** P1
**Depends on:** -

**Exact scope:**

Render the existing family-managed language control as another entry of the parent-only Family settings surface, in both `/workspace` desktop web and `/telegram` Mini App. Remove the separate workspace-level display that currently appears before the parent/child shells; retain the mandatory initial setup flow for families without a locale.

**Files:**

- Modify `apps/web/src/lib/components/telegram/TelegramParentFamily.svelte`.
- Modify `apps/web/src/lib/features/workspace/WorkspaceRoleResolver.svelte`.
- Modify `apps/web/src/lib/features/workspace/ParentWorkspaceShell.svelte`.
- Modify `apps/web/src/lib/features/workspace/ChildWorkspaceShell.svelte`.
- Modify `apps/web/src/routes/workspace/+page.svelte`.
- Modify `apps/web/src/lib/i18n/messages/en/app.ts`.
- Modify `apps/web/src/lib/i18n/messages/ru/app.ts`.
- Modify `apps/web/tests/e2e/telegram-parent.spec.ts`.
- Search anchor: `familyLocale` in `apps/web/tests/e2e/workspace-access.spec.ts` for the existing family-admin and initial-setup coverage to extend or adjust without changing unrelated access assertions.

**Goal:**

A family administrator finds the current language and can switch it from Family settings in either web surface. A non-admin or child cannot change it, and the prior global workspace switcher is no longer rendered.

### Outcome

The Family tab presents a labelled, semantic language-settings row alongside its existing settings. Opening it exposes the existing English/Russian choices, reflects the active family locale, handles request failure with its current retry behavior, and reloads into the selected locale after a successful save.

### Architectural decision

Compose `LocaleSwitcher` inside `TelegramParentFamily` rather than duplicating locale-save state or calling the API from a new component. Remove the obsolete `showFamilyLocale` prop path from the workspace shells/resolver so the Family settings component is the single editable placement; do not remove the `/workspace` initial-language gate because it handles an unset persisted locale before the regular workspace is available.

### Required changes

1. Add a parent-admin-only language item to the existing `familySettings` list. Use the established `TelegramIcon`/setting-row visual contract, translated title and explanatory/current-language copy, and an accessible dialog or inline expansion consistent with the nearby settings interactions.
2. Render `LocaleSwitcher` in family-managed editable mode only in that item. Preserve its API call, error alert, retry focus, and reload semantics; do not introduce a browser-locale fallback for authenticated family state.
3. Remove the pre-shell `LocaleSwitcher` rendering and the now-unused `showFamilyLocale`/`familyAdmin` plumbing from the normal workspace route and role/shell components. Keep the setup gate and its editable switcher for an administrator when `languageSetupRequired` is true.
4. Add matching English and Russian messages for the Family settings row and any dialog/expanded-state text. Ensure keyboard operation, visible focus, `aria-expanded`/dialog labelling as applicable, and controls of at least 44 by 44 CSS pixels.
5. Extend focused browser coverage with mocked authenticated data: desktop `/workspace` and Mini App `/telegram` expose the setting to a family admin, the global placement is absent, a child/read-only session has no editable language action, successful `PUT /api/family/locale` reloads/uses the updated locale, and a failed update exposes the retry path without horizontal overflow at 320px.

### Acceptance criteria

- For a family admin, `/workspace` and `/telegram` show the language entry only inside Family settings; no standalone family-language switcher appears above the workspace shell.
- Opening the entry exposes English and Russian with the persisted active locale selected. Selecting the other language sends the existing `PUT /api/family/locale` payload and the reloaded page renders translated UI in that locale.
- A failed update leaves the selected state unchanged, announces an error, and gives keyboard focus to the retry action; retry submits the same requested locale.
- A child and a non-family-admin parent cannot access an editable family language control. An administrator of a family with no locale still reaches the existing first-time setup before the regular workspace.
- At 320px wide, the Family settings row and opened language control remain within the viewport, all interactive controls are at least 44 by 44 CSS pixels, and the control is usable via keyboard with visible focus.

### Targeted validation

```bash
cd apps/web && npm run lint && npm run test && npm run build && npm run test:e2e -- tests/e2e/telegram-parent.spec.ts tests/e2e/workspace-access.spec.ts
```

### Out of scope

- Adding locales beyond English and Russian.
- Changing public-page/browser-only locale selection.
- Changing family-language persistence, its REST contract, or database schema.
- Moving unrelated Family settings or redesigning the tab bar.

### Commit

```bash
git add apps/web/src/lib/components/telegram/TelegramParentFamily.svelte apps/web/src/lib/features/workspace/WorkspaceRoleResolver.svelte apps/web/src/lib/features/workspace/ParentWorkspaceShell.svelte apps/web/src/lib/features/workspace/ChildWorkspaceShell.svelte apps/web/src/routes/workspace/+page.svelte apps/web/src/lib/i18n/messages/en/app.ts apps/web/src/lib/i18n/messages/ru/app.ts apps/web/tests/e2e/telegram-parent.spec.ts apps/web/tests/e2e/workspace-access.spec.ts
git commit -m "feat(web): move family language into settings"
```

## TASK-FLANG-002: Add a parent language menu to the Telegram bot

**Status:** TODO
**Priority:** P1
**Depends on:** -

**Exact scope:**

Add a localized reply-keyboard action for a linked parent to choose English or Russian, persist the selection to that parent's family, and replace the parent reply keyboard and confirmation copy with the newly selected locale. Do not offer the action to children or unlinked users.

**Files:**

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/BotKeyboardFactory.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/BotNavAction.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramReplyKeyboardNavigator.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramBotServiceImpl.java` only if constructor/wiring needs the existing family repository passed to the navigator.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramCopy.java`.
- Modify `apps/backend/src/main/resources/telegram_messages.properties`.
- Modify `apps/backend/src/main/resources/telegram_messages_ru.properties`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramReplyKeyboardNavigatorTest.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramBotServiceImplTest.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/i18n/TelegramCatalogContractTest.java` if it is the catalog-completeness guard for the added keys.

**Goal:**

A linked parent can select the family language from the bot and immediately receives the refreshed parent menu and confirmation in that language. The selection changes the same family locale used by the web and Mini App.

### Outcome

The parent persistent keyboard has a localized Language action. Its menu presents one English and one Russian choice in separate rows; choosing a supported different locale updates the verified parent's family and sends a fresh parent reply keyboard in the new language. Selecting the current locale is safe and does not create an inconsistent state.

### Architectural decision

Extend the existing reply-keyboard label recognition and `TelegramReplyKeyboardNavigator`, with `FamilyRepository` as the persistence extension point already used by message handling. Resolve the parent/family from the verified Telegram identity and current quick-action view before saving; do not trust free-form locale text, direct family IDs from incoming messages, callback data, or a new Telegram-only settings store.

### Required changes

1. Add localized bot catalog keys and `TelegramCopy` accessors for the parent Language action, language-picker prompt, English/Russian choices, and successful/no-op/error feedback. Add the action to `BotNavAction` so both current and previous-language labels are recognized.
2. Add the Language button to the parent reply keyboard as its own row or other non-crowded layout, respecting the bot's existing role-specific keyboard construction. Do not add it to the child keyboard.
3. Extend `TelegramReplyKeyboardNavigator` to recognize the parent Language action, load the linked parent's current view/locale, and send a localized two-choice reply keyboard. Model the choices as fixed localized labels mapped to `FamilyLocale.en` and `FamilyLocale.ru`; do not parse arbitrary user input.
4. On a supported choice, verify that the sender still resolves to a linked parent and an existing family, call the existing repository update operation for that family, and send confirmation plus a newly built parent main keyboard within `TelegramLocaleContext.with(selectedLocale, ...)`. If persistence fails or the identity/view is unavailable, leave state unchanged and return a localized safe response without exposing IDs or throwing a null-dependency error.
5. Update production constructor wiring and narrow test constructors only as needed to pass the repository into the navigator. Preserve webhook idempotency, rollout gating, and the existing `/start` keyboard-reset version behavior.
6. Add focused tests for both locales: parent keyboard contains the action, each language choice persists the expected `FamilyLocale` for the authenticated parent family, the response and replacement keyboard use the selected locale, repeated/current selection is safe, children cannot reach or apply the action, unlinked/missing-family paths do not mutate, and old Russian/English navigation labels still work.

### Acceptance criteria

- A linked parent receives a persistent Language control with the rest of the parent bot menu; a linked child and unlinked account do not.
- The language picker contains exactly English and Russian as separate reachable choices, localized according to the current family language.
- Selecting English or Russian updates only the authenticated parent's existing family through the current repository path. The subsequent confirmation and parent main keyboard use the selected locale immediately and retain all existing parent actions.
- A stale/unlinked child, non-parent identity, missing family, unsupported/free-form message, or repository update failure does not alter any family locale and yields a safe localized outcome without server error leakage.
- Existing `/start`, reply-keyboard navigation, signed inline callbacks, feature-gate behavior, and webhook duplicate-update handling remain covered and unchanged.

### Targeted validation

```bash
cd apps/backend && ./mvnw -B -ntp verify -Dtest=TelegramReplyKeyboardNavigatorTest,TelegramBotServiceImplTest,TelegramCatalogContractTest
```

Local Maven verification proves the serialized bot requests and persisted-update path only. After deployment, verify in a real linked parent chat that Telegram renders the refreshed keyboard and that a fresh `/start` uses the selected language; this is separate Telegram-client/deployment proof.

### Out of scope

- Per-user bot language preferences.
- Child-controlled family language changes.
- Telegram BotFather command registration or deployment configuration changes.
- New database columns, migrations, locale values, or changes to Mini App authentication.

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/BotKeyboardFactory.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/BotNavAction.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramReplyKeyboardNavigator.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramBotServiceImpl.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramCopy.java apps/backend/src/main/resources/telegram_messages.properties apps/backend/src/main/resources/telegram_messages_ru.properties apps/backend/src/test/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramReplyKeyboardNavigatorTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramBotServiceImplTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/i18n/TelegramCatalogContractTest.java
git commit -m "feat(backend): add bot family language menu"
```
