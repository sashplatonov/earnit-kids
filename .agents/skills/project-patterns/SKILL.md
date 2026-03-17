---
name: earnit-kids-patterns
description: Паттерны и конвенции проекта EarnIt Kids — читай это вместо README/architecture.md
---

## Module System
- **CommonJS only** (`require`/`module.exports`), NO ESM
- 4-space indentation
- Node.js test runner (NOT jest/mocha)

## Naming
- Files: `camelCase.js` (e.g., `familyService.js`)
- DB columns: `snake_case` (e.g., `family_id`, `child_id`)
- Routes: `/api/family/:familyId/tasks`

## Architecture Layers (top → bottom)
1. `routes/` — route definitions, auth middleware
2. `controllers/` — request parsing, response formatting
3. `services/` — business logic, validation
4. `db/` — SQL queries via `pg` pool (no ORM)

## Key Patterns
- All queries MUST filter by `family_id` from JWT (data isolation)
- Parameterized SQL only (`$1`, `$2`, …) — no string interpolation
- Migrations are sequential: `NNN_description.sql`, never edit merged files
- Views: EJS templates with HTMX for dynamic updates
- Logging: emoji-prefixed `console.log` (🪙, 🔍, ❌)
- ESLint: max complexity=10, max-lines=300

## Frontend
- Vanilla HTML/CSS/JS only — no React/Vue/etc.
- Scripts: `public/js/modules/` (admin.js, actions.js, ui.js)
- No TailwindCSS — vanilla CSS only

## Test Structure
- `tests/*.test.js` — general unit tests (Node.js test runner)
- `tests/unit/*.test.js` — module-specific unit tests
- `tests/integration/*.test.js` — API integration tests
- `tests/ui-e2e/*.e2e.test.js` — Playwright E2E tests
- Run order: `npm run lint` → `npm test` → `npm run test:integration`

## Shop Flow Key Files
- `public/js/modules/admin.js` — admin storefront
- `public/js/modules/actions.js` — purchase actions
- `public/js/modules/ui.js` — UI helpers
- `src/db/familyDataRepository.js` — all shop SQL queries

## Security
- Never read `.env` — use `.env.example` or `docker-compose.yml` for reference
- JWT contains `familyId`, `childId`, `role` (admin/child)
- CSRF protection in `src/middleware/security.js`
