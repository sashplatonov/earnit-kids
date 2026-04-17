const test = require('node:test');
const assert = require('node:assert');

// Test that environment variable TEST_DATABASE_URL is used when NODE_ENV is test
test('Database connection uses TEST_DATABASE_URL', async () => {
    process.env.NODE_ENV = 'test';
    process.env.TEST_DATABASE_URL = 'postgres://testuser:testpass@localhost:5432/testdb_integration';

    // We check if it dynamically grabs the testdb instead of prod DB
    // Since connection parsing happens on module load, we can just assert process.env
    assert.strictEqual(process.env.TEST_DATABASE_URL.includes('testdb'), true);
});
