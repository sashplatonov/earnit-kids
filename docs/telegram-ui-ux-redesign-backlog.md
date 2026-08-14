# Telegram Mini App and Bot UX Redesign - Implementation Backlog

## Goal

Redesign the completed Telegram integration without reopening identity, webhook, outbox, or shared-domain work. The Mini App is the complete role-specific interface: a parent decision/management workspace and a child action/reward workspace. The Bot is a fast Telegram-native companion only for actions that finish in one or two taps.

## Non-negotiable product rules

- **The Bot is not a lightweight Mini App.** Keep only frequent Telegram-native actions needing no form or broad data exploration. If two or more conditions are not true, open the appropriate Mini App context.
- **Every Mini App actionable button has a meaningful SVG icon plus an accessible concise label.** Use one central semantic map and one icon family. Do not use emoji, decorative imagery, or AI-generated iconography as Mini App controls.
- **Every Bot inline button has exactly one meaningful emoji.** The emoji is centrally defined, never arbitrary decoration.
- Bot and Mini App reuse the existing shared domain/business actions. Neither channel gets an independent balance, request status, history, or catalogue-management implementation.

## Visual composition and interaction rules

- Avoid card-inside-card layouts and oversized containers. Prefer flat lists, sections, and subtle separators; reserve a larger surface only for an actionable exception or confirmation.
- One primary action must visually dominate each screen. Parent UI optimizes for dense, neutral decision information; child UI optimizes for clear action, understandable progress, and slightly stronger semantic graphics.
- A button has exactly one semantic icon: 20--24px for primary and 16--20px for secondary actions. Do not repeat an icon within the same row.
- Approve, Reject, Done, Get reward, and Switch child are inline. Use a bottom sheet only for a required note or destructive confirmation; never show a success modal.
- List titles use no more than two lines, then truncate while retaining the primary action and providing an accessible details disclosure.

## Architectural decisions

- PostgreSQL, FamilyActionService, current request transitions, and the application outbox remain source of truth. TelegramQuickActionServiceImpl remains the authorization-aware Bot adapter; no Telegram-specific DTO/state machine/direct persistence is added.
- Preserve the /telegram route and verified TelegramRoleResolver. A Bot link may select a validated local view context, but unverified URL state never grants a role or child scope.
- Reuse signed navigation/opaque mutation callbacks, TelegramMenuBuilder, and existing outbox retries. Add only a small presentation mapping/renderer necessary for label and keyboard consistency.
- Parent navigation is exactly Home, Tasks, Rewards, Family; requests belong in Home and activity is contextual. Child navigation is exactly Today, Rewards, Activity. Do not create a universal configurable tab bar.
- Scope is Telegram Mini App and Bot only: do not redesign normal browser pages, change authentication, add dashboards, or add Bot catalogue CRUD.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | TUX-001 | P0 | - | Establish semantic control contracts before surface changes. |
| 2 | TUX-004 | P0 | TUX-001 | Establish shared SVG controls and role-specific visual contracts before feature screens. |
| 3 | TUX-005 | P0 | TUX-004 | Build the parent decision workspace. |
| 4 | TUX-006 | P0 | TUX-004 | Build the child action workspace. |
| 5 | TUX-002 | P0 | TUX-001, TUX-004 | Make parent Bot notification-first and remove duplicate catalog navigation. |
| 6 | TUX-003 | P0 | TUX-001, TUX-002, TUX-004 | Bound child Bot actions and outcome feedback. |
| 7 | TUX-007 | P2 | TUX-002--TUX-006 | Prove boundary, responsive, and release behaviour. |

## TUX-001: Define semantic SVG and Bot emoji vocabularies

**Status:** ✅ Completed  
**Priority:** P0  
**Depends on:** -

### Outcome

Every Mini App and Bot control follows one inspectable semantic vocabulary.

### Architectural decision

Install Lucide Svelte as the one SVG family, exposed through a local TelegramIcon wrapper and semantic map. Add a small TelegramBotEmoji Java value holder for menu/notification presentation only; it does not encode domain rules.

### Files

