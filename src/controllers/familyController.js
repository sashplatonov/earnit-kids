const {
    loadFamilyData, saveFamilyData, updateLastActivity,
    loadFamilies,
    updateNickname, searchByNickname, addFriend, getFriendsData,
    addChild, deleteChild, updateChildSettings, getPaginatedHistory, getPaginatedRequests
} = require('../services/familyService');
const { createLogger } = require('../utils/logger');
const logger = createLogger('familyController');
const parseBody = require('../middleware/body-parser');
const { sendJSON } = require('../utils/controllerUtils');
const websocket = require('../utils/websocket');

function enrichWithFamilyInfo({ data, familyInfo, ctx }) {
    data.isAdmin = ctx.role === 'admin';

    if (ctx.role === 'admin' && familyInfo) {
        data.children = familyInfo.children || [];
    } else if (ctx.role === 'child' && familyInfo) {
        const child = familyInfo.children.find((childItem) => childItem.id === ctx.childId);
        data.childNickname = child ? child.name : 'Child';
        if (child) {
            data.monthlyLimit = child.monthlyLimit;
            data.dailyCoinLimit = child.dailyCoinLimit;
        }
    }
}

async function resolveTargetChildId({ ctx, res, queryChildId, familiesCache = null }) {
    if (ctx.role === 'child') return ctx.childId;
    if (!queryChildId) return null;

    const targetChildId = parseInt(queryChildId);
    if (!Number.isInteger(targetChildId)) {
        sendJSON(res, { error: 'Child not found' }, 404);
        return false;
    }

    const families = familiesCache || await loadFamilies();
    const familyInfo = families.families[ctx.familyId];
    if (!familyInfo?.children?.some((c) => c.id === targetChildId)) {
        sendJSON(res, { error: 'Child not found' }, 404);
        return false;
    }

    return targetChildId;
}

async function handleDataGet(ctx, req, res) {
    const queryChildId = ctx.urlObj.searchParams.get('childId');
    const families = await loadFamilies();
    const familyInfo = families.families[ctx.familyId];
    const targetChildId = await resolveTargetChildId({ ctx, res, queryChildId, familiesCache: families });
    if (targetChildId === false) return;
    const data = await loadFamilyData(ctx.familyId, targetChildId);

    enrichWithFamilyInfo({ data, familyInfo, ctx });

    sendJSON(res, data);
}

async function handleDataPost(ctx, req, res) {
    if (ctx.role !== 'admin' && ctx.role !== 'child') {
        return sendJSON(res, { error: 'Not Found or Forbidden' }, 404);
    }

    const body = await parseBody(req);
    const actingChildId = ctx.role === 'child' ? ctx.childId : null;
    const payloadKeys = Array.isArray(body) ? ['array'] : Object.keys(body || {});
    logger.debug({ familyId: ctx.familyId, actingChildId, keys: payloadKeys }, 'Family data mutation received');
    const balanceChanges = Array.isArray(body?.children)
        ? body.children
            .filter((child) => child?.balance !== undefined)
            .map((child) => ({
                childId: child.id,
                childName: child.name || 'Child',
                balance: child.balance
            }))
        : [];
    const saved = await saveFamilyData(ctx.familyId, body, actingChildId);
    if (!saved) return sendJSON(res, { error: 'Save failed' }, 500);

    balanceChanges.forEach((change) => {
        logger.info({
            familyId: ctx.familyId,
            childId: change.childId,
            childName: change.childName,
            balance: change.balance
        }, 'Child balance changed');
    });

    await updateLastActivity(ctx.familyId);

    // Notify family members about the update via WebSocket
    websocket.notifyFamily(ctx.familyId, 'DATA_UPDATED', {
        by: ctx.role,
        childId: actingChildId
    });

    sendJSON(res, { success: true });
}

async function handleChildrenCreate(ctx, req, res) {
    if (ctx.role !== 'admin') return sendJSON(res, { error: 'Not Found or Forbidden' }, 404);
    const body = await parseBody(req);
    if (!body.name) return sendJSON(res, { error: 'Name required' }, 400);
    const result = await addChild(ctx.familyId, body.name);
    sendJSON(res, result, result.success ? 200 : 400);
}

async function handleUpdateNickname(ctx, req, res) {
    if (ctx.role !== 'child') return sendJSON(res, { error: 'Not Found or Forbidden' }, 404);
    const body = await parseBody(req);
    const result = await updateNickname(ctx.familyId, ctx.childId, body.nickname);
    sendJSON(res, result, result.success ? 200 : 400);
}

async function handleHistoryGet(ctx, req, res) {
    const page = parseInt(ctx.urlObj.searchParams.get('page')) || 1;
    const limit = parseInt(ctx.urlObj.searchParams.get('limit')) || 50;
    const queryChildId = ctx.urlObj.searchParams.get('childId');
    const targetChildId = await resolveTargetChildId({ ctx, res, queryChildId });
    if (targetChildId === false) return;
    const historyData = await getPaginatedHistory(ctx.familyId, targetChildId, { page, limit });
    sendJSON(res, historyData);
}

async function handleRequestsGet(ctx, req, res) {
    const page = parseInt(ctx.urlObj.searchParams.get('page')) || 1;
    const limit = parseInt(ctx.urlObj.searchParams.get('limit')) || 50;
    const queryChildId = ctx.urlObj.searchParams.get('childId');
    const targetChildId = await resolveTargetChildId({ ctx, res, queryChildId });
    if (targetChildId === false) return;
    const requestsData = await getPaginatedRequests(ctx.familyId, targetChildId, { page, limit });
    sendJSON(res, requestsData);
}

module.exports = {
    handleDataGet,
    handleDataPost,
    handleChildrenCreate,
    handleUpdateNickname,
    handleHistoryGet,
    handleRequestsGet
};
