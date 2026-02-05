const {
    loadFamilyData, saveFamilyData, updateLastActivity,
    loadFamilies, saveFamilies, findFamilyByEmail, getChildLoginLink,
    regenerateChildToken, updateFamilySettings,
    updateNickname, searchByNickname, addFriend, getFriendsData,
    addChild, deleteChild, updateChildSettings
} = require('../services/familyService');
const {
    authenticateUser, authenticateChildByToken,
    registerFamily, isValidPassword, changePassword, recoverPassword, resetPasswordWithToken, verifyEmailToken
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
                'family_id=; Max-Age=0; Path=/; HttpOnly; SameSite=Lax',
                'child_id=; Max-Age=0; Path=/; HttpOnly; SameSite=Lax'
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

    if (url === '/api/reset-password' && method === 'POST') {
        const body = await parseBody(req);
        const { email, token, password } = body;
        if (!email || !token || !password) return sendJSON(res, { error: 'Missing fields' }, 400);
        const result = await resetPasswordWithToken(email, token, password);
        return sendJSON(res, result, result.success ? 200 : 400);
    }

    if (url === '/api/verify' && method === 'POST') {
        const body = await parseBody(req);
        const { email, token } = body;
        const result = await verifyEmailToken(email, token);
        return sendJSON(res, result, result.success ? 200 : 400);
    }

    if (url === '/api/auth-config' && method === 'GET') {
        return sendJSON(res, {
            emailVerificationEnabled: process.env.ENABLE_EMAIL_VERIFICATION !== 'false',
            passwordRecoveryEnabled: process.env.ENABLE_PASSWORD_RECOVERY !== 'false'
        });
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
            `family_id=${authResult.familyId}; Max-Age=${maxAge}; Path=/; HttpOnly; SameSite=Lax`,
            `child_id=${authResult.childId}; Max-Age=${maxAge}; Path=/; HttpOnly; SameSite=Lax`
        ];
        res.writeHead(302, { 'Location': '/', 'Set-Cookie': cookiesArr });
        res.end();
    } else {
        res.writeHead(302, { 'Location': '/login.html?error=invalid_token' });
        res.end();
    }
}

