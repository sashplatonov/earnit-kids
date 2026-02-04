const http = require('http');
const path = require('path');

// Load environment variables
require('dotenv').config({ path: path.join(__dirname, '../.env') });

const config = require('./config');
const { setSecurityHeaders } = require('./middleware/security');
const apiRoutes = require('./routes/api');
const { handleMagicLink } = require('./controllers/apiController');
const { serveStatic, serveIndex, serveLogin, serveSuperAdmin } = require('./controllers/viewController');
const { loadFamilies, loadFamilyData } = require('./services/familyService');
const { loadBaseData } = require('./services/baseDataService');
const { testConnection } = require('./db/connection');


const server = http.createServer(async (req, res) => {
    // Add security headers to all responses
    setSecurityHeaders(res);

    const url = req.url;
    const method = req.method;

    console.log(`${new Date().toISOString()} ${method} ${url}`);

    // CORS preflight
    if (method === 'OPTIONS') {
        res.writeHead(204, {
            'Access-Control-Allow-Origin': req.headers.origin || '*',
            'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
            'Access-Control-Allow-Headers': 'Content-Type',
            'Access-Control-Allow-Credentials': 'true'
        });
        res.end();
        return;
    }

    try {
        const [pathOnly, queryString] = url.split('?');

        // Magic Link
        if (pathOnly.startsWith('/login-child/')) {
            await handleMagicLink(req, res);
            return;
        }

        // Static Files and Views
        if (!pathOnly.startsWith('/api/')) {
            if (pathOnly === '/' || pathOnly === '/index.html') {
                return await serveIndex(req, res);
            }
            if (pathOnly === '/login.html') {
                return serveLogin(req, res);
            }
            return serveStatic(req, res);
        }

        // API Routes
        await apiRoutes(req, res);
    } catch (err) {
        console.error('Server Catch Error:', err);
        res.writeHead(500, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Internal Server Error' }));
    }
});

async function startServer() {
    // Validate Super Admin credentials
    if (!process.env.SUPER_ADMIN_EMAIL || !process.env.SUPER_ADMIN_PASSWORD) {
        console.error('❌ CRITICAL ERROR: SUPER_ADMIN_EMAIL or SUPER_ADMIN_PASSWORD not set in environment variables');
        process.exit(1);
    }

    // Test database connection
    console.log('🔌 Testing database connection...');
    try {
        await testConnection();
        console.log('✅ Database connection successful');
    } catch (err) {
        console.error('❌ Database connection failed!');
        console.error('Error details:', err);
        console.error('\n💡 Please check your DATABASE_URL in the .env file.');
        process.exit(1);
    }

    // Run migrations automatically
    try {
        const { migrate } = require('../scripts/migrate');
        const { runDataMigration } = require('../scripts/migrate-data');
        const familyRepository = require('./db/familyRepository');

        // 1. Run schema migrations
        await migrate();

        // 2. Run data migration (JSON -> DB)
        await runDataMigration();

        // Check super admin status
        console.log(`🔑 Super Admin credentials loaded: ${process.env.SUPER_ADMIN_EMAIL}`);

    } catch (err) {
        console.error('❌ Failed to run migrations or startup checks:', err.message);
        process.exit(1);
    }

    server.listen(config.PORT, async () => {
        console.log(`🪙 Coin Shop Server running at http://localhost:${config.PORT}`);

        // DevOps Stats
        try {
            const familiesData = await loadFamilies();
            const familyIds = Object.keys(familiesData.families);

            let tasksCount = 0;
            let productsCount = 0;

            for (const id of familyIds) {
                const data = await loadFamilyData(id);
                tasksCount += (data.tasks || []).length;
                productsCount += (data.shop || []).length;
            }

            const catalog = loadBaseData();

            console.log('-------------------------------------------');
            console.log('📊 APP STARTUP STATISTICS:');
            console.log(`🏠 Total Shops (Families): ${familyIds.length}`);
            console.log(`✅ Total tasks in all shops: ${tasksCount}`);
            console.log(`🎁 Total products in all shops: ${productsCount}`);
            console.log(`📚 Global Catalog: ${catalog.tasks.length} tasks, ${catalog.products.length} products`);
            console.log('-------------------------------------------');
        } catch (err) {
            console.error('Error generating startup stats:', err.message);
        }
    });
}

startServer().catch(err => {
    console.error('Failed to start server:', err.message);
    process.exit(1);
});
