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
        for (const tab of [/Home|Главная/, /Tasks|Задания/, /Rewards|Награды/, /Family|Семья/]) {
            await page.getByRole('tab', { name: tab }).click();
            await page.waitForTimeout(150);
            expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
        }
        await mock(page, 'child', childData, width);
        for (const tab of [/Tasks|Задания/, /Rewards|Награды/, /Activity|Активность/]) {
            await page.getByRole('tab', { name: tab }).click();
            await page.waitForTimeout(150);
            expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
        }
    }
});

test('every Mini App button meets the 44px minimum touch target', async ({ page }) => {
    await mock(page, 'parent', parentData, 375);
    for (const tab of [/Home|Главная/, /Tasks|Задания/, /Rewards|Награды/, /Family|Семья/]) {
        await page.getByRole('tab', { name: tab }).click();
        await page.waitForTimeout(150);
        for (const button of await page.locator('button').all()) {
            const label = (await button.innerText().catch(() => '')) || (await button.getAttribute('aria-label').catch(() => '')) || '';
            const dims = await button.evaluate((node) => Math.min(node.getBoundingClientRect().width, node.getBoundingClientRect().height));
            expect(dims, `button below 44px: "${label}"`).toBeGreaterThanOrEqual(44);
        }
    }
    await mock(page, 'child', childData, 375);
    for (const tab of [/Tasks|Задания/, /Rewards|Награды/, /Activity|Активность/]) {
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
    await expect(page.getByRole('button', { name: /Add coins|Начислить/ })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Manage tasks' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Manage rewards' })).toHaveCount(0);

    // No redundant page title or catalog chrome.
    await expect(page.getByText('Family space', { exact: true })).toHaveCount(0);
    await expect(page.getByText('Manage catalog', { exact: true })).toHaveCount(0);

    // Tasks/Rewards must not use large per-row Edit buttons.
    await page.getByRole('tab', { name: /Tasks|Задания/ }).click();
    await page.waitForTimeout(150);
    await expect(page.getByText('Manage catalog', { exact: true })).toHaveCount(0);
    for (const button of await page.locator('button').all()) {
        const text = (await button.innerText().catch(() => '')) || '';
        expect(text.trim(), 'no large Edit button on task rows').not.toBe('Edit');
    }

    // Family invite must be collapsed by default.
    await page.getByRole('tab', { name: /Family|Семья/ }).click();
    await page.waitForTimeout(150);
    await expect(page.getByText('Create a sign-in link for this child.', { exact: true })).toHaveCount(0);
});

