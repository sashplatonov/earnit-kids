const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const { pathToFileURL } = require('node:url');

async function loadState() {
    const modulePath = pathToFileURL(path.join(process.cwd(), 'public/js/modules/state.js')).href;
    return import(modulePath);
}

async function loadActionHelpers() {
    global.window = { CONFIG: {} };
    global.document = { cookie: '' };
    const modulePath = pathToFileURL(path.join(process.cwd(), 'public/js/modules/action-helpers.js')).href;
    return import(`${modulePath}?t=${Date.now()}`);
}

function resetState(state) {
    Object.assign(state, {
        isAdmin: true,
        role: 'admin',
        balance: 0,
        tasks: [],
        shopItems: [],
        history: [],
        requests: [],
        friends: [],
        childNickname: null,
        isPinSet: false,
        familyId: null,
        monthlyLimit: 10000,
        dailyCoinLimit: 0,
        baseData: { tasks: [], products: [] },
        children: [],
        currentChildId: null,
        isLoading: false
    });
}

function installFetchStub(requests, responseFactory) {
    global.fetch = async (url, options = {}) => {
        requests.push({ url, options });
        return responseFactory();
    };
}

function populateQueuedSnapshot(state) {
    state.currentChildId = 10;
    state.balance = 45;
    state.tasks = [{ id: 101, childId: 10, name: 'Read' }];
    state.history = [{ id: 301, childId: 10, type: 'spend', amount: 5 }];
    state.children = [
        { id: 10, name: 'Alice', balance: 45 },
        { id: 11, name: 'Bob', balance: 12 }
    ];
}

function mutateStateAfterQueue(state) {
    state.currentChildId = 11;
    state.balance = 12;
    state.tasks = [{ id: 202, childId: 11, name: 'Toy' }];
    state.history = [];
    state.children[0].balance = 0;
}

function assertQueuedSnapshotPayload(requests) {
    const payload = JSON.parse(requests[0].options.body);

    assert.equal(payload.childId, 10);
    assert.equal(payload.balance, 45);
    assert.deepEqual(payload.tasks, [{ id: 101, childId: 10, name: 'Read' }]);
    assert.deepEqual(payload.history, [{ id: 301, childId: 10, type: 'spend', amount: 5 }]);
    assert.equal(payload.children.find(child => child.id === 10).balance, 45);
}

test('scheduleSave persists the queued snapshot even when state changes before the request settles', async () => {
    const { state } = await loadState();
    const { scheduleSave, flushPendingSave } = await loadActionHelpers();
    const requests = [];

    resetState(state);
    installFetchStub(requests, () => ({
            ok: true,
            json: async () => ({ success: true })
        }));

    populateQueuedSnapshot(state);

    scheduleSave();
    mutateStateAfterQueue(state);

    await flushPendingSave();

    assert.equal(requests.length, 1);
    assertQueuedSnapshotPayload(requests);
});

test('flushPendingSave waits for an in-flight immediate save', async () => {
    const { state } = await loadState();
    const { scheduleSave, flushPendingSave } = await loadActionHelpers();
    const requests = [];
    let releaseRequest;
    const requestFinished = new Promise(resolve => {
        releaseRequest = resolve;
    });

    resetState(state);
    global.fetch = async (url, options = {}) => {
        requests.push({ url, options });
        await requestFinished;
        return {
            ok: true,
            text: async () => JSON.stringify({ success: true })
        };
    };

    state.currentChildId = 10;
    state.balance = 9;
    state.children = [{ id: 10, name: 'Alice', balance: 9 }];

    scheduleSave();
    const flushPromise = flushPendingSave({ keepalive: true });
    releaseRequest();
    await flushPromise;

    assert.equal(requests.length, 1);
    assert.equal(requests[0].options.keepalive, false);
    const payload = JSON.parse(requests[0].options.body);
    assert.equal(payload.childId, 10);
    assert.equal(payload.balance, 9);
});