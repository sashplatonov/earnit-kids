---
name: earnit-kids-patterns
description: Project patterns and conventions for EarnIt Kids — use instead of README/architecture.md
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

## Java
- Nested classes and interfaces inside other classes are forbidden: each class and interface must be declared as top-level and placed in its own file matching the package and class name (for example, `com.example.MyClass` → `src/main/java/com/example/MyClass.java`).
- No Javadoc: do not add `/** ... */` (Javadoc) blocks to the codebase. Short inline comments (`//`) and brief internal notes are allowed, but avoid Javadoc-style blocks.

### Comments and backup files
- Comments in code are allowed ONLY in two cases: 1) to briefly explain non-trivial logic in complex algorithms, and 2) when fixing unusual bugs where recording cause and context is important. In all other cases comments are NOT REQUIRED — use descriptive names for classes, methods, and variables.
- If a comment is necessary it must be brief and start with the prefix `EXPLAIN:` or `FIXME:` and include justification (what, why, how to fix). Long explanations should be placed in external documentation.
- Forbidden: any Javadoc form (`/** ... */`) and arbitrary block comments `/* ... */` without the explanatory prefix.
- Do not create or commit backup files in the repository (files with `*.bak` extension). Add `*.bak` to `.gitignore` and never leave such files in the source tree.
- Project enforcement: Checkstyle is configured to flag plain comments and block comments; follow `EXPLAIN:`/`FIXME:` prefixes for allowed cases.
