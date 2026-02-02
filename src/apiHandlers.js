const { loadData, saveData } = require('./dataService');
const crypto = require('crypto');

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

// Verify Telegram Hash
function verifyTelegramAuth(data, botToken) {
    if (!botToken) return false;

    const { hash, ...params } = data;
    const dataCheckString = Object.keys(params)
        .sort()
        .map(key => `${key}=${params[key]}`)
        .join('\n');

    const secretKey = crypto.createHash('sha256').update(botToken).digest();
    const hmac = crypto.createHmac('sha256', secretKey)
        .update(dataCheckString)
        .digest('hex');

    return hmac === hash;
}

// Common logic for Telegram Login (Redirect or Callback)
async function handleTelegramLogin(req, res, data, isRedirect = false) {
    const botToken = process.env.BOT_TOKEN;

    if (!botToken) {
        console.warn('BOT_TOKEN is not set in environment variables!');
        if (isRedirect) return redirectWithError(res, 'Server configuration error');
        return sendJSON(res, { error: 'Сервер не настроен для входа через Telegram' }, 500);
    }

    if (!verifyTelegramAuth(data, botToken)) {
        if (isRedirect) return redirectWithError(res, 'Invalid auth');
        return sendJSON(res, { error: 'Ошибка проверки данных Telegram' }, 401);
    }

    // Check if this telegram user is allowed
    const savedData = loadData();
    const allowedUsername = savedData.child_telegram_username;
    const allowedId = savedData.child_telegram_id;

    const isAllowed = (allowedUsername && String(data.username).toLowerCase() === String(allowedUsername).replace('@', '').toLowerCase()) ||
        (allowedId && String(data.id) === String(allowedId));

    if (!isAllowed && (allowedUsername || allowedId)) {
        if (isRedirect) return redirectWithError(res, 'Access denied for this Telegram account');
        return sendJSON(res, { error: 'Этот аккаунт Telegram не привязан к магазину' }, 403);
    }

    // Success - set persistent auth cookies
    res.setHeader('Set-Cookie', [
        `app_auth_tg=${data.id}; Max-Age=${30 * 24 * 60 * 60}; Path=/; HttpOnly; SameSite=Lax`,
        `app_tg_hash=${data.hash}; Max-Age=${30 * 24 * 60 * 60}; Path=/; HttpOnly; SameSite=Lax`
    ]);

    if (isRedirect) {
        res.writeHead(302, { 'Location': '/' });
        return res.end();
    }

    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end(JSON.stringify({ success: true }));
}

function redirectWithError(res, msg) {
    res.writeHead(302, { 'Location': '/?error=' + encodeURIComponent(msg) });
    res.end();
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

                // Set authenticated cookie for 24 hours
                res.writeHead(200, {
                    'Content-Type': 'application/json',
                    'Set-Cookie': `app_auth=${body.pin}; Max-Age=${24 * 60 * 60}; Path=/; HttpOnly`
                });
                return res.end(JSON.stringify({ success: true }));
            } else {
                // Fail - increment attempts
                attempts.count++;
                ipAttempts[clientIp] = attempts;
                return sendJSON(res, { error: 'Invalid PIN' }, 401);
            }
        }

        // POST /api/auth/telegram (Callback mode)
        if (url === '/api/auth/telegram' && method === 'POST') {
            const body = await parseBody(req);
            return handleTelegramLogin(req, res, body);
        }

        // GET /api/auth/telegram-redirect (Redirect mode)
        if (url.startsWith('/api/auth/telegram-redirect') && method === 'GET') {
            const queryString = url.split('?')[1] || '';
            const params = new URLSearchParams(queryString);
            const data = {};
            for (const [key, value] of params.entries()) {
                data[key] = value;
            }

            await handleTelegramLogin(req, res, data, true);
            return;
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
