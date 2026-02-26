const { test, expect } = require('@playwright/test');
const { startAppHarness, gotoAppAsAdmin, openTab } = require('./helpers/appHarness');
const { readFixture, installAppNetworkMocks } = require('./helpers/networkMocks');

let harness;

async function openAddShopModal(page) {
    await page.evaluate(() => {
        const modal = document.getElementById('shop-modal');
        if (!modal) throw new Error('shop-modal not found');
        if (window.app?.editShopItem) {
            window.app.editShopItem();
        }
        if (!modal.open) {
            modal.showModal();
        }
    });
    await expect(page.locator('#shop-modal')).toBeVisible();
}

async function createShopItem(page) {
    await openAddShopModal(page);
    await page.fill('#shop-name', 'Билет в кино');
    await page.fill('#shop-group', 'Развлечения');
    await page.fill('#shop-price', '75');
    await page.fill('#shop-money-limit', '1200');
    await page.selectOption('#shop-type', 'small');
    await page.fill('#shop-freq-limit', '2');
    await page.selectOption('#shop-freq-period', 'month');
    await page.fill('#shop-comment', 'Только по выходным');
    await page.click('#shop-save');
}

async function editShopItem(page) {
    await page.locator('#shop-list button:has-text("Изменить")').first().click();
    await expect(page.locator('#shop-modal')).toBeVisible();
    await page.fill('#shop-name', 'Семейный киносеанс');
    await page.fill('#shop-price', '90');
    await page.click('#shop-save');
}

async function deleteShopItem(page) {
    await page.locator('#shop-list button:has-text("Изменить")').first().click();
    await expect(page.locator('#shop-modal')).toBeVisible();
    await page.click('#shop-delete');
    await page.click('#confirm-ok');
}

test.beforeAll(async () => {
    harness = await startAppHarness();
});

test.afterAll(async () => {
    await harness?.stop?.();
});

test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => {
        window.confirm = () => true;
    });
});

test.describe('@shop-admin базовые состояния', () => {
    test('@shop-admin empty-state для магазина', async ({ page }) => {
        await installAppNetworkMocks(page, { initialData: readFixture('admin-shop-empty.json') });
        await gotoAppAsAdmin(page, harness.baseUrl);

        await openTab(page, 'shop');
        await expect(page.locator('#shop-empty')).toBeVisible();
        await expect(page.locator('#shop-list .card')).toHaveCount(0);
    });

    test('@shop-admin валидация полей товара', async ({ page }) => {
        await installAppNetworkMocks(page, { initialData: readFixture('admin-shop-empty.json') });
        await gotoAppAsAdmin(page, harness.baseUrl);
        await openTab(page, 'shop');

        await openAddShopModal(page);
        await page.fill('#shop-price', '20');
        await page.click('#shop-save');
        await expect(page.locator('.toast__message').last()).toContainText('Введите название');

        await page.fill('#shop-name', 'Награда');
        await page.fill('#shop-price', '-1');
        await page.click('#shop-save');
        await expect(page.locator('.toast__message').last()).toContainText('Введите корректную цену');
    });
});

test.describe('@shop-admin CRUD и фильтры', () => {
    test('@shop-admin add/edit/delete товара через modal', async ({ page }) => {
        const mocks = await installAppNetworkMocks(page, { initialData: readFixture('admin-shop-empty.json') });
        await gotoAppAsAdmin(page, harness.baseUrl);
        await openTab(page, 'shop');

        await createShopItem(page);
        await expect(page.locator('#shop-list .card')).toHaveCount(1);
        await expect(page.locator('#shop-list')).toContainText('Билет в кино');
        await expect(page.locator('#shop-list')).toContainText('75');

        await editShopItem(page);
        await expect(page.locator('#shop-list')).toContainText('Семейный киносеанс');
        await expect(page.locator('#shop-list')).toContainText('90');

        await deleteShopItem(page);
        await expect(page.locator('#shop-empty')).toBeVisible();

        await expect.poll(() => mocks.getData().shop.some(item => item.isDeleted)).toBeTruthy();
    });

    test('@shop-admin фильтрация shop по currentChildId', async ({ page }) => {
        await installAppNetworkMocks(page, { initialData: readFixture('admin-shop-seeded.json') });
        await gotoAppAsAdmin(page, harness.baseUrl);
        await openTab(page, 'shop');

        await expect(page.locator('#shop-list')).toContainText('Поход в кино');
        await expect(page.locator('#shop-list')).not.toContainText('Мороженое');

        await page.click('[data-child-toggle]');
        await page.click('.child-menu-item:has-text("Илья")');

        await expect(page.locator('#shop-list')).toContainText('Мороженое');
        await expect(page.locator('#shop-list')).not.toContainText('Поход в кино');
    });
});
