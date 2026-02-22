const {
    loadFamilyData, saveFamilyData, updateLastActivity,
    loadFamilies, updateFamilySettings,
    updateNickname, searchByNickname, addFriend, getFriendsData,
    addChild, deleteChild, updateChildSettings, getPaginatedHistory, getPaginatedRequests
} = require('../services/familyService');
const parseBody = require('../middleware/body-parser');
const { sendJSON } = require('../utils/controllerUtils');

function enrichWithFamilyInfo({ data, familyInfo, ctx }) {
    data.isAdmin = ctx.role === 'admin';
    data.familyName = familyInfo ? familyInfo.name : 'Shop';

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

async function handleDataGet(ctx, req, res) {
    const queryChildId = ctx.urlObj.searchParams.get('childId');
    const targetChildId = ctx.role === 'child' ? ctx.childId : (queryChildId ? parseInt(queryChildId) : null);
    const data = await loadFamilyData(ctx.familyId, targetChildId);

    const families = await loadFamilies();
    const familyInfo = families.families[ctx.familyId];

    enrichWithFamilyInfo({ data, familyInfo, ctx });

    sendJSON(res, data);
}

async function handleDataPost(ctx, req, res) {
    if (ctx.role !== 'admin' && ctx.role !== 'child') {
        return sendJSON(res, { error: 'Not Found or Forbidden' }, 404);
    }

    const body = await parseBody(req);
    const actingChildId = ctx.role === 'child' ? ctx.childId : null;
    const saved = await saveFamilyData(ctx.familyId, body, actingChildId);
    if (!saved) return sendJSON(res, { error: 'Save failed' }, 500);

    await updateLastActivity(ctx.familyId);
    sendJSON(res, { success: true });
}

async function handleChildrenCreate(ctx, req, res) {
    if (ctx.role !== 'admin') return sendJSON(res, { error: 'Not Found or Forbidden' }, 404);
    const body = await parseBody(req);
    if (!body.name) return sendJSON(res, { error: 'Name required' }, 400);
    const result = await addChild(ctx.familyId, body.name);
    sendJSON(res, result, result.success ? 200 : 400);
}

async function handleUpdateFamilySettings(ctx, req, res) {
    if (ctx.role !== 'admin') return sendJSON(res, { error: 'Not Found or Forbidden' }, 404);
    const body = await parseBody(req);
    const result = await updateFamilySettings(ctx.familyId, body);
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
    const targetChildId = ctx.role === 'child' ? ctx.childId : (queryChildId ? parseInt(queryChildId) : null);

    const historyData = await getPaginatedHistory(ctx.familyId, targetChildId, { page, limit });
    sendJSON(res, historyData);
}

async function handleRequestsGet(ctx, req, res) {
    const page = parseInt(ctx.urlObj.searchParams.get('page')) || 1;
    const limit = parseInt(ctx.urlObj.searchParams.get('limit')) || 50;
    const queryChildId = ctx.urlObj.searchParams.get('childId');
    const targetChildId = ctx.role === 'child' ? ctx.childId : (queryChildId ? parseInt(queryChildId) : null);

    const requestsData = await getPaginatedRequests(ctx.familyId, targetChildId, { page, limit });
    sendJSON(res, requestsData);
}

module.exports = {
    handleDataGet,
    handleDataPost,
    handleChildrenCreate,
    handleUpdateFamilySettings,
    handleUpdateNickname,
    handleHistoryGet,
    handleRequestsGet
};
