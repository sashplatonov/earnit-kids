// Simple Node.js server with JSON file storage
const http = require('http');
const path = require('path');
const fs = require('fs');
const { handleAPI } = require('./src/apiHandlers');

const PORT = process.env.PORT || 3000;
const DATA_FILE = path.join(__dirname, 'data.json'); // Keep reference for log if needed, but logic is moved

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

    if (req.url.startsWith('/api/')) {
        await handleAPI(req, res);
    } else {
        serveStatic(req, res);
    }
});

server.listen(PORT, () => {
    console.log(`🪙 Coin Shop Server running at http://localhost:${PORT}`);
});
