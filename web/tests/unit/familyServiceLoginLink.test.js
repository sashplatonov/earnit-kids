const test = require('node:test');
const assert = require('node:assert/strict');
const proxyquire = require('proxyquire');

test('getChildLoginLink prefers forwarded host when present', async () => {
    const familyService = proxyquire('../../src/services/familyService', {
        '../db/familyRepository': {
            findById: async () => ({
                children: [{ id: 7, token: 'child-token-7' }]
            })
        },
        '../db/familyDataRepository': {
            DEFAULT_FAMILY_DATA: {}
        },
        './pushService': {},
        '../utils/logger': {
            createLogger: () => ({
                error() {},
                warn() {},
                info() {},
                debug() {}
            })
        }
    });

    const link = await familyService.getChildLoginLink('family-1', 7, {
        headers: {
            host: 'legacy-api:3000',
            'x-forwarded-host': 'earnit-kids.example.com',
            'x-forwarded-proto': 'https'
        }
    });

    assert.equal(link, 'https://earnit-kids.example.com/login-child/child-token-7');
});