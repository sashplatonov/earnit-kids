const { handleAuthAPI, handleMagicLink } = require('./authController');
const { handleSuperAdminAPI } = require('./superAdminController');
const familyController = require('./familyController');
const childController = require('./childController');
const { loadBaseData } = require('../services/baseDataService');
const { sendJSON, createRouteContext } = require('../utils/controllerUtils');

async function handleChildrenDynamicRoute(ctx, req, res) {
    const linkMatch = ctx.pathname.match(/^\/api\/children\/(\d+)\/link$/);
    if (linkMatch && ctx.method === 'GET') {
        await childController.handleLinkGet({ ctx, req, res, targetChildId: parseInt(linkMatch[1]) });
        return true;
    }

    const regenMatch = ctx.pathname.match(/^\/api\/children\/(\d+)\/regenerate-token$/);
    if (regenMatch && ctx.method === 'POST') {
        await childController.handleTokenRegen({ ctx, req, res, targetChildId: parseInt(regenMatch[1]) });
        return true;
    }

    const deleteMatch = ctx.pathname.match(/^\/api\/children\/(\d+)$/);
    if (deleteMatch && ctx.method === 'DELETE') {
        await childController.handleDeleteChild({ ctx, req, res, targetChildId: parseInt(deleteMatch[1]) });
        return true;
    }

    const settingsMatch = ctx.pathname.match(/^\/api\/children\/(\d+)\/settings$/);
    if (settingsMatch && ctx.method === 'POST') {
        await childController.handleUpdateSettings({ ctx, req, res, targetChildId: parseInt(settingsMatch[1]) });
        return true;
    }

    return false;
}

async function handleAPI(req, res) {
    const ctx = createRouteContext(req);
    if (!ctx.familyId) return sendJSON(res, { error: 'Unauthorized' }, 401);

    if (['POST', 'DELETE', 'PUT'].includes(ctx.method)) {
        const { validateCsrf } = require('../utils/authUtils');
        if (!validateCsrf(req, ctx.csrfToken)) {
            return sendJSON(res, { error: 'Invalid CSRF token' }, 403);
        }
    }

    const routes = {
        'GET /api/data': familyController.handleDataGet,
        'POST /api/data': familyController.handleDataPost,
        'POST /api/children': familyController.handleChildrenCreate,
        'GET /api/base-data': () => sendJSON(res, loadBaseData()),
        'POST /api/update-family-settings': familyController.handleUpdateFamilySettings,
        'POST /api/update-nickname': familyController.handleUpdateNickname,
        'GET /api/search-user': familyController.handleSearchUser,
        'POST /api/add-friend': familyController.handleAddFriend,
        'GET /api/friends-list': familyController.handleFriendsList,
        'GET /api/analytics': familyController.handleAnalytics
    };

    const handler = routes[`${ctx.method} ${ctx.pathname}`];
    if (handler) return await handler(ctx, req, res);

    if (await handleChildrenDynamicRoute(ctx, req, res)) return;

    sendJSON(res, { error: 'Not Found or Forbidden' }, 404);
}


module.exports = {
    handleAPI,
    handleSuperAdminAPI: (req, res) => handleSuperAdminAPI(req, res, createRouteContext(req)),
    handleAuthAPI,
    handleMagicLink
};
