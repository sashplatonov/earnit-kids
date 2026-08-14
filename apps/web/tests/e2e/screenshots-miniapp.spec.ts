import { expect, test } from '@playwright/test';
import { preserveTelegramFixture } from './telegramSdkFixture';

test.beforeEach(async ({ page }) => {
    await preserveTelegramFixture(page);
});

const parentData = {
    isAdmin: true,
    balance: 22,
    childId: 10,
    children: [{ id: 10, nickname: 'Aliska', balance: 22 }, { id: 11, nickname: 'Lizka', balance: 5 }],
    childNickname: 'Aliska',
    tasks: [
        { id: 1, name: 'Умыться, одеться и причесаться', coins: 1, groupName: 'Утро', isActive: true },
        { id: 2, name: 'Сделать одно дело по дому', coins: 2, groupName: 'Дом и порядок', isActive: true },
        { id: 3, name: 'Убрать свой стол или рабочее место', coins: 1, groupName: 'Дом и порядок', isActive: true },
        { id: 4, name: 'Разобрать одну зону со своими вещами', coins: 2, groupName: 'Дом и порядок', isActive: true },
        { id: 5, name: 'Книжная искра — 15 минут', coins: 2, groupName: 'Учёба', isActive: true },
    ],
    shop: [
        { id: 11, name: 'Выбрать настольную игру на вечер', price: 2, groupName: 'Время с семьёй', isActive: true },
        { id: 12, name: 'Поиграть с мамой или папой 20 минут', price: 4, groupName: 'Время с семьёй', isActive: true },
        { id: 13, name: 'Построить крепость из подушек', price: 6, groupName: 'Время с семьёй', isActive: true },
        { id: 14, name: 'Почитать с родителем', price: 4, groupName: 'Учёба', isActive: true },
        { id: 15, name: 'Домашняя лаборатория', price: 8, groupName: 'Учёба', isActive: true },
    ],
    requests: [],
};

