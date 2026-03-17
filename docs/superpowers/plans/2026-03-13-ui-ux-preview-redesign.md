# UI/UX Preview Redesign Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Привести UI приложения EarnIt Kids к виду, описанному в `ux-preview.html` — единая warm-цветовая гамма, pill-кнопки везде, emoji-иконки в навигации, правильный dropdown "Ещё", адаптивный публичный nav и обновлённый суперадмин.

**Architecture:** CSS-first подход — большинство изменений в `public/css/partials/components.css`, `layout.css`, `public-top-nav.css`, `super-admin.css`. HTML-шаблоны правятся точечно (`nav.html`, `super-admin.html`). JS правок минимально — только иконки и dropdown-логика.

**Tech Stack:** Vanilla HTML/CSS/JS, Node.js/Express, PostgreSQL. Без фреймворков. CSS custom properties (OKLCH токены). Шаблоны в `views/components/`.

**Security note:** При использовании `innerHTML` в JS — применять только с доверенными данными с сервера (числа, даты, имена файлов). Никаких user-controlled строк в HTML без sanitization.

**Reference:** `ux-preview.html` — эталонный mockup со сравнением "до/после" по 7 секциям.

---

## Chunk 1: Кнопки — pill shape и единый warm цвет

### Task 1: Все кнопки → border-radius: 999px

**Files:**
- Modify: `public/css/partials/components.css` (`.btn` base, `.btn--add`, `.btn-add`)

- [ ] **Step 1: Найти текущие значения**

```bash
grep -n "border-radius" public/css/partials/components.css | grep -v "999px" | head -30
```

Ожидаемый вывод: строки с `var(--radius-md)`, `0.5rem`, `8px`, `12px` в контексте кнопок.

- [ ] **Step 2: Изменить `.btn` base — добавить pill radius**

В `public/css/partials/components.css` найти блок `.btn {` и изменить `border-radius`:

```css
.btn {
  /* было: border-radius: var(--radius-md); */
  border-radius: 999px;
  /* остальные свойства без изменений */
}
```

- [ ] **Step 3: Исправить `.btn--add`**

Найти `.btn--add` и убрать его собственный `border-radius` (наследуется от base):

```css
.btn--add {
  /* удалить строку: border-radius: 0.5rem; */
  background: var(--gradient-warm-cta);
  color: white;
}
```

- [ ] **Step 4: Исправить `.btn-add` (альтернативный класс)**

```css
.btn-add {
  /* было: border-radius: 0.5rem; */
  border-radius: 999px;
}
```

- [ ] **Step 5: Проверить визуально**

Открыть приложение, проверить что кнопки "Выполнить", "Добавить", "Сохранить", "Отмена" — все скруглённые pill.

- [ ] **Step 6: Commit**

```bash
git add public/css/partials/components.css
git commit -m "style: all buttons pill shape (border-radius: 999px)"
```

---

### Task 2: Единый warm orange CTA — убрать синие/зелёные CTA-кнопки

**Files:**
- Modify: `public/css/partials/components.css` (`.btn--add`, section overrides)

- [ ] **Step 1: Найти все нестандартные градиенты в кнопках**

```bash
grep -n "gradient" public/css/partials/components.css | grep "btn" | grep -v "warm-cta\|gold\|--primary"
```

- [ ] **Step 2: Унифицировать `.btn--add`**

```css
.btn--add {
  background: var(--gradient-warm-cta);
  color: white;
  box-shadow: 0 6px 16px rgba(255, 160, 133, 0.24);
}
.btn--add:hover {
  box-shadow: 0 10px 24px rgba(255, 160, 133, 0.32);
  transform: translateY(-1px);
}
```

- [ ] **Step 3: Исправить override в #shop-section**

Найти `#shop-section .btn--add` и удалить синий градиент (блок с `#5cc7f3 / #8ddfb7`). После удаления блока — наследует warm-cta от base.

- [ ] **Step 4: `.btn--success` — оставить зелёным**

`.btn--success` — сохранить зелёным (одобрить заявку, подтвердить выполнение). Это семантически верно, не менять.

- [ ] **Step 5: Commit**