- Modify apps/web/package.json.
- Create apps/web/src/lib/components/telegram/TelegramIcon.svelte.
- Create apps/web/src/lib/components/telegram/telegramIconMap.ts.
- Create apps/web/src/lib/components/telegram/telegramIconMap.test.ts.
- Create apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramBotEmoji.java.
- Modify apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramMenuBuilder.java.
- Modify apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramOutboxProcessor.java.
- Modify apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramMenuBuilderTest.java.

### Work

1. Inventory all actions under apps/web/src/lib/components/telegram and every TelegramMenuBuilder button.
2. Map approve, reject, done, request reward, coin adjustment, child switch, open app, back, filter, add, edit, archive, delete, task, reward, request, activity, family, and balance to one SVG name.
3. Document narrow text-only exceptions; navigation/mutation buttons never qualify.
4. Centralize Bot labels with semantic markers (🏠, ✅, 🎁, 🎯, 👍, 👎, 🪙, 📜, 👧, 🔄, 📱, ⬅️, ➕, ➖, 🔢, ⏳) and remove scattered literals without changing callback data.
5. Test vocabulary coverage and unchanged callback format.

### Acceptance criteria

- Each Mini App navigation/mutation button has a semantic SVG and programmatic label, with no emoji icon.
- Each Bot inline button has exactly one central semantic emoji; random labels such as “✨ Open” and “🔥 Balance” do not exist.
- The map covers every current Telegram Mini App action and TelegramMenuBuilder control.
- Signed callback payloads and existing scope checks remain compatible.

### Verification

```bash
cd apps/web && npm run lint && npm run test -- telegramIconMap.test.ts
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -Dtest=TelegramMenuBuilderTest test
git diff --check
```

### Commit

```bash
git add apps/web/package.json apps/web/package-lock.json apps/web/src/lib/components/telegram/TelegramIcon.svelte apps/web/src/lib/components/telegram/telegramIconMap.ts apps/web/src/lib/components/telegram/telegramIconMap.test.ts apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramBotEmoji.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramMenuBuilder.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramOutboxProcessor.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramMenuBuilderTest.java
git commit -m "feat(telegram): Add semantic control mappings"
```

## TUX-002: Reduce the parent Bot to a decision inbox

**Status:** ✅ Completed
**Priority:** P0  
**Depends on:** TUX-001, TUX-004

### Outcome

A parent can approve/reject a notification, see the selected child’s balance, make a bounded adjustment, switch child, view five recent events, or open Mini App. Parent catalogue browsing and management disappear.

### Architectural decision

Extend TelegramMenuBuilder, TelegramMenuFlow, TelegramParentRequestHandler, and current outbox sender. Decisions remain in TelegramQuickActionService and FamilyActionService; request notifications reuse existing events/deliveries. Do not add REST endpoints, tables, or a separate Balance view.

### Files

- Modify apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramMenuBuilder.java.
- Modify apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramMenuFlow.java.
- Modify apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramParentRequestHandler.java.
- Modify apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramOutboxProcessor.java.
- Modify apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramBotServiceImpl.java.
- Modify apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramMenuBuilderTest.java.
- Modify apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramBotServiceImplTest.java.
- Modify apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramOutboxProcessorTest.java.

### Work

1. Parent home contains only 🎯 Requests, 🪙 Coins, 📜 Recent, 🔄 Switch child, and 📱 Open Mini App, plus child name, balance, and pending count.
2. Remove catalog, parent Tasks, parent Rewards, and separate Balance from menu construction/routing.
3. Render only pending request cards with 👍 Approve and 👎 Reject; attach the same actions to outbox request notifications. The core approval path must complete without /start or Bot Home.
4. Edit the original notification after decision with title, signed amount, and refreshed balance where available; retain idempotent stale/replay handling.
5. Apply +1, +2, +5, +10, -1, and -2 immediately; require confirmation only for -5 and -10. Disable the clicked action until the authoritative response edits the message; on failure reload the current snapshot and expose Retry rather than showing an unconfirmed balance. Custom amount/full history open Mini App. Recent is capped at five rows; child selection returns to Home.
6. Order Bot home dynamically: pending Requests first with a count; otherwise Coins/Recent lead. Child Bot leads with Tasks while work is active and Rewards when all current work is completed. Open Mini App remains last.

