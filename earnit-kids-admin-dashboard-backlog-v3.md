# EarnIt Kids Mini App - Admin Dashboard

## Goal

Add an admin mode for the Telegram bot and expose a new Mini App screen:

`Настройки → Дашборд`

The dashboard must show aggregated usage across all families in EarnIt Kids and help answer two product questions:

1. Do parents and children actually use the coin/reward economy?
2. What do their behavior patterns suggest should be improved in tasks, rewards, pricing, limits, onboarding, and retention?

The dashboard is for product/admin analysis, not family management.

## Mandatory visual reference

Use:

`earnit-kids-admin-dashboard-reference.html`

Reference sections:

- `Reference A - Admin entry in Settings`
- `Reference B - Dashboard overview`
- `Reference C - Coin economy`
- `Reference D - Parent & child needs`
- `Reference E - Retention & activity`

The reference defines hierarchy, grouping, density, card behavior, filters, and mobile layout.

Do not copy exact mock values into production. Use real aggregated backend data.

---

# ADM-01 - Admin access from environment

Add configuration that defines Telegram bot administrators.

Recommended ENV:

```text
TELEGRAM_ADMIN_USER_IDS=123456789,987654321
```

Requirements:

- support one or multiple Telegram user IDs;
- parse once into a set of numeric IDs;
- admin privilege is based on Telegram user ID, not username;
- never grant admin based on display name, `@username`, email, or Mini App query parameter alone;
- backend remains the source of truth for authorization.

Suggested application abstraction:

```text
AdminAccessService
  boolean isAdmin(telegramUserId)
```

Do not scatter ENV checks through controllers/UI handlers.

## Acceptance criteria

- normal parent does not receive admin data even if they manually open the dashboard route;
- admin Telegram account does;
- invalid/missing ENV results in zero admins, not open access.

---

# ADM-02 - Add `Дашборд` to Settings for admins only

In Mini App `Настройки`, add an admin-only row:

```text
🛡️ Администрирование
📊 Дашборд
   Использование сервиса и экономика монет
```

Use the pattern from `Reference A`.

Requirements:

- row appears only for admin accounts;
- normal parent Settings remain unchanged;
- selecting row opens the admin dashboard in Mini App;
- do not add admin navigation to the child's UI;
- no separate permanent bottom-nav item is needed.

This is intentionally a Settings entry because the dashboard is an administrative tool, not a daily parent workflow.

---

# ADM-03 - Dashboard top-level structure with tabs

Dashboard title:

`Дашборд`

Subtitle:

`Все семьи · агрегированные данные`

Do NOT build the dashboard as one long scrolling page.

Use top-level tabs so each product question has its own compact screen.

Required tabs:

```text
📊 Обзор
🪙 Монеты
🎁 Награды
✅ Задания
📈 Активность
```

Каждый tab должен содержать небольшую смысловую графику/emoji, чтобы разделы быстрее различались визуально.

Recommended meaning:

### `Обзор`
- ключевые KPI;
- сколько семей и детей;
- активность;
- краткий health summary;
- самые важные product signals.

### `Монеты`
- earned vs spent;
- Spend / Earn;
- balances;
- zero/high unused balance;
- time to first reward;
- children earning but not spending.

### `Награды`
- reward shop effectiveness;
- reward requests/completions;
- median prices;
- reward price distribution;
- top reward categories;
- failed reward requests.

### `Задания`
- configured tasks;
- completions;
- approvals/rejections;
- median coins per task;
- top task categories;
- catalog vs custom content.

### `Активность`
- activation funnel;
- new/returning families;
- active families;
- active days;
- retention/rolling activity;
- parent response delay;
- pending backlog.

Tabs should be sticky or remain easy to reach near the top of the screen.

Top controls shared by all tabs:

- period selector;
- optional refresh action;
- last-updated timestamp.

Recommended period presets:

```text
7 дней
30 дней
90 дней
Всё время
```

Default:

`30 дней`

The selected period should affect activity/economy/retention metrics.

Lifetime totals such as total registered families may remain lifetime metrics and must be clearly labeled.

Do not add complex date pickers in v1.

## Mobile behavior

Tabs must **полностью помещаться в ширину Mini App**.

Запрещено:
- horizontal scroll для основного tab bar;
- выход последнего tab за правую границу;
- fixed/min-width, из-за которого tab row становится шире viewport;
- длинные padding, из-за которых 5 tabs не помещаются.

