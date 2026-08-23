import { expect, test, type Route } from '@playwright/test';

const publicPages = ['index.html', 'how.html', 'tasks.html', 'rewards.html', 'parents.html', 'faq.html'];

test('public pages keep both access choices usable at the compact mobile width', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 568 });
    await page.route('**/public/config.js', (route) => route.fulfill({
        status: 200,
        contentType: 'application/javascript',
        body: 'window.EARNIT_CONFIG = { telegramMiniAppUrl: "https://t.me/earnit_test_bot?startapp=public-entry" };',
    }));

    for (const publicPage of publicPages) {
        await page.goto(`/public/${publicPage}`);

        const telegramLinks = page.locator('[data-miniapp-link]');
        const browserLinks = page.locator('[data-browser-workspace-link]');
        expect(await telegramLinks.count()).toBeGreaterThanOrEqual(2);
        expect(await browserLinks.count()).toBeGreaterThanOrEqual(2);
        for (const link of [...await telegramLinks.all(), ...await browserLinks.all()]) {
            await expect(link).toHaveCSS('min-height', /^(44px|48px)$/);
        }
        await expect(telegramLinks.first()).toHaveAttribute('href', 'https://t.me/earnit_test_bot?startapp=public-entry');
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
    }
});

test('public Google entry uses same-origin startup and preserves the local workspace target', async ({ page }) => {
    await page.route('**/public/config.js', (route) => route.fulfill({
        status: 200,
        contentType: 'application/javascript',
        body: 'window.EARNIT_CONFIG = { telegramMiniAppUrl: "https://t.me/earnit_test_bot?startapp=public-entry" };',
    }));
    await page.route('**/api/auth-config', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ googleEnabled: true }),
    }));
    await page.route('**/api/login-google/url**', async (route) => {
        await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ url: 'https://accounts.google.com/o/oauth2/auth?state=signed-test-state' }),
        });
    });
    await page.route('https://accounts.google.com/**', (route) => route.fulfill({ status: 200, body: 'OAuth stub' }));

    await page.goto('/public/index.html');
    const publicOrigin = new URL(page.url()).origin;
    const oauthRequest = page.waitForRequest('**/api/login-google/url**');
    await page.getByRole('link', { name: 'Продолжить с Google' }).first().click();
    const oauthRequestUrl = (await oauthRequest).url();

    expect(new URL(oauthRequestUrl).origin).toBe(publicOrigin);
    expect(oauthRequestUrl).toContain('/api/login-google/url?redirect_to=%2Fworkspace');
    await expect(page).toHaveURL(/accounts\.google\.com\/o\/oauth2\/auth\?state=signed-test-state/);
});

test('public Google entry keeps its local fallback for disabled, failed, and invalid startup', async ({ page }) => {
    const cases = [
        { configure: (route: Route) => route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ googleEnabled: false }),
        }) },
        { configure: (route: Route) => route.abort() },
        { configure: (route: Route) => route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ googleEnabled: true }),
        }), oauth: (route: Route) => route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ url: 'not-a-usable-url' }),
        }) },
    ];

    let currentCase = cases[0];
    await page.route('**/api/auth-config', (route) => currentCase.configure(route));
    await page.route('**/api/login-google/url**', (route) => currentCase.oauth?.(route) ?? route.abort());

    for (const testCase of cases) {
        currentCase = testCase;
        await page.goto('/public/index.html');
        const browserLink = page.getByRole('link', { name: 'Продолжить с Google' }).first();
        await browserLink.click();
        await expect(page.getByRole('status')).toContainText('Вход через Google временно недоступен');
        await expect(browserLink).toHaveAttribute('href', '/login?continue=%2Fworkspace');
        await browserLink.click();
        await expect(page).toHaveURL(/\/login\?continue=%2Fworkspace$/);
    }
});

test('unauthenticated workspace access stays on a local login continuation', async ({ page }) => {
    await page.goto('/workspace');

    await expect(page).toHaveURL(/\/(?:en|ru)\/login\?continue=%2F(?:en%2F|ru%2F)?workspace$/);
});
