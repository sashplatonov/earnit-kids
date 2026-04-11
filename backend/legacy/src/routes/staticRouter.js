/** @file Static Router Express route definitions */
/**
 * Handle CORS preflight requests
 * @param {import('http').IncomingMessage} req 
 * @param {import('http').ServerResponse} res 
 * @returns {boolean} True if request was handled
 */
function handleCors(req, res) {
    if (req.method !== 'OPTIONS') {
        return false;
    }
    res.writeHead(204, {
        'Access-Control-Allow-Origin': req.headers.origin || '*',
        'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
        'Access-Control-Allow-Headers': 'Content-Type',
        'Access-Control-Allow-Credentials': 'true'
    });
    res.end();
    return true;
}

/**
 * Route request to static views or files
 * @param {object} params
 */
const blogController = require('../controllers/blogController');

async function handlePublicRoutes({ pathOnly, req, res, viewController }) {
    const { serveRoot, serveFeaturePage, serveAbout, serveFaq } = viewController;

    if (pathOnly === '/' || pathOnly === '/index.html') {
        await serveRoot(req, res);
        return true;
    }
    if (pathOnly === '/features' || pathOnly === '/features/') {
        res.writeHead(302, { Location: '/features/tasks' });
        res.end();
        return true;
    }
    if (pathOnly.startsWith('/features/') && pathOnly.length > '/features/'.length) {
        const slug = pathOnly.replace('/features/', '').replace(/\/+$/, '');
        await serveFeaturePage(req, res, slug);
        return true;
    }
    if (pathOnly === '/about') {
        await serveAbout(req, res);
        return true;
    }
    if (pathOnly === '/faq') {
        await serveFaq(req, res);
        return true;
    }
    return false;
}

async function handleBlogRoutes(pathOnly, req, res) {
    if (pathOnly === '/blog') {
        await blogController.serveBlogIndex(req, res);
        return true;
    }
    if (pathOnly.startsWith('/blog/') && pathOnly.length > '/blog/'.length) {
        const articleSlug = pathOnly.replace('/blog/', '').replace(/\/+$/, '');
        await blogController.serveArticle(req, res, articleSlug);
        return true;
    }
    return false;
}

async function handleAuthRoutes({ pathOnly, req, res, viewController }) {
    const { serveLogin, serveResetPassword, serveVerify } = viewController;
    if (pathOnly === '/login.html') {
        await serveLogin(req, res);
        return true;
    }
    if (pathOnly === '/reset-password') {
        await serveResetPassword(req, res);
        return true;
    }
    if (pathOnly === '/verify') {
        await serveVerify(req, res);
        return true;
    }
    return false;
}

async function routeStatic({ pathOnly, req, res, viewController }) {
    const { serveStatic } = viewController;
    if (await handlePublicRoutes({ pathOnly, req, res, viewController })) return;
    if (await handleBlogRoutes(pathOnly, req, res)) return;
    if (await handleAuthRoutes({ pathOnly, req, res, viewController })) return;
    return serveStatic(req, res);
}

module.exports = {
    handleCors,
    routeStatic
};
