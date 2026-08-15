# Редизайн публичного сайта (reference → SvelteKit) — Implementation Backlog v7 — Task Split

## Цель (Goal)

Заменить текущий публичный (маркетинговый) сайт EarnIt Kids новой версией, перенесённой из статического референса `earnit-kids-public-site 7`, в SvelteKit.

На первом этапе публичный сайт работает **только на русском языке**. Архитектура должна позволять позже добавить английский и другие языки без переписывания маршрутов, компонентов и бизнес-логики.

Текущие публичные страницы не удаляются до готовности замены. После успешного cutover они перемещаются в `apps/web/legacy/public-site/`.

В Mini App появляется возможность поделиться ссылкой на публичный сайт. Поддержка шаринга из Telegram-бота остаётся опциональной.

---

## Ключевые архитектурные решения

### 1. Только RU сейчас, расширяемость позже

- На первом этапе публичный сайт имеет один пользовательский язык — русский.
- Публичные страницы используют i18n-домен `public`, но **не требуется создавать английские копии всех текстов сейчас**.
- Marketing copy хранится по страницам в `apps/web/src/lib/content/public/ru/*`, reusable UI labels - в `apps/web/src/lib/i18n/messages/ru/public.ts`.
- Компоненты публичного сайта не должны содержать длинный маркетинговый текст напрямую, кроме технических fallback-строк.
- Ограничение текущего i18n-типа: `MessageKey` выводится из английского каталога (`apps/web/src/lib/i18n/index.ts`), а `svelte-check` (часть `npm run lint`) проверяет ключи `t(...)`. Поэтому каждый reusable label-ключ (`public.navigation.*`, `public.cta.*`, `public.common.*`, `public.share.*`) должен быть объявлен и в `apps/web/src/lib/i18n/messages/en/public.ts`, даже если его значение временно совпадает с RU. Полные EN marketing-тексты при этом не нужны — они остаются в `content/public/en/*` на будущее.
- Архитектура маршрутов и URL не должна зависеть от количества языков.
- При добавлении EN позже достаточно:
  1. добавить `content/public/en/*` и `messages/en/public.ts`;
  2. включить locale в публичный route resolver;
  3. добавить localized canonical/hreflang;
  4. включить переключатель языка, если он потребуется.

### 2. Канонический публичный URL

- Один источник правды для публичного origin — `appConfig.publicOrigin`.
- Если `publicOrigin` не задан, fallback — `window.location.origin` только в browser-side коде.
- Все ссылки на публичный сайт строятся через один helper/service:
  - `getPublicSiteUrl(path?)`;
  - позже API может быть расширен до `getPublicSiteUrl(path?, locale?)`.
- Helper используется в:
  - Public CTA;
  - Mini App sharing;
  - Telegram bot;
  - canonical URL;
  - Open Graph URL;
  - sitemap.

Нельзя вручную собирать URL через `window.location.origin + '/ru'` в разных местах.

### 3. URL-модель первого этапа

На первом этапе:

- `/` — canonical URL русской главной страницы;
- `/how`
- `/tasks`
- `/rewards`
- `/parents`
- `/faq`

Не создавать публичные `/ru/*` URL только ради будущей локализации.

> Текущий рантайм устроен иначе: `apps/web/src/hooks.server.ts` сейчас редиректит `/` → `/en/` и через `shouldCanonicalizePath` приводит все bare-пути к `/{locale}/...`; `$i18n.href()` и `alternates()` тоже всегда добавляют locale-префикс. Поэтому bare-URL для public-страниц — это явное изменение, которое выполняется в PUB-02A (hooks + `resolveDomainsForPath` + отказ от `$i18n.href()` в public-компонентах в пользу обычных `<a href>`/resolver).

При добавлении нескольких языков позже migration может быть выполнен отдельно, например:

- `/ru/...`
- `/en/...`

с permanent redirect старых RU URL на `/ru/...`, если будет принято такое продуктовое решение.

### 4. Безопасная миграция

Legacy-маршруты не отключаются до готовности всех новых страниц.

Порядок:

```text
foundation
→ новые страницы
→ SEO / metadata
→ redirects
→ smoke/E2E
→ archive legacy
```

Не допускается состояние production, в котором существующие URL временно начинают возвращать 404 только из-за порядка выполнения backlog.

### 5. Общий Public Site Layout через SvelteKit route group

Публичные страницы должны жить в одном route group и автоматически получать общий shell через SvelteKit layout:

```text
apps/web/src/routes/(public)/
├── +layout.svelte
├── +page.svelte
├── how/+page.svelte
├── tasks/+page.svelte
├── rewards/+page.svelte
├── parents/+page.svelte
└── faq/+page.svelte
```

`(public)/+layout.svelte` владеет:

- `PublicSiteHeader`;
- `<main>`;
- `PublicSiteFooter`;
- подключением public styles.

Страницы не должны вручную оборачивать себя в `PublicSiteLayout.svelte`. Route group не влияет на URL.

Корневой `apps/web/src/routes/+layout.svelte` остаётся глобальным (импортирует `../app.css` и рендерит `<slot/>`), а его `apps/web/src/routes/+layout.server.ts` уже отдаёт `appConfig`, `i18n`, `locale`, `session`; эти данные каскадно доступны в `(public)/+layout.svelte` через `data`. Отдельный `+layout.server.ts` для `(public)` не нужен, если public не вводит собственный load.

### Root auth redirect contract

До переноса `/` внутрь `(public)` проверить текущую root server-логику. Нельзя рассчитывать, что существующий `src/routes/+page.server.ts` автоматически применится к `(public)/+page.svelte`.

Если root `+page.server.ts` сейчас отвечает за redirect authenticated/super-admin пользователей, перенести или переиспользовать эту server-логику на совместимом уровне внутри новой route structure.

Текущий `apps/web/src/routes/+page.server.ts` именно это и делает (redirect authenticated → `/app/*`, super-admin → `/super-admin`), а `apps/web/src/routes/+page.svelte` — старая landing. Оба файла нужно перенести/удалить: SvelteKit не разрешает два `+page` для одного `/` (root и `(public)`), иначе сборка падает с route collision.

Обязательный integration contract:

```text
anonymous GET /      → public landing
authenticated GET /  → /app
super-admin GET /    → /super-admin
```

Не допускать двух конкурирующих `+page` для одного URL.

### 6. Публичный сайт не требует backend API

Страницы чисто презентационные.

Backend API не добавляется для:

- контента публичного сайта;
- CTA;
- sharing.

Исключение — уже существующая server-side конфигурация приложения.

### 7. Telegram Mini App URL

CTA «Открыть в Telegram» конфигурируется через env:

`PUBLIC_TELEGRAM_MINI_APP_URL`

