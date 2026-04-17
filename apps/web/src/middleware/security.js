/** @file Security headers middleware */
'use strict';

const helmet = require('helmet');

const helmetMiddleware = helmet({
    contentSecurityPolicy: false,
    crossOriginEmbedderPolicy: false,
    crossOriginResourcePolicy: { policy: 'same-site' },
    referrerPolicy: { policy: 'no-referrer' },
    frameguard: { action: 'deny' }
});

function setSecurityHeaders(reqOrRes, resArg) {
    const [req, res] = resArg ? [reqOrRes, resArg] : [{ headers: {}, socket: {} }, reqOrRes];
    res.removeHeader = typeof res.removeHeader === 'function' ? res.removeHeader : () => {};
    res.getHeader = typeof res.getHeader === 'function' ? res.getHeader : () => undefined;
    helmetMiddleware(req, res, () => {});
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('X-XSS-Protection', '1; mode=block');
    res.setHeader('Strict-Transport-Security', 'max-age=31536000; includeSubDomains');
}

module.exports = { setSecurityHeaders };
