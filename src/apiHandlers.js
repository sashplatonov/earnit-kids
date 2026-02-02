const { loadFamilyData, saveFamilyData, changePassword, registerFamily, loadFamilies, findFamilyByEmail, loadBaseData, saveBaseData, authenticateUser, toggleFamilyBlock, updateLastActivity, recoverPassword } = require('./dataService');

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
        'Content-Type': 'application/json'
    });
    res.end(JSON.stringify(data));
}

// Get family context from cookies
function getFamilyContext(req) {
    const cookies = {};
    const rc = req.headers.cookie;
    rc && rc.split(';').forEach((cookie) => {
        const parts = cookie.split('=');
        cookies[parts.shift().trim()] = decodeURI(parts.join('='));
    });

    return {
        familyId: cookies.family_id || null,
        role: cookies.app_role || null
    };
}

// Handle API requests
async function handleAPI(req, res) {
    const url = req.url;
    const method = req.method;

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


        // GET /api/base-data - get base tasks and products
        if (url === '/api/base-data' && method === 'GET') {
            const { familyId } = getFamilyContext(req);
            if (!familyId) {
                return sendJSON(res, { error: 'Unauthorized' }, 401);
            }
            const data = loadBaseData();
            return sendJSON(res, data);
        }

        // POST /api/register - register new family
        if (url === '/api/register' && method === 'POST') {
            const body = await parseBody(req);
            const { familyName, email, adminPin, childPin } = body;

            if (!email) {
                return sendJSON(res, { error: 'Email обязателен' }, 400);
            }

            const result = registerFamily(familyName, email, adminPin, childPin);

            if (result.success) {
                return sendJSON(res, { success: true, familyId: result.familyId });
            } else {
                return sendJSON(res, { error: result.error }, 400);
            }
        }

        // POST /api/forgot-password
        if (url === '/api/forgot-password' && method === 'POST') {
            const body = await parseBody(req);
            const { email } = body;

            if (!email) {
                return sendJSON(res, { error: 'Email обязателен' }, 400);
            }

            const result = await recoverPassword(email);

            if (result.success) {
                return sendJSON(res, { success: true });
            } else {
                return sendJSON(res, { error: result.error || 'Ошибка восстановления' }, 400);
            }
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
            const { email, pin } = body; // 'pin' field contains password from frontend

            if (!email || !pin) {
                return sendJSON(res, { error: 'Введите Email и Пароль' }, 400);
            }

            const authResult = authenticateUser(email, pin);

            if (authResult.success) {
                // Success - reset attempts
                delete ipAttempts[clientIp];

                // Set authenticated cookies for 24 hours
                const maxAge = 24 * 60 * 60;
                const cookies = [
                    `app_auth=${email}; Max-Age=${maxAge}; Path=/; HttpOnly; SameSite=Lax`,
                    `app_role=${authResult.role}; Max-Age=${maxAge}; Path=/; SameSite=Lax`
                ];

                if (authResult.familyId) {
                    cookies.push(`family_id=${authResult.familyId}; Max-Age=${maxAge}; Path=/; HttpOnly; SameSite=Lax`);
                }

                res.writeHead(200, {
                    'Content-Type': 'application/json',
                    'Set-Cookie': cookies
                });

                return res.end(JSON.stringify({
                    success: true,
                    role: authResult.role,
                    familyName: authResult.familyName
                }));
            } else {
                // Fail - increment attempts
                attempts.count++;
                ipAttempts[clientIp] = attempts;
                return sendJSON(res, { error: authResult.error || 'Неверный Email или Пароль' }, 401);
            }
        }

        // POST /api/logout
        if (url === '/api/logout' && method === 'POST') {
            res.writeHead(200, {
                'Content-Type': 'application/json',
                'Set-Cookie': [
                    'app_auth=; Max-Age=0; Path=/; HttpOnly',
                    'app_role=; Max-Age=0; Path=/',
                    'family_id=; Max-Age=0; Path=/; HttpOnly'
                ]
            });
            return res.end(JSON.stringify({ success: true }));
        }

        // POST /api/change-pin (kept endpoint name for compatibility, but changes password)
        if (url === '/api/change-pin' && method === 'POST') {
            const { familyId, role } = getFamilyContext(req);

            if (!familyId) {
                return sendJSON(res, { error: 'Unauthorized' }, 401);
            }

            const body = await parseBody(req);
            const { oldPin, newPin } = body; // oldPin/newPin contain passwords

            const result = changePassword(familyId, role, oldPin, newPin);

            if (result.success) {
                // Update cookie?? Actually we store email in cookie, so no need to update cookie unless email changed.
                // Passwords are not in cookies anymore.
                return sendJSON(res, { success: true });
            } else {
                return sendJSON(res, { error: result.error }, 400);
            }
        }

        // GET /api/data - load family data
        if (url === '/api/data' && method === 'GET') {
            const { familyId, role } = getFamilyContext(req);

            if (!familyId) {
                return sendJSON(res, { error: 'Unauthorized' }, 401);
            }

            const data = loadFamilyData(familyId);
            // Add role info for frontend
            data.isAdmin = role === 'admin';
            data.familyId = familyId;
            return sendJSON(res, data);
        }

        // POST /api/data - save family data (admin only)
        if (url === '/api/data' && method === 'POST') {
            const { familyId, role } = getFamilyContext(req);

            if (!familyId) {
                return sendJSON(res, { error: 'Unauthorized' }, 401);
            }

            // Only admin can save data
            if (role !== 'admin') {
                return sendJSON(res, { error: 'Forbidden: Admin only' }, 403);
            }

            const body = await parseBody(req);

            // Don't allow overwriting certain fields
            delete body.familyId;
            delete body.isAdmin;

            if (saveFamilyData(familyId, body)) {
                // Update last activity
                updateLastActivity(familyId);
                return sendJSON(res, { success: true });
            } else {
                return sendJSON(res, { error: 'Failed to save' }, 500);
            }
        }

        // 404 for unknown API routes
        sendJSON(res, { error: 'Not Found' }, 404);

    } catch (err) {
        console.error('API Error:', err);
        sendJSON(res, { error: err.message }, 400);
    }
}

