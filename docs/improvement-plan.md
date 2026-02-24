# План улучшения проекта — EarnIt Kids

## Приоритеты

Задачи сгруппированы по уровням приоритета:
- 🔴 **Критично** — безопасность и стабильность
- 🟡 **Высокий** — качество кода и DX
- 🟢 **Средний** — новые возможности и оптимизация
- 🔵 **Низкий** — nice-to-have

---

## 🔴 Безопасность и стабильность

### 1. Улучшить аутентификацию
- [x] Заменить cookie `app_auth` (plain email) на подписанный JWT или secure session token
- [x] Добавить `HttpOnly`, `Secure`, `SameSite=Strict` флаги для cookies
- [x] Добавить CSRF-защиту для POST-запросов
- [x] Срок действия magic-link токенов (сейчас бессрочные)

### 2. Input Validation & Sanitization
- [x] Централизованная валидация входных данных (длина, тип, формат)
- [x] Sanitize HTML в пользовательском вводе (названия задач, описания)
- [x] Rate limiting на уровне API (не только для логина)

### 3. Error Handling
- [x] Централизованный error handler вместо try/catch в каждом контроллере
- [x] Структурированные ошибки с кодами (`INVALID_PARAM`, `NOT_FOUND`, `UNAUTHORIZED`)
- [x] Не отправлять stack trace клиенту в production

### 4. Логирование
- [x] Заменить `console.log` на структурированный логгер (pino / winston)
- [x] Уровни логирования (debug, info, warn, error)
- [x] Correlation ID для трассировки запросов

---

## 🟡 Качество кода и Developer Experience

### 5. Рефакторинг роутинга
- [x] Вынести body parsing в отдельный middleware (сейчас в каждом контроллере)
- [x] Создать Router-абстракцию вместо цепочки `if (url.startsWith(...))`
- [x] Разбить `familyController.js` (4753 байт) и `apiController.js` на более мелкие файлы

### 6. Тестирование
- [x] Покрыть unit-тестами сервисы (familyService, authService)
- [x] Добавить integration-тесты для API-эндпоинтов
- [x] Настроить test database для изолированных тестов
- [x] CI pipeline — запуск `npm run check` на push/PR
- [x] Добавить coverage report

### 7. TypeScript (опционально)
- [ ] Добавить JSDoc-типизацию для основных функций и объектов
- [ ] Или: постепенная миграция на TypeScript (начиная с `src/db/`)

### 8. Конфигурация
- [x] Валидация env-переменных при старте (с понятными ошибками)
- [x] Разделение конфигурации на development/production/test
- [x] Вынести магические числа (MAX_ATTEMPTS, BLOCK_WINDOW_MS) в конфиг

---

## 🟢 Новые возможности и оптимизация

### 9. Кеширование
- [x] Кеширование статических файлов (Cache-Control, ETag headers)
- [x] Кеширование собранного HTML (сейчас пересобирается на каждый запрос)
- [x] Кеширование DB-запросов для частых операций (список задач, баланс)

### 10. Производительность фронтенда
- [x] Минификация CSS/JS для production (через `npm run build`)
- [x] Lazy loading для секций (динамическая загрузка `analytics-ui.js`)
- [x] Service Worker для offline-поддержки (PWA)
- [x] Gzip/Brotli сжатие ответов сервера

### 11. API Improvements
- [x] Версионирование API (`/api/v1/...`)
- [x] OpenAPI/Swagger документация (доступна по `/api/docs`)
- [x] Пагинация для history и requests (добавлены `/api/v1/history` и `/api/v1/requests`)
- [x] WebSocket для уведомлений в реальном времени (новые задачи, одобрение запросов)

### 12. База данных
- [x] Connection pooling tuning (min/max/idle timeout)
- [x] Добавить `updated_at` on all основные таблицы
- [x] Миграция: автоматический rollback при ошибке (реализовано через транзакции в Postgres)
- [x] Добавить `EXPLAIN ANALYZE` для медленных запросов (логируется при `AUTO_EXPLAIN=true`)

### 13. Мониторинг
- [x] Health endpoint (`/api/health`) с проверкой БД и services
- [x] Логирование времени ответа и медленных запросов (>500ms)
- [x] Метрики (количество запросов, ошибки в Prometheus формате по `/api/metrics`)
- [x] Алерты при сбоях (email/telegram)

---

## 🔵 Nice-to-have

### 16. Инфраструктура
- [x] Database backups в телеграм бота файлы БД интервал настриваемый
- [x] Docker multi-stage build для оптимизации образа

### 17. Аналитика для родителей
- [x] Расширенные дашборды (тренды, сравнение с предыдущими периодами)
- [x] Recommendations engine (какие задачи мотивируют больше)
