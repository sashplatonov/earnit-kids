# New Relic monitoring

EarnIt Kids keeps New Relic observability on the backend. The JVM agent owns
backend APM and application-log forwarding; Quarkus Micrometer exports backend
metrics over OTLP/HTTP. The web app does not embed a New Relic Browser agent or
read public browser-monitoring build settings.

## Backend runtime settings

- `NEW_RELIC_AGENT_ENABLED` enables the Java agent.
- `NEW_RELIC_LICENSE_KEY` and `NEW_RELIC_APP_NAME` identify the backend.
- `NEW_RELIC_METRICS_ENABLED` enables OTLP metrics export.
- `NEW_RELIC_OTLP_METRICS_ENDPOINT` and `NEW_RELIC_OTLP_METRICS_PROTOCOL` select
  the metrics destination and protocol.
- `NEW_RELIC_APPLICATION_LOGGING_FORWARDING_ENABLED`,
  `NEW_RELIC_APPLICATION_LOGGING_FORWARDING_MAX_SAMPLES_STORED`, and
  `NEW_RELIC_APPLICATION_LOGGING_LOCAL_DECORATING_ENABLED` control optional
  agent log forwarding.

All settings default to the safe local-development mode in `.env.example`.
Backend values are injected by the Compose backend service and are independent
of the web build.

## Validation

```bash
docker compose --env-file .env.example --profile db config --quiet
```

When enabled, verify the backend entity and metrics in New Relic. Browser
entities, browser logs, and client-side JavaScript error events are not part of
the supported observability contract.
