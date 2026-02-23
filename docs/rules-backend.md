# Правила бэкенда — Coins Kids Shop

## Архитектура

- **Чистый Node.js** — `http.createServer()` без фреймворков (Express, Fastify, Koa)
- **CommonJS** — `require()` / `module.exports`, без ESM
- **Слоёная архитектура** — Controllers → Services → Repositories (DB)
- **PostgreSQL** — единственная БД, подключение через `pg` (node-postgres)

---

## Структура бэкенда

```
src/
├── app.js                     # Точка входа: HTTP-сервер, роутинг верхнего уровня
├── config/
│   └── index.js               # Конфигурация (PORT, пути, MIME-типы, лимиты)
├── controllers/
│   ├── apiController.js       # Роутинг API-запросов (/api/*)
│   ├── authController.js      # Аутентификация (login, register, verify, reset-password)
│   ├── childController.js     # Операции детей (magic-link, запросы)
│   ├── familyController.js    # Семейные операции (tasks, shop, history, settings)
│   ├── superAdminController.js # Super Admin (список семей, блокировка, бекапы)
│   └── viewController.js      # Рендеринг HTML-страниц из компонентов
├── middleware/
│   └── security.js            # Security-заголовки (HSTS, X-Frame-Options, XSS Protection)
├── routes/
│   ├── api.js                 # Маршрутизация API: auth → superAdmin → family
│   └── staticRouter.js        # Статические файлы + HTML-страницы
├── services/
│   ├── authService.js         # Логика аутентификации (пароли, bcrypt, токены, brute-force)
│   ├── backupService.js       # Резервное копирование БД
│   ├── baseDataService.js     # Шаблонные данные для новых семей
│   ├── emailService.js        # Email через Mailgun / Nodemailer
│   ├── familyService.js       # Бизнес-логика семей (CRUD, аналитика, друзья)
│   └── pushService.js         # Push-уведомления (FCM через Google Auth)
├── db/
│   ├── connection.js          # Pool подключений к PostgreSQL
│   ├── childRepository.js     # CRUD детей
│   ├── familyRepository.js    # CRUD семей
│   ├── familyDataRepository.js # Данные семьи (tasks, shop, history, requests, balance)
│   ├── pushTokenRepository.js # Push-токены устройств
│   ├── syncRepository.js      # Синхронизация данных (bulk save/load)
│   └── syncUtils.js           # Утилиты синхронизации
├── templates/                 # Email-шаблоны
└── utils/
    ├── buildVersion.js        # Версия сборки
    ├── startup-init.js        # Валидация env, инициализация БД
    └── stats-logger.js        # Статистика при старте
```

---

## Правила маршрутизации

### Порядок обработки запросов (app.js)

1. `setSecurityHeaders()` — Security-заголовки на все запросы
2. CORS-обработка (`handleCors`)
3. `/login-child/:token` → Magic-link авторизация ребёнка
4. НЕ `/api/*` → Статика и HTML-рендеринг (`staticRouter`)
5. `/api/*` → API-роутинг:
   - `/api/login|logout|register|forgot-password|reset-password|verify|auth-config` → `authController`
   - `/api/super/*` → `superAdminController`
   - Остальное → `familyController` / `childController`

### Аутентификация

- **Cookies** — `app_auth` (email), `family_id`, `app_role` (admin/child/super_admin)
- **Magic-link** — для детей: уникальный токен в URL `/login-child/:token`
- **Пароль** — для родителей: email + пароль
- **Brute-force защита** — `MAX_ATTEMPTS: 5`, `BLOCK_WINDOW_MS: 15 мин`

---

## Правила написания кода

### Контроллеры
- Принимают `(req, res)`, парсят body/params, вызывают сервис, отправляют JSON-ответ
- Обработка ошибок через `try/catch`, ответ `{ error: "message" }` с HTTP-кодом
- Не содержат бизнес-логики — делегируют в сервисы

### Сервисы
- Содержат бизнес-логику
- Работают с репозиториями для доступа к БД
- Возвращают объекты/массивы, не пишут в `res`
- Функции — именованные, экспортируются через `module.exports`

### Репозитории (db/)
- **Чистый SQL** через `pool.query()` с параметризованными запросами (`$1, $2, ...`)
- **НЕ использовать ORM** — только raw SQL
- Возвращают `rows` из результата запроса
- Транзакции — `BEGIN; ... COMMIT;` через `pool.connect()` → `client.query()`

### Общие правила
- **Отступы** — 4 пробела
- **Точка с запятой** — обязательна
- **camelCase** — для переменных и функций
- **Логирование** — `console.log()` / `console.error()` с emoji-префиксами (🪙, 🔍, ❌)
- **Env-переменные** — через `process.env`, загружаются `dotenv`
- **Секреты** — никогда не коммитить `.env`, использовать `.env.example` как образец

---

## API-конвенции

- **Формат** — JSON (Content-Type: `application/json`)
- **Метод** — POST для мутаций, GET для чтения
- **Ответ: успех** — `{ success: true, data: {...} }`
- **Ответ: ошибка** — `{ error: "описание" }` + соответствующий HTTP-код (400, 401, 403, 404, 500)
- **Body parsing** — ручной через `req.on('data')` / `req.on('end')`, JSON.parse

---

## Docker / деплой

- **Dockerfile** — стандартный Node.js образ
- **docker-compose.yml** — один сервис `coins-kids-shop`, порт `3001:3000`
- **Healthcheck** — `wget` к `http://127.0.0.1:3000/`
- **Restart policy** — `unless-stopped`
- **Env** — через `.env` файл

---

## Тестирование

- Unit-тесты — `node --test tests/*.test.js` (встроенный test runner Node.js)
- UI smoke-тесты — `tests/ui/*.test.js`
- E2E — Playwright (`tests/ui-e2e/`)
- Lint — ESLint (`eslint.config.cjs`) + custom lint scripts (`lint-syntax.js`, `lint-commonjs.js`)
- **Скрипты проверки**:
  - `npm run lint` — полный lint
  - `npm test` — unit + UI smoke
  - `npm run check` — lint + test
  - `npm run build` — check + E2E
