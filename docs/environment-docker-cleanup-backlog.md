# Environment Examples and Docker Cleanup - Implementation Backlog

## Goal

Make the checked-in environment examples and both Docker Compose modes describe only settings that are consumed by the current runtime, build, or supported maintenance scripts. Operators should have one clear Compose contract without dead settings, ineffective overrides, or documentation for removed push infrastructure.

## Architectural decisions

- The root `.env.example` is the canonical input contract for the root Docker Compose stacks and the backend maintenance scripts that deliberately load root `.env`; it must not claim to configure values that Compose always replaces.
- `docker-compose.yml` and `docker-compose.native.yml` own container wiring. They must pass only the runtime values required by the web edge, Quarkus, PostgreSQL, and build stages; application code retains its documented compatibility fallbacks for direct local runs.
- `apps/backend/.env.example` is a standalone backend-oriented reference, not an alternative Compose source. Keep only properties that Quarkus or backend maintenance scripts actually consume.
- Remove obsolete mobile push/FCM/VAPID and Clarity configuration references rather than retaining inert feature flags. Do not restore the removed push API, schema, or client functionality as part of this cleanup.
- Preserve supported operational settings such as Telegram, Google Identity, database, cache, New Relic, and native build configuration when a current consumer exists, even if the default value is normally sufficient.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | TASK-ENV-001 | P2 | - | Establish the single effective Compose and example-variable contract before editing explanatory documentation. |
| 2 | TASK-ENV-002 | P2 | TASK-ENV-001 | Remove stale operator guidance only after the surviving configuration names and responsibilities are final. |

## TASK-ENV-001: Reduce environment examples and Compose wiring to live contracts

**Status:** DONE
**Priority:** P2  
**Depends on:** -

**Exact scope:**

Audit each entry in the two environment examples and the `web`/`backend` Compose environment blocks against a current runtime, build-time, or supported maintenance-script consumer. Remove entries that have no such consumer or are unconditionally overridden by Compose, then make the JVM and native Compose files expose the same intended configuration contract where their backend mode does not require a difference.

**Files:**

- Modify `.env.example`.
- Modify `apps/backend/.env.example`.
- Modify `docker-compose.yml`.
- Modify `docker-compose.native.yml`.
- Search anchors: `loadAppConfig` in `apps/web/src/lib/server/config.ts`, `resolveProxyContext` in `apps/web/scripts/proxy-context.mjs`, `app.telegram` in `apps/backend/src/main/resources/application.properties`, and `loadEnvFile` in `apps/backend/scripts/lib/db.js`.

**Goal:**

A developer copying the root example can render either Compose stack without supplying inert settings, and every setting retained in either example has an identified active consumer.

### Outcome

The root example no longer contains root-only web values that Compose replaces (`PORT`, `BACKEND_URL`, `LOG_LEVEL`, and the duplicate root `QUARKUS_HTTP_PORT`) or removed push/FCM/VAPID and Clarity settings. Compose no longer injects redundant aliases when the selected runtime path already has a single canonical value.

### Architectural decision

Keep the current application compatibility aliases in code for direct and existing deployments, but do not inject several aliases with identical values from Compose. Retain `DB_PORT` where backend maintenance scripts consume it; do not mistake it for a dead Compose-only setting.

### Required changes

1. Build a variable-to-consumer inventory from Compose interpolation, Docker build arguments, Quarkus property expansion/config mappings, web server configuration, and `apps/backend/scripts/lib/db.js`; classify each example entry as required, optional-but-live, defaulted-and-omittable, overridden, or obsolete.
2. Remove the confirmed obsolete root-example entries: `ENABLE_PUSH_NOTIFICATIONS`, `FCM_PROJECT_ID`, `FCM_SERVICE_ACCOUNT_PATH`, `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`, `VAPID_CONTACT`, and `CLARITY_PROJECT_ID`; remove `PORT`, `BACKEND_URL`, `LOG_LEVEL`, and root `QUARKUS_HTTP_PORT` only after preserving the effective Compose values in their owning service blocks.
3. Reconcile `apps/backend/.env.example` with the same inventory. Keep direct-backend and maintenance-script inputs that are live; remove only entries that no current backend consumer reads.
4. Simplify each Compose service environment block so its explicitly injected values are necessary for that service and do not duplicate a lower-priority alias with the same value. Preserve required service-to-service DNS URLs, health checks, `DATA_DIR`, read-only/tmpfs behavior, profiles, volumes, and the JVM/native image distinction.
5. Update comments next to surviving variables and port mappings so they describe the effective owner and do not imply host-loopback database access from a container.

### Out of scope

- Reintroducing push notifications, FCM, VAPID, Clarity, or their database/API/mobile implementations.
- Renaming supported environment variables or removing compatibility fallback handling from application code.
- Changing credentials in local `.env`, production secrets, Dokploy configuration, image versions, network topology, health-check semantics, or database schema.

