# Admin analytics

<a name="top"></a>

This guide defines what the super-admin dashboard measures. The API is under
`/api/admin/dashboard` and `/api/admin/analytics/*`; only a super-admin session
may read it.

## Table of contents

- [🧭 Time periods](#-time-periods)
- [📊 Core measures](#-core-measures)
- [👨‍👩‍👧 Activity and behaviour](#-activity-and-behaviour)
- [⚠️ Reading the data](#️-reading-the-data)
- [🧪 Change a metric safely](#-change-a-metric-safely)

## 🧭 Time periods

Analytics endpoints accept `period`:

| Value | Window |
| --- | --- |
| `7d` | last seven days |
| `30d` | last thirty days; default |
| `90d` | last ninety days |
| `all` | all recorded time |

The window starts at the server’s current time minus the selected number of
days. Measures described as current state are not period-filtered.

[↑ Back to top](#top)

## 📊 Core measures

| Measure | Meaning |
| --- | --- |
| Families | Non-deleted family count |
| Children | Child count |
| Coins earned | Positive task or balance-history amounts in the window |
| Coins spent | Absolute reward-spend amounts in the window |
| Spend rate | Coins spent divided by coins earned; zero when nothing was earned |
| Rewards completed | Approved reward requests in the window |
| Tasks completed | Completed-task history records in the window |
| Pending requests | Requests currently awaiting a decision |
| Median balance | Median current child balance |

The dashboard is for product operations, not accounting. It reports the state
stored by the application and should not be used as a payment ledger.

[↑ Back to top](#top)

## 👨‍👩‍👧 Activity and behaviour

An active family or child has at least one history record in the selected
window. Opening the app alone is not activity. The remaining analytics views
describe trends, task and coin economy, reward use, parent behaviour, child
behaviour, retention, and the activation funnel.

The activation funnel is cumulative: registered family, added child, created a
task, completed a task, earned coins, created a reward, then received a reward.
It is a current-state funnel, not a cohort report.

[↑ Back to top](#top)

## ⚠️ Reading the data

- Current-state counts can change after cleanup or correction of stored data.
- A short period can show zero without meaning a feature is broken.
- Dashboard values are cached for short periods. A mutation is not guaranteed
  to appear in every card immediately.
- Never expose dashboard data to family users or put personal data in a metric
  label, URL, or client-side log.

[↑ Back to top](#top)

## 🧪 Change a metric safely

Keep the definition, query, response field, dashboard label, and focused test
in sync. Use one repository query per metric group; do not introduce a query
per visible card. Test period boundaries, empty data, authorization, and any
new current-state versus period distinction.

```bash
cd apps/backend
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

[↑ Back to top](#top)
