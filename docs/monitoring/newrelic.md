# New Relic metrics runbook

<a name="top"></a>

The backend can export metrics to New Relic through OTLP. The integration is
off by default, so local development does not need an account or license key.

## Table of contents

- [⚙️ Configure it](#️-configure-it)
- [🔍 Check it safely](#-check-it-safely)
- [🚨 Troubleshoot](#-troubleshoot)
- [🧪 Release proof](#-release-proof)

## ⚙️ Configure it

Set these deployment secrets or environment variables:

| Variable | Purpose |
| --- | --- |
| `NEW_RELIC_METRICS_ENABLED` | Enables OTLP metric export |
| `NEW_RELIC_LICENSE_KEY` | New Relic ingest credential |
| `NEW_RELIC_OTLP_METRICS_ENDPOINT` | OTLP endpoint; defaults to New Relic |
| `NEW_RELIC_OTLP_METRICS_PROTOCOL` | OTLP protocol; defaults to `http/protobuf` |

Keep the license key only in the deployment secret manager. The backend exports
metrics only through this OTLP pipeline; traces and logs use their own runtime
configuration.

[↑ Back to top](#top)

## 🔍 Check it safely

After deployment, check:

1. `/q/health` reports the service ready.
2. The `new-relic-metrics-config` readiness entry is up when metrics are enabled.
3. Structured logs contain no exporter configuration or authentication error.
4. New Relic receives service metrics after normal application traffic.

Do not paste the license key into a shell transcript, log query, issue, or chat.

[↑ Back to top](#top)

## 🚨 Troubleshoot

| Symptom | Check first |
| --- | --- |
| Readiness fails | `NEW_RELIC_METRICS_ENABLED`, endpoint, protocol, and license key are present |
| No metrics arrive | network egress to the OTLP endpoint and the correct New Relic account |
| Local startup tries an OTLP endpoint | metrics should be disabled locally unless intentionally testing export |
| Logs expose a secret | rotate the key immediately and remove the leaked value from retained logs where possible |

⚠️ A green application health check does not prove New Relic ingestion. Confirm
the metric in the New Relic account after deployment.

[↑ Back to top](#top)

## 🧪 Release proof

Record the deployed version, checked origin, readiness result, and one received
metric. This is deployment evidence, separate from `./mvnw verify`.

[↑ Back to top](#top)