URL не хардкодится.

Если URL отсутствует, **не показывать disabled CTA**. Использовать один из вариантов:

- скрыть CTA;
- показать альтернативный доступный CTA, если он существует по продуктовой логике.

Конфигурация CTA должна быть доступна уже при SSR render через `appConfig`, чтобы CTA не появлялась только после hydration и не создавала layout shift/flicker.

Пользователь не должен видеть «сломанный» главный CTA из-за конфигурации deployment.


### Progressive enhancement и semantic navigation

Основной public-site flow должен оставаться usable даже если hydration/часть client JS не сработала.

Обязательные правила:

- internal navigation использует обычные `<a href>`;
- Telegram CTA использует обычную ссылку там, где Telegram WebApp API не даёт отдельного UX-преимущества;
- FAQ работает через native `<details>/<summary>`;
- основной marketing content рендерится SSR;
- screenshot gallery не требует JS для базового просмотра;
- `button` используется для действий, а не для навигации.

Минимальный no-JS smoke contract:

```text
/ открывается
navigation работает
/how открывается
/tasks открывается
FAQ раскрывается
Telegram link остаётся доступной
```

### 8. Legacy

После успешного cutover старые публичные страницы перемещаются в:

`apps/web/legacy/public-site/`

вне `src/routes`.

Использовать `git mv`.

### 9. Редиректы

Для окончательно заменённых URL использовать permanent redirects:

- предпочтительно `308`;
- `301` допустим, если текущая инфраструктура лучше его поддерживает.

302 использовать только для действительно временных переходов.

### 10. Trust и parent-control contract

Public site должен явно объяснять границы контроля родителя, а не только перечислять features.

Минимальный trust contract:

- родитель управляет заданиями и наградами;
- ребёнок может отмечать выполнение, но не начисляет себе монеты;
- начисление/подтверждение контролирует родитель;
- роли Parent/Child визуально и смыслово разделены;
- если ребёнку не нужен отдельный Telegram/account/device, это говорится прямо;
- privacy/security формулировки должны соответствовать реальному продукту и не обещать того, чего нет.

Trust-блок должен появляться на `/parents` и кратко на главной.

### 11. Favicon

Используется существующий набор приложения из `apps/web/static/img/`.

Favicon из reference не копируются в SvelteKit.

---


# Правила размера задач

Цель декомпозиции - чтобы implementation task имела **одну основную ответственность**, отдельный проверяемый результат и могла быть реализована/ревьюиться независимо.

Разбивать задачу обязательно, если выполняются минимум два пункта:

- внутри одновременно есть route/config, UI, content и testing;
- есть 2+ независимых результата, один из которых можно выпустить/проверить без второго;
- acceptance criteria относятся к разным слоям системы;
- задача требует менять много несвязанных областей проекта;
- ошибка в одной части не должна блокировать проверку другой;
- задача фактически является Epic, а не единицей реализации.

Практический формат:

```text
Epic
├── A — foundation/contract
├── B — independent feature/page
├── C — independent feature/page
└── D — validation/polish
```

Epic сам по себе не является implementation task. Коммит/PR делается по подзадаче, если нет явной причины объединять.

Не дробить до уровня «создать один файл» или «поменять один CSS token». Слишком мелкая декомпозиция создаёт больше coordination cost, чем пользы.

---

# Рекомендуемый порядок реализации

| № | Задача | Приоритет | Зависит от | Результат |
|---:|---|---|---|---|
| 1 | PUB-02A | P1 | - | Route group + root auth contract |
| 2 | PUB-02B | P1 | PUB-02A | Public origin + URL/CTA config |
| 3 | PUB-02C | P1 | PUB-02A | UI shell + navigation + a11y |
| 4 | PUB-02D | P1 | PUB-02A | Typed RU content + UI labels + ownership |
| 5 | PUB-03 | P1 | PUB-02A..PUB-02D | ✅ Главная |
| 6 | PUB-04A | P1 | PUB-02A..PUB-02D | ✅ `/how` |
| 7 | PUB-04B | P1 | PUB-02A..PUB-02D | ✅ `/parents` |
| 8 | PUB-05A | P1 | PUB-02A..PUB-02D | ✅ `/tasks` |
| 9 | PUB-05B | P1 | PUB-02A..PUB-02D | ✅ `/rewards` |
| 10 | PUB-06 | P1 | PUB-02A..PUB-02D | ✅ FAQ |
| 11 | PUB-07A | P1 | PUB-03..PUB-06 | ✅ Page metadata + canonical + OG |
| 12 | PUB-07B | P1 | PUB-07A | ✅ Sitemap + environment indexing + 404 |
| 13 | PUB-08 | P1 | PUB-02B, PUB-02C | ✅ Sharing из Mini App |
| 14 | PUB-09 | P1 | PUB-03..PUB-07B | ✅ Permanent redirects |
| 15 | PUB-11A | P1 | PUB-03..PUB-09 | ✅ Functional E2E + progressive enhancement |
| 16 | PUB-11B | P1 | PUB-11A | Responsive + accessibility + content stress |
| 17 | PUB-11C | P1 | PUB-11A | Visual regression + performance |
| 18 | PUB-10 | P1 | PUB-11A..PUB-11C | Cutover + archive legacy |
| 19 | PUB-12 | P2 | PUB-02B, PUB-02C | Минимальная product analytics |
| 20 | PUB-13 | P3 | PUB-02B | Sharing из Telegram-бота |
| 21 | PUB-01 | P3 | - | Очистить favicon reference |
| 22 | PUB-14 | P3 | - | Опциональная чистка favicon приложения |

---

## Команды проверки (по задачам)

Web-задачи (PUB-01..PUB-12, PUB-14) — из `apps/web`:

```bash
cd apps/web
npm run lint       # svelte-kit sync + eslint + svelte-check (проверяет MessageKey)
npm run test       # vitest unit
npm run build      # vite build (adapter-node)
```

UI/E2E-задачи (PUB-02C, PUB-03..PUB-09, PUB-11A..PUB-11C) дополнительно:

```bash
cd apps/web
npm run test:e2e   # playwright
```

Backend-задача PUB-13 — из `apps/backend`:

```bash
cd apps/backend
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-amzn" ./mvnw verify
```

---

# PUB-01: Убрать лишние favicon из reference

**Статус:** ⬜ Не начато  
**Приоритет:** P3  
**Зависит от:** -

## Результат

В reference остаётся минимальный favicon-набор для статического preview. В SvelteKit favicon reference не копируются.

## Работа

1. Удалить:
   - `assets/icons/favicon-16x16.png`
   - `favicon-32x32.png`
   - `favicon-48x48.png`
   - `favicon-64x64.png`
   - `favicon-180x180.png`
   - `favicon-192x192.png`
   - `favicon-512x512.png`.
