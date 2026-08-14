import { expect, test } from '@playwright/test';
import { preserveTelegramFixture } from './telegramSdkFixture';

test.beforeEach(async ({ page }) => {
    await preserveTelegramFixture(page);
});

test('Mini App reconciles stale child action from the server snapshot', async ({ page }) => {
    let familyDataLoads = 0;
    await page.setViewportSize({ width: 390, height: 700 });
    await page.addInitScript(() => {
        (window as Window & { Telegram?: unknown }).Telegram = { WebApp: { initData: 'signed-child-data', ready: () => {}, expand: () => {} } };
    });
    await page.route('**/api/telegram/auth/exchange', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ role: 'child', familyId: 'family-1' }) }));
    await page.route('**/api/data/details**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ requests: [], history: [], friends: [] }) }));
    await page.route('**/api/data**', (route) => {
        familyDataLoads += 1;
        return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ isAdmin: false, childId: 10, balance: 10, childNickname: 'Mia', tasks: [{ id: 1, name: 'Read', coins: 5, isActive: true }], shop: [], requests: [] }) });
    });
    await page.route('**/api/tasks/1/request', (route) => route.fulfill({ status: 409, contentType: 'application/json', body: JSON.stringify({ errorCode: 'STALE_STATE', detail: 'Task already requested' }) }));

    await page.goto('/telegram');
    await page.getByRole('button', { name: 'Request' }).click();
    const loadsBeforeSubmit = familyDataLoads;
    await page.getByRole('button', { name: 'Send request' }).click();
    await expect(page.getByRole('alert')).toContainText('changed');
    await expect.poll(() => familyDataLoads).toBeGreaterThan(loadsBeforeSubmit);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});
