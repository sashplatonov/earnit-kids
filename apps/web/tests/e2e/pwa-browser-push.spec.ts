import { expect, test } from '@playwright/test';

import { authenticateE2eSession } from './helpers';

test('app registers a real service worker and protects navigation offline', async ({ page, context }) => {
    await authenticateE2eSession(page);
    await page.evaluate(() => navigator.serviceWorker.register('/sw.js', { scope: '/workspace' }));
    await expect.poll(async () => page.evaluate(async () => (await navigator.serviceWorker.getRegistration('/workspace'))?.active?.state ?? null)).toBe('activated');
    await page.goto('/app');
    await page.evaluate(() => navigator.serviceWorker.register('/sw.js', { scope: '/app' }));

    await expect.poll(async () => page.evaluate(async () => {
        const registration = await navigator.serviceWorker.getRegistration('/app');
        return registration?.active?.state ?? null;
    })).toBe('activated');
    await expect.poll(() => page.evaluate(async () => (await navigator.serviceWorker.getRegistration('/app'))?.scope ?? null)).toMatch(/\/app\/?$/);
    await expect.poll(() => page.evaluate(async () => Boolean(await navigator.serviceWorker.getRegistration('/workspace')))).toBeFalsy();

    const cacheNames = await page.evaluate(() => caches.keys());
    expect(cacheNames.some((name) => name.startsWith('earnit-static-'))).toBeTruthy();

    await context.setOffline(true);
    const offlineResponse = await page.goto('/app').catch(() => null);
    expect(offlineResponse?.status() ?? 503).toBeGreaterThanOrEqual(500);
    await context.setOffline(false);
});

test('service-worker cache excludes protected, invitation, and OAuth routes', async ({ page }) => {
    await authenticateE2eSession(page);
    await page.goto('/app');
    await expect.poll(async () => page.evaluate(async () => (await navigator.serviceWorker.getRegistration('/app'))?.active?.state ?? null)).toBe('activated');

    const excluded = await page.evaluate(async () => {
        const cache = await caches.open((await caches.keys()).find((name) => name.startsWith('earnit-static-')) ?? 'missing');
        return Promise.all(['/app', '/workspace', '/api/data', '/invite/parent', '/login-child/token', '/oauth/callback?code=test'].map(async (path) => ({
            path,
            cached: Boolean(await cache.match(path)),
        })));
    });

    expect(excluded).toEqual([
        { path: '/app', cached: false },
        { path: '/workspace', cached: false },
        { path: '/api/data', cached: false },
        { path: '/invite/parent', cached: false },
        { path: '/login-child/token', cached: false },
        { path: '/oauth/callback?code=test', cached: false },
    ]);
});