### Acceptance criteria

- Notification → decision completes in one callback and edits the original message; duplicate, expired, or resolved callbacks never mutate twice.
- Parent Bot exposes no catalog, CRUD, settings, invitations, categories, or full history.
- Labels use TelegramBotEmoji and depth is Home → action except coin confirmation.
- The primary approval flow works from a notification without opening Bot Home or Mini App.
- Immediate coin adjustment never leaves an unconfirmed balance on the Bot message after an error.
- Forged foreign-child/request callbacks retain current signing and server scope protection.
- Outbox retry/terminal behaviour survives notification keyboards.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -Dtest=TelegramMenuBuilderTest,TelegramBotServiceImplTest,TelegramOutboxProcessorTest test
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
git diff --check
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramMenuBuilder.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramMenuFlow.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramParentRequestHandler.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramOutboxProcessor.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramBotServiceImpl.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramMenuBuilderTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramBotServiceImplTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramOutboxProcessorTest.java
git commit -m "feat(telegram): Focus parent bot on decisions"
```

## TUX-003: Make the child Bot a short action companion

**Status:** ✅ Completed
**Priority:** P0  
**Depends on:** TUX-001, TUX-002, TUX-004

### Outcome

The child Bot presents a few current tasks, a few requestable rewards, balance, recent results, and Mini App entry. It never becomes a catalogue.

### Architectural decision

TelegramQuickActionServiceImpl remains the sole Bot adapter and its capped response stays the data source. Task/reward requests use existing state transitions; the child never receives parent actions or management.

### Files

- Modify apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramMenuBuilder.java.
- Modify apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramMenuFlow.java.
- Modify apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramBotServiceImpl.java.
- Modify apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramOutboxProcessor.java.
- Modify apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramMenuBuilderTest.java.
- Modify apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramBotServiceImplTest.java.
- Modify apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramOutboxProcessorTest.java.

### Work

1. Child home is exactly ✅ My tasks, 🎁 Rewards, 📜 Recent, and 📱 Open Mini App; balance is in the home message and Requests is not permanent navigation.
2. Show at most five current tasks, at most three affordable/requestable rewards, and at most one closest unavailable reward as motivation. Task action is ✅ Done: name; reward action is 🎁 Get name only when requestable. More items open Mini App.
3. Edit a submitted action to ⏳ Waiting for parent and use current outbox events for approval/rejection feedback and updated balance where applicable.
4. Keep Recent to five presentation-safe rows. Exclude parent requests, coin adjustment, switching, settings, filters, full history, and all CRUD.

### Acceptance criteria

- A child action creates one current request and callback replay cannot change balance twice.
- Outcome feedback contains no raw IDs or backend terminology.
- Parent-only actions cannot be reached through child controls or manually supplied callbacks.
- Full catalogs never paginate in Bot; they open Mini App.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -Dtest=TelegramMenuBuilderTest,TelegramBotServiceImplTest,TelegramOutboxProcessorTest,TelegramQuickActionServiceImplTest test
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
git diff --check
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramMenuBuilder.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramMenuFlow.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramBotServiceImpl.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramOutboxProcessor.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramMenuBuilderTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramBotServiceImplTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramOutboxProcessorTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramQuickActionServiceImplTest.java
git commit -m "feat(telegram): Bound child bot quick actions"
```

## TUX-004: Build the compact role-aware Mini App foundation

**Status:** ✅ Completed
**Priority:** P0  
**Depends on:** TUX-001

### Outcome

Verified /telegram receives one mobile-first visual system and separate Parent/Child shells. Controls are compact, SVG-labelled, and usable through loading, empty, error, retry, and revalidation states.

### Architectural decision

Extend the route, role resolver, app store, and bootstrap revalidation rather than adding a second router/store. Shared primitives remain local to Telegram components; remove visible Refresh controls, not data revalidation.