```bash
git add public/css/partials/components.css
git commit -m "style: unify CTA buttons to warm orange gradient"
```

---

## Chunk 2: Навигация — emoji-иконки и dropdown "Ещё"

### Task 3: Навигационные иконки → emoji

**Files:**
- Modify: `views/components/nav.html`
- Modify: `public/css/partials/components.css` (`.nav__icon`, `.nav__label`)

- [ ] **Step 1: Прочитать текущую структуру nav.html**

```bash
cat views/components/nav.html
```

Найти `.nav__btn` элементы с иконками (сейчас используются SVG-классы `.gamified-icon`).

- [ ] **Step 2: Заменить иконки в child-навигации**

Найти `nav__group--child` и заменить иконки на emoji (сохранив все `data-tab`, классы видимости):

```html
<button class="nav__btn active" data-tab="today">
  <span class="nav__icon">📋</span>
  <span class="nav__label">Сегодня</span>
</button>
<button class="nav__btn" data-tab="tasks">
  <span class="nav__icon">✅</span>
  <span class="nav__label">Задания</span>
</button>
<button class="nav__btn" data-tab="shop">
  <span class="nav__icon">🛍️</span>
  <span class="nav__label">Награды</span>
</button>
<button class="nav__btn" data-tab="progress">
  <span class="nav__icon">🏆</span>
  <span class="nav__label">Прогресс</span>
</button>
<button class="nav__btn child-only" data-tab="requests">
  <span class="nav__icon">📨</span>
  <span class="nav__label">Заявки</span>
</button>
```

- [ ] **Step 3: Заменить иконки в parent-навигации**

Найти `nav__group--parent`:

```html
<button class="nav__btn" data-tab="today">
  <span class="nav__icon">📊</span>
  <span class="nav__label">Обзор</span>
</button>
<button class="nav__btn requires-child" data-tab="tasks">
  <span class="nav__icon">✅</span>
  <span class="nav__label">Задания</span>
</button>
<button class="nav__btn requires-child" data-tab="requests">
  <span class="nav__icon">📨</span>
  <span class="nav__label">Заявки</span>
</button>
<button class="nav__btn requires-child" data-tab="shop">
  <span class="nav__icon">🛍️</span>
  <span class="nav__label">Награды</span>
</button>
```

- [ ] **Step 4: Иконка кнопки "Ещё"**

```html
<button class="nav__more" aria-haspopup="menu" aria-label="Ещё">
  <span class="nav__icon">•••</span>
</button>
```

- [ ] **Step 5: CSS для emoji-иконок**

В `components.css` добавить/обновить:

```css
.nav__icon {
  font-size: 1.1rem;
  line-height: 1;
  display: block;
}
.nav__label {
  font-size: 0.62rem;
  font-weight: 700;
  margin-top: 0.1rem;
}
```

- [ ] **Step 6: Проверить на мобиле (< 900px)**

Сузить до 375px — все иконки видны, текст не обрезан.

- [ ] **Step 7: Commit**

```bash
git add views/components/nav.html public/css/partials/components.css
git commit -m "style: nav buttons emoji icons per ux-preview"
```

---

### Task 4: Dropdown "Ещё" — секции с заголовками

**Files:**
- Modify: `views/components/nav.html`
- Modify: `public/css/partials/components.css` (`.nav__dropdown*`)

- [ ] **Step 1: Прочитать текущий dropdown**

```bash
grep -A 50 "nav__dropdown" views/components/nav.html
```

Зафиксировать какие id/data-атрибуты используются для JS-логики переключения табов.

- [ ] **Step 2: Переписать child dropdown**

