const {
    loadFamilyData, saveFamilyData, updateLastActivity,
    loadFamilies, saveFamilies, findFamilyByEmail, getChildLoginLink,
    regenerateChildToken, updateFamilySettings,
    updateNickname, searchByNickname, addFriend, getFriendsData
} = require('../services/familyService');
const {
    authenticateUser, authenticateChildByToken,
    registerFamily, isValidPassword, changePassword, recoverPassword
} = require('../services/authService');
const { loadBaseData, saveBaseData } = require('../services/baseDataService');
const { createBackup, restoreBackup } = require('../services/backupService');
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
        role: cookies.app_role || null,
        email: cookies.app_auth || null
    };
}

async function handleAuthAPI(req, res) {
    const url = req.url;
    const method = req.method;

    if (url === '/api/login' && method === 'POST') {
        const body = await parseBody(req);
        const { email, pin } = body;
        const result = await authenticateUser(email, pin);

        if (result.success) {
            const maxAge = 24 * 60 * 60;
            const cookiesArr = [
                `app_auth=${email}; Max-Age=${maxAge}; Path=/; HttpOnly; SameSite=Lax`,
                `app_role=${result.role}; Max-Age=${maxAge}; Path=/; SameSite=Lax`
            ];
            if (result.familyId) cookiesArr.push(`family_id=${result.familyId}; Max-Age=${maxAge}; Path=/; HttpOnly; SameSite=Lax`);

            res.writeHead(200, { 'Content-Type': 'application/json', 'Set-Cookie': cookiesArr });
            return res.end(JSON.stringify(result));
        }
        return sendJSON(res, { error: result.error }, 401);
    }

    if (url === '/api/register' && method === 'POST') {
        const body = await parseBody(req);
        const { familyName, email, adminPin } = body;
        const result = await registerFamily(familyName, email, adminPin);
        return sendJSON(res, result, result.success ? 200 : 400);
    }

    if (url === '/api/logout' && method === 'POST') {
        res.writeHead(200, {
            'Content-Type': 'application/json',
            'Set-Cookie': [
                'app_auth=; Max-Age=0; Path=/; HttpOnly; SameSite=Lax',
                'app_role=; Max-Age=0; Path=/; SameSite=Lax',
                'family_id=; Max-Age=0; Path=/; HttpOnly; SameSite=Lax'
            ]
        });
        return res.end(JSON.stringify({ success: true }));
    }

    if (url === '/api/forgot-password' && method === 'POST') {
        const body = await parseBody(req);
        const { email } = body;
        if (!email) return sendJSON(res, { error: 'Email required' }, 400);
        const result = await recoverPassword(email);
        return sendJSON(res, result, result.success ? 200 : 400);
    }

    sendJSON(res, { error: 'Not Found' }, 404);
}

async function handleMagicLink(req, res) {
    const token = req.url.split('?')[0].split('/login-child/')[1];
    const authResult = await authenticateChildByToken(token);

    if (authResult.success) {
        const maxAge = 365 * 24 * 60 * 60;
        const cookiesArr = [
            `app_auth=${authResult.email}; Max-Age=${maxAge}; Path=/; HttpOnly; SameSite=Lax`,
            `app_role=child; Max-Age=${maxAge}; Path=/; SameSite=Lax`,
            `family_id=${authResult.familyId}; Max-Age=${maxAge}; Path=/; HttpOnly; SameSite=Lax`
        ];
        res.writeHead(302, { 'Location': '/', 'Set-Cookie': cookiesArr });
        res.end();
    } else {
        res.writeHead(302, { 'Location': '/login.html?error=invalid_token' });
        res.end();
    }
}

