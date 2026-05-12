# Family Parent Access Backlog

<a id="top"></a>

Planning snapshot created on 2026-05-12.

Delivery status: `Planned`.

This backlog covers a new parent-sharing model for the family shop:

- A family admin can add other parent emails to the same family.
- Every added parent receives one of three access levels: `viewer`, `editor`, or `family_admin`.
- `viewer` can open the family and see data, but cannot mutate it.
- `editor` can view and edit family data, but cannot grant access to other parents.
- `family_admin` can view, edit, and add or manage other parent emails.
- If the invited email has no existing family account, it should be able to accept access and enter the invited family.
- If the invited email already belongs to another family, login must offer a family chooser so the parent can enter the intended family.

The current codebase is not ready for this behavior yet. Today one `families.email`
row owns one family, one password, one verification token, and one auth session
with one `familyId`. That is visible in `FamilyEntity`, `FamilyRepository`,
`AuthServiceImpl`, `CookieBuilder`, `AuthResponse`, `SessionPageDataResponse`,
and the current parent settings UI.

<a id="table-of-contents"></a>

## Table of Contents

- [Goal](#goal)
- [Current Constraints](#current-constraints)
- [Target Product Contract](#target-product-contract)
- [Sprint Plan](#sprint-plan)
- [Backlog Tasks](#backlog-tasks)
- [Test Plan](#test-plan)
- [Definition of Done](#definition-of-done)

<a id="goal"></a>

## Goal

Deliver shared parent access without breaking the current child flow, family data
loading, or admin-only protections.

The implementation must:

- separate parent identity from family ownership;
- support one parent email linked to multiple families;
- preserve family scoping for all existing data tables;
- make the active family explicit in auth and session state;
- expose clear permission gates for view-only, edit, and family-admin actions;
- keep login understandable when one email can enter more than one family.

[Back to top](#top)

<a id="current-constraints"></a>

## Current Constraints

- `apps/backend/src/main/resources/db/migration/V1__initial_schema.sql` stores one unique `families.email` per family.
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/FamilyEntity.java` stores `email`, `adminPassword`, verification, and reset tokens directly on the family row.
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/FamilyRepository.java` authenticates and creates families by email, not by parent account plus membership.
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/AuthServiceImpl.java` assumes one email resolves to exactly one family.
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/CookieBuilder.java` signs one `familyId` into the auth payload and has no active-family switch contract.
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/AuthResponse.java` and `SessionPageDataResponse.java` cannot describe multiple family memberships or a pending family choice state.
- `apps/web/src/routes/login/+page.svelte` only supports direct register/login into one family.
- `apps/web/src/lib/components/app/sections/SettingsSection.svelte` has no parent access management UI.
- `apps/web/src/lib/types/session.ts` and `apps/web/src/lib/stores/app.ts` do not carry membership permission or family chooser state.

[Back to top](#top)

<a id="target-product-contract"></a>

## Target Product Contract

1. Parent identity becomes account-based, not family-row-based.
2. A parent email can belong to one or many families through membership rows.
3. Every membership stores one permission level: `viewer`, `editor`, or `family_admin`.
4. At least one `family_admin` must remain in every family.
5. Only `family_admin` can add, remove, or change another parent membership.
6. `editor` can modify family content but cannot manage parent access.
7. `viewer` can read family content and switch children where allowed by current UI rules, but cannot mutate family data.
8. Registration still creates a new family plus the first `family_admin` membership for the registering email.
9. Login with one matching membership enters the family directly.
10. Login with multiple memberships stops on a family chooser before entering the app.
11. Session state must carry both the parent account identity and the active family membership permission.
12. Child login by magic link stays family-scoped and does not use the new family chooser.

[Back to top](#top)

<a id="sprint-plan"></a>

## Sprint Plan

| Sprint | Priority | Theme | Exit criteria |
| --- | --- | --- | --- |
| Sprint 1 | P0 | Domain and auth model refactor | Backend can model parent accounts, memberships, active-family sessions, and family chooser responses |
| Sprint 2 | P0 | Parent access management API | Family admins can add, edit, remove, and list parent memberships with permission checks |
| Sprint 3 | P0 | Web login and family chooser UX | Parent login works for zero/one/many memberships and exposes correct permission-aware session state |
| Sprint 4 | P1 | Settings UI, polish, and release safety | Settings UI manages parent access end-to-end and regression coverage closes the flow |

[Back to top](#top)

<a id="backlog-tasks"></a>

## Backlog Tasks

| ID | Sprint | Priority | Depends on | Main outcome |
| --- | --- | --- | --- | --- |
| FPA-01 | 1 | P0 | none | Freeze the auth and membership contract | ✅ **DONE**
| FPA-02 | 1 | P0 | FPA-01 | Add DB schema for parent accounts and family memberships | ✅ **DONE**
| FPA-03 | 1 | P0 | FPA-02 | Refactor backend entities, repositories, and auth payloads | ✅ **DONE**
| FPA-04 | 1 | P0 | FPA-03 | Add login family-selection flow and active-family session contract | ✅ **DONE**
| FPA-05 | 2 | P0 | FPA-03 | Add parent membership management service and resource endpoints | ✅ **DONE**
| FPA-06 | 2 | P0 | FPA-05 | Enforce role-based mutation guards across existing family write paths | ✅ **DONE**
| FPA-07 | 3 | P0 | FPA-04 | Extend web auth/session models for membership-aware login |
| FPA-08 | 3 | P0 | FPA-07 | Build parent family chooser screen and routing behavior |
| FPA-09 | 4 | P1 | FPA-05, FPA-07 | Add parent access management UI in Settings |
| FPA-10 | 4 | P1 | FPA-06, FPA-09 | Add copy, i18n, UX polish, and empty/error states |
| FPA-11 | 4 | P0 | all implementation tasks | Add regression tests and run release gates |

### FPA-01 - Freeze the parent access contract

Priority: P0

Primary files:

- `docs/family-parent-access-backlog.md`
- `apps/backend/docs/ARCHITECTURE.md`
- `apps/web/docs/ARCHITECTURE.md`

Description:

Lock the product and technical rules before code changes. The repo currently
mixes family ownership, login identity, password ownership, and verification on
one table. This task defines the replacement contract clearly enough that DB,
API, and UI work can proceed without incompatible assumptions.

Do:

- Define the parent permission matrix for `viewer`, `editor`, and `family_admin`.
- Define whether family membership is active immediately or requires explicit acceptance.
- Define the login resolution rules for zero matches, one match, and many matches.
- Define whether adding an email that already owns another family creates an additional membership instead of failing.
- Define the minimum session payload fields required after login and after family switching.
- Define whether password reset and email verification belong to the parent account or to a family membership. The recommended direction is parent account.

Files likely to change:

- `apps/backend/docs/ARCHITECTURE.md`
- `apps/web/docs/ARCHITECTURE.md`
- optional follow-up API contract note in `docs/`

How to verify:

- The contract exists in repo markdown and is consistent with the backlog tasks below.
- No task below relies on a different meaning of `viewer`, `editor`, or `family_admin`.

Tests to create:

- none in this task; this is a written contract gate.

### FPA-02 - Add DB schema for parent accounts and family memberships

Priority: P0

Depends on: FPA-01

Primary files:

- `apps/backend/src/main/resources/db/migration/V17__parent_accounts_and_family_memberships.sql`
- optional follow-up migration if backfill must be split for safety

Description:

Replace the single-email-per-family model with parent accounts and membership
rows while preserving all existing family-scoped business data.

Do:

- Create a `parent_accounts` table with unique email, password hash, verification state, reset token state, and account timestamps.
- Create a `family_parent_memberships` table keyed by parent account plus family, with permission enum/string, membership status, and audit timestamps.
- Backfill every existing `families.email` + `admin_password` into one parent account plus one `family_admin` membership.
- Preserve current `families.family_id` and integer family PK so child/task/shop/history/request tables do not need FK rewrites.
- Decide whether `families.email`, `admin_password`, verification fields, and reset fields are removed immediately or kept temporarily for one compatibility release. Recommended: backfill first, then keep legacy columns read-only for one release if risk is high.
- Add DB constraints that prevent duplicate memberships for the same family/email pair.

Files likely to change:

- `apps/backend/src/main/resources/db/migration/V17__parent_accounts_and_family_memberships.sql`
- possibly later cleanup migration such as `V18__drop_legacy_family_auth_columns.sql`

How to verify:

- Flyway migration applies on PostgreSQL from an empty database.
- H2-backed test baseline still boots or is updated if H2 compatibility SQL differs.
- Backfilled records preserve access for existing family owners.

Tests to create:

- extend repository smoke coverage in `apps/backend/src/test/java/com/sashplatonov/earnit/kids/repository/RepositorySmokeTest.java`
- add migration/backfill assertions that one legacy family becomes one parent account and one `family_admin` membership

### FPA-03 - Refactor backend entities, repositories, and auth payloads

Priority: P0

Depends on: FPA-02

Primary files:

- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/domain/model/FamilyEntity.java`
- new `.../domain/model/ParentAccountEntity.java`
- new `.../domain/model/FamilyParentMembershipEntity.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/repository/FamilyRepository.java`
- new `.../repository/ParentAccountRepository.java`
- new `.../repository/FamilyParentMembershipRepository.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/AuthPayload.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/AuthResponse.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/response/SessionPageDataResponse.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/AuthContext.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/CookieBuilder.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/JwtCompatVerifier.java`

Description:

Move auth ownership from `FamilyEntity` to `ParentAccountEntity` and make every
authenticated parent session permission-aware.

Do:

- Stop treating `FamilyEntity.email` and `FamilyEntity.adminPassword` as the source of truth.
- Add repository methods to resolve parent accounts by email and memberships by parent account and family.
- Extend auth/session DTOs with active membership permission and, if needed, available family choices.
- Make JWT/cookie payloads include active family membership permission so resource-level guards can rely on it.
- Keep child sessions backward-compatible and family-scoped.
- Preserve super-admin override handling without allowing a normal `family_admin` membership to impersonate `super_admin`.

How to verify:

- Unit tests prove a parent with one membership authenticates into the correct family.
- Session page-data output exposes the active permission needed by the web app.
- Existing child auth tests still pass unchanged or with only expected payload shape updates.

Tests to create:

- extend `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/AuthServiceImplTest.java`
- extend `apps/backend/src/test/java/com/sashplatonov/earnit/kids/config/CookieBuilderTest.java`
- extend `apps/backend/src/test/java/com/sashplatonov/earnit/kids/config/JwtCompatVerifierTest.java`
- extend `apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/SessionPageDataResourceTest.java`

### FPA-04 - Add login family-selection flow and active-family switching contract

Priority: P0

Depends on: FPA-03

Primary files:

- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/AuthService.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/AuthServiceImpl.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/AuthResource.java`
- new request/response DTOs under `apps/backend/src/main/java/com/sashplatonov/earnit/kids/dto/{request,response}/`

Description:

When one email belongs to more than one family, login must stop before the app
shell and require an explicit family choice.

Do:

- Change email/password login and Google login to resolve parent account first, then memberships.
- For one membership, issue cookies immediately as today.
- For many memberships, return a structured response such as `selectionRequired=true` with a family list instead of immediately issuing an active-family session.
- Add an endpoint to activate one selected family and issue the final auth cookies.
- Decide whether family choice is one-time per login or persisted as last-used family for the parent account. Recommended: persist last-used family per parent account but always allow switching later.
- Add a safe server-side path for switching active family without re-entering the password after a valid parent session exists.

Files likely to change:

- `AuthService.java`
- `AuthServiceImpl.java`
- `AuthResource.java`
- `AuthResponse.java`
- new DTOs such as `SelectFamilyRequest`, `FamilyChoiceResponse`

How to verify:

- Login with one family still lands in the app directly.
- Login with multiple families returns a chooser payload and does not issue an ambiguous active-family session.
- Selecting a family produces cookies bound to the chosen family and its permission.

Tests to create:

- extend `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/AuthServiceImplTest.java`
- extend `apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/AuthResourceTest.java`

### FPA-05 - Add parent membership management service and endpoints

Priority: P0

Depends on: FPA-03

Primary files:

- new `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyParentAccessService.java`
- new `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyParentAccessServiceImpl.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/FamilyResource.java`
- new DTO request/response files for membership list, add, update, and delete
- backend i18n message surfaces under `apps/backend/src/main/java/com/sashplatonov/earnit/kids/i18n/`

Description:

Expose CRUD-like APIs for family-admin-only management of parent emails and permissions.

Do:

- Add `GET /api/family/parents` to list memberships for the active family.
- Add `POST /api/family/parents` to add an email plus permission.
- Add `PATCH /api/family/parents/{membershipId}` to change permission.
- Add `DELETE /api/family/parents/{membershipId}` to remove access.
- If the email already has a parent account, create an additional membership instead of creating a duplicate account.
- If the email has no parent account, create the account shell or pending invitation record according to the FPA-01 contract.
- Prevent deleting or downgrading the last `family_admin`.

Files likely to change:

- `FamilyResource.java`
- new service and DTO files
- backend message catalog and localized text

How to verify:

- Family admin can add a new parent email to the active family.
- Editor and viewer cannot manage memberships.
- Duplicate membership for the same email and family is rejected with a stable error.
- Removing the last `family_admin` is rejected.

Tests to create:

- new `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/FamilyParentAccessServiceTest.java`
- extend `apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/FamilyResourceTest.java`

### FPA-06 - Enforce permission guards on existing family write paths

Priority: P0

Depends on: FPA-05

Primary files:

- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/resource/FamilyResource.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyServiceImpl.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/FamilyActionServiceImpl.java`
- `apps/backend/src/main/java/com/sashplatonov/earnit/kids/config/AuthContext.java`

Description:

Today parent behavior is effectively binary: parent/admin versus child. Shared
parent access requires write paths to distinguish `viewer`, `editor`, and
`family_admin`.

Do:

- Audit every parent-only endpoint and mark whether it requires `editor` or `family_admin`.
- Ensure read-only family pages and data fetch endpoints can be opened by `viewer`.
- Ensure mutation endpoints for tasks, shop, settings, child links, balance, requests moderation, and child settings reject `viewer`.
- Ensure membership-management endpoints reject `editor` and `viewer`.
- Keep child restrictions unchanged.

How to verify:

- Viewer session can load family data but cannot mutate it.
- Editor can edit family business data but cannot manage parent access.
- Family admin can do both.

Tests to create:

- extend `apps/backend/src/test/java/com/sashplatonov/earnit/kids/resource/FamilyResourceTest.java`
- extend `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/FamilyServiceImplTest.java`
- add focused permission regression tests for critical write endpoints

### FPA-07 - Extend web auth and session models for membership-aware parent access

Priority: P0

Depends on: FPA-04

Primary files:

- `apps/web/src/lib/types/session.ts`
- `apps/web/src/lib/services/serverContract.ts`
- `apps/web/src/lib/services/api.ts`
- `apps/web/src/lib/stores/app.ts`
- `apps/web/src/lib/app/routes.ts`
- any session bootstrap/load files that normalize `/api/page-data/session`

Description:

The web app must understand both the active family permission and the case where
login returns a pending family selection instead of a completed session.

Do:

- Extend the normalized auth response and session snapshot types with membership permission.
- Add client API helpers for fetching family choices and selecting the active family.
- Store the active permission in app state so UI sections can hide or disable controls without guessing from `role` alone.
- Preserve current child and super-admin routing behavior.
- Ensure protected route loads do not assume every successful parent login already has a ready app session.

How to verify:

- Session bootstrap parses the new payload correctly.
- App state exposes `viewer`, `editor`, and `family_admin` cleanly.
- Existing admin-only nav logic does not accidentally treat `viewer` as full editor access.

Tests to create:

- extend `apps/web/tests/unit/api.test.ts`
- extend `apps/web/tests/unit/serverContract.test.ts`
- extend `apps/web/tests/unit/appRoutes.test.ts`

### FPA-08 - Build the parent family chooser screen and active-family switch UX

Priority: P0

Depends on: FPA-07

Primary files:

- `apps/web/src/routes/login/+page.svelte`
- `apps/web/src/routes/login/+page.server.ts`
- optional new component such as `apps/web/src/lib/components/auth/FamilyChooser.svelte`
- auth i18n files under `apps/web/src/lib/i18n/messages/{en,ru}/auth.ts`

Description:

A parent with multiple memberships must see an explicit family selection step
after successful identity verification and before entering `/app`.

Do:

- Add chooser UI for multiple-family login response.
- Show family name/id and the permission label for each available membership.
- Support selecting the active family and continuing into `/app`.
- Show safe empty/error states if a membership becomes invalid before selection.
- Keep the one-family login flow visually unchanged as much as possible.
- Add a switch-family action for already-authenticated parents if FPA-04 exposes a backend switch endpoint.

How to verify:

- Parent with one family still reaches the app directly.
- Parent with two families is blocked on chooser until one family is selected.
- Choosing one family enters the correct family and keeps the correct permission.
- Returning to login/logout resets chooser state correctly.

Tests to create:

- extend `apps/web/tests/e2e/auth.spec.ts`
- add `apps/web/tests/e2e/parent-family-chooser.spec.ts`
- add or extend unit coverage for chooser response normalization

### FPA-09 - Add parent access management UI in Settings

Priority: P1

Depends on: FPA-05, FPA-07

Primary files:

- `apps/web/src/lib/components/app/sections/SettingsSection.svelte`
- optional new components such as:
  - `apps/web/src/lib/components/app/settings/ParentAccessList.svelte`
  - `apps/web/src/lib/components/app/settings/ParentAccessForm.svelte`
- `apps/web/src/lib/services/api.ts`
- admin/app i18n files under `apps/web/src/lib/i18n/messages/{en,ru}/`

Description:

Expose the shared parent access feature in the authenticated parent settings
area with clear permission-aware controls.

Do:

- Add a parent access card to Settings for `family_admin` only.
- List all parent emails in the active family and show their permission labels.
- Add a form to invite a new email and pick `viewer`, `editor`, or `family_admin`.
- Allow changing permission and removing access with confirmation.
- Show viewer/editor users the list in read-only or hidden form according to the product contract from FPA-01. Recommended: editors see read-only summary, viewers see no management surface.
- Prevent self-removal or self-downgrade when it would leave the family without a `family_admin`.

How to verify:

- Family admin can manage parent access from Settings.
- Editor cannot see mutation controls for memberships.
- Viewer cannot see write controls anywhere in Settings.
- Loading, success, duplicate-email, and last-admin error states render clearly.

Tests to create:

- extend `apps/web/tests/unit/api.test.ts`
- add focused component tests if the repo already uses them for Svelte sections, otherwise cover through E2E
- add `apps/web/tests/e2e/parent-access-settings.spec.ts`

### FPA-10 - Add i18n, copy, and UX safety states

Priority: P1

Depends on: FPA-06, FPA-09

Primary files:

- `apps/web/src/lib/i18n/messages/en/auth.ts`
- `apps/web/src/lib/i18n/messages/ru/auth.ts`
- `apps/web/src/lib/i18n/messages/en/app.ts`
- `apps/web/src/lib/i18n/messages/ru/app.ts`
- backend localized message surfaces if new API errors are introduced

Description:

The feature adds several new product concepts that must be understandable in
both English and Russian: parent access roles, family chooser, duplicate
membership errors, and last-admin protection.

Do:

- Add stable copy for permission labels and descriptions.
- Add chooser title, subtitle, and button copy.
- Add success and error messages for add/change/remove membership actions.
- Add explicit error copy for duplicate membership, no memberships, and last-family-admin rejection.
- Avoid raw backend error strings leaking into the UI without translation mapping.

How to verify:

- Both `en` and `ru` render all new auth/settings texts.
- No untranslated key names appear in chooser or settings flows.
- Permission labels are consistent between API responses and UI copy.

Tests to create:

- extend relevant i18n-aware web tests if present
- cover new translation key usage through the E2E flows in FPA-08 and FPA-09

### FPA-11 - Close release gates and regression coverage

Priority: P0

Depends on: all implementation tasks

Primary files:

- test files listed throughout this backlog
- optional release notes doc if needed

Description:

This feature rewires core auth and permission behavior, so it is not complete
until both backend and web gates prove the legacy flows still work.

Do:

- Run backend `JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify`.
- Run web `npm run lint`.
- Run web `npm run test`.
- Run web `npm run build`.
- Run web `npm run test:e2e` because the login and settings UI changes are user-facing.
- Verify that child magic-link login, single-family parent login, and super-admin login still work.

How to verify:

- All required gates pass.
- No regression breaks child-only sessions or family-scoped CRUD behavior.
- Permission matrix is proven by automated tests rather than manual claims only.

Tests to create:

- backend:
  - `AuthServiceImplTest`
  - `AuthResourceTest`
  - `SessionPageDataResourceTest`
  - `JwtCompatVerifierTest`
  - `FamilyParentAccessServiceTest`
  - `FamilyResourceTest`
  - `FamilyServiceImplTest`
  - `RepositorySmokeTest`
- web:
  - `tests/unit/api.test.ts`
  - `tests/unit/serverContract.test.ts`
  - `tests/unit/appRoutes.test.ts`
  - `tests/e2e/auth.spec.ts`
  - `tests/e2e/parent-family-chooser.spec.ts`
  - `tests/e2e/parent-access-settings.spec.ts`

[Back to top](#top)

<a id="test-plan"></a>

## Test Plan

### Backend unit and resource coverage

- Authenticate a parent email with exactly one membership and assert direct login.
- Authenticate a parent email with multiple memberships and assert chooser response instead of immediate cookies.
- Select a family after chooser and assert the active-family permission is signed into the session payload.
- Add a new invited parent email with `viewer`, `editor`, and `family_admin` permissions.
- Reject duplicate membership add for the same email and family.
- Reject removal or downgrade of the last `family_admin`.
- Prove `viewer` cannot call mutation endpoints.
- Prove `editor` can mutate family data but cannot manage parent memberships.
- Prove `family_admin` can mutate family data and manage memberships.
- Prove child magic-link login still ignores the family chooser path.

### Repository and migration coverage

- Backfill one legacy family row into one parent account plus one `family_admin` membership.
- Confirm the unique account email constraint and unique membership pair constraint.
- Confirm active family data queries still use the family PK/FK graph unchanged.

### Web unit coverage

- Normalize chooser-required login responses correctly.
- Normalize active-family permission values correctly.
- Hide or disable write-only UI when permission is `viewer`.
- Hide membership-management UI when permission is `editor`.
- Preserve current admin/child route behavior outside the new shared-parent flow.

### Web E2E coverage

- Register a new family and confirm the original parent remains `family_admin`.
- Add a second email with `viewer`, log in with that email, and confirm read-only behavior.
- Upgrade the second email to `editor`, log in again, and confirm edit access without parent-management access.
- Add one email already linked to another family, log in, and confirm family chooser appears.
- Switch between two families from the chooser or in-app switcher and confirm the loaded family changes correctly.
- Confirm child login by magic link still bypasses parent chooser and still hides parent-only controls.

[Back to top](#top)

<a id="definition-of-done"></a>

## Definition of Done

- The DB model supports many parent accounts to many family memberships.
- The old one-email-per-family assumption is removed from runtime auth behavior.
- Parent login supports one-family and many-family outcomes safely.
- Active family and membership permission are present in backend session payloads and web state.
- Family admins can manage parent emails and permissions from Settings.
- Viewers cannot mutate family data.
- Editors can mutate family data but cannot manage parent memberships.
- Family admins can mutate family data and manage parent memberships.
- Child magic-link login and super-admin flows still work.
- Backend `verify`, web `lint`, web `test`, web `build`, and web `test:e2e` pass.

[Back to top](#top)