```html
<div class="nav__dropdown hidden child-only" role="menu">
  <div class="nav__dropdown-section-label">Разделы</div>
  <button class="nav__dropdown-item" data-tab="history" role="menuitem">
    <span class="nav__dropdown-icon">📜</span>
    <span>История</span>
  </button>
  <button class="nav__dropdown-item" data-tab="requests" role="menuitem">
    <span class="nav__dropdown-icon">📨</span>
    <span>Заявки</span>
    <span class="nav__dropdown-badge badge badge-blue requests-count hidden">0</span>
  </button>
  <button class="nav__dropdown-item" data-tab="friends" role="menuitem">
    <span class="nav__dropdown-icon">⭐</span>
    <span>Друзья</span>
  </button>
  <div class="nav__dropdown-divider"></div>
  <div class="nav__dropdown-section-label">Настройки</div>
  <button class="nav__dropdown-item" data-tab="analytics" role="menuitem">
    <span class="nav__dropdown-icon">📊</span>
    <span>Достижения</span>
  </button>
  <button class="nav__dropdown-item" data-tab="rules" role="menuitem">
    <span class="nav__dropdown-icon">📖</span>
    <span>Правила семьи</span>
  </button>
  <button class="nav__dropdown-item" data-tab="settings" role="menuitem">
    <span class="nav__dropdown-icon">⚙️</span>
    <span>Настройки</span>
  </button>
  <div class="nav__dropdown-divider"></div>
  <button class="nav__dropdown-item nav__dropdown-item--danger logout-trigger" role="menuitem">
    <span class="nav__dropdown-icon">🚪</span>
    <span>Выйти</span>
  </button>
</div>
```

- [ ] **Step 3: Переписать parent dropdown**

```html
<div class="nav__dropdown hidden admin-only" role="menu">
  <div class="nav__dropdown-section-label">Управление</div>
  <button class="nav__dropdown-item" data-tab="history" role="menuitem">
    <span class="nav__dropdown-icon">📜</span>
    <span>История</span>
  </button>
  <button class="nav__dropdown-item" data-tab="friends" role="menuitem">
    <span class="nav__dropdown-icon">⭐</span>
    <span>Друзья</span>
  </button>
  <button class="nav__dropdown-item requires-child" data-tab="catalog" role="menuitem">
    <span class="nav__dropdown-icon">📋</span>
    <span>Каталог</span>
  </button>
  <button class="nav__dropdown-item requires-child" data-tab="limits" role="menuitem">
    <span class="nav__dropdown-icon">🔒</span>
    <span>Лимиты</span>
  </button>
  <button class="nav__dropdown-item requires-child copy-child-link" role="menuitem">
    <span class="nav__dropdown-icon">🔗</span>
    <span>Ссылка ребёнку</span>
  </button>
  <div class="nav__dropdown-divider"></div>
  <div class="nav__dropdown-section-label">Семья</div>
  <button class="nav__dropdown-item" data-tab="analytics" role="menuitem">
    <span class="nav__dropdown-icon">📊</span>
    <span>Достижения</span>
  </button>
  <button class="nav__dropdown-item" data-tab="rules" role="menuitem">
    <span class="nav__dropdown-icon">📖</span>
    <span>Правила семьи</span>
  </button>
  <button class="nav__dropdown-item" data-tab="settings" role="menuitem">
    <span class="nav__dropdown-icon">⚙️</span>
    <span>Настройки</span>
  </button>
  <div class="nav__dropdown-divider"></div>
  <button class="nav__dropdown-item nav__dropdown-item--danger logout-trigger" role="menuitem">
    <span class="nav__dropdown-icon">🚪</span>
    <span>Выйти</span>
  </button>
</div>
```

- [ ] **Step 4: CSS dropdown секции**

```css
.nav__dropdown {
  background: white;
  border-radius: 16px;
  border: 1px solid rgba(122, 149, 191, 0.16);
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.12);
  padding: 0.5rem;
  min-width: 210px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav__dropdown-section-label {
  font-size: 0.68rem;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.09em;
  color: #94a3b8;
  padding: 0.35rem 0.75rem 0.15rem;
}

.nav__dropdown-item {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  padding: 0.55rem 0.75rem;
  border-radius: 10px;
  font-size: 0.88rem;
  font-weight: 700;
  color: var(--color-text);
  cursor: pointer;
  background: transparent;
  border: none;
  font-family: var(--font);
  width: 100%;
  text-align: left;
}

.nav__dropdown-item:hover {
  background: #f8f9fb;
}

.nav__dropdown-item--danger { color: #ef4444; }
.nav__dropdown-item--danger:hover { background: #fef2f2; }

.nav__dropdown-icon {
  font-size: 1rem;
  width: 1.4rem;
  text-align: center;
  flex-shrink: 0;
}

.nav__dropdown-badge { margin-left: auto; }

.nav__dropdown-divider {
  height: 1px;
  background: rgba(0, 0, 0, 0.06);
  margin: 0.25rem 0.5rem;
}
```

