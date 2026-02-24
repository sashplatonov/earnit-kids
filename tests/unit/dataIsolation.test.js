const test = require('node:test');
const assert = require('node:assert');
const proxyquire = require('proxyquire').noCallThru();
const { URL } = require('url');

function createQueryStub() {
    const calls = [];
    const stub = async (sql, params) => {
        calls.push({ sql, params });
        if (sql.startsWith('SELECT balance FROM children')) {
            return { rows: [{ balance: 100 }] };
        }
        if (/^(INSERT|UPDATE|DELETE)/i.test(sql.trim())) {
            return { rowCount: 1 };
        }
        return { rows: [] };
    };
    stub.calls = calls;
    return stub;
}

function loadFamilyDataRepository(queryStub) {
    return proxyquire('../../src/db/familyDataRepository', {
        './connection': { query: queryStub },
        './familyRepository': { getDbId: async () => 1 },
        './syncRepository': {
            syncBalances: async () => {},
            syncTasks: async () => {},
            syncShop: async () => {},
            syncHistory: async () => {},
            syncRequests: async () => {},
            syncFriends: async () => {}
        },
        './familyDataMappers': {
            val: (v, d) => (v === undefined || v === null) ? d : v,
            mapTask: (row) => row,
            mapShopItem: (row) => row,
            mapHistoryEntry: (row) => row,
            mapRequest: (row) => row
        },
        '../utils/Cache': {
            get: () => null,
            set: () => {},
            invalidatePrefix: () => {}
        },
        './analyticsRepository': {
            getAnalyticsData: async () => ({})
        }
    });
}

function loadChildRepository(queryStub) {
    return proxyquire('../../src/db/childRepository', {
        './connection': { query: queryStub }
    });
}

function loadFamilyController(familyServiceMock, sendJSON) {
    return proxyquire('../../src/controllers/familyController', {
        '../services/familyService': familyServiceMock,
        '../utils/controllerUtils': { sendJSON }
    });
}

test('child sees only own tasks', async () => {
    const queryStub = createQueryStub();
    const familyDataRepository = loadFamilyDataRepository(queryStub);

    await familyDataRepository.getFamilyData('family-1', 77);

    const taskCall = queryStub.calls.find(call => call.sql.includes('FROM tasks'));
    assert.ok(taskCall, 'Tasks query should be executed');
    assert.ok(taskCall.sql.includes('AND t.child_id = $2'), 'Tasks query must filter by child_id');
    assert.deepStrictEqual(taskCall.params, [1, 77]);
});

test('child sees only own shop items', async () => {
    const queryStub = createQueryStub();
    const familyDataRepository = loadFamilyDataRepository(queryStub);

    await familyDataRepository.getFamilyData('family-1', 88);

    const shopCall = queryStub.calls.find(call => call.sql.includes('FROM shop_items'));
    assert.ok(shopCall, 'Shop query should be executed');
    assert.ok(shopCall.sql.includes('AND s.child_id = $2'), 'Shop query must filter by child_id');
    assert.deepStrictEqual(shopCall.params, [1, 88]);
});

test('admin cannot access child of another family', async () => {
    const sendLog = [];
    const familyController = loadFamilyController({
        loadFamilyData: async () => ({ balance: 0 }),
        saveFamilyData: async () => true,
        updateLastActivity: async () => {},
        loadFamilies: async () => ({
            families: {
                'family-1': { children: [{ id: 1 }] }
            }
        }),
        updateNickname: async () => ({ success: true }),
        searchByNickname: async () => [],
        addFriend: async () => ({ success: true }),
        getFriendsData: async () => [],
        addChild: async () => ({ success: true }),
        deleteChild: async () => true,
        updateChildSettings: async () => ({ success: true }),
        getPaginatedHistory: async () => ({ data: [], total: 0 }),
        getPaginatedRequests: async () => ({ data: [], total: 0 })
    }, (res, data, status = 200) => {
        sendLog.push({ res, data, status });
    });

    const ctx = {
        familyId: 'family-1',
        role: 'admin',
        urlObj: new URL('https://example.com/api/data?childId=99')
    };

    await familyController.handleDataGet(ctx, {}, {});

    assert.strictEqual(sendLog.length, 1, 'Expected a single response');
    assert.strictEqual(sendLog[0].status, 404);
    assert.strictEqual(sendLog[0].data.error, 'Child not found');
});

test('updateBalance rejects foreign child', async () => {
    const queryStub = createQueryStub();
    const familyDataRepository = loadFamilyDataRepository(queryStub);

    await familyDataRepository.updateBalance('family-x', 500, 5);

    const updateCall = queryStub.calls.find(call => call.sql.includes('UPDATE children SET balance'));
    assert.ok(updateCall.sql.includes('AND family_id=(SELECT id FROM families WHERE family_id=$3)'), 'Balance update must scope by family_id');
    assert.strictEqual(updateCall.params[2], 'family-x');
});

test('updateRequestStatus rejects foreign request', async () => {
    const queryStub = createQueryStub();
    const familyDataRepository = loadFamilyDataRepository(queryStub);

    await familyDataRepository.updateRequestStatus('family-y', 10, 'approved');

    const requestCall = queryStub.calls.find(call => call.sql.includes('UPDATE requests SET status'));
    assert.ok(requestCall.sql.includes('AND family_id=(SELECT id FROM families WHERE family_id=$3)'), 'Request update must scope by family_id');
    assert.strictEqual(requestCall.params[2], 'family-y');
});

test('searchByNickname does not expose family_id', async () => {
    const calls = [];
    const queryStub = async (sql, params) => {
        calls.push({ sql, params });
        return { rows: [{ id: 1, name: 'Kiddo', family_id: 'family-secret' }] };
    };
    const childRepository = loadChildRepository(queryStub);

    const result = await childRepository.searchByNickname('abc');

    assert.strictEqual(result[0].id, 1);
    assert.strictEqual(result[0].nickname, 'Kiddo');
    assert.ok(!Object.prototype.hasOwnProperty.call(result[0], 'familyId'));
});

test('syncBalances scoped by family', async () => {
    const calls = [];
    const client = {
        query: async (sql, params) => {
            calls.push({ sql, params });
            return { rowCount: 1 };
        }
    };

    const syncRepository = require('../../src/db/syncRepository');

    await syncRepository.syncBalances({ client, data: { balance: 10 }, actingChildId: 2, dbId: 99 });
    assert.ok(calls[0].sql.includes('AND family_id = $3'));
    assert.strictEqual(calls[0].params[2], 99);

    calls.length = 0;
    await syncRepository.syncBalances({ client, data: { children: [{ id: 7, balance: 40 }] }, actingChildId: null, dbId: 99 });
    assert.ok(calls[0].sql.includes('AND family_id = $3'));
    assert.strictEqual(calls[0].params[2], 99);
});
