import { expect, test, type Browser, type Page, type Route } from '@playwright/test';

const publicPages = ['/', '/how.html', '/tasks.html', '/rewards.html', '/parents.html', '/faq.html', '/demo.html'];
const publicLocales = [
    { locale: 'en', prefix: '', title: ['Home', 'How it works', 'Tasks', 'Rewards', 'For parents', 'Questions', 'Parent demo'], body: 'Rewards' },
    { locale: 'ru', prefix: '/ru', title: ['Главная', 'Как работает', 'Задания', 'Награды', 'Для родителей', 'Вопросы', 'Демо для родителя'], body: 'Награды' },
] as const;

test.use({ locale: 'en-US' });

async function expectPublicMetadata(page: Page, locale: 'en' | 'ru', path: string, title: string, body: string) {
    const expectedPath = `${locale === 'ru' ? '/ru' : ''}${path}`;
    const publicOrigin = new URL(page.url()).origin;
    await expect(page).toHaveURL(new RegExp(`${expectedPath.replaceAll('/', '\\/')}$`));
    await expect(page.locator('html')).toHaveAttribute('lang', locale);
    await expect(page).toHaveTitle(new RegExp(`^${title} - EarnIt Kids$`));
    await expect(page.locator('meta[name="description"]')).toHaveAttribute('content', /.+/);
    await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', `${publicOrigin}${expectedPath}`);
    await expect(page.locator('link[rel="alternate"][hreflang="en"]')).toHaveAttribute('href', `${publicOrigin}${path}`);
    await expect(page.locator('link[rel="alternate"][hreflang="ru"]')).toHaveAttribute('href', `${publicOrigin}/ru${path}`);
    await expect(page.locator('link[rel="alternate"][hreflang="x-default"]')).toHaveAttribute('href', `${publicOrigin}${path}`);
    await expect(page.getByText(body, { exact: false }).first()).toBeVisible();
}

async function expectRawPublicDocument(browser: Browser, locale: 'en' | 'ru', path: string, title: string, body: string) {
    const context = await browser.newContext({
        javaScriptEnabled: false,
        extraHTTPHeaders: { 'Accept-Language': locale === 'ru' ? 'ru-RU' : 'en-US' },
    });
    const page = await context.newPage();
    try {
        await page.goto(`${locale === 'ru' ? '/ru' : ''}${path}`);
        await expectPublicMetadata(page, locale, path, title, body);
        await expect(page.locator('[data-language][aria-current="page"]')).toHaveCount(1);
        await expect(page.locator(`[data-language="${locale}"]`)).toHaveAttribute('aria-current', 'page');
        await expect(page.locator('script[src="/public/site.js"]')).toHaveCount(1);
    } finally {
        await context.close();
    }
}

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

    const appResponse = await page.goto('/ru/app');
    expect(appResponse?.status()).toBe(200);
    expect(appResponse?.headers()['permissions-policy']).toContain('microphone=()');

    const apiResponse = await request.get('/api/page-data/session');
    expect(apiResponse.headers()['content-security-policy']).toContain("object-src 'none'");
    expect(apiResponse.headers()['permissions-policy']).toContain('geolocation=()');
});

test('public pages keep both access choices usable at the compact mobile width', async ({ page }) => {
    await page.context().setExtraHTTPHeaders({ 'Accept-Language': 'en-US' });
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
        await expect(browserLinks.first()).toHaveAttribute('href', '/api/login-google/start?continue=%2Fapp');
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
        await expect(page.locator('[data-language]:visible').first()).toBeVisible();
    }
});

test('public pages stay in the canonical URL set and preserve language across navigation', async ({ page }) => {
    await page.context().setExtraHTTPHeaders({ 'Accept-Language': 'en-US' });
    await page.goto('/');
    await expect(page).toHaveURL('/');
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
    await expect(page).toHaveTitle('Home - EarnIt Kids');
    await expect(page.getByText('Tasks, coins and rewards - without notes and endless reminders.')).toBeVisible();
    const publicOrigin = new URL(page.url()).origin;
    await expect(page.locator('[data-language="en"]')).toHaveAttribute('href', `${publicOrigin}/`);
    await expect(page.locator('[data-language="ru"]')).toHaveAttribute('href', `${publicOrigin}/ru/`);

    await page.goto('/how.html');
    await expect(page).toHaveURL('/how.html');
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
    await expect(page).toHaveTitle('How it works - EarnIt Kids');

    await page.locator('[data-language="ru"]').click();
    await expect(page).toHaveURL('/ru/how.html');
    await expect(page.locator('html')).toHaveAttribute('lang', 'ru');
    await expect(page).toHaveTitle('Как работает - EarnIt Kids');
    await expect(page.getByRole('heading', { name: 'Четыре шага — и всё понятно' })).toBeVisible();
    await expect(page.locator('[data-language="ru"]')).toHaveAttribute('href', `${publicOrigin}/ru/how.html`);
    await expect(page.getByRole('link', { name: 'Задания', exact: true })).toHaveCount(1);
});

