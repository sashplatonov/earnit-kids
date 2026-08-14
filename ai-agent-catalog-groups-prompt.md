# AI coding agent prompt - EarnIt Kids Groups & Ready Catalog

Implement:

`earnit-catalog-groups-followup-backlog.md`

Before changing UI, open:

`earnit-catalog-groups-target-reference.html`

Use the HTML as the visual/composition reference for:

- Task group management;
- Reward group management;
- group create/edit form;
- ready Task catalog;
- ready Reward catalog;
- catalog search and filters;
- single add;
- already-added state;
- multi-select / bulk add;
- item details;
- mobile density.

## Critical boundaries

The previous redesign backlog is already completed.

Do not reopen unrelated Telegram Bot or infrastructure work.

This iteration is Parent Mini App focused.

## Legacy form rule

Current group management opens a legacy website form.

Remove that behavior.

All Task/Reward group management must be Mini App-native.

## Ready catalog rule

The catalog is a curated template library for parents.

It is NOT:

- the child's Reward shop;
- a social feed;
- a marketplace;
- catalog admin tooling.

Parent can:

- browse;
- search;
- filter;
- preview;
- add one;
- select several;
- copy selected templates into the family.

## Content rule

Catalog Task/Reward titles intentionally begin with one emoji.

This is content, not UI graphics.

Example:

`📖 Почитать книгу 15 минут`

Keep SVG icons for controls/navigation/entity graphics.

Do not replace Mini App SVG controls with emoji.

## Title quality

For Tasks, the title must answer:

`Что мне нужно сделать?`

For Rewards:

`Что я получу?`

A child aged 6-14 should understand the title without opening description or group.

Reject vague/game-like titles such as:

- Книжная искра
- Королева настолки
- Командир вечера
- Супер-цель
- Домашний помощник

Follow the full quality contract in the backlog.

## Architecture

Do not hardcode catalog content inside Svelte components.

Use a catalog read model / seed / backend source.

Adding a catalog item must COPY it into family-owned Task/Reward state.

Future global template changes must not overwrite family copies.

Protect against duplicate add and callback/double-tap replay.

## UX

Keep Parent Mini App compact.

Do not add a desktop filter sidebar.

Use horizontal chips + bottom sheet for advanced filters.

Do not force details before adding.

Support bulk selection without introducing a table UI.

Implement in the recommended order in the backlog.


## Mandatory age catalog volume

Seed and expose at least:

- 20 Tasks + 20 Rewards for age 6-8;
- 20 Tasks + 20 Rewards for age 9-11;
- 20 Tasks + 20 Rewards for age 12-14.

Use the exact reference tables in `CAT-009A` as the baseline seed content.

Every item must include:

- title;
- coins or price;
- group;
- frequency;
- age metadata.

Do not implement only a few examples in UI. The filters must have real catalog depth.


## Catalog group navigation - mandatory

Change the catalog reference/implementation so catalog groups behave exactly like the existing main Mini App group submenu:

- place the group submenu directly above bottom navigation;
- `Все` first;
- up to 3 most frequently explicitly selected groups;
- `Ещё` last;
- no horizontal scrolling;
- `Ещё` opens the remaining groups in a bottom sheet;
- Task Catalog and Reward Catalog rankings are independent;
- use the same stable-boundary ranking behavior as the main Mini App so chips do not jump after each tap.

Do not place catalog group chips under search.

## Catalog filters - mandatory

Filters must not horizontally scroll.

Under search show a compact row that fits 320px:

- `Возраст` with a semantic SVG icon;
- `Фильтры` with a semantic SVG sliders/filter icon.

Age choices and advanced filters open compact bottom sheets.

Every filter option must use a semantic SVG graphic from the existing Mini App icon family.

Do not use emoji for filter graphics.

Group selection and filters are different concepts and must be visually separated.
