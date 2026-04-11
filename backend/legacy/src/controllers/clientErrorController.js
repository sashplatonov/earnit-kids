/** @file Client Error Controller REST controller helpers */
const { sendJSON } = require('../utils/controllerUtils');
const { createLogger } = require('../utils/logger');
const { sendAlert } = require('../utils/alerts');

const logger = createLogger('clientErrorController');
const MAX_FIELD_LENGTH = 500;
const MAX_STACK_LENGTH = 2000;

function normalizeString(value, maxLength = MAX_FIELD_LENGTH) {
    if (!value) return '';
    return String(value).slice(0, maxLength);
}

function normalizeErrorPayload(body = {}) {
    return {
        message: normalizeString(body.message),
        stack: normalizeString(body.stack, MAX_STACK_LENGTH),
        source: normalizeString(body.source),
        lineno: Number.isInteger(body.lineno) ? body.lineno : null,
        colno: Number.isInteger(body.colno) ? body.colno : null,
        type: normalizeString(body.type || 'runtime'),
        href: normalizeString(body.href),
        userAgent: normalizeString(body.userAgent),
        buildVersion: normalizeString(body.buildVersion, 100)
    };
}

function getRequestMeta(req, reqId, payload) {
    return {
        reqId,
        ip: req.socket?.remoteAddress || 'unknown',
        referer: req.headers?.referer || '',
        userAgent: payload.userAgent || req.headers['user-agent'] || '',
        path: req.url
    };
}

function buildAlertError(payload) {
    const alertError = new Error(payload.message || 'Unknown client browser error');
    if (payload.stack) {
        alertError.stack = payload.stack;
    }
    return alertError;
}

function toSourceValue(value, fallback = '?') {
    return value != null ? value : fallback;
}

function buildAlertContext(reqId, payload) {
    const sourceParts = [
        payload.type || 'runtime',
        payload.source || 'unknown',
        toSourceValue(payload.lineno),
        toSourceValue(payload.colno)
    ];
    return `ID: ${reqId} | CLIENT ${sourceParts.join(':')} | ${payload.href || 'unknown page'} | build=${payload.buildVersion || 'unknown'}`;
}

async function handleClientError(_ctx, req, res) {
    const payload = normalizeErrorPayload(req.body);
    const reqId = req.id || 'unknown';
    const requestMeta = getRequestMeta(req, reqId, payload);

    logger.error({
        ...requestMeta,
        clientError: payload
    }, 'Client browser error reported');

    await sendAlert(buildAlertError(payload), buildAlertContext(reqId, payload));

    sendJSON(res, { success: true }, 202);
}

module.exports = {
    handleClientError
};
