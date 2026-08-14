# EarnIt Kids - Mini App UX Follow-up Backlog

## Status and scope

This is a **new follow-up backlog** created after the previous Telegram UX backlog was completed.

Do **not** reopen or reimplement the completed backlog unless a task below explicitly requires changing the resulting UI.

The source of truth for this backlog is:

1. the **current Mini App screenshots after the completed redesign**;
2. the product requirements below;
3. `earnit-miniapp-target-reference-v2.html` as the target visual/composition reference.

The previous implementation is the baseline codebase, **not** the desired visual result.

## Goal

Correct the current Mini App implementation so it actually matches the intended mobile UX:

- use the Telegram mobile viewport efficiently;
- remove large empty areas and redundant information;
- make navigation shallow and action-oriented;
- make Parent and Child UX visibly different;
- replace emoji-based entity graphics in Mini App with consistent semantic SVG graphics;
- every actionable Mini App button must have a meaningful SVG icon;
- keep the UI modern, compact, readable and touch-friendly;
- do not recreate an admin dashboard or nested-card layout.

## Non-negotiable visual rules

- Use `earnit-miniapp-target-reference-v2.html` as the visual reference for hierarchy, density, row composition, button placement and screen structure.
- Do not preserve current layout just because it already exists.
- No card-inside-card composition.
- No large page-level rounded wrapper around a whole list.
- No oversized `Edit` button on each Task/Reward row.
- No permanent `Refresh` button.
- No emoji as Mini App entity or action graphics.
- Use one SVG icon family and one centralized semantic icon map.
- Parent UI is denser and decision-oriented.
- Child UI is more action-oriented and slightly more expressive.
- Maintain minimum 44x44px touch targets.
- At 375x667, Parent Tasks/Rewards should show about 5 normal rows with the compact header.
- Large empty states must collapse to a compact row/message.
- Bottom navigation must not duplicate actions already presented as large quick-action buttons without a strong reason.

---

# NUX-001 - Rebuild Parent Home composition

**Status:** ✅ Done

**Priority:** P0  
**Depends on:** -

## Problem visible in current screenshots

Current Home still has several structural problems:

- large `Family space` heading consumes vertical space;
- balance is shown in the top area and again beside `Selected child`;
- `Requests` is a large empty card when there are no requests;
- `No requests yet.` and `Nothing needs attention right now.` communicate the same state twice;
- `Tasks` and `Rewards` are shown as large quick-action buttons even though both are already permanent bottom-navigation tabs;
- a large unused blank area remains between content and bottom navigation;
- the screen still reads like a sparse dashboard rather than a parent decision workspace.

## Target

Parent Home should be a compact **decision inbox**.

### With pending requests

Structure:

```text
[Aliska ▼]                                  [22 coins]

Требуют внимания · 2

[task icon] Утренний старт
            Aliska выполнила задание
            +1 coin
[✓ Одобрить] [× Отклонить]

Быстрые действия
[coin+ Добавить монеты] [history История]

Недавняя активность
...
```

Show at most 1-2 pending requests inline. If more exist, show `Все запросы (N)`.

### With zero pending requests

Do not render a large Requests card.

Use one compact state row:

```text
[check-circle] Сейчас ничего не требует внимания
```

Then show useful next content immediately:

```text
Быстрые действия
[coin+ Добавить монеты] [history История]

Недавняя активность
...
```

## Required changes

- Remove the persistent `Family space` heading from Parent Home.
- Use selected child + balance as the compact page context.
- Remove duplicate balance near `Selected child`.
- Remove the large empty Requests container.
- Remove duplicate zero-state messaging.
- Remove large Home buttons for `Tasks` and `Rewards` because they duplicate bottom navigation.
- Add `Добавить монеты` and `История` as the Home quick actions instead.
- Add 3-5 recent activity rows so the Home viewport contains useful information.
- Keep bottom navigation fixed/safe-area aware.

## Acceptance criteria

- At 375x667, zero-request Home contains child context, balance, compact zero-state, quick actions and at least 2 recent events in the first viewport.
- With pending requests, at least the first request and both decision actions are visible without navigation.
- No duplicate balance.
- No `Family space` title.
- No large Requests empty card.
- No large blank region caused by missing content.
- No Tasks/Rewards quick-action duplication.

---

# NUX-002 - Replace Parent Tasks with a true compact list

**Priority:** P0  
**Depends on:** NUX-004

## Problem visible in current screenshots

Current Tasks still retains the old composition:

- page-level rounded card around the whole catalog;
- `MANAGE CATALOG` adds noise but no value;
- large blue `+ Add task` button;
- each task is its own large rounded card;
- every row has a large pencil + `Edit` control taking substantial width;
- task names still start with emoji;
- category metadata still uses emoji;
- only a few rows fit on screen.

## Target

```text
Задания                                  [+ Добавить]

[sun SVG] Умыться, одеться и причесаться          [⋮]
          1 coin · Утро

[home SVG] Сделать одно дело по дому               [⋮]
           2 coins · Дом и порядок

[desk SVG] Убрать свой стол                        [⋮]
           1 coin · Дом и порядок
```

