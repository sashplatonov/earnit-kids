# Admin Analytics — Metric Definitions

This document is the single source of truth for how each admin dashboard metric is defined and computed. Every metric must be defined here exactly once. If a metric appears in multiple queries, those queries must use the same definition.

## Architecture

The admin analytics layer follows the project's standard layering:

```text
resource/  →  service/  →  repository/
```

- `resource/admin/Admin*Resource.java` — HTTP endpoints, admin role enforcement, period parsing.
- `service/admin/Admin*Service.java` — orchestration, period calculation, response assembly.
- `repository/AdminAnalyticsRepository.java` — all aggregation queries live here. No per-card queries are executed from the UI or controller.

## Period semantics

All period-dependent metrics accept a `period` query parameter:

| Value | Meaning |
| --- | --- |
| `7d` | last 7 days |
| `30d` | last 30 days (default) |
| `90d` | last 90 days |
| `all` | lifetime (from epoch) |

Period start is computed as `now - N days`. Lifetime totals (e.g. total registered families) are always lifetime and are clearly labeled in the UI.

## Metric definitions

### `total_families`

- **Definition:** Count of non-deleted `FamilyEntity` rows.
- **Query:** `SELECT COUNT(f) FROM FamilyEntity f`.
- **Lifetime:** yes.

### `total_children`

- **Definition:** Count of `ChildEntity` rows.
- **Query:** `SELECT COUNT(c) FROM ChildEntity c`.
- **Lifetime:** yes.

### `active_family`

- **Definition:** A family with at least one meaningful product action during the period. Meaningful actions are any `HistoryEntryEntity` row created in the period (task completion, approval/rejection, reward, balance change). Merely opening the app is not counted.
- **Query:** `SELECT COUNT(DISTINCT h.familyDbId) FROM HistoryEntryEntity h WHERE h.createdAt >= :periodStart`.
- **Period:** yes.

### `active_child`

- **Definition:** A child with at least one `HistoryEntryEntity` row in the period.
- **Query:** `SELECT COUNT(DISTINCT h.childId) FROM HistoryEntryEntity h WHERE h.createdAt >= :periodStart`.
- **Period:** yes.

### `coins_earned`

- **Definition:** Sum of positive `HistoryEntryEntity.amount` where `type = earn` in the period.
- **Query:** `SELECT COALESCE(SUM(h.amount),0) FROM HistoryEntryEntity h WHERE h.type = earn AND h.amount > 0 AND h.createdAt >= :periodStart`.
- **Period:** yes.

### `coins_spent`

- **Definition:** Sum of absolute `HistoryEntryEntity.amount` where `type = spend` in the period.
- **Query:** `SELECT COALESCE(SUM(ABS(h.amount)),0) FROM HistoryEntryEntity h WHERE h.type = spend AND h.createdAt >= :periodStart`.
- **Period:** yes.

### `spend_rate`

- **Definition:** `coins_spent / coins_earned`, expressed as a percentage. Zero denominator yields `0`.
- **Period:** yes.

### `reward_completed`

- **Definition:** A `PurchaseRequestEntity` with `status = approved` created in the period.
- **Query:** `SELECT COUNT(p) FROM PurchaseRequestEntity p WHERE p.status = approved AND p.createdAt >= :periodStart`.
- **Period:** yes.

### `task_completed`

- **Definition:** A `HistoryEntryEntity` with `type = TASK_COMPLETED` created in the period.
- **Query:** `SELECT COUNT(h) FROM HistoryEntryEntity h WHERE h.type = TASK_COMPLETED AND h.createdAt >= :periodStart`.
- **Period:** yes.

### `first_reward_at`

- **Definition:** Time from family/child activation to first successful reward redemption. Computed as median across families.
- **Period:** lifetime.

### `median_balance`

- **Definition:** Median of current `ChildEntity.balance` across non-test children. Median is preferred over mean because a few large balances distort the mean.
- **Period:** current state (lifetime).

### `zero_balance_child`

- **Definition:** Child with current `balance = 0`.
- **Period:** current state.

### `high_balance_child`

- **Definition:** Child with current `balance >= 20` (v1 explicit threshold).
- **Period:** current state.

### `families_with_reward`

- **Definition:** Families with at least one `PurchaseRequestEntity` with `status = approved` in the period.
- **Period:** yes.

### `reward_request`

- **Definition:** Any `PurchaseRequestEntity` created in the period (any status).
- **Period:** yes.

### `reward_rejection_rate`

- **Definition:** `(rejected + cancelled) / total requests` in the period, as a percentage.
- **Period:** yes.

### `median_reward_price`

- **Definition:** Median of `ShopItemEntity.price` where `price > 0`.
- **Period:** current state.

### `median_purchased_reward_price`

- **Definition:** Median of `PurchaseRequestEntity.coins` where `status = approved` and `coins > 0` in the period.
- **Period:** yes.

### `families_using_catalog`

- **Definition:** Active families with at least one history entry whose `groupName` is non-empty and not `custom`.
- **Period:** yes.

### `families_using_custom_content`

- **Definition:** Active families with at least one history entry whose `groupName` is empty or `custom`.
- **Period:** yes.

### `approval_delay`

- **Definition:** Median duration between `PurchaseRequestEntity.createdAt` and `updatedAt` for requests with `status = approved` or `rejected` in the period.
- **Period:** yes.

### `pending_request`

- **Definition:** A `PurchaseRequestEntity` with `status = pending` (current state).
- **Period:** current state.

### `active_days_per_child`

- **Definition:** Median number of distinct calendar days with activity per active child in the period.
- **Period:** yes.

### `tasks_before_reward`

- **Definition:** Median number of approved task completions before each successful reward redemption in the period.
- **Period:** yes.

### `children_earning_not_spending`

- **Definition:** Active children who earned coins in the period but had no approved reward redemption.
- **Period:** yes.

### `children_requested_not_received`

- **Definition:** Children with a reward request in the period but no approved reward completion.
- **Period:** yes.

### `activation_funnel`

- **Definition:** Current-state (ever-completed) funnel, not cohort-based. Stages:
  1. Registered families
  2. Added a child
  3. Has ≥1 task
  4. Child completed a task
  5. Earned coins
  6. Has ≥1 reward
  7. Received first reward
- **Period:** lifetime (current state).

### `new_family`

- **Definition:** A `FamilyEntity` with `createdAt >= :periodStart`.
- **Period:** yes.

### `returning_family`

- **Definition:** A family active in the period that registered before it (`createdAt < :periodStart`).
- **Period:** yes.

### `active_7d` / `active_30d`

- **Definition:** Families with activity in the last 7 / 30 days (rolling activity, distinct from cohort retention).
- **Period:** rolling window.

## Trend points

`getTrendPoints` aggregates the following per calendar day:

- `active_families` — distinct families with history in that day.
- `coins_earned` / `coins_spent` — sum of earn/spend amounts that day.
- `reward_redemptions` — approved requests that day.
- `task_completions` — `TASK_COMPLETED` history entries that day.

## Rules

- Do not define the same metric differently in multiple queries.
- Do not execute per-card queries directly from the UI/controller; route through `AdminAnalyticsRepository`.
- Aggregate in the database; never load all families/tasks/rewards into application memory.
- Handle zero denominators safely (return `0` or `—`).
- Lifetime metrics must be clearly labeled in the UI.
