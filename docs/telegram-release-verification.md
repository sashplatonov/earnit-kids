# Telegram release verification

This checklist separates local proof from remote and real-client evidence.

## Local revision gates

```bash
docker compose config --quiet
docker compose -f docker-compose.native.yml config --quiet
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -Dtest=TelegramCrossChannelIntegrationTest verify
cd apps/web && npm run lint && npm run test -- --run && npm run build
cd apps/web && npm run test:e2e -- telegram-auth.spec.ts telegram-parent.spec.ts telegram-child.spec.ts telegram-activity.spec.ts telegram-consistency.spec.ts telegram-layout.spec.ts --workers=1
```

The local tests use a mocked Telegram transport and browser routes. They prove
server reconciliation and mobile geometry, not BotFather configuration or a
physical Telegram client.

The current role contracts are intentionally narrow: Parent Mini App tabs are
Home, Tasks, Rewards, and Family; Child tabs are Today, Rewards, and Activity.
Bot menus are bounded quick actions and every Mini App control must resolve
through the shared SVG vocabulary; Bot buttons use the shared emoji vocabulary.

## Remote CI and deployment evidence

Record the workflow URL, commit SHA, Compose config result, migration result,
and deployment health checks for the exact revision. A green local run is not
remote CI evidence. Keep bot secrets in the deployment secret store.

When the bot capability is enabled, backend startup registers the public
`/api/telegram/webhook` endpoint derived from `TELEGRAM_MINI_APP_URL` and
reconciles it every `TELEGRAM_WEBHOOK_REGISTRATION_INTERVAL` (five minutes by
default). Confirm the deployment log reports successful registration before
testing `/start`; it must not print the bot token or webhook secret.

## Staged Telegram client checklist

- In BotFather, set the bot's Main Mini App URL to `https://your-domain/telegram`.
  Set `TELEGRAM_MINI_APP_URL` to the same HTTPS URL. A `startapp` deep link opens
  the BotFather URL, so it must reach the `/telegram` authentication gate.
- Enable Mini App, bot, and notifications independently for the allow-listed family.
- Verify parent link, child invite, revoke, expiry, and re-link conflict handling.
- Verify child task/reward requests, parent approve/reject, balance adjustment,
  stale callback refresh, retry, and notification delivery.
- Check 320/390px safe areas, keyboard focus, and message editing in a real client.
- Confirm legacy web login, requests, balance, history, and catalog flows remain intact.

## Retention operations

Retention is enabled by `ENABLE_TELEGRAM_RETENTION` and runs once per
`TELEGRAM_RETENTION_POLL_INTERVAL` (24h by default). Operational records use
30-day windows by default; security audit records use a separate 365-day window.
`TELEGRAM_RETENTION_BATCH_SIZE` bounds each category per run. The service's
count-only dry run reports eligible row counts without exposing payloads or
Telegram identities. Pending or retryable deliveries protect their outbox
events; cleanup never touches canonical family, request, balance, or history
data.

## Rollback

Disable the relevant capability flag first. This stops new Telegram access or
delivery without deleting identities, committed domain events, balances, or
history. Re-enable only after the failure mode and delivery backlog are reviewed.
