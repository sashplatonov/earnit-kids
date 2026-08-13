import { expect, test } from '@playwright/test';

test('non-Telegram browser gets a compact handoff state', async ({ page }) => {
    await page.goto('/telegram');

    await expect(page.getByRole('heading', { name: 'EarnIt Kids' })).toBeVisible();
    await expect(page.getByText('Open this page inside Telegram to continue.')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});

test('unlinked Telegram identity gets a safe parent-link and child-invitation handoff', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 568 });
    await page.addInitScript(() => {
        (window as Window & { Telegram?: unknown }).Telegram = {
            WebApp: { initData: 'signed-init-data', ready: () => {}, expand: () => {} },
        };
    });
    await page.route('**/api/telegram/auth/exchange', (route) => route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ errorCode: 'TELEGRAM_IDENTITY_UNLINKED' }),
    }));

    await page.goto('/telegram');

    await expect(page.getByText('This Telegram account is not linked to a family yet.')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Sign in as a parent to link it' })).toHaveAttribute('href', '/login');
    await expect(page.getByText('For a child account, ask a parent to send an invitation.')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});
