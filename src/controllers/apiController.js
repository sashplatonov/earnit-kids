const {
    loadFamilyData, saveFamilyData, updateLastActivity,
    loadFamilies, saveFamilies, getChildLoginLink,
    regenerateChildToken, updateFamilySettings,
    updateNickname, searchByNickname, addFriend, getFriendsData,
    addChild, deleteChild, updateChildSettings
} = require('../services/familyService');
const {
    authenticateUser, authenticateChildByToken,
    registerFamily, changePassword, recoverPassword, resetPasswordWithToken, verifyEmailToken
} = require('../services/authService');
const { loadBaseData, saveBaseData } = require('../services/baseDataService');
const { createBackup, restoreBackup, copyToReserve, checkReserveDbConnection } = require('../services/backupService');
const parseBody = require('../middleware/body-parser');
const { getCookies } = require('./viewController');

function sendJSON(res, data, status = 200) {
    res.writeHead(status, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(data));
}

function getFamilyContext(req) {
    const cookies = getCookies(req);
    return {
        familyId: cookies.family_id || null,
        childId: cookies.child_id ? parseInt(cookies.child_id) : null,
        role: cookies.app_role || null,
        email: cookies.app_auth || null
    };
}

function buildAuthCookies(email, role, familyId, maxAge) {
    const cookies = [
        `app_auth=${email}; Max-Age=${maxAge}; Path=/; HttpOnly; SameSite=Lax`,
        `app_role=${role}; Max-Age=${maxAge}; Path=/; SameSite=Lax`
    ];
    if (familyId) {
        cookies.push(`family_id=${familyId}; Max-Age=${maxAge}; Path=/; HttpOnly; SameSite=Lax`);
    }
    return cookies;
}

function sendLoginSuccess(res, email, result) {
    const maxAge = result.role === 'admin' ? 30 * 24 * 60 * 60 : 24 * 60 * 60;
    res.writeHead(200, {
        'Content-Type': 'application/json',
        'Set-Cookie': buildAuthCookies(email, result.role, result.familyId, maxAge)
    });
    res.end(JSON.stringify(result));
}

async function handleLogin(req, res) {
    const { email, pin } = await parseBody(req);
    const result = await authenticateUser(email, pin);
    if (!result.success) return sendJSON(res, { error: result.error }, 401);
    sendLoginSuccess(res, email, result);
}

async function handleRegister(req, res) {
    const { familyName, email, adminPin } = await parseBody(req);
    const result = await registerFamily(familyName, email, adminPin);
    sendJSON(res, result, result.success ? 200 : 400);
}

function handleLogout(res) {
    res.writeHead(200, {
        'Content-Type': 'application/json',
        'Set-Cookie': [
            'app_auth=; Max-Age=0; Path=/; HttpOnly; SameSite=Lax',
            'app_role=; Max-Age=0; Path=/; SameSite=Lax',
            'family_id=; Max-Age=0; Path=/; HttpOnly; SameSite=Lax',
            'child_id=; Max-Age=0; Path=/; HttpOnly; SameSite=Lax'
        ]
    });
    res.end(JSON.stringify({ success: true }));
}

async function handleForgotPassword(req, res) {
    const { email } = await parseBody(req);
    if (!email) return sendJSON(res, { error: 'Email required' }, 400);
    const result = await recoverPassword(email);
    sendJSON(res, result, result.success ? 200 : 400);
}

async function handleResetPassword(req, res) {
    const { email, token, password } = await parseBody(req);
    if (!email || !token || !password) return sendJSON(res, { error: 'Missing fields' }, 400);
    const result = await resetPasswordWithToken(email, token, password);
    sendJSON(res, result, result.success ? 200 : 400);
}

async function handleVerify(req, res) {
    const { email, token } = await parseBody(req);
    const result = await verifyEmailToken(email, token);
    sendJSON(res, result, result.success ? 200 : 400);
}

