# Internationalization Completion Review - Remediation Backlog

## Goal

Close confirmed gaps in the completed internationalization backlog so that an
invalid language tag cannot silently select a supported family language, a
family administrator is told when a language update fails, and every Telegram
Bot delivery path uses the recipient family's language.

## Review basis

- Reviewed completed `I18N-001` through `I18N-010` tasks in
  `docs/internationalization-backlog.md`, commits `3a7d8964` through
  `a113a7a4`, and their current frontend, API, persistence, bot, and outbox
  paths.
- Local focused backend proof passed on 2026-08-25:
  `FamilyLocaleResourceTest`, `TelegramOutboxProcessorTest`,
  `TelegramMenuBuilderTest`, and `TelegramMessageResolverTest` (34 tests).
  This does not prove EN bot delivery because the existing outbox/menu tests
  assert Russian literals and do not exercise the EN recipient contract.

## Architectural decisions

- `FamilyLocale` remains the only persisted family-language source of truth.
  Locale parsing at HTTP and browser boundaries must accept only the supported
  BCP-47 language or regional tags (`en`, `en-US`, `ru`, `ru-RU`); prefix
  matching must not turn arbitrary input such as `english` or `russian` into a
  valid choice.
- The locale update resource owns the machine-readable error contract. A
  rejected locale returns a named error code and safe field parameter; the UI
  displays catalog-owned copy and keeps the setup control usable rather than
  reloading on an unsuccessful response.
- Telegram presentation is resolved at the delivery recipient boundary. Bot
  navigation identifiers remain stable ASCII codes, while labels and all
  message bodies are resolved from `telegram_messages*.properties` under the
  recipient family's `FamilyLocale`; neither a `ThreadLocal` default nor
  legacy Russian constants may be a source of visible copy.

## Rejected observations

- The legacy `/public/*.html` files are intentionally retained compatibility
  pages and each inspected page has `meta name="robots" content="noindex, nofollow"`;
  no duplicate-indexing task is needed.
- The focused Maven command emits existing Checkstyle warnings but reports zero
  Checkstyle violations and succeeds. This review does not add an unrelated
  style-cleanup task.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | I18N-REV-001 | P1 | - | Restore the documented locale-input and API-error contract before callers rely on it. |
| 2 | I18N-REV-002 | P1 | I18N-REV-001 | Make every Bot and outbox presentation path use that persisted family locale. |
| 3 | I18N-REV-003 | P2 | I18N-REV-001 | Keep the shared language selector recoverable when the server rejects or cannot process an update. |

## I18N-REV-001: Enforce exact supported locale tags at the family-language boundary

**Status:** DONE
**Priority:** P1
**Depends on:** -

**Exact scope:** Align frontend and backend locale normalization with the ADR,
and make `PUT /api/family/locale` return a specific stable problem when its
input is unsupported. This task changes no persisted locale values or family
authorization rules.

