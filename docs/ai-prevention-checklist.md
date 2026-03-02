# AI Prevention Checklist

Read when: >3 files touched, cross-module refactor, prior lint/test failed, or user says it's recurring.

## Known recurring mistakes

- **CommonJS only** — no `import`/`export`; use `require()`/`module.exports`. Check with `npm run lint:commonjs`.
- **No framework routing** — routing is manual `url.startsWith()` in `src/app.js`; do not add Express or similar.
- **Raw SQL** — no ORM; write raw SQL via `pg` pool. Parameterized queries only (`$1`, `$2`).
- **Migration files are append-only** — never edit existing `migrations/*.sql`; add a new numbered file.
- **Views are server-concatenated components** — `views/components/` files are joined in order; do not break that contract.
- **Lint must pass with 0 warnings** — `eslint --max-warnings=0`; fix all warnings, not just errors.
- **CSS/JS assets** — JS lives in `public/js/`; CSS in `public/css/`. Build minifies to `public/dist/`. Never edit `dist/` directly.
- **Push tokens scope** — push tokens are per-user-role; filter by role before sending notifications.
- **Auth cookies** — session is cookie-based; never store auth state in localStorage.
- **Soft delete** — tasks and shop items use `is_deleted = true`; never hard-delete rows.