### Files

- Modify apps/web/src/routes/telegram/+page.svelte.
- Modify apps/web/src/lib/components/telegram/TelegramRoleResolver.svelte.
- Create apps/web/src/lib/components/telegram/TelegramActionButton.svelte.
- Create apps/web/src/lib/components/telegram/TelegramIconButton.svelte.
- Create apps/web/src/lib/components/telegram/TelegramBottomNav.svelte.
- Create apps/web/src/lib/components/telegram/TelegramPageState.svelte.
- Modify apps/web/src/lib/components/telegram/TelegramBalanceHeader.svelte.
- Modify apps/web/src/lib/components/telegram/TelegramParentShell.svelte.
- Modify apps/web/src/lib/components/telegram/TelegramChildShell.svelte.
- Modify apps/web/tests/e2e/telegram-layout.spec.ts.

### Work

1. Replace emoji-only page/tab visuals with shared SVG action/icon buttons, retaining labels, aria-label, focus, disabled, and loading semantics.
2. Implement four parent destinations (Home, Tasks, Rewards, Family) and three child destinations (Today, Rewards, Activity), with hierarchy Parent: pending decision → child/balance → quick actions → recent; Child: today's actions → Done → reward/value → progress.
3. Use 12--16px padding, 56--72px rows, 56--64px safe-area bottom navigation, mapped 20--24px primary/16--20px secondary icons, and 44px targets; apply the no-nested-card rule.
4. Handle Telegram header and bottom safe areas, soft-keyboard open state, viewport resize, and iOS/Android WebView differences; no fixed navigation covers a focused input or primary action.
5. Replace text-only full-screen loading and visible Refresh controls with immediate skeletons, retryable errors, post-mutation invalidation, and existing visibility revalidation.

### Acceptance criteria

- At 320px, 375px, and 430px there is no horizontal overflow and primary actions are visible or reachable with ordinary vertical scroll.
- Parent and child have distinct tab order/labels/counts; child client state cannot reveal parent navigation.
- Every shell action has mapped SVG, visible keyboard focus, and 44 by 44px target minimum.
- Loading, empty, error, retry, and refresh states do not depend on a permanent Refresh button.
- Parent and child use visibly different density/hierarchy while sharing primitives; no card-inside-card layout is introduced.

### Verification

```bash
cd apps/web && npm run lint && npm run test
cd apps/web && npm run test:e2e -- telegram-layout.spec.ts telegram-parent.spec.ts telegram-child.spec.ts --workers=1
cd apps/web && npm run build
git diff --check
```

### Commit

```bash
git add apps/web/src/routes/telegram/+page.svelte apps/web/src/lib/components/telegram/TelegramRoleResolver.svelte apps/web/src/lib/components/telegram/TelegramActionButton.svelte apps/web/src/lib/components/telegram/TelegramIconButton.svelte apps/web/src/lib/components/telegram/TelegramBottomNav.svelte apps/web/src/lib/components/telegram/TelegramPageState.svelte apps/web/src/lib/components/telegram/TelegramBalanceHeader.svelte apps/web/src/lib/components/telegram/TelegramParentShell.svelte apps/web/src/lib/components/telegram/TelegramChildShell.svelte apps/web/tests/e2e/telegram-layout.spec.ts
git commit -m "feat(web): Add Telegram Mini App design system"
```

## TUX-005: Redesign the parent Mini App as a decision workspace

**Status:** ✅ Completed
**Priority:** P0  
**Depends on:** TUX-004

### Outcome

Parent Home begins with selected child, balance, pending approvals, and quick actions. Tasks, rewards, and family are compact management screens; activity is on-demand history.

### Architectural decision

Reuse store snapshots, TelegramRequestList, catalogue modals, child switching, invite lifecycle, and history services. Pending requests keep their current domain/API path; no dashboard aggregate or new request endpoint is introduced.

### Files