- [ ] **Step 5: Проверить JS — нет ли поломки после переименования классов**

```bash
grep -rn "nav__dropdown-item\|logout-trigger\|requests-count\|copy-child-link" public/js/
```

Убедиться что JS ищет правильные классы/селекторы. Обновить если нужно.

- [ ] **Step 6: Commit**

```bash
git add views/components/nav.html public/css/partials/components.css
git commit -m "style: dropdown More menu — sections, icons, role-based grouping"
```

---

## Chunk 3: Публичный nav и мобильная адаптивность

### Task 5: Публичный nav → горизонтальный scroll на мобиле

**Files:**
- Modify: `public/css/public-top-nav.css`
- Modify: `views/components/public-top-nav.html`

- [ ] **Step 1: Прочитать текущий CSS**

```bash
cat public/css/public-top-nav.css
```

Найти media query где `flex-wrap: wrap` — причина стекирования ~150px.

- [ ] **Step 2: Исправить мобильный nav**

```css
@media (max-width: 768px) {
  .public-site-header {
    gap: 0.35rem;
    padding: 0.45rem 0.65rem;
  }

  .public-top-nav {
    flex-wrap: nowrap;
    overflow-x: auto;
    scrollbar-width: none;
    -webkit-overflow-scrolling: touch;
    flex: 1;
    min-width: 0;
  }

  .public-top-nav::-webkit-scrollbar {
    display: none;
  }

  .public-top-nav a {
    flex-shrink: 0;
    white-space: nowrap;
    font-size: 0.8rem;
    padding: 0.4rem 0.65rem;
  }

  .public-top-nav-cta {
    flex-shrink: 0;
    white-space: nowrap;
  }
}
```

- [ ] **Step 3: Сократить brand на мобиле**

В `public-top-nav.html`:
```html
<a class="public-site-brand" href="/">
  <span class="public-site-brand-dot"></span>
  <span class="brand-full">EarnIt Kids</span>
  <span class="brand-short">EarnIt</span>
</a>
```

В `public-top-nav.css`:
```css
.brand-short { display: none; }

@media (max-width: 480px) {
  .brand-full  { display: none; }
  .brand-short { display: inline; }
}
```

- [ ] **Step 4: Проверить высоту**

Сузить до 375px. Публичный nav: одна строка ~56px, горизонтальный scroll, "Войти →" всегда видна справа.

- [ ] **Step 5: Commit**

```bash
git add public/css/public-top-nav.css views/components/public-top-nav.html
git commit -m "style: public nav horizontal scroll on mobile ~56px"
```

---

## Chunk 4: Суперадмин — полный редизайн

### Task 6: Суперадмин header и tabs

**Files:**
- Modify: `views/super-admin.html`
- Modify: `public/css/super-admin.css`

- [ ] **Step 1: Прочитать текущий header суперадмина**

```bash
grep -n "header\|logout\|title\|eyebrow" views/super-admin.html | head -30
```

- [ ] **Step 2: Обновить header**

Найти header блок и заменить на двухколоночный layout:

```html
<div class="super-header">
  <div class="super-header__left">
    <div class="super-header__eyebrow">Системная панель</div>
    <div class="super-header__title">EarnIt Kids Admin</div>
    <div class="super-header__sub">
      🟢 <span id="families-count">—</span> семей · <span id="users-count">—</span> пользователей
    </div>
  </div>
  <button class="super-header__btn logout-btn">Выйти →</button>
</div>
```

- [ ] **Step 3: CSS для header**