function handleAuthConfig(res) {
    sendJSON(res, {
        emailVerificationEnabled: process.env.ENABLE_EMAIL_VERIFICATION !== 'false',
        passwordRecoveryEnabled: process.env.ENABLE_PASSWORD_RECOVERY !== 'false'
    });
}

async function handleAuthAPI(req, res) {
    const handlers = {
        'POST /api/login': () => handleLogin(req, res),
        'POST /api/register': () => handleRegister(req, res),
        'POST /api/logout': () => handleLogout(res),
        'POST /api/forgot-password': () => handleForgotPassword(req, res),
        'POST /api/reset-password': () => handleResetPassword(req, res),
        'POST /api/verify': () => handleVerify(req, res),
        'GET /api/auth-config': () => handleAuthConfig(res)
    };

    const handler = handlers[`${req.method} ${req.url}`];
    if (!handler) return sendJSON(res, { error: 'Not Found' }, 404);
    await handler();
}

async function handleMagicLink(req, res) {
    const token = req.url.split('?')[0].split('/login-child/')[1];
    const authResult = await authenticateChildByToken(token);

    if (authResult.success) {
        const maxAge = 365 * 24 * 60 * 60;
        const cookies = buildAuthCookies(authResult.email, 'child', authResult.familyId, maxAge);
        cookies.push(`child_id=${authResult.childId}; Max-Age=${maxAge}; Path=/; HttpOnly; SameSite=Lax`);
        res.writeHead(302, { Location: '/', 'Set-Cookie': cookies });
        return res.end();
    }

    res.writeHead(302, { Location: '/login.html?error=invalid_token' });
    res.end();
}

function createRouteContext(req) {
    const familyContext = getFamilyContext(req);
    const urlObj = new URL(req.url, `http://${req.headers.host}`);
    return {
        ...familyContext,
        method: req.method,
        pathname: urlObj.pathname,
        urlObj
    };
}

