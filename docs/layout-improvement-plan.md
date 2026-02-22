# План улучшения вёрстки — Web & Mobile (Best Practices 2026)

## Текущее состояние

- Тёмная тема с CSS-переменными, шрифт Nunito
- Одна CSS-файл (`style.css`, ~34 KB), media queries для мобильных
- Нет CSS-препроцессоров, нет Tailwind
- Нет CSS Container Queries
- Нет View Transitions API
- Минимальные анимации
- Pull-to-refresh реализован вручную

---

## 🎨 Web-вёрстка

### 1. Современная типографика
- [x] Использовать `font-display: swap` для Google Fonts
- [x] Fluid typography через `clamp()`: `font-size: clamp(1rem, 2.5vw, 1.25rem)`
- [x] Вариативные шрифты (Variable Fonts) — один файл вместо нескольких начертаний
- [x] Увеличить line-height для мобильных (1.6–1.8 для body copy)

### 2. CSS Container Queries
- [x] Заменить медиа-запросы на container queries для компонентов-карточек:
  ```css
  .card-container { container-type: inline-size; }
  @container (max-width: 400px) { .card { flex-direction: column; } }
  ```
- [x] Карточки задач, товаров магазина, истории — адаптировать по ширине контейнера, не viewport

### 3. CSS Subgrid
- [x] Использовать `subgrid` для выравнивания вложенных элементов в карточках:
  ```css
  .grid-layout { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); }
  .card { display: grid; grid-row: span 3; grid-template-rows: subgrid; }
  ```

### 4. Scroll-driven Animations
- [x] Анимации при скролле через CSS `animation-timeline: scroll()`:
  - Fade-in карточек секций при появлении
  - Progress bar в header при скролле вниз
  ```css
  @keyframes fadeIn { from { opacity: 0; transform: translateY(20px); } }
  .card { animation: fadeIn linear; animation-timeline: view(); animation-range: entry 0% entry 100%; }
  ```

### 5. View Transitions API
- [x] Плавные переходы между табами/секциями:
  ```javascript
  document.startViewTransition(() => { switchTab(newTab); });
  ```
- [x] CSS-правила для transitions:
  ```css
  ::view-transition-old(section) { animation: fade-out 0.2s ease; }
  ::view-transition-new(section) { animation: fade-in 0.2s ease; }
  ```

### 6. Color Scheme & Palette
- [x] Добавить `color-scheme: dark` на `<html>` и `:root`
- [x] Использовать `oklch()` / `oklab()` для более перцептуально-ровных градиентов:
  ```css
  --color-primary: oklch(0.55 0.25 264);
  --gradient-primary: linear-gradient(135deg, oklch(0.55 0.25 264), oklch(0.6 0.2 290));
  ```
- [ ] `light-dark()` функция для автотемы (если добавить light mode):
  ```css
  color: light-dark(#1a1a2e, #f8fafc);
  ```

### 7. Микроанимации
- [x] `@starting-style` для анимации появления модальных окон:
  ```css
  dialog[open] { opacity: 1; transform: scale(1); }
  @starting-style { dialog[open] { opacity: 0; transform: scale(0.95); } }
  ```
- [ ] Анимация числа баланса при изменении (counter animation)
- [x] Haptic-like feedback при нажатии кнопок (subtle scale + shadow change)
- [ ] Skeleton loading вместо спиннеров при загрузке данных

### 8. Производительность CSS
- [x] `content-visibility: auto` для скрытых секций (экономия рендеринга):
  ```css
  .section[hidden] { content-visibility: auto; contain-intrinsic-size: auto 500px; }
  ```
- [x] `will-change` только на анимируемых элементах
- [ ] Минификация CSS для production

---

## 📱 Мобильная вёрстка

### 9. Safe Area & Notch
- [x] Корректная поддержка `env(safe-area-inset-*)` для всех элементов:
  ```css
  .header { padding-top: calc(0.5rem + env(safe-area-inset-top)); }
  .nav { padding-bottom: calc(0.5rem + env(safe-area-inset-bottom)); }
  ```
- [x] `viewport-fit=cover` в `<meta viewport>` для полноэкранного режима

### 10. Touch-оптимизация
- [x] Минимальный tap target — **48×48px** (Google Material 3 рекомендация 2026)
- [x] `touch-action: manipulation` на интерактивных элементах (убирает 300ms задержку)
- [ ] Убрать `user-select: none` с текстового контента (только на кнопках)
- [ ] Swipe-жесты для навигации между табами:
  ```javascript
  // Swipe left → следующий таб, swipe right → предыдущий
  ```