```css
.super-header {
  background: linear-gradient(140deg, rgba(255,255,255,0.97), rgba(245,249,255,0.92)),
              linear-gradient(135deg, rgba(92,199,243,0.1), rgba(255,214,107,0.14));
  border-bottom: 1px solid rgba(123,147,181,0.16);
  padding: 1.25rem 1.5rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.super-header__left { display: flex; flex-direction: column; gap: 0.2rem; }
.super-header__eyebrow {
  font-size: 0.65rem; font-weight: 800;
  letter-spacing: 0.15em; text-transform: uppercase; color: #7c8ca4;
}
.super-header__title { font-size: 1.4rem; font-weight: 900; color: #26344f; line-height: 1.2; }
.super-header__sub { font-size: 0.8rem; color: #5d6f89; }
.super-header__btn {
  padding: 0.65rem 1.1rem; border-radius: 999px; border: none; cursor: pointer;
  font-size: 0.85rem; font-weight: 800; color: white;
  background: linear-gradient(135deg, #ffb65c, #ff8f70);
  box-shadow: 0 8px 20px rgba(255,143,112,0.3);
}
```

- [ ] **Step 4: CSS для tabs**

```css
.tabs {
  display: flex;
  gap: 0.35rem;
  padding: 0.75rem 1.25rem;
  background: rgba(255,255,255,0.6);
  border-bottom: 1px solid rgba(123,147,181,0.12);
}

.tab-btn {
  padding: 0.5rem 0.95rem;
  border-radius: 10px;
  border: 1px solid transparent;
  font-size: 0.82rem;
  font-weight: 700;
  color: #5d6f89;
  cursor: pointer;
  background: transparent;
}

.tab-btn.active {
  background: white;
  color: #26344f;
  border-color: rgba(92,199,243,0.3);
  box-shadow: 0 4px 12px rgba(0,0,0,0.06);
}
```

- [ ] **Step 5: Commit**

```bash
git add views/super-admin.html public/css/super-admin.css
git commit -m "style: super-admin header eyebrow+title+stats layout"
```

---

### Task 7: Суперадмин — вкладка "Семьи"

**Files:**
- Modify: `views/super-admin.html`
- Modify: `public/css/super-admin.css`
- Modify: `public/js/modules/super-admin-families.js`

- [ ] **Step 1: Прочитать как рендерятся карточки**

```bash
grep -n "family-card\|renderFamily\|familyCard\|family_card" public/js/modules/super-admin-families.js | head -20
```

- [ ] **Step 2: CSS карточки семьи**

```css
.family-card {
  background: white;
  border: 1px solid rgba(123,147,181,0.16);
  border-radius: 14px;
  padding: 0.9rem 1rem;
  display: flex;
  align-items: center;
  gap: 0.85rem;
  box-shadow: 0 4px 12px rgba(104,133,176,0.07);
  transition: border-color 0.15s;
}

.family-card:hover { border-color: rgba(92,199,243,0.3); }

.family-avatar {
  width: 2.5rem; height: 2.5rem; border-radius: 12px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center; font-size: 1.1rem;
  background: linear-gradient(135deg, rgba(92,199,243,0.2), rgba(255,214,107,0.2));
}

.family-info { flex: 1; min-width: 0; }
.family-name { font-size: 0.9rem; font-weight: 800; color: #26344f; }
.family-meta { font-size: 0.72rem; color: #5d6f89; margin-top: 0.1rem; }

.family-actions { display: flex; gap: 0.35rem; flex-shrink: 0; align-items: center; }

.btn-view, .btn-block, .btn-unblock {
  padding: 0.35rem 0.75rem; border-radius: 999px; border: none;
  cursor: pointer; font-size: 0.73rem; font-weight: 700;
}
.btn-view    { background: rgba(92,199,243,0.15); color: #3aa7d4; }
.btn-block   { background: rgba(235,107,122,0.12); color: #eb6b7a; }
.btn-unblock { background: rgba(120,197,138,0.15); color: #2e7d45; }
```

- [ ] **Step 3: Обновить рендер карточки в JS**

Найти функцию рендера семьи и добавить avatar (только доверенные данные из сервера):

```javascript
function getFamilyAvatar(childrenCount) {
  const n = Number(childrenCount);
  if (n === 0) return '👤';
  if (n === 1) return '👨‍👧';
  return '👨‍👩‍👧';
}
```

Обновить шаблон карточки добавив `.family-avatar` с вызовом `getFamilyAvatar`.

- [ ] **Step 4: CSS фильтры — pill стиль**

