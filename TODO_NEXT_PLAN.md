# Полный план реализации: современная админка в стиле сайта + системный дашборд (без Next.js)

## 1) Цель и границы

## Цель

Сделать super-admin раздел визуально единым с основным сайтом и добавить в него расширенный техдашборд для контроля состояния сервера/БД/логов.

## В scope

- Редизайн super-admin под текущую дизайн-систему проекта.
- Новая вкладка “Система” с метриками:
    - CPU/load/memory/uptime процесса и хоста.
    - HTTP-метрики API.
    - Состояние БД.
    - Просмотр логов с фильтрацией.
- Новые read-only API для super_admin.
- Тесты (unit/integration/UI smoke), регрессии существующего super-admin.

## Вне scope

- Любая миграция/реализация под Next.js.
- Внешняя аналитика посещаемости (отложена по вашему решению).
- Изменение бизнес-логики семей/магазина/заданий.

———

## 2) Текущее состояние (что уже есть и переиспользуем)

- Отдельный super-admin интерфейс: views/super-admin.html, public/css/super-admin.css, public/js/super-admin.js.
- Базовые тех-endpoint’ы:
    - GET /api/health
    - GET /api/metrics (Prometheus text, только super_admin).
- Бэкап/восстановление БД в super-admin уже реализованы.
- Логирование через pino + endpoint client errors.
- Основная дизайн-система и токены уже есть в public/css/style.css (partials).

———

## 3) Архитектурные решения (decision-complete)

1. Стек фиксируем текущий: Node.js + CommonJS backend, vanilla JS/CSS/HTML frontend.
2. Дашборд строим внутри существующего super-admin, не выносим в отдельный сервис.
3. Новые API только read-only и только для роли super_admin.
4. Polling вместо websocket для первой версии:
    - overview/db: 10с
    - http-metrics: 15с
    - logs: 15с
5. Логи выдаем безопасно:
    - маскировка секретов/токенов/email.
    - ограничение limit (max 500).
6. UI строго на текущих токенах/компонентах проекта, без новых фреймворков.

———

## 4) Детальный план работ

## Этап A — UI модернизация super-admin (P0)

1. Обновить структуру страницы:
    - вкладки: Семьи, Каталог задач, Каталог товаров, База данных, Система.
2. Убрать инлайн-стили из views/super-admin.html, заменить на классы.
3. Переписать public/css/super-admin.css как слой поверх текущей дизайн-системы:
    - цвета/градиенты/тени/радиусы/типографика из токенов.
    - единые состояния кнопок/чипов/таблиц/модалок.
    - mobile-first + safe area.
4. Добавить унифицированные состояния:
    - loading / empty / error для каждой панели.

Критерий готовности этапа A
Визуально super-admin не выбивается из основного UI (темная тема, карточки, интерактив, адаптив).

———

## Этап B — backend сервисы системного дашборда (P1)

Создать сервисы:

1. src/services/systemStatsService.js
    - process uptime.
    - process.memoryUsage() (rss/heapUsed/heapTotal).
    - os.loadavg(), os.totalmem(), os.freemem(), os.cpus().length.
2. src/services/httpMetricsService.js
    - преобразование текущих счетчиков в агрегаты:
        - total requests
        - total errors
        - error rate %
        - top endpoints (count/errors/avg duration)
    - если p95/p99 пока нет в исходных метриках — явно вернуть null, без фейковых расчетов.
3. src/services/dbHealthService.js
    - ping БД (SELECT 1) + latency ms.
    - статус reserve БД (переиспользовать существующую проверку).
4. src/services/logsService.js
    - чтение последних N строк логов (tail-подход).
    - фильтрация по level.
    - санитизация:
        - токены/пароли/секреты/authorization/cookie/email маскировать.

Критерий готовности этапа B
Сервисы возвращают стабильные JSON DTO, не падают при частичных сбоях (например, DB down).

———

## Этап C — новые API super-admin (P1)

Добавить endpoint’ы:

1. GET /api/super/system/overview
2. GET /api/super/system/http-metrics
3. GET /api/super/system/db
4. GET /api/super/system/logs?level=info|warn|error&limit=1..500

Требования:

- обязательная проверка ctx.role === 'super_admin'.
- единый ответ:
    - success: true|false
    - timestamp
    - секция данных/ошибка.
- для ошибок:
    - 403 (доступ)
    - 400 (невалидные query params)
    - 500 (внутренние сбои)