## Required changes

- Remove `MANAGE CATALOG`.
- Remove the page-level rounded wrapper.
- Make the Add control compact, icon + label.
- Replace emoji prefixes with semantic SVG entity icons.
- Replace emoji category markers with text + optional small SVG category icon.
- Entire row tap opens edit/details.
- Overflow `[⋮]` contains secondary actions such as duplicate/archive/delete.
- Do not show a large `Edit` button.
- Target row height: 56-72px depending on two-line title.
- Use subtle divider/surface separation rather than nested cards.
- Sticky compact page header is allowed on scroll.

## Acceptance criteria

- At 375x667, approximately 5 regular rows are visible.
- No row contains a large `Edit` button.
- No task title starts with emoji.
- No outer catalog card.
- Titles up to 2 lines preserve the overflow action.
- 44x44 minimum tap target remains satisfied.

---

# NUX-003 - Replace Parent Rewards with a true compact list

**Priority:** P0  
**Depends on:** NUX-004

## Problem visible in current screenshots

Rewards has the same unresolved issues as Tasks:

- large outer wrapper;
- `MANAGE CATALOG`;
- oversized Add button;
- large Edit control per row;
- emoji-based reward graphics;
- emoji category marker;
- low information density.

## Target

```text
Награды                                  [+ Добавить]

[dice SVG] Выбрать настольную игру на вечер        [⋮]
           2 coins · Время с семьёй

[family SVG] Поиграть с мамой или папой 20 минут   [⋮]
             4 coins · Время с семьёй
```

## Required changes

- Mirror the compact list architecture from Tasks.
- Reward semantic graphics must be SVG, not emoji.
- Price should scan faster than category.
- Row tap opens edit/details.
- Overflow contains secondary management actions.
- Remove oversized `Edit`.
- Remove outer card and `MANAGE CATALOG`.

## Acceptance criteria

Same density and layout criteria as NUX-002.

---

# NUX-004 - Fix Mini App icon/entity graphic system

**Status:** ✅ Done

**Priority:** P0  
**Depends on:** -

## Problem visible in current screenshots

Navigation buttons now use line icons, but entity content still uses emoji:

- task titles;
- reward titles;
- categories;
- balance coin graphic.

This creates a mixed visual language.

## Target

One coherent Mini App graphic system:

- action icons: one SVG family;
- navigation icons: same family;
- task/reward semantic icons: same visual weight;
- coin: dedicated SVG icon;
- no emoji in Mini App controls or entity graphics.

## Semantic examples

- morning → sunrise
- reading → book
- writing → pencil-line
- cleaning → sparkles/brush
- home → house
- board game → dice
- family time → users/heart
- science → flask
- reward → gift
- activity → history
- child/family → user/users
- coin → dedicated coin-circle SVG

Fallback:

- task → checklist
- reward → gift

## Architectural requirement

Centralize mappings. Do not embed arbitrary icon decisions in individual screens.

Suggested shape:

```ts
type EntityGraphic =
  | { kind: 'task'; semantic?: TaskGraphicKey }
  | { kind: 'reward'; semantic?: RewardGraphicKey }
  | { kind: 'category'; semantic?: CategoryGraphicKey };

getTelegramEntityIcon(...)
getTelegramActionIcon(...)
```

## Acceptance criteria

- Search of Mini App Telegram components finds no product emoji literals used as icons.
- Every actionable button has exactly one semantic SVG icon.
- Entity graphics have a deterministic fallback.
- Bot emoji rules are not changed by this task.

---

# NUX-005 - Rebuild Parent Family screen

**Priority:** P0  
**Depends on:** NUX-004

## Problem visible in current screenshots

Current Family still behaves like the previous Child screen:

- large `Family space` title remains;
- child selector is inside a large rounded card;
- sync/refresh-like symbols appear before child names;
- selected child is represented mainly by a heavy blue border;
- invite flow is permanently expanded;
- technical explanatory copy takes a large amount of screen space;
- large `Create invite link` button dominates a secondary flow;
- substantial vertical space is wasted.

## Target

```text
Семья

Дети

[avatar] Aliska                               22
         Текущий ребёнок

[avatar] Lizka                                  5

[user-plus] Добавить ребёнка

Настройки семьи

[shield] Роли и доступы                         >
[link]   Приглашения                            >
[bell]   Уведомления                            >
```

## Required changes

- Remove `Family space`.
- Replace the large selected-child card with compact child rows.
- Remove sync/refresh glyphs from names unless they represent a real documented state.
- Mark selected child with a subtle check/status treatment, not a giant border.
- Collapse invite creation behind `Добавить ребёнка` or `Приглашения`.
- Do not show technical Telegram linking instructions on the main Family screen.
- Put family settings below the children list.
- Use compact rows and semantic SVG icons.

## Acceptance criteria

- At least two children, Add child and the start of family settings fit in the first 667px viewport.
- No permanently expanded invite card.
- No technical linking paragraph on the main screen.
- No redundant `Family space` title.
- Current child is recognizable without a large bordered card.

---

# NUX-006 - Normalize Parent header and balance context

**Status:** ✅ Done

