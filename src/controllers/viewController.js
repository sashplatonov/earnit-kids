const fs = require('fs');
const path = require('path');
const { MIME_TYPES } = require('../config');
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

function isAuthenticated(req) {
    const cookies = getCookies(req);
    const { family_id, app_auth, app_role } = cookies;
    if (!app_auth) return false;

    const user = findFamilyByEmail(app_auth);
    if (!user) return false;

    if (user.isSuperAdmin && app_role === 'super_admin') return true;
    if (family_id && user.id === family_id) {
        return app_role === 'admin' || app_role === 'child';
    }
    return false;
}

function serveStatic(req, res) {
    let urlPath = req.url;
    // Map root style.css etc to public/css/
    if (urlPath === '/style.css') urlPath = '/css/style.css';
    if (urlPath === '/super-admin.css') urlPath = '/css/super-admin.css';

    // All static assets are in public/
    let filePath = path.join(__dirname, '../../public', urlPath);

    // Prevent directory traversal
    const publicDir = path.resolve(__dirname, '../../public');
    const resolvedPath = path.resolve(filePath);
    if (!resolvedPath.startsWith(publicDir)) {
        res.writeHead(403);
        res.end('Forbidden');
        return;
    }

    const ext = path.extname(resolvedPath);
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';

    fs.readFile(resolvedPath, (err, content) => {
        if (err) {
            res.writeHead(err.code === 'ENOENT' ? 404 : 500);
            res.end(err.code === 'ENOENT' ? 'Not Found' : 'Server Error');
        } else {
            res.writeHead(200, { 'Content-Type': contentType });
            res.end(content);
        }
    });
}

function serveLogin(req, res) {
    const loginPath = path.join(__dirname, '../../views', 'login.html');
    fs.readFile(loginPath, 'utf8', (err, content) => {
        if (err) {
            res.writeHead(500);
            res.end('Server Error');
            return;
        }
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
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
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end(content);
    });
}

function serveIndex(req, res) {
    if (!isAuthenticated(req)) {
        return serveLogin(req, res);
    }

    const cookies = getCookies(req);
    if (cookies.app_role === 'super_admin') {
        return serveSuperAdmin(req, res);
    }

    const componentOrder = [
        'head.html', 'header.html', 'nav.html', 'main_start.html',
        'section_tasks.html', 'section_requests.html', 'section_shop.html',
        'section_catalog.html', 'section_about.html', 'section_history.html',
        'section_rules.html', 'section_friends.html', 'section_settings.html', 'main_end.html',
        'modals.html', 'scripts.html'
    ];

    const componentsDir = path.join(__dirname, '../../views/components');
    let fullHtml = '';

    try {
        componentOrder.forEach(file => {
            fullHtml += fs.readFileSync(path.join(componentsDir, file), 'utf8') + '\n';
        });
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end(fullHtml);
    } catch (err) {
        console.error('Error assembling index:', err.message);
        res.writeHead(500);
        res.end('Server Error: Could not assemble index.html');
    }
}

module.exports = { serveStatic, serveIndex, serveLogin, serveSuperAdmin, getCookies };