Целевой mobile layout:

```text
[📊       ][🪙       ][🎁       ][✅       ][📈       ]
 Обзор     Монеты     Награды    Задания    Активность
```

Предпочтительный вариант:
- 5 равных колонок `grid-template-columns: repeat(5, minmax(0, 1fr))`;
- `width: 100%`;
- gap примерно `3-4px`;
- icon/emoji сверху или рядом с короткой подписью;
- font-size примерно `10-11px`;
- horizontal padding внутри tab примерно `4-6px`;
- `min-width: 0`;
- текст tab не должен расширять колонку;
- если нужно, `Активность` может использовать немного меньший font-size, но не обрезаться.

Active state:
- accent background или accent border;
- icon и text визуально остаются читаемыми;
- не увеличивать active tab по ширине.

На совсем узких экранах допустим вариант `icon сверху + label снизу`, потому что он лучше использует ширину, чем длинная горизонтальная pill.

Preserve selected tab when period changes.

Do not render all tab contents into DOM as one continuous page just hidden visually if that causes unnecessary expensive data loading.

---

## ✅ ADM-03 - Dashboard top-level structure with tabs

**Status:** ✅ COMPLETE - Implemented in commits `8cabbac5` and `817f7b63`

**Implemented:**
- Dashboard page at `/app/dashboard` with 5 tabs
- Tab navigation with semantic emoji icons (📊 Обзор, 🪙 Монеты, 🎁 Награды, ✅ Задания, 📈 Активность)
- Period selector toolbar with presets (7 дней, 30 дней, 90 дней, Всё время)
- Sticky tab navigation optimized for Mini App viewport
- KPI card structure with tooltip support
- Full i18n translations (Russian and English)
- Admin-only access control (client-side + server-side)
- Last updated timestamp display
- Responsive design matching Reference B specification

**Files created:**
- `apps/web/src/routes/app/dashboard/+page.svelte` - Complete UI with all 5 tabs
- `apps/web/src/routes/app/dashboard/+page.server.ts` - Server-side admin authorization
- i18n translations in `apps/web/src/lib/i18n/messages/ru/admin.ts` and `en/admin.ts`

**Verified:**
- ✅ `npm run lint` passes
- ✅ `npm run build` passes
- ✅ Backend tests pass
- ✅ Admin users can access dashboard
- ✅ Non-admin users are redirected to settings

**Next step:** Implement actual data loading for Overview tab KPIs (ADM-04)

---

# ADM-04 - Executive overview KPIs

At the top show the small set of metrics that explains system health fastest.

Use compact 2-column KPI cards on mobile.

Required KPIs:

### Families

`Всего семей`

Count of non-deleted family entities.

### Active families

`Активные семьи`

Families with at least one meaningful product action during selected period.

Meaningful actions include at minimum:

- task completion;
- task approval/rejection;
- reward request/purchase;
- coin balance-changing operation.

Do not count merely opening the app as meaningful activity.

### Children

`Детей`

Total active/non-deleted child profiles.

### Active children

`Активных детей`

Children with meaningful activity during selected period.

### Coins earned

`Монет заработано`

Total positive child coin accrual during selected period.

### Coins spent

`Монет потрачено`

Total coins consumed by rewards during selected period.

### Reward purchases

`Получено наград`

Count of successfully completed reward redemptions.

### Task completions

`Выполнено заданий`

Count of child task completions submitted/approved according to existing domain semantics.

Avoid showing more than 6-8 primary KPI cards before deeper sections.

---

# ADM-05 - Coin economy health

Create section:

`Экономика монет`

This section is the core of the dashboard.

Required metrics:

## Earned vs spent

Show:

```text
Заработано       12 480
Потрачено         8 320
Spend / Earn        67%
```

Define:

```text
spendRate = coinsSpent / coinsEarned
```

Handle zero denominator safely.

Purpose:

- very low spend rate may mean rewards are unattractive, too expensive, or redemption flow is weak;
- very high spend rate may mean rewards are too cheap or parents grant coins too slowly.

Do not encode those interpretations as universal truth in backend logic. Show them as product hints only.

## Median current balance

`Медианный баланс ребёнка`

Prefer median over average because a small number of large balances can distort the mean.

Optionally show average as secondary value.

## Children with zero balance

`Без монет`

Count/percentage of children with current balance `0`.

## Children with high unused balance

`Копят монеты`

