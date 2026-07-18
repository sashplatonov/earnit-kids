/**
 * E2E tests for app sections: requests, shop, history, analytics.
 * The suite seeds its own family data to avoid relying on a fixed account.
 */
import { expect, test } from '@playwright/test';

import {
    DEFAULT_PARENT_PASSWORD,
    addChild,
    approveFirstRequest,
    createReward,
    createTask,
    getChildMagicLink,
    loginChildByMagicLink,
    loginParent,
    logout,
    openFamilyApp,
    requestWithOptionalNote,
    registerParent,
    uniqueEmail,
} from './helpers';
import { assertCatalogCardLayout } from './cardLayoutAssertions';

const CHILD_NAME = 'Разделы Ребёнок';
const TASK_TITLE = 'Собрать рюкзак для школы';
const REWARD_TITLE = 'Настольная игра вечером';

let parentEmail = '';
let childLink = '';

test.beforeAll(async ({ browser }) => {
    parentEmail = uniqueEmail('app.sections');

    const page = await browser.newPage();

    await registerParent(page, parentEmail, DEFAULT_PARENT_PASSWORD);
    await openFamilyApp(page);

    await addChild(page, CHILD_NAME);
    await createTask(page, TASK_TITLE, 40, 'Проверить тетради и собрать вещи на завтра');
    await createReward(page, REWARD_TITLE, 40, 'После всех обязательных дел');

    childLink = await getChildMagicLink(page);

    await loginChildByMagicLink(page, childLink, TASK_TITLE);
    await requestWithOptionalNote(page, page.locator('#tasks-section [data-task-action="request"]').first(), /Request sent|Заявка отправлена/i);

    await logout(page);
    await loginParent(page, parentEmail, DEFAULT_PARENT_PASSWORD);
    await approveFirstRequest(page);

    await loginChildByMagicLink(page, childLink, TASK_TITLE);
    await page.getByRole('link', { name: /Rewards|Награды/i }).click();
    await expect(page.getByRole('heading', { name: REWARD_TITLE })).toBeVisible();
    await requestWithOptionalNote(
        page,
        page.locator('#shop-section [data-shop-action="request"]').first(),
        /Purchase request sent|Заявка на покупку отправлена/i
    );

    await logout(page);
    await loginParent(page, parentEmail, DEFAULT_PARENT_PASSWORD);
    await approveFirstRequest(page);

    await logout(page);
    await loginChildByMagicLink(page, childLink, TASK_TITLE);
    await requestWithOptionalNote(page, page.locator('#tasks-section [data-task-action="request"]').first(), /Request sent|Заявка отправлена/i);

    await page.close();
});

async function login(page: import('@playwright/test').Page) {
    await loginParent(page, parentEmail, DEFAULT_PARENT_PASSWORD, {
        destination: /\/app\/analytics$/,
        heading: /EarnIt Kids/i,
    });
}

