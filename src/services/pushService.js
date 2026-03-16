/** @file Push Service business services */
const https = require('https');
const webpush = require('web-push');
const { GoogleAuth } = require('google-auth-library');
const pushTokenRepository = require('../db/pushTokenRepository');
const {
    detectBalanceChanges,
    detectApprovedRequests,
    detectCreatedRequests,
    dedupeTokens
} = require('./pushServiceHelpers');
const { createLogger } = require('../utils/logger');
const logger = createLogger('pushService');

const FCM_SCOPE = 'https://www.googleapis.com/auth/firebase.messaging';
let authClientPromise = null;
let vapidConfigured = false;

function isPushEnabled() {
    return process.env.ENABLE_PUSH_NOTIFICATIONS === 'true';
}

function configureVapid() {
    if (vapidConfigured) return;
    const publicKey = (process.env.VAPID_PUBLIC_KEY || '').trim();
    const privateKey = (process.env.VAPID_PRIVATE_KEY || '').trim();
    const contact = (process.env.VAPID_CONTACT || '').trim();
    if (publicKey && privateKey && contact) {
        webpush.setVapidDetails(contact, publicKey, privateKey);
        vapidConfigured = true;
    }
}

function parseServiceAccountJson() {
    const raw = process.env.FCM_SERVICE_ACCOUNT_JSON;
    if (!raw) return null;

    try {
        return JSON.parse(raw);
    } catch (err) {
        logger.error({ err: err.message }, 'Invalid FCM service account JSON');
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
            const chunks = [];
            response.on('data', (chunk) => {
                chunks.push(chunk);
            });
            response.on('end', () => {
                const body = Buffer.concat(chunks).toString('utf8');
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

async function sendWebPushNotification({ endpoint, keyP256dh, keyAuth, title, body, data = {} }) {
    configureVapid();
    if (!vapidConfigured) {
        return { success: false, invalidToken: false, reason: 'VAPID not configured' };
    }

    const subscription = {
        endpoint,
        keys: { p256dh: keyP256dh, auth: keyAuth }
    };

    const payload = JSON.stringify({ title, body, data });

    try {
        await webpush.sendNotification(subscription, payload);
        return { success: true, invalidToken: false, reason: null };
    } catch (err) {
        const gone = err.statusCode === 410 || err.statusCode === 404;
        return { success: false, invalidToken: gone, reason: err.message };
    }
}

async function sendToSingleToken({ tokenEntry, title, body, data }) {
    if (tokenEntry.pushType === 'web') {
        return await sendWebPushNotification({
            endpoint: tokenEntry.endpoint,
            keyP256dh: tokenEntry.keyP256dh,
            keyAuth: tokenEntry.keyAuth,
            title,
            body,
            data
        });
    }

    return await sendFcmNotification({ token: tokenEntry.token, title, body, data });
}

function trackFailedToken(result, tokenEntry, invalidFcmTokens) {
    if (result.success) return;

    logger.warn({ reason: result.reason, pushType: tokenEntry.pushType }, 'Push notification failed');
    if (result.invalidToken && tokenEntry.token) invalidFcmTokens.push(tokenEntry.token);
}

async function sendToTokens({ tokens, title, body, data = {} }) {
    if (!isPushEnabled() || !Array.isArray(tokens) || tokens.length === 0) return;

    const invalidFcmTokens = [];
    for (const tokenEntry of tokens) {
        const result = await sendToSingleToken({ tokenEntry, title, body, data });
        trackFailedToken(result, tokenEntry, invalidFcmTokens);
    }

    if (invalidFcmTokens.length > 0) {
        await pushTokenRepository.deactivateTokens(invalidFcmTokens);
    }
}

async function registerPushToken({ familyId, childId, role, token, platform }) {
    if (!familyId || !token || !role) return false;
    return await pushTokenRepository.upsertToken({ familyId, childId, role, token, platform });
}

async function registerWebPushSubscription({ familyId, childId, role, endpoint, keyP256dh, keyAuth, platform }) {
    if (!familyId || !endpoint || !role) return false;
    return await pushTokenRepository.upsertWebSubscription({ familyId, childId, role, endpoint, keyP256dh, keyAuth, platform });
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

async function handleBalanceChanges(familyId, changes, actingRole = null) {
    for (const change of changes) {
        const fetchTasks = [
            pushTokenRepository.getActiveTokens(familyId, { roles: ['child'], childId: change.childId })
        ];

        // Only fetch admin tokens if the acting party is NOT an admin
        if (actingRole !== 'admin') {
            fetchTasks.push(pushTokenRepository.getActiveTokens(familyId, { roles: ['admin'] }));
        }

        const tokenGroups = await Promise.all(fetchTasks);
        const allTokens = dedupeTokens(tokenGroups.flat().filter(t => t));

        if (allTokens.length === 0) continue;

        await sendToTokens({
            tokens: allTokens,
            title: 'Баланс изменен',
            body: `${change.childName}: ${change.delta > 0 ? '+' : ''}${change.delta} 🪙 (итого ${change.balance})`,
            data: { eventType: 'balance_changed', childId: String(change.childId || ''), delta: String(change.delta), balance: String(change.balance) }
        });
    }
}

/**
 * Detect all family changes and send notifications
 * @param {Object} params
 * @param {number} params.familyId
 * @param {Object} params.beforeData
 * @param {Object} params.afterData
 * @param {Array} params.beforeChildren
 * @param {Array} params.afterChildren
 * @param {string} [params.actingRole] - 'admin' or 'child'
 * @param {number} [params.actingChildId] - ID of acting child if role='child'
 */
async function notifyFamilyChanges({ familyId, beforeData, afterData, beforeChildren, afterChildren, actingRole = null, actingChildId = null }) {
    if (!isPushEnabled() || !familyId) return;

    const createdReqs = detectCreatedRequests(beforeData, afterData);
    const approvedReqs = detectApprovedRequests(beforeData, afterData);
    const balanceChanges = detectBalanceChanges(beforeChildren, afterChildren);

    // Filter created requests - usually created by child, notify admin
    if (createdReqs.length > 0 && actingRole !== 'admin') {
        await handleCreatedRequests(familyId, createdReqs);
    }

    // Filter approved requests - approved by admin, notify child
    if (approvedReqs.length > 0) {
        await handleApprovedRequests(familyId, approvedReqs);
    }

    // Filter balance changes - notify based on role
    if (balanceChanges.length > 0) {
        await handleBalanceChanges(familyId, balanceChanges, actingRole);
    }
}

module.exports = {
    sendFcmNotification,
    sendToTokens,
    registerPushToken,
    registerWebPushSubscription,
    unregisterPushToken,
    notifyFamilyChanges
};
