# Web and Telegram Mini App Access - Review Remediation Backlog

## Goal

Remediate the confirmed security and delivery defects found while reviewing completed tasks in `docs/web-miniapp-access-backlog.md`. Parent invitations must use a rotatable secret outside source control; browser notifications must use the Web Push protocol end to end; and the installed PWA must control its actual workspace entry URL and expose a usable update lifecycle.

## Architectural decisions

- **TASK-WMAR-001 — P0:** `ParentInvitationService` currently hashes invitation tokens with the literal `local-parent-invitation-pepper-change-me` (`ParentInvitationService.java:33,191-196`). This violates TASK-WM-002's required configured, separately rotatable HMAC pepper. Move token hashing into one configuration-backed component, persist a non-secret key identifier with each digest, and support verification of still-valid invitations issued by the previous configured key during rotation. Do not reuse JWT, OAuth, or Telegram callback secrets.
- **TASK-WMAR-002 — P1:** Browser subscription creation omits `applicationServerKey` (`browserPush.ts:30`) and delivery posts unencrypted JSON with a non-VAPID authorization value (`WebPushService.java:94-103`). These are not valid Web Push/VAPID requests, so a configured real browser/provider cannot subscribe and receive a notification. Keep subscription ownership and outbox planning in their current layers, but use one standards-compliant Web Push adapter and expose only the VAPID public key through an authenticated/read-only API contract.
- **TASK-WMAR-003 — P1:** The manifest starts at `/workspace` but both it and registration use `/workspace/` scope (`manifest.json:5-6`, `registerServiceWorker.ts:4`), so the worker cannot control the installed start URL. In addition, the install handler calls `skipWaiting` before any UI handles the dispatched update event (`sw.js:10-12`, `registerServiceWorker.ts:5-10`). Register one workspace PWA lifecycle with a scope that includes the canonical entry route, keep an update worker waiting until an accessible user action sends `SKIP_WAITING`, and reload once after `controllerchange`. Do not add caches for authenticated API, invitation, child-link, or OAuth URLs.
- Rejected observation: the focused Java and web unit tests pass, but they use mocks and subscription serialization only; they do not prove a real VAPID subscription, encrypted Web Push request, service-worker control of `/workspace`, or waiting-worker update path. This is missing regression coverage, not evidence that the existing contracts work.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | TASK-WMAR-001 | P0 | - | Removes the source-controlled invitation-token secret before more invitation links are issued. |
| 2 | TASK-WMAR-002 | P1 | - | Establishes the valid push subscription/delivery contract used by the browser UI. |
| 3 | TASK-WMAR-003 | P1 | TASK-WMAR-002 | Completes the browser-side PWA and push lifecycle against the corrected public-key contract. |

## TASK-WMAR-001: Externalize and rotate the parent-invitation token pepper

**Status:** DONE
**Priority:** P0
**Depends on:** -

**Exact scope:**

Replace the hard-coded token pepper used in the email parent-invitation create, resend, entry, and OAuth-continuation flow. Preserve pending invitations safely across a configured key rotation.

**Files:**

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/application/invitation/ParentInvitationService.java`.
- Create `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/application/invitation/ParentInvitationTokenHasher.java` (or the existing security/config extension point found for HMAC key rotation).
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/domain/model/invitation/ParentEmailInvitationEntity.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/infrastructure/persistence/invitation/ParentEmailInvitationRepository.java`.
- Create the next sequential Flyway migration under `apps/backend/src/main/resources/db/migration/` to add a non-secret digest key identifier and backfill the legacy rows deliberately.
- Modify `apps/backend/src/main/resources/application.properties` and `.env.example`.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/application/invitation/ParentInvitationServiceImplTest.java`.

**Goal:**

Raw parent-invitation tokens are looked up only with a configured, independently rotatable HMAC key; the key itself never appears in committed source, API responses, or audit records.

### Outcome

An operator can issue new invitations with an active key and rotate to a new key without incorrectly accepting a token under an unrelated key or silently breaking a pending invitation that remains inside its valid migration window.

### Architectural decision

The hasher owns HMAC-SHA-256 and key selection. The invitation row stores the active key's public identifier with its digest so lookup remains deterministic; the configuration owns the active/previous secret material. The existing 256-bit opaque token and repository/service boundaries remain unchanged.

### Required changes

1. Remove the literal pepper and inject validated configuration through a dedicated token-hashing boundary; fail startup or invitation operations safely when the required active key is absent outside an explicitly documented local-development mode.
2. Add an immutable, non-secret key identifier to parent-email invitations. New and resent links use the active identifier; lookup hashes only against the row's key identifier, with a bounded, documented legacy migration path for pre-existing pending rows.
3. Keep token values, HMAC inputs, configured secret values, and digests out of public DTOs, error messages, logs, and audits.
4. Add tests for active-key issuance, successful lookup after configured rotation, unknown/retired key rejection, legacy-row migration behavior, and no source-default fallback.

### Out of scope

- Changing child magic-link or Telegram token formats.
- Changing invitation expiry, email delivery provider, or Google OAuth authorization rules.

### Acceptance criteria

- No parent-invitation digest is derived from a source-controlled default secret.
- A new invitation can be entered and redeemed with the configured active key.
- A valid pending invitation issued under the immediately previous configured key behaves according to the documented rotation window; an unknown or retired key identifier never accepts a token.
- Rotating the active key does not expose a raw token or key material through API, audit, logs, or database fields.
- PostgreSQL and H2 migration tests cover a database containing pre-migration pending invitations.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -Dtest=ParentInvitationServiceImplTest test
```