async function openParent(page: import('@playwright/test').Page, data: unknown = parentData, details?: unknown, historyItems: unknown[] = []) {
    await page.setViewportSize({ width: 375, height: 667 });
    await page.addInitScript(() => {
        (window as Window & { Telegram?: unknown }).Telegram = { WebApp: { initData: 'signed-init-data', ready: () => {}, expand: () => {} } };
    });
    await page.route('**/api/telegram/auth/exchange', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ role: 'parent', familyId: 'family-1' }) }));
    await page.route('**/api/base-data', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ tasks: [], products: [] }) }));
    await page.route('**/api/data**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(data) }));
    await page.route('**/api/data/details**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(details ?? { requests: [], history: [], friends: [] }) }));
    await page.route('**/api/history?**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: historyItems, total: historyItems.length, page: 1, limit: 10 }) }));
    await page.goto('/telegram');
}

test('screenshot parent home zero requests at 375x667', async ({ page }) => {
    const history = [
        { id: 31, type: 'task_completed', title: 'Утренний старт', amount: 1, createdAt: '2026-08-14T08:32:00Z' },
        { id: 32, type: 'task_completed', title: 'Красивые 5 строк', amount: 1, createdAt: '2026-08-13T18:40:00Z' },
        { id: 33, type: 'task_completed', title: 'Книжная искра', amount: 2, createdAt: '2026-08-13T17:05:00Z' },
    ];
    await openParent(page, parentData, undefined, history);
    await page.waitForTimeout(600);
    await page.screenshot({ path: 'tmp/shot-parent-home-zero.png', fullPage: false });
    await expect(page.getByText('Nothing needs attention right now.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Add coins' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'History' })).toBeVisible();
    await expect(page.getByText('Утренний старт')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});

test('screenshot parent home pending requests at 375x667', async ({ page }) => {
    const pendingDetails = { requests: [{ id: 21, requestType: 'task_completion', taskName: 'Утренний старт', childNickname: 'Aliska', coins: 1, status: 'pending' }], history: [], friends: [] };
    await openParent(page, parentData, pendingDetails);
    await page.waitForTimeout(600);
    await page.screenshot({ path: 'tmp/shot-parent-home-pending.png', fullPage: false });
    await expect(page.getByRole('heading', { name: 'Needs attention' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Approve request' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Reject request' })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});

test('screenshot parent tasks at 375x667', async ({ page }) => {
    await openParent(page);
    await page.getByRole('tab', { name: 'Tasks' }).click();
    await page.waitForTimeout(600);
    await page.screenshot({ path: 'tmp/shot-parent-tasks.png', fullPage: false });
    await expect(page.locator('h1', { hasText: 'Tasks' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Edit Умыться, одеться и причесаться' })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});

test('screenshot parent rewards at 375x667', async ({ page }) => {
    await openParent(page);
    await page.getByRole('tab', { name: 'Rewards' }).click();
    await page.waitForTimeout(600);
    await page.screenshot({ path: 'tmp/shot-parent-rewards.png', fullPage: false });
    await expect(page.locator('h1', { hasText: 'Rewards' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Edit Выбрать настольную игру на вечер' })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});

test('screenshot parent family at 375x667', async ({ page }) => {
    await openParent(page);
    await page.getByRole('tab', { name: 'Family' }).click();
    await page.waitForTimeout(600);
    await page.screenshot({ path: 'tmp/shot-parent-family.png', fullPage: false });
    await expect(page.getByRole('heading', { name: 'Family', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Add child' })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});

const childData = {
    isAdmin: false,
    balance: 22,
    childNickname: 'Aliska',
    childId: 10,
    tasks: [
        { id: 1, name: 'Утренний старт', coins: 1, groupName: 'Утро', isActive: true, periodProgress: { period: 'day', completed: 0, pending: 0, limit: 1, remaining: 1, available: true } },
        { id: 2, name: 'Книжная искра — 15 минут', coins: 2, groupName: 'Учёба', isActive: true, periodProgress: { period: 'day', completed: 1, pending: 0, limit: 1, remaining: 0, available: false } },
        { id: 3, name: 'Красивые 5 строк', coins: 1, groupName: 'Учёба', isActive: true, periodProgress: { period: 'day', completed: 0, pending: 0, limit: 1, remaining: 1, available: true } },
    ],
    shop: [
        { id: 11, name: 'Королева настолки', price: 2, groupName: 'Игры', isActive: true },
        { id: 12, name: '20 минут только со мной', price: 4, groupName: 'Семья', isActive: true },
        { id: 13, name: 'Домашняя лаборатория', price: 30, groupName: 'Учёба', isActive: true },
    ],
    requests: [],
};

async function openChild(page: import('@playwright/test').Page, data: unknown = childData) {
    await page.setViewportSize({ width: 375, height: 667 });
    await page.addInitScript(() => {
        (window as Window & { Telegram?: unknown }).Telegram = { WebApp: { initData: 'signed-child-data', ready: () => {}, expand: () => {} } };
    });
    await page.route('**/api/telegram/auth/exchange', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ role: 'child', familyId: 'family-1' }) }));
    await page.route('**/api/base-data', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ tasks: [], products: [] }) }));
    await page.route('**/api/data**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(data) }));
    await page.route('**/api/data/details**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ requests: [], history: [], friends: [] }) }));
    await page.route('**/api/history?**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [], total: 0, page: 1, limit: 20 }) }));
    await page.goto('/telegram');
}

test('screenshot child today at 375x667', async ({ page }) => {
    await openChild(page);
    await page.waitForTimeout(600);
    await page.screenshot({ path: 'tmp/shot-child-today.png', fullPage: false });
    await expect(page.getByRole('heading', { name: 'Tasks · Today' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Done' }).first()).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});

test('screenshot child rewards at 375x667', async ({ page }) => {
    await openChild(page);
    await page.getByRole('tab', { name: 'Rewards' }).click();
    await page.waitForTimeout(600);
    await page.screenshot({ path: 'tmp/shot-child-rewards.png', fullPage: false });
    await expect(page.getByRole('heading', { name: 'Rewards' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Get reward' }).first()).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});
