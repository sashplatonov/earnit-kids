import { expect, test } from '@playwright/test';

test('child activity shows own requests and bounded history on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 568 });
    await page.addInitScript(() => {
        (window as Window & { Telegram?: unknown }).Telegram = { WebApp: { initData: 'signed-child-data', ready: () => {}, expand: () => {} } };
    });
    await page.route('**/api/telegram/auth/exchange', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ role: 'child', familyId: 'family-1' }) }));
    await page.route('**/api/data/details**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ requests: [{ id: 7, requestType: 'task_completion', taskName: 'Read', status: 'pending', coins: 20 }], history: [], friends: [] }) }));
    await page.route('**/api/data**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ isAdmin: false, balance: 40, childNickname: 'Mia', childId: 10, tasks: [], shop: [], requests: [] }) }));
    await page.route('**/api/history?**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [{ id: 3, title: 'Read', amount: 20, createdAt: '2026-08-13T10:00:00Z' }], total: 1, page: 1, limit: 20 }) }));

    await page.goto('/telegram');
    await page.getByRole('button', { name: 'Activity' }).click();
    await expect(page.getByRole('heading', { name: 'Requests' })).toBeVisible();
    await expect(page.getByText('Read')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Recent activity' })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});
