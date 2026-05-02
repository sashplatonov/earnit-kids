---
description: Deploy with Docker Compose
---

1. `docker compose --profile db up -d --build` — JVM backend
2. Or `docker compose -f docker-compose.native.yml --profile db up -d --build` — native
3. `docker compose down` — stop
4. Run `docker compose config` before rebuild to catch env drift