### 11. Мобильные модалки
- [ ] Использовать нативный `<dialog>` element вместо кастомных модалок
- [x] Bottom sheet pattern для мобильных вместо центрированных модалок:
  ```css
  @media (max-width: 768px) {
    dialog { position: fixed; bottom: 0; left: 0; right: 0;
             border-radius: 16px 16px 0 0; max-height: 85vh; }
  }
  ```
- [x] Backdrop blur для overlay: `backdrop-filter: blur(8px)`

### 12. Мобильные формы
- [ ] `inputmode="numeric"` для полей с монетами (показывает цифровую клавиатуру)
- [ ] `autocomplete` атрибуты для email/password полей
- [ ] Sticky submit button внизу экрана на мобильных
- [ ] Автофокус первого поля при открытии модалки

### 13. Адаптивные компоненты
- [ ] Header — компактный на мобильных (balance в одну строку, мелкий шрифт)
- [ ] Навигация — bottom tab bar (аналог iOS/Android нативной навигации):
  ```css
  .nav { position: fixed; bottom: 0; display: grid; grid-template-columns: repeat(auto-fit, minmax(60px, 1fr)); }
  ```
- [ ] Карточки — stack layout на мобильных, grid на desktop
- [ ] Таблицы (history) — горизонтальный скролл или card-layout на мобильных

### 14. Производительность на мобильных
- [ ] Ленивая загрузка изображений: `<img loading="lazy">`
- [ ] `prefers-reduced-motion` — отключить анимации для пользователей с motion sensitivity:
  ```css
  @media (prefers-reduced-motion: reduce) {
    *, *::before, *::after { animation-duration: 0.01ms !important; transition-duration: 0.01ms !important; }
  }
  ```
- [ ] Минимизировать reflows при динамическом обновлении списков
- [ ] Virtual scrolling для длинных списков (history, requests)

---

## 🔄 Общие улучшения (Web + Mobile)

### 15. Accessibility (a11y)
- [ ] Семантические теги: `<nav>`, `<main>`, `<section>`, `<article>`, `<dialog>`
- [ ] ARIA-атрибуты: `role`, `aria-label`, `aria-expanded`, `aria-selected` для табов
- [ ] Фокус-трэп в модальных окнах
- [ ] Контрастность: проверить WCAG 2.2 AA для всех текстов
- [ ] Skip-to-content link
- [ ] `prefers-contrast: more` media query для высококонтрастного режима

### 16. Design Tokens & System
- [ ] Вынести дизайн-токены (spacing, typography scale) в отдельный файл `tokens.css`:
  ```css
  :root {
    --space-1: 0.25rem; --space-2: 0.5rem; --space-3: 0.75rem; --space-4: 1rem;
    --text-xs: 0.75rem; --text-sm: 0.875rem; --text-base: 1rem; --text-lg: 1.125rem;
  }
  ```
- [ ] Компонентные стили: `button.css`, `card.css`, `modal.css` — разбить `style.css` на логические части
- [ ] CSS layers (`@layer`) для управления каскадом:
  ```css
  @layer reset, tokens, components, utilities;
  ```

### 17. Логическая разбивка CSS
- [ ] Разбить `style.css` (34KB) на:
  - `tokens.css` — переменные, дизайн-токены
  - `reset.css` — сброс стилей
  - `components.css` — карточки, кнопки, формы
  - `layout.css` — grid, header, nav, sections
  - `animations.css` — все keyframes и transitions
  - `responsive.css` — все медиа-запросы
- [ ] Объединять в один файл при production build

### 18. PWA-манифест
- [ ] Добавить `manifest.json` с иконками, цветами, ориентацией
- [ ] Theme color и background color в `<meta>` тегах
- [ ] Splash screen для iOS: `apple-touch-startup-image`

---

## Порядок внедрения (рекомендуемый)

| Фаза | Задачи | Сложность |
|------|--------|-----------|
| **1** | Safe Area (#9), Touch (#10), Tap targets, `<dialog>` (#11) | [x] Лёгкая |
| **2** | Design Tokens (#16), Font optimization (#1), Color scheme (#6) | Средняя |
| **3** | Accessibility (#15), Mobile forms (#12), Responsive components (#13) | Средняя |
| **4** | Container Queries (#2), View Transitions (#5), Microanimations (#7) | Средняя |
| **5** | Scroll animations (#4), CSS Subgrid (#3), Virtual scrolling (#14) | Сложная |
| **6** | CSS splitting (#17), PWA (#18), Performance (#8, #14) | Сложная |
