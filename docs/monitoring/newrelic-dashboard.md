# New Relic Backend Dashboard Contract

<a id="top"></a>

## Table of Contents

- [🧭 Scope](#scope)
- [📊 Widgets](#widgets)
- [🧪 NRQL Queries](#nrql-queries)
- [🚦 Alert Thresholds](#alert-thresholds)
- [✅ Rollout Notes](#rollout-notes)

[↩ Back to toc](#top)

## 🧭 Scope <a name="scope"></a>

This document describes the minimum backend dashboard contract for New Relic after `NR-13` and `NR-14`.

It only references metrics that the backend already emits through Micrometer/OTLP:

- `earnit.backend.http.request.duration`
- `earnit.backend.http.request.count`
- `earnit.backend.http.request.errors`
- `earnit.backend.http.response.payload.bytes`
- `earnit.backend.service.operation.duration`
- `earnit.backend.service.operation.count`
- `earnit.backend.websocket.active.sessions`
- `earnit.backend.websocket.notification.count`
- standard Micrometer JVM meters such as `jvm.memory.used`

Labels are intentionally low-cardinality:

- `method`
- `route`
- `status`
- `service`
- `operation`
- `outcome`

[↩ Back to toc](#top)

## 📊 Widgets <a name="widgets"></a>

### 1. Dashboard latency by operation

Purpose:

- show whether dashboard, auth, admin, and family-action use cases stay within expected latency
- separate success and failure paths

Suggested chart:

- percentile latency over time
- facet by `service`, `operation`, and `outcome`

### 2. Operation volume and failures

Purpose:

- track whether `auth`, `dashboard`, `analytics`, `family_action`, `admin`, and `websocket` traffic is healthy
- make failure spikes visible before users report them

Suggested chart:

- request or action rate over time
- facet by `service`, `operation`, and `outcome`

### 3. WebSocket fan-out health

Purpose:

- confirm active websocket session count stays non-zero when the app is in use
- catch send failures or stale sessions before fan-out breaks silently

Suggested chart:

- latest active session count
- notification volume by outcome

### 4. JVM sanity check

Purpose:

- confirm the backend is still ingesting metrics and the runtime is not under obvious memory pressure

Suggested chart:

- `jvm.memory.used` over time

[↩ Back to toc](#top)

## 🧪 NRQL Queries <a name="nrql-queries"></a>

### Latency widgets

```nrql
SELECT percentile(earnit.backend.http.request.duration, 95)
FROM Metric
FACET method, route, status
TIMESERIES
```

```nrql
SELECT percentile(earnit.backend.service.operation.duration, 95)
FROM Metric
WHERE service = 'dashboard'
FACET operation, outcome
TIMESERIES
```

```nrql
SELECT percentile(earnit.backend.service.operation.duration, 95)
FROM Metric
WHERE service = 'analytics'
FACET operation, outcome
TIMESERIES
```

```nrql
SELECT percentile(earnit.backend.service.operation.duration, 95)
FROM Metric
WHERE service = 'auth'
FACET operation, outcome
TIMESERIES
```

### Volume and failure widgets

```nrql
SELECT rate(sum(earnit.backend.http.request.count), 1 minute)
FROM Metric
FACET method, route, status
TIMESERIES
```

```nrql
SELECT rate(sum(earnit.backend.http.request.errors), 1 minute)
FROM Metric
FACET method, route, status
TIMESERIES
```

```nrql
SELECT rate(sum(earnit.backend.service.operation.count), 1 minute)
FROM Metric
FACET service, operation, outcome
TIMESERIES
```

```nrql
SELECT rate(sum(earnit.backend.service.operation.count), 1 minute)
FROM Metric
WHERE outcome = 'failure'
FACET service, operation
TIMESERIES
```

### WebSocket widgets

```nrql
SELECT latest(earnit.backend.websocket.active.sessions)
FROM Metric
WHERE service = 'websocket'
TIMESERIES
```

```nrql
SELECT rate(sum(earnit.backend.websocket.notification.count), 1 minute)
FROM Metric
WHERE service = 'websocket'
FACET operation, outcome
TIMESERIES
```

### JVM sanity widget

```nrql
SELECT average(jvm.memory.used)
FROM Metric
TIMESERIES
```

[↩ Back to toc](#top)

## 🚦 Alert Thresholds <a name="alert-thresholds"></a>

Use these as rollout-safe starting points. Tighten them only after a stable burn-in period.

| Signal | Warning | Page |
| --- | --- | --- |
| HTTP request p95 latency | above 800 ms for 10 minutes | above 1500 ms for 5 minutes |
| `dashboard` p95 latency | above 800 ms for 10 minutes | above 1500 ms for 5 minutes |
| `analytics` p95 latency | above 1200 ms for 10 minutes | above 2500 ms for 5 minutes |
| `family_action` failure rate | above 5% for 15 minutes | above 10% for 10 minutes |
| `auth` failure rate | above 5% for 15 minutes | above 10% for 10 minutes |
| `admin` failure rate | above 5% for 15 minutes | above 10% for 10 minutes |
| `websocket` notification failure rate | above 1% for 15 minutes | above 5% for 10 minutes |
| `websocket` active sessions | below expected floor during active hours for 30 minutes | only page when paired with notification failures |
| `jvm.memory.used` | above 80% of expected heap envelope for 15 minutes | above 90% for 10 minutes |

Operational notes:

- `outcome = 'failure'` should be the primary alert facet for counter widgets.
- `service = 'dashboard'` should include `shell`, `detail`, and `full` operations.
- `service = 'auth'` should include all auth entrypoints, but alerts should usually group by outcome first and operation second.
- `service = 'family_action'` should include task, item, approval, import, and balance operations.
- `service = 'admin'` should include family settings, token regeneration, and base-data maintenance flows.
- keep warning thresholds in place during rollout before enabling the tighter page thresholds.

[↩ Back to toc](#top)

## ✅ Rollout Notes <a name="rollout-notes"></a>

Recommended rollout sequence:

1. enable the metrics export path and confirm metrics arrive in New Relic
2. add the widgets using the NRQL examples above
3. start with warning-only thresholds
4. promote to page thresholds after the baseline is stable

This dashboard contract should stay aligned with the backend KPI set. If metric names or tags change, update the docs in the same change.

[↩ Back to toc](#top)