// Handle super admin API requests
async function handleSuperAdminAPI(req, res) {
    const url = req.url;
    const method = req.method;

    // Get role from context
    const { role } = getFamilyContext(req);

    if (role !== 'super_admin') {
        return sendJSON(res, { error: 'Forbidden: Super Admin only' }, 403);
    }

    try {
        // GET /api/super/families - list all families
        if (url === '/api/super/families' && method === 'GET') {
            const families = loadFamilies();

            // Map families and attach PINs
            // Map families and attach PINs
            const familyList = Object.entries(families.families || {}).map(([id, data]) => {
                const familyData = loadFamilyData(id);
                return {
                    id,
                    name: data.name,
                    email: data.email,
                    created_at: data.created_at,
                    template: data.template,
                    adminPin: data.admin_password,
                    childPin: data.child_password,
                    isBlocked: !!data.isBlocked,
                    tasksCount: familyData.tasks ? familyData.tasks.length : 0,
                    shopCount: familyData.shop ? familyData.shop.length : 0,
                    lastActivity: data.last_activity || null
                };
            });

            return sendJSON(res, { families: familyList });
        }

        // GET /api/super/base-data
        if (url === '/api/super/base-data' && method === 'GET') {
            const data = loadBaseData();
            return sendJSON(res, data);
        }

        // POST /api/super/base-data
        if (url === '/api/super/base-data' && method === 'POST') {
            const body = await parseBody(req);
            if (saveBaseData(body)) {
                return sendJSON(res, { success: true });
            } else {
                return sendJSON(res, { error: 'Failed to save base data' }, 500);
            }
        }

        // GET /api/super/family/:id/data - get family data
        const getFamilyMatch = url.match(/^\/api\/super\/family\/([^/]+)\/data$/);
        if (getFamilyMatch && method === 'GET') {
            const familyId = getFamilyMatch[1];
            const data = loadFamilyData(familyId);
            const families = loadFamilies();
            const familyInfo = families.families[familyId];

            return sendJSON(res, {
                familyId,
                familyInfo,
                data
            });
        }

        // POST /api/super/family/:id/data - update family data
        const postFamilyMatch = url.match(/^\/api\/super\/family\/([^/]+)\/data$/);
        if (postFamilyMatch && method === 'POST') {
            const familyId = postFamilyMatch[1];
            const body = await parseBody(req);

            if (saveFamilyData(familyId, body)) {
                return sendJSON(res, { success: true });
            } else {
                return sendJSON(res, { error: 'Failed to save' }, 500);
            }
        }

        // POST /api/super/family/:id/block
        const blockFamilyMatch = url.match(/^\/api\/super\/family\/([^/]+)\/block$/);
        if (blockFamilyMatch && method === 'POST') {
            const familyId = blockFamilyMatch[1];
            const body = await parseBody(req);
            const { isBlocked } = body;

            const result = toggleFamilyBlock(familyId, isBlocked);
            if (result.success) {
                return sendJSON(res, { success: true });
            } else {
                return sendJSON(res, { error: result.error || 'Failed to update block status' }, 500);
            }
        }

        // 404 for unknown super admin routes
        sendJSON(res, { error: 'Not Found' }, 404);

    } catch (err) {
        console.error('Super Admin API Error:', err);
        sendJSON(res, { error: err.message }, 400);
    }
}

module.exports = { handleAPI, handleSuperAdminAPI };