**Files:**

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/i18n/BackendLocaleSupport.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/domain/model/FamilyLocale.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/api/resource/FamilyReadResource.java`.
- Modify `apps/web/src/lib/i18n/config.ts`.
- Modify or create focused tests beside `FamilyLocaleResourceTest`,
  `BackendValidationLocaleResolverTest`, and `apps/web/tests/unit/config.test.ts`.

**Goal:** `en`, `en-US`, `ru`, and `ru-RU` normalize to the two supported
values, while arbitrary prefix-sharing input is rejected consistently before a
family setting changes.

### Outcome

An administrator submitting `english`, `russian`, or `xx-YY` receives a 400
problem with a named unsupported-locale code and the existing family locale is
unchanged. Browser URL/header handling does not interpret these strings as a
supported locale.

### Architectural decision

Keep normalization in the existing boundary helpers and use `FamilyLocale` in
Java beyond that boundary. Return a safe parameter such as `field=locale` and
a stable `UNSUPPORTED_LOCALE` code instead of the hard-coded
`"Unsupported locale"` detail; retain localized `detail` only as compatible
presentation, never as client logic.

### Required changes

1. Replace `startsWith("en")` / `startsWith("ru")` acceptance with exact
   language-tag parsing/validation shared by `BackendLocaleSupport`,
   `FamilyLocale`, and the frontend `normalizeLocale` function.
2. Preserve accepted regional forms and the safe `en` fallback for absent or
   unsupported request-precedence inputs; do not broaden the persisted DB
   constraint or add user-specific locale state.
3. Make the locale update resource map unsupported input to its named stable
   error code and safe parameters without exposing raw submitted input.
4. Add regression tests for supported base/regional tags, prefix-sharing
   invalid values, and a rejected update that leaves the stored locale intact.

### Out of scope

- Adding `sr` or another supported language.
- Changing anonymous public URL precedence, auth permissions, or existing
  families' `NULL` onboarding semantics.

### Acceptance criteria

- `en`, `en-US`, `ru`, and `ru-RU` resolve to their documented supported
  locale on both server and web boundaries.
- `english`, `russian`, `enough`, `ruble`, and `xx-YY` never select `en` or
  `ru` merely by prefix.
- `PUT /api/family/locale` for unsupported input returns 400 with
  `UNSUPPORTED_LOCALE`, a safe field parameter, and no persisted change.
- The existing family-admin authorization and valid locale update behavior
  remain unchanged.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME=/Users/sash/.sdkman/candidates/java/25.0.2-amzn ./mvnw -B -ntp -Dtest=FamilyLocaleResourceTest,BackendValidationLocaleResolverTest test
cd apps/web && npm run test -- --run tests/unit/config.test.ts
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/{i18n,family} apps/backend/src/test/java/com/sashplatonov/earnit/kids/{i18n,family} apps/web/src/lib/i18n/config.ts apps/web/tests/unit/config.test.ts
git commit -m "fix(i18n): Validate supported family locale tags"
```

## I18N-REV-002: Resolve every Telegram Bot delivery and reply label per family locale

**Status:** TODO
**Priority:** P1
**Depends on:** I18N-REV-001

**Exact scope:** Remove remaining user-visible Russian/English literals and
legacy label constants from bot menus, `/start`, notification composition, and
outbox resolution edits. Preserve callback payloads, delivery idempotency, and
the existing family-based recipient model.

**Files:**

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramCopy.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramOutcomeCopy.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramMenuText.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/BotNavAction.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramMessageUpdateHandler.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramRequestResolutionText.java` and
  `TelegramChildOutcomeText.java` in the same directory if required to retain
  one resolver path.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/notification/TelegramNotificationComposer.java` and
  `TelegramOutboxProcessor.java`.
- Modify `apps/backend/src/main/resources/telegram_messages.properties` and
  `apps/backend/src/main/resources/telegram_messages_ru.properties`.
- Modify focused tests under
  `apps/backend/src/test/java/com/sashplatonov/earnit/kids/telegram/application/{bot,notification}/`.

**Goal:** Every message, keyboard, notification fallback, and resolution edit
sent to an EN family is English and the corresponding RU family result is
Russian, including paths that operate after the initiating request is gone.

### Outcome

The current direct literals in `TelegramCopy.requestNotification`,
`TelegramOutcomeCopy.child*`, `TelegramMenuText`,
`TelegramMessageUpdateHandler`, and `TelegramNotificationComposer.generic`
cannot bypass the locale resolver. The outbox's immediate recheck edit uses
the same recipient-family resolution as its initial delivery.

### Architectural decision

Keep `TelegramMessageResolver` and the existing bundles as the sole bot-copy
owner. Resolve a locale from the delivery event's family at send/edit time;
for interactive commands, use the loaded view/identity's family locale before
building visible content. `BotNavAction` may recognize localized reply labels
for compatibility, but it must not own a visible default Russian label.

### Required changes

1. Move remaining bot message fragments, menu headings, balance/goal text,
   child outcomes, `/start` fallbacks, and generic outbox copy into parity
   checked bundle keys with named parameters.
2. Replace legacy visible constants and the Russian-default `ThreadLocal`
   fallback with explicit locale-aware methods; preserve stable callback and
   command identifiers.
3. Route both initial outbox messages and the post-send request-resolution
   edit through the same recipient-family locale resolution. Do not derive the
   language from request headers, Telegram hints, or the event creator.
