/** @file Startup Init utility helpers */
const { migrate } = require('../../scripts/migrate');
const { runDataMigration } = require('../../scripts/migrate-data');
const { testConnection } = require('../db/connection');
const { createLogger } = require('./logger');
const logger = createLogger('startupInit');

function getRetryCount() {
    const parsed = Number.parseInt(process.env.DB_INIT_RETRIES || '10', 10);
    return Number.isInteger(parsed) && parsed > 0 ? parsed : 10;
}

function getRetryDelayMs() {
    const parsed = Number.parseInt(process.env.DB_INIT_RETRY_DELAY_MS || '2000', 10);
    return Number.isInteger(parsed) && parsed >= 0 ? parsed : 2000;
}

function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

function validateEnv() {
    const required = ['SUPER_ADMIN_EMAIL', 'SUPER_ADMIN_PASSWORD'];
    const missing = required.filter(key => !process.env[key]);

    if (missing.length > 0) {
        logger.fatal({ missing }, 'Missing required environment variables');
        process.exit(1);
    }
}

async function initDatabase() {
    try {
        await initializeDatabase();
    } catch (err) {
        logger.fatal({ err: err.message }, 'Database initialization failed');
        process.exit(1);
    }
}

async function waitForDatabaseConnection(options = {}) {
    const attempts = options.attempts ?? getRetryCount();
    const retryDelayMs = options.retryDelayMs ?? getRetryDelayMs();
    const testConnectionFn = options.testConnectionFn ?? testConnection;
    const loggerInstance = options.logger ?? logger;

    for (let attempt = 1; attempt <= attempts; attempt += 1) {
        loggerInstance.info({ attempt, attempts }, 'Testing database connection');

        try {
            await testConnectionFn();
            return;
        } catch (err) {
            if (attempt === attempts) {
                throw err;
            }

            loggerInstance.warn({
                attempt,
                attempts,
                retryDelayMs,
                err: err.message
            }, 'Database connection failed during startup, retrying');

            await sleep(retryDelayMs);
        }
    }
}

async function initializeDatabase(options = {}) {
    const loggerInstance = options.logger ?? logger;
    const migrateFn = options.migrateFn ?? migrate;
    const dataMigrationFn = options.dataMigrationFn ?? runDataMigration;

    await waitForDatabaseConnection(options);
    loggerInstance.info('Database connection successful');

    await migrateFn();
    await dataMigrationFn();

    loggerInstance.info('Super admin credentials loaded');
}

module.exports = {
    validateEnv,
    initDatabase,
    waitForDatabaseConnection,
    initializeDatabase
};
