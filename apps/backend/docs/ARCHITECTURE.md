# EarnIt Kids backend architecture

<a name="top"></a>

The backend is a Quarkus service and the source of truth for every family. It
owns access decisions, family state, migrations, Telegram verification, and
outgoing delivery records.

## Table of contents

- [🧭 Module map](#-module-map)
- [🔐 Access rules](#-access-rules)
- [🗄️ Data and transactions](#️-data-and-transactions)
- [🌍 Language boundary](#-language-boundary)
- [📘 API and health](#-api-and-health)
- [🧪 Verify backend work](#-verify-backend-work)

## 🧭 Module map

```text
identity/   account login, cookies, sessions, Google sign-in
family/     families, children, tasks, rewards, requests, history, access
admin/      super-admin analytics
telegram/   Mini App authentication, bot, webhooks, invitations, delivery
platform/   outbox, push, WebSocket delivery, health, metrics, diagnostics
config/     filters and application configuration
shared/     HTTP response contracts
```

Each feature follows the same direction:

```text
resource -> application service -> repository -> database
```

Resources handle HTTP and role checks. Services own transactions and business
rules. Repositories query and persist data. Do not add family business rules to
a resource or make Telegram mutate entities directly.

[↑ Back to top](#top)

## 🔐 Access rules

Authentication is cookie-based. `AuthFilter` turns a valid session into an
`AuthContext`, which every protected resource uses.

- A parent account can be a member of more than one family.
- Parent permissions are `viewer`, `editor`, and `family_admin`.
- `viewer` reads; `editor` changes family data; `family_admin` also manages
  parents and family settings.
- A child session can access only that child’s family-owned data.
- Super-admin APIs live under `/api/admin/*` and are separate from family APIs.

⚠️ Never take a family or child ID from a request as permission. Derive the
allowed scope from the authenticated session, then validate ownership before
every read or mutation.

[↑ Back to top](#top)

## 🗄️ Data and transactions

PostgreSQL is the production database. Flyway migrations live in
`src/main/resources/db/migration`; H2 test migrations live in
`src/test/resources/db/migration`. Add a new sequential migration for a schema
change—never edit a migration that has already run elsewhere.

The main family data includes families, children, parent memberships, tasks,
shop items, requests, history, notification settings, Telegram identities, and
outbox records. Services keep a state change and its outbox event in the same
transaction. Workers deliver Telegram or push notifications later, with their
own retry and claim logic.

Use database locking where concurrent actions could spend coins twice, decide a
request twice, consume an invitation, or send one delivery more than once.

[↑ Back to top](#top)

## 🌍 Language boundary

The backend normalizes supported locales to `en` and `ru`. The family locale is
the presentation locale for authenticated family screens and Telegram messages.
It is not a personal profile setting.

API contracts stay language-neutral: clients use `errorCode`, safe `params`,
and `traceId`, not translated message text. Validation and server-generated
copy live in `messages*.properties` and `telegram_messages*.properties`.
Keep placeholders identical in both languages and never expose raw keys,
exception details, or SQL errors to a user.

[↑ Back to top](#top)

## 📘 API and health

Quarkus generates the API description from resource annotations:

- OpenAPI: `/api/openapi.yaml`
- Swagger UI: `/q/swagger-ui`
- Health: `/q/health`

The web app is the browser-facing edge. Browser calls go through its same-origin
proxy; do not loosen backend CORS to work around an edge configuration issue.

[↑ Back to top](#top)

## 🧪 Verify backend work

Run the full gate from `apps/backend`:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

`verify` runs tests plus Checkstyle, PMD, JaCoCo, and SpotBugs. `./mvnw test`
is useful while working, but it is not the final backend check. After a
migration change, make sure both PostgreSQL and the H2 test baseline pass.

[↑ Back to top](#top)
