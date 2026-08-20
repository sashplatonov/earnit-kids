import { expect, test } from '@playwright/test';
import { preserveTelegramFixture } from './telegramSdkFixture';

test.beforeEach(async ({ page }) => {
    await preserveTelegramFixture(page);
});

test('child Mini App keeps safe mobile geometry with multiple groups', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 568 });
    await page.addInitScript(() => {
        (window as Window & { Telegram?: unknown }).Telegram = { WebApp: { initData: 'signed-child-data', ready: () => {}, expand: () => {} } };
    });
    await page.route('**/api/telegram/auth/exchange', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ role: 'child', familyId: 'family-1' }) }));
    await page.route('**/api/base-data', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ tasks: [], products: [] }) }));
    await page.route('**/api/data/details**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ requests: [], history: [], friends: [] }) }));
    await page.route('**/api/data**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ isAdmin: false, balance: 1, childNickname: 'Mia', tasks: [{ id: 1, name: 'Read', coins: 2, groupName: 'Home', isActive: true }, { id: 2, name: 'A long task title that remains reachable on a narrow mobile viewport', coins: 2, groupName: 'School', isActive: true }, { id: 3, name: 'Pack', coins: 2, groupName: 'School', isActive: true }], shop: [], requests: [] }) }));
    await page.goto('/telegram');
    await expect(page.locator('.group-subnav .chip')).toHaveCount(3);
    const taskList = page.locator('section[aria-labelledby="child-tasks-title"] .list');
    await expect(taskList.locator('.row')).toHaveCount(3);
    await expect(taskList.getByText('A long task title that remains reachable on a narrow mobile viewport')).toBeVisible();
    expect(await taskList.evaluate((node) => {
        const rows = [...node.children];
        return rows.every((row) => row.classList.contains('row'))
            && rows.slice(0, -1).every((row) => getComputedStyle(row).borderBottomWidth === '1px')
            && rows.every((row) => getComputedStyle(row).backgroundColor === 'rgba(0, 0, 0, 0)');
    })).toBeTruthy();
    expect(await taskList.locator('.check').first().evaluate((node) => {
        const rect = node.getBoundingClientRect();
        return rect.width >= 44 && rect.height >= 44;
    })).toBeTruthy();
    expect(await page.getByRole('tablist').evaluate((node) => {
        const rect = node.getBoundingClientRect();
        return Math.round(rect.height) < 80 && Math.round(window.innerHeight - rect.bottom) === 0;
    })).toBeTruthy();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
    for (const button of await taskList.locator('.row-main, .check').all()) expect(await button.evaluate((node) => Math.min(node.getBoundingClientRect().width, node.getBoundingClientRect().height) >= 44)).toBeTruthy();
});
