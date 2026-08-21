import { expect, test } from '@playwright/test';
import { preserveTelegramFixture } from './telegramSdkFixture';

test.beforeEach(async ({ page }) => {
    await preserveTelegramFixture(page);
});

test('child activity shows child-readable history on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 568 });
    await page.addInitScript(() => {
        (window as Window & { Telegram?: unknown }).Telegram = { WebApp: { initData: 'signed-child-data', ready: () => {}, expand: () => {} } };
    });
    await page.route('**/api/telegram/auth/exchange', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ role: 'child', familyId: 'family-1' }) }));
    await page.route('**/api/data/details**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ requests: [], history: [], friends: [] }) }));
    await page.route('**/api/data**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ isAdmin: false, balance: 40, childNickname: 'Mia', childId: 10, tasks: [], shop: [], requests: [] }) }));
    await page.route('**/api/history?**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [{ id: 3, title: 'A very long activity title that remains readable on a narrow viewport', amount: 20, groupName: 'Morning', createdAt: '2026-08-13T10:00:00Z' }, { id: 4, title: 'Spent coins', amount: -7, type: 'spend', groupName: 'Personal', createdAt: '2026-08-12T10:00:00Z' }], total: 2, page: 1, limit: 20 }) }));

    await page.goto('/telegram');
    await page.getByRole('tab', { name: /Activity|Активность/ }).click();
    await expect(page.getByText('A very long activity title that remains readable on a narrow viewport')).toBeVisible();
    await expect(page.getByText('+20')).toBeVisible();
    await expect(page.getByText('-7')).toBeVisible();
    await expect(page.getByText('Morning')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Morning' })).toBeVisible();
    await expect(page.locator('section[aria-label="History"], section[aria-label="История"]')).toBeVisible();
    const list = page.locator('.list-surface');
    await expect(list.locator(':scope > .entity-row')).toHaveCount(2);
    await expect(list.locator('.history-time')).toHaveCount(2);
    await page.getByRole('button', { name: 'Morning' }).click();
    await expect(list.locator(':scope > .entity-row')).toHaveCount(1);
    await expect(page.getByText('A very long activity title that remains readable on a narrow viewport')).toBeVisible();
    await expect(page.getByText('Spent coins')).toBeHidden();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});
