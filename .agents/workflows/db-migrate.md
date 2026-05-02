---
description: Apply database migrations (Flyway)
---

1. `ls -la apps/backend/src/main/resources/db/migration/` — list pending migrations
2. `cd apps/backend && ./mvnw compile flyway:migrate` — apply via Flyway
3. Check logs for errors; validate both PostgreSQL and H2 test baseline after changes
