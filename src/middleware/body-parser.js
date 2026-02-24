/** @file Body Parser Express middleware */
const { createLogger } = require('../utils/logger');
const logger = createLogger('bodyParser');

function parseBody(req) {
    if (req.body !== undefined) {
        return Promise.resolve(req.body);
    }

    if (req._bodyParsing) {
        return req._bodyParsing;
    }

    const { sanitizePayload } = require('../utils/validation');
    req._bodyParsing = new Promise((resolve, reject) => {
        let body = '';
        req.on('data', chunk => body += chunk);
        req.on('end', () => {
            try {
                const parsed = body ? JSON.parse(body) : {};
                const sanitized = sanitizePayload(parsed);
                req.body = sanitized;
                resolve(sanitized);
            } catch (e) {
                logger.warn({ err: e.message }, 'Malformed JSON payload');
                const { ValidationError } = require('../utils/errors');
                reject(new ValidationError('Invalid JSON'));
            }
        });
        req.on('error', reject);
    });

    return req._bodyParsing;
}

// Optional middleware usage
parseBody.middleware = async (ctx, req, res) => {
    if (['POST', 'PUT', 'PATCH'].includes(req.method)) {
        await parseBody(req);
    }
};

module.exports = parseBody;