2. Оставить reference preview assets:
   - `favicon.svg`
   - `favicon.ico`
   - `apple-touch-icon.png`
   - `assets/icons/app-icon.png`
   - `assets/icons/web-ui-icon.png`.
3. Проверить отсутствие битых ссылок.
4. Не копировать reference favicon в SvelteKit.

## Критерии приёмки

- Reference preview открывается без битых favicon.
- В SvelteKit используется существующий favicon приложения.
- `npm run build` зелёный.

---

# PUB-02A: Route group и root auth contract

**Статус:** ✅ Завершено  
**Приоритет:** P1  
**Зависит от:** -

## Результат

Public routes собраны в SvelteKit route group без конфликта с существующей root auth-логикой.

## Работа

Создать/использовать:

```text
apps/web/src/routes/(public)/
├── +layout.svelte
├── +page.svelte
├── how/+page.svelte
├── tasks/+page.svelte
├── rewards/+page.svelte
├── parents/+page.svelte
└── faq/+page.svelte
```

До переноса `/` проверить текущий `src/routes/+page.server.ts`.

Обязательный contract:

```text
anonymous GET /      → public landing
authenticated GET /  → /app
super-admin GET /    → /super-admin
```

Если server logic нужно перенести внутрь новой route structure, сделать это здесь.

## Файлы

- Создать `apps/web/src/routes/(public)/+layout.svelte` (владеет `PublicSiteHeader`, `<main>`, `PublicSiteFooter`, public styles).
- Создать `apps/web/src/routes/(public)/+page.svelte` (заглушка/новая landing; контент наполняется в PUB-03).
- Создать `apps/web/src/routes/(public)/+page.server.ts` (перенести сюда auth redirect из root `+page.server.ts`).
- Удалить `apps/web/src/routes/+page.svelte` и `apps/web/src/routes/+page.server.ts` (иначе два `+page` для `/` → route collision).
- Изменить `apps/web/src/hooks.server.ts`: public-пути (`/`, `/how`, `/tasks`, `/rewards`, `/parents`, `/faq`) обслуживать без locale-префикса и с `locals.locale = 'ru'` (по аналогии с текущей обработкой `/telegram`); убрать редирект `/` → `/en/` для public landing.
- Изменить `apps/web/src/lib/i18n/config.ts`: `shouldCanonicalizePath` не должен редиректить bare public-пути; `resolveDomainsForPath` должен отдавать `['common','public','errors']` для `/`, `/how`, `/tasks`, `/rewards`, `/parents`, `/faq` и убрать `/about`, `/features`, `/blog`.

## Критерии приёмки

- нет двух конкурирующих `+page` для `/`;
- `(public)` не меняет URL;
- anonymous/authenticated/super-admin сценарии покрыты integration test;
- новый route group компилируется;
- lint/test/build зелёные.

---

# PUB-02B: Public origin, URL resolver и CTA config

**Статус:** ✅ Завершено  
**Приоритет:** P1  
**Зависит от:** PUB-02A

## Результат

Все public URL и Telegram CTA используют один config contract и доступны уже на SSR.

## Работа

Создать:

`apps/web/src/lib/services/publicSiteUrl.ts`

API:

```ts
getPublicSiteUrl(path?: string): string
```

Требования:

- source of truth для origin - `appConfig.publicOrigin`;
- browser fallback - `window.location.origin`, только где нужен;
- slash/path нормализуются;
- `/ru` не хардкодится;
- API можно позже расширить параметром locale;
- `PUBLIC_TELEGRAM_MINI_APP_URL` попадает в `appConfig`;
- CTA availability известна уже при SSR render;
- отсутствие Telegram URL не создаёт disabled/dead CTA.

## Файлы

- Создать `apps/web/src/lib/services/publicSiteUrl.ts` (`getPublicSiteUrl(path?)`).
- Изменить `apps/web/src/lib/types/config.ts`: добавить `telegramMiniAppUrl: string | null` в `AppConfig`.
- Изменить `apps/web/src/lib/server/config.ts`: читать `PUBLIC_TELEGRAM_MINI_APP_URL` (или `TELEGRAM_MINI_APP_URL`) в `loadAppConfig`.
- Изменить `.env.example`: добавить `PUBLIC_TELEGRAM_MINI_APP_URL=` с примером `https://t.me/<bot>?startapp=home`.
- Учесть: клиентского config-стора нет — `appConfig.publicOrigin` приходит только через `data.appConfig` из root `+layout.server.ts`. Поэтому `getPublicSiteUrl(path, publicOrigin?)` принимает origin параметром, а browser-only fallback — `window.location.origin`.

## Критерии приёмки

- public URL одинаково корректен при одинаковом и отдельном marketing origin;
- canonical/OG/share смогут переиспользовать resolver;
- CTA не появляется только после hydration;
- URL resolver покрыт unit tests;
- lint/test/build зелёные.

---

# PUB-02C: UI shell, navigation и accessibility foundation

**Статус:** ✅ Завершено  
**Приоритет:** P1  
**Зависит от:** PUB-02A

## Результат

Создан общий public shell и минимальный UI contract без page-specific marketing content.

## Shared UI

`(public)/+layout.svelte` владеет:

- `PublicSiteHeader`;
- `<main>`;
- `PublicSiteFooter`;
- public styles.

Создавать только реально переиспользуемые компоненты:

- `PublicSiteHeader.svelte`;
- `PublicSiteFooter.svelte`;
- `PublicButton.svelte`;
- `PublicSection.svelte`;
- `PublicCard.svelte` только при повторяющемся card pattern;
- `PublicScreenshotGallery.svelte` только при повторном использовании.

## Navigation

Desktop:

```text
Как работает
Задания
Награды
Для родителей
Вопросы
[Открыть EarnIt Kids в Telegram]
```

Logo ведёт на `/`.

Mobile:

- brand;
- menu button;
- раскрываемое меню;
- CTA внутри меню;
- без horizontal-scroll navigation;
- без sticky CTA в MVP.

### Mobile menu contract

- `aria-expanded` + `aria-controls`;
- `Escape` закрывает;
- выбор ссылки закрывает;
- focus возвращается на trigger;
- overlay блокирует background scroll;
- focus не уходит за modal-like overlay;
- scroll position страницы не сбрасывается.

## Semantic/progressive enhancement contract

- navigation и link-like CTA - реальные `<a href>`;
- `button` только для действий;
- marketing content SSR;
- FAQ использует native `<details>/<summary>`;
- базовый просмотр screenshots не зависит от JS;
- основной flow остаётся usable при hydration failure.

## UI tokens

Минимум:

- content max-width;
- text max-width;
- section spacing;
- spacing scale;
- heading scale;
- radius;
- button variants;
- focus style;
- safe-area.

