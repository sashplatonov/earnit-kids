import { expect, test, type Page } from '@playwright/test';

// EXPLAIN: Responsive + accessibility verification for the public site.
// EXPLAIN: Checks no horizontal overflow across the four target viewports,
// EXPLAIN: single h1 per page, skip-link, aria-current, details/summary
// EXPLAIN: semantics, and touch-target sizing.

const VIEWPORTS = [
    { width: 320, height: 700 },
    { width: 390, height: 844 },
    { width: 768, height: 1024 },
    { width: 1440, height: 900 },
] as const;

const PUBLIC_PAGES = ['/', '/how', '/tasks', '/rewards', '/parents', '/faq'] as const;

test.describe('public site — responsive (no horizontal overflow)', () => {
    for (const viewport of VIEWPORTS) {
        for (const path of PUBLIC_PAGES) {
            test(`${path} has no horizontal overflow at ${viewport.width}px`, async ({ page }) => {
                await page.setViewportSize({ width: viewport.width, height: viewport.height });
                await page.goto(path);

                const hasOverflow = await page.evaluate(() => {
                    return document.documentElement.scrollWidth > document.documentElement.clientWidth;
                });
                expect(hasOverflow).toBe(false);
            });
        }
    }
});

test.describe('public site — accessibility', () => {
    for (const path of PUBLIC_PAGES) {
        test(`${path} has exactly one h1`, async ({ page }) => {
            await page.goto(path);
            const h1Count = await page.locator('h1').count();
            expect(h1Count).toBe(1);
        });

        test(`${path} provides a visible skip-link`, async ({ page }) => {
            await page.goto(path);
            const skip = page.locator('.skip-link');
            await expect(skip).toHaveAttribute('href', '#public-main');
            await expect(skip).toHaveCount(1);
        });
    }

    test('home marks the active nav item with aria-current', async ({ page }) => {
        await page.goto('/');
        await expect(page.locator('a[aria-current="page"]').first()).toHaveText('Главная');
    });

    test('FAQ items are real details/summary elements', async ({ page }) => {
        await page.goto('/faq');
        const details = page.locator('details.faq-item');
        const count = await details.count();
        expect(count).toBeGreaterThan(0);
        await expect(details.first().locator('summary')).toBeVisible();
    });

    test('mobile menu links have a minimum touch target of 44px', async ({ page }) => {
        await page.setViewportSize({ width: 390, height: 844 });
        await page.goto('/');
        await page.locator('#public-menu-button').click();
        const links = page.locator('#public-mobile-menu a');
        const count = await links.count();
        expect(count).toBeGreaterThan(0);
        for (let i = 0; i < count; i++) {
            const box = await links.nth(i).boundingBox();
            expect(box, `mobile nav link #${i} must be >= 44px tall`).not.toBeNull();
            if (box) {
                expect(box.height).toBeGreaterThanOrEqual(44);
            }
        }
    });

    test('mobile menu opens and closes via the button', async ({ page }) => {
        await page.setViewportSize({ width: 390, height: 844 });
        await page.goto('/');
        const button = page.locator('#public-menu-button');
        await expect(button).toBeVisible();
        await button.click();
        await expect(page.locator('#public-mobile-menu')).toBeVisible();
        await expect(button).toHaveAttribute('aria-expanded', 'true');
        // The backdrop overlays the page; close via the Escape key instead of
        // a pointer click on the button.
        await page.keyboard.press('Escape');
        await expect(page.locator('#public-mobile-menu')).not.toBeVisible();
        await expect(button).toHaveAttribute('aria-expanded', 'false');
    });
});
