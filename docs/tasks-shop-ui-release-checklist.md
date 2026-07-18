# Tasks and Shop UI release checklist

## Automated gates

- [x] `apps/web`: `npm run lint`
- [x] `apps/web`: `npm run test -- --run`
- [x] `apps/web`: `npm run build`
- [x] `apps/backend`: `./mvnw verify`
- [x] Layout assertion helper covers card overlap, chip clipping, empty chips, and chip intersections.
- [x] Focused Tasks/Shop parent-child Playwright scenario against the production Compose stack.
- [ ] Full `apps/web`: `npm run test:e2e` suite.

The focused scenario creates isolated family data and verifies parent and child Tasks/Shop surfaces at `1024 × 900` and `390 × 844`, including reward-goal visibility, role-specific controls, card-region geometry, and page-level horizontal overflow. It passed against a temporary clean PostgreSQL 18 container because the existing local Compose data volume still uses the legacy mount layout. No persistent data was deleted or migrated.

## Manual gate

Run [`tasks-shop-ui-usability-protocol.md`](tasks-shop-ui-usability-protocol.md) and attach only aggregate outcomes. Do not capture child names, free-text notes, cue content, or screenshots containing personal data.
