# Internationalization Follow-up Review - Remediation Backlog

## Goal

Close the remaining confirmed locale leaks after the completed internationalization
backlogs. An English family must receive English Telegram Bot callback and reply
messages, while the Telegram Mini App must format its visible numeric limits using
the persisted family locale rather than the device locale. The public SSR site,
including the marketing shop page, and the existing Mini App catalog keep their
current compact interaction and layout contracts.

## Review basis

- Reviewed completed `I18N-001` through `I18N-010`, the completed
  `I18N-REV-001` through `I18N-REV-003`, and the current i18n, public SSR,
  Telegram Bot, outbox, and Mini App paths.
- The current public route tree has locale-prefixed SSR pages for the home page,
  task feature page, reward-shop feature page, about page, and FAQ. Their
  headings, navigation, metadata, canonical URLs, and alternate URLs resolve
  through the shared web catalog. The legacy `/public/*` files remain explicitly
  noindex compatibility pages.
- Local focused checks passed on 2026-08-25: 31 backend tests across
  `TelegramReplyKeyboardNavigatorTest`, `TelegramMenuBuilderTest`,
  `TelegramMessageResolverTest`, and `TelegramChildOutcomeTextTest`; 25 web
  tests across `config.test.ts`, `LocaleSwitcher.test.ts`, and
  `catalog.test.ts`. The backend test command reports pre-existing Checkstyle
  warnings with zero violations.
- Those checks do not execute the coin-adjustment or parent-request callback
  branches with an EN `TelegramQuickActionResponse`, and do not render the
  Mini App number-limit validation under a locale different from the browser
  locale. The gaps below are confirmed by the currently reachable source paths,
  not inferred from the passing tests.

## Architectural decisions

- `FamilyLocale` on the authenticated family remains the sole language source
  for Telegram and Mini App presentation. `TelegramQuickActionResponse.locale()`
  is the callback/reply boundary representation of that source; no callback path
  may silently use `TelegramLocaleContext`'s thread-default Russian locale.
- Keep `TelegramMessageResolver`, `TelegramCopy`, and `TelegramOutcomeCopy` as
  the Bot's visible-copy owners. Stable callback action codes and localized reply
  labels remain separate: labels may be recognized in both supported languages,
  but the response must be rebuilt in the recipient's resolved locale.
- Use the existing `$i18n.formatNumber()` facade for visible Mini App numeric
  values. Do not call browser-default `toLocaleString()` in a localized surface,
  and do not introduce a component-local `Intl` formatter.
- Preserve the existing Bot/Mini App split: the Bot handles quick actions and
  navigation, while the Mini App owns richer management. No changes to
  persistence, callback signing, public routes, catalog data, or visual density
  belong in this remediation.

## Rejected observations

- The static `/public/*.html` compatibility pages are noindex and are not a
  second indexable public-site implementation; no migration or removal task is
  needed.
- The public task and reward-shop SSR pages already use `PublicShell`,
  `PublicTopNav`, and the shared catalog. No current hard-coded visible copy was
  confirmed there.
- The Mini App's dialogs, localized fields, bottom navigation, and family locale
  selector retain their existing semantic roles and 44px controls. This review
  found no evidence that the completed i18n work introduced overflow or an
  inaccessible primary action. Browser and physical Telegram-client validation
  still require a running deployment and a real client.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | I18N-FOLLOWUP-001 | P1 | - | Callback edits must use the resolved family locale before their assertions can protect the Bot contract. |
| 2 | I18N-FOLLOWUP-002 | P1 | I18N-FOLLOWUP-001 | Reply-keyboard site responses need the same recipient-locale boundary and localized regression coverage. |
| 3 | I18N-FOLLOWUP-003 | P2 | - | The Mini App can reuse its established formatter independently after Bot fixes. |

## I18N-FOLLOWUP-001: Resolve Telegram callback outcomes from the acting family's locale

**Status:** DONE
**Priority:** P1
**Depends on:** -

**Exact scope:** Repair the callback paths for parent coin adjustment, parent
request decisions, and child task requests. Every edited message and retry/menu
button must be created inside the `FamilyLocale` obtained from the current
quick-action view; title fallbacks must be catalog keys, never literals.

