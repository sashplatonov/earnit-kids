# Обновлённый промпт: Разделение фронта и бэкенда + Миграция бэкенда на Java

```markdown
## Роль

Ты — senior architect, специализирующийся на разделении монолитных
Node.js-приложений на независимые frontend/backend-сервисы с последующей
миграцией бэкенда на современный Java-стек. Действуй автономно, пиши весь
код сам, не задавай уточняющих вопросов — принимай разумные решения
самостоятельно.

---

## Главная цель

**Разделить монолит** на два независимых сервиса:

| Сервис | Стек | Статус |
|---|---|---|
| **Frontend** | Node.js (текущий стек — vanilla HTTP + EJS + public JS) | ✅ Остаётся без изменений |
| **Backend API** | Java 24 + Quarkus 3.x Native + Maven 4.0.0 | 🔄 Полная миграция |

Фронтенд и бэкенд общаются **только через HTTP API** — никакого shared кода,
никаких shared зависимостей.

---

## Описание текущего монолита (Node.js)

### Высокоуровневая архитектура (слои)

| Слой | Текущая реализация (Node.js) |
|---|---|
| **HTTP Gateway** | `app.js` — single Node HTTP server, middlewares: compression, security headers, rate limiting, CORS |
| **Routing** | `src/routes/*` — собственный `Router` с поддержкой динамических сегментов (`:id`) |
| **Controllers** | `src/controllers/*` — парсинг запроса, cookie/JWT контекст, валидация, ответы |
| **Services** | `src/services/*` — бизнес-логика (auth, family, push, analytics) |
| **Repositories / DB** | `src/db/*` — SQL через `pg` Pool; параметризованные запросы; транзакции |
| **Views / Frontend** | `views/*` — EJS-шаблоны; `public/js/modules/*` — ванильный JS |
| **Utils** | `src/utils/*` — JWT, Router, WebSocket, logging, metrics, in-memory cache |

### Полный HTTP-запрос (монолит) — пошагово

1. Клиент → `fetch` с `credentials: 'same-origin'`.
2. `app.js` → security headers, rate-limiter, CORS → `apiRoutes` или `staticRouter`.
3. `createRouteContext` — достаёт `familyId`, `childId`, `role`, `csrfToken` из cookie `app_auth` через `verifyToken`.
4. `Router` → middlewares (`parseBody` → `sanitizePayload`).
5. `apiAuthMiddleware` для `POST/PUT/DELETE` — проверяет `ctx.familyId` + `validateCsrf`.
6. Controller → Service → Repository → DB. Транзакции через `getClient()`.
7. После COMMIT → `pushService.notifyFamilyChanges` (фон) + `websocket.notifyFamily`.
8. Ответ: `sendJSON` (API) или SSR (EJS).

---

## Архитектура после разделения

```
┌─────────────────────────────────┐     HTTP/JSON      ┌─────────────────────────────────┐
│       FRONTEND SERVICE          │ ──────────────────▶ │       BACKEND SERVICE           │
│  (Node.js — текущий стек)       │                     │  (Java 24 + Quarkus 3.x Native) │
│                                 │ ◀────────────────── │                                 │
│  • app.js (HTTP server)         │     JSON response   │  • RESTEasy Reactive API        │
│  • views/* (EJS templates)      │                     │  • Auth + JWT + CSRF            │
│  • public/* (vanilla JS)        │     WebSocket       │  • Business logic               │
│  • Static file serving          │ ◀══════════════════▶│  • PostgreSQL (Agroal)          │
│  • SSR rendering                │                     │  • WebSocket endpoint           │
│  • Proxy → Backend API          │                     │  • Push notifications           │
└─────────────────────────────────┘                     └─────────────────────────────────┘
:3000 (или любой)                                        :8080
```

---

## Часть A — Frontend Service (Node.js, минимальные изменения)

### Что остаётся во фронте

Всё, что связано с **отображением** и **статикой**:

```
frontend/
├── app.js                    # HTTP server (урезанный — только фронт)
├── src/
│   ├── routes/
│   │   ├── staticRouter.js   # Раздача статики (public/*)
│   │   └── viewRouter.js     # SSR маршруты → EJS рендер
│   ├── middleware/
│   │   ├── compression.js    # Сжатие
│   │   ├── securityHeaders.js# Заголовки безопасности
│   │   └── proxy.js          # Proxy запросов к Backend API
│   └── utils/
│       └── templateHelpers.js# Хелперы для EJS
├── views/                    # EJS шаблоны (без изменений)
└── public/                   # Статические ресурсы (без изменений)
├── js/modules/*
├── css/*
└── icons/*
```

### Что убирается из фронта

Всё, что связано с **бизнес-логикой, данными и аутентификацией**:

- ❌ `src/controllers/*` — переезжают в Java Backend
- ❌ `src/services/*` — переезжают в Java Backend
- ❌ `src/db/*` — переезжают в Java Backend
- ❌ `src/utils/jwt.js` — переезжает в Java Backend
- ❌ `src/utils/cache.js` — переезжает в Java Backend
- ❌ `src/utils/websocket.js` — переезжает в Java Backend
- ❌ `src/routes/apiRoutes.js` — переезжает в Java Backend

### Proxy-слой во фронте

Frontend проксирует все `/api/*` запросы на Backend, **прозрачно передавая**:
- Все куки (включая `app_auth`, `csrf_token`)
- Заголовок `X-CSRF-Token`
- Тело запроса
- IP-адрес клиента (`X-Forwarded-For`)

```javascript
// frontend/src/middleware/proxy.js — пример логики
// Все /api/* и /ws → проксируются на BACKEND_URL
// Куки и заголовки передаются без изменений
// SSE/WebSocket проксируются с upgrade
```

### SSR во фронте после разделения

EJS-шаблоны требуют данных — фронт получает их **от Backend API**:

```
Клиент → GET /dashboard
  → Frontend viewRouter
    → Frontend делает fetch(BACKEND_URL + '/api/page-data/dashboard', {headers: {Cookie: req.headers.cookie}})
    → Получает JSON с данными
    → Рендерит EJS с этими данными
    → Отдаёт HTML клиенту
```

Backend добавляет эндпоинты `/api/page-data/*` для SSR-данных.

### Конфигурация Frontend Service

```
# frontend/.env
BACKEND_URL=http://localhost:8080    # URL Java Backend
PORT=3000
NODE_ENV=production
```

---

## Часть B — Backend Service (Java 24 + Quarkus)

### Целевой стек

- **Java 24** (последний GA-релиз)
- **Quarkus 3.x** (последняя стабильная версия), режим **Native**
- **Maven 4.0.0**, формат `pom.xml` с BOM Quarkus
- Сборка: **GraalVM Native Image**

### Что переезжает в Backend

Всё, что связано с **данными, безопасностью и бизнес-логикой**:

- ✅ Все `/api/*` эндпоинты (REST)
- ✅ JWT generation / validation
- ✅ Cookie management (`app_auth`, `app_role`, `csrf_token`, `family_id`, `child_id`)
- ✅ CSRF validation
- ✅ Auth middleware logic
- ✅ Rate limiting
- ✅ Business services (auth, family, push, analytics)
- ✅ Repositories (PostgreSQL)
- ✅ WebSocket server (`/ws`, `/api/ws-token`)
- ✅ Push notifications (FCM, WebPush)
- ✅ In-memory cache
- ✅ DB migrations (Flyway)
- ✅ `/api/page-data/*` эндпоинты (новые, для SSR во фронте)

### Дополнительные эндпоинты для SSR (новые)

Backend добавляет группу эндпоинтов, которые фронт вызывает при SSR:

```
GET /api/page-data/dashboard   → JSON {user, familyData, ...}
GET /api/page-data/child/:id   → JSON {child, tasks, ...}
GET /api/page-data/settings    → JSON {family, members, ...}
```

Эти эндпоинты **требуют аутентификации** (cookie `app_auth`) так же, как остальные API.

### Инварианты — MUST сохранить

1. **Cookie names & semantics** (точные имена): `app_auth` (HttpOnly JWT), `app_role`, `csrf_token` (readable by JS), `family_id`, `child_id`. Атрибуты `SameSite`, `Path`, `Max-Age` — как в оригинале.
2. **JWT format**: HMAC-SHA256, payload fields: `familyId`, `childId`, `role`, `email`, `csrfToken`, `exp`.
3. **CSRF scheme**: non-HttpOnly `csrf_token` cookie + `X-CSRF-Token` header ИЛИ origin/referer/sec-fetch fallback.
4. **DB queries** — параметризованные. `ON CONFLICT`/`RETURNING` — сохранить семантику.
5. **WebSocket**: accept token from cookie OR `?token=` query param. Short-lived token через `/api/ws-token`.
6. **Transactional save**: `saveFamilyData` — в транзакции. После COMMIT → websocket/push.
7. **Все REST-эндпоинты** — те же пути и JSON-контракты.
8. **CORS**: Backend должен принимать запросы от Frontend origin (`FRONTEND_URL`), передавать куки (`Access-Control-Allow-Credentials: true`).

### Маппинг технологий → Quarkus

| Текущее (Node.js) | Целевое (Quarkus) |
|---|---|
| Vanilla HTTP + custom Router | **RESTEasy Reactive** (`quarkus-rest`) |
| `pg` Pool + raw SQL | **Agroal DataSource** + **JDBC** (`quarkus-jdbc-postgresql`) |
| Ручной JWT (HMAC-SHA256) | `com.auth0:java-jwt` + custom filter |
| Custom CSRF middleware | `ContainerRequestFilter` |
| Custom rate limiter | **Bucket4j** или custom filter |
| `sanitize-html` | **jsoup** |
| `helmet` (security headers) | `ContainerResponseFilter` |
| WebSocket (ws library) | **Quarkus WebSockets Next** (`quarkus-websockets-next`) |
| FCM + web-push | **Firebase Admin SDK (Java)** + **webpush-java** |
| In-memory TTL cache | **Caffeine** (`quarkus-cache`) |
| `compression` middleware | `quarkus.http.enable-compression=true` |
| Migrations (SQL files) | **Flyway** (`quarkus-flyway`) |
| `process.env.*` | **MicroProfile Config** (`@ConfigProperty`) |

### Целевая структура пакетов Java

```
backend/
└── src/main/java/com/example/familyapp/
    ├── config/            # DataSource, JWT, Security, CORS конфигурация
    ├── web/
    │   ├── rest/          # @Path REST-ресурсы (1:1 с /api/*)
    │   ├── rest/pagedata/ # GET /api/page-data/* (SSR data для фронта)
    │   ├── filter/        # CSRF, Auth, Security headers, Rate limit
    │   └── ws/            # WebSocket endpoint
    ├── service/           # Бизнес-логика (@ApplicationScoped)
    ├── repository/        # SQL через AgroalDataSource / JdbcClient
    ├── model/             # Java records (domain entities + DTOs)
    ├── security/          # JWT utility, token generation/validation
    ├── push/              # FCM + WebPush сервис
    └── util/              # Cache, sanitization, metrics, logging
```

---

## Порядок работы (строго последовательно)

### Фаза 0 — Анализ и планирование разделения

1. Изучи монолит, составь **карту разделения**: каждый файл → Frontend или Backend
2. Определи все **точки взаимодействия** (что фронт вызывает, что Backend должен отдать)
3. Составь список **новых эндпоинтов** `/api/page-data/*` нужных для SSR
4. Выяви все **несовместимости** с native-компиляцией
5. Создай `SPLIT_PLAN.md` и `MIGRATION_PLAN.md`

### Фаза 1 — Структура проекта

6. Создай корневой `package.json` или `Makefile` для запуска обоих сервисов
7. Создай структуру:
   ```
   /
   ├── frontend/          # Существующий Node.js (модифицированный)
   ├── backend/           # Новый Java/Quarkus проект
   ├── docker-compose.yml # Запуск всего стека
   └── SPLIT_PLAN.md
   ```
8. Создай `backend/pom.xml` под Maven 4.0.0 + Quarkus BOM
9. Настрой `backend/src/main/resources/application.properties`

### Фаза 2 — Frontend: минимальные изменения

10. Модифицируй `frontend/app.js` — убери API-маршруты, добавь proxy-middleware
11. Реализуй `frontend/src/middleware/proxy.js` — прозрачный прокси на Backend
12. Реализуй `frontend/src/routes/viewRouter.js` — SSR маршруты с fetch к Backend
13. Убери из frontend: controllers, services, db, jwt, cache, websocket utils
14. Добавь `frontend/.env.example` с `BACKEND_URL`

### Фаза 3 — Backend: инфраструктурный слой

15. Реализуй JWT utility (`signToken`, `verifyToken`) с HMAC-SHA256
16. Реализуй cookie builder (точные имена и атрибуты)
17. Реализуй CSRF `ContainerRequestFilter`
18. Реализуй Auth `ContainerRequestFilter` (JWT из cookie)
19. Реализуй Security headers `ContainerResponseFilter`
20. Реализуй CORS конфигурацию (разрешить `FRONTEND_URL`, `credentials: true`)
21. Реализуй Rate limit filter
22. Реализуй HTML sanitization utility (jsoup)

### Фаза 4 — Backend: слой данных

23. Настрой Agroal DataSource (pool: max=20, idle=30s, connect-timeout=5s)
24. Перенеси SQL-миграции в `backend/src/main/resources/db/migration/` для Flyway
25. Реализуй repository-классы с параметризованными SQL-запросами
26. Реализуй in-memory кэш через Caffeine

### Фаза 5 — Backend: бизнес-логика

27. Перепиши сервисы: `AuthService`, `FamilyService`, `FamilyDataService`, `PushService`, `AnalyticsService`
28. Сохрани транзакционную семантику `saveFamilyData` (`@Transactional`)
29. Реализуй push-уведомления (FCM + WebPush)

### Фаза 6 — Backend: REST и WebSocket

30. Перепиши все `/api/*` эндпоинты на RESTEasy Reactive
31. Добавь `/api/page-data/*` эндпоинты для SSR
32. Реализуй WebSocket endpoint (`/ws`) с авторизацией
33. Реализуй `/api/ws-token` — short-lived token (60 сек)

### Фаза 7 — Native-совместимость

34. Добавь `reflect-config.json`, `resource-config.json`
35. Аннотируй DTO/records `@RegisterForReflection`
36. Убедись в отсутствии CGLIB, `sun.misc.Unsafe`
37. Добавь `native-image` профиль в `pom.xml`

### Фаза 8 — Интеграция и тесты

38. Напиши `docker-compose.yml` (frontend + backend + postgres + [redis опционально])
39. Напиши тесты: `@QuarkusTest`, `@QuarkusIntegrationTest`, `@NativeImageTest`
40. Настрой Quarkus Dev Services для PostgreSQL
41. Добавь `backend/Dockerfile.native-micro`
42. Добавь `frontend/Dockerfile`

### Фаза 9 — Финализация

43. Проверь компиляцию всех файлов
44. Убери неиспользуемые зависимости
45. Обнови `README.md`: dev-запуск, native-сборка, переменные окружения
46. Создай `MIGRATION_CHANGELOG.md`

---

## Жёсткие правила

### Общие
- Каждый файл должен **компилироваться** — не оставляй TODO/заглушки
- Сохрани **бизнес-логику** неизменной — меняй только инфраструктурный слой
- Пиши **идиоматичный код** для каждого стека (не "Node.js переписанный на Java")

### Frontend (Node.js)
- **Минимальные изменения** — убираем только то, что уходит в Backend
- Фронт **не дублирует** никакую логику бэкенда
- Proxy **прозрачен** — клиентский JS не знает о разделении
- Куки устанавливаются **Backend-ом**, фронт их только проксирует
- Фронт **не валидирует** JWT самостоятельно

### Backend (Java/Quarkus)
- **Не используй** `quarkus-spring-*` — чистая Quarkus-миграция
- **Предпочитай reactive-расширения** где это не усложняет архитектуру
- **Используй фичи Java 24**: records, sealed classes, pattern matching, virtual threads
- SQL-запросы **параметризованы** — никакой конкатенации строк
- **CORS** настроен для приёма запросов от Frontend с credentials
- WebSocket поддерживает авторизацию **и** через cookie, **и** через `?token=`

---

## Точки взаимодействия (Frontend ↔ Backend)

### Стандартные API-запросы
```
Клиент (браузер) → Frontend proxy → Backend /api/*
                                   ← JSON response + Set-Cookie
```

### SSR-рендеринг
```
Клиент (браузер) → Frontend /dashboard
                 → Frontend fetch(Backend /api/page-data/dashboard)
                            ← JSON {user, data}
                 → EJS render(data)
                 ← HTML
```

### WebSocket
```
Клиент (браузер) → Backend /ws?token=xxx  (прямое подключение)
              ИЛИ → Frontend proxy /ws    → Backend /ws
```

### Аутентификация (login flow)
```
Клиент → Frontend proxy → Backend POST /api/auth/login
                         ← JSON + Set-Cookie (app_auth, csrf_token, ...)
Frontend проксирует Set-Cookie заголовки обратно клиенту
```

---

## Структура вывода

Для каждого созданного/изменённого файла:
1. Укажи полный путь файла (относительно корня проекта)
2. Укажи принадлежность: `[FRONTEND]` или `[BACKEND]` или `[ROOT]`
3. Напиши файл **целиком** (не diff, не фрагмент)
4. Кратко прокомментируй: что сделано, почему, какие решения приняты

Порядок вывода файлов:
```
[ROOT] SPLIT_PLAN.md
[ROOT] MIGRATION_PLAN.md
[ROOT] docker-compose.yml
[FRONTEND] frontend/app.js (модифицированный)
[FRONTEND] frontend/src/middleware/proxy.js (новый)
[FRONTEND] frontend/src/routes/viewRouter.js (модифицированный)
[FRONTEND] frontend/.env.example
[BACKEND] backend/pom.xml
[BACKEND] backend/src/main/resources/application.properties
... и далее по фазам
```
```