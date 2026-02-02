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
        // Magic Link
        if (url.startsWith('/login-child/')) {
            await handleMagicLink(req, res);
            return;
        }

        // Static Files and Views
        if (!url.startsWith('/api/')) {
            if (url === '/' || url === '/index.html') {
                return serveIndex(req, res);
            }
            if (url === '/login.html') {
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
});
