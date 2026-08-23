import { expect, test, type Page } from '@playwright/test';

async function authenticate(page: Page, role: 'parent' | 'child') {
    await page.goto('/public/index.html');
    const host = new URL(page.url()).hostname;
    await page.context().addCookies([
        { name: 'e2e_session', value: role, domain: host, path: '/' },
        { name: 'csrf_token', value: 'e2e-csrf', domain: host, path: '/' },
    ]);
}

test('normal browser workspace access preserves a local login continuation', async ({ page }) => {
    await page.goto('/workspace');

    await expect(page).toHaveURL(/\/(?:en|ru)\/login\?continue=%2F(?:en|ru)%2Fworkspace$/);
    await expect(page.getByRole('heading', { name: /sign in|вход для родителей и детей/i })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});

test('parent invitation failure is safe and does not enumerate family data', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 568 });
    await page.goto('/invite/parent?error=expired');

    await expect(page.getByTestId('parent-invitation')).toBeVisible();
    await expect(page.getByRole('alert')).toContainText(/expired|истёк/i);
    await expect(page.getByTestId('parent-invitation')).not.toContainText(/family id|@/i);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});

test('Telegram-hosted workspace does not expose browser sign out', async ({ page }) => {
    await page.goto('/telegram');

    await expect(page.getByRole('button', { name: /sign out|выйти/i })).toHaveCount(0);
});

test('an authenticated parent can sign out with one pending request', async ({ page }) => {
    await authenticate(page, 'parent');
    await page.goto('/workspace');
    const logoutButton = page.locator('button.logout');

    await expect(logoutButton).toBeVisible();
    await expect(page.locator('.workspace-parent')).toBeVisible();
    let requestCount = 0;
    await page.route('**/api/logout', async (route) => {
        requestCount += 1;
        await new Promise((resolve) => setTimeout(resolve, 100));
        await route.fulfill({ status: 204 });
    });
    let navigationCount = 0;
    page.on('framenavigated', (frame) => {
        if (frame === page.mainFrame()) navigationCount += 1;
    });
    await logoutButton.dispatchEvent('click');
    await expect(logoutButton).toBeDisabled();
    await logoutButton.dispatchEvent('click');

    await expect(page.getByRole('link', { name: /войти в браузер|продолжить с google|sign in/i }).first()).toBeVisible();
    expect(navigationCount).toBeGreaterThan(0);
    expect(requestCount).toBe(1);
});

test('a failed browser logout stays in place and can be retried', async ({ page }) => {
    await authenticate(page, 'parent');
    await page.goto('/workspace');
    const logoutButton = page.locator('button.logout');
    const workspaceUrl = page.url();
    let requestCount = 0;
    await page.route('**/api/logout', async (route) => {
        requestCount += 1;
        await route.fulfill({ status: requestCount === 1 ? 403 : 204 });
    });

    await logoutButton.click();

    await expect(page.getByRole('alert')).toContainText(/could not sign out|не удалось выйти/i);
    await expect(page).toHaveURL(workspaceUrl);
    await logoutButton.click();
    await expect(page).toHaveURL(/\/public\/index\.html$/);
    expect(requestCount).toBe(2);
});

test('an authenticated child sees the child workspace and browser sign out', async ({ page }) => {
    await authenticate(page, 'child');
    await page.goto('/workspace');

    await expect(page.getByRole('button', { name: /sign out|выйти/i })).toBeVisible();
    await expect(page.locator('.child-workspace')).toBeVisible();
});
