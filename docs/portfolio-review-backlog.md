# Public Portfolio Review - Remediation Backlog

## Goal

Make EarnIt Kids safe to publish as a public repository and easy for a hiring
manager to assess as a Senior Java backend portfolio. The scope is deliberately
small: remove real publication risks, make the most important invariants
auditable, and document evidence that already exists in the codebase.

## Review evidence and architectural decisions

- **P0 publication blocker:** `apps/mobile/earnit-kids.keystore` is tracked and
  was introduced in commit `75546922`. A signing keystore is private key
  material, even when its password is not in the repository. It must be treated
  as compromised: remove it from every reachable ref, rotate the Android signing
  key, and prevent recurrence. Current CI scans only `apps/backend` and
  `apps/web`, so it does not cover this file or Git history.
- **Tenant and channel boundaries are sound in the reviewed mutation path.**
  `FamilyActionSupportService` scopes children, tasks, rewards, requests, and
  history by the authenticated family. `TelegramInitDataVerifier` verifies the
  Telegram HMAC, rejects duplicate fields, and enforces `auth_date` freshness.
  `FamilyActionRequestService` locks a request and then locks its child before
  resolving it. Do not replace this modular monolith with per-channel business
  logic or distributed locks.
- **Balance is a cached mutable projection, not an audit ledger.**
  `FamilyActionBalanceService` mutates `ChildEntity.balance` directly and writes
  a deletable `HistoryEntryEntity`; `deleteHistoryEntry` both changes the balance
  and removes the original event. This prevents reconstructing a balance change.
  Keep the existing history table and family action boundary, but make coin
  events append-only and reverse them with a compensating entry.
- **Sensitive public entrypoints have no inbound rate limit.** The public child
  magic-link endpoint (`ChildMagicLinkResource`) and invitation/OAuth entrypoints
  rely on high-entropy tokens but have no request throttling. Reuse Quarkus
  configuration and a small bounded local limiter; do not introduce Redis solely
  for this portfolio application.
- **Portfolio documentation should describe present behavior only.** The README
  currently has a useful run path but links to retired planning backlogs instead
  of surfacing the existing architecture, CI, security, and cross-channel
  guarantees.

## Rejected observations

- No hard-coded Telegram bot token, JWT signing value, OAuth client secret, or
  production database credential was found in the checked tracked text or
  reachable text-history patterns. `.env` is ignored and `.env.example` uses
  local-safe placeholders/defaults. This is not proof that an external secret
  manager or deployed environment has never leaked a credential.
- Do not add Kafka, microservices, CQRS/event sourcing, Kubernetes, Redis, or a
  full tracing platform. The existing Quarkus modular monolith, PostgreSQL,
  transactional outbox, health checks, JSON logs, trace correlation, and metrics
  are proportionate to the product.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | PORT-001 | P0 | - | Removes private signing material before any public push. |
| 2 | PORT-002 | P1 | PORT-001 | Makes secret scanning cover the whole public repository and history. |
| 3 | PORT-003 | P1 | - | Protects public, bearer-token entrypoints from brute force and abuse. |
| 4 | PORT-004 | P1 | - | Establishes an auditable coin invariant before presentation work. |
| 5 | PORT-005 | P1 | PORT-004 | Proves isolation and concurrent balance correctness against PostgreSQL. |
| 6 | PORT-006 | P1 | PORT-001, PORT-002 | Turns the repository landing page into an evidence-backed portfolio page. |
| 7 | PORT-007 | P2 | PORT-006 | Removes local/planning noise from the public surface. |

## Portfolio prioritization