test('canonical public documents expose localized metadata before JavaScript runs', async ({ browser, request }) => {
    for (const { locale, prefix, title, body } of publicLocales) {
        for (const [index, path] of publicPages.entries()) {
            const urlPath = `${prefix}${path}`;
            const response = await request.get(urlPath, {
                headers: { 'Accept-Language': locale === 'ru' ? 'ru-RU' : 'en-US' },
            });
            expect(response.status()).toBe(200);
            const html = await response.text();
            expect(html).toContain(`<html lang="${locale}">`);
            expect(html).toContain(`<title>${title[index]} - EarnIt Kids</title>`);
            const publicOrigin = new URL(response.url()).origin;
            expect(html).toContain(`<link rel="canonical" href="${publicOrigin}${urlPath}">`);
            expect(html).toContain(`<link rel="alternate" hreflang="en" href="${publicOrigin}${path}">`);
            expect(html).toContain(`<link rel="alternate" hreflang="ru" href="${publicOrigin}/ru${path}">`);
            expect(html).toContain(`<link rel="alternate" hreflang="x-default" href="${publicOrigin}${path}">`);
            expect(html).toContain(body);
            expect(html).not.toMatch(/<link rel="canonical" href="\/public\//);
            expect(html).not.toMatch(/<link rel="alternate"[^>]+href="\/public\//);

            await expectRawPublicDocument(browser, locale, path, title[index], body);
        }
    }
});

test('legacy locale queries redirect to canonical public paths without loops', async ({ request }) => {
    for (const [source, target] of [
        ['/how.html?lang=ru&utm_source=mail', '/ru/how.html?utm_source=mail'],
        ['/ru/tasks.html?lang=en&utm_source=mail', '/tasks.html?utm_source=mail'],
        ['/?lang=ru', '/ru/'],
    ]) {
        const redirect = await request.get(source, { maxRedirects: 0 });
        expect(redirect.status()).toBe(308);
        expect(redirect.headers().location).toBe(target);
        expect((await request.get(target, { maxRedirects: 0 })).status()).toBe(200);
    }

    const unknownLocale = await request.get('/faq.html?lang=de', { maxRedirects: 0 });
    expect(unknownLocale.status()).toBe(200);
    expect(await unknownLocale.text()).toContain('<html lang="en">');
    expect(unknownLocale.headers().location).toBeUndefined();
});

test('Russian browser preference redirects every English public document once', async ({ page, request }) => {
    for (const publicPage of publicPages) {
        const redirect = await request.get(publicPage, {
            headers: { 'Accept-Language': 'ru-RU, en;q=0.8' },
            maxRedirects: 0,
        });
        expect(redirect.status()).toBe(308);
        expect(redirect.headers().location).toBe(`/ru${publicPage}`);
        expect(redirect.headers().vary).toBe('Accept-Language');

        await page.goto(`/ru${publicPage}`);
        await expectPublicMetadata(page, 'ru', publicPage, publicLocales[1].title[publicPages.indexOf(publicPage)], publicLocales[1].body);
    }

    const localized = await request.get('/ru/tasks.html', {
        headers: { 'Accept-Language': 'en-US, ru;q=0.8' },
        maxRedirects: 0,
    });
    expect(localized.status()).toBe(200);
    expect(localized.headers().location).toBeUndefined();
    expect(await localized.text()).toContain('<html lang="ru">');
});

test('browser language routing excludes protected routes and assets', async ({ request }) => {
    for (const path of ['/public/site.js', '/api/page-data/session', '/app', '/workspace']) {
        const response = await request.get(path, {
            headers: { 'Accept-Language': 'ru-RU' },
            maxRedirects: 0,
        });
        if (response.status() === 308) {
            expect(response.headers().location).not.toMatch(/^\/ru(?:\/|$)/);
        }
    }
});

test('language controls remain real, keyboard-accessible public links at 320px', async ({ page }) => {
    await page.context().setExtraHTTPHeaders({ 'Accept-Language': 'en-US' });
    await page.setViewportSize({ width: 320, height: 568 });

    for (const publicPage of publicPages) {
        await page.goto(publicPage);
        const languageLinks = page.locator('[data-language]');
        await expect(languageLinks).toHaveCount(2);
        await expect(page.locator('[data-language][aria-current="page"]')).toHaveCount(1);
        await expect(page.locator('[data-language="en"]')).toHaveAttribute('aria-current', 'page');
        const publicOrigin = new URL(page.url()).origin;
        for (const locale of ['en', 'ru']) {
            const link = languageLinks.filter({ hasText: locale.toUpperCase() });
            const expectedPath = locale === 'en' ? publicPage : `/ru${publicPage}`;
            await expect(link).toHaveAttribute('href', `${publicOrigin}${expectedPath}`);
            await expect(link).toHaveCSS('min-height', /^(44px|48px)$/);
            await link.focus();
            await expect(link).toBeFocused();
            await expect(link).not.toHaveCSS('outline-style', 'none');
        }
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
    }

    await page.goto('/tasks.html');
    await page.locator('[data-language="ru"]').click();
    await expect(page).toHaveURL('/ru/tasks.html');
    await expect(page.locator('html')).toHaveAttribute('lang', 'ru');
    await expect(page.getByRole('link', { name: 'Задания', exact: true })).toHaveCount(1);
});

test('English dynamic feedback stays English for Russian browser preferences', async ({ page }) => {
    await page.context().setExtraHTTPHeaders({ 'Accept-Language': 'ru-RU' });
    await page.goto('/?lang=en&error=oauth');
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
    await expect(page.locator('[role="status"]')).toHaveText('Google sign-in is temporarily unavailable. Use the browser sign-in link to try again.');
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
    await page.getByRole('link', { name: /Sign in|Войти/ }).first().click();
    const oauthRequestUrl = (await oauthRequest).url();

    expect(new URL(oauthRequestUrl).origin).toBe(publicOrigin);
    expect(oauthRequestUrl).toContain('/api/login-google/url?redirect_to=%2Fapp');
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
        const browserLink = page.getByRole('link', { name: /Sign in|Войти/ }).first();
        await browserLink.click();
        await expect(page.getByRole('status')).toContainText(/Google sign-in is temporarily unavailable|Вход через Google временно недоступен/);
        await expect(browserLink).toHaveAttribute('href', '/api/login-google/start?continue=%2Fapp');
        await browserLink.click();
        await expect(page.getByRole('status')).toContainText(/Google sign-in is temporarily unavailable|Вход через Google временно недоступен/);
    }
});

test('public parent demo is localized, read-only, and keeps authenticated destinations explicit', async ({ page }) => {
    const forbiddenRequests: string[] = [];
    page.on('request', (request) => {
        const pathname = new URL(request.url()).pathname;
        if (/^\/api\/(data|history|requests|shop)(?:\/|$)/.test(pathname) || /^\/api\/(?:approve|reject|purchase|delete|retry)(?:\/|$)/.test(pathname)) {
            forbiddenRequests.push(request.url());
        }
    });

    await page.goto('/demo.html');
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
    await expect(page.getByRole('heading', { name: 'Parent demo' })).toBeVisible();
    await expect(page.getByText(/sample data/i)).toBeVisible();
    await expect(page.getByRole('tab')).toHaveCount(4);
    await expect(page.locator('[role="tabpanel"]')).toHaveCount(4);
    await expect(page.getByRole('tab', { name: 'Tasks', exact: true })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('tabpanel', { name: 'Tasks' })).toBeVisible();

    const tabNames = [['Rewards shop', 'rewards'], ['History', 'history'], ['Requests', 'requests']] as const;
    for (const [name, id] of tabNames) {
        await page.getByRole('tab', { name, exact: true }).click();
        await expect(page).toHaveURL(new RegExp(`/demo.html\\?tab=${id}`));
        await expect(page.getByRole('tabpanel', { name })).toBeVisible();
    }
    await page.getByRole('tab', { name: 'Requests', exact: true }).press('Home');
    await expect(page.getByRole('tab', { name: 'Tasks', exact: true })).toBeFocused();
    await page.getByRole('tab', { name: 'Tasks', exact: true }).press('End');
    await expect(page.getByRole('tab', { name: 'Requests', exact: true })).toBeFocused();

    await page.goto('/demo.html?tab=not-a-tab');
    await expect(page.getByRole('tab', { name: 'Tasks', exact: true })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('tabpanel', { name: 'Tasks' })).toContainText('Learning');
    await expect(page).toHaveURL('/demo.html?tab=not-a-tab');
    await expect(page.locator('.demo-actions').getByRole('link', { name: /sign in/i })).toHaveAttribute('href', '/api/login-google/start?continue=%2Fapp');
    await expect(page.locator('.demo-actions').getByRole('link', { name: /open the rewards shop/i })).toHaveAttribute('href', '/app?context=rewards');
    expect(forbiddenRequests).toEqual([]);
});

test('Russian parent demo follows its document locale and preserves it in tab state', async ({ page }) => {
    await page.goto('/ru/demo.html?tab=requests');

    await expect(page.locator('html')).toHaveAttribute('lang', 'ru');
    await expect(page).toHaveTitle('Демо для родителя - EarnIt Kids');
    await expect(page.getByRole('heading', { name: 'Демо для родителя' })).toBeVisible();
    await expect(page.getByRole('tabpanel', { name: 'Запросы' }).getByText('Читать 15 минут')).toBeVisible();
    await page.getByRole('tab', { name: 'Задания', exact: true }).click();
    await expect(page.getByRole('tabpanel', { name: 'Задания' })).toContainText('Учёба');
    await expect(page.getByRole('tabpanel', { name: 'Задания' })).toContainText('Ежедневно');
    await page.getByRole('tab', { name: 'Награды', exact: true }).click();
    await expect(page.getByRole('tabpanel', { name: 'Награды' })).toContainText('Время вместе');
    await expect(page.getByRole('tabpanel', { name: 'Награды' })).toContainText('Доступно: Да');
    await expect(page.getByRole('tabpanel', { name: 'Награды' })).toContainText('Доступно: Нет');
    await expect(page.getByRole('tabpanel', { name: 'Награды' })).not.toContainText(/\b(Yes|No|Learning|Home|Daily|Weekdays|Weekly|Family time|Small joys)\b/);
    await page.getByRole('tab', { name: 'Запросы', exact: true }).click();
    await expect(page.getByRole('tab', { name: 'Запросы', exact: true })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('link', { name: /открыть магазин наград/i })).toHaveAttribute('href', '/ru/app?context=rewards');
    await page.getByRole('tab', { name: 'Задания', exact: true }).click();
    await expect(page).toHaveURL('/ru/demo.html?tab=tasks');
    await expect(page.getByRole('tabpanel', { name: 'Задания' }).getByText('Читать 15 минут')).toBeVisible();
});

test('parent demo stays accessible and compact at 320px', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 568 });
    await page.goto('/demo.html');

    const interactive = page.locator('.demo-tab, .demo-actions a');
    for (const element of await interactive.all()) {
        await expect(element).toHaveCSS('min-height', /^(44px|48px)$/);
    }
    const firstTab = page.getByRole('tab', { name: 'Tasks', exact: true });
    await firstTab.focus();
    await expect(firstTab).toBeFocused();
    await expect(firstTab).not.toHaveCSS('outline-style', 'none');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});

test('public native Google entry remains a real anchor when JavaScript is unavailable', async ({ page }) => {
    await page.goto('/');

    await expect(page.locator('[data-browser-workspace-link]').first())
        .toHaveAttribute('href', '/api/login-google/start?continue=%2Fapp');
});

test('unauthenticated localized app access preserves its local continuation', async ({ page }) => {
    for (const locale of ['en', 'ru']) {
        await page.goto(`/${locale}/app`);

        await expect(page).toHaveURL(new RegExp(`\\/?continue=%2F${locale}%2Fapp$`));
    }
});

test('legacy workspace URLs redirect once to app without rendering the application', async ({ request }) => {
    for (const [legacyPath, appPath] of [['/workspace?tab=tasks&from=mail', '/app?tab=tasks&from=mail'], ['/ru/workspace?tab=tasks&from=mail', '/ru/app?tab=tasks&from=mail']]) {
        const response = await request.get(legacyPath, { maxRedirects: 0 });
        expect(response.status()).toBe(308);
        expect(response.headers().location).toBe(appPath);
        expect(await response.text()).not.toContain('workspace-parent');
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