Define a configurable/explicit threshold for v1, e.g. balance >= 20 or based on observed reward price distribution.

Do not silently hardcode a mysterious threshold in UI.

## Time to first reward

`До первой награды`

Median time from child/family activation to first successful reward redemption.

This metric is especially useful for understanding whether the economy produces an early reward loop.

## Reward purchase rate

`Семьи с наградой`

Percentage of active families in period with at least one completed reward redemption.

---

# ADM-06 - Reward shop effectiveness

Create section:

`Магазин наград`

Required metrics:

### Rewards configured

`Создано наград`

Total current configured family rewards.

### Families with at least one reward

`Есть награды`

Percentage of active families with >=1 available reward.

### Reward requests

`Запросов наград`

Number of reward redemption requests during selected period.

### Approved rewards

`Выдано наград`

Approved/completed reward requests.

### Rejection/cancellation rate

`Не состоялись`

Percentage of reward requests rejected/cancelled/expired depending on current domain statuses.

### Median reward price

`Медианная цена`

Median coin price of active reward definitions.

### Median purchased reward price

`Цена выбранной награды`

Median price of rewards actually redeemed.

This comparison helps detect whether parents create expensive rewards that children rarely choose.

---

# ADM-07 - Reward price distribution

Add a compact distribution block for active reward prices.

Suggested buckets:

```text
1-5
6-10
11-20
21-50
50+
```

Show either:

- horizontal bars; or
- compact percentage rows.

Do not build a complex charting library solely for this screen in v1.

Purpose:

Understand the actual economy parents configure.

---

# ADM-08 - Top reward patterns

Create:

`Что дети выбирают`

Show top 5-10 reward categories or reward templates by successful redemption count.

If rewards currently have groups/categories, aggregate by those.

If many rewards are free-text custom rewards, additionally show:

`Популярные награды из каталога`

only for catalog-backed entities where stable IDs exist.

Do not aggregate arbitrary free text by title using naive string equality as the main analytics source.

Required row shape:

```text
🎲 Время и развлечения
182 выдачи · 24% покупок
```

---

# ADM-09 - Task economy

Create section:

`Задания`

Metrics:

- configured tasks;
- active families with >=1 task;
- task completion submissions;
- approved completions;
- rejected completions;
- approval rate;
- median coins earned per approved task;
- median completions per active child.

Also show top task categories:

`Что дети делают`

Top categories/groups by approved completion count.

Purpose:

Compare where children earn coins vs where they spend them.

---

# ADM-10 - Parent behavior and needs

Create section:

`Родителям`

Use behavioral signals, not speculative profiling.

Required product metrics:

### Families using catalog content

`Используют каталог`

Percentage of active families that added at least one task/reward from the catalog.

### Families using custom content

`Создают своё`

Percentage that created custom task/reward entities.

### Approval delay

`Время до решения`

Median duration between child request and parent approval/rejection.

This is useful because slow parent response can break the child feedback loop.

### Pending backlog

`Ждут решения`

Current number of pending task/reward requests.

Optionally show:

`семей с ≥1 ожидающим запросом`

### Notification adoption

If notification settings are persisted:

`Уведомления включены`

Percentage of active parents with at least one relevant Telegram notification enabled.

Do not add this metric if notification settings are not actually stored reliably.

---

# ADM-11 - Child behavior and needs

Create section:

`Детям`

Required metrics:

### Active child frequency

`Активных дней`

Median number of active days per active child during selected period.

### Tasks before reward

`Заданий до награды`

Median number of approved task completions between successful reward redemptions.

### Children earning but not spending

`Зарабатывают, но не тратят`

Count/percentage of active children who earned coins during the period but had no reward redemption.

This is one of the most useful shop-health signals.

### Children requesting rewards but failing to receive them

`Запросили, но не получили`

Children with reward requests but no successful reward completion during selected period.

This may indicate price, parent approval, or reward-flow friction.

---

# ADM-12 - Activation funnel

Create a compact family activation funnel.

Suggested stages:

```text
Зарегистрировались
→ добавили ребёнка
→ есть ≥1 задание
→ ребёнок выполнил задание
→ заработал монеты
→ есть ≥1 награда
→ получил первую награду
```

Show:

- count;
- percentage from previous stage;
- optional percentage from initial cohort.

Important:

For v1, use current-state/ever-completed funnel semantics and label it clearly.

Do not pretend this is a strict cohort funnel if events are not stored historically enough to reconstruct one.

