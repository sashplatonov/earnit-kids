const { test, expect } = require('@playwright/test');
const { startAppHarness, gotoAppAsAdmin, gotoAppAsChild, openTab } = require('./helpers/appHarness');
const { readFixture, installAppNetworkMocks } = require('./helpers/networkMocks');

let harness;

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

test.describe('@shop-child отправка заявки', () => {
    test('@shop-child pending заявка появляется в my-requests', async ({ page }) => {
        const mocks = await installAppNetworkMocks(page, { initialData: readFixture('child-shop-flow.json') });
        await gotoAppAsChild(page, harness.baseUrl);

        await openTab(page, 'shop');
        await page.click('button:has-text("Купить")');
        await page.click('#confirm-ok');

        await openTab(page, 'requests');
        await expect(page.locator('#my-requests-list')).toContainText('Поход в парк');
        await expect(page.locator('#my-requests-list')).toContainText('В обработке');

        await expect.poll(() => mocks.getData().requests.length).toBe(1);
        const data = mocks.getData();
        expect(data.requests).toHaveLength(1);
        expect(data.requests[0].requestType).toBe('shop_purchase');
        expect(data.requests[0].status).toBe('pending');
    });
});

test.describe('@shop-child решение админа', () => {
    test('@shop-child admin approve: pending -> approved и запись в history', async ({ page }) => {
        const mocks = await installAppNetworkMocks(page, { initialData: readFixture('admin-pending-request.json') });
        await gotoAppAsAdmin(page, harness.baseUrl);

        await openTab(page, 'requests');
        await expect(page.locator('#incoming-requests-list')).toContainText('Поход в парк');
        await page.click('#incoming-requests-list button:has-text("✅")');

        await expect(page.locator('#requests-counter')).toHaveClass(/hidden/);
        await openTab(page, 'history');
        await expect(page.locator('#history-list')).toContainText('Поход в парк');

        await expect.poll(() => mocks.getData().requests[0]?.status).toBe('approved');
        await expect.poll(() => mocks.getData().history.length).toBe(1);
        await expect.poll(() => mocks.getData().children[0]?.balance).toBe(80);

        const data = mocks.getData();
        expect(data.history[0].type).toBe('spend');
        expect(data.history[0].itemId).toBe(2001);
        expect(data.history[0].moneyAmount).toBe(700);
    });

    test('@shop-child admin reject: pending -> rejected без списания', async ({ page }) => {
        const mocks = await installAppNetworkMocks(page, { initialData: readFixture('admin-pending-request.json') });
        await gotoAppAsAdmin(page, harness.baseUrl);

        await openTab(page, 'requests');
        await page.click('#incoming-requests-list button:has-text("❌")');
        await expect(page.locator('#incoming-requests-empty')).toBeVisible();

        await expect.poll(() => mocks.getData().requests[0]?.status).toBe('rejected');
        await expect.poll(() => mocks.getData().history.length).toBe(0);
        await expect.poll(() => mocks.getData().children[0]?.balance).toBe(160);
    });
});
