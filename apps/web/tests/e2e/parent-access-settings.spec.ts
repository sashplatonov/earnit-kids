import { expect, test } from '@playwright/test';
import {
    DEFAULT_PARENT_PASSWORD,
    loginParent,
    openFamilyApp,
    openSettings,
    registerParent,
    uniqueEmail,
} from './helpers';

test('family admin can manage parent access from settings', async ({ page }) => {
    const ownerEmail = uniqueEmail('parent.access.owner');
    const invitedEmail = uniqueEmail('parent.access.invited');

    await registerParent(page, ownerEmail, DEFAULT_PARENT_PASSWORD);
    await openFamilyApp(page);
    await page.getByRole('button', { name: /Additional sections|Дополнительные разделы/i }).click();
    await expect(page.getByRole('menuitem', { name: /Manage parent access|Управление доступом родителей/i })).toBeVisible();
    await page.getByRole('menuitem', { name: /Manage parent access|Управление доступом родителей/i }).click();

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
    await invitedRow.getByRole('button', { name: /Save changes|Сохранить изменения/i }).click();
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
    await openFamilyApp(page);
    await loginParent(page, ownerEmail, DEFAULT_PARENT_PASSWORD);
    await openSettings(page);

    await expect(page.locator('#settings-section')).toBeVisible();
    await expect(page.locator('#parent-access-section')).toBeVisible();
});
