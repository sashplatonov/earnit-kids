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

async function openParent(page: import('@playwright/test').Page, data: unknown = parentData) {
    await page.setViewportSize({ width: 375, height: 667 });
    await page.addInitScript(() => {
        (window as Window & { Telegram?: unknown }).Telegram = { WebApp: { initData: 'signed-init-data', ready: () => {}, expand: () => {} } };
    });
    await page.route('**/api/telegram/auth/exchange', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ role: 'parent', familyId: 'family-1' }) }));
    await page.route('**/api/base-data', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ tasks: [], products: [] }) }));
    await page.route('**/api/data**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(data) }));
    await page.route('**/api/data/details**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ requests: [], history: [], friends: [] }) }));
    await page.route('**/api/history?**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [], total: 0, page: 1, limit: 10 }) }));
    await page.goto('/telegram');
}

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
