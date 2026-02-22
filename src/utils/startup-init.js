const { migrate } = require('../../scripts/migrate');
const { runDataMigration } = require('../../scripts/migrate-data');
const { testConnection } = require('../db/connection');

function validateEnv() {
    const required = ['SUPER_ADMIN_EMAIL', 'SUPER_ADMIN_PASSWORD'];
    const missing = required.filter(key => !process.env[key]);

    if (missing.length > 0) {
        console.error(`❌ CRITICAL ERROR: Missing required environment variables: ${missing.join(', ')}`);
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