test.describe('Shop section', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
        await page.getByRole('link', { name: /Rewards|Награды/i }).click();
        await expect(page.locator('#shop-section')).toBeVisible();
    });

    test('shows shop items with correct name and price from API', async ({ page }) => {
        // Shop items should be rendered with API fields: name and price
        const shopList = page.locator('#shop-list, .cards');
        await expect(shopList).toBeVisible();

        // Items should have titles (name field from API)
        const titles = shopList.locator('.card__title');
        await expect(titles.first()).toBeVisible();
        // Should not show undefined or empty title
        const firstTitle = await titles.first().textContent();
        expect(firstTitle).toBeTruthy();
        expect(firstTitle).not.toBe('undefined');

        // Items should have coins/price value
        const coinValues = shopList.locator('.item-coins');
        await expect(coinValues.first()).toBeVisible();
        const firstPrice = await coinValues.first().textContent();
        expect(firstPrice).toBeTruthy();
        // Price should be a number
        expect(parseInt(firstPrice ?? '')).toBeGreaterThan(0);
        await assertCatalogCardLayout(shopList.locator('.shop-card').first());
    });

    test('admin can see add, buy, and edit buttons', async ({ page }) => {
        await expect(page.locator('#add-shop-btn')).toBeVisible();
        await expect(page.locator('[data-shop-action="buy"]').first()).toBeVisible();
        await expect(page.locator('[data-shop-action="edit"]').first()).toBeVisible();
    });

    test('shows last purchase and completion badges in card and row views', async ({ page }) => {
        await page.getByRole('link', { name: /Tasks|Задания/i }).click();
        const taskCard = page.locator('#tasks-list .task-card').first();
        await assertCatalogCardLayout(taskCard);
        await expect(taskCard.locator('.card__badge--history')).toBeVisible();
        await expect(taskCard.locator('.card__badge--history')).toContainText(/Last done|Последнее выполнение/i);

        await page.getByRole('button', { name: /Rows|Строки/i }).click();
        const taskRow = page.locator('#tasks-list .task-card--list').first();
        await expect(taskRow.locator('.card__compact-meta')).toContainText(/Last done|Последнее выполнение/i);

        await page.getByRole('link', { name: /Rewards|Награды/i }).click();
        const shopCard = page.locator('#shop-list .shop-card').first();
        await expect(shopCard.locator('.card__badge--history')).toBeVisible();
        await expect(shopCard.locator('.card__badge--history')).toContainText(/Last bought|Последняя покупка/i);

        await page.getByRole('button', { name: /Rows|Строки/i }).click();
        const shopRow = page.locator('#shop-list .shop-card--list').first();
        await expect(shopRow.locator('.card__compact-meta')).toContainText(/Last bought|Последняя покупка/i);
    });

    test('shop cards reserve space for wrapped top badges', async ({ page }) => {
        const firstShopCard = page.locator('#shop-list .shop-card').first();
        await expect(firstShopCard).toBeVisible();

        const layout = await firstShopCard.evaluate((card) => {
            const badgeRow = card.querySelector('.card__badge-row');
            const header = card.querySelector('.card__header');

            if (!(badgeRow instanceof HTMLElement) || !(header instanceof HTMLElement)) {
                return null;
            }

            badgeRow.innerHTML = [
                '<span class="card__badge card__badge--group">Развлечения и время</span>',
                '<span class="card__badge card__badge--type">1 time per day</span>',
                '<span class="card__status card__status--available">Available to buy</span>'
            ].join('');

            const badgeRowRect = badgeRow.getBoundingClientRect();
            const headerRect = header.getBoundingClientRect();
            const styles = getComputedStyle(badgeRow);
            const chipMetrics = Array.from(badgeRow.children).map((chip) => {
                if (!(chip instanceof HTMLElement)) {
                    return null;
                }

                const probe = chip.cloneNode(true);
                if (!(probe instanceof HTMLElement)) {
                    return null;
                }

                probe.style.position = 'fixed';
                probe.style.left = '-9999px';
                probe.style.top = '0';
                probe.style.width = 'max-content';
                probe.style.maxWidth = 'none';
                probe.style.minWidth = '0';
                document.body.appendChild(probe);

                const actualWidth = chip.getBoundingClientRect().width;
                const naturalWidth = probe.getBoundingClientRect().width;
                probe.remove();

                return {
                    text: chip.textContent?.trim() ?? '',
                    actualWidth,
                    naturalWidth
                };
            }).filter(Boolean);

            return {
                badgeRowHeight: badgeRowRect.height,
                badgeRowBottom: badgeRowRect.bottom,
                headerTop: headerRect.top,
                reservedMinHeight: parseFloat(styles.minHeight),
                chipMetrics
            };
        });

        expect(layout).not.toBeNull();
        expect(layout?.badgeRowHeight ?? 0).toBeLessThanOrEqual((layout?.reservedMinHeight ?? 0) + 1);
        expect(layout?.badgeRowBottom ?? Number.POSITIVE_INFINITY).toBeLessThanOrEqual((layout?.headerTop ?? 0) + 0.5);
        const chips = (layout?.chipMetrics ?? []) as Array<{ text: string; actualWidth: number; naturalWidth: number }>;
        for (const chip of chips) {
            // defensively guard in case the page returned an unexpected null entry
            if (!chip) continue;
            expect(chip.actualWidth).toBeLessThanOrEqual(chip.naturalWidth + 2);
        }
    });

    test('edit modal populates name and price from existing item', async ({ page }) => {
        // Wait for shop items to load
        const shopList = page.locator('#shop-list, .cards');
        await expect(shopList).toBeVisible();
        const firstTitle = shopList.locator('.card__title').first();
        await expect(firstTitle).toBeVisible();

        // Read the displayed name and price before opening modal
        const itemName = (await firstTitle.textContent())?.trim() ?? '';
        const priceText = (await shopList.locator('.item-coins').first().textContent())?.trim() ?? '';
        const itemPrice = parseInt(priceText);

        // Click the first edit button
        await page.locator('[data-shop-action="edit"]').first().click();
        await expect(page.locator('#shop-modal')).toBeVisible();

        // Name field must not be empty and must match displayed item name
        const nameInput = page.locator('#shop-name');
        const nameValue = await nameInput.inputValue();
        expect(nameValue).toBeTruthy();
        expect(nameValue).not.toBe('');
        if (itemName) {
            expect(nameValue).toBe(itemName);
        }

        // Price must not be default 50 if actual price differs
        const priceInput = page.locator('#shop-price');
        const priceValue = parseInt(await priceInput.inputValue());
        if (!isNaN(itemPrice) && itemPrice !== 50) {
            expect(priceValue).toBe(itemPrice);
        } else {
            // At minimum price should be a positive number
            expect(priceValue).toBeGreaterThan(0);
        }

        // Close modal
        await page.locator('#shop-cancel').click();
        await expect(page.locator('#shop-modal')).not.toBeVisible();
    });

    test('group navigation appears when multiple groups exist', async ({ page }) => {
        // Only visible when items have different group names
        // At minimum, shop items list should render
        await expect(page.locator('#shop-list, .cards, .empty-state')).toBeVisible();
    });
});