async function handleDataGet(ctx, req, res) {
    const queryChildId = ctx.urlObj.searchParams.get('childId');
    const targetChildId = ctx.role === 'child' ? ctx.childId : (queryChildId ? parseInt(queryChildId) : null);
    const data = await loadFamilyData(ctx.familyId, targetChildId);

    const families = await loadFamilies();
    const familyInfo = families.families[ctx.familyId];
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

async function handleBaseDataGet(res) {
    sendJSON(res, loadBaseData());
}

async function handleChangePin(ctx, req, res) {
    const { oldPin, newPin } = await parseBody(req);
    const result = await changePassword(ctx.familyId, ctx.role, oldPin, newPin);
    sendJSON(res, result, result.success ? 200 : 400);
}

async function handleLegacyChildLink(ctx, req, res) {
    if (ctx.role !== 'admin') return sendJSON(res, { error: 'Not Found or Forbidden' }, 404);
    const families = await loadFamilies();
    const family = families.families[ctx.familyId];
    if (!family || !family.children || family.children.length === 0) {
        return sendJSON(res, { error: 'No children found' }, 404);
    }

    const link = await getChildLoginLink(ctx.familyId, family.children[0].id, req);
    sendJSON(res, { link });
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

async function handleSearchUser(ctx, res) {
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

async function handleFriendsList(ctx, res) {
    if (ctx.role !== 'child') return sendJSON(res, { error: 'Not Found or Forbidden' }, 404);
    const friends = await getFriendsData(ctx.familyId, ctx.childId);
    sendJSON(res, friends);
}

async function handleChildrenDynamicRoute(ctx, req, res) {
    const linkMatch = ctx.pathname.match(/^\/api\/children\/(\d+)\/link$/);
    if (linkMatch && ctx.method === 'GET' && ctx.role === 'admin') {
        const targetChildId = parseInt(linkMatch[1]);
        const link = await getChildLoginLink(ctx.familyId, targetChildId, req);
        return sendJSON(res, { link });
    }

    const regenMatch = ctx.pathname.match(/^\/api\/children\/(\d+)\/regenerate-token$/);
    if (regenMatch && ctx.method === 'POST' && ctx.role === 'admin') {
        const targetChildId = parseInt(regenMatch[1]);
        const success = await regenerateChildToken(ctx.familyId, targetChildId);
        if (!success) return sendJSON(res, { error: 'Failed' }, 400);
        const link = await getChildLoginLink(ctx.familyId, targetChildId, req);
        return sendJSON(res, { success: true, link });
    }

    const deleteMatch = ctx.pathname.match(/^\/api\/children\/(\d+)$/);
    if (deleteMatch && ctx.method === 'DELETE' && ctx.role === 'admin') {
        const targetChildId = parseInt(deleteMatch[1]);
        const success = await deleteChild(ctx.familyId, targetChildId);
        return sendJSON(res, success ? { success: true } : { error: 'Failed' }, success ? 200 : 400);
    }

    const settingsMatch = ctx.pathname.match(/^\/api\/children\/(\d+)\/settings$/);
    if (settingsMatch && ctx.method === 'POST' && ctx.role === 'admin') {
        const targetChildId = parseInt(settingsMatch[1]);
        const body = await parseBody(req);
        const result = await updateChildSettings(ctx.familyId, targetChildId, body);
        return sendJSON(res, result, result.success ? 200 : 400);
    }

    return false;
}

async function handleAPI(req, res) {
    const ctx = createRouteContext(req);
    if (!ctx.familyId) return sendJSON(res, { error: 'Unauthorized' }, 401);

    const staticRoutes = {
        'GET /api/data': () => handleDataGet(ctx, req, res),
        'POST /api/data': () => handleDataPost(ctx, req, res),
        'POST /api/children': () => handleChildrenCreate(ctx, req, res),
        'GET /api/base-data': () => handleBaseDataGet(res),
        'POST /api/change-pin': () => handleChangePin(ctx, req, res),
        'GET /api/child-link': () => handleLegacyChildLink(ctx, req, res),
        'POST /api/update-family-settings': () => handleUpdateFamilySettings(ctx, req, res),
        'POST /api/update-nickname': () => handleUpdateNickname(ctx, req, res),
        'GET /api/search-user': () => handleSearchUser(ctx, res),
        'POST /api/add-friend': () => handleAddFriend(ctx, req, res),
        'GET /api/friends-list': () => handleFriendsList(ctx, res)
    };

    const staticHandler = staticRoutes[`${ctx.method} ${ctx.pathname}`];
    if (staticHandler) return staticHandler();

    const handledDynamic = await handleChildrenDynamicRoute(ctx, req, res);
    if (handledDynamic !== false) return;

    sendJSON(res, { error: 'Not Found or Forbidden' }, 404);
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

async function handleSuperFamilyData(url, method, req, res) {
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

async function handleSuperFamilyBlock(url, method, req, res) {
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

async function handleSuperFamilyRegen(url, method, res) {
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

async function handleSuperChildRegen(url, method, res) {
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

async function handleSuperAdminAPI(req, res) {
    const { role } = getFamilyContext(req);
    if (role !== 'super_admin') return sendJSON(res, { error: 'Forbidden' }, 403);

    const key = `${req.method} ${req.url}`;
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
        }
    };

    const handler = staticHandlers[key];
    if (handler) return handler();

    const dynamicHandlers = [
        () => handleSuperFamilyData(req.url, req.method, req, res),
        () => handleSuperFamilyBlock(req.url, req.method, req, res),
        () => handleSuperFamilyRegen(req.url, req.method, res),
        () => handleSuperChildRegen(req.url, req.method, res)
    ];

    for (const dynamicHandler of dynamicHandlers) {
        const handled = await dynamicHandler();
        if (handled !== false) return;
    }

    sendJSON(res, { error: 'Not Found' }, 404);
}

module.exports = { handleAPI, handleSuperAdminAPI, handleAuthAPI, handleMagicLink };
