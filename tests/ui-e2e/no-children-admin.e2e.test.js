const { test, expect } = require('@playwright/test');
const { startAppHarness, gotoAppAsAdmin, openTab } = require('./helpers/appHarness');
const { readFixture, installAppNetworkMocks } = require('./helpers/networkMocks');

let harness;

async function expectChildNavItemsHidden(moreDropdown) {
    await expect(moreDropdown.locator('button[data-tab="history"]')).toBeHidden();
    await expect(moreDropdown.locator('button[data-tab="friends"]')).toBeHidden();
    await expect(moreDropdown.locator('button[data-tab="analytics"]')).toBeHidden();
    await expect(moreDropdown.locator('#nav-limits')).toBeHidden();
    await expect(moreDropdown.locator('button[data-tab="catalog"]')).toBeHidden();
    await expect(moreDropdown.locator('#nav-child-link')).toBeHidden();
}

async function expectAdminNavItemsVisible(moreDropdown) {
    await expect(moreDropdown.locator('#nav-settings')).toBeVisible();
    await expect(moreDropdown.locator('button[data-tab="rules"]')).toBeVisible();
    await expect(moreDropdown.locator('#logout-btn')).toBeVisible();
}

test.beforeAll(async () => {
    harness = await startAppHarness();
});

test.afterAll(async () => {
    await harness?.stop?.();
});

test.describe('Admin with No Children Flow', () => {
    test('Should show Add Child CTA on Today screen and Switcher', async ({ page }) => {
        // Mock data with 0 children
        await installAppNetworkMocks(page, {
            initialData: readFixture('admin-no-children.json')
        });

        await gotoAppAsAdmin(page, harness.baseUrl);

        // 1. Verify Today screen has the "Add Child" CTA
        const todaySection = page.locator('#today-section');
        await expect(todaySection).toContainText('Нет детей в профиле');
        await expect(todaySection).toContainText('Добавить ребенка');

        // 2. Verify Child Switcher shows the "+ Ребенок" button
        const switcherContainer = page.locator('#child-switcher-container');
        await expect(switcherContainer).toContainText('Ребенок');
    });

    test('Navigation items requiring a child should be hidden', async ({ page }) => {
        await installAppNetworkMocks(page, {
            initialData: readFixture('admin-no-children.json')
        });
        await gotoAppAsAdmin(page, harness.baseUrl);

        // Child-specific tabs in main nav
        const tasksTab = page.locator('.nav__group--parent button[data-tab="tasks"]');
        const shopTab = page.locator('.nav__group--parent button[data-tab="shop"]');
        const requestsTab = page.locator('.nav__group--parent button[data-tab="requests"]');

        await expect(tasksTab).toBeHidden();
        await expect(shopTab).toBeHidden();
        await expect(requestsTab).toBeHidden();

        // More items
        await page.click('#nav-more-btn');
        const moreDropdown = page.locator('#nav-more-dropdown');

        await expectChildNavItemsHidden(moreDropdown);

        // Settings, Rules, Logout should be visible
        await expectAdminNavItemsVisible(moreDropdown);

        // Settings section
        await moreDropdown.locator('#nav-settings').click();
        const settingsSection = page.locator('#settings-section');
        await expect(settingsSection).toBeVisible();

        // Child settings card should be hidden
        const childSettingsCard = settingsSection.locator('.card:has-text("Настройки ребенка")');
        await expect(childSettingsCard).toBeHidden();
    });

    test('Clicking CTA should open Add Child modal', async ({ page }) => {
        await installAppNetworkMocks(page, {
            initialData: readFixture('admin-no-children.json')
        });
        await gotoAppAsAdmin(page, harness.baseUrl);

        // Click Add Child in Today section
        await page.click('#today-section button:has-text("Добавить ребенка")');

        // Verify Modal is visible
        const modal = page.locator('#add-child-modal');
        await expect(modal).toBeVisible();
    });
});