- Modify apps/web/src/lib/components/telegram/TelegramParentShell.svelte.
- Create apps/web/src/lib/components/telegram/TelegramParentHome.svelte.
- Modify apps/web/src/lib/components/telegram/TelegramRequestList.svelte.
- Modify apps/web/src/lib/components/telegram/TelegramParentTasks.svelte.
- Modify apps/web/src/lib/components/telegram/TelegramParentRewards.svelte.
- Modify apps/web/src/lib/components/telegram/TelegramChildPicker.svelte.
- Modify apps/web/src/lib/components/telegram/TelegramChildInvite.svelte.
- Modify apps/web/src/lib/components/telegram/TelegramHistoryList.svelte.
- Modify apps/web/src/lib/services/telegramViewState.ts.
- Modify apps/web/src/lib/i18n/index.ts.
- Modify apps/web/tests/e2e/telegram-parent.spec.ts.

### Work

1. Build Home as a decision inbox: child selector/persistent balance; at most two pending requests inline with inline approve/reject; quick coins/activity; then three-to-five recent events. Default here with or without pending work; remaining requests collapse behind See all.
2. Show only PENDING requests in the inbox; success/stale results refresh server data and never create a second local request source.
3. Replace large cards and per-row Edit controls with 56--72px rows. A row tap opens edit/details; overflow contains only secondary operations (duplicate, archive, delete), never duplicate Edit. Delete requires confirmation; Archive and Reject do not unless domain side effects become irreversible. Preserve current modals/permissions.
4. Rename child management to Family; hide invite instructions until Add child/Create invite. The screen has a compact children list, visibly marks the current child, presents balance as secondary metadata, keeps Add child as one compact CTA, and places family settings below the list rather than mixing them into it. Keep the invitation lifecycle.
5. Keep Activity separate and presentation-safe; add an icon-labelled filter only if existing history data supports it.

### Acceptance criteria

- The initial 568px parent viewport includes child, balance, first pending request or explicit zero state, and a next action without giant cards.
- Only pending requests expose approve/reject and every result refreshes deterministically from server data.
- Compact task/reward rows retain current permission/modal behaviour and accessible overflow.
- Child switching reloads its current server snapshot; long text is readable or has accessible disclosure.
- Every parent control uses the central SVG map.
- All Parent copy is resolved through the existing centralized i18n mechanism; no user-facing label is hardcoded in a Telegram component.

### Verification

```bash
cd apps/web && npm run lint && npm run test
cd apps/web && npm run test:e2e -- telegram-parent.spec.ts telegram-consistency.spec.ts telegram-layout.spec.ts --workers=1
cd apps/web && npm run build
git diff --check
```

### Commit

```bash
git add apps/web/src/lib/components/telegram/TelegramParentShell.svelte apps/web/src/lib/components/telegram/TelegramParentHome.svelte apps/web/src/lib/components/telegram/TelegramRequestList.svelte apps/web/src/lib/components/telegram/TelegramParentTasks.svelte apps/web/src/lib/components/telegram/TelegramParentRewards.svelte apps/web/src/lib/components/telegram/TelegramChildPicker.svelte apps/web/src/lib/components/telegram/TelegramChildInvite.svelte apps/web/src/lib/components/telegram/TelegramHistoryList.svelte apps/web/src/lib/services/telegramViewState.ts apps/web/src/lib/i18n/index.ts apps/web/tests/e2e/telegram-parent.spec.ts
git commit -m "feat(web): Redesign Telegram parent workspace"
```

## TUX-006: Redesign the child Mini App for immediate action

**Status:** ⬜ Not started  
**Priority:** P0  
**Depends on:** TUX-004

### Outcome

Today opens with balance and direct Done actions. Rewards show affordability and direct request action; pending, approved, and rejected outcomes are clear without parent/backend terminology.

### Architectural decision

Reuse TelegramChildTasks, TelegramChildRewards, TelegramGroupedCatalog, TelegramRequestSheet, TelegramActionStatus, and current request APIs. The client presents server snapshots only; no optimistic balance or independent completion status.

### Files

