import { expect, test } from '@playwright/test';
import { preserveTelegramFixture } from './telegramSdkFixture';

test.beforeEach(async ({ page }) => {
    await preserveTelegramFixture(page);
});

test('parent Mini App is server-role scoped and mobile-safe', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 568 });
    await page.addInitScript(() => {
        (window as Window & { Telegram?: unknown }).Telegram = {
            WebApp: { initData: 'signed-init-data', ready: () => {}, expand: () => {} },
        };
    });
    await page.route('**/api/telegram/auth/exchange', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ role: 'parent', familyId: 'family-1' }),
    }));
    await page.route('**/api/base-data', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ tasks: [], products: [] }),
    }));
    await page.route('**/api/data**', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
            isAdmin: true,
            balance: 42,
            childId: 10,
            children: [{ id: 10, nickname: 'Alex', balance: 42 }, { id: 11, nickname: 'Sam', balance: 8 }],
            tasks: [{ id: 1, name: 'Read', coins: 20, isActive: true }],
            shop: [{ id: 2, name: 'Game', price: 50, isActive: true }],
            requests: [],
        }),
    }));
    await page.route('**/api/requests/15/approve**', (route) => route.fulfill({
        status: 409,
        contentType: 'application/json',
        body: JSON.stringify({ detail: 'Already resolved' }),
    }));
    await page.route('**/api/requests/16/reject**', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({}),
    }));
    await page.route('**/api/data/details**', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ requests: [
            { id: 15, taskName: 'Read this very long task title without clipping', coins: 20, status: 'pending', childNickname: 'Alex' },
            { id: 16, itemName: 'Game reward', amount: 50, requestType: 'shop_purchase', status: 'pending', childNickname: 'Sam' },
        ], history: [], friends: [] }),
    }));
    await page.route('**/api/history?**', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [{ id: 21, type: 'earn', description: 'Read completed', amount: 20, createdAt: '2026-08-16T09:00:00Z' }], total: 1, page: 1, limit: 10 }),
    }));

    await page.goto('/telegram');

    await expect(page.getByRole('button', { name: /Switch child|Выбрать ребёнка/ })).toBeVisible();
    await expect(page.getByRole('tab', { name: /Tasks|Задания/ })).toBeVisible();
    await expect(page.getByRole('tab', { name: /Rewards|Награды/ })).toBeVisible();
    await expect(page.getByRole('tab', { name: /Family|Семья/ })).toBeVisible();
    await expect(page.getByRole('tab', { name: /Home|Главная/ })).toHaveAccessibleName(/Home|Главная.*\(2\)/);
    await page.getByRole('tab', { name: /Home|Главная/ }).focus();
    await page.keyboard.press('ArrowRight');
    await expect(page.getByRole('tab', { name: /Tasks|Задания/ })).toBeFocused();
    await expect(page.getByRole('tab', { name: /Tasks|Задания/ })).toHaveAttribute('aria-selected', 'true');
    await page.keyboard.press('Home');
    await expect(page.getByRole('tab', { name: /Home|Главная/ })).toBeFocused();
    await expect(page.getByRole('tab', { name: /Home|Главная/ })).toHaveAttribute('aria-selected', 'true');
    await page.getByRole('tab', { name: /Tasks|Задания/ }).press('End');
    await expect(page.getByRole('tab', { name: /Family|Семья/ })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('heading', { name: /Family|Семья/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /Add child|Добавить ребёнка/ })).toBeVisible();
    await page.getByRole('tab', { name: /Home|Главная/ }).click();
    const requestRows = page.locator('.home .list-surface').first().locator('.row');
    await expect(requestRows).toHaveCount(2);
    await expect(page.locator('.home .list-surface').first()).toHaveCSS('border-style', 'solid');
    await expect(requestRows.nth(0)).toHaveCSS('border-bottom-width', '1px');
    await expect(requestRows.nth(1)).toHaveCSS('border-bottom-width', '0px');
    const activityList = page.locator('.home .list-surface').nth(1);
    await expect(activityList).toBeVisible();
    await expect(activityList.locator('.row')).toHaveCount(1);
    await expect(activityList.getByRole('heading', { name: 'Read completed' })).toBeVisible();
    const approveButton = requestRows.nth(0).getByRole('button', { name: /Approve request|Одобрить заявку/ });
    const rejectButton = requestRows.nth(0).getByRole('button', { name: /Reject request|Отклонить заявку/ });
    await page.locator('body').click({ position: { x: 1, y: 1 } });
    for (let tab = 0; tab < 40; tab += 1) {
        if (await page.evaluate(() => document.activeElement?.getAttribute('aria-label')?.match(/Approve request|Одобрить заявку/))) break;
        await page.keyboard.press('Tab');
    }
    await expect(approveButton).toBeFocused();
    await expect(approveButton).toHaveCSS('outline-width', '3px');
    await expect(approveButton).toHaveCSS('outline-color', 'rgb(128, 170, 255)');
    await approveButton.press('Tab');
    await expect(rejectButton).toBeFocused();
    await expect(rejectButton).toHaveCSS('outline-width', '3px');
    await expect(rejectButton).toHaveCSS('outline-color', 'rgb(128, 170, 255)');
    await approveButton.click();
    await expect(page.getByRole('alert')).toContainText(/This request could not be updated|Не удалось обновить заявку/);
    await requestRows.nth(1).getByRole('button', { name: /Reject request|Отклонить заявку/ }).click();
    const mobileNav = await page.getByRole('tablist').evaluate((node) => {
        const style = getComputedStyle(node);
        const rect = node.getBoundingClientRect();
        return { position: style.position, bottom: Math.round(window.innerHeight - rect.bottom), width: Math.round(rect.width) };
    });
    expect(mobileNav).toEqual({ position: 'fixed', bottom: 0, width: 320 });
    expect(await page.locator('.parent-workspace').evaluate((node) => node.getBoundingClientRect().width)).toBeGreaterThan(300);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});
