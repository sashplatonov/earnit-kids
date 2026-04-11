const test = require('node:test');
const assert = require('node:assert/strict');

const { validateCsrf } = require('../../src/utils/authUtils');

test('validateCsrf accepts matching X-CSRF-Token header', () => {
    const req = {
        headers: {
            'x-csrf-token': 'token-1',
            host: 'localhost:3001'
        }
    };
    assert.equal(validateCsrf(req, 'token-1'), true);
});

test('validateCsrf accepts same-origin POST when X-CSRF-Token header is missing', () => {
    const req = {
        headers: {
            host: 'localhost:3001',
            origin: 'http://localhost:3001'
        }
    };
    assert.equal(validateCsrf(req, 'token-1'), true);
});

test('validateCsrf accepts same-host referer when origin is missing', () => {
    const req = {
        headers: {
            host: 'localhost:3001',
            referer: 'http://localhost:3001/'
        }
    };
    assert.equal(validateCsrf(req, 'token-1'), true);
});

test('validateCsrf accepts same-origin fetch metadata when origin/referer are missing', () => {
    const req = {
        headers: {
            host: 'localhost:3001',
            'sec-fetch-site': 'same-origin'
        }
    };
    assert.equal(validateCsrf(req, 'token-1'), true);
});

test('validateCsrf rejects cross-origin POST when X-CSRF-Token header is missing', () => {
    const req = {
        headers: {
            host: 'localhost:3001',
            origin: 'http://evil.example'
        }
    };
    assert.equal(validateCsrf(req, 'token-1'), false);
});

test('validateCsrf rejects when no CSRF proof headers are present', () => {
    const req = {
        headers: {
            host: 'localhost:3001'
        }
    };
    assert.equal(validateCsrf(req, 'token-1'), false);
});
