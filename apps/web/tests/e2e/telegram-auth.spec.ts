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

    await expect(page.getByRole('button', { name: 'Switch child' })).toBeVisible();
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
    await expect(page.getByRole('button', { name: 'Switch child' })).toBeVisible();
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

test('a Telegram parent invitation is accepted with signed init data only', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 568 });
    await page.addInitScript(() => {
        (window as Window & { Telegram?: unknown }).Telegram = {
            WebApp: {
                initData: 'signed-parent-invite-data',
                initDataUnsafe: { start_param: 'pi_one-time-token' },
                ready: () => {},
                expand: () => {},
            },
        };
    });
    let acceptHit = false;
    await page.route('**/api/telegram/parents/invite/accept', async (route) => {
        acceptHit = true;
        expect(route.request().postDataJSON()).toEqual({
            token: 'pi_one-time-token',
            initData: 'signed-parent-invite-data',
        });
        await route.fulfill({ status: 204 });
    });
    await page.route('**/api/telegram/auth/exchange', (route) => {
        return route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ role: 'admin', familyId: 'family-1' }),
        });
    });

    await page.goto('/en/telegram');

    const acceptButton = page.getByRole('button', { name: /Accept invitation|Принять приглашение/ });
    await expect(acceptButton).toBeVisible();
    await expect(page.locator('input')).toHaveCount(0);
    await acceptButton.click();
    await expect.poll(() => acceptHit).toBe(true);
    await expect(page.getByRole('button', { name: 'Switch child' })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});

test('Telegram parent access creates a named invite and reloads the canonical Telegram identity', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 568 });
    await page.addInitScript(() => {
        (window as Window & { Telegram?: unknown }).Telegram = {
            WebApp: { initData: 'signed-admin-data', ready: () => {}, expand: () => {} },
        };
    });
    await page.route('**/api/telegram/auth/exchange', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ role: 'admin', familyId: 'family-1' }),
    }));
    await page.route('**/api/data', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ isAdmin: true, role: 'admin', familyId: 'family-1', children: [] }),
    }));
    await page.route('**/api/base-data', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ tasks: [], products: [] }),
    }));
    await page.route('**/api/parents', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([{
            id: 42,
            email: null,
            displayName: 'Maria Example',
            telegramUserId: 90000021,
            telegramUsername: 'maria_example',
            telegramDisplayName: 'Maria Example',
            permission: 'editor',
            status: 'active',
        }]),
    }));
    await page.route('**/api/telegram/parents/invite', async (route) => {
        expect(route.request().postDataJSON()).toEqual({ parentName: 'Maria Example' });
        await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ launchUrl: 'https://t.me/earnit_test_bot?startapp=pi_test' }),
        });
    });

    await page.goto('/telegram');
    const familyTab = page.getByRole('tab', { name: /Family|Семья/ });
    await expect(familyTab).toBeVisible();
    await familyTab.click();
    await page.getByRole('button', { name: /Parents|Родители/ }).click();
    await page.getByRole('button', { name: /Add parent|Добавить родителя/ }).click();
    await page.getByRole('button', { name: /By Telegram|Через Telegram/ }).click();
    await page.getByLabel(/Parent name|Имя родителя/).fill('Maria Example');
    await page.getByRole('button', { name: /Create link|Создать ссылку/ }).click();
    await expect(page.getByText(/Link ready|Ссылка готова/)).toBeVisible();

    await page.getByRole('button', { name: /Create new|Создать новую/ }).click();
    await page.getByRole('button', { name: /Cancel|Отмена/ }).click();
    await page.getByRole('button', { name: /Cancel|Отмена/ }).click();
    await page.getByRole('button', { name: /Close|Закрыть/ }).click();
    await expect(page.getByRole('dialog', { name: /Parents|Родители/ })).toHaveCount(0);
    await page.getByRole('button', { name: /Parents|Родители/ }).click();
    await expect(page.getByText('Maria Example').first()).toBeVisible();
    await expect(page.getByText('Maria Example')).toHaveCount(2);
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

    await expect(page.getByRole('button', { name: 'Switch child' })).toBeVisible();
});

test('a child-invite token is sent through the exchange without a parent self-link', async ({ page }) => {
    await page.addInitScript(() => {
        (window as Window & { Telegram?: unknown }).Telegram = {
            WebApp: {
                initData: 'signed-child-data',
                initDataUnsafe: { start_param: 'ci_abc123' },
                ready: () => {},
                expand: () => {},
            },
        };
    });
    let parentLinkHit = false;
    await page.route('**/api/telegram/account-connection/complete', async () => { parentLinkHit = true; });
    await page.route('**/api/telegram/auth/exchange', async (route) => {
        expect(route.request().postDataJSON()).toEqual({ initData: 'signed-child-data', token: 'ci_abc123' });
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ role: 'child', familyId: 'family-1' }) });
    });

    await page.goto('/telegram');

    await expect(page.locator('#child-tab-tasks')).toBeVisible();
    expect(parentLinkHit).toBe(false);
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

    await expect(page.getByRole('button', { name: 'Switch child' })).toBeVisible();
});
