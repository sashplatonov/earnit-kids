const Router = require('../utils/Router');
const { createRouteContext, sendJSON } = require('../utils/controllerUtils');
const familyController = require('../controllers/familyController');
const childController = require('../controllers/childController');
const analyticsController = require('../controllers/analyticsController');
const friendsController = require('../controllers/friendsController');
const { loadBaseData } = require('../services/baseDataService');
const authController = require('../controllers/authController');
const superAdminController = require('../controllers/superAdminController');
const { validateCsrf } = require('../utils/authUtils');
const parseBody = require('../middleware/body-parser');

const apiRouter = new Router();

// Middleware to setup ctx and body parsing
apiRouter.use(async (ctx, req, res) => {
    // Already populated by initial context if passed, but let's use ctx
    if (['POST', 'PUT', 'DELETE', 'PATCH'].includes(req.method)) {
        await parseBody.middleware(ctx, req, res);
    }
});

// Middleware for CSRF and Auth checks for main API
const apiAuthMiddleware = async (ctx, req, res) => {
    if (!ctx.familyId) {
        sendJSON(res, { error: 'Unauthorized' }, 401);
        return false;
    }
    if (['POST', 'DELETE', 'PUT'].includes(ctx.method)) {
        if (!validateCsrf(req, ctx.csrfToken)) {
            sendJSON(res, { error: 'Invalid CSRF token' }, 403);
            return false;
        }
    }
    return true; // continue
};

// --- AUTH ROUTES ---
apiRouter.post('/api/login', authController.handleLogin);
apiRouter.post('/api/register', authController.handleRegister);
apiRouter.post('/api/logout', (ctx, req, res) => authController.handleLogout(res));
apiRouter.post('/api/forgot-password', authController.handleForgotPassword);
apiRouter.post('/api/reset-password', authController.handleResetPassword);
apiRouter.post('/api/verify', authController.handleVerify);
apiRouter.get('/api/auth-config', (ctx, req, res) => authController.handleAuthConfig(res));


// --- SUPER ADMIN ROUTES ---
const superAdminMiddleware = async (ctx, req, res) => {
    if (ctx.role !== 'super_admin') {
        sendJSON(res, { error: 'Forbidden' }, 403);
        return false;
    }
    return true; // continue
};

apiRouter.get('/api/super/families', async (ctx, req, res) => {
    if (!await superAdminMiddleware(ctx, req, res)) return;
    return sendJSON(res, { families: await superAdminController.getSuperFamiliesList() });
});
apiRouter.get('/api/super/base-data', async (ctx, req, res) => {
    if (!await superAdminMiddleware(ctx, req, res)) return;
    sendJSON(res, loadBaseData());
});
// The rest of super limits can be handled by just mapping `handleSuperAdminAPI`. Wait, `handleSuperAdminAPI` has its own logic internally. Let's just delegate for now.
apiRouter.add('ALL', '/api/super/:path', async (ctx, req, res) => {
    await superAdminController.handleSuperAdminAPI(req, res, ctx);
});

// --- MAIN API ROUTES ---
const mainApiHandler = async (ctx, req, res, fn) => {
    if (!await apiAuthMiddleware(ctx, req, res)) return;
    return await fn(ctx, req, res);
};

apiRouter.get('/api/data', (ctx, req, res) => mainApiHandler(ctx, req, res, familyController.handleDataGet));
apiRouter.post('/api/data', (ctx, req, res) => mainApiHandler(ctx, req, res, familyController.handleDataPost));
apiRouter.post('/api/children', (ctx, req, res) => mainApiHandler(ctx, req, res, familyController.handleChildrenCreate));
apiRouter.get('/api/base-data', (ctx, req, res) => mainApiHandler(ctx, req, res, async () => sendJSON(res, loadBaseData())));
apiRouter.post('/api/update-family-settings', (ctx, req, res) => mainApiHandler(ctx, req, res, familyController.handleUpdateFamilySettings));
apiRouter.post('/api/update-nickname', (ctx, req, res) => mainApiHandler(ctx, req, res, familyController.handleUpdateNickname));

// Friends & Social
apiRouter.get('/api/search-user', (ctx, req, res) => mainApiHandler(ctx, req, res, friendsController.handleSearchUser));
apiRouter.post('/api/add-friend', (ctx, req, res) => mainApiHandler(ctx, req, res, friendsController.handleAddFriend));
apiRouter.get('/api/friends-list', (ctx, req, res) => mainApiHandler(ctx, req, res, friendsController.handleFriendsList));

// Analytics
apiRouter.get('/api/analytics', (ctx, req, res) => mainApiHandler(ctx, req, res, analyticsController.handleAnalytics));

// Children dynamic routes
apiRouter.get('/api/children/:id/link', (ctx, req, res) => mainApiHandler(ctx, req, res, async (ctx, req, res) => {
    ctx.targetChildId = parseInt(ctx.params.id);
    await childController.handleLinkGet({ ctx, req, res, targetChildId: ctx.targetChildId });
}));

apiRouter.post('/api/children/:id/regenerate-token', (ctx, req, res) => mainApiHandler(ctx, req, res, async (ctx, req, res) => {
    ctx.targetChildId = parseInt(ctx.params.id);
    await childController.handleTokenRegen({ ctx, req, res, targetChildId: ctx.targetChildId });
}));

apiRouter.delete('/api/children/:id', (ctx, req, res) => mainApiHandler(ctx, req, res, async (ctx, req, res) => {
    ctx.targetChildId = parseInt(ctx.params.id);
    await childController.handleDeleteChild({ ctx, req, res, targetChildId: ctx.targetChildId });
}));

apiRouter.post('/api/children/:id/settings', (ctx, req, res) => mainApiHandler(ctx, req, res, async (ctx, req, res) => {
    ctx.targetChildId = parseInt(ctx.params.id);
    await childController.handleUpdateSettings({ ctx, req, res, targetChildId: ctx.targetChildId });
}));

async function apiRoutes(req, res) {
    const ctx = createRouteContext(req);
    const handled = await apiRouter.handle(req, res, ctx);
    if (!handled) {
        sendJSON(res, { error: 'Not Found' }, 404);
    }
}

module.exports = apiRoutes;