**Priority:** P0  
**Depends on:** NUX-001, NUX-004

## Problem

Current screens repeatedly show `Family space` and a large yellow balance pill. On Home the balance is duplicated again in selected-child context.

## Target

Create one compact Parent context header used consistently:

```text
[Aliska ▼]                              [coin] 22
```

For management screens, the page title is the main title:

```text
Задания                                  [+ Добавить]
```

Do not show both `Family space` and `Tasks/Rewards/Family`.

## Acceptance criteria

- No Parent screen renders `Family space` as an additional page heading.
- Balance appears once in the active context.
- Header height is compact and consistent.
- Balance chip does not visually dominate primary actions.

---

# NUX-007 - Revalidate Child Mini App against the same visual target

**Priority:** P1  
**Depends on:** NUX-004

## Scope

The latest screenshots supplied for this follow-up show Parent screens. Do not invent regressions that are not visible.

However, because the shared design system changes in NUX-004 affect both roles, run a focused Child Mini App regression pass against `earnit-miniapp-target-reference-v2.html`.

## Check

### Child Today

- compact greeting/balance context;
- today's tasks first;
- direct `Сделать/Готово` action;
- Available → Pending → Completed order;
- completed section does not dominate the screen;
- semantic SVG task graphics;
- progress is visible but not oversized.

### Child Rewards

- affordable rewards first;
- direct `Получить`;
- one nearest unavailable reward as next goal;
- semantic SVG reward graphics;
- no emoji;
- no admin-style management controls.

### Child Activity

- presentation-safe, child-readable events;
- signed coin amount;
- no backend terminology.

## Acceptance criteria

- Parent density changes do not make Child UI look like Parent management UI.
- No emoji regressions are introduced.
- Child primary actions remain larger/clearer than Parent secondary management actions.

---

# NUX-008 - Bottom navigation cleanup

**Priority:** P1  
**Depends on:** NUX-001, NUX-005

## Parent navigation

Exactly:

- Home
- Tasks
- Rewards
- Family

## Child navigation

Exactly:

- Today
- Rewards
- Activity

## Required changes

- Keep compact safe-area aware nav.
- Selected state should be clear but not a giant pill.
- Nav icon + label.
- Do not duplicate permanent nav destinations as oversized Home buttons.

## Acceptance criteria

- Bottom nav height approximately 56-64px plus safe area.
- No overlap with focused inputs or page actions.
- Parent Home no longer has large Tasks/Rewards buttons.

---

# NUX-009 - Empty, loading and error state density

**Priority:** P1  
**Depends on:** NUX-001

## Rules

- Empty state should not reserve the same height as populated state.
- Skeletons should resemble the final compact rows.
- Error state includes retry but stays compact.
- Success does not use modal dialogs.
- Stale request result reconciles automatically.

Examples:

```text
[check] Сейчас ничего не требует внимания
```

```text
[alert] Не удалось загрузить запросы   [retry icon Повторить]
```

## Acceptance criteria

- No empty state creates a large blank card.
- No permanent Refresh button returns.
- Loading does not block the entire shell when cached/navigation chrome can render.

---

# NUX-010 - Visual regression and screenshot acceptance

**Priority:** P1  
**Depends on:** NUX-001..NUX-009

## Required screenshots

Capture at 375x667:

- Parent Home, zero requests
- Parent Home, pending requests
- Parent Tasks
- Parent Rewards
- Parent Family
- Child Today
- Child Rewards

Also validate 320px and 430px widths for overflow.

## Visual assertions

The implementation must visibly match the composition of `earnit-miniapp-target-reference-v2.html`.

Tests/review should fail if:

- `Family space` reappears as redundant Parent page title;
- large Requests empty card reappears;
- Tasks/Rewards large `Edit` buttons reappear;
- outer catalog wrapper reappears;
- emoji entity graphics reappear;
- Home duplicates Tasks/Rewards as large quick actions;
- Family invite instructions are permanently expanded;
- horizontal overflow occurs;
- touch targets fall below 44px.

---

# Implementation order

1. NUX-004 - SVG/entity graphic system
2. NUX-006 - Parent compact context header
3. NUX-001 - Parent Home
4. NUX-002 - Parent Tasks
5. NUX-003 - Parent Rewards
6. NUX-005 - Parent Family
7. NUX-008 - Navigation cleanup
8. NUX-009 - State density
9. NUX-007 - Child regression pass
10. NUX-010 - Visual regression verification

---

# Definition of Done

This follow-up backlog is complete when the current screenshots could no longer be reproduced because:

- Parent Home is compact and useful when there are zero requests;
- Parent Home is a decision inbox when requests exist;
- duplicate balance and `Family space` are gone;
- Tasks and Rewards are compact flat lists;
- large `Edit` buttons are gone;
- parent list density reaches roughly five rows at 375x667;
- Mini App entity graphics no longer use emoji;
- Family is a compact management screen, not an expanded invite form;
- permanent bottom navigation is not duplicated by large Home buttons;
- Parent and Child remain visibly role-specific;
- the final implementation is visually comparable to `earnit-miniapp-target-reference-v2.html`.
