const test = require('node:test');
const assert = require('node:assert');
const proxyquire = require('proxyquire').noCallThru();

const mockData = {
    testfamily: {
        id: 'testfamily',
        name: 'My Family',
        children: [
            { id: 1, name: 'Alice' }
        ],
        tasks: [],
        shop: []
    }
};

const familyService = proxyquire('../../src/services/familyService', {
    '../db/familyRepository': {
        findAll: async () => ({ families: mockData }),
        findById: async (id) => mockData[id] || null,
        update: async (id, data) => true,
        createChild: async () => ({ id: 2, name: 'Bob' }),
        updateChild: async () => true
    },
    '../db/familyDataRepository': {
        getData: async (id) => mockData[id] || null,
        saveData: async (id, data) => true
    },
    './pushService': {
        notifyFamilyChanges: async () => true
    }
});

test('familyService.addChild', async () => {
    // This is a minimal test to show the pattern for unit-testing the service
    const res = await familyService.addChild('testfamily', 'Bob');
    assert.strictEqual(res.success, true);
});

test('familyService.updateNickname', async () => {
    const res = await familyService.updateNickname('testfamily', 1, 'Alien');
    assert.strictEqual(res.success, true);
});
