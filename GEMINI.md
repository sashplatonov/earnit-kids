<!-- Inherits global rules from /Users/sash/.gemini/GEMINI.md -->
# EarnIt Kids — Gemini Rules

<a name="top"></a>

Project-local rules for Google Gemini (Codey / Gemini CLI) override global Gemini guidance when they conflict.

## 📚 Reference

For full project documentation, see:

- [AGENTS.md](AGENTS.md) — Build commands, coding style, testing, commit guidelines
- [docs/architecture.md](docs/architecture.md) — C4 architecture, data flow, auth flow, Docker networking
- [docs/docker-ops.md](docs/docker-ops.md) — Docker compose workflows and troubleshooting
- [docs/testing.md](docs/testing.md) — Test suites, coverage, and verification gates
- [docs/i18n-contract.md](docs/i18n-contract.md) — Locale contract and URL policy
- [docs/i18n-backlog.md](docs/i18n-backlog.md) — Internationalization tasks
- [apps/web/docs/ARCHITECTURE.md](apps/web/docs/ARCHITECTURE.md) — Frontend routing, state, API integration
- [apps/backend/docs/ARCHITECTURE.md](apps/backend/docs/ARCHITECTURE.md) — Backend layers, DB, auth, API

## ⚙️ Quick Commands

```bash
# Backend
cd apps/backend && JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify

# Web
cd apps/web && npm run lint && npm run test && npm run build

# Docker
docker compose --profile db up -d --build
```

## 🎨 Style

- Follow AGENTS.md style rules for each language.
- Java: no nesting, no Javadoc, no `.bak` files.
- No string interpolation in SQL — use parameterized queries.

[↑ Back to top](#top)