| Priority | Task | Effort | Portfolio impact | Risk if skipped |
| --- | --- | ---: | ---: | --- |
| P0 | PORT-001 Purge and rotate the Android signing keystore | M | 5/5 | Critical private-key exposure in a public repository |
| P1 | PORT-002 Scan repository history and all project areas | S | 5/5 | Future or historical credential exposure is missed |
| P1 | PORT-003 Throttle public authentication entrypoints | M | 4/5 | Brute-force and resource-abuse exposure |
| P1 | PORT-004 Make coin history auditable | M | 5/5 | Balance changes cannot be reconstructed or reliably reversed |
| P1 | PORT-005 Prove isolation and concurrent mutations in a database | M | 5/5 | Senior-level safety claims remain mostly mocked |
| P1 | PORT-006 Rewrite the README for hiring managers | S | 5/5 | Strong engineering work is not visible in the first five minutes |
| P2 | PORT-007 Remove repository noise and retired plans | S | 3/5 | Public surface looks unfinished and machine-specific |

## PORT-001: Purge and rotate the tracked Android signing keystore

**Status:** TODO
**Priority:** P0  
**Depends on:** -

**Exact scope:** Remove `apps/mobile/earnit-kids.keystore` from the working tree
and all reachable Git history, invalidate the associated Android signing key,
and make Android signing material an external release input.

**Files:**

- Delete `apps/mobile/earnit-kids.keystore`.
- Modify `.gitignore`.
- Modify the Android release documentation under `apps/mobile/`.

### Outcome

No private signing material is present in a public clone or its reachable
history, and a maintainer can supply a replacement key without committing it.

### Architectural decision

Android signing is deployment/release infrastructure, not an application asset.
Use a new key held in a CI secret store or a local ignored path. Treat the old
key as compromised even if it is not currently used for a store release.

### Required changes

1. Generate and securely store a replacement signing key; update any app-store
   registration or CI signing configuration before publishing.
2. Remove the old file and add `*.keystore`, `*.jks`, and release-key aliases to
   `.gitignore` without ignoring public build configuration.
3. Rewrite every reachable ref with `git filter-repo` (or BFG), force-push only
   after coordinating with every collaborator, and require fresh clones.
4. Document the external signing-key contract without recording passwords,
   aliases, certificate fingerprints, or paths that identify a private machine.

### Out of scope

- Repackaging the Capacitor application.
- Changing Android application identifiers or store listings beyond key rotation.

### Acceptance criteria

- `git ls-files` does not list a keystore or JKS file.
- `git log --all -- apps/mobile/earnit-kids.keystore` has no result after the
  history rewrite.
- A fresh clone cannot recover the old keystore from any reachable ref.
- Release documentation identifies the required secret inputs without exposing
  their values.

### Targeted validation

```bash
git ls-files | rg -i '(\.keystore$|\.jks$)'
git log --all -- apps/mobile/earnit-kids.keystore
```

### Commit

```text
chore(security): remove tracked Android signing material
```

## PORT-002: Scan the complete repository and its history for publication secrets

**Status:** TODO
**Priority:** P1  
**Depends on:** PORT-001

**Exact scope:** Extend the security gate beyond `apps/backend` and `apps/web` so
root files, `apps/mobile`, binary-key filenames, and the Git history receive a
publication-oriented secret check.

**Files:**

- Modify `.github/workflows/quality.yml`.
- Create or modify the repository security-scan configuration as supported by
  the selected scanner.
- Modify `README.md` only if the contributor verification command changes.

### Outcome

A pull request cannot reintroduce credential-shaped files anywhere in the
repository, and a release/publication check examines reachable history.

### Architectural decision

Keep Trivy for filesystem vulnerabilities if useful, but add a history-aware
secret scanner such as Gitleaks. Do not claim that a tree-only scan proves
history is clean.

### Required changes

1. Add an explicit root-repository scan, including `apps/mobile`, rather than
   relying on the two current directory-scoped Trivy jobs.
2. Add a separate full-history secret scan to the public-release workflow and a
   fast changed-files/pre-commit variant for normal development.
3. Add narrow documented allowlists only for known non-secret test fixtures; do
   not suppress real private-key file types.
4. Upload machine-readable reports only as private CI artifacts, not to the
   public repository.

### Out of scope

