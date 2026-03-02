/## Testing & verification
- Run `npm run lint`, `npm test`, and `npm run build` after edits; each must pass before closing the session.
- Manual Playwright command: `npm run test:ui:e2e` (mention it whenever you summarize testing guidance).
- Include manual verification steps for shop/purchase flows: add/edit/delete shop item, admin direct purchase, child request plus admin decision, and frequency/money limits.


## Documentation Index (read only when needed)
Design-related tasks: read `docs/design-concept.md` first, then `docs/rules-frontend.md`.

| File | When to read |
|------|-------------|
| `docs/architecture.md` | Overall system design |
| `docs/design-concept.md` | Visual language, UX principles, and design constraints |
| `docs/rules-backend.md` | Backend coding standards |
| `docs/rules-frontend.md` | Frontend/CSS/HTMX patterns |
| `docs/rules-database.md` | SQL, migrations, schema |
| `docs/telegram-setup.md` | Telegram bot integration |
| `docs/openapi.yaml` | API specification |