## Accessibility foundation

- skip-link;
- visible focus;
- `aria-current`;
- touch target ≥44px;
- semantic landmarks;
- `prefers-reduced-motion`;
- без horizontal overflow на 320px.

## Критерии приёмки

- все public pages автоматически получают shell;
- header/footer не дублируются на page level;
- mobile menu проходит keyboard/focus сценарии;
- обычная navigation работает без client-side router JS;
- UI устойчив на 320px;
- lint/test/build зелёные.

---

# PUB-02D: Typed RU content, UI labels и content ownership

**Статус:** ✅ Завершено  
**Приоритет:** P1  
**Зависит от:** PUB-02A

## Результат

Marketing content отделён от reusable UI labels и имеет простой typed contract.

## Структура

```text
apps/web/src/lib/content/public/
├── types.ts
└── ru/
    ├── home.ts
    ├── how.ts
    ├── tasks.ts
    ├── rewards.ts
    ├── parents.ts
    └── faq.ts
```

`apps/web/src/lib/i18n/messages/ru/public.ts` хранит только reusable UI labels:

```text
navigation.*
cta.*
common.*
share.*
```

Полные EN marketing-тексты не создавать, но для каждого label-ключа объявить парный ключ в `apps/web/src/lib/i18n/messages/en/public.ts` (значение может временно повторять RU): `MessageKey` в `apps/web/src/lib/i18n/index.ts` типизируется от английского каталога, и `svelte-check` не пропустит RU-only ключ в `t(...)`.

## Typed contract

Использовать только реально повторяющиеся структуры, например:

```ts
type PublicCard = {
  title: string;
  description: string;
};
```

Page content валидировать через `satisfies`/эквивалент.

Не создавать CMS-schema/PageSchema engine.

## Content ownership

При изменении:

```text
permissions             → /parents, /how, FAQ
task mechanics/catalog  → /tasks, /how, home
reward mechanics        → /rewards, /how, home
coins/approval flow     → /how, /parents, FAQ
Telegram requirements   → /parents, FAQ, CTA microcopy
privacy/security        → /parents, FAQ
```

проверять соответствующий public content.

## Conversion copy contract

Primary CTA:

`Открыть EarnIt Kids в Telegram`

Допустима короткая microcopy, например:

`Откроется в Telegram`

Не использовать неподтверждённые обещания вроде `Без регистрации`, `30 секунд`, `Бесплатно`.

## Критерии приёмки

- длинный marketing copy не лежит в одном i18n catalog;
- UI labels отделены от page content;
- typed modules не зависят от Mini App UI/runtime;
- content ownership checklist зафиксирован;
- future EN можно добавить без переписывания page components;
- lint/test/build зелёные.

---

# PUB-03: Главная страница `/`

**Статус:** ⬜ Не начато  
**Приоритет:** P1  
**Зависит от:** PUB-02A, PUB-02B, PUB-02C, PUB-02D

## Результат

Новая RU landing page соответствует смыслу и визуальному языку `index.html` reference и ведёт пользователя по одному понятному conversion path.

Обязательные блоки:

- hero;
- пример «дня Ани»;
- «Что меняется дома»;
- «Как работает» в коротком 4-5 step flow;
- «Быстрые вещи - прямо в Telegram»;
- callout «Проще попробовать на одном деле»;
- короткий trust/parent-control блок;
- primary CTA.

## Product UX contract

За первые 5-10 секунд новый посетитель должен понять:

1. что такое EarnIt Kids;
2. что продукт предназначен для семьи/родителя и ребёнка;
3. как работает базовая механика `задание → подтверждение → монеты → награда`;
4. что основной следующий шаг - открыть Mini App в Telegram.

Primary CTA:

`Открыть EarnIt Kids в Telegram`

Secondary CTA допустим только как переход к объяснению механики:

`Посмотреть, как это работает`

## Архитектура

- Страница живёт внутри `(public)` route group.
- Marketing content брать из `content/public/ru/home.ts`.
- UI labels брать из `messages/ru/public.ts`.
- Сохранить существующую auth redirect logic root route, но перенести/переиспользовать её на уровне, совместимом с `(public)/+page.svelte`.
- Не оставлять старый и новый `+page` одновременно для URL `/`.

## SEO

Добавить базовые:

- title;
- description;
- canonical.

Полная SEO-проверка выполняется в PUB-07.

## Критерии приёмки

- `/` показывает новый RU landing.
- Auth redirect regression отсутствует.
- Пользователь без прокрутки понимает продукт и видит primary CTA.
- Базовый flow `задание → подтверждение → монеты → награда` считывается без перехода на другую страницу.
- CTA использует config + URL resolver.
- На одной viewport-area нет конкурирующих primary CTA.
- 320px без horizontal scroll.
- Visual structure соответствует reference, но UX improvements имеют приоритет над pixel-perfect копированием.

---

# PUB-04: `/how` и `/parents` — Epic

Epic не реализуется одним PR.

## Implementation tasks

- `PUB-04A` — `/how`: flow + screenshots + page-end CTA.
- `PUB-04B` — `/parents`: trust + Parent vs Child + page-end CTA.

**Статус:** ⬜ Не начато  
**Приоритет:** P1  
**Зависит от:** PUB-02A, PUB-02B, PUB-02C, PUB-02D

## Результат

Добавлены:

- `/how`
- `/parents`

на основе reference.

## Screenshot UI

В первой версии **не делать полноценную JS carousel**.

Для 2-5 screenshots использовать:

- desktop: grid/row, если помещается;
- mobile: horizontal scroll + CSS scroll snap;
- touch swipe;
- минимальный JS или без JS;
- визуально показывать, что справа есть следующий screenshot;
- каждый screenshot сопровождается коротким caption: что пользователь сейчас делает и зачем;
- критическая информация не должна существовать только внутри изображения.

Полноценную carousel добавлять только отдельной задачей, если после запуска появится доказанная UX-потребность.

## Product UX

`/how` должна объяснять полный flow:

```text
родитель создаёт/выбирает задание
→ ребёнок отмечает выполнение
→ родитель подтверждает
→ ребёнок получает монеты
→ выбирает награду
```

`/parents` должна явно отвечать минимум на вопросы:

- сколько времени занимает использование;
- кто управляет заданиями и наградами;
- нужен ли ребёнку отдельный Telegram/account/device;
- как родитель контролирует подтверждение;
- что ребёнок не может сам себе начислять монеты;
- какие действия доступны ребёнку, а какие только родителю;
- какие данные реально нужны продукту, без маркетинговых обещаний сверх реализации.

### Parent vs Child visual block

Добавить компактный сравнительный блок, который за несколько секунд объясняет роли:

