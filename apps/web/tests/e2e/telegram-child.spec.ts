import { expect, test } from '@playwright/test';
import { preserveTelegramFixture } from './telegramSdkFixture';

test.beforeEach(async ({ page }) => {
    await preserveTelegramFixture(page);
});

async function expectCompactList(list: import('@playwright/test').Locator, count: number) {
    await expect(list).toBeVisible();
    await expect(list.locator(':scope > .entity-row')).toHaveCount(count);
    expect(await list.evaluate((node) => {
        const rows = [...node.children];
        return rows.every((row) => row.classList.contains('entity-row'))
            && rows.slice(0, -1).every((row) => getComputedStyle(row).borderBottomWidth === '1px')
            && rows.every((row) => getComputedStyle(row).backgroundColor === 'rgba(0, 0, 0, 0)')
            && rows.every((row) => [...row.querySelectorAll('*')].every((child) => {
                const style = getComputedStyle(child);
                return !(style.borderStyle !== 'none' && style.borderRadius !== '0px' && style.backgroundColor === 'rgb(255, 255, 255)');
            }));
    })).toBeTruthy();
}

test('child Mini App opens tasks first and requests a grouped task', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 568 });
    await page.addInitScript(() => {
        (window as Window & { Telegram?: unknown }).Telegram = { WebApp: { initData: 'signed-child-data', ready: () => {}, expand: () => {} } };
    });
    await page.route('**/api/telegram/auth/exchange', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ role: 'child', familyId: 'family-1' }) }));
    await page.route('**/api/base-data', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ tasks: [], products: [] }) }));
    await page.route('**/api/data/details**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ requests: [], history: [], friends: [] }) }));
    await page.route('**/api/data**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ isAdmin: false, balance: 40, childNickname: 'Mia', tasks: [{ id: 1, name: 'Read', coins: 20, groupName: 'Today', isActive: true }, { id: 2, name: 'A very long task title that must remain reachable on a narrow mobile viewport', coins: 10, groupName: 'Today', isActive: true }], shop: [{ id: 2, name: 'Game time', price: 50, groupName: 'Fun', isActive: true }], requests: [] }) }));
    await page.route('**/api/tasks/2/request', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ balance: 40, requests: [{ id: 7, taskId: 2, requestType: 'task_completion', status: 'pending' }] }) }));

    await page.route(/\/api\/data\/details(?:\?|$)/, (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ requests: [], history: [], friends: [] }) }));
    await page.goto('/telegram');
    await expect(page.getByRole('heading', { name: /(?:Hi|Привет), Mia!/ })).toBeVisible();
    const taskTab = page.locator('#child-tab-tasks');
    await expect(taskTab).toHaveAttribute('aria-selected', 'true');
    await taskTab.press('ArrowRight');
    await expect(page.locator('#child-tab-rewards')).toBeFocused();
    await expect(page.locator('#child-tab-rewards')).toHaveAttribute('aria-selected', 'true');
    await page.keyboard.press('Home');
    await expect(taskTab).toBeFocused();
    await expect(taskTab).toHaveAttribute('aria-selected', 'true');
    await taskTab.press('End');
    await expect(page.locator('#child-tab-activity')).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('tabpanel', { name: /Activity|Активность/ })).toBeVisible();
    await page.locator('#child-tab-tasks').click();
    const taskList = page.locator('section[aria-labelledby="child-tasks-title"] .list-surface');
    await expect(taskList).toBeVisible();
    await expectCompactList(taskList, 2);
    await expect(taskList.getByText('A very long task title that must remain reachable on a narrow mobile viewport')).toBeVisible();
    await expect(taskList.getByText('Today')).toHaveCount(2);
    await expect(taskList.getByText(/never completed|ещё не выполнялось/i)).toHaveCount(2);
    expect(await taskList.evaluate((node) => {
        const rows = [...node.children];
        return rows.every((row) => row.classList.contains('entity-row'));
    })).toBeTruthy();
    expect(await taskList.locator('.check').first().evaluate((node) => {
        const rect = node.getBoundingClientRect();
        return rect.width >= 44 && rect.height >= 44;
    })).toBeTruthy();
    const rowTrigger = taskList.locator('.row-main').first();
    const requestButton = taskList.locator('.check').first();
    await rowTrigger.focus();
    await page.keyboard.press('Tab');
    await expect(requestButton).toBeFocused();
    await expect(requestButton).toHaveCSS('outline-width', '3px');
    await expect(requestButton).toHaveCSS('outline-color', 'rgb(128, 170, 255)');
    await page.keyboard.press('Shift+Tab');
    await expect(rowTrigger).toBeFocused();
    await expect(rowTrigger).toHaveCSS('outline-width', '3px');
    await expect(rowTrigger).toHaveCSS('outline-color', 'rgb(128, 170, 255)');
    await page.locator('#child-tab-rewards').click();
    await expect(page.locator('#child-panel-rewards')).toBeVisible();
    const rewardList = page.locator('section[aria-labelledby="child-rewards-title"] .list-surface');
    await expectCompactList(rewardList, 1);
    await expect(rewardList.getByText('Game time')).toBeVisible();
    await expect(rewardList.getByText('Fun')).toBeVisible();
    await expect(rewardList.locator('.grant')).toBeDisabled();
    await page.locator('#child-tab-tasks').click();
    await taskList.locator('.check').first().click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page.locator('#request-note')).toBeFocused();
    await page.keyboard.press('Shift+Tab');
    await expect(page.getByRole('button', { name: /Send request|Отправить заявку/ })).toBeFocused();
    await page.keyboard.press('Tab');
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
    await page.route('**/api/data**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ isAdmin: false, balance: 40, childNickname: 'Mia', tasks: [], shop: [], requests: [{ id: 7, taskId: 1, taskName: '🏠 A very long pending request title that remains readable', requestType: 'task_completion', coins: 20, status: cancelled ? 'cancelled' : 'pending', createdAt: '2026-08-16T09:00:00Z' }, { id: 8, itemId: 2, itemName: '🎁 Game time', requestType: 'shop_purchase', coins: 30, status: 'approved', createdAt: '2026-08-15T09:00:00Z' }] }) }));
    await page.route('**/api/requests/7**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ok: true }) }));

    await page.route(/\/api\/data\/details(?:\?|$)/, (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ requests: [], history: [], friends: [] }) }));
    await page.goto('/telegram');
    await expect(page.getByRole('heading', { name: /(?:Hi|Привет), Mia!/ })).toBeVisible();
    await page.getByRole('tab', { name: /Активность|Activity/ }).click();
    await page.getByRole('tab', { name: /Заявки|Requests/ }).click();
    await expect(page.locator('section[aria-label="Мои заявки"], section[aria-label="My requests"]')).toBeVisible();
    const requestList = page.locator('section[aria-label="Мои заявки"], section[aria-label="My requests"]').locator('.list-surface');
    await expect(requestList).toBeVisible();
    await expectCompactList(requestList, 2);
    await expect(requestList.getByRole('heading', { name: 'A very long pending request title that remains readable' })).toBeVisible();
    await expect(requestList.getByRole('heading', { name: 'Game time' })).toBeVisible();
    await expect(requestList.locator('.entity-emoji')).toHaveCount(0);
    await expect(requestList.locator('.entity-row').nth(0).locator('svg[aria-label="Заявка на задание"], svg[aria-label="Task request"]')).toBeVisible();
    await expect(requestList.locator('.entity-row').nth(1).locator('svg[aria-label="Заявка на награду"], svg[aria-label="Reward request"]')).toBeVisible();
    await expect(requestList.getByText(/Ожидает|Pending/)).toBeVisible();
    await expect(requestList.getByText('+20')).toBeVisible();
    await expect(requestList.getByText('-30')).toBeVisible();
    await expect(requestList.locator('time')).toHaveCount(2);
    expect(await requestList.evaluate((node) => getComputedStyle(node).borderTopWidth === '1px'
        && getComputedStyle(node).backgroundColor === 'rgb(255, 255, 255)')).toBeTruthy();
    await expect(page.getByRole('button', { name: /Отменить эту заявку|Cancel this request/ })).toBeVisible();
    expect(await page.getByRole('button', { name: /Отменить эту заявку|Cancel this request/ }).evaluate((node) => {
        const rect = node.getBoundingClientRect();
        return rect.width >= 44 && rect.height >= 44;
    })).toBeTruthy();
    await page.getByRole('button', { name: /Отменить эту заявку|Cancel this request/ }).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    cancelled = true;
    await page.getByRole('button', { name: /Отменить заявку|Cancel request/ }).click();
    await expect(page.getByText(/Отменено|Cancelled/)).toBeVisible();
    await expect(page.getByRole('button', { name: /Отменить эту заявку|Cancel this request/ })).toHaveCount(0);
    await expect(requestList.getByRole('heading', { name: 'Game time' })).toBeVisible();
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

    await page.route(/\/api\/data\/details(?:\?|$)/, (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ requests: [], history: [], friends: [] }) }));
    await page.goto('/telegram?context=requests');
    await expect(page.getByRole('heading', { name: /(?:Hi|Привет), Mia!/ })).toBeVisible();
    await expect(page.locator('#child-tab-activity')).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('tab', { name: /Заявки|Requests/ })).toHaveAttribute('aria-selected', 'true');
    await expect(page.locator('section[aria-label="Мои заявки"], section[aria-label="My requests"]')).toBeVisible();
});
