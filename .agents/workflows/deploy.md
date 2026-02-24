---
description: Деплой приложения через Docker
---

1. Собери образ:
   `docker compose build`
// turbo
2. Запусти контейнеры:
   `docker compose up -d`
// turbo
3. Проверь статус:
   `docker compose ps`
// turbo
4. Проверь healthcheck:
   `curl -s http://localhost:3000/health`