```text
Родитель                         Ребёнок
────────────────────────────────────────────────
Создаёт/выбирает задания         Видит задания
Настраивает награды              Видит награды
Подтверждает выполнение          Нажимает «Выполнил»
Контролирует начисление          Получает монеты после подтверждения
Управляет правилами              Не может сам начислять себе монеты
```

Текст должен соответствовать реальному текущему поведению продукта. Не добавлять действия, которых ещё нет.

## Images

Обязательно:

- `width`;
- `height`;
- `loading="lazy"` вне первого viewport;
- `decoding="async"`;
- responsive `srcset/sizes`, если подготовлены варианты;
- WebP/AVIF при целесообразности.

### Alt-text contract

- meaningful image/screenshot получает короткий осмысленный `alt`;
- decorative image получает `alt=""`;
- если screenshot уже имеет caption, `alt` не должен дословно дублировать caption;
- alt описывает смысл интерфейса/действия, а не пиксели и декоративные детали;
- критическая информация не должна существовать только в `alt` или только внутри изображения.

## Критерии приёмки

- `PUB-04A` и `PUB-04B` могут быть реализованы/проверены независимо.
- Обе страницы работают на mobile/desktop.
- `/how` позволяет понять весь основной flow без догадок.
- `/parents` закрывает основные вопросы доверия и контроля родителя.
- Screenshot gallery понятна без стрелок/автопрокрутки.
- Нет layout shift из-за изображений.
- Keyboard/a11y requirements соблюдены.

---

# PUB-05: `/tasks` и `/rewards` — Epic

Epic не реализуется одним PR.

## Implementation tasks

- `PUB-05A` — `/tasks`: curated task examples + categories + CTA.
- `PUB-05B` — `/rewards`: curated reward examples + categories + CTA.

**Статус:** ⬜ Не начато  
**Приоритет:** P1  
**Зависит от:** PUB-02A, PUB-02B, PUB-02C, PUB-02D

## Результат

Добавлены публичные страницы:

- `/tasks`
- `/rewards`

## Требования

- Не конфликтуют с `/app/tasks` и `/app/shop`.
- Автоматически используют `(public)/+layout.svelte`.
- Marketing content:
  - `content/public/ru/tasks.ts`
  - `content/public/ru/rewards.ts`
- На каждой странице показать реальные примеры без регистрации.

### Источник примеров

Не поддерживать вручную две независимые версии одних и тех же catalog examples.

Предпочтительный порядок:

1. выделить лёгкий shared data contract без UI/runtime зависимостей;
2. public site использует curated subset этого контракта;
3. Mini App может использовать тот же data source или адаптер поверх него;
4. если общий source сейчас невозможен, явно пометить marketing examples как curated snapshot с отдельным тестом/процессом синхронизации.

Минимальный shared contract может выглядеть так:

```ts
export type CatalogExample = {
  id: string;
  title: string;
  description: string;
  category: string;
  ageRange?: string;
  coins?: number;
};
```

Не импортировать в public site:

- authenticated stores;
- API clients;
- Mini App view models;
- UI-specific models;
- Telegram runtime.

DRY на уровне UI-model здесь хуже, чем небольшой чистый domain/data contract.
- Для каждого примера должны быть видимы основные атрибуты, важные пользователю: название, возраст/контекст, монеты или смысл награды, короткое описание.
- CTA ведут в Mini App только через configuration.
- Mobile cards не выходят за viewport.

## Критерии приёмки

- `PUB-05A` и `PUB-05B` могут быть реализованы/проверены независимо.
- Публичные и authenticated routes независимы.
- Пользователь видит реальные примеры заданий/наград до открытия Mini App.
- Примеры помогают понять ценность продукта, а не выглядят как абстрактный feature list.
- 320px+ работает без horizontal overflow.
- Нет дублирования page shell.

---

## Page-end conversion contract

Каждая informational page должна логично завершать пользовательский путь primary CTA, а не заставлять пользователя возвращаться к hero/header.

Минимум:

```text
/how      → понял механику → Открыть EarnIt Kids в Telegram
/tasks    → увидел примеры → Открыть EarnIt Kids в Telegram
/rewards  → увидел награды → Открыть EarnIt Kids в Telegram
/parents  → снял сомнения → Открыть EarnIt Kids в Telegram
/faq      → получил ответы → Открыть EarnIt Kids в Telegram
```

CTA остаётся семантически тем же, меняется только окружающий текст секции.

---

# PUB-06: `/faq`

**Статус:** ⬜ Не начато  
**Приоритет:** P1  
**Зависит от:** PUB-02A, PUB-02B, PUB-02C, PUB-02D

## Результат

Новая FAQ-страница на основе reference.

## Реализация

Использовать native:

```html
<details>
  <summary>...</summary>
</details>
```

Преимущества:

- работает без JS;
- keyboard-friendly;
- меньше accessibility edge cases.

### Группировка FAQ

Если вопросов немного (ориентир до 8), оставить один простой список.

Если FAQ разрастается, группировать по пользовательским сомнениям, например:

- Начало работы
- Для родителей
- Для ребёнка
- Монеты и награды
- Контроль и данные

Не создавать category navigation заранее при маленьком количестве вопросов.

## Критерии приёмки

- Все FAQ доступны с клавиатуры.
- Marketing content находится в `content/public/ru/faq.ts`.
- Layout соответствует общему public shell.

---

# PUB-07: SEO / Discovery — Epic

Epic не реализуется одним PR.

---

# PUB-07A: Metadata, canonical и Open Graph

**Статус:** ⬜ Не начато  
**Приоритет:** P1  
**Зависит от:** PUB-03, PUB-04A, PUB-04B, PUB-05A, PUB-05B, PUB-06

## Результат

Каждая public page имеет корректную metadata без фиктивной локализации.

## Работа

Для каждой страницы:

- unique title;
- unique description;
- canonical;
- `og:title`;
- `og:description`;
- `og:url`;
- `og:image`.

Canonical первого этапа:

```text
/
/how
/tasks
/rewards
/parents
/faq
```

Не создавать `/ru` canonical и EN hreflang до появления реальных EN pages.

Подготовить production-ready social preview asset, ориентир 1200×630.

## Критерии приёмки

- нет duplicate canonical;
- OG URL использует Public URL resolver;
- Telegram preview главной ссылки осмысленный;
- title/description не дублируются между страницами.

---

# PUB-07B: Sitemap, indexing policy и public 404

**Статус:** ⬜ Не начато  
**Приоритет:** P1  
**Зависит от:** PUB-07A

## Результат

Production индексируется корректно, staging/preview не конкурирует с production, 404 помогает восстановить путь.

## Production

```text
index, follow
sitemap содержит только реальные canonical URL
```

