const fs = require('node:fs');
const path = require('node:path');

const fixturesDir = path.resolve(__dirname, '../fixtures');

function deepClone(value) {
    return JSON.parse(JSON.stringify(value));
}

function readFixture(fileName) {
    const raw = fs.readFileSync(path.join(fixturesDir, fileName), 'utf8');
    return JSON.parse(raw);
}

function createMockState(options = {}) {
    return {
        currentData: deepClone(options.initialData || readFixture('admin-shop-empty.json')),
        baseData: deepClone(options.baseData || readFixture('base-data.json')),
        analyticsData: deepClone(options.analyticsData || {
            summary: { totalEarned: 0, totalSpent: 0, netChange: 0 },
            comparison: {},
            topTasks: [],
            topItems: [],
            trends: [],
            recommendations: []
        }),
        postStatus: options.postStatus || 200,
        postError: options.postError || 'Save failed',
        calls: {
            dataGet: 0,
            dataPost: 0,
            baseDataGet: 0,
            analyticsGet: 0
        }
    };
}

function applySavePayload(mockState, payload) {
    mockState.currentData = {
        ...mockState.currentData,
        balance: payload.balance,
        tasks: payload.tasks || [],
        shop: payload.shop || [],
        history: payload.history || [],
        requests: payload.requests || [],
        children: payload.children || mockState.currentData.children || []
    };
}

async function mockApiData(route, mockState) {
    const method = route.request().method();

    if (method === 'GET') {
        mockState.calls.dataGet += 1;
        await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify(mockState.currentData)
        });
        return;
    }

    if (method !== 'POST') {
        await route.continue();
        return;
    }

    mockState.calls.dataPost += 1;
    const payload = route.request().postDataJSON() || {};

    if (mockState.postStatus >= 400) {
        await route.fulfill({
            status: mockState.postStatus,
            contentType: 'application/json',
            body: JSON.stringify({ error: mockState.postError })
        });
        return;
    }

    applySavePayload(mockState, payload);
    await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true })
    });
}

async function installAppNetworkMocks(page, options = {}) {
    const mockState = createMockState(options);

    await page.route('**/api/base-data', async route => {
        mockState.calls.baseDataGet += 1;
        await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify(mockState.baseData)
        });
    });

    await page.route('**/api/analytics*', async route => {
        mockState.calls.analyticsGet += 1;
        await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify(mockState.analyticsData)
        });
    });

    await page.route('**/api/data', route => mockApiData(route, mockState));

    await page.route('**/api/preferences', async route => {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true }) });
    });

    await page.route('**/api/children/*/link', async route => {
        await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ success: true, link: 'http://localhost/login-child/test-token' })
        });
    });

    await page.route('**/api/logout', async route => {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true }) });
    });

    return {
        getCalls: () => ({ ...mockState.calls }),
        getData: () => deepClone(mockState.currentData),
        setPostFailure: ({ status = 500, error = 'Save failed' } = {}) => {
            mockState.postStatus = status;
            mockState.postError = error;
        }
    };
}

module.exports = {
    readFixture,
    installAppNetworkMocks
};
