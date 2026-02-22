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

function isValidEmail(email) {
    const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return typeof email === 'string' && re.test(email);
}

function isValidId(id) {
    if (typeof id === 'number') return id > 0;
    if (typeof id === 'string') return /^[0-9]+$/.test(id) || /^[a-f0-9-]{36}$/i.test(id);
    return false;
}

function checkType({ field, value, rules, errors }) {
    if (rules.type === 'number' && typeof value !== 'number') {
        errors.push(`Поле ${field} должно быть числом`);
    } else if (rules.type === 'string' && typeof value !== 'string') {
        errors.push(`Поле ${field} должно быть строкой`);
    } else if (rules.type === 'email' && !isValidEmail(value)) {
        errors.push(`Некорректный email в поле ${field}`);
    }
}

function checkRange({ field, value, rules, errors }) {
    if (rules.min !== undefined && value < rules.min) {
        errors.push(`Значение поля ${field} меньше минимального (${rules.min})`);
    }
    if (rules.max !== undefined && value > rules.max) {
        errors.push(`Значение поля ${field} больше максимального (${rules.max})`);
    }
    const len = value?.length;
    if (rules.minLength !== undefined && len < rules.minLength) {
        errors.push(`Длина поля ${field} меньше минимальной (${rules.minLength})`);
    }
}

function validateSchema(data, schema) {
    const errors = [];
    for (const [field, rules] of Object.entries(schema)) {
        const value = data[field];

        if (rules.required && (value === undefined || value === null || value === '')) {
            errors.push(`Поле ${field} обязательно для заполнения`);
            continue;
        }

        if (value !== undefined && value !== null) {
            checkType({ field, value, rules, errors });
            checkRange({ field, value, rules, errors });
        }
    }
    return errors.length > 0 ? errors : null;
}

module.exports = {
    sanitizeString,
    sanitizePayload,
    isValidEmail,
    isValidId,
    validateSchema
};
