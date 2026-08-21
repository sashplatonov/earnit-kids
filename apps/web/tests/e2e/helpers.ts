import { expect, type Page } from '@playwright/test';

export const DEFAULT_PARENT_PASSWORD = 'TestPass123!';

export function uniqueEmail(prefix: string) {
    return `${prefix}.${Date.now()}@example.com`;
}

export async function registerParent(
    page: Page,
    email: string,
    password = DEFAULT_PARENT_PASSWORD
) {
    await page.goto('/login.html');
    await page.getByRole('button', { name: /Register|Регистрация/i }).click();
    const registerPanel = page.locator('[aria-label="Registration"], [aria-label="Регистрация"]');
    await expect(registerPanel).toBeVisible();
    await registerPanel.locator('input[autocomplete="email"]').fill(email);
    await registerPanel.locator('input[autocomplete="new-password"]').fill(password);
    const registerResponse = page.waitForResponse(
        (response) => response.request().method() === 'POST' && /\/api\/register$/.test(response.url())
    );
    await registerPanel.getByRole('button', { name: /Register family|Зарегистрировать/i }).click();
    const response = await registerResponse;
    if (!response.ok()) {
        throw new Error(`Register failed (${response.status()}): ${await response.text()}`);
    }

}
