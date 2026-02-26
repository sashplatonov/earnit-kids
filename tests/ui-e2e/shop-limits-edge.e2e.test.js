const { test, expect } = require('@playwright/test');
const { startAppHarness, gotoAppAsChild, openTab } = require('./helpers/appHarness');
const { installAppNetworkMocks, readFixture } = require('./helpers/networkMocks');

let harness;

function isoNow() {
    return new Date().toISOString();
}

function createLimitsBaseData() {
    return {
        isAdmin: false,
        familyId: 'fam-limits',
        balance: 100,
        tasks: [],
        shop: [
            {
                id: 3001,
                name: 'Крупная награда',
                group: 'Семья',
                price: 80,
                money_limit: 600,
                type: 'large',
                frequency: { limit: 1, period: 'month' },
                childId: 301,
                isDeleted: false
            },
            {
                id: 3002,
                name: 'Повторяемая награда',
                group: 'Режим',
                price: 30,
                money_limit: 100,
                type: 'small',
                frequency: { limit: 1, period: 'week' },
                childId: 301,
                isDeleted: false
            }
        ],
        history: [],
        requests: [],
        monthlyLimit: 500,
        dailyCoinLimit: 50,
        childNickname: 'Миша',
        children: [
            { id: 301, name: 'Миша', balance: 100, monthlyLimit: 500, dailyCoinLimit: 50 }
        ]
    };
}

async function openShop(page, config) {
    const mocks = await installAppNetworkMocks(page, {
        initialData: config.data,
        postStatus: config.postStatus,
        postError: config.postError
    });
    await gotoAppAsChild(page, harness.baseUrl);
    await openTab(page, 'shop');
    return mocks;
}

async function buyFirstItem(page) {
    await page.locator('#shop-list button:has-text("Купить")').first().click();
}

async function buyItemById(page, itemId) {
    const card = page.locator(`#shop-list .card[data-id="${itemId}"]`).first();
    await card.locator('button:has-text("Купить")').click();
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

test.describe('@limits базовые ограничения', () => {
    test('@limits недостаточно монет блокирует покупку', async ({ page }) => {
        const data = createLimitsBaseData();
        data.balance = 20;
        data.children[0].balance = 20;

        await openShop(page, { data });
        const blockedButton = page.locator('#shop-list button:has-text("Не хватает")').first();
        await expect(blockedButton).toBeVisible();
        await expect(blockedButton).toBeDisabled();
    });

    test('@limits monthlyLimit отклоняет покупку', async ({ page }) => {
        const data = createLimitsBaseData();
        data.history = [{
            id: 1,
            type: 'spend',
            itemId: 3002,
            amount: 10,
            moneyAmount: 450,
            childId: 301,
            date: isoNow()
        }];

        await openShop(page, { data });
        await buyFirstItem(page);
        await expect(page.locator('.toast__message').last()).toContainText('Превышен месячный лимит');
    });

});

test.describe('@limits расширенные ограничения', () => {
    test('@limits second large purchase блокируется', async ({ page }) => {
        const data = createLimitsBaseData();
        data.monthlyLimit = 2000;
        data.children[0].monthlyLimit = 2000;
        data.history = [{
            id: 2,
            type: 'spend',
            itemId: 3001,
            amount: 80,
            moneyAmount: 600,
            childId: 301,
            date: isoNow()
        }];

        await openShop(page, { data });
        await buyItemById(page, 3001);
        await expect(page.locator('.toast__message').last()).toContainText('Уже была крупная покупка');
    });

    test('@limits frequency limit блокирует повтор', async ({ page }) => {
        const data = createLimitsBaseData();
        data.history = [{
            id: 3,
            type: 'spend',
            itemId: 3002,
            amount: 30,
            moneyAmount: 100,
            childId: 301,
            date: isoNow()
        }];

        await openShop(page, { data });
        await buyItemById(page, 3002);
        await expect(page.locator('.toast__message').last()).toContainText('Лимит частоты');
    });
});

test.describe('@limits moneyAmount и API ошибки', () => {
    test('@limits moneyAmount отображается в заявке', async ({ page }) => {
        const data = readFixture('child-shop-flow.json');
        await openShop(page, { data });

        await buyFirstItem(page);
        await page.click('#confirm-ok');
        await openTab(page, 'requests');

        await expect(page.locator('#my-requests-list')).toContainText('700');
    });

    test('@limits POST /api/data 403 не ломает UI и логирует ошибку', async ({ page }) => {
        const errors = [];
        page.on('console', msg => {
            if (msg.type() === 'error') errors.push(msg.text());
        });

        const data = readFixture('child-shop-flow.json');
        const mocks = await openShop(page, { data, postStatus: 403, postError: 'Forbidden by test' });

        await buyFirstItem(page);
        await page.click('#confirm-ok');
        await expect.poll(() => mocks.getCalls().dataPost).toBeGreaterThan(0);
        await openTab(page, 'requests');
        await expect(page.locator('#my-requests-list')).toContainText('Поход в парк');

        expect(errors.some(text => text.includes('Failed to save data (403): Forbidden by test'))).toBeTruthy();
    });

    test('@limits POST /api/data 500 не ломает UI и логирует ошибку', async ({ page }) => {
        const errors = [];
        page.on('console', msg => {
            if (msg.type() === 'error') errors.push(msg.text());
        });

        const data = readFixture('child-shop-flow.json');
        const mocks = await openShop(page, { data, postStatus: 500, postError: 'Server crashed in test' });

        await buyFirstItem(page);
        await page.click('#confirm-ok');
        await expect.poll(() => mocks.getCalls().dataPost).toBeGreaterThan(0);
        await openTab(page, 'requests');
        await expect(page.locator('#my-requests-list')).toContainText('Поход в парк');

        expect(errors.some(text => text.includes('Failed to save data (500): Server crashed in test'))).toBeTruthy();
    });
});
