/** @file Database health checks for the super-admin dashboard */
const { testConnection } = require('../db/connection');

async function getDbHealth() {
    const result = {
        connected: false,
        pingMs: null,
        lastError: null
    };

    const start = Date.now();
    try {
        await testConnection();
        result.connected = true;
        result.pingMs = Date.now() - start;
    } catch (err) {
        result.lastError = err.message;
    }

    return result;
}

module.exports = {
    getDbHealth
};
