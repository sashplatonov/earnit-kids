# Каталог заданий — рекомпозиция шапки и строки действий

## Goal

Привести экран «Каталог заданий» (и «Каталог наград») к целевому виду: оставить **один** заголовок `Каталог заданий` с подписью `Найдите готовые задания и добавьте подходящие в свою семью.`, а блок «Выбрать несколько» превратить в компактную кнопку с графикой и поставить её в одну строку с кнопкой «Фильтры».

## Architectural decisions

- **Источник истины заголовка** — родительский компонент `TelegramParentCatalog.svelte`. Он уже рендерит `<h1>` с `taskTitle`/`taskDescription` (и `rewardTitle`/`rewardDescription`). Дублирующий заголовок внутри `TelegramReadyCatalog.svelte` (`catalogTasks` + `catalogTasksHintShort`) должен быть удалён, а не «подправлен».
- **Границы слоёв**: `TelegramParentCatalog.svelte` — экран/шапка; `TelegramReadyCatalog.svelte` — список, поиск, фильтры и bulk-выбор. Заголовок принадлежит родителю, а не списку.
- **Графика «Выбрать несколько»** — семантическая SVG-иконка из уже подключённого семейства `@lucide/svelte` (через `telegramIconMap.ts`), а не эмодзи и не текст. Текущая иконка `check` неоднозначна (читается как «готово/подтвердить»), поэтому заменяется на иконку «список с галочками» (`ListChecks`).
- **Одна строка действий**: `bulk-toggle` и `filter-btn` уже лежат в общем `.action-row`; задача — зафиксировать это как единый ряд и проверить, что он не разъезжается на 320px.
- **Доступность**: текст «Выбрать несколько» сохраняется как `aria-label`/`label` иконки, чтобы кнопка оставалась озвучиваемой для скринридеров.
- **Отклонённые дубли**: не создавать второй источник заголовка, не вводить отдельный компонент-обёртку для шапки, не дублировать i18n-ключи заголовка.

## Recommended implementation order

| Order | Task | Priority | Depends on | Reason |
| ---: | --- | --- | --- | --- |
| 1 | CAT-HDR-1 | P1 | - | Убрать дубль заголовка, чтобы шапка «осталась как есть» в единственном экземпляре |
| 2 | CAT-HDR-2 | P2 | CAT-HDR-1 | Заменить текст/неоднозначную иконку на графическую кнопку и собрать один ряд действий |

## CAT-HDR-1: Убрать дублирующий заголовок каталога

**Status:** ✅ Completed
**Priority:** P1
**Depends on:** -

### Outcome

На экране каталога отображается ровно один заголовок `Каталог заданий` с подписью `Найдите готовые задания и добавьте подходящие в свою семью.` (для наград — `Каталог наград` / `Добавьте только то, что подходит вашей семье.`). Второй заголовок `Каталог заданий` + `Добавьте готовые задания` исчезает.

### Architectural decision

Заголовок принадлежит `TelegramParentCatalog.svelte` (уже рендерит `<h1 id="catalog-title">` и `.desc`). Блок `.catalog-header` внутри `TelegramReadyCatalog.svelte` — дубль, который удаляется целиком. Ключи `catalogTasks`/`catalogRewards` остаются (используются в `aria-label` списка и в `TelegramGroupSubnav`), а `catalogTasksHintShort`/`catalogRewardsHintShort` становятся неиспользуемыми и удаляются из обоих языков.

### Files

- Modify `apps/web/src/lib/components/telegram/TelegramReadyCatalog.svelte`.
- Modify `apps/web/src/lib/i18n/messages/ru/app.ts`.
- Modify `apps/web/src/lib/i18n/messages/en/app.ts`.

### Work

1. Удалить блок `<div class="catalog-header">…</div>` (заголовок `catalog-title` и подпись `catalog-subtitle`) из разметки `TelegramReadyCatalog.svelte`.
2. Удалить ставшие неиспользуемыми CSS-правила `.catalog-header`, `.catalog-title`, `.catalog-subtitle` из `<style>` этого же файла.
3. Удалить ключи `catalogTasksHintShort` и `catalogRewardsHintShort` из `ru/app.ts` и `en/app.ts` (проверить, что они больше нигде не используются).
4. Убедиться, что `catalogTasks`/`catalogRewards` по-прежнему используются (список и поднавигация) и не удаляются.