---

# ADM-13 - Retention/activity

Create section:

`Активность`

Required:

### New families

`Новые семьи`

Families registered during selected period.

### Active families

Already present in KPI overview, but may be shown in trend context.

### Returning families

Families active in selected period that registered before it.

### 7-day return

If event history allows a trustworthy calculation:

Percentage of newly registered families that have meaningful activity on days 2-7 after registration.

### 30-day return

Only show when enough observation window exists.

If reliable cohort retention cannot be derived yet, do NOT invent it.

For v1 fallback, show:

```text
Активны за 7 дней
Активны за 30 дней
```

as rolling activity, clearly distinct from cohort retention.

---

## ✅ ADM-13 - Retention/activity

**Status:** ✅ COMPLETE - Implemented in commit `feat(admin): ADM-13 retention/activity`

**Implemented:**
- New families (registered during selected period)
- Returning families (active in period, registered before it)
- Active families (already present in KPI overview, shown in trend context)
- Active in 7 days (rolling activity)
- Active in 30 days (rolling activity)
- Clearly labeled as rolling activity, distinct from cohort retention
- No invented cohort retention (7-day/30-day return) since event history does not yet support trustworthy cohort reconstruction

**Files created:**
- `AdminRetentionResponse.java` - DTO with RetentionMetrics nested class
- `AdminRetentionService.java` - service layer with period parsing
- `AdminRetentionResource.java` - REST endpoint `GET /api/admin/analytics/retention?period=30d`

**Files modified:**
- `AdminAnalyticsRepository.java` - added `countNewFamilies`, `countReturningFamilies`, made `countActiveFamilies` public
- `+page.server.ts` - fetch retention data
- `+page.svelte` - activity tab retention section
- i18n `ru/admin.ts` and `en/admin.ts` - retention translations

**Verified:**
- ✅ Backend compiles
- ✅ Backend tests pass (484 tests)
- ✅ `npm run lint` passes
- ✅ `npm run build` passes

---

# ADM-14 - Simple trends

For the most useful metrics show compact trend bars/sparklines where data exists:

- active families by day/week;
- coins earned vs spent;
- reward redemptions;
- task completions.

Do not make every KPI a chart.

On mobile, charts must remain readable and vertically compact.

Recommended v1:
- 1 activity trend;
- 1 earned/spent comparison;
- remaining metrics as cards/rows.

---

## ✅ ADM-14 - Simple trends

**Status:** ✅ COMPLETE - Implemented in commit `feat(admin): ADM-14 simple trends`

**Implemented:**
- Active families by day (bar chart)
- Coins earned vs spent by day (dual-bar comparison)
- Reward redemptions and task completions aggregated per day in the same trend points
- Compact vertical bar charts readable on mobile
- Only 2 trend charts (activity + earned/spent) per v1 recommendation; remaining metrics stay as cards/rows
- Empty state when no data for the period

**Files created:**
- `AdminTrendsResponse.java` - DTO with TrendPoint nested class
- `AdminTrendsService.java` - service layer with period parsing
- `AdminTrendsResource.java` - REST endpoint `GET /api/admin/analytics/trends?period=30d`

**Files modified:**
- `AdminAnalyticsRepository.java` - added `getTrendPoints` aggregating history/requests by day
- `+page.server.ts` - fetch trends data
- `+page.svelte` - activity tab trends section with bar charts
- i18n `ru/admin.ts` and `en/admin.ts` - trends translations

**Verified:**
- ✅ Backend compiles
- ✅ Backend tests pass (484 tests)
- ✅ `npm run lint` passes
- ✅ `npm run build` passes

---

# ADM-15 - Data definitions must be explicit

Create a central analytics definitions layer/documentation.

Examples:

```text
active_family
active_child
coins_earned
coins_spent
reward_completed
task_completed
first_reward_at
```

Avoid defining the same metric differently in multiple queries.

Recommended architecture:

```text
AdminAnalyticsService
AdminAnalyticsRepository
AdminDashboardController
```

or equivalent existing project layering.

Do not execute many unrelated per-card queries directly from the UI/controller.

---

## ✅ ADM-15 - Data definitions must be explicit

**Status:** ✅ COMPLETE - Implemented in commit `docs(admin): ADM-15 explicit metric definitions`

