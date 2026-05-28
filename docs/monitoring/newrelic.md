# New Relic Monitoring

<a name="top"></a>

## Table of Contents

- [🎯 Scope](#scope)
- [🔐 Required Environment Variables](#required-environment-variables)
- [🚀 Runtime Modes](#runtime-modes)
- [🧪 Validate](#validate)

This repo uses one backend-only New Relic path:

- JVM agent inside `apps/backend`
- structured JSON logs on stdout
- log forwarding disabled by default until volume review is complete

[↑ Back to top](#top)

## 🎯 Scope <a name="scope"></a>

- Backend APM and JVM telemetry for `earnit-kids-backend`
- Logs-in-context from the Quarkus container stdout stream
- no second log pipeline by default

[↑ Back to top](#top)

## 🔐 Required Environment Variables <a name="required-environment-variables"></a>

Runtime:

- `NEW_RELIC_LICENSE_KEY`
- `NEW_RELIC_APP_NAME=earnit-kids-backend`
- `NEW_RELIC_AGENT_ENABLED=false`
- `DEPLOYMENT_ENV=development`
- `NEW_RELIC_APPLICATION_LOGGING_FORWARDING_ENABLED=false`
- `NEW_RELIC_APPLICATION_LOGGING_FORWARDING_MAX_SAMPLES_STORED=10000`
- `NEW_RELIC_APPLICATION_LOGGING_LOCAL_DECORATING_ENABLED=false`

Notes:

- keep the license key out of git
- keep forwarding off until the log volume review says the bot is safe for the free plan
- keep local decorating off when forwarding is on

[↑ Back to top](#top)

## 🚀 Runtime Modes <a name="runtime-modes"></a>

1. `NEW_RELIC_AGENT_ENABLED=false`
   - default local mode
   - no agent attachment
   - app still runs normally
2. `NEW_RELIC_AGENT_ENABLED=true` with forwarding off
   - first rollout mode
   - APM and JVM telemetry only
3. `NEW_RELIC_AGENT_ENABLED=true` with forwarding on
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
- logs include the application name and deployment environment
- `docker compose down` still stops the stack cleanly

[↑ Back to top](#top)
