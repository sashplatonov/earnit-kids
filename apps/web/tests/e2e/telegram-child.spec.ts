import { expect, test } from '@playwright/test';
import { preserveTelegramFixture } from './telegramSdkFixture';

test.beforeEach(async ({ page }) => {
    await preserveTelegramFixture(page);
});

test('child Mini App opens tasks first and requests a grouped task', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 568 });
    await page.addInitScript(() => {
        (window as Window & { Telegram?: unknown }).Telegram = { WebApp: { initData: 'signed-child-data', ready: () => {}, expand: () => {} } };
    });
    await page.route('**/api/telegram/auth/exchange', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ role: 'child', familyId: 'family-1' }) }));
    await page.route('**/api/base-data', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ tasks: [], products: [] }) }));
    await page.route('**/api/data/details**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ requests: [], history: [], friends: [] }) }));
    await page.route('**/api/data**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ isAdmin: false, balance: 40, childNickname: 'Mia', tasks: [{ id: 1, name: 'Read', coins: 20, groupName: 'Today', isActive: true }], shop: [{ id: 2, name: 'Game time', price: 30, groupName: 'Fun', isActive: true }], requests: [] }) }));
    await page.route('**/api/tasks/1/request', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ balance: 40, requests: [{ id: 7, taskId: 1, requestType: 'task_completion', status: 'pending' }] }) }));

    await page.goto('/telegram');
    await expect(page.getByRole('heading', { name: 'Hi, Mia!' })).toBeVisible();
    const taskTab = page.getByRole('tab', { name: 'Today' });
    await expect(taskTab).toHaveAttribute('aria-selected', 'true');
    await taskTab.press('End');
    await expect(page.getByRole('tab', { name: 'Activity' })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('tabpanel', { name: 'Activity' })).toBeVisible();
    await page.getByRole('tab', { name: 'Today' }).click();
    await expect(page.getByRole('heading', { name: 'Tasks · Today' })).toBeVisible();
    await page.getByRole('tab', { name: 'Rewards' }).click();
    await expect(page.getByRole('heading', { name: 'Rewards' })).toBeVisible();
    await page.getByRole('tab', { name: 'Today' }).click();
    await page.getByRole('button', { name: 'Done' }).first().click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page.locator('#request-note')).toBeFocused();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog')).toBeHidden();
    await expect(page.getByRole('button', { name: 'Done' }).first()).toBeFocused();
    await page.getByRole('button', { name: 'Done' }).first().click();
    await page.getByRole('button', { name: 'Send request' }).click();
    await expect(page.getByRole('status')).toContainText('Request sent');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});

test('child can cancel a pending request from Activity → Requests', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 568 });
    await page.addInitScript(() => {
        (window as Window & { Telegram?: unknown }).Telegram = { WebApp: { initData: 'signed-child-data', ready: () => {}, expand: () => {} } };
    });
    await page.route('**/api/telegram/auth/exchange', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ role: 'child', familyId: 'family-1' }) }));
    await page.route('**/api/base-data', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ tasks: [], products: [] }) }));
    await page.route('**/api/data/details**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ requests: [], history: [], friends: [] }) }));
    let cancelled = false;
    await page.route('**/api/data**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ isAdmin: false, balance: 40, childNickname: 'Mia', tasks: [], shop: [], requests: [{ id: 7, taskId: 1, taskName: 'Read', requestType: 'task_completion', coins: 20, status: cancelled ? 'cancelled' : 'pending', createdAt: '2026-08-16T09:00:00Z' }] }) }));
    await page.route('**/api/requests/7**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ok: true }) }));

    await page.goto('/telegram');
    await expect(page.getByRole('heading', { name: 'Привет, Mia!' })).toBeVisible();
    await page.getByRole('tab', { name: 'Активность' }).click();
    await page.getByRole('tab', { name: 'Заявки' }).click();
    await expect(page.getByRole('heading', { name: 'Мои заявки' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Отменить эту заявку' })).toBeVisible();
    await page.getByRole('button', { name: 'Отменить эту заявку' }).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    cancelled = true;
    await page.getByRole('button', { name: 'Отменить заявку' }).click();
    await expect(page.getByText('Отменено')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Отменить эту заявку' })).toHaveCount(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});

test('child requests deep-link opens Activity with Requests subsection selected', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 568 });
    await page.addInitScript(() => {
        (window as Window & { Telegram?: unknown }).Telegram = { WebApp: { initData: 'signed-child-data', ready: () => {}, expand: () => {} } };
    });
    await page.route('**/api/telegram/auth/exchange', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ role: 'child', familyId: 'family-1' }) }));
    await page.route('**/api/base-data', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ tasks: [], products: [] }) }));
    await page.route('**/api/data/details**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ requests: [], history: [], friends: [] }) }));
    await page.route('**/api/data**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ isAdmin: false, balance: 40, childNickname: 'Mia', tasks: [], shop: [], requests: [] }) }));

    await page.goto('/telegram?context=requests');
    await expect(page.getByRole('heading', { name: 'Привет, Mia!' })).toBeVisible();
    await expect(page.locator('#child-tab-activity')).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('tab', { name: 'Заявки' })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('heading', { name: 'Мои заявки' })).toBeVisible();
});
