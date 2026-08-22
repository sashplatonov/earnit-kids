import { expect, test } from '@playwright/test';

test('registration hands off to password login, which opens the localized Telegram Mini App', async ({ page }) => {
    await page.clock.install();
    await page.route('**/api/auth-config', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ googleEnabled: false }),
    }));
    await page.route('**/api/register', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({}),
    }));
    await page.route('**/api/login', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ familyId: 'family-1', role: 'admin' }),
    }));

    await page.goto('/en/login');
    await page.getByRole('button', { name: 'Register' }).click();
    const registration = page.getByLabel('Registration');
    await registration.locator('input[autocomplete="email"]').fill('parent@example.com');
    await registration.locator('input[autocomplete="new-password"]').fill('Password1!');
    await registration.getByRole('button', { name: 'Register family' }).click();

    await page.clock.runFor(3_000);
    const login = page.getByLabel('Sign in');
    await expect(login.locator('input[autocomplete="username"]')).toHaveValue('parent@example.com');
    await login.locator('input[autocomplete="current-password"]').fill('Password1!');
    await login.locator('.btn-login').click();

    await expect(page).toHaveURL(/\/(?:en|ru)\/telegram$/);
});
