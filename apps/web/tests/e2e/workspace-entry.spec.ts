import { expect, test, type Route } from '@playwright/test';

const publicPages = ['/', '/how.html', '/tasks.html', '/rewards.html', '/parents.html', '/faq.html'];

test('production entry points expose the browser security contract', async ({ page, request }) => {
    const publicResponse = await page.goto('/');
    const publicCsp = await page.locator('meta[http-equiv="Content-Security-Policy"]').getAttribute('content') ?? '';
    expect(publicCsp).toContain("default-src 'self'");
    expect(publicCsp).toContain("script-src 'self'");
    expect(publicCsp).not.toContain('*');
    expect(publicCsp).not.toContain('unsafe-inline');
    expect(publicResponse?.headers()['permissions-policy']).toContain('camera=()');
    expect(publicResponse?.headers()['x-content-type-options']).toBe('nosniff');
    expect(publicResponse?.headers()['x-frame-options']).toBe('DENY');
    expect(publicResponse?.headers()['referrer-policy']).toBe('no-referrer');

    const workspaceResponse = await page.goto('/ru/workspace');
    expect(workspaceResponse?.status()).toBe(200);
    expect(workspaceResponse?.headers()['permissions-policy']).toContain('microphone=()');

    const apiResponse = await request.get('/api/page-data/session');
    expect(apiResponse.headers()['content-security-policy']).toContain("object-src 'none'");
    expect(apiResponse.headers()['permissions-policy']).toContain('geolocation=()');
});

test('public pages keep both access choices usable at the compact mobile width', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 568 });
    await page.route('**/public/config.js', (route) => route.fulfill({
        status: 200,
        contentType: 'application/javascript',
        body: 'window.EARNIT_CONFIG = { telegramMiniAppUrl: "https://t.me/earnit_test_bot?startapp=public-entry" };',
    }));

    for (const publicPage of publicPages) {
        await page.goto(publicPage);

        const telegramLinks = page.locator('[data-miniapp-link]');
        const browserLinks = page.locator('[data-browser-workspace-link]');
        expect(await telegramLinks.count()).toBeGreaterThanOrEqual(2);
        expect(await browserLinks.count()).toBeGreaterThanOrEqual(2);
        for (const link of [...await telegramLinks.all(), ...await browserLinks.all()]) {
            await expect(link).toHaveCSS('min-height', /^(44px|48px)$/);
        }
        await expect(telegramLinks.first()).toHaveAttribute('href', 'https://t.me/earnit_test_bot?startapp=public-entry');
        await expect(browserLinks.first()).toHaveAttribute('href', '/api/login-google/start?continue=%2Fworkspace');
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
        await expect(page.locator('[data-language]:visible').first()).toBeVisible();
    }
});

test('public pages stay in the canonical URL set and preserve language across navigation', async ({ page }) => {
    await page.goto('/?lang=en');
    await expect(page).toHaveURL(/\/\?lang=en$/);
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
    await expect(page).toHaveTitle('Home - EarnIt Kids');
    await expect(page.getByText('Tasks, coins and rewards - without notes and endless reminders.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'EN' })).toHaveAttribute('aria-pressed', 'true');
    await expect(page.getByRole('button', { name: 'RU' })).toHaveAttribute('aria-pressed', 'false');

    await page.getByRole('link', { name: 'How it works' }).click();
    await expect(page).toHaveURL('/how.html?lang=en');
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
    await expect(page).toHaveTitle('How it works - EarnIt Kids');

    await page.getByRole('button', { name: 'RU' }).click();
    await expect(page).toHaveURL('/how.html?lang=ru');
    await expect(page.locator('html')).toHaveAttribute('lang', 'ru');
    await expect(page).toHaveTitle('Как работает - EarnIt Kids');
    await expect(page.getByText('Задания, монеты и награды - без записок и бесконечных напоминаний.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'RU' })).toHaveAttribute('aria-pressed', 'true');
    await expect(page.locator('a[href="/tasks.html?lang=ru"]')).toHaveCount(1);
});

test('public locale resolution uses Russian preference and English fallback', async ({ page }) => {
    await page.addInitScript(() => {
        Object.defineProperty(navigator, 'languages', { configurable: true, value: ['ru-RU'] });
        Object.defineProperty(navigator, 'language', { configurable: true, value: 'ru-RU' });
    });
    await page.goto('/tasks.html');
    await expect(page.locator('html')).toHaveAttribute('lang', 'ru');
    await expect(page).toHaveTitle('Задания - EarnIt Kids');
    await expect(page.getByRole('button', { name: 'RU' })).toHaveAttribute('aria-pressed', 'true');

    await page.addInitScript(() => {
        Object.defineProperty(navigator, 'languages', { configurable: true, value: ['de-DE'] });
        Object.defineProperty(navigator, 'language', { configurable: true, value: 'de-DE' });
    });
    await page.goto('/faq.html');
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
    await expect(page).toHaveTitle('Questions - EarnIt Kids');
    await expect(page.getByRole('button', { name: 'EN' })).toHaveAttribute('aria-pressed', 'true');
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

    await page.goto('/');
    const publicOrigin = new URL(page.url()).origin;
    const oauthRequest = page.waitForRequest('**/api/login-google/url**');
    await page.getByRole('link', { name: /Войти/ }).first().click();
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
        await page.goto('/');
        const browserLink = page.getByRole('link', { name: /Войти/ }).first();
        await browserLink.click();
        await expect(page.getByRole('status')).toContainText('Вход через Google временно недоступен');
        await expect(browserLink).toHaveAttribute('href', '/api/login-google/start?continue=%2Fworkspace');
        await browserLink.click();
        await expect(page.getByRole('status')).toContainText('Вход через Google временно недоступен');
    }
});

test('public native Google entry remains a real anchor when JavaScript is unavailable', async ({ page }) => {
    await page.goto('/');

    await expect(page.locator('[data-browser-workspace-link]').first())
        .toHaveAttribute('href', '/api/login-google/start?continue=%2Fworkspace');
});

test('unauthenticated localized workspace access preserves its local continuation', async ({ page }) => {
    for (const locale of ['en', 'ru']) {
        await page.goto(`/${locale}/workspace`);

        await expect(page).toHaveURL(new RegExp(`\\/?continue=%2F${locale}%2Fworkspace$`));
    }
});

test('legacy login routes stay absent from the production build', async ({ page }) => {
    for (const legacyRoute of ['/login', '/ru/login', '/login.html']) {
        const response = await page.goto(legacyRoute);

        expect(response?.status()).toBe(404);
        await expect(page.getByRole('heading', { name: /sign in|вход для родителей и детей/i })).toHaveCount(0);
        await expect(page.getByText(/Продолжить с Google|Continue with Google/)).toHaveCount(0);
    }
});
