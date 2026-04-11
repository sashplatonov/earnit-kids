const test = require('node:test');
const assert = require('node:assert/strict');

const { buildAuthCookies } = require('../../src/controllers/authController');

test('buildAuthCookies keeps csrf_token readable for frontend CSRF header', () => {
    const cookies = buildAuthCookies({
        email: 'admin@example.com',
        role: 'admin',
        familyId: 42,
        maxAge: 3600
    });

    const csrfCookie = cookies.find(cookie => cookie.startsWith('csrf_token='));
    const authCookie = cookies.find(cookie => cookie.startsWith('app_auth='));

    assert.ok(csrfCookie, 'csrf cookie should exist');
    assert.ok(authCookie, 'auth cookie should exist');
    assert.ok(!csrfCookie.includes('HttpOnly'), 'csrf cookie must be readable by JS');
    assert.ok(authCookie.includes('HttpOnly'), 'auth cookie must remain HttpOnly');
    assert.ok(csrfCookie.includes('SameSite=Strict'));
});
