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
        // GET /api/data - load all data (SECURE: Don't send PINs)
        if (url === '/api/data' && method === 'GET') {
            const data = loadData();
            // Remove PINs from response
            const { admin_pin, child_pin, ...safeData } = data;
            // Tell client if PINs are set
            safeData.isAdminPinSet = !!admin_pin;
            safeData.isChildPinSet = !!child_pin;
            return sendJSON(res, safeData);
        }

        // POST /api/login
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

            const isAdmin = String(body.pin) === String(savedData.admin_pin);
            const isChild = String(body.pin) === String(savedData.child_pin);

            if (isAdmin || isChild) {
                // Success - reset attempts
                delete ipAttempts[clientIp];

                const role = isAdmin ? 'admin' : 'child';

                // Set authenticated cookie for 24 hours
                res.writeHead(200, {
                    'Content-Type': 'application/json',
                    'Set-Cookie': [
                        `app_auth=${body.pin}; Max-Age=${24 * 60 * 60}; Path=/; HttpOnly; SameSite=Lax`,
                        `app_role=${role}; Max-Age=${24 * 60 * 60}; Path=/; SameSite=Lax`
                    ]
                });
                return res.end(JSON.stringify({ success: true, role }));
            } else {
                // Fail - increment attempts
                attempts.count++;
                ipAttempts[clientIp] = attempts;
                return sendJSON(res, { error: 'Неверный ПИН-код' }, 401);
            }
        }

        // POST /api/logout
        if (url === '/api/logout' && method === 'POST') {
            res.writeHead(200, {
                'Content-Type': 'application/json',
                'Set-Cookie': [
                    'app_auth=; Max-Age=0; Path=/; HttpOnly',
                    'app_role=; Max-Age=0; Path=/'
                ]
            });
            return res.end(JSON.stringify({ success: true }));
        }

        // POST /api/change-pin
        if (url === '/api/change-pin' && method === 'POST') {
            const body = await parseBody(req);
            const { oldPin, newPin, role } = body;

            if (!newPin || newPin.length < 6 || !/^\d+$/.test(newPin)) {
                return sendJSON(res, { error: 'ПИН-код должен состоять минимум из 6 цифр' }, 400);
            }

            const currentData = loadData();
            const pinField = role === 'admin' ? 'admin_pin' : 'child_pin';

            if (String(oldPin) !== String(currentData[pinField])) {
                return sendJSON(res, { error: 'Старый ПИН-код неверен' }, 401);
            }

            currentData[pinField] = newPin;
            if (saveData(currentData)) {
                // Update cookie with new PIN
                res.writeHead(200, {
                    'Content-Type': 'application/json',
                    'Set-Cookie': `app_auth=${newPin}; Max-Age=${24 * 60 * 60}; Path=/; HttpOnly; SameSite=Lax`
                });
                return res.end(JSON.stringify({ success: true }));
            } else {
                return sendJSON(res, { error: 'Ошибка сохранения' }, 500);
            }
        }

        // POST /api/data - save all data
        if (url === '/api/data' && method === 'POST') {
            const body = await parseBody(req);
            const currentData = loadData();

            // Preserve PINs
            body.admin_pin = currentData.admin_pin;
            body.child_pin = currentData.child_pin;

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
