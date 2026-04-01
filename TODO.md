

# Единый промпт для AI-агента

```markdown
## Роль

Ты — senior backend architect, специализирующийся на переводе проектов
на современный Java-стек. Действуй автономно, пиши весь код сам, не задавай
уточняющих вопросов — принимай разумные решения самостоятельно.

---

## Задача

Полностью перевести бэкенд проекта с **Node.js (vanilla HTTP + pg + EJS)**
на следующий стек:

- **Java 24** (последний GA-релиз, март 2025)
- **Quarkus 3.x** (последняя стабильная версия) в режиме **Native**
- **Maven 4.0.0**
- Формат сборки: **`pom.xml`** с BOM Quarkus
- Целевая сборка: **GraalVM Native Image**

---

## Описание текущего проекта (Node.js)

### Высокоуровневая архитектура (слои)

| Слой | Текущая реализация (Node.js) |
|---|---|
| **HTTP Gateway** | `app.js` — single Node HTTP server (no Express), подключает маршруты и middlewares: compression, security headers, rate limiting, CORS |
| **Routing** | `src/routes/*` — собственный `Router` с поддержкой динамических сегментов (`:id`) |
| **Controllers** | `src/controllers/*` — парсинг запроса, проверка контекста, формирование ответов/куки, простая валидация |
| **Services** | `src/services/*` — бизнес-логика (auth, family, push, analytics). Не зависят от HTTP |
| **Repositories / DB** | `src/db/*` — SQL через `pg` Pool; параметризованные запросы (`$1`, `$2`); транзакции через `getClient()` |
| **Views / Frontend** | `views/*` — EJS-шаблоны; frontend — ванильный JS в `public/js/modules/*` |
| **Utils** | `src/utils/*` — JWT (ручная реализация), Router, WebSocket, logging, metrics, in-memory cache |

### Полный HTTP-запрос — пошагово

1. Клиент → браузер отправляет запрос (`fetch` с `credentials: 'same-origin'`).
2. `app.js` получает запрос → `setSecurityHeaders`, rate-limiter, CORS (options) → направляет в `apiRoutes` или `staticRouter`.
3. В `apiRoutes` создаётся `ctx` через `createRouteContext` — содержит `familyId`, `childId`, `role`, `csrfToken` (декодируется из cookie `app_auth` через `verifyToken`).
4. `Router` находит маршрут → вызываются middlewares (`parseBody` → `sanitizePayload`).
5. Для защищённых методов (`POST/PUT/DELETE`) `apiAuthMiddleware` проверяет `ctx.familyId` и валидирует CSRF через `validateCsrf` (header `X-CSRF-Token` ИЛИ referer/origin/`sec-fetch-site`).
6. Контроллер → сервис → репозиторий (DB). Для комплексных изменений — `getClient()` + `BEGIN/COMMIT/ROLLBACK`.
7. При изменениях `familyService.saveFamilyData` вызывает `pushService.notifyFamilyChanges` (в фоне) и `websocket.notifyFamily` для real-time обновления.
8. Ответ — `sendJSON` (API) или `applyCommonTemplateData` + `res.end(html)` (SSR).

### Аутентификация, сессии, куки, JWT

- **Логин / magic link** → `authController.buildAuthCookies` формирует куки:
  - `app_auth` — JWT (HttpOnly, Path=/, Max-Age, SameSite=Lax). Генерируется `signToken(payload, maxAge)`.
  - `app_role` — роль (`admin`/`child`/`super_admin`), **не** HttpOnly.
  - `csrf_token` — случайный токен (`generateCsrfToken()`), **не** HttpOnly (фронт читает), SameSite=Strict.
  - `family_id`, `child_id` — HttpOnly.
- **JWT**: ручная реализация — `base64url(header).base64url(payload).HMAC-SHA256(signature)`. Payload: `email`, `role`, `familyId`, `childId?`, `csrfToken?`, `exp`.
- **Magic link**: `handleMagicLink` → `authenticateChildByToken` → `buildAuthCookies` → redirect `/`.
- **CSRF**: фронт берёт `csrf_token` из cookie → заголовок `X-CSRF-Token` для мутаций. Сервер `validateCsrf` проверяет заголовок или `Origin`/`Referer`/`sec-fetch-site`.

### Защита и валидация

- HTTP-заголовки через `helmet` + `setSecurityHeaders`.
- Входные данные: `sanitizePayload` (sanitize-html) в `parseBody` для JSON.
- SQL: все запросы параметризованы (`$1`…), никакой string interpolation.
- Rate limiting: ранний отказ в `app.js` для `/api/*`.
- Логирование и алёрты при 5xx + slow-query logging (threshold 200ms).

### База данных и транзакции

- **PostgreSQL** через `pg` Pool: `max: 20`, `idleTimeoutMillis: 30000`, `connectionTimeoutMillis: 5000`.
- Query wrapper `query(text, params)` логирует время, выбрасывает ошибки.
- Сложные операции — `getClient()` + `BEGIN/COMMIT/ROLLBACK`.
- Репозитории: `familyRepository`, `familyDataRepository`, `pushTokenRepository` — параметризованные запросы, `ON CONFLICT` для upsert, `RETURNING`, explicit transactions.
- Кэш: `Cache.js` — in-memory TTL cache с `invalidatePrefix` (используется для familyData).

### Real-time и Push

- **WebSocket**: клиент получает temp-токен через `/api/ws-token` (`signToken(payload, 60)` — 60 сек), подключается к `/ws?token=...` или использует cookie `app_auth`. Сервер валидирует JWT, устанавливает `ws.familyId`/`ws.role`. Broadcast всем клиентам с тем же `familyId`.
- **Push**: FCM (HTTP v1) и WebPush (VAPID/web-push). Токены в таблице `device_push_tokens`. `notifyFamilyChanges` определяет изменения (balance, requests) и шлёт уведомления.

### Frontend ↔ Backend контракты

- Сессия по cookie — фронт **не** хранит JWT в localStorage. `fetch` с `credentials: 'same-origin'`.
- CSRF: `csrf_token` cookie → `X-CSRF-Token` header для мутаций.
- Data sync: `POST /api/data` — payload = полное/частичное семейное состояние → `familyService.saveFamilyData` (транзакция) → websocket/push.
- WS-сообщения: `DATA_UPDATED`, `CHILD_UPDATED`, `CHILD_DELETED` → клиент вызывает `loadDataFromServer()`.

### Operational

- Метрики и логирование: requests/slow queries/errors. Slow DB threshold 200ms.
- Миграции: `migrations/NNN_desc.sql` (последовательное применение).
- Тесты: unit/integration/playwright e2e.

---

## Инварианты — MUST сохранить при переводе в Java

1. **Cookie names & semantics** (точные имена): `app_auth` (HttpOnly JWT), `app_role`, `csrf_token` (readable by JS), `family_id`, `child_id`. Атрибуты `SameSite`, `Path`, `Max-Age` — как в оригинале.
2. **JWT format**: HMAC-SHA256 signature, payload fields: `familyId`, `childId`, `role`, `email`, `csrfToken`, `exp`. Можно использовать стандартную библиотеку, но сохранить поля и логику валидации (expiration enforced).
3. **CSRF scheme**: non-HttpOnly `csrf_token` cookie + `X-CSRF-Token` header на мутирующих запросах ИЛИ origin/referer/sec-fetch fallback.
4. **DB queries** — параметризованные. Где используется `ON CONFLICT`/`RETURNING` — сохранить семантику.
5. **WebSocket handshake**: accept token from cookie OR `?token=` query param; та же валидация что и HTTP. Short-lived token через `/api/ws-token`.
6. **Transactional save**: `saveFamilyData` — все шаги в транзакции. После COMMIT — websocket/push notifications.
7. **Все REST-эндпоинты** должны сохранить те же пути и контракты (request/response JSON-структуры).

---

## Маппинг текущей технологии → замена в Quarkus

| Текущее (Node.js) | Целевое (Quarkus) |
|---|---|
| Vanilla HTTP server + custom Router | **RESTEasy Reactive** (`quarkus-rest`) |
| EJS шаблоны (SSR) | **Qute** (`quarkus-qute`, `quarkus-rest-qute`) |
| `pg` Pool + raw SQL | **Agroal DataSource** + **JDBC** (`quarkus-jdbc-postgresql`) с `JdbcTemplate`-подобным паттерном ИЛИ **Hibernate ORM with Panache** (если уместно для простых CRUD) |
| Ручной JWT (HMAC-SHA256) | **SmallRye JWT** (`quarkus-smallrye-jwt`) или `com.auth0:java-jwt` + custom filter |
| Custom CSRF middleware | Custom `@Provider` JAX-RS filter (`ContainerRequestFilter`) |
| Custom rate limiter | **Bucket4j** или custom filter |
| `sanitize-html` | **jsoup** (HTML sanitization) |
| `helmet` (security headers) | Custom `ContainerResponseFilter` |
| WebSocket (ws library) | **Quarkus WebSockets Next** (`quarkus-websockets-next`) |
| FCM + web-push (npm) | **Firebase Admin SDK (Java)** + **webpush-java** |
| In-memory TTL cache | **Caffeine** (`quarkus-cache`) |
| `compression` middleware | Quarkus built-in HTTP compression (`quarkus.http.enable-compression=true`) |
| Migrations (raw SQL files) | **Flyway** (`quarkus-flyway`) |
| Body parsing (JSON) | Jackson (встроен в Quarkus REST) |
| `process.env.*` | **MicroProfile Config** / SmallRye Config (`@ConfigProperty`) |

---

## Целевая структура пакетов Java

```
com.example.familyapp/
├── config/            # DataSource, JWT, Security конфигурация
├── web/
│   ├── rest/          # @Path REST-ресурсы (1:1 с /api/*)
│   ├── filter/        # CSRF filter, Auth filter, Security headers, Rate limit
│   ├── view/          # SSR контроллеры (Qute-шаблоны)
│   └── ws/            # WebSocket endpoint
├── service/           # Бизнес-логика (@ApplicationScoped)
├── repository/        # SQL через AgroalDataSource / JdbcClient
├── model/             # Java records для domain entities и DTOs
├── security/          # JWT utility, token generation/validation
├── push/              # FCM + WebPush сервис
└── util/              # Cache, sanitization, metrics, logging
```

---

## Порядок работы (строго последовательно)

### Фаза 0 — Анализ
1. Изучи всё описание проекта выше, составь полный маппинг эндпоинтов (path → method → controller → service → repo)
2. Выяви все несовместимости с native-компиляцией (reflection, dynamic proxies и т.д.)
3. Создай файл `MIGRATION_PLAN.md` с полным планом перед началом написания кода

### Фаза 1 — Фундамент
4. Создай корневой `pom.xml` под Maven 4.0.0 + Quarkus BOM + все необходимые расширения
5. Настрой `application.properties` для Quarkus: datasource (Agroal/PostgreSQL), JWT, HTTP, WebSocket, Flyway, кэш, профили
6. Настрой профили: `dev`, `test`, `prod`, `native`
7. Добавь `native-image` профиль с конфигурацией GraalVM

### Фаза 2 — Инфраструктурный слой
8. Реализуй JWT utility (`signToken`, `verifyToken`) с HMAC-SHA256 — сохрани payload-формат
9. Реализуй cookie builder (точные имена: `app_auth`, `app_role`, `csrf_token`, `family_id`, `child_id`)
10. Реализуй CSRF `ContainerRequestFilter` — логика из `validateCsrf`
11. Реализуй Auth `ContainerRequestFilter` — извлечение контекста из cookie, верификация JWT
12. Реализуй Security headers `ContainerResponseFilter`
13. Реализуй Rate limit filter
14. Реализуй HTML sanitization utility (jsoup)

### Фаза 3 — Слой данных
15. Настрой Agroal DataSource (pool: max=20, idle=30s, connect-timeout=5s)
16. Перенеси SQL-миграции в `src/main/resources/db/migration/` для Flyway
17. Реализуй repository-классы с параметризованными SQL-запросами (ON CONFLICT, RETURNING, транзакции)
18. Реализуй in-memory кэш через Caffeine (`@CacheResult` / `@CacheInvalidate`)

### Фаза 4 — Бизнес-логика
19. Перепиши сервисы: `AuthService`, `FamilyService`, `FamilyDataService`, `PushService`, `AnalyticsService`
20. Сохрани транзакционную семантику `saveFamilyData` (`@Transactional`)
21. Реализуй push-уведомления (FCM + WebPush)

### Фаза 5 — REST и WebSocket
22. Перепиши все REST-эндпоинты на **RESTEasy Reactive** (`@Path`, `@GET`, `@POST`, etc.)
23. Сохрани все пути и контракты (request/response JSON)
24. Реализуй SSR-контроллеры с **Qute** шаблонами (перенеси EJS → Qute)
25. Реализуй WebSocket endpoint (`@WebSocket`, `/ws`) с авторизацией по token/cookie
26. Реализуй `/api/ws-token` — выдача short-lived token (60 сек)

### Фаза 6 — Native-совместимость
27. Добавь `reflect-config.json`, `resource-config.json` где нужно
28. Замени incompatible-библиотеки на native-friendly альтернативы
29. Аннотируй DTO/record-классы `@RegisterForReflection` где необходимо
30. Убедись, что нет `sun.misc.Unsafe`, CGLIB-прокси и другого несовместимого кода

### Фаза 7 — Тесты и DevEx
31. Напиши тесты: `@QuarkusTest` для REST, `@QuarkusIntegrationTest` для интеграции
32. Добавь `@NativeImageTest` для проверки native-сборки
33. Настрой Quarkus Dev Services для PostgreSQL
34. Добавь `Dockerfile.native-micro` для минимального контейнера

### Фаза 8 — Финализация
35. Проверь, что все файлы компилируются без ошибок
36. Убери неиспользуемые зависимости
37. Обнови `README.md` с инструкциями по запуску в dev и native режимах
38. Создай `MIGRATION_CHANGELOG.md` — что было изменено и почему

---

## Жёсткие правила

- **Не используй Spring-совместимость** (`quarkus-spring-*`) — делай чистую Quarkus-миграцию
- **Предпочитай reactive-расширения** Quarkus где это не усложняет архитектуру
- **Каждый файл** должен компилироваться — не оставляй TODO/заглушки
- **Сохрани бизнес-логику** неизменной — меняй только инфраструктурный слой
- **Используй фичи Java 24**: records (для DTO/domain), sealed classes, pattern matching, virtual threads (где уместно)
- Если библиотека не совместима с native — предложи альтернативу и замени
- Все REST-эндпоинты должны сохранить **точные пути** и **JSON-контракты**
- Все cookie должны сохранить **точные имена** и **атрибуты**
- JWT payload должен содержать **те же поля** (`familyId`, `childId`, `role`, `email`, `csrfToken`, `exp`)
- Пиши **идиоматичный Quarkus-код**, а не "Node.js переписанный на Java"
- SQL-запросы должны оставаться **параметризованными** — никакой конкатенации строк
- WebSocket должен поддерживать авторизацию **и** через cookie, **и** через query-param `?token=`

---

## Структура вывода

Для каждого изменённого/созданного файла:
1. Укажи полный путь файла
2. Напиши файл целиком (не diff, не фрагмент)
3. Кратко прокомментируй что и зачем сделано
```