- Modify apps/web/src/lib/components/telegram/TelegramChildShell.svelte.
- Modify apps/web/src/lib/components/telegram/TelegramChildTasks.svelte.
- Modify apps/web/src/lib/components/telegram/TelegramChildRewards.svelte.
- Modify apps/web/src/lib/components/telegram/TelegramGroupedCatalog.svelte.
- Modify apps/web/src/lib/components/telegram/TelegramRequestSheet.svelte.
- Modify apps/web/src/lib/components/telegram/TelegramActionStatus.svelte.
- Modify apps/web/src/lib/components/telegram/TelegramHistoryList.svelte.
- Modify apps/web/src/lib/i18n/index.ts.
- Modify apps/web/tests/e2e/telegram-child.spec.ts.
- Modify apps/web/tests/e2e/telegram-layout.spec.ts.
- Modify apps/web/src/lib/components/telegram/telegramIconMap.test.ts.
- Modify apps/web/src/lib/logging/clientLogger.ts.
- Create apps/web/src/lib/logging/clientLogger.test.ts.

### Work

1. Make Today default with compact semantic task rows and direct Done. Available rows are normal, Pending rows are muted with status icon, Completed rows have reduced emphasis, Rejected rows expose retry, and Disabled rows have no primary CTA. Keep all Available and Pending rows, but collapse Completed to two recent rows plus See completed.
2. Put Rewards in its own tab, retain balance in context, render Get reward for eligible items, and a non-actionable locked/remaining-coins state for unavailable items. Define Almost affordable as exactly one cheapest unavailable reward shown as Next goal; all other unavailable rewards are Locked. Preserve current note sheet.
3. Make Activity presentation-safe: task approved, reward approved, balance change; no raw IDs, request types, or parent-only events.
4. Use shared SVG controls and central task/entity icons. Support long titles, disabled states, and errors without emoji or AI illustrations.

### Acceptance criteria

- At 320px Today opens first with balance/actionable rows; enabled Done buttons have SVG/labels, 44 by 44px target, and no horizontal overflow.
- One task/reward action produces one current API request, reconciles server response, and has clear waiting/success/stale/error state after reload.
- Unaffordable rewards cannot submit and expose accessible precise remaining coins.
- Child-visible controls and view state cannot reach Parent, Family, coin adjustment, or management.
- All Child copy is resolved through centralized i18n keys and remains distinct from Parent copy.

### Verification

```bash
cd apps/web && npm run lint && npm run test
cd apps/web && npm run test:e2e -- telegram-child.spec.ts telegram-layout.spec.ts telegram-consistency.spec.ts --workers=1
cd apps/web && npm run build
git diff --check
```

### Commit

```bash
git add apps/web/src/lib/components/telegram/TelegramChildShell.svelte apps/web/src/lib/components/telegram/TelegramChildTasks.svelte apps/web/src/lib/components/telegram/TelegramChildRewards.svelte apps/web/src/lib/components/telegram/TelegramGroupedCatalog.svelte apps/web/src/lib/components/telegram/TelegramRequestSheet.svelte apps/web/src/lib/components/telegram/TelegramActionStatus.svelte apps/web/src/lib/components/telegram/TelegramHistoryList.svelte apps/web/src/lib/i18n/index.ts apps/web/tests/e2e/telegram-child.spec.ts apps/web/tests/e2e/telegram-layout.spec.ts
git commit -m "feat(web): Redesign Telegram child workspace"
```

## TUX-007: Validate boundaries and staged Telegram release

**Status:** ⬜ Not started  
**Priority:** P2  
**Depends on:** TUX-002--TUX-006

### Outcome

Tests prove Bot/Mini App scope, role restrictions, responsive accessibility, and exactly-one cross-channel effects. The release guide separates local evidence from real Telegram-client proof.

### Architectural decision

Extend existing integration tests and Playwright Telegram fixtures. Retain TelegramFeatureGate and the allow-listed rollout; no local test claims BotFather, deployed webhook, or real Telegram WebView proof.

### Files

- Modify apps/backend/src/test/java/com/sashplatonov/earnit/kids/integration/TelegramCrossChannelIntegrationTest.java.
- Modify apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramBotServiceImplTest.java.
- Modify apps/web/tests/e2e/telegram-parent.spec.ts.
- Modify apps/web/tests/e2e/telegram-child.spec.ts.
- Modify apps/web/tests/e2e/telegram-activity.spec.ts.
- Modify apps/web/tests/e2e/telegram-consistency.spec.ts.
- Modify apps/web/tests/e2e/telegram-layout.spec.ts.
- Modify docs/telegram-release-verification.md.

