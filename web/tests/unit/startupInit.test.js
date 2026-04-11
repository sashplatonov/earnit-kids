const test = require('node:test');
const assert = require('node:assert/strict');
const proxyquire = require('proxyquire');

function createLogger() {
    return {
        info() {},
        warn() {},
        fatal() {}
    };
}

test('waitForDatabaseConnection retries until the database is reachable', async () => {
    let attempts = 0;

    const { waitForDatabaseConnection } = proxyquire('../../src/utils/startup-init', {
        '../../scripts/migrate': { migrate: async () => {} },
        '../../scripts/migrate-data': { runDataMigration: async () => {} },
        '../db/connection': { testConnection: async () => true },
        './logger': { createLogger }
    });

    await waitForDatabaseConnection({
        attempts: 3,
        retryDelayMs: 0,
        logger: createLogger(),
        testConnectionFn: async () => {
            attempts += 1;

            if (attempts < 3) {
                throw new Error('connect ECONNREFUSED');
            }
        }
    });

    assert.equal(attempts, 3);
});

test('initializeDatabase runs migrations once after connection succeeds', async () => {
    let attempts = 0;
    let migrateCalls = 0;
    let dataMigrationCalls = 0;

    const { initializeDatabase } = proxyquire('../../src/utils/startup-init', {
        '../../scripts/migrate': { migrate: async () => {} },
        '../../scripts/migrate-data': { runDataMigration: async () => {} },
        '../db/connection': { testConnection: async () => true },
        './logger': { createLogger }
    });

    await initializeDatabase({
        attempts: 2,
        retryDelayMs: 0,
        logger: createLogger(),
        testConnectionFn: async () => {
            attempts += 1;

            if (attempts === 1) {
                throw new Error('connect ECONNREFUSED');
            }
        },
        migrateFn: async () => {
            migrateCalls += 1;
        },
        dataMigrationFn: async () => {
            dataMigrationCalls += 1;
        }
    });

    assert.equal(attempts, 2);
    assert.equal(migrateCalls, 1);
    assert.equal(dataMigrationCalls, 1);
});

test('waitForDatabaseConnection throws after the last retry', async () => {
    const { waitForDatabaseConnection } = proxyquire('../../src/utils/startup-init', {
        '../../scripts/migrate': { migrate: async () => {} },
        '../../scripts/migrate-data': { runDataMigration: async () => {} },
        '../db/connection': { testConnection: async () => true },
        './logger': { createLogger }
    });

    await assert.rejects(
        waitForDatabaseConnection({
            attempts: 2,
            retryDelayMs: 0,
            logger: createLogger(),
            testConnectionFn: async () => {
                throw new Error('db still unavailable');
            }
        }),
        /db still unavailable/
    );
});