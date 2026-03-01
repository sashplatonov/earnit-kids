const { test, expect } = require('@playwright/test');
const { startAppHarness, gotoAppAsAdmin, openTab } = require('./helpers/appHarness');
const { readFixture, installAppNetworkMocks } = require('./helpers/networkMocks');

let harness;

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

        // 3. Verify error messages when trying to perform child-linked actions
        await openTab(page, 'tasks');
        // Click first task "Earn" button - selector matches button in ui-tasks.js
        await page.click('button:has-text("Начислить")');
        // A toast should appear
        const toast = page.locator('.toast');
        await expect(toast).toContainText('Сначала добавьте ребенка');

        // 4. Verify shop actions
        await openTab(page, 'shop');
        // The button has "Купить" text if balance >= price, or "Не хватает" otherwise. 
        // We select by the card class to be sure.
        const buyBtn = page.locator('.card--shop .btn:has-text("Купить"), .card--shop .btn:has-text("Не хватает")').first();
        await expect(buyBtn).toBeVisible();
        await buyBtn.click({ force: true });
        // Toast for shop
        await expect(page.locator('.toast')).toContainText('Сначала добавьте ребенка');
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