## Staging / preview / dev

```text
noindex, nofollow
не попадать в production sitemap
```

## 404

Минимальный UX:

```text
Страница не найдена

[На главную]

Как работает
Задания
Награды
```

Не превращать 404 в отдельный landing page.

## Файлы

- Изменить `apps/web/static/sitemap.xml` (уже существует): только реальные canonical production URL.
- Изменить `apps/web/static/robots.txt`: политика index/nofollow по environment.
- Изменить `apps/web/src/routes/+error.svelte` (сейчас рендерит ошибку через `$i18n.t`) и/или `apps/web/src/routes/[...path]/+page.server.ts` (сейчас бросает `error(404)`): публичный 404 без legacy/EN copy.

## Критерии приёмки

- sitemap содержит только реальные production URL;
- staging/preview отдаёт `noindex, nofollow`;
- 404 не показывает legacy/English placeholder copy;
- future hreflang/localized sitemap можно добавить отдельно.

---

# PUB-08: Sharing публичного сайта из Mini App

Размер задачи приемлем: один user action + fallback + tests. Не дробить дальше без найденной сложности.


**Статус:** ⬜ Не начато  
**Приоритет:** P1  
**Зависит от:** PUB-02A, PUB-02B

## Результат

Родитель может поделиться публичным сайтом из Mini App.

## Реализация

Создать:

`publicSiteShare.ts`

Использовать:

```ts
getPublicSiteUrl('/')
```

а не:

```ts
new URL('/ru', window.location.origin)
```

### Telegram

Использовать:

`Telegram.WebApp.openTelegramLink(...)`

с `t.me/share/url`.

### Desktop/browser CTA contract

Primary CTA не должен зависеть от наличия Telegram Desktop. Использовать обычную `t.me`/Telegram Web совместимую ссылку и проверить реальные сценарии:

- desktop с Telegram Desktop;
- desktop без Telegram Desktop;
- mobile browser;
- Telegram in-app browser.

Не добавлять сложный JS detection установленного Telegram-клиента без доказанной необходимости.

### Browser fallback

1. Сначала Web Share API, если доступен и UX подходит.
2. Затем clipboard fallback.
3. Показать success/error state.

## Критерии приёмки

- URL правильный при одинаковом и разном public/app origin.
- Нет hardcoded locale.
- Clipboard failure обработан.
- Touch target ≥44px.
- Unit tests покрывают URL builder и fallback logic.

---

# PUB-09: Permanent redirects legacy URL

Размер задачи приемлем: одна migration concern - legacy redirects. Не смешивать сюда archive/cutover.


**Статус:** ✅ Выполнено  
**Приоритет:** P1  
**Зависит от:** PUB-03, PUB-04A, PUB-04B, PUB-05A, PUB-05B, PUB-06, PUB-07B

## Результат

Старые публичные URL корректно ведут на новые страницы без временного периода 404.

## Redirect map

```text
/about          → /parents
/about.html     → /parents
/features/tasks → /tasks
/features/shop  → /rewards
/features       → /tasks
/faq.html       → /faq
/index.html     → /
```

### Blog

Не делать автоматически:

```text
/blog → /
```

Сначала определить судьбу blog content:

- если есть близкая replacement page — redirect туда;
- если контент окончательно удалён и replacement нет — рассмотреть 410;
- redirect каждого indexed article на `/` без смысловой связи не использовать.

## HTTP status

Использовать:

```text
308 Permanent Redirect
```

или 301 при технической необходимости.

## Требования

- query string сохраняется, если это имеет смысл;
- нет redirect chains;
- нет loops;
- auth aliases не ломаются.

## Критерии приёмки

- Ни один ранее поддерживаемый важный URL не получает случайный 404 во время migration.
- Все permanent redirects покрыты тестами.
- Blog migration decision задокументирован отдельно.

## Blog migration decision

Блог не перенаправляется на `/` автоматически. Текущие статьи `/blog/*` сохраняются как есть (контент остаётся в `src/routes/blog`), потому что нет смысловой replacement-страницы. Окончательное решение по судьбе blog (архив, перенос, 410) выносится в отдельную задачу и не входит в PUB-09.

## Redirect map (реализовано)

```text
/about          → /parents   (308)
/about.html     → /parents   (308)
/features       → /tasks     (308)
/features/tasks → /tasks     (308)
/features/shop  → /rewards   (308)
/faq.html       → /faq       (308)
/index.html     → /          (308)
```

Все redirects реализованы через `resolvePublicRedirect()` в `hooks.server.ts` и покрыты юнит-тестами (`tests/unit/publicRedirect.test.ts`). HTTP-статус — `308 Permanent Redirect`. Query string сохраняется.

---

# PUB-10: Cutover и archive legacy

Размер задачи приемлем как release/cutover task, потому что implementation уже завершена до неё. Здесь только переключение, smoke и cleanup.


**Статус:** ⬜ Не начато  
**Приоритет:** P1  
**Зависит от:** PUB-11A, PUB-11B, PUB-11C

## Результат

После успешного pre-release quality gate новый сайт переключается в production, после чего legacy routes удалены из runtime и сохранены в repository archive.

## Работа

1. Выполнить smoke test нового public site.
2. Проверить redirects.
3. Проверить auth redirects.
4. Выполнить content QA:
   - одинаковые термины для одних и тех же сущностей;
   - единый текст primary CTA;
   - нет placeholder/legacy copy;
   - нет случайных EN строк;
   - тексты соответствуют реальному поведению продукта;
   - нет повторяющихся секций, которые не добавляют новой информации.
   - изменения permissions/catalog/approval flow сверены с public content ownership checklist.
5. Через `git mv` перенести legacy pages в:
   - `apps/web/legacy/public-site/`.
6. Удалить мёртвые route/i18n resolution branches.
7. Не удалять исторические файлы без отдельной cleanup задачи.
8. Выполнить короткий manual smoke test на реальных клиентах:
   - iPhone Safari;
   - Android Chrome;
   - Telegram in-app browser;
   - desktop Chrome/Firefox.
9. Проверить критический путь: `/` → понять продукт → открыть меню → `/how` → primary CTA → Telegram.

## Критерии приёмки

- Новый public site работает.
- Legacy runtime routes отсутствуют.
- Legacy source сохранён.
- Нет промежуточных production 404 из-за migration ordering.
- lint/test/build/E2E зелёные.
- Критический conversion path вручную проверен минимум в Telegram in-app browser и одном мобильном браузере.
- Content QA пройден: нет placeholder/legacy/случайного EN текста, CTA и термины согласованы.

---

# PUB-11: Pre-release Quality Gate — Epic

PUB-10 Cutover блокируется, пока все три implementation tasks ниже не зелёные.

---