```css
.super-filters {
  display: flex; gap: 0.5rem; flex-wrap: wrap; align-items: center;
}

.super-filter-input {
  flex: 1; min-width: 160px; padding: 0.5rem 0.85rem;
  border-radius: 999px; border: 1px solid rgba(123,147,181,0.22);
  background: white; font-size: 0.82rem; outline: none;
}
.super-filter-input:focus {
  border-color: rgba(92,199,243,0.5);
  box-shadow: 0 0 0 3px rgba(92,199,243,0.1);
}

.super-filter-select {
  padding: 0.5rem 0.85rem; border-radius: 999px;
  border: 1px solid rgba(123,147,181,0.22);
  background: white; font-size: 0.82rem; cursor: pointer;
}
```

- [ ] **Step 5: Commit**

```bash
git add views/super-admin.html public/css/super-admin.css public/js/modules/super-admin-families.js
git commit -m "style: super-admin families tab warm cards avatar pill buttons"
```

---

### Task 8: Суперадмин — вкладка "База данных"

**Files:**
- Modify: `views/super-admin.html`
- Modify: `public/css/super-admin.css`
- Modify: `public/js/modules/super-admin-db.js`

- [ ] **Step 1: Прочитать текущую DB-секцию**

```bash
grep -n "backup\|restore\|db-card\|database" views/super-admin.html | head -30
```

- [ ] **Step 2: Заменить HTML DB-вкладки на 4-карточный layout**

Найти секцию database/backup и заменить содержимое:

```html
<div class="backup-grid">
  <div class="backup-card backup-card--blue">
    <div class="backup-card__label">Последний бэкап</div>
    <div class="backup-card__value" id="last-backup-status">⏳ Проверка...</div>
    <div class="backup-card__sub" id="last-backup-file">—</div>
    <button class="backup-card-btn backup-card-btn--blue" id="create-backup-btn">Создать бэкап</button>
  </div>
  <div class="backup-card backup-card--green">
    <div class="backup-card__label">Размер базы</div>
    <div class="backup-card__value" id="db-size">—</div>
    <div class="backup-card__sub" id="db-records">—</div>
    <button class="backup-card-btn backup-card-btn--green" id="download-dump-btn">Скачать дамп</button>
  </div>
  <div class="backup-card backup-card--orange">
    <div class="backup-card__label">Восстановление</div>
    <div class="backup-card__value">🔄 Готов</div>
    <div class="backup-card__sub">Загрузить .sql или .sql.gz файл</div>
    <label class="backup-card-btn backup-card-btn--orange" for="restore-file-input" style="cursor:pointer;display:block;text-align:center;">
      Загрузить файл
    </label>
    <input type="file" id="restore-file-input" accept=".sql,.sql.gz,.dump" style="display:none;">
  </div>
  <div class="backup-card backup-card--red">
    <div class="backup-card__label">Опасная зона</div>
    <div class="backup-card__value">⚠️ Сброс</div>
    <div class="backup-card__sub">Удалить все данные (необратимо)</div>
    <button class="backup-card-btn backup-card-btn--red" id="reset-db-btn">Сбросить БД</button>
  </div>
</div>
<div class="backup-list-card">
  <div class="backup-list-title">Последние бэкапы</div>
  <div id="backup-list"></div>
</div>
```

- [ ] **Step 3: CSS backup карточек**