- Replacing all current dependency or misconfiguration scanning.
- Checking secrets into GitHub Actions variables.

### Acceptance criteria

- A deliberately introduced test token or `*.keystore` fixture makes the new
  scan fail outside an explicit test-only allowlist.
- The workflow scans repository root and `apps/mobile`.
- The publication command scans every reachable commit.

### Targeted validation

```bash
gitleaks git --log-opts="--all" --redact
```

### Commit

```text
ci(security): scan repository history before publication
```

## PORT-003: Throttle public bearer-token and authentication entrypoints

**Status:** TODO
**Priority:** P1  
**Depends on:** -

**Exact scope:** Apply a small, configurable inbound rate limit to public child
magic-link, parent-invitation, OAuth-start/callback, and Telegram auth-exchange
entrypoints. Return a consistent `429` response with a safe retry signal.

**Files:**

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/identity/api/resource/auth/ChildMagicLinkResource.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/telegram/api/resource/TelegramMiniAppAuthResource.java`.
- Search anchors: `ParentInvitationEntryResource` and OAuth resource classes in
  `apps/backend/src/main/java/com/sashplatonov/earnit/kids/identity/api/resource/`.
- Create a small security/application limiter in the existing backend security
  package and add configuration to `.env.example` and `application.properties`.
- Add focused tests under `apps/backend/src/test/java`.

### Outcome

Repeated invalid or automated requests do not create unbounded work or reveal
whether a token, account, or invitation exists; legitimate browser and Telegram
flows remain usable.

### Architectural decision

Rate-limit before expensive token verification with bounded local state keyed by
trusted client address plus route. Trust forwarded addresses only through the
existing reverse-proxy contract. A per-instance limiter is an intentional first
step; document that cluster-wide enforcement belongs at the ingress if the app
is horizontally scaled.

### Required changes

1. Define route-specific limits and cooldowns in configuration, disabled only
   for deterministic tests.
2. Preserve existing generic failure responses and avoid logging raw magic-link,
   invitation, OAuth state, or Telegram `initData` values.
3. Return `429 Too Many Requests` with `Retry-After` when a limit is exceeded.
4. Add tests for threshold, expiry/reset, route separation, and a successful
   request below the threshold.

### Out of scope

- Redis or a distributed rate-limiting service.
- A CAPTCHA or user-facing challenge flow.

### Acceptance criteria

- Excess requests to each public sensitive route return `429` and a retry hint.
- Valid Telegram HMAC/freshness and child-link behavior remains unchanged below
  the limit.
- Logs and error bodies never contain a raw bearer token or `initData`.

### Targeted validation

```bash
cd apps/backend && ./mvnw -Dtest='*RateLimit*Test,*ChildMagicLinkResourceTest,*TelegramMiniAppAuthResourceTest' test
```

### Commit

```text
fix(security): throttle public authentication entrypoints
```

## PORT-004: Make coin history append-only and enforce the balance invariant

**Status:** TODO
**Priority:** P1  
**Depends on:** -

**Exact scope:** Turn the current `history` write path into an auditable internal
coin ledger while retaining `children.balance` as the fast current projection.
Replace destructive history deletion with an explicit compensating reversal.

**Files:**

- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/application/action/FamilyActionBalanceService.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/application/action/FamilyActionHistoryFactory.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/domain/model/history/HistoryEntryEntity.java`.
- Modify `apps/backend/src/main/java/com/sashplatonov/earnit/kids/family/api/resource/FamilyHistoryResource.java`.
- Create a new Flyway migration in `apps/backend/src/main/resources/db/migration/`.
- Add service and PostgreSQL/H2 persistence tests near the existing family action
  tests.

### Outcome

Every coin change has an immutable reason, signed delta, timestamp, child and
family scope, and optional reference to the event it reverses. A current balance
cannot become negative unless the product explicitly introduces a debt feature.

### Architectural decision

