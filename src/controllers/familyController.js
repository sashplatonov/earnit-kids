const {
    loadFamilyData, saveFamilyData, updateLastActivity,
    loadFamilies, updateFamilySettings,
    updateNickname, searchByNickname, addFriend, getFriendsData,
    getAnalyticsData,
    addChild, deleteChild, updateChildSettings
} = require('../services/familyService');
const { loadBaseData } = require('../services/baseDataService');
const { changePassword } = require('../services/authService');
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

async function handleAnalytics(ctx, req, res) {
    if (ctx.role !== 'admin' && ctx.role !== 'child') {
        return sendJSON(res, { error: 'Forbidden' }, 403);
    }
    const timeframe = ctx.urlObj.searchParams.get('timeframe') || 'month';
    let childId = ctx.urlObj.searchParams.get('childId') ? parseInt(ctx.urlObj.searchParams.get('childId')) : null;

    if (ctx.role === 'child') {
        childId = ctx.childId;
    }

    const data = await getAnalyticsData(ctx.familyId, childId, timeframe);
    sendJSON(res, data);
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

async function handleFriendsList(ctx, req, res) {
    if (ctx.role !== 'child') return sendJSON(res, { error: 'Not Found or Forbidden' }, 404);
    const friends = await getFriendsData(ctx.familyId, ctx.childId);
    sendJSON(res, friends);
}

async function handleSearchUser(ctx, req, res) {
    if (ctx.role !== 'child') return sendJSON(res, { error: 'Not Found or Forbidden' }, 404);
    const nickname = ctx.urlObj.searchParams.get('nickname');
    const results = await searchByNickname(nickname);
    sendJSON(res, results);
}

async function handleAddFriend(ctx, req, res) {
    if (ctx.role !== 'child') return sendJSON(res, { error: 'Not Found or Forbidden' }, 404);
    const body = await parseBody(req);
    const result = await addFriend(ctx.childId, body.friendId);
    sendJSON(res, result, result.success ? 200 : 400);
}

module.exports = {
    handleDataGet,
    handleDataPost,
    handleAnalytics,
    handleChildrenCreate,
    handleUpdateFamilySettings,
    handleUpdateNickname,
    handleFriendsList,
    handleSearchUser,
    handleAddFriend
};
