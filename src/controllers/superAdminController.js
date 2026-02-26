/** @file Super Admin Controller REST controller helpers */
const {
    loadFamilies, loadFamilyData, saveFamilies, regenerateChildToken,
    saveFamilyData
} = require('../services/familyService');
const { loadBaseData, saveBaseData } = require('../services/baseDataService');
const { createBackup, restoreBackup, copyToReserve, checkReserveDbConnection } = require('../services/backupService');
const parseBody = require('../middleware/body-parser');
const { sendJSON } = require('../utils/controllerUtils');
const { URL } = require('url');
const { getSystemOverview } = require('../services/systemStatsService');
const { getHttpMetrics } = require('../services/httpMetricsService');
const { getDbHealth } = require('../services/dbHealthService');
const { readLogs } = require('../services/logsService');

const VALID_LOG_LEVELS = new Set(['info', 'warn', 'error']);
const DEFAULT_LOG_LEVEL = 'error';
const DEFAULT_LOG_LIMIT = 20;
const MIN_LOG_LIMIT = 1;
const MAX_LOG_LIMIT = 500;

function createTimestamp() {
    return new Date().toISOString();
}

function clampLogLimit(value) {
    const parsed = Number.parseInt(value, 10);
    if (Number.isNaN(parsed)) {
        return DEFAULT_LOG_LIMIT;
    }
    return Math.min(Math.max(parsed, MIN_LOG_LIMIT), MAX_LOG_LIMIT);
}

function respondSuccess(res, payload) {
    sendJSON(res, { success: true, timestamp: createTimestamp(), ...payload });
}

function respondError(res, message, status = 500) {
    sendJSON(res, { success: false, timestamp: createTimestamp(), error: message }, status);
}

async function getSuperFamiliesList() {
    const familiesData = await loadFamilies();
    const familyList = [];
    for (const [id, data] of Object.entries(familiesData.families)) {
        const familyData = await loadFamilyData(id);
        familyList.push({
            id,
            ...data,
            childrenCount: data.children ? data.children.length : 0,
            tasksCount: familyData.tasks ? familyData.tasks.length : 0,
            shopCount: familyData.shop ? familyData.shop.length : 0
        });
    }
    return familyList;
}

async function handleSuperFamilyData({ url, method, req, res }) {
    const match = url.match(/^\/api\/super\/family\/([^/]+)\/data$/);
    if (!match) return false;

    const familyId = match[1];
    if (method === 'GET') {
        const families = await loadFamilies();
        const familyInfo = families.families[familyId];
        if (!familyInfo) return sendJSON(res, { error: 'Not found' }, 404);
        return sendJSON(res, { familyId, familyInfo, data: await loadFamilyData(familyId) });
    }

    if (method === 'POST') {
        const body = await parseBody(req);
        const success = await saveFamilyData(familyId, body);
        return sendJSON(res, success ? { success: true } : { error: 'Failed' }, success ? 200 : 500);
    }

    return false;
}

async function handleSuperFamilyBlock({ url, method, req, res }) {
    const match = url.match(/^\/api\/super\/family\/([^/]+)\/block$/);
    if (!match || method !== 'POST') return false;

    const familyId = match[1];
    const body = await parseBody(req);
    const families = await loadFamilies();
    const family = families.families[familyId];
    if (!family) return sendJSON(res, { error: 'Failed' }, 404);

    family.isBlocked = body.isBlocked;
    const success = await saveFamilies(families);
    sendJSON(res, success ? { success: true } : { error: 'Failed' }, success ? 200 : 404);
}

async function handleSuperFamilyRegen({ url, method, res }) {
    const match = url.match(/^\/api\/super\/family\/([^/]+)\/regenerate-token$/);
    if (!match || method !== 'POST') return false;

    const familyId = match[1];
    const families = await loadFamilies();
    const family = families.families[familyId];
    if (!family || family.children.length === 0) {
        return sendJSON(res, { error: 'Failed or no children' }, 400);
    }

    const success = await regenerateChildToken(familyId, family.children[0].id);
    sendJSON(res, success ? { success: true } : { error: 'Failed or no children' }, success ? 200 : 400);
}

