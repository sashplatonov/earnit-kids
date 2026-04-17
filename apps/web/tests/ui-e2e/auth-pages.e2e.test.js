const { test, expect } = require('@playwright/test');
const path = require('node:path');
const { pathToFileURL } = require('node:url');

const viewsDir = path.resolve(__dirname, '../../views');

function viewUrl(fileName, query = '') {
    const fileUrl = pathToFileURL(path.join(viewsDir, fileName)).href;
    return query ? `${fileUrl}?${query}` : fileUrl;
}

test('login page switches between login and register forms', async ({ page }) => {
    await page.goto(viewUrl('login.html'));

    await expect(page.locator('#login-form')).toBeVisible();
    await page.click('#show-register-btn');

    await expect(page.locator('#register-form')).toHaveClass(/active/);
    await expect(page.locator('#login-form')).toHaveClass(/hidden/);

    await page.click('#show-login-btn');
    await expect(page.locator('#register-form')).not.toHaveClass(/active/);
    await expect(page.locator('#login-form')).not.toHaveClass(/hidden/);
});

test('forgot password flow validates email on client side', async ({ page }) => {
    await page.goto(viewUrl('login.html'));

    await page.click('#forgot-password-link');
    await page.click('#send-recovery-btn');

    await expect(page.locator('#error-msg')).toBeVisible();
    await expect(page.locator('#error-msg')).toContainText('Введите корректный Email');
});

test('reset password form submits and shows success message', async ({ page }) => {
    await page.addInitScript(() => {
        window.fetch = async () => ({ json: async () => ({ success: true }) });
    });
    await page.goto(viewUrl('reset-password.html', 'token=test-token&email=kid%40example.com'));

    await page.fill('#password', '123456');
    await page.fill('#confirmPassword', '123456');
    await page.click('button[type="submit"]');

    await expect(page.locator('#message')).toBeVisible();
    await expect(page.locator('#message')).toContainText('Пароль успешно изменен');
});

test('verify page validates token and displays login button after success', async ({ page }) => {
    await page.addInitScript(() => {
        window.fetch = async () => ({ json: async () => ({ success: true }) });
    });
    await page.goto(viewUrl('verify.html', 'token=verify-token&email=parent%40example.com'));

    await expect(page.locator('#message')).toContainText('Email успешно подтвержден');
    await expect(page.locator('#loginBtn')).toBeVisible();
});
