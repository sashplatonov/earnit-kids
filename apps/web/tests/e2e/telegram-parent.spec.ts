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
    await page.route('**/api/data/details**', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ requests: [], history: [], friends: [] }),
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
            currentChildId: 10,
            children: [{ id: 10, nickname: 'Alex', balance: 42 }, { id: 11, nickname: 'Sam', balance: 8 }],
            tasks: [{ id: 1, name: 'Read', coins: 20, isActive: true }],
            shop: [{ id: 2, name: 'Game', price: 50, isActive: true }],
            requests: [],
        }),
    }));

    await page.goto('/telegram');

    await expect(page.getByRole('heading', { name: 'Family space' })).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Tasks' })).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Rewards' })).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Family' })).toBeVisible();
    await page.getByRole('tab', { name: 'Tasks' }).press('End');
    await expect(page.getByRole('tab', { name: 'Family' })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('heading', { name: 'Selected child' })).toBeVisible();
    const mobileNav = await page.getByRole('tablist').evaluate((node) => {
        const style = getComputedStyle(node);
        const rect = node.getBoundingClientRect();
        return { position: style.position, bottom: Math.round(window.innerHeight - rect.bottom), width: Math.round(rect.width) };
    });
    expect(mobileNav).toEqual({ position: 'fixed', bottom: 0, width: 320 });
    expect(await page.locator('.parent-workspace').evaluate((node) => node.getBoundingClientRect().width)).toBeGreaterThan(300);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});
