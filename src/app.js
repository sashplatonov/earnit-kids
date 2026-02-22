const http = require('http');
const path = require('path');

// Load environment variables
require('dotenv').config({ path: path.join(__dirname, '../.env') });

const config = require('./config');
const { setSecurityHeaders } = require('./middleware/security');
const apiRoutes = require('./routes/api');
const staticRouter = require('./routes/staticRouter');
const { handleMagicLink } = require('./controllers/apiController');
const viewController = require('./controllers/viewController');
const { testConnection } = require('./db/connection');
const { logStartupStats } = require('./utils/stats-logger');


const server = http.createServer(async (req, res) => {
    setSecurityHeaders(res);
    console.log(`${new Date().toISOString()} ${req.method} ${req.url}`);

    if (staticRouter.handleCors(req, res)) return;

    try {
        const { rateLimit } = require('./utils/rateLimiter');
        if (req.url.startsWith('/api/') && rateLimit(req)) {
            res.writeHead(429, { 'Content-Type': 'application/json' });
            return res.end(JSON.stringify({ error: 'Too Many Requests' }));
        }

        const [pathOnly] = req.url.split('?');

        if (pathOnly.startsWith('/login-child/')) {
            return await handleMagicLink(req, res);
        }

        if (!pathOnly.startsWith('/api/')) {
            return await staticRouter.routeStatic(pathOnly, req, res, viewController);
        }

        await apiRoutes(req, res);
    } catch (err) {
        console.error('Server Catch Error:', err);
        res.writeHead(500, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Internal Server Error' }));
    }
});

const { validateEnv, initDatabase } = require('./utils/startup-init');

async function startServer() {
    validateEnv();
    await initDatabase();

    server.listen(config.PORT, async () => {
        console.log(`🪙 Coin Shop Server running at http://localhost:${config.PORT}`);
        await logStartupStats();
    });
}

startServer().catch(err => {
    console.error('Failed to start server:', err.message);
    process.exit(1);
});
