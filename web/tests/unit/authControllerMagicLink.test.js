const test = require('node:test');
const assert = require('node:assert/strict');
const proxyquire = require('proxyquire');

function createMockResponse() {
    const state = { statusCode: null, headers: null, body: '' };
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

test('handleMagicLink parses token with trailing slash and query string', async () => {
    let capturedToken = null;
    const authController = proxyquire('../../src/controllers/authController', {
        '../services/authService': {
            authenticateUser: async () => ({ success: false }),
            authenticateChildByToken: async (token) => {
                capturedToken = token;
                return { success: false };
            },
            registerFamily: async () => ({ success: false }),
            changePassword: async () => ({ success: false }),
            recoverPassword: async () => ({ success: false }),
            resetPasswordWithToken: async () => ({ success: false }),
            verifyEmailToken: async () => ({ success: false })
        }
    });

    const req = { url: '/login-child/abc123/?src=msg' };
    const res = createMockResponse();

    await authController.handleMagicLink(req, res);

    assert.equal(capturedToken, 'abc123');
    assert.equal(res.state.statusCode, 302);
    assert.equal(res.state.headers.Location, '/login.html?error=invalid_token');
});
