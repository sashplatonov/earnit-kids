import { expect, test, type Page } from '@playwright/test';
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

async function mock(page: Page, role: 'parent' | 'child', data: unknown, width: number, details?: unknown) {
    await page.setViewportSize({ width, height: 667 });
    await page.addInitScript((roleArg) => {
        (window as Window & { Telegram?: unknown }).Telegram = { WebApp: { initData: `signed-${roleArg}-data`, ready: () => {}, expand: () => {} } };
    }, role);
    await page.route('**/api/telegram/auth/exchange', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ role, familyId: 'family-1' }) }));
    await page.route('**/api/base-data', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ tasks: [], products: [] }) }));
    await page.route('**/api/data**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(data) }));
    await page.route('**/api/data/details**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(details ?? { requests: [], history: [], friends: [] }) }));
    await page.route('**/api/history?**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [], total: 0, page: 1, limit: 10 }) }));
    await page.goto('/telegram');
}

test('no horizontal overflow at 320, 375 and 430 widths for parent and child', async ({ page }) => {
    for (const width of [320, 375, 430]) {
        await mock(page, 'parent', parentData, width);
        for (const tab of ['Home', 'Tasks', 'Rewards', 'Family']) {
            await page.getByRole('tab', { name: tab }).click();
            await page.waitForTimeout(150);
            expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
        }
        await mock(page, 'child', childData, width);
        for (const tab of ['Today', 'Rewards', 'Activity']) {
            await page.getByRole('tab', { name: tab }).click();
            await page.waitForTimeout(150);
            expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
        }
    }
});

test('every Mini App button meets the 44px minimum touch target', async ({ page }) => {
    await mock(page, 'parent', parentData, 375);
    for (const tab of ['Home', 'Tasks', 'Rewards', 'Family']) {
        await page.getByRole('tab', { name: tab }).click();
        await page.waitForTimeout(150);
        for (const button of await page.locator('button').all()) {
            const label = (await button.innerText().catch(() => '')) || (await button.getAttribute('aria-label').catch(() => '')) || '';
            const dims = await button.evaluate((node) => Math.min(node.getBoundingClientRect().width, node.getBoundingClientRect().height));
            expect(dims, `button below 44px: "${label}"`).toBeGreaterThanOrEqual(44);
        }
    }
    await mock(page, 'child', childData, 375);
    for (const tab of ['Today', 'Rewards', 'Activity']) {
        await page.getByRole('tab', { name: tab }).click();
        await page.waitForTimeout(150);
        for (const button of await page.locator('button').all()) {
            const label = (await button.innerText().catch(() => '')) || (await button.getAttribute('aria-label').catch(() => '')) || '';
            const dims = await button.evaluate((node) => Math.min(node.getBoundingClientRect().width, node.getBoundingClientRect().height));
            expect(dims, `button below 44px: "${label}"`).toBeGreaterThanOrEqual(44);
        }
    }
});

test('forbidden parent patterns are absent from the Mini App', async ({ page }) => {
    await mock(page, 'parent', parentData, 375, { requests: [{ id: 21, requestType: 'task_completion', taskName: 'Утренний старт', childNickname: 'Aliska', coins: 1, status: 'pending' }], history: [], friends: [] });
    await page.waitForTimeout(400);

    // Home must be a decision inbox: no duplicate Tasks/Rewards quick actions.
    await expect(page.getByRole('button', { name: 'Add coins' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'History' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Manage tasks' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Manage rewards' })).toHaveCount(0);

    // No redundant page title or catalog chrome.
    await expect(page.getByText('Family space', { exact: true })).toHaveCount(0);
    await expect(page.getByText('Manage catalog', { exact: true })).toHaveCount(0);

    // Tasks/Rewards must not use large per-row Edit buttons.
    await page.getByRole('tab', { name: 'Tasks' }).click();
    await page.waitForTimeout(150);
    await expect(page.getByText('Manage catalog', { exact: true })).toHaveCount(0);
    for (const button of await page.locator('button').all()) {
        const text = (await button.innerText().catch(() => '')) || '';
        expect(text.trim(), 'no large Edit button on task rows').not.toBe('Edit');
    }

    // Family invite must be collapsed by default.
    await page.getByRole('tab', { name: 'Family' }).click();
    await page.waitForTimeout(150);
    await expect(page.getByText('Create a sign-in link for this child.', { exact: true })).toHaveCount(0);
});