Reuse `HistoryEntryEntity` rather than introduce a parallel accounting
subsystem. Add a constrained event/reason model (`TASK_REWARD`,
`REWARD_PURCHASE`, `MANUAL_ADJUSTMENT`, `REVERSAL`) and a nullable
`reverses_entry_id`; never delete a settled event. Database constraints back the
service invariant, while the pessimistic child lock remains the concurrency
mechanism.

### Required changes

1. Persist an explicit reason and reversal reference for every current balance
   mutation, including request approval and manual adjustment.
2. Replace history deletion with an authorized reversal that writes a new row
   and prevents a second reversal of the same entry.
3. Reject any adjustment or reversal that would make `children.balance` below
   zero; add a forward-only database check only after a data migration proves
   existing rows comply.
4. Preserve historical task/reward names and amounts as the event snapshot, so
   later catalog edits cannot rewrite past accounting evidence.

### Out of scope

- Real-money payments, tax records, or external accounting integration.
- Event sourcing for the rest of the domain.

### Acceptance criteria

- A task reward, reward purchase, manual adjustment, and reversal each create
  one immutable, classified entry.
- The original entry remains queryable after reversal and cannot be reversed
  twice.
- Direct API and database constraints reject a negative resulting balance.
- Existing family scoping, daily reward limits, and outbox event semantics stay
  intact.

### Targeted validation

```bash
cd apps/backend && ./mvnw -Dtest='FamilyActionServiceImplTest,RepositorySmokeTest' test
```

### Commit

```text
feat(backend): make coin history auditable
```

## PORT-005: Add real database proof for tenant isolation and concurrent mutations

**Status:** TODO
**Priority:** P1  
**Depends on:** PORT-004

**Exact scope:** Complement the current mocked action-service coverage with
database-backed tests for the authorization and locking guarantees that matter
to family data and coins.

**Files:**

- Create or extend tests under
  `apps/backend/src/test/java/com/sashplatonov/earnit/kids/family/`.
- Reuse `FamilyResourceTest`, `RepositorySmokeTest`, and
  `FamilyActionServiceImplTest` fixtures rather than creating a second fixture
  system.

### Outcome

The portfolio demonstrates that a caller cannot mutate another family and that
concurrent duplicate resolution/purchase cannot double-credit or overdraw a
child.

### Architectural decision

Test the existing `familyId -> child/request/resource` query scoping and
pessimistic locks against a real transaction boundary. Do not replace those
locks with an optimistic-locking rewrite or a distributed lock.

### Required changes

1. Add REST/resource tests for parent vs child permissions and attempts to use
   a child, request, task, reward, or history ID owned by another family.
2. Add an integration test that runs two transactions against the same pending
   request and asserts exactly one approval/event/balance change succeeds.
3. Add a concurrent insufficient-balance purchase test that proves the final
   balance is non-negative and exactly one purchase is recorded when funds fit
   only one purchase.
4. Cover a repeated public/Telegram-equivalent mutation at the canonical
   `FamilyActionService` boundary, not in a separate adapter implementation.

### Out of scope

- Full load testing or a production Telegram client test.
- Browser E2E for every authorization permutation.

### Acceptance criteria

- Cross-family attempts return the existing safe not-found/unauthorized outcome
  and leave both families unchanged.
- Parallel resolution records one history/ledger event and one final status.
- Parallel purchase never produces a negative balance or duplicate ledger row.

### Targeted validation

```bash
cd apps/backend && ./mvnw -Dtest='FamilyResourceTest,RepositorySmokeTest,*Concurrency*Test' test
```

### Commit

```text
test(backend): prove family isolation and coin concurrency
```

## PORT-006: Rewrite the README as a hiring-manager portfolio page

**Status:** TODO
**Priority:** P1  
**Depends on:** PORT-001, PORT-002

**Exact scope:** Keep the short local-start instructions, but make the root
README communicate the product, architecture, engineering decisions, safety
boundaries, and verified commands in two minutes.

**Files:**

- Modify `README.md`.
- Reuse selected sanitized assets from `apps/web/static/public/assets/screenshots/`
  only after confirming they contain demo data.
