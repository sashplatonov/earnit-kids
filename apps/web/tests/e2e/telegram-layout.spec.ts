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
            && rows.every((row) => {
                const content = row.querySelector('.entity-content');
                return content != null && getComputedStyle(content).minWidth === '0px';
            })
            && rows.every((row) => [...row.querySelectorAll('.row-action')].every((action) => {
                const rect = action.getBoundingClientRect();
                return rect.width >= 44 && rect.height >= 44;
            }))
            && rows.every((row) => [...row.querySelectorAll('*')].every((child) => {
                const style = getComputedStyle(child);
                return !(style.borderStyle !== 'none' && style.borderRadius !== '0px' && style.backgroundColor === 'rgb(255, 255, 255)');
            }));
    })).toBeTruthy();
}

test('child Mini App keeps safe mobile geometry with multiple groups', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 568 });
    await page.addInitScript(() => {
        (window as Window & { Telegram?: unknown }).Telegram = { WebApp: { initData: 'signed-child-data', ready: () => {}, expand: () => {} } };
    });
    await page.route('**/api/telegram/auth/exchange', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ role: 'child', familyId: 'family-1' }) }));
    await page.route('**/api/base-data', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ tasks: [], products: [] }) }));
    await page.route('**/api/data/details**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ requests: [], history: [], friends: [] }) }));
    await page.route('**/api/history?**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [{ id: 9, type: 'earn', title: 'Read', amount: 2, createdAt: '2026-08-13T10:00:00Z' }], total: 1, page: 1, limit: 20 }) }));
    await page.route('**/api/data**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ isAdmin: false, childId: 10, balance: 10, childNickname: 'Mia', tasks: [{ id: 1, name: 'Read', coins: 2, groupName: 'Home', isActive: true }, { id: 2, name: 'A long task title that remains reachable on a narrow mobile viewport', coins: 2, groupName: 'School', isActive: true }, { id: 3, name: 'Pack', coins: 2, groupName: 'School', isActive: true }], shop: [{ id: 4, name: 'Ice cream', price: 5, groupName: 'Fun', isActive: true }], requests: [] }) }));
    await page.goto('/telegram');
    await expect(page.locator('.group-subnav .chip')).toHaveCount(3);
    const taskList = page.locator('section[aria-labelledby="child-tasks-title"] .list-surface');
    await expectCompactList(taskList, 3);
    await expect(taskList.getByText('A long task title that remains reachable on a narrow mobile viewport')).toBeVisible();
    expect(await taskList.evaluate((node) => {
        const rows = [...node.children];
        return rows.every((row) => row.classList.contains('entity-row'))
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
    for (const button of await taskList.locator('.row-action').all()) expect(await button.evaluate((node) => Math.min(node.getBoundingClientRect().width, node.getBoundingClientRect().height) >= 44)).toBeTruthy();
    await page.locator('#child-tab-rewards').click();
    await expect(page.locator('#child-panel-rewards')).toBeVisible();
    const rewardList = page.locator('section[aria-labelledby="child-rewards-title"] .list-surface');
    await expectCompactList(rewardList, 1);
    await expect(rewardList.getByText('Ice cream')).toBeVisible();
    await page.locator('#child-tab-tasks').click();
    const firstRowTrigger = taskList.locator('.row-main').first();
    await firstRowTrigger.focus();
    await page.keyboard.press('Tab');
    await page.keyboard.press('Shift+Tab');
    await expect(firstRowTrigger).toHaveCSS('outline-width', '3px');
    await expect(firstRowTrigger).toHaveCSS('outline-color', 'rgb(128, 170, 255)');
    await page.keyboard.press('Tab');
    await expect(taskList.locator('.check').first()).toBeFocused();
    await expect(taskList.locator('.check').first()).toHaveCSS('outline-width', '3px');
    await expect(taskList.locator('.check').first()).toHaveCSS('outline-color', 'rgb(128, 170, 255)');
    await page.locator('#child-tab-activity').click();
    await page.locator('#child-activity-tab-history').click();
    const activityTabs = page.locator('#child-activity-tab-history').locator('..');
    await expect(page.locator('#child-panel-activity .list-surface')).toBeVisible();
    const activityList = page.locator('#child-panel-activity .list-surface');
    await expectCompactList(activityList, 1);
    await expect(activityList.getByText('Read')).toBeVisible();
    expect(await activityTabs.evaluate((tabs) => {
        const list = tabs.parentElement?.querySelector('.list-surface');
        const marginBottom = Number.parseFloat(getComputedStyle(tabs).marginBottom);
        return list != null
            && marginBottom < 16
            && list.getBoundingClientRect().top - tabs.getBoundingClientRect().bottom < 20;
    })).toBeTruthy();
});