async function handleAPI(req, res) {
    const { familyId, role } = getFamilyContext(req);
    if (!familyId) return sendJSON(res, { error: 'Unauthorized' }, 401);

    const url = req.url;
    const method = req.method;

    if (url === '/api/data' && method === 'GET') {
        const data = await loadFamilyData(familyId);
        const families = await loadFamilies();
        const familyInfo = families.families[familyId];
        data.isAdmin = role === 'admin';
        data.familyName = familyInfo ? familyInfo.name : 'Shop';
        data.childNickname = familyInfo ? familyInfo.child_nickname : null;
        data.monthlyLimit = familyInfo ? (familyInfo.monthly_limit || 10000) : 10000;
        return sendJSON(res, data);
    }

    if (url === '/api/data' && method === 'POST' && (role === 'admin' || role === 'child')) {
        const body = await parseBody(req);
        if (await saveFamilyData(familyId, body)) {
            await updateLastActivity(familyId);
            return sendJSON(res, { success: true });
        }
        return sendJSON(res, { error: 'Save failed' }, 500);
    }

    if (url === '/api/base-data' && method === 'GET') {
        return sendJSON(res, loadBaseData());
    }

    if (url === '/api/change-pin' && method === 'POST') {
        const body = await parseBody(req);
        const { oldPin, newPin } = body;
        const result = await changePassword(familyId, role, oldPin, newPin);
        return sendJSON(res, result, result.success ? 200 : 400);
    }

    if (url === '/api/child-link' && method === 'GET' && role === 'admin') {
        const link = await getChildLoginLink(familyId, req);
        return sendJSON(res, { link });
    }

    if (url === '/api/regenerate-token' && method === 'POST' && role === 'admin') {
        if (await regenerateChildToken(familyId)) {
            return sendJSON(res, { success: true, link: await getChildLoginLink(familyId, req) });
        }
    }

    if (url === '/api/update-family-settings' && method === 'POST' && role === 'admin') {
        const body = await parseBody(req);
        const result = await updateFamilySettings(familyId, body);
        return sendJSON(res, result, result.success ? 200 : 400);
    }

    if (url === '/api/update-nickname' && method === 'POST' && role === 'child') {
        const body = await parseBody(req);
        const result = await updateNickname(familyId, body.nickname);
        return sendJSON(res, result, result.success ? 200 : 400);
    }

    if (url.startsWith('/api/search-user') && method === 'GET' && role === 'child') {
        const urlObj = new URL(req.url, `http://${req.headers.host}`);
        const nickname = urlObj.searchParams.get('nickname');
        const results = await searchByNickname(nickname);
        return sendJSON(res, results);
    }

    if (url === '/api/add-friend' && method === 'POST' && role === 'child') {
        const body = await parseBody(req);
        const result = await addFriend(familyId, body.friendId);
        return sendJSON(res, result, result.success ? 200 : 400);
    }

    if (url === '/api/friends-list' && method === 'GET' && role === 'child') {
        const friends = await getFriendsData(familyId);
        return sendJSON(res, friends);
    }

    sendJSON(res, { error: 'Not Found or Forbidden' }, 404);
}

async function handleSuperAdminAPI(req, res) {
    const { role } = getFamilyContext(req);
    if (role !== 'super_admin') return sendJSON(res, { error: 'Forbidden' }, 403);

    const url = req.url;
    const method = req.method;

    if (url === '/api/super/families' && method === 'GET') {
        const familiesData = await loadFamilies();
        const familyList = [];
        for (const [id, data] of Object.entries(familiesData.families)) {
            const familyData = await loadFamilyData(id);
            familyList.push({
                id,
                ...data,
                tasksCount: familyData.tasks ? familyData.tasks.length : 0,
                shopCount: familyData.shop ? familyData.shop.length : 0
            });
        }
        return sendJSON(res, { families: familyList });
    }

    if (url === '/api/super/base-data' && method === 'GET') {
        return sendJSON(res, loadBaseData());
    }

    if (url === '/api/super/backup' && method === 'GET') {
        return createBackup(req, res);
    }

    if (url === '/api/super/restore' && method === 'POST') {
        return restoreBackup(req, res);
    }

    if (url === '/api/super/base-data' && method === 'POST') {
        const body = await parseBody(req);
        if (saveBaseData(body)) return sendJSON(res, { success: true });
        return sendJSON(res, { error: 'Failed' }, 500);
    }

    const familyMatch = url.match(/^\/api\/super\/family\/([^/]+)\/data$/);
    if (familyMatch) {
        const fId = familyMatch[1];
        if (method === 'GET') {
            const families = await loadFamilies();
            const familyInfo = families.families[fId];
            if (!familyInfo) return sendJSON(res, { error: 'Not found' }, 404);
            return sendJSON(res, { familyId: fId, familyInfo, data: await loadFamilyData(fId) });
        }
        if (method === 'POST') {
            const body = await parseBody(req);
            if (await saveFamilyData(fId, body)) return sendJSON(res, { success: true });
            return sendJSON(res, { error: 'Failed' }, 500);
        }
    }

    const blockMatch = url.match(/^\/api\/super\/family\/([^/]+)\/block$/);
    if (blockMatch && method === 'POST') {
        const fId = blockMatch[1];
        const body = await parseBody(req);
        const families = await loadFamilies();
        if (families.families[fId]) {
            families.families[fId].isBlocked = body.isBlocked;
            if (await saveFamilies(families)) return sendJSON(res, { success: true });
        }
        return sendJSON(res, { error: 'Failed' }, 404);
    }

    const regenMatch = url.match(/^\/api\/super\/family\/([^/]+)\/regenerate-token$/);
    if (regenMatch && method === 'POST') {
        const fId = regenMatch[1];
        if (await regenerateChildToken(fId)) {
            return sendJSON(res, { success: true });
        }
        return sendJSON(res, { error: 'Failed' }, 404);
    }

    sendJSON(res, { error: 'Not Found' }, 404);
}

module.exports = { handleAPI, handleSuperAdminAPI, handleAuthAPI, handleMagicLink };