- Link to `apps/backend/docs/ARCHITECTURE.md` and
  `apps/web/docs/ARCHITECTURE.md` as deeper references.

### Outcome

A reviewer can understand why this is a Senior Java project before opening the
code, and can then verify each claimed engineering decision in a named module.

### Architectural decision

Describe only implemented behavior: SvelteKit/Telegram Mini App to REST to a
Quarkus modular monolith over PostgreSQL, with Telegram as an adapter around
the same family-action domain. State the ledger claim only after PORT-004 is
complete; until then describe the current history projection honestly.

### Required changes

1. Add a concise product statement, a Mermaid architecture diagram, stack, and
   a five-command local-run/test section starting with `cp .env.example .env`.
2. Add 3-6 evidence-linked engineering decisions: family-scoped authorization,
   verified Telegram `initData`, transactional request/outbox handling,
   pessimistic duplicate-resolution protection, Flyway migrations, and
   observability (health, structured logs, trace correlation, metrics).
3. Add a clear optional-integration note: the web/backend stack starts without
   Telegram or Google credentials, while those flows are disabled.
4. Add a privacy/publication note explaining that screenshots and fixtures use
   demo data and that signing keys/secrets are external.
5. Replace the root README links to historical planning backlogs with current
   architecture, testing, operations, and contribution references.

### Out of scope

- A public demo deployment or fabricated performance claims.
- Copying every internal architecture document into the README.

### Acceptance criteria

- A reviewer can identify the product, Java/Quarkus ownership, data boundary,
  Telegram role, and startup command in under two minutes.
- Every engineering claim maps to an existing class/test/doc or a completed task.
- No credentials, real family data, private hostnames, or private signing facts
  appear in README text or screenshots.

### Targeted validation

```bash
git diff --check && rg -n -i 'backlog|roadmap|todo' README.md
```

### Commit

```text
docs: present EarnIt Kids as a public portfolio
```

## PORT-007: Remove public workspace noise and retired planning artifacts

**Status:** TODO
**Priority:** P2  
**Depends on:** PORT-006

**Exact scope:** Remove tracked local-editor/OS artifacts and archive or remove
historical implementation backlogs that are not part of the public project
contract.

**Files:**

- Delete `.github/java-upgrade/.DS_Store`.
- Review tracked `.claude/`, `.codex/`, `.mcp.json`, and `.vscode/` files; keep
  only genuinely shareable, credential-free project configuration.
- Review `docs/*backlog*.md`, `docs/.backlog-execution-state.md`, and `TODO.md`.

### Outcome

The public repository contains architecture and operational documentation, not
local-machine residue or a long trail of completed agent work.

### Architectural decision

Preserve current architecture/runbook documents and active contributor-facing
issues. Remove execution-state files and completed remediation plans rather
than renaming them as public documentation; use Git history for archaeology.

### Required changes

1. Delete the tracked `.DS_Store`; verify existing `.gitignore` covers it.
2. Remove or sanitize local tool configuration that reveals personal paths,
   tokens, or agent-specific behavior and is not needed by a fresh clone.
3. Remove retired backlog/status documents and all README/doc links to them;
   retain durable architecture, testing, security, and operations documents.
4. Verify no public asset or JSON report contains real family/child data before
   retaining it as a screenshot or reference artifact.

### Out of scope

- Deleting useful issue templates, CI workflows, or reproducible editor setup.
- Rewriting commit history for ordinary documentation clutter.

### Acceptance criteria

- No tracked `.DS_Store`, local execution state, or completed planning backlog
  remains without an explicit contributor-facing reason.
- Documentation links resolve and the README contains no dead backlog links.
- Retained screenshots/reference data are demonstrably sanitized.

### Targeted validation

```bash
git ls-files | rg '(^|/)(\.DS_Store|\.env$|.*\.keystore$)'
git diff --check
```

### Commit

```text
chore(docs): remove public repository noise
```