async function handleAPI(req, res) {
    const { familyId, childId, role } = getFamilyContext(req);
    if (!familyId) return sendJSON(res, { error: 'Unauthorized' }, 401);

    const url = req.url;
    const method = req.method;
    const urlObj = new URL(url, `http://${req.headers.host}`);
    const pathname = urlObj.pathname;

    if (pathname === '/api/data' && method === 'GET') {
        // If parent requests, they can specify childId via query param to see specific child's data
        // If no childId param, they get the "Family View" (all data, or summary)
        const queryChildId = urlObj.searchParams.get('childId');
        const targetChildId = role === 'child' ? childId : (queryChildId ? parseInt(queryChildId) : null);

        const data = await loadFamilyData(familyId, targetChildId);
        const families = await loadFamilies();
        const familyInfo = families.families[familyId]; // This familyInfo now contains .children array

        data.isAdmin = role === 'admin';
        data.familyName = familyInfo ? familyInfo.name : 'Shop';

        // Populate Children List for Parent Dashboard
        if (role === 'admin' && familyInfo) {
            data.children = familyInfo.children || [];
        } else if (role === 'child' && familyInfo) {
            const cObj = familyInfo.children.find(c => c.id === childId);
            data.childNickname = cObj ? cObj.name : 'Child';
            if (cObj) {
                data.monthlyLimit = cObj.monthlyLimit;
                data.dailyCoinLimit = cObj.dailyCoinLimit;
            }
        }

        return sendJSON(res, data);
    }

    if (pathname === '/api/data' && method === 'POST' && (role === 'admin' || role === 'child')) {
        const body = await parseBody(req);
        // If role is child, force childId from context
        const actingChildId = role === 'child' ? childId : null;

        if (await saveFamilyData(familyId, body, actingChildId)) {
            await updateLastActivity(familyId);
            return sendJSON(res, { success: true });
        }
        return sendJSON(res, { error: 'Save failed' }, 500);
    }

    // Child Management Endpoints (Admin Only)
    if (pathname === '/api/children' && method === 'POST' && role === 'admin') {
        const body = await parseBody(req);
        if (!body.name) return sendJSON(res, { error: 'Name required' }, 400);
        const result = await addChild(familyId, body.name);
        return sendJSON(res, result, result.success ? 200 : 400);
    }

    // Check for child specific routes
    const childLinkMatch = pathname.match(/^\/api\/children\/(\d+)\/link$/);
    if (childLinkMatch && method === 'GET' && role === 'admin') {
        const targetCId = parseInt(childLinkMatch[1]);
        const link = await getChildLoginLink(familyId, targetCId, req);
        return sendJSON(res, { link });
    }

    const childRegenMatch = pathname.match(/^\/api\/children\/(\d+)\/regenerate-token$/);
    if (childRegenMatch && method === 'POST' && role === 'admin') {
        const targetCId = parseInt(childRegenMatch[1]);
        if (await regenerateChildToken(familyId, targetCId)) {
            return sendJSON(res, { success: true, link: await getChildLoginLink(familyId, targetCId, req) });
        }
        return sendJSON(res, { error: 'Failed' }, 400);
    }

    const childDeleteMatch = pathname.match(/^\/api\/children\/(\d+)$/);
    if (childDeleteMatch && method === 'DELETE' && role === 'admin') {
        const targetCId = parseInt(childDeleteMatch[1]);
        if (await deleteChild(familyId, targetCId)) {
            return sendJSON(res, { success: true });
        }
        return sendJSON(res, { error: 'Failed' }, 400);
    }

    // New child settings route
    const childSettingsMatch = pathname.match(/^\/api\/children\/(\d+)\/settings$/);
    if (childSettingsMatch && method === 'POST' && role === 'admin') {
        const childId = parseInt(childSettingsMatch[1]);
        const body = await parseBody(req);
        const result = await updateChildSettings(familyId, childId, body);
        return sendJSON(res, result, result.success ? 200 : 400);
    }

    // Legacy Routes mapping (deprecated or adapted)

    if (pathname === '/api/base-data' && method === 'GET') {
        return sendJSON(res, loadBaseData());
    }

    if (pathname === '/api/change-pin' && method === 'POST') {
        const body = await parseBody(req);
        const { oldPin, newPin } = body;
        const result = await changePassword(familyId, role, oldPin, newPin);
        return sendJSON(res, result, result.success ? 200 : 400);
    }

    // Deprecated single-child link route - redirect to first child?
    if (pathname === '/api/child-link' && method === 'GET' && role === 'admin') {
        // Fallback: Get first child
        const families = await loadFamilies();
        const fam = families.families[familyId];
        if (fam && fam.children && fam.children.length > 0) {
            const link = await getChildLoginLink(familyId, fam.children[0].id, req);
            return sendJSON(res, { link });
        }
        return sendJSON(res, { error: 'No children found' }, 404);
    }

    if (pathname === '/api/update-family-settings' && method === 'POST' && role === 'admin') {
        const body = await parseBody(req);
        const result = await updateFamilySettings(familyId, body);
        return sendJSON(res, result, result.success ? 200 : 400);
    }

    if (pathname === '/api/update-nickname' && method === 'POST' && role === 'child') {
        const body = await parseBody(req);
        const result = await updateNickname(familyId, childId, body.nickname);
        return sendJSON(res, result, result.success ? 200 : 400);
    }

    if (pathname === '/api/search-user' && method === 'GET' && role === 'child') {
        const nickname = urlObj.searchParams.get('nickname');
        const results = await searchByNickname(nickname);
        return sendJSON(res, results);
    }

    if (pathname === '/api/add-friend' && method === 'POST' && role === 'child') {
        const body = await parseBody(req);
        const result = await addFriend(childId, body.friendId); // friendId is now friendChildId
        return sendJSON(res, result, result.success ? 200 : 400);
    }

    if (pathname === '/api/friends-list' && method === 'GET' && role === 'child') {
        const friends = await getFriendsData(familyId, childId);
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
            // This loop is N+1 but fine for super admin
            const familyData = await loadFamilyData(id); // gets all data
            familyList.push({
                id,
                ...data,
                childrenCount: data.children ? data.children.length : 0,
                tasksCount: familyData.tasks ? familyData.tasks.length : 0, // Total
                shopCount: familyData.shop ? familyData.shop.length : 0
            });
        }
        return sendJSON(res, { families: familyList });
    }

    if (url === '/api/super/base-data' && method === 'GET') {
        return sendJSON(res, loadBaseData());
    }

    if (url === '/api/super/db-backup' && method === 'GET') {
        return createBackup(req, res);
    }

    if (url === '/api/super/db-restore' && method === 'POST') {
        return restoreBackup(req, res);
    }

    if (url === '/api/super/db-copy-reserve' && method === 'POST') {
        return copyToReserve(req, res);
    }

    if (url === '/api/super/db-reserve-status' && method === 'GET') {
        const result = await checkReserveDbConnection();
        return sendJSON(res, result);
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
        // Legacy or Default to First Child
        const fId = regenMatch[1];
        const families = await loadFamilies();
        const f = families.families[fId];
        if (f && f.children.length > 0) {
            if (await regenerateChildToken(fId, f.children[0].id)) {
                return sendJSON(res, { success: true });
            }
        }
        return sendJSON(res, { error: 'Failed or no children' }, 400);
    }

    // New endpoint for specific child
    const regenChildMatch = url.match(/^\/api\/super\/child\/(\d+)\/regenerate-token$/);
    if (regenChildMatch && method === 'POST') {
        const cId = parseInt(regenChildMatch[1]);
        // We need familyId for regenerateChildToken? 
        // Logic: regenerateChildToken(familyId, childId)
        // We need to find familyId from childId or pass it?
        // familyRepository.regenerateToken uses childId directly usually? 
        // Let's check service/repo.
        // Service: regenerateChildToken(familyId, childId).
        // Repo: updateChild(childId, {token...}).
        // So we can find familyId or just ignore it if we trust childId?
        // Service calls `findById` on family.
        // We need to find which family this child belongs to.
        // Let's find family by checking all families? Expensive but ok for super admin?
        // Or query DB? `query('SELECT family_id FROM children WHERE id=$1', [cId])`.
        // Let's us `familyRepository` or `loadFamilies` and find it.
        const families = await loadFamilies();
        // families.families is object {id: family}.
        // We need to search.
        let targetFamilyId = null;
        Object.values(families.families).forEach(f => {
            if (f.children.some(c => c.id === cId)) targetFamilyId = f.id;
        });

        if (targetFamilyId) {
            if (await regenerateChildToken(targetFamilyId, cId)) {
                return sendJSON(res, { success: true });
            }
        }
        return sendJSON(res, { error: 'Child not found or failed' }, 404);
    }

    sendJSON(res, { error: 'Not Found' }, 404);
}

module.exports = { handleAPI, handleSuperAdminAPI, handleAuthAPI, handleMagicLink };