**Files:**

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramCoinAdjustmentHandler.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramParentRequestHandler.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramChildActionHandler.java`.
- Modify `apps/backend/src/main/resources/telegram_messages.properties` and `apps/backend/src/main/resources/telegram_messages_ru.properties` only if a missing named fallback key is needed.
- Create `apps/backend/src/test/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramCallbackLocaleTest.java`, or extend the nearest package-private Bot test only when it can exercise all three handlers without reflection.

**Goal:**

An EN family receives EN text and action labels after every supported quick
callback, and an RU family keeps the corresponding Russian text. Missing task
or request titles use localized catalog fallbacks.

### Outcome

The currently reachable `TelegramCoinAdjustmentHandler` constructs copy while
the thread context defaults to Russian, `TelegramParentRequestHandler` falls
back to literal `"Запрос"`, and `TelegramChildActionHandler` falls back to
literal `"Task"`. Those paths no longer bypass the persisted family locale.

### Architectural decision

The callback adapter loads or receives the `TelegramQuickActionResponse` and
sets `TelegramLocaleContext` only for rendering. The business mutation stays in
`TelegramQuickActionService`; do not copy family lookup or locale parsing into
the handlers. Reuse `TelegramMessageResolver` for named title fallbacks and
keep callback payloads unchanged.

### Required changes

1. In the coin-adjustment success and retry/error paths, resolve the relevant
   quick-action snapshot first and construct both edited text and
   `TelegramMenuBuilder` buttons within its locale context. Use the documented
   EN fallback only when no family view can be loaded.
2. Replace the parent-decision literal request fallback and child-task literal
   task fallback with parity-checked `telegram_messages` keys resolved in the
   active callback locale.
3. Preserve existing successful mutations, message IDs, signed callback codes,
   retry behavior, and inline-keyboard row layout.
4. Add focused handler-level tests for EN and RU that cover coin success and
   retry, a parent approval with no title, and a child task result with no task
   title. Assert the edited text and each visible retry/action label; do not
   assert a thread-default locale.

### Out of scope

- Outbox delivery and menu-start localization already covered by completed
  i18n tasks.
- Changing family locale persistence, webhook transport, callback signatures,
  action authorization, or Bot feature-gate behavior.
- Mini App catalog copy or visual redesign.

### Acceptance criteria

- An EN parent coin-adjustment callback edits an English outcome and supplies
  English follow-up buttons; it never emits a Russian default because a worker
  thread has no locale.
- An EN parent request approval without a title uses the English catalog
  fallback, while the equivalent RU action uses the Russian fallback.
- An RU child task callback with a missing task title does not display English
  `Task`; the equivalent EN action remains English.
- Existing callback data remains valid, the underlying quick action is invoked
  once, and failed actions retain an actionable localized retry button.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME=/Users/sash/.sdkman/candidates/java/25.0.2-amzn ./mvnw -B -ntp -Dtest=TelegramCallbackLocaleTest,TelegramMenuBuilderTest,TelegramMessageResolverTest test
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramCoinAdjustmentHandler.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramParentRequestHandler.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramChildActionHandler.java apps/backend/src/main/resources/telegram_messages.properties apps/backend/src/main/resources/telegram_messages_ru.properties apps/backend/src/test/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramCallbackLocaleTest.java
git commit -m "fix(i18n): Localize Telegram callback outcomes"
```

## I18N-FOLLOWUP-002: Localize Telegram reply-keyboard site responses per recipient

**Status:** DONE
**Priority:** P1
**Depends on:** I18N-FOLLOWUP-001

**Exact scope:** Make the `OPEN_SITE` branch in `TelegramReplyKeyboardNavigator`
load the sender's view and create its message and URL-button label within that
view's locale. Keep both recognized RU and EN reply labels compatible.

**Files:**

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramReplyKeyboardNavigator.java`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramReplyKeyboardNavigatorTest.java`.

**Goal:**

Pressing the localized `Open site` reply keyboard item sends a site link whose
body and button label match the recipient family's language.

### Outcome

`handle()` currently detects the reply label correctly in either language but
calls `sendSiteLink()` without loading a family view. That code reads the
ThreadLocal default and can send Russian site copy to an EN user.

### Architectural decision

Use the existing `TelegramQuickActionService.load(telegramUserId, null)` to
obtain `TelegramQuickActionResponse.locale()` and render the response under
`TelegramLocaleContext`. The persisted reply keyboard and `BotNavAction`
compatibility matcher remain unchanged. If a family view cannot be resolved,
do not fabricate a family locale; use the documented neutral EN fallback or
the existing no-response behavior, chosen and asserted consistently.