**Implemented:**
- Created central analytics definitions document `apps/backend/docs/ADMIN_ANALYTICS.md`
- Single source of truth for every dashboard metric definition and query
- Documents architecture (resource → service → repository) and period semantics
- Defines all metrics exactly once: `active_family`, `active_child`, `coins_earned`, `coins_spent`, `reward_completed`, `task_completed`, `first_reward_at`, and all others
- Documents trend point aggregation
- Rules: no duplicate definitions, no per-card queries from UI/controller, aggregate in DB, safe zero denominators

**Files created:**
- `apps/backend/docs/ADMIN_ANALYTICS.md`

**Verified:**
- ✅ Documentation-only change (no code impact)

---

# ADM-16 - Aggregated API

Expose one dashboard-oriented API response or a small number of section endpoints.

Prefer:

```text
GET /api/admin/dashboard?period=30d
```

Response shape conceptually:

```text
overview
coinEconomy
rewardShop
tasks
parentSignals
childSignals
activation
activity
```

Avoid 20 independent frontend calls for 20 KPI cards.

If some expensive sections require separate loading later, split only those sections deliberately.

---

## ✅ ADM-16 - Aggregated API

**Status:** ✅ COMPLETE - Implemented in commit `feat(admin): ADM-16 aggregated dashboard API`

**Implemented:**
- Added `GET /api/admin/dashboard?period=30d` aggregated endpoint
- Response shape: `overview`, `coinEconomy`, `rewardShop`, `tasks`, `parentSignals`, `childSignals`, `activation`, `activity`
- Composes all section services into one response
- Frontend now makes 1 aggregated call + 1 trends call instead of 9 separate calls
- Individual section endpoints remain available for deliberate split loading

**Files created:**
- `AdminDashboardResponse.java` - aggregated DTO
- `AdminDashboardService.java` - composes all section services
- `AdminDashboardResource.java` - REST endpoint `GET /api/admin/dashboard`

**Files modified:**
- `+page.server.ts` - use aggregated endpoint

**Verified:**
- ✅ Backend compiles
- ✅ Backend tests pass (484 tests)
- ✅ `npm run lint` passes
- ✅ `npm run build` passes

---

# ADM-17 - Authorization is backend-enforced

Every admin analytics endpoint must verify admin privilege server-side.

Frontend hiding is not authorization.

Requirements:

- return 403 for non-admin authenticated Telegram users;
- do not return cross-family data through normal family APIs;
- admin dashboard never depends on client-supplied family IDs for global access;
- validate Telegram Mini App authentication using the same trusted auth path as the existing app.

---

## ✅ ADM-17 - Authorization is backend-enforced

**Status:** ✅ COMPLETE - Implemented in commit `fix(admin): ADM-17 enforce backend admin authorization`

**Implemented:**
- Fixed a critical authorization gap: 5 admin resources (parent-behavior, child-behavior, activation-funnel, retention, trends) used `@RolesAllowed` + `SecurityContext.isUserInRole()`, which is **inert** because the app has no Quarkus security wiring (`quarkus.http.auth.*`, `SecurityIdentity`, `IdentityProvider`).
- Rewrote all 5 resources to use the trusted `AuthContext`/`AuthFilter` path (same as overview, coin-economy, reward-shop, task-economy, dashboard).
- Every admin analytics endpoint now returns:
  - `401` for unauthenticated requests
  - `403` for non-admin authenticated users
- Admin dashboard never depends on client-supplied family IDs for global access.
- Telegram Mini App authentication validated via the same trusted auth path as the rest of the app.

**Files modified:**
- `AdminParentBehaviorResource.java`
- `AdminChildBehaviorResource.java`
- `AdminActivationFunnelResource.java`
- `AdminRetentionResource.java`
- `AdminTrendsResource.java`

**Verified:**
- ✅ Backend compiles
- ✅ Backend tests pass (484 tests)
- ✅ No remaining `@RolesAllowed`/`SecurityContext` in admin resources

---

# ADM-18 - Privacy / no unnecessary PII

Dashboard v1 should be aggregated.

Do NOT show:

- parent names;
- child names;
- Telegram usernames;
- email addresses;
- raw family lists;
- message text;
- individual balances tied to identity.

The goal is product analytics, not surveillance.

If drill-down is later required, make it a separate explicitly designed admin feature.

---

## ✅ ADM-18 - Privacy / no unnecessary PII

**Status:** ✅ COMPLETE - Verified in commit `docs(admin): ADM-18 privacy audit`

