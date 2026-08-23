import { expect, test } from '@playwright/test';

test('workspace registers a real service worker and protects navigation offline', async ({ page, context }) => {
    await page.goto('/workspace');

    await expect.poll(async () => page.evaluate(async () => {
        const registration = await navigator.serviceWorker.getRegistration('/workspace');
        return registration?.active?.state ?? null;
    })).toBe('activated');
    await expect.poll(() => page.evaluate(async () => (await navigator.serviceWorker.getRegistration('/workspace'))?.scope ?? null)).toMatch(/\/workspace$/);

    const cacheNames = await page.evaluate(() => caches.keys());
    expect(cacheNames.some((name) => name.startsWith('earnit-static-'))).toBeTruthy();

    await context.setOffline(true);
    const offlineResponse = await page.goto('/workspace').catch(() => null);
    expect(offlineResponse?.status() ?? 503).toBeGreaterThanOrEqual(500);
    await context.setOffline(false);
});

test('service-worker cache excludes protected, invitation, and OAuth routes', async ({ page }) => {
    await page.goto('/workspace');
    await expect.poll(async () => page.evaluate(async () => (await navigator.serviceWorker.getRegistration('/workspace'))?.active?.state ?? null)).toBe('activated');

    const excluded = await page.evaluate(async () => {
        const cache = await caches.open((await caches.keys()).find((name) => name.startsWith('earnit-static-')) ?? 'missing');
        return Promise.all(['/workspace', '/api/data', '/invite/parent', '/login-child/token', '/oauth/callback?code=test'].map(async (path) => ({
            path,
            cached: Boolean(await cache.match(path)),
        })));
    });

    expect(excluded).toEqual([
        { path: '/workspace', cached: false },
        { path: '/api/data', cached: false },
        { path: '/invite/parent', cached: false },
        { path: '/login-child/token', cached: false },
        { path: '/oauth/callback?code=test', cached: false },
    ]);
});