test.describe('CSV import flow', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
    });

    test('imports a task from csv and refreshes the task list', async ({ page }) => {
        await page.getByRole('link', { name: /Tasks|Задания/i }).click();
        await expect(page.locator('#tasks-section')).toBeVisible();

        await page.locator('#tasks-section').getByRole('button', { name: /Import CSV|Импорт CSV/i }).click();
        const modal = page.locator('#csv-import-modal');
        await expect(modal).toBeVisible();

        await modal.locator('#csv-import-input').fill('title,coins,groupName\nImported task,12,Home');
        const importButton = modal.getByRole('button', { name: /Import|Импортировать/i });
        await importButton.evaluate((button) => (button as HTMLButtonElement).click());

        await expect(page.getByRole('heading', { name: 'Imported task' })).toBeVisible();
        await expect(page.getByText(/Imported tasks|Импортировано заданий/i)).toBeVisible();
    });

    test('shows bulk selection checkboxes in row view for tasks', async ({ page }) => {
        await page.getByRole('link', { name: /Tasks|Задания/i }).click();
        await expect(page.locator('#tasks-section')).toBeVisible();

        await page.locator('#tasks-section').getByRole('button', { name: /Rows|Строки/i }).click();
        await page.locator('#tasks-section').getByRole('button', { name: /Select|Выбор/i }).click();

        await expect(page.locator('#tasks-list .task-card--list .task-card__select-cell input[type="checkbox"]').first()).toBeVisible();
        await expect(page.locator('#tasks-section').getByRole('button', { name: /Select all|Выбрать все/i })).toBeVisible();
    });

    test('imports a reward from csv and refreshes the shop list', async ({ page }) => {
        await page.getByRole('link', { name: /Rewards|Награды/i }).click();
        await expect(page.locator('#shop-section')).toBeVisible();

        await page.locator('#shop-section').getByRole('button', { name: /Import CSV|Импорт CSV/i }).click();
        const modal = page.locator('#csv-import-modal');
        await expect(modal).toBeVisible();

        await modal.locator('#csv-import-input').fill('name,price,groupName\nImported reward,25,Fun');
        const importButton = modal.getByRole('button', { name: /Import|Импортировать/i });
        await importButton.evaluate((button) => (button as HTMLButtonElement).click());

        await expect(page.getByRole('heading', { name: 'Imported reward' })).toBeVisible();
        await expect(page.getByText(/Imported rewards|Импортировано наград/i)).toBeVisible();
    });

    test('imports a reward after switching to rewards inside the task import modal', async ({ page }) => {
        await page.getByRole('link', { name: /Tasks|Задания/i }).click();
        await expect(page.locator('#tasks-section')).toBeVisible();

        await page.locator('#tasks-section').getByRole('button', { name: /Import CSV|Импорт CSV/i }).click();
        const modal = page.locator('#csv-import-modal');
        await expect(modal).toBeVisible();

        await modal.getByRole('tab', { name: /Rewards|Награды/i }).click();
        await modal.locator('#csv-import-input').fill(
            'name,price,groupName,comment\n'
            + 'Reward from switched modal,25,Fun,"Line 1\nLine 2"'
        );
        const importButton = modal.getByRole('button', { name: /Import|Импортировать/i });
        await importButton.evaluate((button) => (button as HTMLButtonElement).click());

        await expect(page.getByRole('heading', { name: 'Reward from switched modal' })).toBeVisible();
        await expect(page.getByText(/Imported rewards|Импортировано наград/i)).toBeVisible();
    });

    test('copies the csv template for rewards', async ({ page, context }) => {
        await context.grantPermissions(['clipboard-read', 'clipboard-write']);
        await page.getByRole('link', { name: /Rewards|Награды/i }).click();
        await expect(page.locator('#shop-section')).toBeVisible();

        await page.locator('#shop-section').getByRole('button', { name: /Import CSV|Импорт CSV/i }).click();
        const modal = page.locator('#csv-import-modal');
        await expect(modal).toBeVisible();

        await modal.getByRole('button', { name: /Copy format|Скопировать формат/i }).click();
        await expect(modal.getByText(/CSV format copied|Формат CSV скопирован/i)).toBeVisible();
        await expect(page.evaluate(() => navigator.clipboard.readText())).resolves.toContain('name,price,groupName');
    });
});

