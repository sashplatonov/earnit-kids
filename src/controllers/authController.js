const {
    authenticateUser, authenticateChildByToken,
    registerFamily, changePassword, recoverPassword, resetPasswordWithToken, verifyEmailToken
} = require('../services/authService');
const parseBody = require('../middleware/body-parser');

const { sendJSON } = require('../utils/controllerUtils');

function buildAuthCookies({ email, role, familyId, maxAge }) {
    const cookies = [
        `app_auth=${email}; Max-Age=${maxAge}; Path=/; HttpOnly; SameSite=Lax`,
        `app_role=${role}; Max-Age=${maxAge}; Path=/; SameSite=Lax`
    ];
    if (familyId) {
        cookies.push(`family_id=${familyId}; Max-Age=${maxAge}; Path=/; HttpOnly; SameSite=Lax`);
    }
    return cookies;
}

function sendLoginSuccess(res, email, result) {
    const maxAge = result.role === 'admin' ? 30 * 24 * 60 * 60 : 24 * 60 * 60;
    res.writeHead(200, {
        'Content-Type': 'application/json',
        'Set-Cookie': buildAuthCookies({ email, role: result.role, familyId: result.familyId, maxAge })
    });
    res.end(JSON.stringify(result));
}

async function handleLogin(req, res) {
    const { email, pin } = await parseBody(req);
    const result = await authenticateUser(email, pin);
    if (!result.success) return sendJSON(res, { error: result.error }, 401);
    sendLoginSuccess(res, email, result);
}

async function handleRegister(req, res) {
    const { familyName, email, adminPin } = await parseBody(req);
    const result = await registerFamily(familyName, email, adminPin);
    sendJSON(res, result, result.success ? 200 : 400);
}

function handleLogout(res) {
    res.writeHead(200, {
        'Content-Type': 'application/json',
        'Set-Cookie': [
            'app_auth=; Max-Age=0; Path=/; HttpOnly; SameSite=Lax',
            'app_role=; Max-Age=0; Path=/; SameSite=Lax',
            'family_id=; Max-Age=0; Path=/; HttpOnly; SameSite=Lax',
            'child_id=; Max-Age=0; Path=/; HttpOnly; SameSite=Lax'
        ]
    });
    res.end(JSON.stringify({ success: true }));
}

async function handleForgotPassword(req, res) {
    const { email } = await parseBody(req);
    if (!email) return sendJSON(res, { error: 'Email required' }, 400);
    const result = await recoverPassword(email);
    sendJSON(res, result, result.success ? 200 : 400);
}

async function handleResetPassword(req, res) {
    const { email, token, password } = await parseBody(req);
    if (!email || !token || !password) return sendJSON(res, { error: 'Missing fields' }, 400);
    const result = await resetPasswordWithToken(email, token, password);
    sendJSON(res, result, result.success ? 200 : 400);
}

async function handleVerify(req, res) {
    const { email, token } = await parseBody(req);
    const result = await verifyEmailToken(email, token);
    sendJSON(res, result, result.success ? 200 : 400);
}

function handleAuthConfig(res) {
    sendJSON(res, {
        emailVerificationEnabled: process.env.ENABLE_EMAIL_VERIFICATION !== 'false',
        passwordRecoveryEnabled: process.env.ENABLE_PASSWORD_RECOVERY !== 'false'
    });
}

/**
 * Main switch for Auth API
 */
async function handleAuthAPI(req, res) {
    const pathname = new URL(req.url, 'http://localhost').pathname.replace(/\/+$/, '') || '/';
    const handlers = {
        'POST /api/login': handleLogin,
        'POST /api/register': handleRegister,
        'POST /api/logout': () => handleLogout(res),
        'POST /api/forgot-password': handleForgotPassword,
        'POST /api/reset-password': handleResetPassword,
        'POST /api/verify': handleVerify,
        'GET /api/auth-config': () => handleAuthConfig(res)
    };

    const handler = handlers[`${req.method} ${pathname}`];
    if (!handler) return sendJSON(res, { error: 'Not Found' }, 404);
    await handler(req, res);
}

async function handleMagicLink(req, res) {
    const token = req.url.split('?')[0].split('/login-child/')[1];
    const authResult = await authenticateChildByToken(token);

    if (authResult.success) {
        const maxAge = 365 * 24 * 60 * 60;
        const cookies = buildAuthCookies({
            email: authResult.email,
            role: 'child',
            familyId: authResult.familyId,
            maxAge
        });
        cookies.push(`child_id=${authResult.childId}; Max-Age=${maxAge}; Path=/; HttpOnly; SameSite=Lax`);
        res.writeHead(302, { Location: '/', 'Set-Cookie': cookies });
        return res.end();
    }

    res.writeHead(302, { Location: '/login.html?error=invalid_token' });
    res.end();
}

module.exports = {
    handleAuthAPI,
    handleMagicLink,
    sendJSON,
    buildAuthCookies // Exported for use in magic link and other places
};
