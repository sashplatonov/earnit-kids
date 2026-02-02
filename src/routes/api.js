const { handleAPI, handleSuperAdminAPI, handleAuthAPI } = require('../controllers/apiController');

async function apiRoutes(req, res) {
    const url = req.url;
    if (url.startsWith('/api/login') || url.startsWith('/api/logout') || url.startsWith('/api/register') || url.startsWith('/api/forgot-password')) {
        await handleAuthAPI(req, res);
        return;
    }
    if (url.startsWith('/api/super/')) {
        await handleSuperAdminAPI(req, res);
        return;
    }
    await handleAPI(req, res);
}

module.exports = apiRoutes;
