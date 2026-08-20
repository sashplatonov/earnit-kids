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
    await page.route('**/api/data**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ isAdmin: false, balance: 40, childNickname: 'Mia', tasks: [{ id: 1, name: 'Read', coins: 20, groupName: 'Today', isActive: true }, { id: 2, name: 'A very long task title that must remain reachable on a narrow mobile viewport', coins: 10, groupName: 'Today', isActive: true }], shop: [{ id: 2, name: 'Game time', price: 30, groupName: 'Fun', isActive: true }], requests: [] }) }));
    await page.route('**/api/tasks/1/request', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ balance: 40, requests: [{ id: 7, taskId: 1, requestType: 'task_completion', status: 'pending' }] }) }));

    await page.goto('/telegram');
    await expect(page.getByRole('heading', { name: /(?:Hi|Привет), Mia!/ })).toBeVisible();
    const taskTab = page.locator('#child-tab-tasks');
    await expect(taskTab).toHaveAttribute('aria-selected', 'true');
    await taskTab.press('End');
    await expect(page.locator('#child-tab-activity')).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('tabpanel', { name: /Activity|Активность/ })).toBeVisible();
    await page.locator('#child-tab-tasks').click();
    const taskList = page.locator('section[aria-labelledby="child-tasks-title"] .list');
    await expect(taskList).toBeVisible();
    await expect(taskList.locator('.row')).toHaveCount(2);
    await expect(taskList.getByText('A very long task title that must remain reachable on a narrow mobile viewport')).toBeVisible();
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
    await page.locator('#child-tab-rewards').click();
    await expect(page.locator('#child-panel-rewards')).toBeVisible();
    await page.locator('#child-tab-tasks').click();
    await taskList.locator('.check').first().click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page.locator('#request-note')).toBeFocused();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog')).toBeHidden();
    await expect(taskList.locator('.check').first()).toBeFocused();
    await taskList.locator('.check').first().click();
    await page.getByRole('button', { name: /Send request|Отправить заявку/ }).click();
    await expect(page.getByRole('status')).toContainText(/Request sent|Заявка отправлена/);
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
