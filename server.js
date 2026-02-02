// Simple Node.js server with JSON file storage
const http = require('http');
const path = require('path');
const fs = require('fs');
const { handleAPI } = require('./src/apiHandlers');
const { loadData } = require('./src/dataService');

const PORT = process.env.PORT || 3000;

// MIME types for static files
const MIME_TYPES = {
    '.html': 'text/html; charset=utf-8',
    '.css': 'text/css; charset=utf-8',
    '.js': 'application/javascript; charset=utf-8',
    '.json': 'application/json; charset=utf-8',
    '.png': 'image/png',
    '.jpg': 'image/jpeg',
    '.ico': 'image/x-icon'
};

// Helper to parse cookies
function getCookies(req) {
    const list = {};
    const rc = req.headers.cookie;
    rc && rc.split(';').forEach((cookie) => {
        const parts = cookie.split('=');
        list[parts.shift().trim()] = decodeURI(parts.join('='));
    });
    return list;
}

// Serve static file
function serveStatic(req, res) {
    // Check if searching for root
    if (req.url === '/' || req.url === '/index.html') {
        serveIndex(res);
        return;
    }

    let filePath = path.join(__dirname, req.url);

    // Security check: Prevent directory traversal
    if (!filePath.startsWith(__dirname)) {
        res.writeHead(403);
        res.end('Forbidden');
        return;
    }

    const ext = path.extname(filePath);
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';

    fs.readFile(filePath, (err, content) => {
        if (err) {
            if (err.code === 'ENOENT') {
                res.writeHead(404);
                res.end('Not Found');
            } else {
                res.writeHead(500);
                res.end('Server Error');
            }
        } else {
            res.writeHead(200, { 'Content-Type': contentType });
            res.end(content);
        }
    });
}

// Serve login page
function serveLogin(res) {
    const loginPath = path.join(__dirname, 'views', 'login.html');
    fs.readFile(loginPath, 'utf8', (err, content) => {
        if (err) {
            res.writeHead(500);
            res.end('Error loading login page');
            return;
        }
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end(content);
    });
}

// Serve composed index.html
function serveIndex(res) {
    const componentOrder = [
        'head.html',
        'header.html',
        'nav.html',
        'main_start.html',
        'section_tasks.html',
        'section_requests.html',
        'section_shop.html',
        'section_history.html',
        'section_rules.html',
        'main_end.html',
        'modals.html',
        'scripts.html'
    ];

    const componentsDir = path.join(__dirname, 'views', 'components');
    let fullHtml = '';

    try {
        componentOrder.forEach(file => {
            const content = fs.readFileSync(path.join(componentsDir, file), 'utf8');
            fullHtml += content + '\n';
        });

        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end(fullHtml);
    } catch (err) {
        console.error('Error assembling index:', err.message);
        res.writeHead(500);
        res.end('Server Error: Could not assemble index.html');
    }
}

// Create server
const server = http.createServer(async (req, res) => {
    console.log(`${new Date().toISOString()} ${req.method} ${req.url}`);

    // Authentication Logic
    const cookies = getCookies(req);
    const savedData = loadData();
    const serverPin = savedData.pin;

    // Paths that don't require auth
    const isAuthPath = req.url === '/api/login';
    const isPublicStatic = req.url === '/style.css' || req.url === '/favicon.ico' || req.url.startsWith('/img/');

    const isAuthenticated = serverPin && cookies.app_auth === String(serverPin);

    // If PIN is not set yet, allow access to set it (admin logic) or if already authenticated
    if (!isAuthenticated && !isAuthPath && !isPublicStatic) {
        if (req.url.startsWith('/api/')) {
            res.writeHead(401, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Unauthorized' }));
            return;
        }
        // Redirect or serve login page for all other requests
        return serveLogin(res);
    }

    if (req.url.startsWith('/api/')) {
        await handleAPI(req, res);
    } else {
        serveStatic(req, res);
    }
});

server.listen(PORT, () => {
    console.log(`🪙 Coin Shop Server running at http://localhost:${PORT}`);
});
