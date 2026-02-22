function parseBody(req) {
    const { sanitizePayload } = require('../utils/validation');
    return new Promise((resolve, reject) => {
        let body = '';
        req.on('data', chunk => body += chunk);
        req.on('end', () => {
            try {
                const parsed = body ? JSON.parse(body) : {};
                resolve(sanitizePayload(parsed));
            } catch (e) {
                const { ValidationError } = require('../utils/errors');
                reject(new ValidationError('Invalid JSON'));
            }
        });
        req.on('error', reject);
    });
}

module.exports = parseBody;
