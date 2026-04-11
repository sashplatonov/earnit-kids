const test = require('node:test');
const assert = require('node:assert/strict');
const proxyquire = require('proxyquire');

function createMockResponse() {
    const state = {
        statusCode: null,
        headers: {},
        body: ''
    };

    return {
        state,
        writeHead(statusCode, headers = {}) {
            state.statusCode = statusCode;
            state.headers = headers;
        },
        end(body = '') {
            state.body = body;
        }
    };
}

test('serveLogin treats JWT claims as source of truth when helper cookies are missing', async () => {
    const viewController = proxyquire('../../src/controllers/viewController', {
        '../services/familyService': {
            findFamilyByEmail: async () => ({ id: 'family_1', isSuperAdmin: false })
        },
        '../utils/authUtils': {
            verifyToken: () => ({ email: 'kid@example.com', role: 'child', familyId: 'family_1' })
        }
    });

    const req = {
        url: '/login.html',
        headers: {
            cookie: 'app_auth=fake.jwt.token'
        }
    };
    const res = createMockResponse();

    await viewController.serveLogin(req, res);

    assert.equal(res.state.statusCode, 302);
    assert.equal(res.state.headers.Location, '/');
});
