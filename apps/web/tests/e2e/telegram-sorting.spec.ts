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
