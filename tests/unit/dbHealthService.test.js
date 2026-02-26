const test = require('node:test');
const assert = require('node:assert');
const proxyquire = require('proxyquire');

test('getDbHealth returns connected status when primary database is available', async () => {
    const { getDbHealth } = proxyquire('../../src/services/dbHealthService', {
        '../db/connection': { testConnection: async () => true }
    });

    const health = await getDbHealth();

    assert.strictEqual(health.connected, true);
    assert.strictEqual(typeof health.pingMs, 'number');
    assert.strictEqual(health.lastError, null);
});

test('getDbHealth returns disconnected status when primary database is down', async () => {
    const { getDbHealth } = proxyquire('../../src/services/dbHealthService', {
        '../db/connection': { testConnection: async () => { throw new Error('Primary down'); } }
    });

    const health = await getDbHealth();

    assert.strictEqual(health.connected, false);
    assert.strictEqual(health.pingMs, null);
    assert.strictEqual(health.lastError, 'Primary down');
});
