# План улучшения проекта — Coins Kids Shop

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
- [ ] Минификация CSS/JS для production
- [ ] Lazy loading для секций (не загружать analytics-ui.js до открытия вкладки)
- [ ] Service Worker для offline-поддержки (PWA)
- [x] Gzip/Brotli сжатие ответов сервера

### 11. API Improvements
- [x] Версионирование API (`/api/v1/...`)
- [ ] OpenAPI/Swagger документация
- [x] Пагинация для history и requests (добавлены `/api/v1/history` и `/api/v1/requests`)
- [ ] WebSocket для уведомлений в реальном времени (новые задачи, одобрение запросов)

### 12. База данных
- [x] Connection pooling tuning (min/max/idle timeout)
- [x] Добавить `updated_at` on all основные таблицы
- [ ] Миграция: автоматический rollback при ошибке
- [ ] Добавить `EXPLAIN ANALYZE` для медленных запросов

### 13. Мониторинг
- [x] Health endpoint (`/api/health`) с проверкой БД и services
- [x] Логирование времени ответа и медленных запросов (>500ms)
- [ ] Метрики (количество запросов, ошибки в Prometheus формате)
- [ ] Алерты при сбоях (email/push/telegram)

---

## 🔵 Nice-to-have

### 14. Интернационализация (i18n)
- [ ] Вынести все тексты в JSON-файлы переводов
- [ ] Поддержка EN/RU (сейчас UI на русском)
- [ ] Language switcher в настройках

### 15. Улучшения UX
- [ ] Звуковые эффекты при получении монет
- [ ] Достижения/бейджи за серии выполнений
- [ ] Streak-трекер (дни подряд)
- [ ] Уведомления в app (bell icon) вместо только push
- [ ] Dark/light theme toggle

### 16. Инфраструктура
- [ ] GitHub Actions CI/CD pipeline
- [ ] Staging-окружение
- [ ] Database backups в облако (S3 / R2)
- [ ] Docker multi-stage build для оптимизации образа
- [ ] SSL/TLS через Let's Encrypt в docker-compose

### 17. Аналитика для родителей
- [ ] Расширенные дашборды (тренды, сравнение с предыдущими периодами)
- [ ] Экспорт данных (CSV/PDF)
- [ ] Recommendations engine (какие задачи мотивируют больше)
