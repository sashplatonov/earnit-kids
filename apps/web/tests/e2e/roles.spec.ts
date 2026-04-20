import { expect, test } from '@playwright/test';
import {
    DEFAULT_PARENT_PASSWORD,
    addChild,
    getChildMagicLink,
    loginChildByMagicLink,
    loginParent,
    openFamilyApp,
    registerParent,
    uniqueEmail,
} from './helpers';

const SUPER_ADMIN_EMAIL = process.env.E2E_SUPER_ADMIN_EMAIL ?? 'admin@example.com';
const SUPER_ADMIN_PASSWORD = process.env.E2E_SUPER_ADMIN_PASSWORD ?? 'change-me';

test('child session does not expose parent controls', async ({ page }) => {
    const email = uniqueEmail('role.child');

    await registerParent(page, email, DEFAULT_PARENT_PASSWORD);
    await openFamilyApp(page);
    await addChild(page, 'Ролевой Ребёнок');

    const childLink = await getChildMagicLink(page);
    await loginChildByMagicLink(page, childLink);

    await expect(page.locator('#child-switcher-add-child')).toHaveCount(0);
    await page.goto('/super-admin');
    await expect(page).toHaveURL('/');
    await expect(page.getByRole('heading', { name: 'Административная панель' })).toHaveCount(0);
});

test('super admin can access the admin panel', async ({ page }) => {
    await loginParent(page, SUPER_ADMIN_EMAIL, SUPER_ADMIN_PASSWORD, {
        destination: /\/super-admin$/,
        heading: 'Административная панель',
    });

    await expect(page).toHaveURL(/\/super-admin$/);
    await expect(page.getByRole('heading', { name: 'Административная панель' })).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Семьи EarnIt Kids' })).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Каталог задач' })).toBeVisible();
});