import { expect, test } from '@playwright/test';

test.describe('public localized site', () => {
    for (const locale of ['en', 'ru']) {
        test(`${locale} home exposes localized SEO and navigation`, async ({ page }) => {
            await page.goto(`/${locale}/`);

            await expect(page.locator('html')).toHaveAttribute('lang', locale);
            await expect(page).toHaveTitle(/EarnIt Kids/);
            await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', new RegExp(`/${locale}/?$`));
            await expect(page.locator('link[hreflang="en"]')).toHaveAttribute('href', /\/en\/$/);
            await expect(page.locator('link[hreflang="ru"]')).toHaveAttribute('href', /\/ru\/$/);
            await expect(page.getByRole('group', { name: locale === 'en' ? 'Switch language' : 'Сменить язык' })).toBeVisible();
        });
    }

    test('legacy static pages remain available but are not indexable', async ({ page }) => {
        await page.goto('/public/index.html');
        await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', 'noindex, nofollow');
    });

    test('Telegram launch parameters keep the Mini App entry point', async ({ page }) => {
        await page.goto('/?tgWebAppStartParam=pairing-token');
        await expect(page).toHaveURL(/\/ru\/telegram\?tgWebAppStartParam=pairing-token$/);
    });
});
