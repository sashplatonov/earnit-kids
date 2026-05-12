import { expect, test } from '@playwright/test';
import {
    DEFAULT_PARENT_PASSWORD,
    loginParent,
    openSettings,
    logout,
    registerParent,
    uniqueEmail,
} from './helpers';

test('family admin can manage parent access from settings', async ({ page }) => {
    const ownerEmail = uniqueEmail('parent.access.owner');
    const invitedEmail = uniqueEmail('parent.access.invited');

    await registerParent(page, ownerEmail, DEFAULT_PARENT_PASSWORD);
    await openSettings(page);

    const parentAccessHeading = /Manage parent access|Управление доступом родителей/i;
    await expect(page.getByRole('heading', { name: parentAccessHeading })).toBeVisible();

    await page.locator('#parent-access-email').fill(invitedEmail);
    await page.locator('#parent-access-permission').selectOption('editor');
    await page.locator('#parent-access-invite').click();

    const invitedRow = page.locator('#parent-access-list [data-membership-id]')
        .filter({ hasText: invitedEmail })
        .first();
    await expect(invitedRow).toBeVisible();
    await expect(invitedRow).toContainText(/Editor|Редактор/i);

    await invitedRow.locator('select').selectOption('viewer');
    await expect(invitedRow).toContainText(/Viewer|Просмотр/i);

    page.once('dialog', async (dialog) => {
        await dialog.accept();
    });
    await invitedRow.getByRole('button', { name: /Remove|Удалить/i }).click();
    await expect(page.locator('#parent-access-list [data-membership-id]')
        .filter({ hasText: invitedEmail }))
        .toHaveCount(0);
});

test('family admin settings still open after re-login', async ({ page }) => {
    const ownerEmail = uniqueEmail('parent.access.relogin');

    await registerParent(page, ownerEmail, DEFAULT_PARENT_PASSWORD);
    await openSettings(page);
    await logout(page);
    await loginParent(page, ownerEmail, DEFAULT_PARENT_PASSWORD, { destination: /\/app\/settings$/ });
    await openSettings(page);

    await expect(page.locator('#settings-section')).toBeVisible();
    await expect(page.locator('#parent-access-section')).toBeVisible();
});

test('primary parent email shows toast error and stays out of membership list', async ({ page }) => {
    const ownerEmail = uniqueEmail('parent.access.primary');

    await registerParent(page, ownerEmail, DEFAULT_PARENT_PASSWORD);
    await openSettings(page);

    await page.locator('#parent-access-email').fill(ownerEmail);
    await page.locator('#parent-access-permission').selectOption('editor');
    await page.locator('#parent-access-invite').click();

    await expect(page.getByRole('alert').filter({
        hasText: /main parent account|основному родителю/i,
    })).toBeVisible();
    await expect(page.locator('#parent-access-list [data-membership-id]')).toHaveCount(0);
    await expect(page.locator('#parent-access-section')).not.toContainText(/already in the family|уже в семье/i);
});