test('request and task lists keep one surface and usable geometry at narrow widths', async ({ page }) => {
    const parentRequests = [
        { id: 31, taskName: 'A very long task request title that must keep its available content width', requestType: 'task_completion', childNickname: 'Aliska', coins: 2, status: 'pending' },
        { id: 32, itemName: 'A reward request with a long readable title', requestType: 'shop_purchase', childNickname: 'Lizka', coins: 5, status: 'pending' },
    ];
    const childRequests = [
        { id: 41, taskName: '🏠 Clean room with a long title', requestType: 'task_completion', coins: 2, status: 'pending', createdAt: '2026-08-19T09:00:00Z' },
        { id: 42, itemName: '🎁 Family reward with a long title', requestType: 'shop_purchase', coins: 5, status: 'approved', createdAt: '2026-08-18T09:00:00Z' },
    ];
    const parentFixture = { ...parentData, requests: parentRequests };
    const childFixture = { ...childData, tasks: [
        ...childData.tasks,
        { id: 4, name: 'A very long child task title that must remain reachable', coins: 3, groupName: 'Дом и порядок', isActive: true, periodProgress: { period: 'day', completed: 0, pending: 0, limit: 1, remaining: 1, available: true } },
    ], requests: childRequests };

    for (const width of [320, 375, 430]) {
        await mock(page, 'parent', parentFixture, width, { requests: parentRequests, history: [], friends: [] });
        await page.getByRole('tab', { name: /Home|Главная/ }).click();
        const parentList = page.locator('section[aria-labelledby="parent-home-title"] .list-surface');
        await expect(parentList).toBeVisible();
        await expect(parentList.locator('.row')).toHaveCount(2);
        await page.keyboard.press('Tab');
        expect(await parentList.evaluate((node) => {
            const rows = [...node.children];
            const surface = getComputedStyle(node);
            return surface.backgroundColor === 'rgb(255, 255, 255)'
                && [surface.borderTopWidth, surface.borderRightWidth, surface.borderBottomWidth, surface.borderLeftWidth].every((width) => width === '1px')
                && [surface.borderTopLeftRadius, surface.borderTopRightRadius, surface.borderBottomRightRadius, surface.borderBottomLeftRadius].every((radius) => Number.parseFloat(radius) > 0)
                && rows.every((row) => {
                    const style = getComputedStyle(row);
                    return style.borderTopWidth === '0px'
                        && style.borderLeftWidth === '0px'
                        && style.borderRightWidth === '0px'
                        && [style.borderTopLeftRadius, style.borderTopRightRadius, style.borderBottomRightRadius, style.borderBottomLeftRadius].every((radius) => radius === '0px')
                        && style.backgroundColor === 'rgba(0, 0, 0, 0)';
                })
                && rows.slice(0, -1).every((row) => getComputedStyle(row).borderBottomWidth === '1px');
        })).toBeTruthy();
        for (const row of await parentList.locator('.row').all()) {
            const geometry = await row.evaluate((node) => {
                const rowRect = node.getBoundingClientRect();
                const mainRect = node.querySelector('.entity-content')!.getBoundingClientRect();
                const actionsRect = node.querySelector('.entity-actions')!.getBoundingClientRect();
                return { rowRect, mainRect, actionsRect };
            });
            expect(geometry.actionsRect.top).toBeGreaterThanOrEqual(geometry.rowRect.top);
            expect(geometry.actionsRect.bottom).toBeLessThanOrEqual(geometry.rowRect.bottom);
            expect(geometry.mainRect.left).toBeGreaterThanOrEqual(geometry.rowRect.left);
            expect(geometry.mainRect.right).toBeLessThanOrEqual(geometry.rowRect.right);
            for (const action of await row.locator('.attention-actions button').all()) {
                await action.focus();
                await expect(action).toBeFocused();
                await expect(action).toHaveCSS('outline-width', '3px');
                await expect(action).toHaveCSS('outline-color', 'rgb(128, 170, 255)');
            }
        }
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();

        await mock(page, 'child', { ...childFixture, requests: [] }, width, { requests: childRequests, history: [], friends: [] });
        await page.getByRole('tab', { name: /Tasks|Задания/ }).click();
        const taskList = page.locator('section[aria-labelledby="child-tasks-title"] .list-surface');
        await expect(taskList).toBeVisible();
        await expect(taskList.locator('.row')).toHaveCount(4);
        expect(await taskList.evaluate((node) => {
            const rows = [...node.children];
            const surface = getComputedStyle(node);
            return [surface.borderTopWidth, surface.borderRightWidth, surface.borderBottomWidth, surface.borderLeftWidth].every((width) => width === '1px')
                && [surface.borderTopLeftRadius, surface.borderTopRightRadius, surface.borderBottomRightRadius, surface.borderBottomLeftRadius].every((radius) => Number.parseFloat(radius) > 0)
                && rows.every((row) => {
                    const style = getComputedStyle(row);
                    return style.backgroundColor === 'rgba(0, 0, 0, 0)'
                        && style.borderTopWidth === '0px'
                        && style.borderLeftWidth === '0px'
                        && style.borderRightWidth === '0px'
                        && [style.borderTopLeftRadius, style.borderTopRightRadius, style.borderBottomRightRadius, style.borderBottomLeftRadius].every((radius) => radius === '0px');
                })
                && rows.slice(0, -1).every((row) => getComputedStyle(row).borderBottomWidth === '1px');
        })).toBeTruthy();
        await expect(taskList.getByText('A very long child task title that must remain reachable')).toBeVisible();
        await page.keyboard.press('Tab');
        expect(await taskList.locator('.check').last().evaluate((node) => {
            const rect = node.getBoundingClientRect();
            return rect.width >= 44 && rect.height >= 44;
        })).toBeTruthy();
        await taskList.locator('.row-main').first().focus();
        await expect(taskList.locator('.row-main').first()).toHaveCSS('outline-width', '3px');
        await expect(taskList.locator('.row-main').first()).toHaveCSS('outline-color', 'rgb(128, 170, 255)');
        await taskList.locator('.check').last().focus();
        await expect(taskList.locator('.check').last()).toHaveCSS('outline-width', '3px');
        await expect(taskList.locator('.check').last()).toHaveCSS('outline-color', 'rgb(128, 170, 255)');

        await page.getByRole('tab', { name: /Activity|Активность/ }).click();
        await page.getByRole('tab', { name: /Requests|Заявки/ }).click();
        const requestList = page.locator('section[aria-label="Мои заявки"] .list-surface, section[aria-label="My requests"] .list-surface');
        await expect(requestList).toBeVisible();
        await expect(requestList.locator('.row')).toHaveCount(2);
        await expect(requestList.locator('.entity-emoji')).toHaveCount(0);
        await expect(requestList.locator('.row').first().getByLabel(/Task request|Заявка на задание/)).toBeVisible();
        await expect(requestList.locator('.row').nth(1).getByLabel(/Reward request|Заявка на награду/)).toBeVisible();
        const cancelButton = requestList.locator('.cancel').first();
        await expect(cancelButton).toBeVisible();
        expect(await cancelButton.evaluate((node) => {
            const row = node.closest('.row')!.getBoundingClientRect();
            const actions = node.closest('.entity-actions')!.getBoundingClientRect();
            const button = node.getBoundingClientRect();
            return button.width >= 44
                && button.height >= 44
                && button.top >= actions.top
                && button.bottom <= row.bottom;
        })).toBeTruthy();
        expect(await requestList.evaluate((node) => {
            const rows = [...node.children];
            const surface = getComputedStyle(node);
            return surface.backgroundColor === 'rgb(255, 255, 255)'
                && [surface.borderTopWidth, surface.borderRightWidth, surface.borderBottomWidth, surface.borderLeftWidth].every((width) => width === '1px')
                && [surface.borderTopLeftRadius, surface.borderTopRightRadius, surface.borderBottomRightRadius, surface.borderBottomLeftRadius].every((radius) => Number.parseFloat(radius) > 0)
                && rows.every((row) => {
                    const style = getComputedStyle(row);
                    return style.backgroundColor === 'rgba(0, 0, 0, 0)'
                        && style.borderTopWidth === '0px'
                        && style.borderLeftWidth === '0px'
                        && style.borderRightWidth === '0px'
                        && [style.borderTopLeftRadius, style.borderTopRightRadius, style.borderBottomRightRadius, style.borderBottomLeftRadius].every((radius) => radius === '0px');
                })
                && rows.slice(0, -1).every((row) => getComputedStyle(row).borderBottomWidth === '1px');
        })).toBeTruthy();
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
    }
});
