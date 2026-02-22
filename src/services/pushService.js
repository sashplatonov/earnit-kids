const https = require('https');
const { GoogleAuth } = require('google-auth-library');
const pushTokenRepository = require('../db/pushTokenRepository');

const FCM_SCOPE = 'https://www.googleapis.com/auth/firebase.messaging';
let authClientPromise = null;

function isPushEnabled() {
    return process.env.ENABLE_PUSH_NOTIFICATIONS === 'true';
}

function parseServiceAccountJson() {
    const raw = process.env.FCM_SERVICE_ACCOUNT_JSON;
    if (!raw) return null;

    try {
        return JSON.parse(raw);
    } catch (err) {
        console.error('[push] FCM_SERVICE_ACCOUNT_JSON is not valid JSON:', err.message);
        return null;
    }
}

function buildGoogleAuth() {
    const credentials = parseServiceAccountJson();
    if (credentials) {
        return new GoogleAuth({ credentials, scopes: [FCM_SCOPE] });
    }

    const keyFilename = (process.env.FCM_SERVICE_ACCOUNT_PATH || process.env.GOOGLE_APPLICATION_CREDENTIALS || '').trim();
    if (keyFilename) {
        return new GoogleAuth({ keyFilename, scopes: [FCM_SCOPE] });
    }

    return new GoogleAuth({ scopes: [FCM_SCOPE] });
}

function getAuthClient() {
    if (authClientPromise) return authClientPromise;

    const auth = buildGoogleAuth();
    authClientPromise = auth.getClient();
    return authClientPromise;
}

async function getAccessToken() {
    const client = await getAuthClient();
    const token = await client.getAccessToken();
    return typeof token === 'string' ? token : (token?.token || '');
}

async function resolveFcmProjectId() {
    const fromEnv = (process.env.FCM_PROJECT_ID || '').trim();
    if (fromEnv) return fromEnv;

    const creds = parseServiceAccountJson();
    if (creds?.project_id) return creds.project_id;

    try {
        const auth = buildGoogleAuth();
        const detected = await auth.getProjectId();
        return (detected || '').trim();
    } catch (err) {
        return '';
    }
}

function buildFcmV1Payload({ token, title, body, data }) {
    return {
        message: {
            token,
            notification: { title, body },
            data,
            android: { priority: 'high', notification: { sound: 'default' } },
            apns: {
                headers: { 'apns-priority': '10' },
                payload: { aps: { sound: 'default' } }
            }
        }
    };
}

function safeJsonParse(raw) {
    try {
        return JSON.parse(raw || '{}');
    } catch (err) {
        return {};
    }
}

function getErrorCode(response, parsedBody) {
    const details = Array.isArray(parsedBody?.error?.details) ? parsedBody.error.details : [];
    const fcmError = details.find((item) => item['@type']?.includes('google.firebase.fcm.v1.FcmError'));
    if (fcmError?.errorCode) return fcmError.errorCode;
    if (parsedBody?.error?.status) return parsedBody.error.status;
    return `HTTP_${response.statusCode}`;
}

function parseFcmError(response, parsedBody) {
    const errorCode = getErrorCode(response, parsedBody);
    const msg = parsedBody?.error?.message || response.body || '';
    const reason = `${errorCode} ${msg}`.trim();

    const bad = reason.includes('UNREGISTERED') || reason.includes('INVALID_ARGUMENT');
    return { reason, invalidToken: bad };
}

function postJson(url, headers, payload) {
    return new Promise((resolve, reject) => {
        const data = JSON.stringify(payload);
        const request = https.request(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Content-Length': Buffer.byteLength(data),
                ...headers
            }
        }, (response) => {
            let body = '';
            response.on('data', (chunk) => {
                body += chunk.toString();
            });
            response.on('end', () => {
                resolve({
                    statusCode: response.statusCode,
                    body
                });
            });
        });

        request.on('error', reject);
        request.write(data);
        request.end();
    });
}

