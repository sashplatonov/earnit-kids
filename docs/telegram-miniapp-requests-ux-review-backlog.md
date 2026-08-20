# Telegram Mini App Request Lists UX - Review Remediation Backlog

## Review scope

Reviewed the completed tasks in `docs/telegram-miniapp-requests-ux-backlog.md` against commits `e304192c`, `876a21bd`, `3fd521ca`, and `d9b16afa`, their current Svelte components, and their Playwright coverage.

The list-surface migration and child icon policy are implemented. The remaining work closes three unfulfilled acceptance criteria: visible keyboard focus for parent decisions and child task requests, plus regression coverage that rejects every form of nested row card.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | P1-1 | P1 | - | Makes parent decision controls keyboard-visible as the completed parent task promised. |
| 2 | P1-2 | P1 | - | Makes child task request controls keyboard-visible as the completed child task task promised. |
| 3 | P1-3 | P1 | P1-1, P1-2 | Turns the list-surface and focus contracts into regressions that cannot silently return. |

## P1-1: Make parent request decisions visibly focusable

**Priority:** P1  
**Depends on:** -

### Outcome

Keyboard users can always see when either pending parent request decision is focused, without changing the approve/reject workflow, layout, or busy state.

### Architectural decision

- Source of truth: browser focus state.
- Owner: `TelegramRequestList.svelte`, which renders the approve/reject controls.
- Extend: the existing Telegram focus-ring language (`outline: 3px solid #80aaff; outline-offset: 2px`).
- Remove/retire: reliance on a focus selector in an ancestor Svelte component; scoped ancestor CSS does not style this component's buttons.

### Files

- Modify `apps/web/src/lib/components/telegram/TelegramRequestList.svelte`.
- Modify `apps/web/tests/e2e/telegram-parent.spec.ts`.

### Work

1. Add the established `:focus-visible` ring locally to the parent list's action and retry buttons.
2. Add a keyboard-driven Playwright assertion that the focused approve and reject controls retain a non-transparent visible outline at the 320px fixture.
3. Preserve the existing ARIA labels, disabled state while the mutation is pending, and separate action-row geometry.

### Acceptance criteria

- [x] Tabbing to Approve or Reject gives the focused control a visible focus ring that meets the established Telegram Mini App style. ✅
- [x] The focus ring does not cause horizontal overflow or overlap the adjacent action at 320px. ✅
- [x] Mouse-only styling, action labels, mutation behavior, error feedback, and 44px targets remain unchanged. ✅

### Verification

```bash
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- tests/e2e/telegram-parent.spec.ts
cd apps/web && npm run lint && npm run test && npm run build
```

### Commit

```bash
git add apps/web/src/lib/components/telegram/TelegramRequestList.svelte apps/web/tests/e2e/telegram-parent.spec.ts
git commit -m "fix(web): restore parent request focus feedback"
```

## P1-2: Make child task request controls visibly focusable

**Priority:** P1  
**Depends on:** -

### Outcome

On the child Tasks tab, both the task-row trigger and the 44px request button visibly indicate keyboard focus before the request sheet opens.

### Architectural decision

- Source of truth: browser focus state.
- Owner: `TelegramChildTasks.svelte`, which renders both task interaction buttons.
- Extend: the existing local Telegram focus-ring convention.
- Remove/retire: reliance on `TelegramChildShell.svelte` to provide focus treatment across a Svelte component boundary.

### Files

- Modify `apps/web/src/lib/components/telegram/TelegramChildTasks.svelte`.
- Modify `apps/web/tests/e2e/telegram-child.spec.ts`.
- Modify `apps/web/tests/e2e/telegram-layout.spec.ts`.

### Work

1. Apply the existing visible `:focus-visible` treatment locally to `.row-main` and `.check`.
2. Add keyboard coverage at 320px for focusing the task-row trigger and request control before invoking either action.
3. Preserve the group navigation, task sheet focus restoration, duplicate-pending/limit guards, two-line title treatment, and 44px request target.

### Acceptance criteria

- [x] A keyboard user can distinguish the focused task-row trigger and the focused request button without relying on color-only state. ✅
- [x] Escape from the request sheet still restores focus to the request button. ✅
- [x] Multiple task rows remain one divider-separated list surface with no horizontal overflow at 320px. ✅

### Verification

```bash
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- tests/e2e/telegram-child.spec.ts tests/e2e/telegram-layout.spec.ts
cd apps/web && npm run lint && npm run test && npm run build
```

### Commit

```bash
git add apps/web/src/lib/components/telegram/TelegramChildTasks.svelte apps/web/tests/e2e/telegram-child.spec.ts apps/web/tests/e2e/telegram-layout.spec.ts
git commit -m "fix(web): restore child task focus feedback"
```

## P1-3: Fully lock the one-surface request-row contract

**Priority:** P1  
**Depends on:** P1-1, P1-2

### Outcome

The Mini App visual-regression suite fails if a parent request, child request, or child task row regains any standalone-card shell, and it keeps the keyboard-focus contract from P1-1 and P1-2 observable at 320px, 375px, and 430px.

### Architectural decision

- Source of truth: `visual-regression-miniapp.spec.ts` fixtures and browser-computed geometry.
- Owner: the existing Telegram visual-regression suite.
- Extend: the current multi-role, multi-viewport fixture and deterministic DOM/CSS assertions.
- Remove/retire: partial checks that only reject a top border and transparent row background while allowing left/right borders or row radius to recreate a nested card.

### Files

- Modify `apps/web/tests/e2e/visual-regression-miniapp.spec.ts`.

### Work

1. For parent requests, child requests, and child task rows, assert zero left/right borders and zero row radius in addition to the existing transparent background and divider-only border checks.
2. Keep the outer list surface checks explicit: white background, one border, one radius, and dividers only between adjacent rows.
3. At all three current widths, assert the parent actions remain below the primary content and that keyboard focus on each touched action has the expected visible outline.
4. Preserve the existing task/reward icon assertions, leading-emoji normalization assertion, and document-width overflow checks.

### Acceptance criteria

- [x] The suite fails if any tested row receives a left/right border, an independent radius, a non-transparent card background, or an extra border beyond its divider. ✅
- [x] The suite fails if parent actions share the primary-content row or reduce its available width by becoming a side control. ✅
- [x] The suite fails if parent decision or child task-request keyboard focus becomes visually indiscernible. ✅
- [x] Checks remain deterministic and do not introduce screenshot snapshots or a second test harness. ✅

### Verification

```bash
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- tests/e2e/visual-regression-miniapp.spec.ts
cd apps/web && npm run lint && npm run test && npm run build
```

### Commit

```bash
git add apps/web/tests/e2e/visual-regression-miniapp.spec.ts
git commit -m "test(web): harden Telegram list UX regressions"
```

## Final quality gate

```bash
cd apps/web && npm run lint && npm run test && npm run build
cd apps/web && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- tests/e2e/telegram-parent.spec.ts tests/e2e/telegram-child.spec.ts tests/e2e/telegram-layout.spec.ts tests/e2e/visual-regression-miniapp.spec.ts
```