### Work

1. Assert allowed Parent/Child Bot menus and that excluded flows have neither visible buttons nor executable callback routes.
2. Cover notification-first decisions, duplicate callbacks, scope enforcement, coin confirmation, child feedback, and Mini App link context.
3. Cover 320/375/430px tab order, focus, touch geometry, Telegram header/bottom safe areas, soft-keyboard/viewport resize, overflow, loading/empty/error/retry, stale reconciliation, and long-content disclosure.
4. Test child action in Mini App/Bot → parent decision in Bot/Mini App → one balance/history outcome → refreshed child feedback under normal PostgreSQL/H2 coverage.
5. Add stable screenshot baselines for Parent Home, Parent Tasks, Child Today, and Child Rewards; assert Bot keyboard/menu output in TelegramMenuBuilder unit tests because Playwright cannot render Telegram.
6. Fail a regression test for unmapped Mini App actions, raw Bot emoji outside its mapping, Bot buttons without semantic emoji, or tabs outside their role-specific sets.
7. Record privacy-safe UX validation through the existing client logger: event name, role/channel, timing, and outcome only for parent_request_approved, parent_request_rejected, child_task_done, child_reward_requested, bot_open_mini_app, and child_switch. Never log content or family/child/Telegram/callback IDs.
8. Verify skeleton renders synchronously on entry, cached shell render is not blocked by a full-screen loader, perceived mutation feedback begins within 300ms, and independent initial reads are parallelized where current APIs permit.
9. Update the release guide with local gates, deployment/allow-list/flags, webhook checks, and manual Parent/Child Telegram-client validation.

### Acceptance criteria

- Tests fail if a parent Bot catalog, child parent-only action, text-only Mini App action, or non-semantic Bot emoji reappears.
- Simulated callback replay/double tap gives one request decision, balance effect, and history entry.
- Specified viewports retain reachable controls, visible focus, and no horizontal overflow.
- Screenshot and contract tests protect hierarchy, compact density, semantic controls, and role-specific navigation.
- UX validation logging contains no content or identifying values; timing tests do not depend on remote telemetry.
- The guide explicitly distinguishes local checks, remote CI/deploy, and real Telegram-client proof.

### Verification

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
cd apps/web && npm run lint && npm run test && npm run build
cd apps/web && npm run test:e2e -- telegram-auth.spec.ts telegram-parent.spec.ts telegram-child.spec.ts telegram-activity.spec.ts telegram-consistency.spec.ts telegram-layout.spec.ts --workers=1
git diff --check
```

### Commit

```bash
git add apps/backend/src/test/java/com/sashplatonov/earnit/kids/integration/TelegramCrossChannelIntegrationTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramBotServiceImplTest.java apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/TelegramMenuBuilderTest.java apps/web/tests/e2e/telegram-parent.spec.ts apps/web/tests/e2e/telegram-child.spec.ts apps/web/tests/e2e/telegram-activity.spec.ts apps/web/tests/e2e/telegram-consistency.spec.ts apps/web/tests/e2e/telegram-layout.spec.ts apps/web/src/lib/components/telegram/telegramIconMap.test.ts apps/web/src/lib/logging/clientLogger.ts apps/web/src/lib/logging/clientLogger.test.ts docs/telegram-release-verification.md
git commit -m "test(telegram): Cover redesigned channel flows"
```

## Final quality gates

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
cd apps/web && npm run lint && npm run test && npm run build
cd apps/web && npm run test:e2e -- telegram-auth.spec.ts telegram-parent.spec.ts telegram-child.spec.ts telegram-activity.spec.ts telegram-consistency.spec.ts telegram-layout.spec.ts --workers=1
git diff --check
```

These commands are local/static/browser-fixture evidence only. Before production enablement, follow docs/telegram-release-verification.md for deployed webhook, staged-family flags, and manual checks in real Parent and Child Telegram clients.
