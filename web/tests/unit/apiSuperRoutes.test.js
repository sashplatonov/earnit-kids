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

function buildApiRoutes(state) {
    return proxyquire('../../src/routes/api', {
        '../controllers/superAdminController': {
            getSuperFamiliesList: async () => [],
            handleSuperAdminAPI: async (req, res) => {
                state.calls.push(req.url);
                res.writeHead(200, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ ok: true }));
            }
        },
        '../utils/controllerUtils': {
            createRouteContext: (req) => ({
                method: req.method,
                pathname: req.url.split('?')[0],
                role: 'super_admin',
                familyId: null,
                csrfToken: null
            }),
            sendJSON: (res, data, status = 200) => {
                res.writeHead(status, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify(data));
            }
        },
        '../middleware/body-parser': { middleware: async () => {} },
        '../controllers/familyController': {},
        '../controllers/childController': {},
        '../controllers/analyticsController': {},
        '../controllers/friendsController': {},
        '../controllers/authController': {},
        '../controllers/clientErrorController': {},
        '../services/baseDataService': { loadBaseData: () => ({}) },
        '../utils/authUtils': { validateCsrf: () => true }
    });
}

test('GET /api/super/system/overview is routed to super admin handler', async () => {
    const state = { calls: [] };
    const apiRoutes = buildApiRoutes(state);
    const req = { method: 'GET', url: '/api/super/system/overview', headers: { host: 'localhost:3000' } };
    const res = createResponseMock();

    await apiRoutes(req, res);

    assert.strictEqual(res.statusCode, 200);
    assert.deepStrictEqual(state.calls, ['/api/super/system/overview']);
});

test('GET /api/super/family/:id/data is routed to super admin handler', async () => {
    const state = { calls: [] };
    const apiRoutes = buildApiRoutes(state);
    const req = {
        method: 'GET',
        url: '/api/super/family/platonov_sash_gmail_com_1770116382827/data',
        headers: { host: 'localhost:3000' }
    };
    const res = createResponseMock();

    await apiRoutes(req, res);

    assert.strictEqual(res.statusCode, 200);
    assert.deepStrictEqual(state.calls, ['/api/super/family/platonov_sash_gmail_com_1770116382827/data']);
});
