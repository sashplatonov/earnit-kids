# New Relic Monitoring

<a name="top"></a>

## Table of Contents

- [🎯 Scope](#scope)
- [🔐 Required Environment Variables](#required-environment-variables)
- [🚀 Runtime Modes](#runtime-modes)
- [🧪 Validate](#validate)

This repo uses one New Relic path split by runtime:

- JVM agent inside `apps/backend`
- OTLP metrics export from Quarkus Micrometer to New Relic
- Browser agent inside `apps/web`
- structured JSON logs on stdout
- browser logs and JS errors sent from the SvelteKit client when browser config is present
- backend log forwarding disabled by default until volume review is complete
- backend observability settings are exposed via typed config mappings under `app.performance.*` and `app.observability.*`
- slow-request and slow-query diagnostics are threshold-driven, not per-request noise
- readiness probes cover enabled New Relic metrics config before traffic is accepted
- the backend dashboard contract lives in [newrelic-dashboard.md](newrelic-dashboard.md)

[↑ Back to top](#top)

## 🎯 Scope <a name="scope"></a>

- Backend APM and JVM telemetry for `earnit-kids-backend`
- Backend metrics export to New Relic dashboards through OTLP/HTTP
- Browser page views, JS errors, and client-side logs for the web app
- Logs-in-context from the Quarkus container stdout stream
- no second log pipeline by default

[↑ Back to top](#top)

## 🔐 Required Environment Variables <a name="required-environment-variables"></a>

Runtime:

- `HTTP_METRICS_PAYLOAD_ESTIMATION_ENABLED=true`
- `HTTP_METRICS_PAYLOAD_ESTIMATION_MAX_COLLECTION_SIZE=256`
- `NEW_RELIC_LICENSE_KEY`
- `NEW_RELIC_APP_NAME=earnit-kids-backend`
- `NEW_RELIC_AGENT_ENABLED=false`
- `NEW_RELIC_METRICS_ENABLED=false`
- `NEW_RELIC_OTLP_METRICS_ENDPOINT=https://otlp.nr-data.net`
- `NEW_RELIC_OTLP_METRICS_PROTOCOL=http/protobuf`
- `DEPLOYMENT_ENV=development`
- `NEW_RELIC_APPLICATION_LOGGING_FORWARDING_ENABLED=false`
- `NEW_RELIC_APPLICATION_LOGGING_FORWARDING_MAX_SAMPLES_STORED=10000`
- `NEW_RELIC_APPLICATION_LOGGING_LOCAL_DECORATING_ENABLED=false`
- `VITE_NEW_RELIC_BROWSER_ENABLED=false`
- `VITE_NEW_RELIC_BROWSER_INFO=` copied from the Browser app `NREUM.info` snippet
- `VITE_NEW_RELIC_BROWSER_INIT=` copied from the Browser app `NREUM.init` snippet
- `VITE_NEW_RELIC_BROWSER_LOADER_CONFIG=` copied from the Browser app `NREUM.loader_config` snippet

Backend config mappings:

- `app.performance.http-metrics.payload-estimation-enabled`
- `app.performance.http-metrics.payload-estimation-max-collection-size`
- `app.performance.http-metrics.slow-request-threshold-ms`
- `app.performance.http-metrics.slow-query-threshold-ms`
- `app.observability.new-relic.agent-enabled`
- `app.observability.new-relic.metrics.enabled`
- `app.observability.new-relic.metrics.otlp-metrics-endpoint`
- `app.observability.new-relic.metrics.otlp-metrics-protocol`
- `app.observability.new-relic.metrics.license-key`
- `app.observability.new-relic.logging.forwarding-enabled`
- `app.observability.new-relic.logging.forwarding-max-samples-stored`
- `app.observability.new-relic.logging.local-decorating-enabled`

Notes:

- keep the license key out of git
- browser config is build-time data for the frontend image, so populate it before `apps/web` is built
- keep forwarding off until the log volume review says the bot is safe for the free plan
- keep local decorating off when forwarding is on
- Quarkus does not expose the servlet-container JMX pool set that powers the built-in APM `Threads` tab, so the image now ships a custom JMX extension under `/opt/newrelic/extensions`.
- Query custom JVM/thread metrics in New Relic from the `Metric` event with names like `JMX/Runtime/Threads/ThreadCount`.
- Browser logs arrive in the New Relic `Logs` UI and can be filtered by browser app name plus the custom `event` attribute.
- Metrics export uses the Quarkus Micrometer + OpenTelemetry bridge. When `app.observability.new-relic.metrics.enabled=true`, backend JVM and HTTP server meters are exported to the New Relic OTLP endpoint with the `api-key` header sourced from `app.observability.new-relic.metrics.license-key`.
- Quarkus trace and log exporters are intentionally disabled: the New Relic Java agent owns APM traces and application-log forwarding. This prevents the OpenTelemetry SDK from falling back to a nonexistent collector at `localhost:4317` while preserving the separate OTLP metrics pipeline.
- New Relic recommends OTLP/HTTP protobuf for metric ingest.
- slow-request diagnostics emit only when a request crosses `app.performance.http-metrics.slow-request-threshold-ms` or fails with 5xx status
- slow-query diagnostics emit only when a DB-backed operation crosses `app.performance.http-metrics.slow-query-threshold-ms` or throws an exception
- diagnostic log lines include trace and scope context from the active request MDC, so the slow path can be tied back to `traceId`, `familyId`, and `childId`

### Telegram rollout and alerts

Telegram capabilities are independently controlled by `ENABLE_TELEGRAM_MINI_APP`,
`ENABLE_TELEGRAM_BOT`, and `ENABLE_TELEGRAM_NOTIFICATIONS`. During staged rollout,
set `TELEGRAM_ROLLOUT_FAMILY_ID` to the allow-listed family; an empty value allows
all families only when the capability flag is explicitly enabled. Disabling
notifications preserves the application outbox event and records terminal
`SKIPPED_DISABLED` deliveries.

The `earnit.telegram.events` metric is tagged by event and outcome. Alert on
authentication rejects, callback failures, retry exhaustion, and increasing
delivery age; dashboard panels must use aggregate counts and never expose
Telegram IDs, callback payloads, notes, or task/reward text.

[↑ Back to top](#top)

## 🚀 Runtime Modes <a name="runtime-modes"></a>

1. `NEW_RELIC_AGENT_ENABLED=false`
   - default local mode
   - no agent attachment
   - app still runs normally
2. `NEW_RELIC_AGENT_ENABLED=true` with forwarding off
   - first rollout mode
   - APM and JVM telemetry only
3. frontend browser config present with `VITE_NEW_RELIC_BROWSER_ENABLED=true`
   - Browser page views, JS errors, and client logs enabled
   - configure from the Browser app copy/paste snippet before building the web image
4. backend metrics export enabled with `NEW_RELIC_METRICS_ENABLED=true`
   - Micrometer JVM and HTTP server meters are exported to New Relic dashboards over OTLP/HTTP
   - keep `NEW_RELIC_LICENSE_KEY` set so the exporter can send the `api-key` header
   - point the metrics exporter at the New Relic OTLP endpoint before rollout
5. `NEW_RELIC_AGENT_ENABLED=true` with forwarding on
   - only after log volume review
   - keep `debug,trace` denied
   - keep the sample cap explicit

[↑ Back to top](#top)

## 🧪 Validate <a name="validate"></a>

```bash
cd earnit-kids
docker compose --env-file .env.example config
docker compose --env-file .env.example --profile db up --build
```

Checks:

- backend entity appears in New Relic APM
- metrics arrive in New Relic `Metric` data when `NEW_RELIC_METRICS_ENABLED=true`
- browser entity appears in New Relic Browser after opening the web app
- frontend logs appear in New Relic `Logs`
- custom thread metrics appear in New Relic `Metric` data after a few scrape intervals
- logs include the application name and deployment environment
- `docker compose down` still stops the stack cleanly

## 📋 Dashboard Contract <a name="dashboard-contract"></a>

For the widget definitions, NRQL examples, and alert thresholds, see
[newrelic-dashboard.md](newrelic-dashboard.md).

[↑ Back to top](#top)