```css
.backup-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.backup-card {
  background: white;
  border: 1px solid rgba(123,147,181,0.16);
  border-radius: 14px;
  padding: 1rem;
  position: relative;
  overflow: hidden;
}

.backup-card::before {
  content: '';
  position: absolute; top: 0; left: 0; right: 0; height: 3px;
  border-radius: 3px 3px 0 0;
}

.backup-card--blue::before   { background: linear-gradient(90deg, #5cc7f3, #4ea8d0); }
.backup-card--green::before  { background: linear-gradient(90deg, #78c58a, #4ade80); }
.backup-card--orange::before { background: linear-gradient(90deg, #ffd86b, #ffb65c); }
.backup-card--red::before    { background: linear-gradient(90deg, #eb6b7a, #f43f5e); }

.backup-card__label {
  font-size: 0.65rem; font-weight: 800; text-transform: uppercase;
  letter-spacing: 0.09em; color: #94a3b8; margin-bottom: 0.4rem;
}
.backup-card__value {
  font-size: 1.1rem; font-weight: 900; color: #26344f; margin-bottom: 0.15rem;
}
.backup-card__sub {
  font-size: 0.72rem; color: #5d6f89; margin-bottom: 0.6rem;
}

.backup-card-btn {
  display: block; width: 100%;
  padding: 0.38rem 0.9rem; border-radius: 999px; border: none;
  cursor: pointer; font-size: 0.73rem; font-weight: 700; text-align: center;
}
.backup-card-btn--blue   { background: rgba(92,199,243,0.15); color: #3aa7d4; }
.backup-card-btn--green  { background: rgba(120,197,138,0.15); color: #2e7d45; }
.backup-card-btn--orange { background: rgba(255,182,107,0.15); color: #c4620a; }
.backup-card-btn--red    { background: rgba(235,107,122,0.12); color: #c0374a; }

.backup-list-card {
  background: white; border-radius: 14px; padding: 1rem;
  border: 1px solid rgba(123,147,181,0.16);
}
.backup-list-title {
  font-size: 0.72rem; font-weight: 800; text-transform: uppercase;
  letter-spacing: 0.08em; color: #94a3b8; margin-bottom: 0.6rem;
}
.backup-list-item {
  display: flex; align-items: center; gap: 0.75rem;
  font-size: 0.82rem; padding: 0.4rem 0;
  border-bottom: 1px solid rgba(0,0,0,0.04);
}
.backup-list-item:last-child { border-bottom: none; }
.backup-list-item-name { font-weight: 700; flex: 1; }
.backup-list-item-size { color: #5d6f89; }

@media (max-width: 600px) {
  .backup-grid { grid-template-columns: 1fr; }
}
```

- [ ] **Step 4: JS — обновить рендер бэкапов**

В `super-admin-db.js` найти функцию рендера списка бэкапов. Обновить разметку чтобы использовать новые классы:

```javascript
// Только доверенные данные с сервера (имя файла, размер — числа/строки без HTML)
function renderBackupItem(filename, size) {
  const item = document.createElement('div');
  item.className = 'backup-list-item';

  const icon = document.createElement('span');
  icon.textContent = '🗄️';

  const name = document.createElement('span');
  name.className = 'backup-list-item-name';
  name.textContent = filename; // textContent — безопасно

  const sizeEl = document.createElement('span');
  sizeEl.className = 'backup-list-item-size';
  sizeEl.textContent = size;

  const btn = document.createElement('button');
  btn.className = 'btn-view';
  btn.textContent = 'Скачать';
  btn.addEventListener('click', () => downloadBackup(filename));

  item.append(icon, name, sizeEl, btn);
  return item;
}
```

- [ ] **Step 5: Commit**

```bash
git add views/super-admin.html public/css/super-admin.css public/js/modules/super-admin-db.js
git commit -m "style: super-admin DB tab 4-card layout with warm accent bars"
```

---

## Chunk 5: Today-карточки и мобильные карточки

### Task 9: Today-карточки — сверить и довести до mockup

**Files:**
- Modify: `views/components/section_today.html`
- Modify: `public/css/partials/components.css`

- [ ] **Step 1: Прочитать текущий section_today.html**

```bash
cat views/components/section_today.html
```

Сравнить с mockup секции 6 ux-preview.html: 4 карточки — Баланс, Сделать сейчас, Статус заявок, Прогресс недели.

- [ ] **Step 2: Проверить CSS today-card**

```bash
grep -n "today-card\|today-grid" public/css/partials/components.css
```

Убедиться что есть:
- `background: #fff8f0` (warm surface)
- `::before` с цветными accent-полосками
- `border-radius: 16px`
- `.today-grid` с `grid-template-columns: 1fr 1fr`

Если отличается — привести к виду:

