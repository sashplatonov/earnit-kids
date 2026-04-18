import { expect, test } from '@playwright/test';

test('root page and health probe are available', async ({ page, request }) => {
    const healthResponse = await request.get('/healthz');
    expect(healthResponse.ok()).toBeTruthy();

    const healthPayload = await healthResponse.json();
    expect(healthPayload.status).toBe('ok');
    expect(healthPayload.service).toBe('web-svelte');

    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'EarnIt Kids Svelte Migration' })).toBeVisible();
    await expect(page.getByText('Wave 1 foundation')).toBeVisible();
});
