/** @file Database health checks for the super-admin dashboard */
const { testConnection } = require('../db/connection');
const { checkReserveDbConnection } = require('./backupService');

async function getDbHealth() {
    const result = {
        connected: false,
        pingMs: null,
        reserveConnected: false,
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

    try {
        const reserveStatus = await checkReserveDbConnection();
        result.reserveConnected = reserveStatus && reserveStatus.success;
        if (!reserveStatus.success && reserveStatus.error) {
            result.lastError = result.lastError || reserveStatus.error;
        }
    } catch (err) {
        result.reserveConnected = false;
        result.lastError = result.lastError || err.message;
    }

    return result;
}

module.exports = {
    getDbHealth
};
