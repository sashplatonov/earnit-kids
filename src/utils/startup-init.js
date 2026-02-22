const { migrate } = require('../../scripts/migrate');
const { runDataMigration } = require('../../scripts/migrate-data');
const { testConnection } = require('../db/connection');

function validateEnv() {
    if (!process.env.SUPER_ADMIN_EMAIL || !process.env.SUPER_ADMIN_PASSWORD) {
        console.error('❌ CRITICAL ERROR: SUPER_ADMIN_EMAIL or SUPER_ADMIN_PASSWORD not set in environment variables');
        process.exit(1);
    }
}

async function initDatabase() {
    console.log('🔌 Testing database connection...');
    try {
        await testConnection();
        console.log('✅ Database connection successful');

        await migrate();
        await runDataMigration();

        console.log(`🔑 Super Admin credentials loaded: ${process.env.SUPER_ADMIN_EMAIL}`);
    } catch (err) {
        console.error('❌ Database or Migration error:', err.message);
        process.exit(1);
    }
}

module.exports = {
    validateEnv,
    initDatabase
};
