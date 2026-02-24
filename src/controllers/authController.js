/** @file Auth Controller REST controller helpers */
const {
    authenticateUser, authenticateChildByToken,
    registerFamily, changePassword, recoverPassword, resetPasswordWithToken, verifyEmailToken
} = require('../services/authService');
const parseBody = require('../middleware/body-parser');
const { signToken, generateCsrfToken } = require('../utils/authUtils');

const { sendJSON } = require('../utils/controllerUtils');
const { createLogger } = require('../utils/logger');
const logger = createLogger('authController');

function buildAuthCookies({ email, role, familyId, childId, maxAge }) {
    const csrfToken = generateCsrfToken();
    const payload = { email, role, familyId, csrfToken };
    if (childId) {
        payload.childId = childId;
    }

    const token = signToken(payload, maxAge);
    const secureSegment = process.env.NODE_ENV === 'production' ? 'Secure; ' : '';
    const authFlags = `Max-Age=${maxAge}; Path=/; HttpOnly; ${secureSegment}`;
    const roleFlags = `Max-Age=${maxAge}; Path=/; ${secureSegment}`;
    const sameSiteLax = 'SameSite=Lax';
    const sameSiteStrict = 'SameSite=Strict';

    const cookies = [
        `app_auth=${token}; ${authFlags}${sameSiteLax}`,
        `app_role=${role}; ${roleFlags}${sameSiteLax}`,
        `csrf_token=${csrfToken}; ${authFlags}${sameSiteStrict}`
    ];

    if (familyId) {
        cookies.push(`family_id=${familyId}; ${authFlags}${sameSiteLax}`);
    }
    if (childId) {
        cookies.push(`child_id=${childId}; ${authFlags}${sameSiteLax}`);
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
    if (!result.success) {
        logger.warn({ email, error: result.error }, 'Login failed');
        return sendJSON(res, { error: result.error }, 401);
    }
    sendLoginSuccess(res, email, result);
}

async function handleRegister(req, res) {
    const { email, adminPin } = await parseBody(req);
    const result = await registerFamily(email, adminPin);
    if (result.success) {
        logger.info({ email, familyId: result.familyId }, 'Family registered');
    }
    sendJSON(res, result, result.success ? 200 : 400);
}

function handleLogout(res) {
    res.writeHead(200, {
        'Content-Type': 'application/json',
        'Set-Cookie': [
            'app_auth=; Max-Age=0; Path=/; HttpOnly; SameSite=Strict',
            'app_role=; Max-Age=0; Path=/; SameSite=Strict',
            'family_id=; Max-Age=0; Path=/; HttpOnly; SameSite=Strict',
            'child_id=; Max-Age=0; Path=/; HttpOnly; SameSite=Strict',
            'csrf_token=; Max-Age=0; Path=/; SameSite=Strict'
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
        const maxAge = parseInt(process.env.MAGIC_LINK_EXPIRES_IN) || 7 * 24 * 60 * 60; // default 7 days instead of indefinite
        const cookies = buildAuthCookies({
            email: authResult.email,
            role: 'child',
            familyId: authResult.familyId,
            childId: authResult.childId,
            maxAge
        });
        res.writeHead(302, { Location: '/', 'Set-Cookie': cookies });
        return res.end();
    }

    logger.warn({ tokenProvided: !!token }, 'Magic link authentication failed');
    res.writeHead(302, { Location: '/login.html?error=invalid_token' });
    res.end();
}

module.exports = {
    handleAuthAPI,
    handleMagicLink,
    sendJSON,
    buildAuthCookies, // Exported for use in magic link and other places
    handleLogin,
    handleRegister,
    handleLogout,
    handleForgotPassword,
    handleResetPassword,
    handleVerify,
    handleAuthConfig
};
