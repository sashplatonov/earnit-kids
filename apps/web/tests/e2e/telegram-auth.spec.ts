import { expect, test } from '@playwright/test';
import { preserveTelegramFixture } from './telegramSdkFixture';

test.beforeEach(async ({ page }) => {
    await preserveTelegramFixture(page);
});

test('the Telegram SDK loaded at the public root starts the Mini App auth flow', async ({ page }) => {
    await page.unroute('https://telegram.org/js/telegram-web-app.js');
    await page.route('https://telegram.org/js/telegram-web-app.js', (route) => route.fulfill({
        status: 200,
        contentType: 'application/javascript',
        body: `window.Telegram = { WebApp: {
            initData: 'sdk-parent-data',
            initDataUnsafe: { start_param: 'sdk-pairing-token' },
            ready: () => {},
            expand: () => {}
        } };`,
    }));
    await page.route('**/api/telegram/account-connection/complete', (route) => route.fulfill({ status: 204 }));
    await page.route('**/api/telegram/auth/exchange', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ role: 'admin', familyId: 'family-1' }),
    }));

    await page.goto('/?tgWebAppStartParam=sdk-pairing-token');

    await expect(page.getByRole('heading', { name: 'Family space' })).toBeVisible();
});

test('a Telegram Main Mini App launch from the public root is handed off to the auth gate', async ({ page }) => {
    await page.addInitScript(() => {
        (window as Window & { Telegram?: unknown }).Telegram = {
            WebApp: {
                initData: 'signed-parent-data',
                initDataUnsafe: { start_param: 'opaque-pairing-token' },
                ready: () => {},
                expand: () => {},
            },
        };
    });
    await page.route('**/api/telegram/account-connection/complete', (route) => route.fulfill({ status: 204 }));
    await page.route('**/api/telegram/auth/exchange', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ role: 'admin', familyId: 'family-1' }),
    }));

    await page.goto('/?tgWebAppStartParam=opaque-pairing-token');

    await expect(page).toHaveURL(/\/en\/telegram\?tgWebAppStartParam=opaque-pairing-token$/);
    await expect(page.getByRole('heading', { name: 'Family space' })).toBeVisible();
});

test('non-Telegram browser gets a compact handoff state', async ({ page }) => {
    await page.goto('/telegram');

    await expect(page.locator('script[src="https://telegram.org/js/telegram-web-app.js"]')).toHaveCount(1);
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

test('Telegram Mini App completes a one-time parent link before exchanging its session', async ({ page }) => {
    await page.addInitScript(() => {
        (window as Window & { Telegram?: unknown }).Telegram = {
            WebApp: {
                initData: 'signed-parent-data',
                initDataUnsafe: { start_param: 'opaque-pairing-token' },
                ready: () => {},
                expand: () => {},
            },
        };
    });
    await page.route('**/api/telegram/account-connection/complete', async (route) => {
        expect(route.request().postDataJSON()).toEqual({
            token: 'opaque-pairing-token',
            initData: 'signed-parent-data',
        });
        await route.fulfill({ status: 204 });
    });
    await page.route('**/api/telegram/auth/exchange', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ role: 'admin', familyId: 'family-1' }),
    }));

    await page.goto('/telegram');

    await expect(page.getByRole('heading', { name: 'Family space' })).toBeVisible();
});

test('a consumed pairing token still opens the Mini App for its now-linked Telegram user', async ({ page }) => {
    await page.addInitScript(() => {
        (window as Window & { Telegram?: unknown }).Telegram = {
            WebApp: {
                initData: 'signed-parent-data',
                initDataUnsafe: { start_param: 'consumed-pairing-token' },
                ready: () => {},
                expand: () => {},
            },
        };
    });
    await page.route('**/api/telegram/account-connection/complete', (route) => route.fulfill({
        status: 400,
        contentType: 'application/json',
        body: JSON.stringify({ errorCode: 'TELEGRAM_LINK_INVALID' }),
    }));
    await page.route('**/api/telegram/auth/exchange', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ role: 'admin', familyId: 'family-1' }),
    }));

    await page.goto('/telegram');

    await expect(page.getByRole('heading', { name: 'Family space' })).toBeVisible();
});
