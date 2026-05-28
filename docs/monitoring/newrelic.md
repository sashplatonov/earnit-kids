# New Relic Monitoring

<a name="top"></a>

## Table of Contents

- [🎯 Scope](#scope)
- [🔐 Required Environment Variables](#required-environment-variables)
- [🚀 Runtime Modes](#runtime-modes)
- [🧪 Validate](#validate)

This repo uses one New Relic path split by runtime:

- JVM agent inside `apps/backend`
- Browser agent inside `apps/web`
- structured JSON logs on stdout
- browser logs and JS errors sent from the SvelteKit client when browser config is present
- backend log forwarding disabled by default until volume review is complete

[↑ Back to top](#top)

## 🎯 Scope <a name="scope"></a>

- Backend APM and JVM telemetry for `earnit-kids-backend`
- Browser page views, JS errors, and client-side logs for the web app
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
- `VITE_NEW_RELIC_BROWSER_ENABLED=false`
- `VITE_NEW_RELIC_BROWSER_INFO=` copied from the Browser app `NREUM.info` snippet
- `VITE_NEW_RELIC_BROWSER_INIT=` copied from the Browser app `NREUM.init` snippet
- `VITE_NEW_RELIC_BROWSER_LOADER_CONFIG=` copied from the Browser app `NREUM.loader_config` snippet

Notes:

- keep the license key out of git
- browser config is build-time data for the frontend image, so populate it before `apps/web` is built
- keep forwarding off until the log volume review says the bot is safe for the free plan
- keep local decorating off when forwarding is on
- Quarkus does not expose the servlet-container JMX pool set that powers the built-in APM `Threads` tab, so the image now ships a custom JMX extension under `/opt/newrelic/extensions`.
- Query custom JVM/thread metrics in New Relic from the `Metric` event with names like `JMX/Runtime/Threads/ThreadCount`.
- Browser logs arrive in the New Relic `Logs` UI and can be filtered by browser app name plus the custom `event` attribute.

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
4. `NEW_RELIC_AGENT_ENABLED=true` with forwarding on
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
- browser entity appears in New Relic Browser after opening the web app
- frontend logs appear in New Relic `Logs`
- custom thread metrics appear in New Relic `Metric` data after a few scrape intervals
- logs include the application name and deployment environment
- `docker compose down` still stops the stack cleanly

[↑ Back to top](#top)
