import { expect, test } from '@playwright/test';

test.use({ locale: 'en-US' });

test('family management stays live and external controls remain isolated', async ({ page }) => {
    const apiRequests: string[] = [];
    page.on('request', (request) => {
        if (new URL(request.url()).pathname.startsWith('/api/')) apiRequests.push(request.url());
    });

    await page.goto('/demo');
    await page.getByRole('tab', { name: 'Family' }).click();
    await expect(page.getByRole('heading', { name: 'Family', level: 1 })).toBeVisible();

    await page.getByRole('button', { name: /Add child/ }).first().click();
    await page.locator('#family-new-child-name').fill('Nora');
    await page.locator('.invite').getByRole('button', { name: /Add child/ }).click();
    await expect(page.getByText('Nora', { exact: true })).toBeVisible();

    await page.getByRole('button', { name: /Family language/ }).click();
    await page.getByRole('button', { name: 'Switch to Russian' }).click();
    await expect(page).toHaveURL(/\/ru\/demo$/);
    await expect(page.locator('html')).toHaveAttribute('lang', 'ru');
    await page.getByRole('tab', { name: 'Семья' }).click();
    await expect(page.getByRole('heading', { name: 'Семья', level: 1 })).toBeVisible();
    await expect(page.getByText('Nora', { exact: true })).not.toBeVisible();

    await page.getByRole('button', { name: /Язык семьи/ }).click();
    await page.getByRole('button', { name: 'Переключить на английский' }).click();
    await expect.poll(() => new URL(page.url()).pathname).toBe('/demo');
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
    await expect(page.getByText('Nora', { exact: true })).not.toBeVisible();

    await page.getByRole('tab', { name: 'Family' }).click();
    await page.locator('.settings > .setting').first().click();
    await expect(page.getByRole('alert')).toContainText('unavailable in the demo');
    expect(apiRequests).toEqual([]);
});