### Acceptance criteria

- Every variable retained in `.env.example` and `apps/backend/.env.example` is read by a current Compose interpolation, Docker build/runtime command, application configuration path, or supported backend maintenance script.
- The obsolete push/FCM/VAPID/Clarity entries and the four ineffective root Compose overrides named above are absent from `.env.example`.
- Both Compose files preserve a reachable web-to-backend URL using service DNS, retain `DB_HOST`/`DB_INTERNAL_PORT` for container database access, and do not introduce `localhost` or `127.0.0.1` as a backend-to-database host.
- JVM and native Compose files differ only where their backend image/runtime requirements differ; equivalent web, database, and shared runtime settings have consistent names and values.
- Rendering each stack with the sanitized root example succeeds without unresolved-variable warnings or Compose schema errors.

### Targeted validation

```bash
docker compose --env-file .env.example --profile db config --quiet
docker compose -f docker-compose.native.yml --env-file .env.example --profile db config --quiet
! rg -n "ENABLE_PUSH_NOTIFICATIONS|FCM_PROJECT_ID|FCM_SERVICE_ACCOUNT_PATH|VAPID_PUBLIC_KEY|VAPID_PRIVATE_KEY|VAPID_CONTACT|CLARITY_PROJECT_ID|^PORT=|^BACKEND_URL=|^LOG_LEVEL=|^QUARKUS_HTTP_PORT=" .env.example
git diff --check
```

### Commit

```bash
git add .env.example apps/backend/.env.example docker-compose.yml docker-compose.native.yml
git commit -m "refactor(docker): remove stale environment wiring"
```

## TASK-ENV-002: Remove stale environment documentation and align operator references

**Status:** TODO
**Priority:** P2  
**Depends on:** TASK-ENV-001

**Exact scope:**

Align environment documentation with the completed variable contract, including removal of mobile FCM guidance that references backend endpoints and flags no longer present in the application.

**Files:**

- Modify `README.md`.
- Modify `apps/web/README.md`.
- Modify `apps/web/docs/ARCHITECTURE.md`.
- Modify `apps/mobile/README.md`.
- Delete `apps/mobile/README-firebase-fcm.md` if no remaining current mobile/backend feature links to it.
- Search anchors: `Environment Variables` in `README.md`, `Environment Variables` in `apps/web/README.md`, and `Background push notifications` in `apps/mobile/README.md`.

**Goal:**

Documentation tells operators which live variable owns each behavior and no longer instructs them to enable infrastructure that the repository cannot use.

### Outcome

The root and web environment references match the final samples and Compose contract. Mobile documentation no longer claims that `/api/push/register`, FCM configuration, or `ENABLE_PUSH_NOTIFICATIONS` are supported.

### Architectural decision

Documentation mirrors the canonical example and configuration consumers from TASK-ENV-001; it does not become a second variable registry. The web documentation may still describe compatibility aliases that application code supports, but must label them as such and not require Compose to inject all aliases.

### Required changes

1. Update the root README environment table and Compose notes to describe only live, effective root-example variables and the container-network database contract.
2. Reconcile web README and architecture tables with `loadAppConfig` and `resolveProxyContext`, distinguishing preferred names, compatibility aliases, and values Compose owns.
3. Remove obsolete push configuration snippets, endpoint claims, troubleshooting steps, and references from mobile documentation; delete the standalone FCM guide when it has no supported consumer or inbound link.
4. Search the maintained documentation for the removed variable names and correct or remove every active-reference result without changing historical backlog records.

### Out of scope

- New mobile notification design or provider integration.
- Editing completed backlog files solely to erase historical context.
- Changes to application behavior, test fixtures, deployment secrets, or Docker images beyond TASK-ENV-001.

### Acceptance criteria

- A new operator can follow the README Compose quick start using only the sanitized root example and understands that application containers reach PostgreSQL at the Compose service host, normally `db`.
- No maintained documentation instructs users to configure `ENABLE_PUSH_NOTIFICATIONS`, FCM, VAPID, Clarity, or a removed push-registration endpoint.
- Web documentation states the live preferred configuration names and describes aliases only where they remain supported by current code.
- All documentation paths and links changed by the task resolve to existing files; a removed FCM guide has no remaining active repository link.

### Targeted validation

```bash
! rg -n --glob '*.md' --glob '!docs/*backlog*.md' "ENABLE_PUSH_NOTIFICATIONS|FCM_|VAPID_|CLARITY_PROJECT_ID|/api/push/register" README.md apps docs
! rg -n "apps/mobile/README-firebase-fcm.md|README-firebase-fcm" README.md apps docs .github
git diff --check
```

### Commit

```bash
git add README.md apps/web/README.md apps/web/docs/ARCHITECTURE.md apps/mobile/README.md apps/mobile/README-firebase-fcm.md
git commit -m "docs: remove obsolete environment guidance"
```
