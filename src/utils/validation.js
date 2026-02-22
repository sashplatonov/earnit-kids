const sanitizeHtml = require('sanitize-html');

function sanitizeString(str) {
    if (typeof str !== 'string') return str;
    return sanitizeHtml(str, {
        allowedTags: [], // Strip all HTML
        allowedAttributes: {}
    }).trim();
}

function sanitizePayload(payload) {
    if (Array.isArray(payload)) {
        return payload.map(item => sanitizePayload(item));
    }
    if (payload !== null && typeof payload === 'object') {
        const result = {};
        for (const [key, value] of Object.entries(payload)) {
            result[key] = sanitizePayload(value);
        }
        return result;
    }
    if (typeof payload === 'string') {
        return sanitizeString(payload);
    }
    return payload;
}

module.exports = { sanitizeString, sanitizePayload };