### Acceptance criteria

- На экране каталога ровно один заголовок `Каталог заданий` и одна подпись `Найдите готовые задания и добавьте подходящие в свою семью.`.
- Для наград — один заголовок `Каталог наград` и подпись `Добавьте только то, что подходит вашей семье.`.
- Нет визуального дубля заголовка ни на десктопе, ни на 320px.
- `npm run lint` и `npm run build` проходят без ошибок и предупреждений о неиспользуемых ключах/стилях.

### Verification

```bash
cd apps/web
npm run lint
npm run test
npm run build
```

### Commit

```bash
git add apps/web/src/lib/components/telegram/TelegramReadyCatalog.svelte apps/web/src/lib/i18n/messages/ru/app.ts apps/web/src/lib/i18n/messages/en/app.ts
git commit -m "fix(web): remove duplicate catalog header in ready catalog"
```

## CAT-HDR-2: Графическая кнопка «Выбрать несколько» в одной строке с «Фильтры»

**Status:** ✅ Completed
**Priority:** P2
**Depends on:** CAT-HDR-1

### Outcome

Блок «Выбрать несколько» отображается как компактная кнопка с семантической графикой (иконка «список с галочками»), а не как текст или неоднозначная иконка-галочка. Кнопки «Выбрать несколько» и «Фильтры» стоят в одной строке и не разъезжаются на ширине 320px.

### Architectural decision

Иконка добавляется в `telegramIconMap.ts` под новым ключом (например `selectSeveral` → `ListChecks` из `@lucide/svelte`), чтобы не плодить инлайн-SVG. Текст «Выбрать несколько» остаётся только как `label`/`aria-label` для доступности. Ряд `.action-row` уже существует — задача зафиксировать его как единый ряд и проверить поведение при активном bulk-режиме (иконка меняется на «Готово»).

### Files

- Modify `apps/web/src/lib/components/telegram/TelegramReadyCatalog.svelte`.
- Modify `apps/web/src/lib/components/telegram/telegramIconMap.ts`.

### Work

1. Добавить импорт `ListChecks` в `telegramIconMap.ts` и ключ `selectSeveral: ListChecks` в `telegramIconMap`.
2. В `TelegramReadyCatalog.svelte` заменить иконку `check` в неактивном состоянии `bulk-toggle` на `selectSeveral` (активное состояние `done`/`check` оставить).
3. Сохранить `label={$i18n.t('app.telegram.readyCatalog.selectSeveral')}` для доступности.
4. Проверить/подправить `.action-row` и `.bulk-toggle`/`.filter-btn`, чтобы обе кнопки гарантированно помещались в одну строку на 320px без горизонтального скролла (при необходимости — `flex-wrap: nowrap`, `min-width:0`, `flex:0 0 auto`).

### Acceptance criteria

- Кнопка «Выбрать несколько» визуально является графической (иконка «список с галочками»), без текста.
- Кнопки «Выбрать несколько» и «Фильтры» находятся в одной строке на 320px и на десктопе, без горизонтального скролла.
- В bulk-режиме иконка меняется на «Готово», поведение выбора не ломается.
- Кнопка остаётся доступной: `aria-label`/`label` = «Выбрать несколько», тач-таргет ≥ 44×44px (или в рамках принятого компактного стандарта проекта), видимый фокус сохраняется.
- `npm run lint` и `npm run build` проходят.

### Verification

```bash
cd apps/web
npm run lint
npm run test
npm run build
npm run test:e2e
```

### Commit

```bash
git add apps/web/src/lib/components/telegram/TelegramReadyCatalog.svelte apps/web/src/lib/components/telegram/telegramIconMap.ts
git commit -m "feat(web): graphic select-several button in single action row"
```
