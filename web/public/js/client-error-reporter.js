const API_URL = '/api/client-errors';
const MAX_REPORTS = 10;
const sentFingerprints = new Set();
let reportsSent = 0;

function getBuildVersion() {
    return document.querySelector('meta[name="app-build-version"]')?.content?.trim() || '';
}

function toStringSafe(value) {
    if (!value) return '';
    try {
        return String(value);
    } catch (_err) {
        return '';
    }
}

function buildPayload(base) {
    return {
        ...base,
        href: window.location.href,
        userAgent: navigator.userAgent,
        buildVersion: getBuildVersion()
    };
}

function buildFingerprint(payload) {
    return [
        payload.type || 'runtime',
        payload.message || '',
        payload.source || '',
        payload.lineno || 0,
        payload.colno || 0
    ].join('|');
}

function getCsrfToken() {
    const cookieRow = document.cookie
        .split(';')
        .map((row) => row.trim())
        .find((row) => row.startsWith('csrf_token='));

    if (!cookieRow) return '';
    return decodeURIComponent(cookieRow.slice('csrf_token='.length));
}

const NOISY_ERRORS = [
    'View transition was skipped because document visibility state is hidden',
    'Skipping view transition because document visibility state has become hidden.',
    'Script error.'
];

function shouldSkip(payload) {
    if (reportsSent >= MAX_REPORTS) return true;

    const message = payload.message || '';
    if (NOISY_ERRORS.some(noisy => message.includes(noisy))) {
        return true;
    }

    const fingerprint = buildFingerprint(payload);
    if (sentFingerprints.has(fingerprint)) return true;
    sentFingerprints.add(fingerprint);
    return false;
}

function sendPayload(payload) {
    if (shouldSkip(payload)) return;
    reportsSent += 1;
    const csrfToken = getCsrfToken();
    const headers = { 'Content-Type': 'application/json' };
    if (csrfToken) {
        headers['X-CSRF-Token'] = csrfToken;
    }
    fetch(API_URL, {
        method: 'POST',
        headers,
        credentials: 'same-origin',
        keepalive: true,
        body: JSON.stringify(payload)
    }).catch(function () { });
}

function createErrorPayload(event) {
    const source = toStringSafe(event.filename);
    if (source.includes('client-error-reporter.js')) return null;
    return buildPayload({
        type: 'error',
        message: toStringSafe(event.message) || 'Unknown browser error',
        source,
        lineno: Number.isFinite(event.lineno) ? event.lineno : null,
        colno: Number.isFinite(event.colno) ? event.colno : null,
        stack: toStringSafe(event.error?.stack)
    });
}

function createUnhandledRejectionPayload(event) {
    const reason = event.reason;
    return buildPayload({
        type: 'unhandledrejection',
        message: toStringSafe(reason?.message || reason) || 'Unhandled Promise rejection',
        source: '',
        lineno: null,
        colno: null,
        stack: toStringSafe(reason?.stack)
    });
}

window.addEventListener('error', function (event) {
    const payload = createErrorPayload(event);
    if (!payload) return;
    sendPayload(payload);
});

window.addEventListener('unhandledrejection', function (event) {
    sendPayload(createUnhandledRejectionPayload(event));
});
