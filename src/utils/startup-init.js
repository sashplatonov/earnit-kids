/** @file Startup Init utility helpers */
const { migrate } = require('../../scripts/migrate');
const { runDataMigration } = require('../../scripts/migrate-data');
const { testConnection } = require('../db/connection');
const { createLogger } = require('./logger');
const logger = createLogger('startupInit');

function validateEnv() {
    const required = ['SUPER_ADMIN_EMAIL', 'SUPER_ADMIN_PASSWORD'];
    const missing = required.filter(key => !process.env[key]);

    if (missing.length > 0) {
        logger.fatal({ missing }, 'Missing required environment variables');
        process.exit(1);
    }
}

async function initDatabase() {
    logger.info('Testing database connection');
    try {
        await testConnection();
        logger.info('Database connection successful');

        await migrate();
        await runDataMigration();

        logger.info('Super admin credentials loaded');
    } catch (err) {
        logger.fatal({ err: err.message }, 'Database initialization failed');
        process.exit(1);
    }
}

module.exports = {
    validateEnv,
    initDatabase
};