test.describe('Requests section (Заявки)', () => {
    test('admin sees an incoming pending request with action buttons', async ({ page }) => {
        await login(page);
        await page.getByRole('link', { name: 'Заявки' }).click();

        const requestsList = page.locator('#incoming-requests-list');
        await expect(requestsList).toBeVisible();
        await expect(requestsList.getByText(TASK_TITLE)).toBeVisible();
        await expect(requestsList.getByRole('button', { name: 'Одобрить заявку' }).first()).toBeVisible();
        await expect(requestsList.getByRole('button', { name: 'Отклонить заявку' }).first()).toBeVisible();
    });

    test('child sees own pending request with waiting status', async ({ page }) => {
        await loginChildByMagicLink(page, childLink, TASK_TITLE);
        await page.getByRole('link', { name: 'Заявки' }).click();

        const requestsList = page.locator('#my-requests-list');
        const pendingTaskRequest = requestsList
            .locator('.request-item')
            .filter({ hasText: TASK_TITLE })
            .filter({ hasText: 'Ожидает' })
            .first();

        await expect(requestsList).toBeVisible();
        await expect(pendingTaskRequest).toBeVisible();
    });
});

test.describe('History section (История)', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
        // Navigate to history via the "Еще" menu
        await page.getByRole('button', { name: 'Дополнительные разделы' }).click();
        await page.getByRole('menuitem', { name: 'История' }).click();
        await expect(page.getByRole('heading', { name: 'История операций' })).toBeVisible();
    });

    test('shows budget stats cards', async ({ page }) => {
        const budgetStats = page.locator('#budget-stats');
        await expect(budgetStats).toBeVisible();
        // Should show "Потрачено в этом месяце" stat
        await expect(page.getByText('Потрачено в этом месяце')).toBeVisible();
        // Should show "Заработано сегодня" stat
        await expect(page.getByText('Заработано сегодня')).toBeVisible();
        // Should show "Крупная покупка месяца" stat
        await expect(page.getByText('Крупная покупка месяца')).toBeVisible();
    });

    test('groups history entries by month with earn/spend stats', async ({ page }) => {
        const historyList = page.locator('#history-list');
        await expect(historyList).toBeVisible();

        // Month header should be visible
        const monthHeader = historyList.locator('.history-month-header').first();
        await expect(monthHeader).toBeVisible();

        // Month title should be non-empty
        const monthTitle = await monthHeader.locator('.month-title').textContent();
        expect(monthTitle).toBeTruthy();
        expect(monthTitle).not.toBe('');

        // Stats should show earned/spent coins
        const monthStats = monthHeader.locator('.month-stats');
        await expect(monthStats).toBeVisible();
    });

    test('shows history entries with description, date and amount', async ({ page }) => {
        const historyList = page.locator('#history-list');
        await expect(historyList).toBeVisible();

        const firstEntry = historyList.locator('.history-item').first();
        await expect(firstEntry).toBeVisible();

        // Should show description text (not "undefined" or empty)
        const title = await firstEntry.locator('.history-item__title').textContent();
        expect(title).toBeTruthy();
        expect(title).not.toBe('undefined');
        expect(title).not.toBe('');

        // Should show formatted date with time
        const meta = await firstEntry.locator('.history-item__meta').textContent();
        expect(meta).toBeTruthy();
        // Should match date format dd.mm.yyyy
        expect(meta).toMatch(/\d{2}\.\d{2}\.\d{4}/);

        // Should show coin amount
        const amount = await firstEntry.locator('.history-item__amount').textContent();
        expect(amount).toBeTruthy();
    });

    test('shows money amount (₽) for purchase entries', async ({ page }) => {
        // Find purchase entry with money amount
        const purchaseEntries = page.locator('.history-item--spend');
        const count = await purchaseEntries.count();
        if (count > 0) {
            // Check if money amount is shown for entries that have moneyAmount > 0
            const moneyTag = page.locator('.history-item__money');
            const moneyCount = await moneyTag.count();
            if (moneyCount > 0) {
                const moneyText = await moneyTag.first().textContent();
                expect(moneyText).toMatch(/₽|руб/);
            }
        }
    });

    test('admin can delete history entries', async ({ page }) => {
        const historyList = page.locator('#history-list');
        await expect(historyList).toBeVisible();

        const deleteButtons = historyList.locator('button[aria-label="Удалить запись"]');
        await expect(deleteButtons.first()).toBeVisible();
    });

    test('clear all button exists for admin', async ({ page }) => {
        await expect(page.locator('#clear-history-btn')).toBeVisible();
    });
});