```css
.today-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.65rem;
}

.today-card {
  background: #fff8f0;
  border: 1px solid var(--color-warm-border);
  border-radius: 16px;
  padding: 0.9rem;
  position: relative;
  overflow: hidden;
}

.today-card::before {
  content: '';
  position: absolute; top: 0; left: 0; right: 0;
  height: 3px; border-radius: 3px 3px 0 0;
}

.today-card:nth-child(1)::before { background: linear-gradient(90deg, #ffb65c, #ff8f70); }
.today-card:nth-child(2)::before { background: linear-gradient(90deg, #5fb8e8, #4ea8d0); }
.today-card:nth-child(3)::before { background: linear-gradient(90deg, #86efac, #4ade80); }
.today-card:nth-child(4)::before { background: linear-gradient(90deg, #c4b5fd, #8b73c4); }

@media (max-width: 480px) {
  .today-grid { grid-template-columns: 1fr; }
}
```

- [ ] **Step 3: Commit**

```bash
git add views/components/section_today.html public/css/partials/components.css
git commit -m "style: today-cards warm style accent bars 2-col grid"
```

---

### Task 10: Мобильные карточки — убрать dark режим

**Files:**
- Modify: `public/css/responsive.css`

- [ ] **Step 1: Найти dark overrides**

```bash
grep -n "0f172a\|1e293b\|rgba(15\|rgba(30" public/css/responsive.css
```

- [ ] **Step 2: Заменить dark card backgrounds на warm**

Любое `background: rgba(15, 23, 42, ...)` в контексте карточек на мобиле заменить:

```css
/* было: dark card */
/* background: rgba(15, 23, 42, 0.92); color: white; */

/* стало: warm */
background: #fff8f0;
border: 1px solid var(--color-warm-border);
color: var(--color-text);
```

- [ ] **Step 3: Проверить на 375px**

Карточки заданий: warm (#fff8f0), не тёмные.

- [ ] **Step 4: Commit**

```bash
git add public/css/responsive.css
git commit -m "style: remove dark card backgrounds on mobile"
```

---

## Chunk 6: Финальная проверка

### Task 11: Smoke test всех изменений

- [ ] **Step 1: Child-роль**
  - Кнопки pill везде (Выполнить, Добавить, Сохранить, Отмена)
  - Warm orange цвет на primary CTA кнопках
  - Nav иконки: 📋✅🛍️🏆📨
  - Dropdown "Ещё" — секции "Разделы" и "Настройки" с emoji-иконками
  - Today-карточки — 4 warm карточки с цветными полосками

- [ ] **Step 2: Parent-роль**
  - Header синий/фиолетовый с бейджем "Родитель"
  - Nav: 📊 Обзор, ✅ Задания, 📨 Заявки, 🛍️ Награды
  - Dropdown "Ещё" — секции "Управление" и "Семья"

- [ ] **Step 3: Мобиль 375px**
  - Карточки заданий warm, не тёмные
  - Публичный nav: одна строка, горизонтальный scroll, "Войти →" видна

- [ ] **Step 4: Суперадмин**
  - Header: eyebrow + title + stats + кнопка "Выйти →"
  - Вкладка Семьи: avatar + name + meta + кнопки Детали/Блок
  - Вкладка БД: 4 карточки с цветными полосками + список бэкапов

- [ ] **Step 5: Финальный commit если всё ОК**

```bash
git add .
git commit -m "style: complete UI/UX redesign per ux-preview.html"
```

---

## Сводная таблица файлов

| Задача | Файлы |
|--------|-------|
| T1: Pill buttons | `public/css/partials/components.css` |
| T2: Warm CTA color | `public/css/partials/components.css` |
| T3: Nav emoji icons | `views/components/nav.html`, `components.css` |
| T4: Dropdown sections | `views/components/nav.html`, `components.css` |
| T5: Public nav mobile | `public/css/public-top-nav.css`, `views/components/public-top-nav.html` |
| T6: Super-admin header | `views/super-admin.html`, `super-admin.css` |
| T7: Super-admin families | `views/super-admin.html`, `super-admin.css`, `super-admin-families.js` |
| T8: Super-admin DB tab | `views/super-admin.html`, `super-admin.css`, `super-admin-db.js` |
| T9: Today cards | `views/components/section_today.html`, `components.css` |
| T10: Mobile dark → warm | `public/css/responsive.css` |
| T11: Smoke test | — |
