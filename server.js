// Simple Node.js server with JSON file storage
const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PORT || 3000;
const DATA_FILE = path.join(__dirname, 'data.json');

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

// Default data structure
const DEFAULT_DATA = {
    pin: null,
    balance: 0,
    tasks: [],
    shop: [],
    history: []
};

// Load data from file
function loadData() {
    try {
        if (fs.existsSync(DATA_FILE)) {
            const content = fs.readFileSync(DATA_FILE, 'utf8');
            return { ...DEFAULT_DATA, ...JSON.parse(content) };
        }
    } catch (err) {
        console.error('Error loading data:', err.message);
    }
    return { ...DEFAULT_DATA };
}

// Save data to file
function saveData(data) {
    try {
        fs.writeFileSync(DATA_FILE, JSON.stringify(data, null, 2), 'utf8');
        return true;
    } catch (err) {
        console.error('Error saving data:', err.message);
        return false;
    }
}

// Serve static file
function serveStatic(req, res) {
    // Check if searching for root
    if (req.url === '/' || req.url === '/index.html') {
        serveIndex(res);
        return;
    }

    let filePath = path.join(__dirname, req.url);
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
    let hasError = false;

    // Synchronous reading for simplicity in this dev server
    // (Async is better for prod, but this ensures order easily for this small task)
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

// Parse JSON body
function parseBody(req) {
    return new Promise((resolve, reject) => {
        let body = '';
        req.on('data', chunk => body += chunk);
        req.on('end', () => {
            try {
                resolve(body ? JSON.parse(body) : {});
            } catch (e) {
                reject(new Error('Invalid JSON'));
            }
        });
        req.on('error', reject);
    });
}

// Send JSON response
function sendJSON(res, data, status = 200) {
    res.writeHead(status, {
        'Content-Type': 'application/json',
        'Access-Control-Allow-Origin': '*'
    });
    res.end(JSON.stringify(data));
}

// Handle API requests
async function handleAPI(req, res) {
    const url = req.url;
    const method = req.method;

    // CORS preflight
    if (method === 'OPTIONS') {
        res.writeHead(204, {
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
            'Access-Control-Allow-Headers': 'Content-Type'
        });
        res.end();
        return;
    }

    try {
        // GET /api/data - load all data
        if (url === '/api/data' && method === 'GET') {
            const data = loadData();
            return sendJSON(res, data);
        }

        // POST /api/data - save all data
        if (url === '/api/data' && method === 'POST') {
            const body = await parseBody(req);
            if (saveData(body)) {
                return sendJSON(res, { success: true });
            } else {
                return sendJSON(res, { error: 'Failed to save' }, 500);
            }
        }

        // 404 for unknown API routes
        sendJSON(res, { error: 'Not Found' }, 404);

    } catch (err) {
        sendJSON(res, { error: err.message }, 400);
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
    console.log(`   Data file: ${DATA_FILE}`);
});