# PUB-11A: Functional E2E + progressive enhancement

**Статус:** ✅ Выполнено  
**Приоритет:** P1  
**Зависит от:** PUB-03..PUB-09

## Проверить

- `/`;
- `/how`;
- `/tasks`;
- `/rewards`;
- `/parents`;
- `/faq`;
- navigation;
- mobile menu;
- CTA;
- sharing;
- legacy redirects;
- root auth redirects.

Минимальный no-JS/progressive-enhancement smoke:

```text
/ открывается
navigation работает
/how и /tasks открываются
FAQ раскрывается
Telegram link доступна
```

## Критерии приёмки

- functional E2E зелёный;
- hydration failure не ломает базовый public flow;
- legacy/auth redirect regression отсутствует.

---

# PUB-11B: Responsive + accessibility + content stress

**Статус:** ⬜ Не начато  
**Приоритет:** P1  
**Зависит от:** PUB-11A

## Viewports

```text
320
390
768
1440
```

## Accessibility

- keyboard;
- focus;
- skip-link;
- landmarks;
- `aria-current`;
- details/summary;
- touch target ≥44px;
- reduced motion;
- mobile menu focus/open/close/Escape;
- один `h1` на страницу;
- semantic heading hierarchy;
- meaningful/decorative alt contract.

## Content stress

Проверить:

```text
navigation label +50%
CTA +50%
card title → 2 строки
card description → 4-5 строк
FAQ summary → 2 строки
section heading → 2 строки
```

## Критерии приёмки

- нет horizontal overflow;
- layout не зависит от фиксированной длины RU copy;
- keyboard/focus сценарии работают;
- images/alt contract корректен.

---

# PUB-11C: Visual regression + performance

**Статус:** ⬜ Не начато  
**Приоритет:** P1  
**Зависит от:** PUB-11A

## Visual regression

Playwright screenshots минимум:

- mobile;
- desktop.

Baseline - утверждённая текущая SvelteKit implementation, а не вечное pixel-perfect сравнение с исходным reference.

## Performance targets

Ориентиры:

- LCP ≤ 2.5 s;
- CLS ≤ 0.1;
- INP ≤ 200 ms;
- initial images ≈ ≤ 500 KB;
- screenshot желательно ≤ 200 KB при достаточном качестве.

Жёсткие требования:

- не грузить heavy screenshots eagerly вне первого viewport;
- public bundle не тянет authenticated stores/charts/Mini App UI;
- нет multi-MB initial payload без явной причины;
- нет JS carousel, если CSS scroll-snap решает задачу.

## Критерии приёмки

- visual snapshots утверждены;
- нет очевидной bundle boundary regression;
- screenshots не создают неоправданный initial payload;
- performance deviation выше target задокументирован, если осознанно принят.

---

# PUB-12: Минимальная Product Analytics public site

Размер задачи приемлем: минимальный analytics contract из трёх событий. Не дробить по event.


**Статус:** ⬜ Не начато  
**Приоритет:** P2  
**Зависит от:** PUB-02A, PUB-02B

## Результат

Analytics является **неблокирующей для PUB-10 cutover**. Если существующая analytics infrastructure не готова, public site выпускается без задержки, а события добавляются позже.

Можно ответить на три практических вопроса:

1. какие public pages реально посещают;
2. откуда пользователи открывают Mini App;
3. пользуются ли sharing.

## Event contract

На первом этапе достаточно:

```text
public_page_view
public_cta_click
public_share
```

## Event properties

```text
page
placement
```

Примеры `placement`:

```text
hero
header
mobile_menu
footer
share_control
```

Не добавлять `public_nav_click`, отдельные success/error/open события без конкретного аналитического вопроса, на который они должны отвечать.

## Файлы

- Переиспользовать существующий `apps/web/src/lib/observability/newrelic.ts` (`setBrowserPageViewName`, уже вызывается в root `+layout.svelte` в `afterNavigate`), а не вводить вторую analytics-инфраструктуру. Если браузерный New Relic выключен (`VITE_NEW_RELIC_BROWSER_ENABLED=false`), события не отправляются.

## Privacy

Не отправлять:

- имя ребёнка;
- user-entered task/reward data;
- Telegram identifiers;
- PII.

## Критерии приёмки

- Можно сравнить CTA conversion по page/placement.
- Можно увидеть использование sharing.
- Analytics failures не ломают UI.
- Event schema можно расширить позже без изменения UI components.

---

# PUB-13: Sharing из Telegram-бота

**Статус:** ⬜ Не начато  
**Приоритет:** P3  
**Зависит от:** PUB-02A

## Результат

В Telegram-боте родитель может получить/открыть публичную ссылку.

## Архитектура

Backend использует тот же конфигурационный public origin contract.

Не дублировать invite services.

## Файлы

- Изменить `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramCopy.java`: текст пункта меню.
- Изменить `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramMenuBuilder.java`: добавить `url`-кнопку/пункт в главное меню родителя.
- Изменить `apps/backend/src/main/java/com/sashplatonov/earnit/kids/service/telegram/TelegramMenuFlow.java` и `TelegramBotServiceImpl.java`: прокинуть public origin (источник `APP_URL`, уже используется в `util/PublicOriginResolver.java`).
- Добавить тесты меню в `apps/backend/src/test/java/com/sashplatonov/earnit/kids/service/telegram/`.

## Критерии приёмки

- URL не hardcoded.
- Пустой config не создаёт битую кнопку.
- Backend tests/PMD/Checkstyle/SpotBugs зелёные.

---

# PUB-14: Опциональная чистка favicon приложения

**Статус:** ⬜ Не начато  
**Приоритет:** P3  
**Зависит от:** -

## Результат

Удалены только действительно неиспользуемые favicon/static icons.

## Проверить

- `apps/web/src/app.html` (сейчас ссылается на `/img/favicon.ico`, `/img/favicon-32x32.png`, `/img/icon-192.png`);
- `apps/web/static/manifest.json`;
- `apps/web/static/sw.js` (service worker);
- `apps/web/static/robots.txt`;
- компоненты и `apps/web/static/` (текущий набор в `apps/web/static/img/`: `favicon.ico`, `favicon.svg`, `favicon-16x16.png`, `favicon-32x32.png`, `favicon.png`, `apple-touch-icon.png`, `apple-touch-icon-180.png`, `icon-192.png`, `icon-512.png`).

Не удалять:

- PWA 192/512 icons, если используются manifest;
- apple touch icons, если используются.

## Критерии приёмки

- Нет orphan static favicon.
- PWA не сломан.
- Build зелёный.

---

# Definition of Done для каждой public page

Каждая новая публичная страница считается готовой только если:

