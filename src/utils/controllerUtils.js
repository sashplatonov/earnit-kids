const { getCookies } = require('../controllers/viewController');

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

module.exports = {
    sendJSON,
    getFamilyContext,
    createRouteContext
};
