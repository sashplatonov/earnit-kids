# Family Language Settings and Telegram Bot - Review Backlog

## Goal

Restore the family-admin authorization boundary for changing a family's shared language in the workspace, Telegram Mini App, and Telegram bot. A viewer or editor must neither see nor be able to invoke the action; an active family administrator continues to change the one persisted `FamilyEntity.locale` value.

## Architectural decisions

- The authoritative permission is the active `FamilyParentMembershipEntity.permission`, not the broad UI/bot role `parent` or `$appStore.isAdmin`. The existing REST endpoint already enforces this boundary and remains the web mutation path.
- Bot menu visibility and bot mutation authorization must use the same active Telegram identity and its parent-account membership for the linked family. Reuse `FamilyParentMembershipRepository.findByParentAndFamily`; do not trust a reply-keyboard label, `TelegramQuickActionResponse.role`, or a family ID from the incoming message.
- Keep `FamilyEntity.locale` as the only persisted locale and `FamilyRepository.updateLocale` as the bot persistence operation. This task adds no locale values, schema, or Telegram-only preference.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | TASK-FLANG-REV-001 | P1 | - | Fixes the shared authorization regression before adding further language-setting work. |

## TASK-FLANG-REV-001: Enforce family-admin authorization for language changes in every channel

**Status:** DONE
**Priority:** P1
**Depends on:** -

**Exact scope:**

Repair the authorization regression introduced by `TASK-FLANG-001` and `TASK-FLANG-002`: ordinary parent members (`viewer` and `editor`) currently receive the language setting because `TelegramParentFamily` checks `$appStore.isAdmin` and the bot treats every `role == "parent"` as authorized. The bot then calls `FamilyRepository.updateLocale` without checking the linked parent's membership.

**Files:**

- Modify `apps/web/src/lib/components/telegram/TelegramParentFamily.svelte`.
- Modify `apps/web/tests/e2e/telegram-parent.spec.ts`.
- Modify `apps/web/tests/e2e/workspace-access.spec.ts`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/BotKeyboardFactory.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramBotServiceImpl.java` and `apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramMessageUpdateHandler.java` only as needed to pass verified identity/membership context to the keyboard builder.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramReplyKeyboardNavigator.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramReplyKeyboardNavigatorTest.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramBotServiceImplTest.java`.
- Search anchors: `findByParentAndFamily` in `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/infrastructure/persistence/membership/FamilyParentMembershipRepository.java` and `isFamilyAdmin()` in `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/api/resource/FamilyReadResource.java` define the existing active-membership authorization rule.

**Goal:**

Only an active family administrator can discover or change the shared family language from a browser, Mini App, or bot. Editors and viewers retain their normal parent functionality but have no language-management action and cannot mutate the locale by replaying a bot reply label.

### Outcome

The Family settings row renders only when `permission === 'family_admin'`. The bot's parent main keyboard includes Language only for a linked active family administrator; both opening the picker and submitting either fixed choice re-check that same membership before updating the family locale.

### Architectural decision

Keep `$appStore.isAdmin` for its existing broad parent/admin presentation uses, but use the precise membership permission for this family-owned setting. At the bot boundary, resolve the active Telegram identity and then its active parent membership for that identity's family before constructing the language action or calling `updateLocale`; do not expand `TelegramQuickActionResponse` into a second, stale permission source.

### Required changes

1. Gate the Mini App/workspace language setting on `$appStore.permission === 'family_admin'`, preserving the existing `LocaleSwitcher` behavior and the server-side `PUT /api/family/locale` enforcement.
2. Make parent reply-keyboard construction accept the verified language-management capability (or an equivalent narrowly scoped context) so viewer/editor parent keyboards omit Language while family-admin keyboards retain its single-row placement.
3. In `TelegramReplyKeyboardNavigator`, before sending the picker and again before persisting either language choice, resolve the active Telegram parent identity and verify its active `FamilyParentMembershipEntity.Permission.family_admin` membership for the same family. On missing, inactive, viewer, editor, or mismatched membership, leave `FamilyEntity.locale` unchanged and use the existing safe localized error path.
4. Preserve old English/Russian labels, child behavior, reply-keyboard reset, feature gate, and the existing `FamilyRepository.updateLocale` persistence contract. Do not grant authorization from `role == "parent"` alone.
5. Add focused web E2E fixtures that distinguish family_admin from editor/viewer for both `/workspace` and `/telegram`, and backend tests proving only a family admin sees the bot button and can update the locale; assert editor/viewer cannot open the picker or mutate state even when sending a fixed language-choice label directly.

### Out of scope

- Changing permissions for existing task, reward, request, or child-management actions.
- Adding locales, migrations, or per-user Telegram language preferences.
- Changing the REST family-locale authorization rule or redesigning the Family settings UI.
- Reworking unrelated uses of `$appStore.isAdmin`.

### Acceptance criteria

- A family administrator sees the language setting only in Family settings on `/workspace` and `/telegram`, and can still complete the existing locale update/retry flow.
- An editor, viewer, and child see no editable family-language setting in either web surface; a direct `PUT /api/family/locale` remains rejected for those sessions.
- A linked family-admin bot user receives Language in the parent reply keyboard, can select English or Russian, and the confirmation/replacement keyboard immediately use the selected locale.
- A linked viewer/editor bot user receives no Language button. If that user sends any current or previous localized language-action or language-choice label manually, no picker is opened, `FamilyRepository.updateLocale` is not called, and the persisted locale is unchanged.
- A stale, inactive, unlinked, or family-mismatched Telegram identity cannot mutate a locale. Existing child keyboard, `/start`, keyboard-version reset, and duplicate webhook protections remain unchanged.

### Targeted validation

```bash
cd apps/backend && ./mvnw -B -ntp verify -Dtest=TelegramReplyKeyboardNavigatorTest,TelegramBotServiceImplTest
cd apps/web && npm run lint && npm run test && npm run build && npm run test:e2e -- tests/e2e/telegram-parent.spec.ts tests/e2e/workspace-access.spec.ts
```

The backend command proves local authorization and serialized keyboard behavior; the web command proves mocked browser behavior. Verify the refreshed reply keyboard in a real linked administrator, editor, and viewer Telegram chat after deployment separately.

### Commit

```bash
git add apps/web/src/lib/components/telegram/TelegramParentFamily.svelte apps/web/tests/e2e/telegram-parent.spec.ts apps/web/tests/e2e/workspace-access.spec.ts apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/BotKeyboardFactory.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramBotServiceImpl.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramMessageUpdateHandler.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramReplyKeyboardNavigator.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramReplyKeyboardNavigatorTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramBotServiceImplTest.java
git commit -m "fix(auth): restrict family language changes to admins"
```

## Rejected observations

- The REST API was not included as a finding: `FamilyReadResource.updateFamilyLocale` already rejects sessions that are not `family_admin`, so the browser endpoint does not share the bot's authorization bypass.
- The existing successful-path tests are useful, but their fixtures omit membership permissions and therefore cannot prove the required editor/viewer boundary.
