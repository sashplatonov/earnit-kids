import { expect, test, type Page } from '@playwright/test';

test.use({ locale: 'en-US' });

function captureApiRequests(page: Page): string[] {
    const apiRequests: string[] = [];
    page.on('request', (request) => {
        if (new URL(request.url()).pathname.startsWith('/api/')) apiRequests.push(request.url());
    });
    return apiRequests;
}

async function expectNoApiRequests(apiRequests: string[]): Promise<void> {
    expect(apiRequests, 'live demo must stay isolated from the API').toEqual([]);
}

async function expectTargetSize(page: Page, locator: ReturnType<Page['getByRole']>): Promise<void> {
    const box = await locator.boundingBox();
    expect(box).not.toBeNull();
    expect(box!.width).toBeGreaterThanOrEqual(44);
    expect(box!.height).toBeGreaterThanOrEqual(44);
}

test('anonymous live demo submits a noted request and keeps it in memory', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    const apiRequests = captureApiRequests(page);
    const response = await page.goto('/demo');

    expect(response?.status()).toBe(200);
    await expect(page).toHaveTitle('Live rewards shop demo · EarnIt Kids');
    await expect(page.getByRole('heading', { name: 'Live rewards shop demo', level: 1 })).toBeVisible();
    await expect(page.getByText('This is a temporary demo.', { exact: false })).toBeVisible();
    await expect(page.getByText('75 / 120', { exact: true })).toBeVisible();

    const iceCream = page.getByRole('listitem').filter({ hasText: 'Ice cream' });
    const requestButton = iceCream.getByRole('button', { name: 'Ask for reward', exact: true });
    await requestButton.click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await dialog.getByRole('textbox', { name: 'Optional note' }).fill('Saturday treat');
    await dialog.getByRole('button', { name: /Ask for reward/ }).click();

    await expect(page.getByRole('status')).toContainText('Reward request sent for approval.');
    await expect(requestButton).toBeDisabled();
    await expectNoApiRequests(apiRequests);

    await page.getByRole('button', { name: 'Reset demo', exact: true }).click();
    await expect(page.locator('.announcement')).toContainText('The demo was reset to its starting state.');
    await expect(requestButton).toBeEnabled();
    await expectNoApiRequests(apiRequests);

    await page.reload();
    await expect(page.getByText('75 / 120', { exact: true })).toBeVisible();
    await expect(page.getByRole('listitem').filter({ hasText: 'Ice cream' }).getByRole('button', { name: 'Ask for reward', exact: true })).toBeEnabled();
    await expectNoApiRequests(apiRequests);
});

test('Russian live demo is directly reachable and preserves locale copy', async ({ page }) => {
    const apiRequests = captureApiRequests(page);
    const response = await page.goto('/ru/demo');

    expect(response?.status()).toBe(200);
    await expect(page.locator('html')).toHaveAttribute('lang', 'ru');
    await expect(page).toHaveTitle('Живое демо магазина наград · EarnIt Kids');
    await expect(page.getByRole('heading', { name: 'Живое демо магазина наград', level: 1 })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Сбросить демо', exact: true })).toBeVisible();
    await expect(page.getByText('Ваши действия остаются в этой вкладке браузера', { exact: false })).toBeVisible();
    await expectNoApiRequests(apiRequests);
});

test('live demo remains usable at 320px with keyboard-visible touch targets', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 568 });
    const apiRequests = captureApiRequests(page);
    await page.goto('/demo');

    const reset = page.getByRole('button', { name: 'Reset demo', exact: true });
    const request = page.getByRole('listitem').filter({ hasText: 'Ice cream' }).getByRole('button', { name: 'Ask for reward', exact: true });
    await expectTargetSize(page, reset);
    await expectTargetSize(page, request);

    await reset.focus();
    await expect(reset).toBeFocused();
    await expect(reset).toHaveCSS('outline-style', 'solid');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
    await expectNoApiRequests(apiRequests);
});
