# Правила фронтенда — EarnIt Kids

> [!NOTE]
> AI agents: core rules live in root `AGENTS.md`. This file provides extended detail for humans.

## Общие принципы

- **Без фреймворков** — чистый HTML + Vanilla JS + Vanilla CSS
- **Модульная архитектура** — каждый JS-модуль в `public/js/modules/` отвечает за одну область (tasks, shop, friends, analytics и т.д.)
- **CommonJS не используется** — в браузерных модулях используются обычные `<script>` без бандлера; модули общаются через глобальный объект `window.CoinShop` / `window`
- **Серверный рендеринг** — HTML собирается на сервере из компонентов `views/components/`, клиент получает готовую страницу

---

## Структура файлов

```
public/
├── css/
│   ├── style.css              # Основные стили приложения
│   └── super-admin.css        # Стили страницы Super Admin
├── js/
│   ├── config.js              # API URL и общая конфигурация
│   ├── super-admin.js         # Логика панели Super Admin
│   └── modules/               # 36 модулей (функциональные области)
│       ├── main.js / main-init.js / main-tabs.js  # Точки входа, инициализация
│       ├── state.js           # Глобальное состояние приложения
│       ├── api.js             # Все API-вызовы (fetch)
│       ├── ui.js              # Общие UI-утилиты (тосты, модалки)
│       ├── utils.js           # Форматирование, хелперы
│       ├── action-*.js        # Бизнес-действия (tasks, shop, requests, history)
│       ├── admin-*.js         # Админские функции (children, settings, passwords, shop, tasks)
│       ├── ui-*.js            # Рендеринг секций (tasks, shop, requests, history, friends)
│       ├── analytics-ui.js    # Дашборд «Мои достижения»
│       ├── budget-ui.js       # Бюджетная аналитика
│       ├── child-switcher-ui.js # Переключатель между детьми
│       ├── friends.js         # Логика друзей
│       ├── rules.js           # Правила семьи
│       ├── push.js            # Push-уведомления
│       ├── pull-to-refresh.js # Pull-to-refresh для мобильных
│       └── ios-dev-fallback.js # Фоллбэк для iOS-разработки
├── img/                       # Изображения
└── about.html                 # Статическая страница "О приложении"

views/components/              # Серверные HTML-компоненты (17 файлов)
├── head.html                  # <head>, мета, стили
├── header.html                # Верхняя панель (баланс, монетки)
├── nav.html                   # Нижняя навигация (табы)
├── section_today.html         # Сегодня / главный экран
├── section_tasks.html         # Секция задач
├── section_shop.html / section_catalog.html  # Магазин
├── section_progress.html      # Прогресс и достижения
├── section_requests.html      # Запросы
├── section_analytics.html     # Аналитика / Достижения
├── section_history.html       # История
├── section_friends.html       # Друзья
├── section_rules.html         # Правила
├── section_settings.html      # Настройки
├── modals.html                # Все модальные окна
└── scripts.html               # Подключение JS-скриптов
```

---

## Правила написания CSS

1. **CSS-переменные обязательны** — все цвета, тени, радиусы, transitions определены в `:root` в `style.css`
2. **Тёмная тема по умолчанию** — `--color-bg: #0f172a`, `--color-text: #f8fafc`
3. **Именование** — используются семантические имена: `--color-primary`, `--color-success`, `--color-danger`, `--gradient-card`
4. **Миксины через переменные** — `--shadow-sm/md/lg`, `--radius-sm/md/lg/xl`, `--transition-fast/normal/slow`
5. **Адаптивность** — медиа-запросы для мобильных (`@media (max-width: 768px)` и `480px`). Mobile-first approach
6. **Шрифт** — `'Nunito'` как основной, fallback на системные
7. **Без препроцессоров** — чистый CSS, без SCSS/LESS/PostCSS

### Соглашения по именованию классов

- БЭМ-подобный стиль: `.pull-refresh-indicator__icon`, `.card__title`
- Секции: `.section-tasks`, `.section-shop`, `.section-analytics`
- Модификаторы: `.active`, `.ready`, `.loading`, `.hidden`

---

## Правила написания JavaScript

1. **Без бандлера** — каждый файл подключается отдельным `<script>` тегом
2. **Функциональный стиль** — модули экспортируют функции, а не классы
3. **API-вызовы** — все через `api.js`, используется `fetch` с `credentials: 'include'`
4. **Состояние** — хранится в `state.js` через `window.CoinShop.state` (familyId, role, childId, data)
5. **Роли** — UI адаптируется под `admin` (родитель) и `child` (ребёнок): разные видимые элементы, разные действия
6. **Модалки** — показ/скрытие через манипуляцию DOM (`display: block/none`, классы `.active`)
7. **Именование** — camelCase для функций и переменных
8. **Без TypeScript** — чистый ES6+ JavaScript
9. **Без async/await в старом коде** — в новом коде допускается `async/await`

---

## Правила создания новых секций/компонентов

1. Создать HTML-компонент в `views/components/section_*.html`
2. Добавить его в массив `componentOrder` в `viewController.js` → `serveIndex()`
3. Создать JS-модуль в `public/js/modules/` для логики
4. Подключить скрипт в `views/components/scripts.html`
5. Добавить таб в `nav.html` если секция видна в навигации
6. Стили добавить в `public/css/style.css`

---

## Мобильные правила

- **Pull-to-refresh** — реализован в `pull-to-refresh.js`, визуальный индикатор в CSS
- **Safe area** — учитывать `env(safe-area-inset-*)` для iOS
- **Touch-friendly** — минимальный размер tap-target 44×44px
- **Нет hover на мобильных** — использовать `:active` и `:focus` вместо `:hover` на сенсорных устройствах
- **Viewport** — `<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">`
- **PWA-подобное** — нативная обёртка через Android WebView / iOS WKWebView в папке `mobile/`
