/** @file View Controller REST controller helpers */
const fs = require('fs');
const path = require('path');
const { applyCommonTemplateData, buildSeoReplacements } = require('./seoTemplates');
const {
    getHtmlHeaders,
    normalizeStaticPath,
    resolvePublicFilePath,
    sendStaticFile,
    tryServeDistOverride,
    setServeNotFoundHandler
} = require('./staticUtils');
const { findFamilyByEmail } = require('../services/familyService');

function getCookies(req) {
    const list = {};
    const rc = req.headers.cookie;
    rc && rc.split(';').forEach((cookie) => {
        const parts = cookie.split('=');
        list[parts.shift().trim()] = decodeURI(parts.join('='));
    });
    return list;
}

const { verifyToken } = require('../utils/authUtils');
const { createLogger } = require('../utils/logger');
const logger = createLogger('viewController');

/**
 * Verify if user session is valid
 */
async function verifyUserSession(cookies) {
    const { app_auth, app_role, family_id } = cookies;
    if (!app_auth) return false;

    const decoded = verifyToken(app_auth);
    if (!decoded || !decoded.email) return false;

    const user = await findFamilyByEmail(decoded.email);
    if (!user) return false;

    return isValidSessionScope({
        user,
        sessionRole: decoded.role || app_role,
        sessionFamilyId: decoded.familyId || family_id
    });
}

function isValidSessionScope({ user, sessionRole, sessionFamilyId }) {
    if (user.isSuperAdmin && sessionRole === 'super_admin') return true;
    if (!sessionFamilyId || user.id !== sessionFamilyId) return false;
    return sessionRole === 'admin' || sessionRole === 'child';
}

/**
 * Check if request is authenticated
 */
async function isAuthenticated(req) {
    const cookies = getCookies(req);
    return await verifyUserSession(cookies);
}

const crypto = require('crypto');

async function serveStatic(req, res) {
    const rawUrl = req.url.split('?')[0];
    const urlPath = normalizeStaticPath(rawUrl);
    if (tryServeDistOverride(rawUrl, req, res)) return;

    const resolvedPath = resolvePublicFilePath(urlPath);
    if (!resolvedPath) {
        res.writeHead(403);
        res.end('Forbidden');
        return;
    }

    sendStaticFile({ filePath: resolvedPath, req, res });
}

async function serveLogin(req, res) {
    if (await isAuthenticated(req)) {
        res.writeHead(302, { Location: '/' });
        res.end();
        return;
    }

    const loginPath = path.join(__dirname, '../../views', 'login.html');
    fs.readFile(loginPath, 'utf8', (err, content) => {
        if (err) {
            res.writeHead(500);
            res.end('Server Error');
            return;
        }
        content = applyCommonTemplateData(content, buildSeoReplacements(req), req);
        res.writeHead(200, getHtmlHeaders(req));
        res.end(content);
    });
}

function serveSuperAdmin(req, res) {
    const superAdminPath = path.join(__dirname, '../../views', 'super-admin.html');
    fs.readFile(superAdminPath, 'utf8', (err, content) => {
        if (err) {
            res.writeHead(500);
            res.end('Server Error');
            return;
        }
        content = applyCommonTemplateData(content, buildSeoReplacements(req), req);
        res.writeHead(200, getHtmlHeaders(req));
        res.end(content);
    });
}

let cachedIndexHtml = null;

/**
 * Assemble full HTML from components
 */
function assembleIndexHtml() {
    const componentOrder = [
        'head.html', 'header.html', 'nav.html', 'main_start.html',
        'section_tasks.html', 'section_requests.html', 'section_shop.html',
        'section_catalog.html', 'section_analytics.html', 'section_about.html', 'section_history.html',
        'section_rules.html', 'section_friends.html', 'section_settings.html', 'main_end.html',
        'modals.html', 'scripts.html'
    ];

    const componentsDir = path.join(__dirname, '../../views/components');
    let fullHtml = '';

    componentOrder.forEach(file => {
        fullHtml += fs.readFileSync(path.join(componentsDir, file), 'utf8') + '\n';
    });

    return fullHtml;
}

/**
 * Serve the main application index
 */
async function serveIndex(req, res) {
    if (!(await isAuthenticated(req))) return serveLogin(req, res);

    const cookies = getCookies(req);
    const decoded = cookies.app_auth ? verifyToken(cookies.app_auth) : null;
    const role = decoded?.role || cookies.app_role;
    if (role === 'super_admin') return serveSuperAdmin(req, res);

    try {
        let template = cachedIndexHtml;
        if (!template || process.env.NODE_ENV !== 'production') {
            template = assembleIndexHtml();
            if (process.env.NODE_ENV === 'production') cachedIndexHtml = template;
        }
        const finalHtml = applyCommonTemplateData(template, buildSeoReplacements(req), req);

        res.writeHead(200, getHtmlHeaders(req));
        res.end(finalHtml);
    } catch (err) {
        logger.error({ err: err.message }, 'Index assembly failed');
        res.writeHead(500);
        res.end('Server Error: Index assembly failed');
    }
}

function serveResetPassword(req, res) {
    const resetPath = path.join(__dirname, '../../views', 'reset-password.html');
    fs.readFile(resetPath, 'utf8', (err, content) => {
        if (err) {
            res.writeHead(500);
            res.end('Server Error');
            return;
        }
        content = applyCommonTemplateData(content, buildSeoReplacements(req), req);
        res.writeHead(200, getHtmlHeaders(req));
        res.end(content);
    });
}

function serveVerify(req, res) {
    const verifyPath = path.join(__dirname, '../../views', 'verify.html');
    fs.readFile(verifyPath, 'utf8', (err, content) => {
        if (err) {
            res.writeHead(500);
            res.end('Server Error');
            return;
        }
        content = applyCommonTemplateData(content, buildSeoReplacements(req), req);
        res.writeHead(200, getHtmlHeaders(req));
        res.end(content);
    });
}

function serveNotFound(req, res) {
    const notFoundPath = path.join(__dirname, '../../views', '404.html');
    fs.readFile(notFoundPath, 'utf8', (err, content) => {
        if (err) {
            res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
            res.end('Page not found');
            return;
        }
        const finalContent = applyCommonTemplateData(content, buildSeoReplacements(req), req);
        res.writeHead(404, getHtmlHeaders(req));
        res.end(finalContent);
    });
}

setServeNotFoundHandler(serveNotFound);

module.exports = { serveStatic, serveIndex, serveLogin, serveSuperAdmin, serveResetPassword, serveVerify, getCookies };
