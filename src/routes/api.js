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
        if (ctx.pathname === '/api/super/db-restore') {
            return;
        }
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
apiRouter.post('/api/login', (ctx, req, res) => authController.handleLogin(req, res));
apiRouter.post('/api/register', (ctx, req, res) => authController.handleRegister(req, res));
apiRouter.post('/api/logout', (ctx, req, res) => authController.handleLogout(res));
apiRouter.post('/api/forgot-password', (ctx, req, res) => authController.handleForgotPassword(req, res));
apiRouter.post('/api/reset-password', (ctx, req, res) => authController.handleResetPassword(req, res));
apiRouter.post('/api/verify', (ctx, req, res) => authController.handleVerify(req, res));
apiRouter.get('/api/auth-config', (ctx, req, res) => authController.handleAuthConfig(res));

// Health check
apiRouter.get('/api/health', async (ctx, req, res) => {
    const { testConnection } = require('../db/connection');
    const dbOk = await testConnection().catch(() => false);

    const status = dbOk ? 200 : 503;
    sendJSON(res, {
        status: dbOk ? 'ok' : 'error',
        database: dbOk ? 'connected' : 'disconnected',
        uptime: process.uptime(),
        timestamp: new Date().toISOString()
    }, status);
});

apiRouter.get('/api/metrics', async (ctx, req, res) => {
    const { generateMetrics } = require('../utils/metrics');
    res.writeHead(200, { 'Content-Type': 'text/plain; version=0.0.4; charset=utf-8' });
    res.end(generateMetrics());
});

apiRouter.get('/api/docs', async (ctx, req, res) => {
    const fs = require('fs');
    const path = require('path');
    const content = fs.readFileSync(path.join(__dirname, '../../views/api-docs.html'), 'utf8');
    res.writeHead(200, { 'Content-Type': 'text/html' });
    res.end(content);
});

apiRouter.get('/api/openapi.yaml', async (ctx, req, res) => {
    const fs = require('fs');
    const path = require('path');
    const content = fs.readFileSync(path.join(__dirname, '../../docs/openapi.yaml'), 'utf8');
    res.writeHead(200, { 'Content-Type': 'text/yaml' });
    res.end(content);
});


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
const mainApiHandler = async ({ ctx, req, res, fn }) => {
    if (!await apiAuthMiddleware(ctx, req, res)) return;
    return await fn(ctx, req, res);
};

apiRouter.get('/api/data', (ctx, req, res) => mainApiHandler({ ctx, req, res, fn: familyController.handleDataGet }));
apiRouter.post('/api/data', (ctx, req, res) => mainApiHandler({ ctx, req, res, fn: familyController.handleDataPost }));
apiRouter.post('/api/children', (ctx, req, res) => mainApiHandler({ ctx, req, res, fn: familyController.handleChildrenCreate }));
apiRouter.get('/api/base-data', (ctx, req, res) => mainApiHandler({ ctx, req, res, fn: async () => sendJSON(res, loadBaseData()) }));
apiRouter.post('/api/update-nickname', (ctx, req, res) => mainApiHandler({ ctx, req, res, fn: familyController.handleUpdateNickname }));

// Friends & Social
apiRouter.get('/api/search-user', (ctx, req, res) => mainApiHandler({ ctx, req, res, fn: friendsController.handleSearchUser }));
apiRouter.post('/api/add-friend', (ctx, req, res) => mainApiHandler({ ctx, req, res, fn: friendsController.handleAddFriend }));
apiRouter.get('/api/friends-list', (ctx, req, res) => mainApiHandler({ ctx, req, res, fn: friendsController.handleFriendsList }));

// Analytics
apiRouter.get('/api/analytics', (ctx, req, res) => mainApiHandler({ ctx, req, res, fn: analyticsController.handleAnalytics }));

// Paginated History & Requests (New)
apiRouter.get('/api/history', (ctx, req, res) => mainApiHandler({ ctx, req, res, fn: familyController.handleHistoryGet }));
apiRouter.get('/api/requests', (ctx, req, res) => mainApiHandler({ ctx, req, res, fn: familyController.handleRequestsGet }));

// --- API V1 (Versioning) ---
// We map /api/v1/ to the same handlers for now
apiRouter.get('/api/v1/data', (ctx, req, res) => mainApiHandler({ ctx, req, res, fn: familyController.handleDataGet }));
apiRouter.post('/api/v1/data', (ctx, req, res) => mainApiHandler({ ctx, req, res, fn: familyController.handleDataPost }));
apiRouter.get('/api/v1/history', (ctx, req, res) => mainApiHandler({ ctx, req, res, fn: familyController.handleHistoryGet }));
apiRouter.get('/api/v1/requests', (ctx, req, res) => mainApiHandler({ ctx, req, res, fn: familyController.handleRequestsGet }));
apiRouter.get('/api/v1/analytics', (ctx, req, res) => mainApiHandler({ ctx, req, res, fn: analyticsController.handleAnalytics }));

// Children dynamic routes
apiRouter.get('/api/children/:id/link', (ctx, req, res) => mainApiHandler({
    ctx, req, res,
    fn: async (c, rq, rs) => {
        c.targetChildId = parseInt(c.params.id);
        await childController.handleLinkGet({ ctx: c, req: rq, res: rs, targetChildId: c.targetChildId });
    }
}));

apiRouter.post('/api/children/:id/regenerate-token', (ctx, req, res) => mainApiHandler({
    ctx, req, res,
    fn: async (c, rq, rs) => {
        c.targetChildId = parseInt(c.params.id);
        await childController.handleTokenRegen({ ctx: c, req: rq, res: rs, targetChildId: c.targetChildId });
    }
}));

apiRouter.delete('/api/children/:id', (ctx, req, res) => mainApiHandler({
    ctx, req, res,
    fn: async (c, rq, rs) => {
        c.targetChildId = parseInt(c.params.id);
        await childController.handleDeleteChild({ ctx: c, req: rq, res: rs, targetChildId: c.targetChildId });
    }
}));

apiRouter.post('/api/children/:id/settings', (ctx, req, res) => mainApiHandler({
    ctx, req, res,
    fn: async (c, rq, rs) => {
        c.targetChildId = parseInt(c.params.id);
        await childController.handleUpdateSettings({ ctx: c, req: rq, res: rs, targetChildId: c.targetChildId });
    }
}));

async function apiRoutes(req, res) {
    const ctx = createRouteContext(req);
    const handled = await apiRouter.handle(req, res, ctx);
    if (!handled) {
        sendJSON(res, { error: 'Not Found' }, 404);
    }
}

module.exports = apiRoutes;