async function sendFcmNotification({ token, title, body, data = {} }) {
    const projectId = await resolveFcmProjectId();
    if (!projectId) return { success: false, invalidToken: false, reason: 'FCM_PROJECT_ID not resolved' };

    try {
        const accessToken = await getAccessToken();
        if (!accessToken) return { success: false, invalidToken: false, reason: 'Unable to get OAuth access token' };

        const response = await postJson(
            `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
            { Authorization: `Bearer ${accessToken}` },
            buildFcmV1Payload({ token, title, body, data })
        );

        const parsed = safeJsonParse(response.body);
        if (response.statusCode >= 200 && response.statusCode < 300) {
            return { success: true, invalidToken: false, reason: null };
        }

        const parsedError = parseFcmError(response, parsed);
        return { success: false, invalidToken: parsedError.invalidToken, reason: parsedError.reason };
    } catch (err) {
        return { success: false, invalidToken: false, reason: err.message };
    }
}

function getPendingRequests(data) {
    return (data?.requests || []).filter((item) => item.status === 'pending');
}

function indexById(items) {
    const map = new Map();
    items.forEach((item) => {
        map.set(String(item.id), item);
    });
    return map;
}

function getNewHistoryEntries(beforeData, afterData) {
    const beforeIds = new Set((beforeData?.history || []).map((entry) => String(entry.id)));
    return (afterData?.history || []).filter((entry) => !beforeIds.has(String(entry.id)));
}

function detectCreatedRequests(beforeData, afterData) {
    const beforeMap = indexById(getPendingRequests(beforeData));
    return getPendingRequests(afterData).filter((req) => !beforeMap.has(String(req.id)));
}

function detectApprovedRequests(beforeData, afterData) {
    const beforePending = getPendingRequests(beforeData);
    const afterPendingMap = indexById(getPendingRequests(afterData));
    const removedPending = beforePending.filter((req) => !afterPendingMap.has(String(req.id)));
    if (removedPending.length === 0) return [];

    const newHistory = getNewHistoryEntries(beforeData, afterData);
    if (newHistory.length === 0) return [];

    return removedPending.filter((req) => {
        return newHistory.some((entry) => {
            const sameChild = String(entry.childId || '') === String(req.childId || '');
            const sameAmount = Number(entry.amount || 0) === Number(req.coins || 0);
            return sameChild && sameAmount;
        });
    });
}

function detectBalanceChanges(beforeChildren, afterChildren) {
    const beforeMap = new Map((beforeChildren || []).map((child) => [String(child.id), child]));
    const changes = [];

    (afterChildren || []).forEach((child) => {
        const prev = beforeMap.get(String(child.id));
        if (!prev) return;

        const previousBalance = Number(prev.balance || 0);
        const currentBalance = Number(child.balance || 0);
        if (previousBalance === currentBalance) return;

        changes.push({
            childId: child.id,
            childName: child.name || 'Ребенок',
            delta: currentBalance - previousBalance,
            balance: currentBalance
        });
    });

    return changes;
}

function dedupeTokens(tokens) {
    return [...new Set(tokens.map((item) => item.token))];
}

async function sendToTokens({ tokens, title, body, data = {} }) {
    if (!isPushEnabled() || !Array.isArray(tokens) || tokens.length === 0) return;

    const invalidTokens = [];
    for (const token of tokens) {
        const result = await sendFcmNotification({ token, title, body, data });
        if (!result.success) {
            console.warn('[push] send failed:', result.reason);
            if (result.invalidToken) invalidTokens.push(token);
        }
    }

    if (invalidTokens.length > 0) {
        await pushTokenRepository.deactivateTokens(invalidTokens);
    }
}

async function registerPushToken({ familyId, childId, role, token, platform }) {
    if (!familyId || !token || !role) return false;
    return await pushTokenRepository.upsertToken({ familyId, childId, role, token, platform });
}

async function unregisterPushToken({ familyId, token }) {
    if (!familyId || !token) return false;
    return await pushTokenRepository.deactivateToken(familyId, token);
}

async function handleCreatedRequests(familyId, requests) {
    for (const r of requests) {
        const adminTokens = await pushTokenRepository.getActiveTokens(familyId, { roles: ['admin'] });
        await sendToTokens({
            tokens: dedupeTokens(adminTokens),
            title: 'Новая заявка',
            body: `${r.taskName || 'Заявка'}: ${r.coins || 0} 🪙`,
            data: { eventType: 'request_created', requestId: String(r.id || ''), childId: String(r.childId || '') }
        });
    }
}

async function handleApprovedRequests(familyId, requests) {
    for (const r of requests) {
        const childTokens = await pushTokenRepository.getActiveTokens(familyId, { roles: ['child'], childId: r.childId });
        await sendToTokens({
            tokens: dedupeTokens(childTokens),
            title: 'Заявка подтверждена',
            body: `${r.taskName || 'Заявка'}: ${r.coins || 0} 🪙`,
            data: { eventType: 'request_approved', requestId: String(r.id || ''), childId: String(r.childId || '') }
        });
    }
}

async function handleBalanceChanges(familyId, changes) {
    for (const change of changes) {
        const [admT, chT] = await Promise.all([
            pushTokenRepository.getActiveTokens(familyId, { roles: ['admin'] }),
            pushTokenRepository.getActiveTokens(familyId, { roles: ['child'], childId: change.childId })
        ]);
        const allTokens = dedupeTokens([...(admT || []), ...(chT || [])]);
        await sendToTokens({
            tokens: allTokens,
            title: 'Баланс изменен',
            body: `${change.childName}: ${change.delta > 0 ? '+' : ''}${change.delta} 🪙 (итого ${change.balance})`,
            data: { eventType: 'balance_changed', childId: String(change.childId || ''), delta: String(change.delta), balance: String(change.balance) }
        });
    }
}

async function notifyFamilyChanges({ familyId, beforeData, afterData, beforeChildren, afterChildren }) {
    if (!isPushEnabled() || !familyId) return;

    const createdReqs = detectCreatedRequests(beforeData, afterData);
    const approvedReqs = detectApprovedRequests(beforeData, afterData);
    const balanceChanges = detectBalanceChanges(beforeChildren, afterChildren);

    await handleCreatedRequests(familyId, createdReqs);
    await handleApprovedRequests(familyId, approvedReqs);
    await handleBalanceChanges(familyId, balanceChanges);
}

module.exports = {
    registerPushToken,
    unregisterPushToken,
    notifyFamilyChanges
};