### Commit

```bash
git add apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/application/invitation apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/domain/model/invitation apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/infrastructure/persistence/invitation apps/backend/src/main/resources/db/migration apps/backend/src/main/resources/application.properties .env.example apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/application/invitation/ParentInvitationServiceImplTest.java
git commit -m "fix(backend): rotate parent invitation token keys"
```

## TASK-WMAR-002: Implement standards-compliant browser Web Push

**Status:** DONE
**Priority:** P1
**Depends on:** -

**Exact scope:**

Make the existing authenticated subscription and outbox flow interoperable with a real Web Push browser endpoint by supplying the public VAPID key to the browser and encrypting/signing delivery server-side.

**Files:**

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/api/PushResource.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/webpush/WebPushConfig.java` and `WebPushService.java`.
- Modify `apps/backend/pom.xml` to add the selected maintained Web Push implementation, if one is not already available transitively.
- Modify `apps/backend/src/main/resources/application.properties` and `.env.example` only for public-key/readiness configuration that is not already represented.
- Modify `apps/backend/src/test/java/com/sashplatonov/earnit/kids/platform/api/PushResourceTest.java` and `apps/backend/src/test/java/com/sashplatonov/earnit/kids/platform/webpush/WebPushServiceTest.java`.
- Modify `apps/web/src/lib/features/workspace/notifications/browserPush.ts` and `apps/web/src/lib/services/api.ts`.
- Modify `apps/web/tests/unit/browserPush.test.ts`.

**Goal:**

On a supported HTTPS browser, a user can register a VAPID-bound subscription and receive a protocol-valid encrypted notification from the outbox without exposing the private key.

### Outcome

The browser and server use one explicit Web Push contract: the public VAPID key is supplied only for `PushManager.subscribe`, while a dedicated backend adapter creates the encrypted payload and VAPID authorization expected by the endpoint.

### Architectural decision

`WebPushService` remains responsible for authenticated subscription ownership and event eligibility. A protocol adapter owns RFC-compliant subscription encryption and VAPID JWT signing, rather than manually composing HTTP headers or JSON. The public key may be returned by a narrow configuration endpoint; the private key remains server-only.

### Required changes

1. Add an authenticated, cache-safe read contract for the public VAPID key, with deterministic disabled/unconfigured behavior; do not return the private key, subject secrets, or arbitrary configuration.
2. Pass the decoded public key as `applicationServerKey` when subscribing. Treat missing/invalid public key and browser subscription failures as actionable non-subscribed UI states.
3. Replace manual `HttpClient` JSON delivery with a maintained protocol implementation that encrypts to each subscription's `p256dh`/`auth` keys and creates a correctly signed VAPID authorization for its endpoint audience.
4. Preserve current ownership/rebind, outbox retry, 404/410 deletion, and secret-redaction behavior. Distinguish invalid configuration, transient transport failure, and permanent invalid endpoint in persisted delivery outcomes.
5. Add contract tests with a controllable endpoint/assertions for encrypted payload and VAPID authorization, plus browser-unit coverage asserting the public key is required and passed to `subscribe`.

### Out of scope

- New notification preferences or changes to Telegram notification delivery.
- Native/mobile push and live VAPID-provider credentials.
- PWA update UI, handled by TASK-WMAR-003.

### Acceptance criteria

- In a supported browser, subscription creation calls `PushManager.subscribe` with `userVisibleOnly: true` and the decoded public VAPID key; it does not call the server registration endpoint after a failed subscription.
- The public-key endpoint never exposes private VAPID material and its unconfigured response does not make the browser prompt repeatedly.
- A test endpoint can decrypt a planned delivery using the registered subscription keys and validate VAPID authorization; plaintext JSON POSTs are not accepted as a passing delivery.
- Existing authorization ensures a parent or child can only register/remove their own active-family subscription.
- `404`/`410` still delete the endpoint and transient errors still retry outside the originating domain transaction.

### Targeted validation

```bash
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw -Dtest=WebPushServiceTest,PushResourceTest test && cd ../web && npm run test -- tests/unit/browserPush.test.ts
```

### Commit

```bash
git add apps/backend/pom.xml apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/api/PushResource.java apps/backend/src/main/java/com/sashplatonov/earnit/kids/platform/webpush apps/backend/src/main/resources/application.properties .env.example apps/backend/src/test/java/com/sashplatonov/earnit/kids/platform apps/web/src/lib/features/workspace/notifications/browserPush.ts apps/web/src/lib/services/api.ts apps/web/tests/unit/browserPush.test.ts
git commit -m "fix(backend): deliver standards-compliant web push"
```

## TASK-WMAR-003: Make the workspace PWA control its entry route and update safely

**Status:** DONE
**Priority:** P1
**Depends on:** TASK-WMAR-002

**Exact scope:**

Correct the service-worker scope/manifest relationship for `/workspace`, restore a user-mediated update path, and make the E2E test prove control rather than merely registration.

**Files:**

- Modify `apps/web/src/lib/features/workspace/pwa/registerServiceWorker.ts`.
- Modify `apps/web/src/routes/+layout.svelte` or create a focused workspace PWA update component under `apps/web/src/lib/features/workspace/pwa/`.
- Modify `apps/web/static/sw.js`.
- Modify `apps/web/static/manifest.json`.
- Modify `apps/web/tests/e2e/pwa-browser-push.spec.ts`.
- Modify or create unit tests under `apps/web/tests/unit/` for service-worker update state if the behavior is extracted.

**Goal:**

The installed PWA's actual `/workspace` launch URL is controlled by its worker, while an available update is announced accessibly and activated only after the user chooses it.

### Outcome

The PWA has one lifecycle owner: it registers a worker whose scope includes `/workspace`, shows an update control only for `registration.waiting`, sends `SKIP_WAITING` to that waiting worker, and reloads exactly once after a `controllerchange`.

### Architectural decision

Align `start_url`, manifest scope, and registration scope with the canonical workspace route (including its no-trailing-slash form). The service worker must not call `skipWaiting` during installation; its existing message listener is the sole activation path. Existing no-cache exclusions and safe offline response remain in the worker.

### Required changes

1. Choose and apply a single valid scope that controls `/workspace` and all intended workspace subroutes; make `start_url` and manifest scope match it without accidentally taking ownership of Telegram or invitation flows.
2. Remove automatic `skipWaiting` from installation. Surface a visible, keyboard-accessible, announced update action only when a genuinely waiting worker exists; handle activation timeout/error and clean up event listeners.
3. After user confirmation, post `SKIP_WAITING` to the waiting registration and reload one time on `controllerchange`; do not reload loops or interrupt initial installation.
4. Retain the explicit fetch exclusions for `/api/`, parent invitations, child magic links, and OAuth paths, and keep offline navigation free of protected cached data.
5. Upgrade production-preview E2E to assert `navigator.serviceWorker.controller` is non-null at `/workspace`, the cache exclusions work while offline, and an update uses a waiting worker plus one user-triggered reload. Preserve Telegram fixture coverage.

### Out of scope

- Offline task/reward mutations, background sync, or native packaging.
- New browser-notification preferences or backend delivery changes beyond consuming TASK-WMAR-002's public-key contract.

### Acceptance criteria

- A production-built installed/normal browser at `/workspace` is controlled by the active service worker, including after a reload.
- The update notice is absent on first install and appears only for `registration.waiting`; it is keyboard reachable, has a clear accessible name/status, and works at 320px.
- Choosing the update activates the waiting worker and causes exactly one reload; dismissing/ignoring it does not activate the update early.
- Offline requests never serve cached authenticated API responses, invitation paths, child-link paths, or OAuth callback data.
- The E2E test fails if a worker is merely active but does not control the workspace entry page.

### Targeted validation

```bash
cd apps/web && npm run lint && PLAYWRIGHT_USE_PREVIEW=true npm run test:e2e -- tests/e2e/pwa-browser-push.spec.ts && npm run build
```

### Commit

```bash
git add apps/web/src/lib/features/workspace/pwa apps/web/src/routes/+layout.svelte apps/web/static/sw.js apps/web/static/manifest.json apps/web/tests/e2e/pwa-browser-push.spec.ts apps/web/tests/unit
git commit -m "fix(web): control workspace PWA updates safely"
```