**Audit result:**
- All admin analytics responses are aggregated; no raw family/child lists exposed.
- No PII fields selected in `AdminAnalyticsRepository` queries (no `c.name`, `f.email`, `username`, `description`, `comment`, `note`, `token`).
- No PII rendered in the dashboard UI.
- The only "name" fields are `groupName`/`icon` (task/reward category names, not person identifiers).
- No individual balances tied to identity; balances are aggregated (median, counts, percentages).
- No drill-down to individual families/children in v1.

**Files reviewed:**
- All `Admin*Response.java` DTOs
- `AdminAnalyticsRepository.java`
- `+page.svelte`

**Verified:**
- ✅ No PII in DTOs, queries, or UI

---

# ADM-19 - Empty / low-data states

For a new deployment with little data:

- show `Нет данных за период`;
- do not render divide-by-zero values;
- do not show misleading `0%` when denominator is not meaningful;
- use `—` for unavailable metrics where appropriate.

Dashboard must work with:
- 0 families;
- 1 family;
- no rewards;
- no reward purchases;
- coins earned = 0;
- no historical events.

---

# ADM-20 - Performance

Global analytics can become expensive as family count grows.

For v1:

- aggregate in database;
- avoid loading all families/tasks/rewards into application memory;
- index timestamps/status/family-child relationships needed by queries;
- avoid N+1 queries.

If response becomes expensive:
- cache dashboard aggregates for a short interval, e.g. 1-5 minutes;
- show `Обновлено ...` timestamp.

Do not introduce a separate analytics warehouse in this backlog unless existing scale requires it.

---

# ADM-21 - Metric tooltips with explanations and examples

Every non-obvious KPI/metric must have an info affordance:

```text
ⓘ
```

or an equivalent compact help icon.

The tooltip/popover must answer:

1. **Что показывает показатель**
2. **Как он считается**
3. **Пример**
4. **Как его интерпретировать**, only where interpretation is useful

Do not make the tooltip just repeat the metric title.

### Example: `Spend / Earn`

Tooltip:

```text
Показывает, какая доля заработанных детьми монет была потрачена на награды.

Расчёт:
потраченные монеты / заработанные монеты.

Пример:
дети заработали 1 000 монет и потратили 650.
Spend / Earn = 65%.

Низкое значение может означать, что награды слишком дорогие или недостаточно интересные.
```

### Example: `До первой награды`

```text
Медианное время от начала использования семьи до первой успешно полученной ребёнком награды.

Пример:
у 5 семей первая награда была через 1, 2, 4, 6 и 12 дней.
Медиана = 4 дня.

Чем дольше этот путь, тем выше риск, что ребёнок не успеет почувствовать ценность цикла «задание → монеты → награда».
```

### Example: `Зарабатывают, но не тратят`

```text
Доля активных детей, которые получили монеты за выбранный период, но не получили ни одной награды.

Пример:
100 детей зарабатывали монеты, 38 из них не получили наград.
Показатель = 38%.

Это сигнал проверить цены, ассортимент наград и процесс запроса/одобрения.
```

### Example: `Активные семьи`

```text
Семьи, в которых за выбранный период было хотя бы одно значимое действие:
выполнение задания, решение родителя по запросу, выдача награды или изменение баланса.

Простое открытие Mini App не считается активностью.
```

### Example: `Время до решения`

```text
Медианное время между отправкой ребёнком запроса и решением родителя.

Пример:
решения были через 2, 8, 18, 40 и 120 минут.
Медиана = 18 минут.
```

### Example: `Семьи с наградой`

```text
Доля активных семей, где за выбранный период была хотя бы одна успешно выданная награда.

Пример:
из 200 активных семей награду получили дети в 116 семьях.
Показатель = 58%.
```

## Tooltip UX requirements

- tap/click on `ⓘ` opens a small popover/bottom sheet;
- mobile tooltip must not depend on hover;
- icon has its own accessible label;
- tooltip closes by tap outside / close action;
- tooltip text may be 3-6 short lines;
- examples must use simple round numbers;
- do not place permanent long explanations inside KPI cards;
- keep KPI cards compact.

Use tooltips especially for:
- active families;
- active children;
- Spend / Earn;
- median balance;
- time to first reward;
- families with reward;
- failed rewards;
- median reward price;
- chosen reward price;
- approval rate;
- tasks before reward;
- earning but not spending;
- activation funnel percentages;
- retention/return metrics.

