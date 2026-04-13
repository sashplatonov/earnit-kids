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

## Java
- Вложенные классы и интерфейсы внутри других классов запрещены: каждый класс и интерфейс должны быть объявлены как top-level и находиться в отдельном файле, соответствующем пакету и имени класса (например, `com.example.MyClass` → `src/main/java/com/example/MyClass.java`).
- JavaDoc не нужен: не добавлять `/** ... */` (Javadoc) в кодовую базу. Для пояснений допускаются однострочные комментарии `//` и внутренние пояснения, но не Javadoc-формат.

### Комментарии и резервные файлы
- Комментарии в коде допускаются ТОЛЬКО в двух случаях: 1) в сложных алгоритмах для краткого пояснения нетривиальной логики, 2) при фиксе необычных багов, где важно зафиксировать причину и контекст. Во всех остальных случаях комментарии НЕ НУЖНЫ — пишите говорящие имена классов, методов и переменных.
- Если комментарий необходим, он должен быть кратким и начинаться с префикса `EXPLAIN:` или `FIXME:` и содержать обоснование (что, почему, как можно исправить). Длинные пояснения оформляются в документации вне кода.
- Запрещено: любая Javadoc-форма (`/** ... */`) и произвольные блок-комментарии `/* ... */` без префикса объяснения.
- Нельзя создавать или коммитить резервные копии в репозиторий (файлы с расширениями `*.bak`). Добавьте `*.bak` в `.gitignore` и никогда не оставляйте такие файлы в дереве исходников.
- Применение в проекте: Checkstyle настроен выдавать ошибку при обнаружении обычных комментариев и блок-комментариев; следуйте префиксам `EXPLAIN:`/`FIXME:` для допустимых случаев.
