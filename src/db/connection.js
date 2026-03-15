/** @file Connection PostgreSQL data access */
const { Pool } = require('pg');
const { getSearchPath } = require('./schema');

// Load environment variables
require('dotenv').config();

const connectionString = process.env.NODE_ENV === 'test' && process.env.TEST_DATABASE_URL
    ? process.env.TEST_DATABASE_URL
    : process.env.DATABASE_URL || buildConnectionStringFromEnv();

const { createLogger } = require('../utils/logger');
const pool = new Pool({
    connectionString,
    options: `-c search_path=${getSearchPath()} -c client_encoding=UTF8`,
    ssl: process.env.DB_SSL === 'false' ? false : (process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : undefined),
    // Database connection pool tuning
    max: 20, // Maximum number of clients in the pool
    idleTimeoutMillis: 30000, // How long a client is allowed to remain idle before being closed
    connectionTimeoutMillis: 2000, // How long to wait for a connection
    maxUses: 7500 // Close and replace a connection after it has been used this many times
});

const logger = createLogger('dbConnection');

// Test connection on startup
pool.on('connect', () => {
    logger.debug('New PostgreSQL pool connection');
});

pool.on('error', (err) => {
    logger.error({ err: err.message }, 'PostgreSQL pool error');
});

/**
 * Execute a query with parameters
 * @param {string} text - SQL query
 * @param {Array} params - Query parameters
 * @returns {Promise<QueryResult>}
 */
async function query(text, params) {
    const start = Date.now();
    try {
        const res = await pool.query(text, params);
        const duration = Date.now() - start;

        // Log all queries if DEBUG_SQL is enabled
        if (process.env.DEBUG_SQL) {
            logger.debug({ text, duration: `${duration}ms`, rows: res.rowCount }, 'Database query executed');
        }

        // Log slow queries (> 200ms) with warning level
        if (duration > 200) {
            logger.warn({
                text,
                params: params ? params.length : 0,
                duration: `${duration}ms`,
                rows: res.rowCount
            }, 'Slow database query detected');

            // In development, we could automatically run EXPLAIN ANALYZE
            if (process.env.NODE_ENV !== 'production' && process.env.AUTO_EXPLAIN) {
                runExplain(text, params);
            }
        }

        return res;
    } catch (err) {
        const duration = Date.now() - start;
        logger.error({ text, duration: `${duration}ms`, err: err.message }, 'Database query failed');
        throw err;
    }
}

/**
 * Run EXPLAIN ANALYZE for a query
 */
async function runExplain(text, params) {
    try {
        const explainRes = await pool.query(`EXPLAIN ANALYZE ${text}`, params);
        logger.info({
            text,
            explanation: explainRes.rows.map(r => r['QUERY PLAN']).join('\n')
        }, 'Query Explanation (EXPLAIN ANALYZE)');
    } catch (explainErr) {
        logger.error({ err: explainErr.message }, 'Failed to run EXPLAIN ANALYZE');
    }
}

/**
 * Get a client from the pool for transactions
 * @returns {Promise<PoolClient>}
 */
async function getClient() {
    return pool.connect();
}

/**
 * Test database connection
 * @returns {Promise<boolean>}
 */
async function testConnection() {
    await pool.query('SELECT NOW()');
    return true;
}

module.exports = {
    pool,
    query,
    getClient,
    testConnection
};

/**
 * Build a fallback PostgreSQL connection string from explicit env vars.
 * This lets local `npm start` runs target localhost without requiring an explicit DATABASE_URL.
 */
function buildConnectionStringFromEnv() {
    const host = readEnvValue(['DATABASE_HOST', 'POSTGRES_HOST'], 'localhost');
    const port = readEnvValue(['DATABASE_PORT', 'POSTGRES_PORT'], '5432');
    const database = readEnvValue(['DATABASE_NAME', 'POSTGRES_DB'], 'earnit_kids');
    const user = readEnvValue(['DATABASE_USER', 'POSTGRES_USER'], 'postgres');
    const password = readEnvValue(['DATABASE_PASSWORD', 'POSTGRES_PASSWORD'], '');

    const encodedUser = encodeURIComponent(user);
    const auth = password
        ? `${encodedUser}:${encodeURIComponent(password)}`
        : encodedUser;

    return `postgresql://${auth}@${host}:${port}/${database}`;
}

function readEnvValue(keys, fallback) {
    for (const key of keys) {
        if (process.env[key]) {
            return process.env[key];
        }
    }
    return fallback;
}