Basic self-explanatory counts such as `Всего семей` may omit a tooltip unless definition ambiguity exists.

# ADM-22 - Product insight hints

The UI may show small neutral hints under selected metrics, for example:

```text
43% детей зарабатывали монеты, но не получали награды.
Проверьте цены и привлекательность наград.
```

Rules:

- hints are derived from transparent metrics;
- avoid pretending there is one universally correct target;
- do not use alarming red states without a defined threshold;
- v1 should focus on observation, not automated scoring.

---

# Recommended first-version tab information architecture

```text
Дашборд
[ period ]

[ Обзор | Монеты | Награды | Задания | Активность ]
```

## Обзор
- Всего семей
- Активные семьи
- Дети / активные дети
- Выполнено заданий
- Получено наград
- краткий Spend / Earn
- 2-3 ключевых product signals

## Монеты
- Earned vs spent
- Spend / Earn
- Median balance
- Zero balance
- High unused balance
- Time to first reward
- Earning but not spending

## Награды
- Reward requests
- Successful rewards
- Failed reward requests
- Reward prices
- Price distribution
- Top reward categories

## Задания
- Configured tasks
- Completion volume
- Approval rate
- Median task reward
- Top task categories
- Catalog vs custom

## Активность
- Activation funnel
- New/returning families
- Active days
- Parent decision delay
- Pending backlog
- Retention/rolling activity

This split avoids one giant analytics page and keeps each tab centered on a specific product question.

---

# Tests

## Authorization

- admin from ENV can open dashboard;
- non-admin parent gets 403;
- child gets 403;
- forged frontend/admin route does not bypass backend check;
- missing ENV grants nobody admin.

## Metrics

Verify at minimum:

- total families;
- active families;
- children;
- earned/spent coins;
- reward requests/completions;
- task completions;
- spend rate zero denominator;
- median reward price;
- pending request count;
- first reward duration.

## UI

- admin Settings contains `Дашборд`;
- normal parent Settings does not;
- dashboard works at mobile Mini App width;
- dashboard uses tabs instead of one long page;
- active tab is clearly visible;
- switching tabs does not reset the selected period;
- 2-column KPI cards do not truncate values;
- period selection refreshes period-dependent sections;
- lifetime metrics stay clearly labeled;
- all non-obvious metrics have working `ⓘ` help;
- tooltip examples match the metric definition;
- tooltips work by tap on mobile, not hover only;
- empty state works.

---

# Definition of Done

Admin configured by Telegram user ID in ENV can open:

`Настройки → 📊 Дашборд`

and see aggregated, privacy-safe product analytics across all families.

At minimum the dashboard answers:

1. How many families and children exist?
2. How many are actually active?
3. How many tasks are completed?
4. How many coins are earned?
5. How many coins are spent?
6. Are children actually receiving rewards?
7. What reward prices/categories work?
8. Are children accumulating coins without spending them?
9. How quickly do parents respond to requests?
10. At what activation step do families stop using the product?

The screen must stay useful and compact on mobile and must not become a generic BI dashboard with dozens of low-value metrics.

The dashboard must be split into the five required tabs and non-obvious metrics must explain themselves through compact tap-accessible tooltips with examples.


# ADM-23 - Semantic graphics in dashboard tabs

Use a compact semantic icon for every main dashboard tab:

```text
📊 Обзор
🪙 Монеты
🎁 Награды
✅ Задания
📈 Активность
```

Requirements:

- icon is part of the tab itself, not decorative content outside it;
- use one stable icon per section;
- icons must remain visible in active/inactive state;
- do not use large illustrations inside the tab bar;
- do not add separate text descriptions under the tab bar;
- visual weight of icon must be secondary to the selected state;
- all 5 tabs still fit the viewport without horizontal scrolling.

Preferred mobile composition:

```text
┌──────┬──────┬──────┬──────┬──────┐
│  📊  │  🪙  │  🎁  │  ✅  │  📈  │
│Обзор │Монеты│Награды│Задания│Актив.│
└──────┴──────┴──────┴──────┴──────┘
```

Do not literally abbreviate `Активность` to `Актив.` unless required by the actual available width. First prefer smaller typography and icon-over-label composition.

## Acceptance criteria

- all five tabs fit within 320-430 px Mini App viewport;
- no horizontal tab scroll;
- no clipping outside the screen;
- each tab has a semantic icon;
- labels remain understandable;
- active tab is immediately recognizable;
- tab bar height remains compact.