- rate limit на /api/super/system/*.

Критерий готовности этапа C
Все endpoint’ы отвечают быстро, безопасно и предсказуемо; не ломают существующие super-admin маршруты.

———

## Этап D — фронтенд вкладка “Система” (P1)

Создать модуль public/js/modules/super-admin-system.js:

1. Загрузка и рендер карточек KPI:
    - CPU/load, RAM, uptime, DB ping, error rate.
2. Блок HTTP:
    - таблица top endpoints (метод, path, count, errors, avg ms).
3. Блок логов:
    - фильтр уровня + limit.
    - автообновление.
    - кнопка manual refresh.
4. Fault-tolerant UI:
    - если API недоступен — показать non-blocking warning.
5. Интеграция в public/js/super-admin.js:
    - запуск polling только при активной вкладке “Система”.
    - останов polling при переключении вкладки/выходе.

Критерий готовности этапа D
Панель живая, не перегружает API, корректно деградирует при ошибках.

———

## Этап E — тесты (обязательно)

## Unit

- systemStatsService:
    - валидные типы полей.
    - uptime > 0.
- httpMetricsService:
    - корректные агрегаты.
    - пустые метрики -> пустой безопасный ответ.
- dbHealthService:
    - connected/disconnected ветки.
- logsService:
    - limit clamp.
    - фильтр уровней.
    - маскирование секретов.

## Integration

- /api/super/system/*:
    - 200 для super_admin.
    - 403 для non-super_admin.
    - 400 для bad query (limit=-1, неизвестный level).
    - отказоустойчивость при недоступной БД.

## UI smoke

- вкладка “Система” отображается.
- KPI/таблица/логи рендерятся.
- автообновление не дублирует таймеры.

———

## 5) Контракты API (фиксируем заранее)

## GET /api/super/system/overview

{
"success": true,
"timestamp": "2026-02-26T12:00:00.000Z",
"process": {
"uptimeSec": 1234,
"rssBytes": 123,
"heapUsedBytes": 123,
"heapTotalBytes": 123
},
"os": {
"loadAvg1": 0.12,
"loadAvg5": 0.2,
"loadAvg15": 0.25,
"cpuCount": 8,
"totalMemBytes": 123,
"freeMemBytes": 123
}
}

## GET /api/super/system/http-metrics

{
"success": true,
"timestamp": "2026-02-26T12:00:00.000Z",
"summary": {
"requestsTotal": 1000,
"errorsTotal": 20,
"errorRatePct": 2
},
"topEndpoints": [
{
"method": "GET",
"path": "/api/data",
"count": 300,
"errors": 5,
"avgDurationMs": 42
}
],
"latency": {
"p95Ms": null,
"p99Ms": null
}
}

## GET /api/super/system/db

{
"success": true,
"timestamp": "2026-02-26T12:00:00.000Z",
"db": {
"connected": true,
"pingMs": 14,
"reserveConnected": true,
"lastError": null
}
}

## GET /api/super/system/logs?level=error&limit=100

{
"success": true,
"timestamp": "2026-02-26T12:00:00.000Z",
"query": {
"level": "error",
"limit": 100
},
"logs": [
{
"ts": "2026-02-26T11:59:59.000Z",
"level": "error",
"module": "app",
"msg": "Internal Server Error",
"reqId": "..."
}
]
}

———

## 6) Проверка результата (пошагово)

1. Локальные проверки кода:
    - ESLint измененных файлов через MCP mcp__eslint__lint-files.
    - npm test.
    - npm run build.
2. Ручная API-проверка:
    - под super_admin: все /api/super/system/* => 200.
    - под обычной ролью: все /api/super/system/* => 403.
    - bad params => 400.
3. Ручная UI-проверка super-admin:
    - вкладка “Система” корректно рендерится.
    - фильтры логов работают.
    - polling обновляет данные, не размножает запросы.
4. Регрессии существующего super-admin:
    - семьи (просмотр/блокировка/поиск/фильтры).
    - каталоги (add/edit/delete).
    - база (backup/restore/copy reserve).
5. Рекомендованный e2e прогон:
    - npm run test:ui:e2e.
6. Доп. регрессия по проектным потокам (обязательный чек-лист):
    - add/edit/delete shop item.
    - direct purchase админом.
    - child request + admin approve/reject.
    - frequency/money limits.

———

## 7) Риски и меры

- Утечка чувствительных данных в логах → жесткая маскировка + whitelist полей.
- Нагрузка от polling → интервалы 10–15с + пауза при неактивной вкладке.
- Шум в метриках → агрегировать и ограничить top-N (например, 20).
- Стили “поедут” → использовать только существующие токены/DS, без новых UI-подходов.

———

## 8) Допущения по умолчанию

- Next.js отсутствует и не рассматривается.
- Реализация только в текущем Node.js/vanilla стеке.
- Веб-аналитика посещаемости (внешний сервис) отложена в отдельную задачу.
- Доступ к системной панели — строго super_admin.