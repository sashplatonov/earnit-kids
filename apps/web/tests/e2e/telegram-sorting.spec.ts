import { expect, test } from '@playwright/test';
import { preserveTelegramFixture } from './telegramSdkFixture';

test.beforeEach(async ({ page }) => {
    await preserveTelegramFixture(page);
    await page.addInitScript(() => {
        (window as Window & { Telegram?: unknown }).Telegram = {
            WebApp: { initData: 'signed-child-data', ready: () => {}, expand: () => {} },
        };
    });
    await page.route('**/api/telegram/auth/exchange', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ role: 'child', familyId: 'family-1' }),
    }));
    await page.route('**/api/base-data', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ tasks: [], products: [] }),
    }));
    await page.route('**/api/data/details**', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ requests: [], history: [], friends: [] }),
    }));
    await page.route('**/api/data**', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
            isAdmin: false,
            balance: 40,
            childNickname: 'Mia',
            tasks: [
                { id: 1, name: 'Read', coins: 20, groupName: 'Today', isActive: true },
                { id: 2, name: 'Clean', coins: 10, groupName: 'Today', isActive: true },
            ],
            shop: [],
            requests: [],
        }),
    }));
});

test('catalog sort choices are touch-sized and do not overflow narrow screens', async ({ page }) => {
    for (const width of [320, 390, 1280]) {
        await page.setViewportSize({ width, height: 568 });
        await page.goto('/telegram');

        const control = page.getByRole('group', { name: 'Сортировка списка' });
        const groups = control.getByRole('button', { name: 'Группы' });
        const coins = control.getByRole('button', { name: 'Монеты ↑' });
        await expect(control).toBeVisible();

        for (const button of [groups, coins]) {
            expect(await button.evaluate((node) => {
                const rect = node.getBoundingClientRect();
                return rect.width >= 44 && rect.height >= 44;
            })).toBeTruthy();
        }

        await coins.focus();
        await expect(coins).toHaveAttribute('aria-pressed', 'false');
        await expect(coins).toHaveCSS('outline-width', '3px');
        await expect(coins).toHaveCSS('outline-color', 'rgb(128, 170, 255)');
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
    }
});

async function expectRowTitles(list: import('@playwright/test').Locator, titles: string[]) {
    await expect(list).toBeVisible();
    await expect(list.locator(':scope > .entity-row .row-main .title')).toHaveText(titles);
}

async function expectCatalogSorting(page: import('@playwright/test').Page, lists: Array<{ list: import('@playwright/test').Locator; grouped: string[]; coins: string[] }>) {
    const control = page.getByRole('group', { name: /Сортировка списка|List sorting/ });
    const groups = control.getByRole('button', { name: /Группы|Groups/ });
    const coins = control.getByRole('button', { name: /Монеты ↑|Coins ↑/ });
    await expect(control).toBeVisible();
    await expect(groups).toHaveAttribute('aria-pressed', 'true');
    await expect(coins).toHaveAttribute('aria-pressed', 'false');
    for (const list of lists) await expectRowTitles(list.list, list.grouped);

    await coins.click();
    await expect(coins).toHaveAttribute('aria-pressed', 'true');
    await expect(groups).toHaveAttribute('aria-pressed', 'false');
    for (const list of lists) await expectRowTitles(list.list, list.coins);

    await groups.click();
    await expect(groups).toHaveAttribute('aria-pressed', 'true');
    await expect(coins).toHaveAttribute('aria-pressed', 'false');
    for (const list of lists) await expectRowTitles(list.list, list.grouped);
}

test('parent and child catalogs preserve grouped and ascending coin order', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 568 });
    await page.unroute('**/api/data**');
    await page.route('**/api/data**', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
            isAdmin: false,
            childId: 10,
            balance: 100,
            childNickname: 'Mia',
            children: [{ id: 10, nickname: 'Mia', balance: 100 }],
            tasks: [
                { id: 1, name: 'Task B high', coins: 80, groupName: 'School', isActive: true },
                { id: 2, name: 'Task A low', coins: 10, groupName: 'Home', isActive: true },
                { id: 3, name: 'Task A high', coins: 70, groupName: 'Home', isActive: true },
                { id: 4, name: 'Task B low', coins: 20, groupName: 'School', isActive: true },
            ],
            shop: [
                { id: 5, name: 'Reward B high', price: 90, groupName: 'School', isActive: true },
                { id: 6, name: 'Reward A low', price: 15, groupName: 'Home', isActive: true },
                { id: 7, name: 'Reward A high', price: 60, groupName: 'Home', isActive: true },
                { id: 8, name: 'Reward B low', price: 25, groupName: 'School', isActive: true },
            ],
            requests: [],
        }),
    }));
    await page.goto('/telegram');
    await expectCatalogSorting(page, [
        { list: page.locator('#child-panel-tasks .list-surface'), grouped: ['Task B low', 'Task B high', 'Task A low', 'Task A high'], coins: ['Task A low', 'Task B low', 'Task A high', 'Task B high'] },
    ]);
    await page.locator('#child-tab-rewards').click();
    await expectCatalogSorting(page, [
        { list: page.locator('#child-panel-rewards .list-surface'), grouped: ['Reward B low', 'Reward B high', 'Reward A low', 'Reward A high'], coins: ['Reward A low', 'Reward B low', 'Reward A high', 'Reward B high'] },
    ]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();

    await page.unroute('**/api/telegram/auth/exchange');
    await page.route('**/api/telegram/auth/exchange', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ role: 'parent', familyId: 'family-1' }),
    }));
    await page.unroute('**/api/data**');
    await page.route('**/api/data**', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
            isAdmin: true,
            childId: 10,
            balance: 100,
            children: [{ id: 10, nickname: 'Mia', balance: 100 }],
            tasks: [
                { id: 11, name: 'Parent task B high', coins: 80, groupName: 'School', isActive: true },
                { id: 12, name: 'Parent task A low', coins: 10, groupName: 'Home', isActive: true },
                { id: 13, name: 'Parent task A high', coins: 70, groupName: 'Home', isActive: true },
                { id: 14, name: 'Parent task B low', coins: 20, groupName: 'School', isActive: true },
            ],
            shop: [
                { id: 15, name: 'Parent reward B high', price: 90, groupName: 'School', isActive: true },
                { id: 16, name: 'Parent reward A low', price: 15, groupName: 'Home', isActive: true },
                { id: 17, name: 'Parent reward A high', price: 60, groupName: 'Home', isActive: true },
                { id: 18, name: 'Parent reward B low', price: 25, groupName: 'School', isActive: true },
            ],
            requests: [],
        }),
    }));
    await page.reload();
    await page.getByRole('tab', { name: /Tasks|Задания/ }).click();
    await expectCatalogSorting(page, [
        { list: page.locator('.tasks .list-surface'), grouped: ['Parent task B low', 'Parent task B high', 'Parent task A low', 'Parent task A high'], coins: ['Parent task A low', 'Parent task B low', 'Parent task A high', 'Parent task B high'] },
    ]);
    await page.getByRole('tab', { name: /Rewards|Награды/ }).click();
    await expectCatalogSorting(page, [
        { list: page.locator('.rewards .list-surface'), grouped: ['Parent reward B low', 'Parent reward B high', 'Parent reward A low', 'Parent reward A high'], coins: ['Parent reward A low', 'Parent reward B low', 'Parent reward A high', 'Parent reward B high'] },
    ]);
    for (const width of [320, 390, 1280]) {
        await page.setViewportSize({ width, height: 568 });
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
    }
});
