const { test, expect } = require('@playwright/test');
const { startAppHarness } = require('./helpers/appHarness');

let harness;

test.describe('@landing публичные страницы', () => {
    test.beforeAll(async () => {
        harness = await startAppHarness();
    });

    test.afterAll(async () => {
        await harness?.stop?.();
    });

    test('@landing root рендерит landing и ключевые CTA', async ({ page }) => {
        await page.goto(`${harness.baseUrl}/`);

        await expect(page.getByRole('heading', { name: 'Делаем полезные привычки понятными и веселыми' })).toBeVisible();
        await expect(page.getByRole('link', { name: 'Начать вместе' })).toHaveAttribute('href', '/login.html');
        await expect(page.getByRole('link', { name: 'Смотреть сценарии задач' })).toHaveAttribute('href', '/features/tasks');
        await expect(page.getByRole('link', { name: 'Посмотреть магазин' })).toHaveAttribute('href', '/features/shop');
    });

    test('@landing public-top-nav маршруты доступны', async ({ page }) => {
        await page.goto(`${harness.baseUrl}/`);

        await expect(page.locator('.public-top-nav')).toBeVisible();

        const routes = ['/features/tasks', '/features/shop', '/about', '/faq', '/blog'];
        for (const route of routes) {
            await page.goto(`${harness.baseUrl}${route}`);
            await expect(page.locator('main')).toBeVisible();
        }
    });

    test('@landing feature pages и невалидный slug', async ({ page }) => {
        const tasksResponse = await page.goto(`${harness.baseUrl}/features/tasks`);
        expect(tasksResponse.status()).toBe(200);
        await expect(page.getByRole('heading', { name: 'Задания, которые хочется выполнять' })).toBeVisible();

        const shopResponse = await page.goto(`${harness.baseUrl}/features/shop`);
        expect(shopResponse.status()).toBe(200);
        await expect(page.getByRole('heading', { name: 'Магазин радостей за монетки' })).toBeVisible();

        const invalidResponse = await page.goto(`${harness.baseUrl}/features/unknown-feature`);
        expect(invalidResponse.status()).toBe(404);
        await expect(page.getByRole('heading', { name: '404' })).toBeVisible();
    });

    test('@landing mobile critical blocks visible', async ({ page }) => {
        await page.setViewportSize({ width: 390, height: 844 });
        await page.goto(`${harness.baseUrl}/`);

        await expect(page.locator('.public-top-nav')).toBeVisible();
        await expect(page.locator('.landing-hero')).toBeVisible();
        await expect(page.locator('.public-cta')).toBeVisible();
    });

    test('@landing desktop critical blocks visible', async ({ page }) => {
        await page.setViewportSize({ width: 1440, height: 900 });
        await page.goto(`${harness.baseUrl}/`);

        await expect(page.locator('.landing-hero')).toBeVisible();
        await expect(page.locator('.value-grid').first()).toBeVisible();
    });
});
