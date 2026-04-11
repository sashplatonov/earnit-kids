/** @file Auth Utils utility helpers */
const crypto = require('crypto');

// fallback to default secret if env var is missing
const JWT_SECRET = process.env.JWT_SECRET || 'fallback-secret-for-development';

function base64url(str) {
    return Buffer.from(str)
        .toString('base64')
        .replace(/=/g, '')
        .replace(/\+/g, '-')
        .replace(/\//g, '_');
}

/**
 * Creates a JWT without external dependencies
 */
function signToken(payload, expiresInSeconds = null) {
    const header = { alg: 'HS256', typ: 'JWT' };

    if (expiresInSeconds) {
        payload.exp = Math.floor(Date.now() / 1000) + expiresInSeconds;
    }

    const encodedHeader = base64url(JSON.stringify(header));
    const encodedPayload = base64url(JSON.stringify(payload));

    const signatureInput = `${encodedHeader}.${encodedPayload}`;
    const signature = crypto.createHmac('sha256', JWT_SECRET).update(signatureInput).digest('base64')
        .replace(/=/g, '')
        .replace(/\+/g, '-')
        .replace(/\//g, '_');

    return `${signatureInput}.${signature}`;
}

/**
 * Verifies and decodes a JWT 
 */
function verifyToken(token) {
    try {
        const parts = token.split('.');
        if (parts.length !== 3) return null;

        const [encodedHeader, encodedPayload, signature] = parts;
        const signatureInput = `${encodedHeader}.${encodedPayload}`;

        const expectedSignature = crypto.createHmac('sha256', JWT_SECRET).update(signatureInput).digest('base64')
            .replace(/=/g, '')
            .replace(/\+/g, '-')
            .replace(/\//g, '_');

        if (signature !== expectedSignature) return null;

        const payloadStr = Buffer.from(encodedPayload.replace(/-/g, '+').replace(/_/g, '/'), 'base64').toString();
        const payload = JSON.parse(payloadStr);

        if (payload.exp && payload.exp < Math.floor(Date.now() / 1000)) {
            return null; // Expired
        }

        return payload;
    } catch (e) {
        return null;
    }
}

/**
 * Validates CSRF token header
 */
function hasSameHostUrl(urlValue, host) {
    if (!urlValue) return false;
    try {
        return new URL(urlValue).host === host;
    } catch (_) {
        return false;
    }
}

function hasTrustedFetchMetadata(req) {
    const secFetchSite = req.headers['sec-fetch-site'];
    return secFetchSite === 'same-origin' || secFetchSite === 'same-site' || secFetchSite === 'none';
}

function validateCsrf(req, csrfCookie) {
    if (!csrfCookie) return false;
    const headerToken = req.headers['x-csrf-token'];
    if (headerToken) return headerToken === csrfCookie;

    const host = req.headers.host;
    if (!host) return false;

    if (hasSameHostUrl(req.headers.origin, host)) return true;
    if (hasSameHostUrl(req.headers.referer, host)) return true;
    return hasTrustedFetchMetadata(req);
}

/**
 * Generates a random CSRF token
 */
function generateCsrfToken() {
    return crypto.randomBytes(16).toString('hex');
}

module.exports = {
    signToken,
    verifyToken,
    validateCsrf,
    generateCsrfToken
};
