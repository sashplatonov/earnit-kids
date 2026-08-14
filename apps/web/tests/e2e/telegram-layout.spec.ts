import { expect, test } from '@playwright/test';

test('child Mini App keeps safe mobile geometry with multiple groups', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 568 });
    await page.addInitScript(() => {
        (window as Window & { Telegram?: unknown }).Telegram = { WebApp: { initData: 'signed-child-data', ready: () => {}, expand: () => {} } };
    });
    await page.route('**/api/telegram/auth/exchange', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ role: 'child', familyId: 'family-1' }) }));
    await page.route('**/api/data/details**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ requests: [], history: [], friends: [] }) }));
    await page.route('**/api/data**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ isAdmin: false, balance: 1, childNickname: 'Mia', tasks: [{ id: 1, name: 'Read', coins: 2, groupName: 'Home', isActive: true }, { id: 2, name: 'Pack', coins: 2, groupName: 'School', isActive: true }], shop: [], requests: [] }) }));
    await page.goto('/telegram');
    await expect(page.locator('summary').filter({ hasText: 'Home' })).toBeVisible();
    await expect(page.locator('summary').filter({ hasText: 'School' })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
    for (const button of await page.locator('button').all()) expect(await button.evaluate((node) => Math.min(node.getBoundingClientRect().width, node.getBoundingClientRect().height) >= 44)).toBeTruthy();
});
