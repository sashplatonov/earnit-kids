/** @file Controller Utils utility helpers */
const { getCookies } = require('../controllers/viewController');

function sendJSON(res, data, status = 200) {
    res.writeHead(status, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(data));
}

const { verifyToken } = require('./authUtils');

function getFamilyContext(req) {
    const cookies = getCookies(req);
    const token = cookies.app_auth;
    if (token) {
        const decoded = verifyToken(token);
        if (decoded) {
            return {
                familyId: decoded.familyId || null,
                childId: decoded.childId || null,
                role: decoded.role || null,
                email: decoded.email || null,
                csrfToken: cookies.csrf_token || decoded.csrfToken || null
            };
        }
    }
    return {
        familyId: null,
        childId: null,
        role: null,
        email: null,
        csrfToken: null
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
