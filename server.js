const http = require('http');
const path = require('path');
const fs = require('fs');
const { handleAPI, handleSuperAdminAPI } = require('./src/apiHandlers');
const { findFamilyByEmail } = require('./src/dataService');

const PORT = process.env.PORT || 3000;

// MIME types for static files
const MIME_TYPES = {
    '.html': 'text/html; charset=utf-8',
    '.css': 'text/css; charset=utf-8',
    '.js': 'application/javascript; charset=utf-8',
    '.json': 'application/json; charset=utf-8',
    '.md': 'text/markdown; charset=utf-8',
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
            res.end('Server Error');
            return;
        }
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end(content);
    });
}

// Serve super admin page
function serveSuperAdmin(res) {
    const superAdminPath = path.join(__dirname, 'views', 'super-admin.html');
    fs.readFile(superAdminPath, 'utf8', (err, content) => {
        if (err) {
            res.writeHead(500);
            res.end('Server Error');
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
        'section_catalog.html',
        'section_about.html',
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

    // Paths that don't require auth
    const isAuthPath = req.url === '/api/login' || req.url === '/api/logout' || req.url === '/api/register';
    const isPublicStatic = req.url === '/style.css' || req.url === '/favicon.ico' ||
        req.url.startsWith('/img/') || req.url.startsWith('/js/');

    // Check authentication via family_id cookie (set during login)
    const familyId = cookies.family_id;
    const appAuth = cookies.app_auth;
    const appRole = cookies.app_role;

    // Verify that the PIN still maps to this family (or super admin)
    let isAuthenticated = false;
    let isSuperAdmin = false;

    if (appAuth) {
        // Authenticate via Email token (appAuth cookie now stores email)
        const user = findFamilyByEmail(appAuth);

        if (user) {
            // Check if Super Admin
            if (user.isSuperAdmin && appRole === 'super_admin') {
                isAuthenticated = true;
                isSuperAdmin = true;
            }
            // Check if Regular Family Member
            else if (familyId && user.id === familyId) {
                // Verify role is valid for this family
                if (appRole === 'admin' || appRole === 'child') {
                    isAuthenticated = true;
                }
            }
        }
    }

    // Debug logging for auth issues
    if (req.url === '/' || req.url.startsWith('/api/login')) {
        console.log(`[DEBUG] Auth status: ${isAuthenticated}, familyId: ${familyId}, role: ${cookies.app_role}`);
    }

    // If not authenticated and not on auth/public path
    if (!isAuthenticated && !isAuthPath && !isPublicStatic) {
        if (req.url.startsWith('/api/')) {
            res.writeHead(401, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Unauthorized' }));
            return;
        }
        // Redirect or serve login page for all other requests
        return serveLogin(res);
    }

    // Super admin routes
    if (isAuthenticated && isSuperAdmin) {
        if (req.url.startsWith('/api/super/')) {
            await handleSuperAdminAPI(req, res);
            return;
        }
        if (req.url === '/' || req.url === '/index.html') {
            return serveSuperAdmin(res);
        }
    }

    if (req.url.startsWith('/api/')) {
        await handleAPI(req, res);
    } else {
        serveStatic(req, res);
    }
});

server.listen(PORT, () => {
    console.log(`🪙 Coin Shop Server running at http://localhost:${PORT}`);
    console.log(`📁 Data directory: ./data/`);
});
