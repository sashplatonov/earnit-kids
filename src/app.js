const http = require('http');
const path = require('path');
const fs = require('fs');

// Load environment variables manually
const envPath = path.join(__dirname, '../.env');
if (fs.existsSync(envPath)) {
    const envContent = fs.readFileSync(envPath, 'utf8');
    envContent.split('\n').forEach(line => {
        const trimmedLine = line.trim();
        if (trimmedLine && !trimmedLine.startsWith('#')) {
            const [key, ...valueParts] = trimmedLine.split('=');
            if (key) process.env[key.trim()] = valueParts.join('=').trim().replace(/^["']|["']$/g, '');
        }
    });
}

const config = require('./config');
const { setSecurityHeaders } = require('./middleware/security');
const apiRoutes = require('./routes/api');
const { handleMagicLink } = require('./controllers/apiController');
const { serveStatic, serveIndex, serveLogin, serveSuperAdmin } = require('./controllers/viewController');
const { loadFamilies, loadFamilyData } = require('./services/familyService');
const { loadBaseData } = require('./services/baseDataService');


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
                return serveIndex(req, res);
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

server.listen(config.PORT, () => {
    console.log(`🪙 Coin Shop Server running at http://localhost:${config.PORT}`);

    // DevOps Stats
    try {
        const familiesData = loadFamilies();
        const familyIds = Object.keys(familiesData.families);
        const shopStats = familyIds.reduce((acc, id) => {
            const data = loadFamilyData(id);
            acc.tasks += (data.tasks || []).length;
            acc.products += (data.shop || []).length;
            return acc;
        }, { tasks: 0, products: 0 });

        const catalog = loadBaseData();

        console.log('-------------------------------------------');
        console.log('📊 APP STARTUP STATISTICS:');
        console.log(`🏠 Total Shops (Families): ${familyIds.length}`);
        console.log(`✅ Total tasks in all shops: ${shopStats.tasks}`);
        console.log(`🎁 Total products in all shops: ${shopStats.products}`);
        console.log(`📚 Global Catalog: ${catalog.tasks.length} tasks, ${catalog.products.length} products`);
        console.log('-------------------------------------------');
    } catch (err) {
        console.error('Error generating startup stats:', err.message);
    }
});

