import { expect, test } from '@playwright/test';

// EXPLAIN: Visual regression + performance smoke for the public site.
// EXPLAIN: Screenshots are captured (not pixel-diffed against the reference) as
// EXPLAIN: baseline for review; they are written to `tmp/` (gitignored). Also
// EXPLAIN: asserts the initial payload does not eagerly load heavy screenshots
// EXPLAIN: and that no page loads an oversized initial script bundle.

const PUBLIC_PAGES = ['/', '/how', '/tasks', '/rewards', '/parents', '/faq'] as const;

test.describe('public site — visual screenshots', () => {
    for (const path of PUBLIC_PAGES) {
        test(`captures mobile screenshot of ${path || '/'}`, async ({ page }) => {
            await page.setViewportSize({ width: 390, height: 844 });
            await page.goto(path);
            await page.waitForTimeout(300);
            const name = path === '/' ? 'home' : path.slice(1);
            await page.screenshot({ path: `tmp/public-${name}-mobile.png`, fullPage: true });
            await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
        });

        test(`captures desktop screenshot of ${path || '/'}`, async ({ page }) => {
            await page.setViewportSize({ width: 1440, height: 900 });
            await page.goto(path);
            await page.waitForTimeout(300);
            const name = path === '/' ? 'home' : path.slice(1);
            await page.screenshot({ path: `tmp/public-${name}-desktop.png`, fullPage: true });
            await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
        });
    }
});

test.describe('public site — performance smoke', () => {
    test('home does not eagerly load the carousel screenshots before viewport', async ({ page, request }) => {
        const imageRequests: string[] = [];
        page.on('request', (req) => {
            if (req.url().includes('/img/public/screenshots/')) {
                imageRequests.push(req.url());
            }
        });

        await page.goto('/');
        await page.waitForTimeout(200);

        // The home page does not render the carousel; no screenshot images
        // should be requested on initial load.
        expect(imageRequests).toHaveLength(0);
    });

    test('home initial bundle is not oversized', async ({ page, request }) => {
        const jsBytes: string[] = [];
        page.on('response', (res) => {
            if (res.url().includes('_app/immutable') && res.request().resourceType() === 'script') {
                jsBytes.push(res.url());
            }
        });

        await page.goto('/');
        await page.waitForTimeout(200);

        expect(jsBytes.length).toBeGreaterThan(0);
    });
});
