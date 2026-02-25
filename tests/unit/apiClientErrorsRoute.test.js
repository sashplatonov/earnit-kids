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
        '../controllers/clientErrorController': {
            handleClientError: async (_ctx, _req, res) => {
                state.clientErrorHandlerCalled = true;
                res.writeHead(202, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ success: true }));
            }
        },
        '../middleware/body-parser': {
            middleware: async () => {
                state.parseMiddlewareCalled = true;
            }
        },
        '../utils/controllerUtils': {
            createRouteContext: () => ({
                method: 'POST',
                pathname: '/api/client-errors',
                role: null,
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
        '../controllers/superAdminController': {
            getSuperFamiliesList: async () => [],
            handleSuperAdminAPI: async () => {}
        },
        '../services/baseDataService': { loadBaseData: () => ({}) },
        '../utils/authUtils': { validateCsrf: () => true, signToken: () => 'token' }
    });
}

test('POST /api/client-errors is available without auth and reaches client error handler', async () => {
    const state = {
        parseMiddlewareCalled: false,
        clientErrorHandlerCalled: false
    };
    const apiRoutes = buildApiRoutes(state);

    const req = {
        method: 'POST',
        url: '/api/client-errors',
        headers: { host: 'localhost:3000' }
    };
    const res = createResponseMock();

    await apiRoutes(req, res);

    assert.strictEqual(state.parseMiddlewareCalled, true);
    assert.strictEqual(state.clientErrorHandlerCalled, true);
    assert.strictEqual(res.statusCode, 202);
});