async function handleSuperChildRegen({ url, method, res }) {
    const match = url.match(/^\/api\/super\/child\/(\d+)\/regenerate-token$/);
    if (!match || method !== 'POST') return false;

    const childId = parseInt(match[1]);
    const families = await loadFamilies();
    let targetFamilyId = null;

    Object.values(families.families).forEach((family) => {
        if (family.children.some((child) => child.id === childId)) {
            targetFamilyId = family.id;
        }
    });

    if (!targetFamilyId) return sendJSON(res, { error: 'Child not found or failed' }, 404);

    const success = await regenerateChildToken(targetFamilyId, childId);
    sendJSON(res, success ? { success: true } : { error: 'Child not found or failed' }, success ? 200 : 404);
}

async function handleSuperAdminAPI(req, res, ctx) {
    if (ctx.role !== 'super_admin') return sendJSON(res, { error: 'Forbidden' }, 403);

    const normalizedPath = req.url.split('?')[0];
    const key = `${req.method} ${normalizedPath}`;
    const staticHandlers = {
        'GET /api/super/families': async () => sendJSON(res, { families: await getSuperFamiliesList() }),
        'GET /api/super/base-data': () => sendJSON(res, loadBaseData()),
        'GET /api/super/db-backup': () => createBackup(req, res),
        'POST /api/super/db-restore': () => restoreBackup(req, res),
        'POST /api/super/db-copy-reserve': () => copyToReserve(req, res),
        'GET /api/super/db-reserve-status': async () => sendJSON(res, await checkReserveDbConnection()),
        'POST /api/super/base-data': async () => {
            const body = await parseBody(req);
            const success = saveBaseData(body);
            sendJSON(res, success ? { success: true } : { error: 'Failed' }, success ? 200 : 500);
        },
        'GET /api/super/system/overview': () => handleSystemOverview(res),
        'GET /api/super/system/http-metrics': () => handleSystemHttpMetrics(res),
        'GET /api/super/system/db': () => handleSystemDb(res),
        'GET /api/super/system/logs': () => handleSystemLogs(req, res)
    };

    const handler = staticHandlers[key];
    if (handler) return handler();

    const dynamicHandlers = [handleSuperFamilyData, handleSuperFamilyBlock, handleSuperFamilyRegen, handleSuperChildRegen];
    for (const dynamicHandler of dynamicHandlers) {
        if (await dynamicHandler({ url: req.url, method: req.method, req, res })) return;
    }

    sendJSON(res, { error: 'Not Found' }, 404);
}

async function handleSystemOverview(res) {
    try {
        const overview = await getSystemOverview();
        respondSuccess(res, overview);
    } catch (err) {
        respondError(res, 'Failed to load system overview');
    }
}

async function handleSystemHttpMetrics(res) {
    try {
        const metrics = await getHttpMetrics();
        respondSuccess(res, metrics);
    } catch (err) {
        respondError(res, 'Failed to load HTTP metrics');
    }
}

async function handleSystemDb(res) {
    try {
        const dbHealth = await getDbHealth();
        respondSuccess(res, { db: dbHealth });
    } catch (err) {
        respondError(res, 'Failed to load database status');
    }
}

async function handleSystemLogs(req, res) {
    const urlObj = new URL(req.url, 'http://localhost');
    const levelParam = urlObj.searchParams.get('level') || DEFAULT_LOG_LEVEL;
    if (!VALID_LOG_LEVELS.has(levelParam)) {
        return respondError(res, 'Invalid log level', 400);
    }
    const limit = clampLogLimit(urlObj.searchParams.get('limit'));
    try {
        const logs = await readLogs({ level: levelParam, limit });
        respondSuccess(res, { query: { level: levelParam, limit }, logs });
    } catch (err) {
        respondError(res, 'Failed to load logs');
    }
}

module.exports = {
    getSuperFamiliesList,
    handleSuperAdminAPI
};
