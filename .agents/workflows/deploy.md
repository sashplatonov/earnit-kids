---
description: Deploy the application using Docker
---

1. Build the image:
   `docker compose build`
// turbo
2. Start containers:
   `docker compose up -d`
// turbo
3. Check status:
   `docker compose ps`
// turbo
4. Check healthcheck:
   `curl -s http://localhost:3000/health`