- автоматически использует `(public)/+layout.svelte`;
- marketing copy находится в `content/public/ru/*`, reusable UI labels - в `messages/ru/public.ts`;
- page content соответствует typed contract и проверяется через `satisfies`/эквивалент;
- URL не собираются вручную;
- primary CTA соответствует общему conversion contract;
- страница отвечает на свой главный пользовательский вопрос, а не только повторяет feature list;
- responsive от 320px;
- нет horizontal overflow;
- semantic heading hierarchy соблюдена;
- touch targets ≥44px;
- keyboard navigation работает;
- navigation и link-like CTA используют semantic `<a href>`, а не JS navigation button;
- visible focus присутствует;
- изображения имеют фиксированные dimensions и корректный alt/decorative contract;
- metadata заполнена;
- CTA не превращается в disabled dead-end;
- CTA availability известна на SSR и не зависит от hydration для первого render;
- informational pages имеют логичный page-end CTA;
- page не тянет authenticated Mini App modules без необходимости;
- unit/E2E test добавлен там, где есть новое поведение;
- базовый public flow не зависит от успешной hydration для navigation/content/FAQ.

---

# Отдельные архитектурные ограничения

## Не делать сейчас

- не создавать английские тексты только ради parity;
- не добавлять language switcher;
- не создавать `/ru` route tree;
- не создавать `/en` placeholder pages;
- не добавлять backend CMS;
- не добавлять API для sharing;
- не оборачивать каждую страницу вручную в public layout, использовать route group `+layout.svelte`;
- не hardcode Telegram URL;
- не hardcode public origin;
- не делать temporary production 404;
- не делать `/blog → /` без анализа blog content;
- не добавлять JS carousel в первой версии, использовать CSS scroll snap/grid;
- не складывать весь marketing copy в один i18n catalog;
- не добавлять analytics event без конкретного продуктового вопроса;
- не добавлять sticky CTA в MVP;
- не добавлять dropdown только ради объединения «Задания» и «Награды»;
- не индексировать staging/preview environments;
- не поддерживать отдельную вручную синхронизируемую копию catalog examples без явного процесса;
- не импортировать Mini App UI/view-model/runtime слой в public site ради каталога;
- не использовать screenshot без caption, если без него смысл шага неочевиден;
- не делать сложный client detection Telegram Desktop/Web;
- не использовать `button + goto()` для обычной навигации;
- не делать базовый просмотр public content зависимым от client JS/hydration;
- не создавать универсальные layout/content abstraction без найденной реальной проблемы.

## Должно быть легко добавить позже

- EN/другие locale;
- locale switcher;
- `/ru` + `/en` routing;
- hreflang;
- localized sitemap;
- отдельный marketing domain;
- другой Telegram Mini App URL по environment;
- дополнительные analytics dimensions.

---


# Рекомендации по размеру backlog после декомпозиции

## Оставить как отдельные implementation tasks

- PUB-01
- PUB-02A
- PUB-02B
- PUB-02C
- PUB-02D
- PUB-03
- PUB-04A
- PUB-04B
- PUB-05A
- PUB-05B
- PUB-06
- PUB-07A
- PUB-07B
- PUB-08
- PUB-09
- PUB-10
- PUB-11A
- PUB-11B
- PUB-11C
- PUB-12
- PUB-13
- PUB-14

## Не реализовывать одним PR

- PUB-04 Epic
- PUB-05 Epic
- PUB-07 Epic
- PUB-11 Epic

## Не дробить дальше заранее

Следующие задачи уже достаточно узкие:

- PUB-03 - одна страница;
- PUB-06 - одна FAQ page;
- PUB-08 - один sharing flow;
- PUB-09 - одна redirect policy;
- PUB-10 - один release cutover;
- PUB-12 - один минимальный analytics contract;
- PUB-13 - один bot flow.

Дальнейшее дробление допустимо только если во время реализации обнаружится независимая сложность.

---

# Architecture stop-rule

После v6 новая abstraction/task добавляется только если она закрывает найденную проблему реализации, UX или поддержки.

Не добавлять заранее:

- `PublicContentRepository`;
- универсальный `PageSchema engine`;
- CMS abstraction;
- locale provider framework;
- CTA orchestration service;
- generic component factory.

Для текущих шести public pages предпочтительны явные typed modules, semantic HTML и простые Svelte components.

---

# UX/UI принципы первой версии

1. Один главный conversion path: открыть Mini App в Telegram.
2. Navigation отражает пользовательские вопросы, а не 1:1 route tree.
3. Главная объясняет продукт и базовую механику за первые 5-10 секунд.
4. `/how` показывает последовательный end-to-end flow.
5. `/tasks` и `/rewards` показывают реальные примеры до регистрации.
6. В MVP нет sticky CTA; её добавление требует отдельного UX/analytics обоснования.
7. Screenshots используют grid/scroll-snap, без carousel JS.
8. Reference - источник визуального направления, но UX improvements важнее pixel-perfect копирования.
9. Любой новый UI primitive создаётся только при повторном использовании.
10. Любая новая аналитика должна отвечать на заранее сформулированный продуктовый вопрос.

---

# Финальная схема архитектуры

```text
                     AppConfig
                        │
                 publicOrigin
                        │
              PublicSiteUrlResolver
                 ┌──────┼───────────┐
                 │      │           │
             Public   Sharing    Metadata
               CTA    Mini App    / Sitemap
                 │
        ┌────────┴─────────────┐
        │ (public)/+layout.svelte │
        ├────────────────────────┤
        │ Header           │
        │ Main             │
        │ Footer           │
        └────────┬─────────┘
                 │
    ┌────────────┼─────────────────────────┐
    │            │          │              │
   /           /how      /tasks/...       /faq

Content today:
content/public/types.ts   ← small typed contracts
content/public/ru/*       ← marketing copy
messages/ru/public.ts     ← reusable UI labels

Future:
content/public/en/*
messages/en/public.ts
content/public/<locale>/*
messages/<locale>/public.ts
```

---

# Product-release principles

1. Главный conversion path: понять ценность → понять механику → открыть Telegram.
2. Parent trust/control объясняется до CTA, а не спрятан только в FAQ.
3. Telegram/OG preview считается частью первого пользовательского впечатления.
4. Public examples синхронизируются с реальным catalog data без зависимости от authenticated runtime.
5. Pre-release quality gate проходит до cutover.
6. Public content имеет ownership contract и проверяется при изменении продуктовой механики.
7. CTA объясняет, что произойдёт после клика, без неподтверждённых обещаний.

---

# Главный принцип миграции

**Сначала построить и проверить новый публичный сайт, затем переключить URL и только после этого архивировать legacy.**

На первом этапе пользователь видит только русский сайт, но компоненты, URL resolver, content namespaces и metadata architecture не должны требовать переписывания при появлении второго языка.
