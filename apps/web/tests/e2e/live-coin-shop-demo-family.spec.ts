import { expect, test } from '@playwright/test';
import { captureApiRequests, expectNoApiRequests } from './live-coin-shop-demo.helpers';

test.use({ locale: 'en-US' });

test('family management and nested settings stay live without API calls', async ({ page }) => {
    const apiRequests = captureApiRequests(page);

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
    await page.getByRole('button', { name: 'Parents' }).click();
    await expect(page.getByRole('heading', { name: 'Parents' })).toBeVisible();
    await page.getByLabel('Email').fill('demo@example.com');
    await page.getByRole('button', { name: 'Save' }).click();
    await expect(page.getByRole('status')).toContainText('updated');
    expectNoApiRequests(apiRequests);
});

test('live-demo family settings keep notifications local and omit server administration', async ({ page }) => {
    const apiRequests = captureApiRequests(page);

    await page.goto('/demo');
    await expect(page.getByText('Learning', { exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: /Statistics/ })).not.toBeVisible();

    await page.getByRole('tab', { name: 'Family' }).click();
    await page.getByRole('button', { name: 'Notifications' }).click();
    const notifications = page.getByRole('dialog', { name: 'Notifications' });
    await expect(notifications).toBeVisible();
    await expect(notifications.getByText('Browser notifications', { exact: true })).not.toBeVisible();
    await expect(notifications.getByText('Child marked a task as done', { exact: true })).toBeVisible();
    await notifications.getByRole('button', { name: 'Close' }).click();

    await page.getByRole('button', { name: /View as child/ }).click();
    await page.getByRole('tab', { name: 'Activity' }).click();
    await expect(page.getByText('Learning', { exact: true })).toBeVisible();
    expectNoApiRequests(apiRequests);
});
