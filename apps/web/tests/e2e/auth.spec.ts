import { expect, test } from '@playwright/test';
import { DEFAULT_PARENT_PASSWORD, loginParent, logout, openFamilyApp, registerParent, uniqueEmail } from './helpers';

test('register and immediately login', async ({ page }) => {
    const email = uniqueEmail('auth.regression');

    await registerParent(page, email, DEFAULT_PARENT_PASSWORD);
    await openFamilyApp(page);
    await logout(page);
    await loginParent(page, email, DEFAULT_PARENT_PASSWORD);

    await expect(page.locator('#child-switcher-add-child')).toBeVisible();
});