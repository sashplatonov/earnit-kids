const test = require('node:test');
const assert = require('node:assert/strict');

const { setSecurityHeaders } = require('../src/middleware/security');

test('setSecurityHeaders applies all expected headers', () => {
    const headers = {};
    const res = {
        setHeader(name, value) {
            headers[name] = value;
        },
    };

    setSecurityHeaders(res);

    assert.equal(headers['X-Content-Type-Options'], 'nosniff');
    assert.equal(headers['X-Frame-Options'], 'DENY');
    assert.equal(headers['X-XSS-Protection'], '1; mode=block');
    assert.equal(headers['Strict-Transport-Security'], 'max-age=31536000; includeSubDomains');
    assert.equal(headers['Referrer-Policy'], 'no-referrer');
});
