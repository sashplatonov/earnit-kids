const test = require('node:test');
const assert = require('node:assert');
const proxyquire = require('proxyquire');

function createResponseMock() {
    return {
        statusCode: 0,
        headers: {},
        body: '',
        writeHead(status, headers) {
            this.statusCode = status;
            this.headers = headers;
        },
        end(payload) {
            this.body = payload ? String(payload) : '';
        }
    };
}

function buildController(overrides = {}) {
    const systemOverview = overrides.systemOverview || (async () => ({
        process: { uptimeSec: 12, rssBytes: 100, heapUsedBytes: 40, heapTotalBytes: 50 },
        os: { loadAvg1: 0.3, loadAvg5: 0.2, loadAvg15: 0.1, cpuCount: 4, totalMemBytes: 1000, freeMemBytes: 400 }
    }));
    const httpMetrics = overrides.httpMetrics || (async () => ({
        summary: { requestsTotal: 1, errorsTotal: 0, errorRatePct: 0 },
        topEndpoints: [],
        latency: { p95Ms: null, p99Ms: null }
    }));
    const dbHealth = overrides.dbHealth || (async () => ({ connected: true, pingMs: 5, reserveConnected: true, lastError: null }));
    const logs = overrides.readLogs || (async () => []);
    return proxyquire('../../src/controllers/superAdminController', {
        '../services/systemStatsService': { getSystemOverview: systemOverview },
        '../services/httpMetricsService': { getHttpMetrics: httpMetrics },
        '../services/dbHealthService': { getDbHealth: dbHealth },
        '../services/logsService': { readLogs: logs },
        '../services/familyService': {
            loadFamilies: async () => ({ families: {} }),
            loadFamilyData: async () => ({ tasks: [], shop: [], history: [] }),
            saveFamilies: async () => true,
            saveFamilyData: async () => true,
            regenerateChildToken: async () => true
        },
        '../services/baseDataService': { loadBaseData: () => ({}) },
        '../services/backupService': {
            createBackup: () => {},
            restoreBackup: () => {},
            copyToReserve: () => {},
            checkReserveDbConnection: async () => ({ success: true })
        },
        '../middleware/body-parser': Object.assign(
            (req) => Promise.resolve({}),
            { middleware: async () => {}, parseBody: async () => ({}) }
        )
    });
}

test('GET /api/super/system/overview returns system data for super admin', async () => {
    const controller = buildController();
    const req = { method: 'GET', url: '/api/super/system/overview' };
    const res = createResponseMock();
    await controller.handleSuperAdminAPI(req, res, { role: 'super_admin' });
    assert.strictEqual(res.statusCode, 200);
    const payload = JSON.parse(res.body);
    assert.strictEqual(payload.success, true);
    assert.ok(payload.process);
    assert.ok(payload.os);
});

test('GET /api/super/system/logs rejects invalid level', async () => {
    const controller = buildController();
    const req = { method: 'GET', url: '/api/super/system/logs?level=invalid' };
    const res = createResponseMock();
    await controller.handleSuperAdminAPI(req, res, { role: 'super_admin' });
    assert.strictEqual(res.statusCode, 400);
    const payload = JSON.parse(res.body);
    assert.strictEqual(payload.success, false);
});

test('GET /api/super/system/logs rejects invalid limit', async () => {
    const controller = buildController();
    const req = { method: 'GET', url: '/api/super/system/logs?level=error&limit=9999' };
    const res = createResponseMock();
    await controller.handleSuperAdminAPI(req, res, { role: 'super_admin' });
    assert.strictEqual(res.statusCode, 400);
    const payload = JSON.parse(res.body);
    assert.strictEqual(payload.success, false);
});

test('GET /api/super/system/db returns degraded status when DB is unavailable', async () => {
    const controller = buildController({
        dbHealth: async () => ({
            connected: false,
            pingMs: null,
            reserveConnected: false,
            lastError: 'Database unavailable'
        })
    });
    const req = { method: 'GET', url: '/api/super/system/db' };
    const res = createResponseMock();
    await controller.handleSuperAdminAPI(req, res, { role: 'super_admin' });
    assert.strictEqual(res.statusCode, 200);
    const payload = JSON.parse(res.body);
    assert.strictEqual(payload.success, true);
    assert.strictEqual(payload.db.connected, false);
    assert.strictEqual(payload.db.lastError, 'Database unavailable');
});

test('Non-super admin access is forbidden for system overview', async () => {
    const controller = buildController();
    const req = { method: 'GET', url: '/api/super/system/overview' };
    const res = createResponseMock();
    await controller.handleSuperAdminAPI(req, res, { role: 'admin' });
    assert.strictEqual(res.statusCode, 403);
    const payload = JSON.parse(res.body);
    assert.strictEqual(payload.error, 'Forbidden');
});
