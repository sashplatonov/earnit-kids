const test = require('node:test');
const assert = require('node:assert/strict');
const proxyquire = require('proxyquire');

test('getFamilyContext falls back to csrf_token cookie when JWT has no csrfToken claim', () => {
    const { getFamilyContext } = proxyquire('../../src/utils/controllerUtils', {
        '../controllers/viewController': {
            getCookies: () => ({
                app_auth: 'jwt-token',
                csrf_token: 'cookie-csrf-token'
            })
        },
        './authUtils': {
            verifyToken: () => ({
                familyId: 'family_1',
                role: 'admin',
                email: 'admin@example.com'
            })
        }
    });

    const context = getFamilyContext({ headers: { cookie: 'app_auth=jwt-token; csrf_token=cookie-csrf-token' } });

    assert.equal(context.familyId, 'family_1');
    assert.equal(context.role, 'admin');
    assert.equal(context.csrfToken, 'cookie-csrf-token');
});

test('getFamilyContext prefers csrf_token cookie over JWT csrfToken claim', () => {
    const { getFamilyContext } = proxyquire('../../src/utils/controllerUtils', {
        '../controllers/viewController': {
            getCookies: () => ({
                app_auth: 'jwt-token',
                csrf_token: 'cookie-csrf-token'
            })
        },
        './authUtils': {
            verifyToken: () => ({
                familyId: 'family_1',
                role: 'admin',
                email: 'admin@example.com',
                csrfToken: 'jwt-csrf-token'
            })
        }
    });

    const context = getFamilyContext({ headers: { cookie: 'app_auth=jwt-token; csrf_token=cookie-csrf-token' } });

    assert.equal(context.csrfToken, 'cookie-csrf-token');
});
