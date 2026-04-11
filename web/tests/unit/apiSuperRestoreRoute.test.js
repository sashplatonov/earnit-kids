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
        '../middleware/body-parser': {
            middleware: async () => {
                state.parseMiddlewareCalled = true;
                throw new Error('JSON parser must be skipped for binary restore');
            }
        },
        '../controllers/superAdminController': {
            getSuperFamiliesList: async () => [],
            handleSuperAdminAPI: async (_req, res) => {
                state.superHandlerCalled = true;
                res.writeHead(200, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ success: true }));
            }
        },
        '../utils/controllerUtils': {
            createRouteContext: () => ({
                method: 'POST',
                pathname: '/api/super/db-restore',
                role: 'super_admin',
                familyId: null,
                csrfToken: null
            }),
            sendJSON: (res, data, status = 200) => {
                res.writeHead(status, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify(data));
            }
        },
        '../controllers/familyController': {},
        '../controllers/childController': {},
        '../controllers/analyticsController': {},
        '../controllers/friendsController': {},
        '../controllers/authController': {},
        '../services/baseDataService': { loadBaseData: () => ({}) },
        '../utils/authUtils': { validateCsrf: () => true }
    });
}

test('POST /api/super/db-restore skips JSON body parser and reaches super admin handler', async () => {
    const state = {
        parseMiddlewareCalled: false,
        superHandlerCalled: false
    };
    const apiRoutes = buildApiRoutes(state);

    const req = {
        method: 'POST',
        url: '/api/super/db-restore',
        headers: { host: 'localhost:3000' }
    };
    const res = createResponseMock();

    await apiRoutes(req, res);

    assert.strictEqual(state.parseMiddlewareCalled, false);
    assert.strictEqual(state.superHandlerCalled, true);
    assert.strictEqual(res.statusCode, 200);
});
