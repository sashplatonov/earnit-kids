const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const { pathToFileURL } = require('node:url');

async function loadActionHelpers() {
    global.window = { CONFIG: {} };
    const modulePath = pathToFileURL(path.join(process.cwd(), 'public/js/modules/action-helpers.js')).href;
    return import(`${modulePath}?t=${Date.now()}`);
}

test('checkLimits does not count the currently approved pending purchase twice', async () => {
    const { state } = await import(pathToFileURL(path.join(process.cwd(), 'public/js/modules/state.js')).href);
    const { checkLimits } = await loadActionHelpers();

    state.isAdmin = true;
    state.currentChildId = 201;
    state.monthlyLimit = 1000;
    state.shopItems = [{ id: 2001, type: 'activity', name: 'Поход в парк' }];
    state.history = [];
    state.requests = [{
        id: 9001,
        childId: 201,
        requestType: 'shop_purchase',
        itemId: 2001,
        moneyAmount: 700,
        status: 'pending',
        date: new Date().toISOString()
    }];

    const result = checkLimits(state.shopItems[0], 700, {
        childIdOverride: 201,
        excludeRequestId: 9001
    });

    assert.equal(result, null);
});