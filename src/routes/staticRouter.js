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
async function routeStatic({ pathOnly, req, res, viewController }) {
    const { serveIndex, serveLogin, serveResetPassword, serveVerify, serveStatic } = viewController;

    if (pathOnly === '/' || pathOnly === '/index.html') {
        return await serveIndex(req, res);
    }
    if (pathOnly === '/login.html') {
        return serveLogin(req, res);
    }
    if (pathOnly === '/reset-password') {
        return serveResetPassword(req, res);
    }
    if (pathOnly === '/verify') {
        return serveVerify(req, res);
    }
    return serveStatic(req, res);
}

module.exports = {
    handleCors,
    routeStatic
};
