const { loadData, saveData } = require('./dataService');

// Rate limiting store: { ip: { count, resetTime } }
const ipAttempts = {};
const MAX_ATTEMPTS = 5;
const BLOCK_WINDOW_MS = 15 * 60 * 1000; // 15 minutes

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
        // GET /api/data - load all data (SECURE: Don't send PIN)
        if (url === '/api/data' && method === 'GET') {
            const data = loadData();
            // Remove PIN from response
            const { pin, ...safeData } = data;
            // Tell client if PIN is set
            safeData.isPinSet = !!pin;
            return sendJSON(res, safeData);
        }

        // POST /api/login - new endpoint for auth
        if (url === '/api/login' && method === 'POST') {
            const clientIp = req.socket.remoteAddress;
            const now = Date.now();

            // Cleanup old attempts
            if (ipAttempts[clientIp] && now > ipAttempts[clientIp].resetTime) {
                delete ipAttempts[clientIp];
            }

            // Check rate limit
            const attempts = ipAttempts[clientIp] || { count: 0, resetTime: now + BLOCK_WINDOW_MS };
            if (attempts.count >= MAX_ATTEMPTS) {
                const retryAfter = Math.ceil((attempts.resetTime - now) / 1000);
                return sendJSON(res, { error: `Too many attempts. Try again in ${retryAfter} seconds.` }, 429);
            }

            const body = await parseBody(req);
            const savedData = loadData();
            const serverPin = savedData.pin;

            if (!serverPin) {
                // No PIN set yet, allow access to set it
                return sendJSON(res, { success: true, message: 'PIN not set' });
            }

            if (String(body.pin) === String(serverPin)) {
                // Success - reset attempts
                delete ipAttempts[clientIp];
                return sendJSON(res, { success: true });
            } else {
                // Fail - increment attempts
                attempts.count++;
                ipAttempts[clientIp] = attempts;
                return sendJSON(res, { error: 'Invalid PIN' }, 401);
            }
        }

        // POST /api/data - save all data (SECURE: Preserve PIN if not provided)
        if (url === '/api/data' && method === 'POST') {
            const body = await parseBody(req);
            const currentData = loadData();

            // If body.pin is missing or null, keep existing PIN
            if (body.pin === undefined || body.pin === null) {
                body.pin = currentData.pin;
            }

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

module.exports = { handleAPI };