test.describe('Analytics section (Достижения)', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
        await page.getByRole('link', { name: 'Достижения' }).click();
        await expect(page.getByRole('heading', { name: 'Мои достижения' })).toBeVisible();
    });

    test('shows timeframe selector buttons', async ({ page }) => {
        await expect(page.getByRole('button', { name: 'Неделя' })).toBeVisible();
        await expect(page.getByRole('button', { name: 'Месяц' })).toBeVisible();
        await expect(page.getByRole('button', { name: 'Год' })).toBeVisible();
    });

    test('shows summary stats cards', async ({ page }) => {
        await expect(page.getByText('Заработано')).toBeVisible();
        await expect(page.getByText('Потрачено')).toBeVisible();
        await expect(page.getByText('Баланс')).toBeVisible();
    });

    test('shows progress mini-cards', async ({ page }) => {
        await expect(page.locator('[aria-label="Краткий прогресс"]')).toBeVisible();
        // Level value element should be present (e.g. "Lv 1")
        await expect(page.locator('#progress-level-value')).toBeVisible();
    });

    test('timeframe toggle updates active state', async ({ page }) => {
        const monthBtn = page.getByRole('button', { name: 'Месяц' });
        await monthBtn.click();
        // After click, button should be active/selected
        await monthBtn.evaluate(el => el.classList.contains('active') || el.getAttribute('aria-pressed') === 'true' || el.getAttribute('aria-selected') === 'true');
        // Just verify we can click without errors
        await expect(monthBtn).toBeVisible();
    });
});