### Required changes

1. Pass the acting Telegram user ID to the site-link flow and load the existing
   quick-action view before producing visible Bot copy.
2. Render `TelegramCopy.site(...)` and its URL button from that view locale;
   preserve URL normalization and send no external link when configuration is
   blank or invalid.
3. Keep both EN and RU input labels accepted by `BotNavAction.fromLabel()` so
   keyboards sent before a family language change remain usable.
4. Extend the navigator test with EN and RU site-link assertions, including the
   URL, message body, and inline button label. Retain a coverage case for an
   unresolved identity/family fallback.

### Out of scope

- Replacing reply keyboards with inline navigation.
- Changing the public-site URL, Mini App deep-link semantics, or account
  linking.
- Adding a third Bot language.

### Acceptance criteria

- An EN `Open site` reply produces English body and button text with the
  configured normalized public URL.
- A Russian `Открыть сайт` reply continues to produce the Russian equivalent.
- A label sent before switching between supported family languages still routes
  to the site-link action.
- No link is emitted from empty or invalid public-site configuration, and the
  implementation does not rely on the `TelegramLocaleContext` default.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME=/Users/sash/.sdkman/candidates/java/25.0.2-amzn ./mvnw -B -ntp -Dtest=TelegramReplyKeyboardNavigatorTest,TelegramMessageResolverTest test
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramReplyKeyboardNavigator.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/telegram/application/bot/TelegramReplyKeyboardNavigatorTest.java
git commit -m "fix(i18n): Localize Telegram site replies"
```

## I18N-FOLLOWUP-003: Format the Mini App coin limit with the family locale

**Status:** TODO
**Priority:** P2
**Depends on:** -

**Exact scope:** Update the oversized manual coin-adjustment validation in
`TelegramCoinAdjust` to use the shared i18n number formatter, then prove the
localized Mini App error remains usable on a 320px viewport.

**Files:**

- Modify `apps/web/src/lib/components/telegram/TelegramCoinAdjust.svelte`.
- Modify or create focused coverage under `apps/web/tests/e2e/telegram-parent.spec.ts`.
- Modify or create `apps/web/tests/unit/formatters.test.ts` only if the current
  formatter contract needs a direct locale-separator regression assertion.

**Goal:**

The coin-limit validation displays `1,000,000` for English and the matching
Russian grouping for Russian, regardless of the device/browser language.

### Outcome

The component currently interpolates `MAX_ADJUST.toLocaleString()` into a
localized message. That selects the browser's language, so the message can
mix an EN family UI with Russian number punctuation or vice versa.

### Architectural decision

`$i18n.formatNumber()` owns number formatting for the active family locale and
is already available on the component context. Reuse it directly; retain the
existing catalog key, validation constraints, bottom sheet, 44px controls,
and error-alert semantics rather than adding local formatter state or altering
the layout.

### Required changes

1. Replace the browser-default formatting call with the shared i18n formatter
   while preserving the maximum value and current validation order.
2. Add a focused EN/RU regression assertion for the visible validation message.
   Where browser coverage is used, open the existing coin-adjust sheet at a
   320px viewport, submit an oversized whole number, and assert the input,
   localized alert, Cancel, and Save controls remain visible without horizontal
   overflow.
3. Keep the localized error tied to `coin-adjust-error` and do not change the
   adjustment request payload or server validation.

### Out of scope

- Currency conversion, decimal coin values, changes to `MAX_ADJUST`, or new
  locale settings.
- Reworking the bottom-sheet component or the parent dashboard.

### Acceptance criteria

- The same family locale yields the same displayed maximum across browser
  language settings.
- EN and RU messages retain their existing translated copy with their own
  locale-appropriate number grouping.
- On a 320px Mini App viewport, an oversized input leaves the alert and both
  44px actions reachable with no page-level horizontal overflow.
- Valid adjustment payloads and current server-side limits remain unchanged.

### Targeted validation

```bash
cd apps/web && npm run test -- tests/unit/formatters.test.ts
cd apps/web && npm run test:e2e -- tests/e2e/telegram-parent.spec.ts --grep "localized coin adjustment"
cd apps/web && npm run lint
```

### Commit

```bash
git add apps/web/src/lib/components/telegram/TelegramCoinAdjust.svelte apps/web/tests/e2e/telegram-parent.spec.ts apps/web/tests/unit/formatters.test.ts
git commit -m "fix(web): Format coin limits with family locale"
```