4. Add EN and RU regression coverage for child/parent reply navigation,
   `/start` without children, request-created notification, child outcome,
   generic fallback, and the immediate resolved-message edit. Assert callback
   data is identical across locales.

### Out of scope

- New bot commands, a user-selectable bot locale, callback-signing changes,
  delivery schema changes, or translation of task/reward/child user content.
- Changing the Telegram client parse mode or unrelated bot UX.

### Acceptance criteria

- For the same event and stable callback ID, EN-family output is English and
  RU-family output is Russian for menus, reply keyboards, notifications,
  generic fallbacks, and resolution edits.
- The `REQUEST_RESOLVED` and immediate post-send resolution paths do not fall
  back to a request-thread/default language.
- `/start` for a linked parent with no children and an unlinked user has
  catalog-owned copy appropriate to the resolved locale/fallback.
- Tests fail if any covered output reintroduces the former hard-coded Russian
  or English presentation, while user-created names remain verbatim text.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME=/Users/sash/.sdkman/candidates/java/25.0.2-amzn ./mvnw -B -ntp -Dtest=TelegramMessageResolverTest,TelegramMenuBuilderTest,TelegramBotServiceImplTest,TelegramOutboxProcessorTest test
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram apps/backend/src/main/resources/telegram_messages*.properties apps/backend/src/test/java/com/sashplatonov/earnit/kids/telegram
git commit -m "fix(i18n): Localize Telegram delivery paths"
```

## I18N-REV-003: Keep family-language updates recoverable in the shared selector

**Status:** TODO
**Priority:** P2
**Depends on:** I18N-REV-001

**Exact scope:** Make the existing `LocaleSwitcher` show and recover from a
failed family-managed update. Do not redesign the selector or change public
visitor locale navigation.

**Files:**

- Modify `apps/web/src/lib/components/LocaleSwitcher.svelte`.
- Modify `apps/web/src/lib/services/api.ts` only to expose an existing-style
  typed PUT result for the family-locale endpoint, if the component cannot use
  an established helper directly.
- Create or modify a focused component/unit test under `apps/web/tests/` using
  the existing test convention for service/component behavior.
- Modify `apps/web/src/lib/i18n/messages/{en,ru}/common.ts` or `app.ts` only
  for the failure/retry copy that is actually rendered.

**Goal:** A failed language save stays on the current page, explains the
failure in the active catalog language, restores the control, and lets the
administrator retry.

### Outcome

`LocaleSwitcher.handleChange` no longer calls `location.reload()` after an
HTTP 400/401/403/500 response or network failure. It reloads only after a
successful family-locale update, so setup cannot appear to have succeeded when
the server retained `NULL` or the previous value.

### Architectural decision

Use the existing API-result/error-code normalization rather than inspecting
localized `detail`. Keep state local to the shared selector: it owns busy,
failure, and retry presentation, while the backend remains the source of
truth for the selected family locale.

### Required changes

1. Consume a structured update result and distinguish success from HTTP and
   network failure before navigating/reloading.
2. Render a localized status/alert with a retryable control, preserve visible
   focus, and return the buttons to an enabled state after failure.
3. Add tests for successful reload/navigation and a rejected/network update
   that displays the localized error without reloading.

### Out of scope

- Changing the family-locale API authorization or locale parsing.
- Per-user language choices, public URL selector behavior, or new toast
  infrastructure.

### Acceptance criteria

- A successful family-language update reloads once and reflects the server
  response after the new session/bootstrap.
- A failed update keeps the selector visible and retryable, with a localized
  error announced through appropriate status semantics.
- The selector continues to meet its 44px target and accessible-name contract
  in both EN and RU.

### Targeted validation

```bash
cd apps/web && npm run test -- --run tests/unit/LocaleSwitcher.test.ts && npm run lint
```

### Commit

```bash
git add apps/web/src/lib/components/LocaleSwitcher.svelte apps/web/src/lib/services/api.ts apps/web/src/lib/i18n/messages/{en,ru} apps/web/tests/unit/LocaleSwitcher.test.ts
git commit -m "fix(web): Recover from family language update failures"
```
