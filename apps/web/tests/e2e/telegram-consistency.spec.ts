import { expect, test } from '@playwright/test';
import { preserveTelegramFixture } from './telegramSdkFixture';

test.beforeEach(async ({ page }) => {
    await preserveTelegramFixture(page);
});

test('Mini App reconciles stale child action from the server snapshot', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 700 });
    await page.addInitScript(() => {
        (window as Window & { Telegram?: unknown }).Telegram = { WebApp: { initData: 'signed-child-data', ready: () => {}, expand: () => {} } };
    });
    await page.route('**/api/telegram/auth/exchange', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ role: 'child', familyId: 'family-1' }) }));
    await page.route('**/api/data/details**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ requests: [], history: [], friends: [] }) }));
    await page.route('**/api/data**', (route) => {
        return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ isAdmin: false, childId: 10, balance: 10, childNickname: 'Mia', tasks: [{ id: 1, name: 'Read', coins: 5, isActive: true }], shop: [], requests: [] }) });
    });
    await page.route('**/api/tasks/1/request**', (route) => route.fulfill({ status: 409, contentType: 'application/json', body: JSON.stringify({ errorCode: 'STALE_STATE', detail: 'Task already requested' }) }));

    await page.goto('/telegram');
    await page.locator('section[aria-labelledby="child-tasks-title"] .check').first().click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await page.getByRole('button', { name: /Send request|Отправить заявку/ }).click();
    await expect(page.getByRole('alert')).toContainText(/changed|изменилось|Не удалось выполнить запрос/);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});
