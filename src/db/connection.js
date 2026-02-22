const { Pool } = require('pg');

// Load environment variables
require('dotenv').config();

const connectionString = process.env.NODE_ENV === 'test' && process.env.TEST_DATABASE_URL
    ? process.env.TEST_DATABASE_URL
    : process.env.DATABASE_URL;

const pool = new Pool({
    connectionString,
    ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : undefined,
    // Database connection pool tuning
    max: 20, // Maximum number of clients in the pool
    idleTimeoutMillis: 30000, // How long a client is allowed to remain idle before being closed
    connectionTimeoutMillis: 2000, // How long to wait for a connection
    maxUses: 7500 // Close and replace a connection after it has been used this many times
});

// Test connection on startup
pool.on('connect', () => {
    console.log('📦 Connected to PostgreSQL database');
});

pool.on('error', (err) => {
    console.error('❌ PostgreSQL pool error:', err.message);
});

const logger = require('../utils/logger');

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
