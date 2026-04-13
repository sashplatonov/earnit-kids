const { createLogger } = require('./utils/logger');

const logger = createLogger('webSessionClient');
const BACKEND_URL = (process.env.BACKEND_URL || 'http://localhost:8080').replace(/\/+$/, '');

function buildForwardedHeaders(req) {
    const headers = {
        Accept: 'application/json',
        Cookie: req.headers.cookie || '',
        'User-Agent': req.headers['user-agent'] || 'apps-web',
        'X-Forwarded-Host': req.headers['x-forwarded-host'] || req.headers.host || 'localhost:3000',
        'X-Forwarded-Proto': req.headers['x-forwarded-proto'] || (req.socket.encrypted ? 'https' : 'http')
    };

    if (req.headers['x-forwarded-for']) {
        headers['X-Forwarded-For'] = req.headers['x-forwarded-for'];
    }

    return headers;
}

async function fetchSessionSnapshot(req) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 3000);

    try {
        const response = await fetch(`${BACKEND_URL}/api/page-data/session`, {
            method: 'GET',
            headers: buildForwardedHeaders(req),
            signal: controller.signal
        });

        if (!response.ok) {
            logger.warn({ status: response.status }, 'Session endpoint returned a non-OK status');
            return { authenticated: false };
        }

        return await response.json();
    } catch (err) {
        logger.warn({ err: err.message }, 'Failed to resolve session snapshot from backend');
        return { authenticated: false };
    } finally {
        clearTimeout(timeout);
    }
}

module.exports = {
    BACKEND_URL,
    fetchSessionSnapshot
};