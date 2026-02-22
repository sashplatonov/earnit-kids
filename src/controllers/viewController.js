const fs = require('fs');
const path = require('path');
const { MIME_TYPES } = require('../config');
const { findFamilyByEmail } = require('../services/familyService');
const { getBuildVersion } = require('../utils/buildVersion');

// Read package.json to get app version
const packageJsonPath = path.join(__dirname, '../../package.json');
const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, 'utf8'));
const APP_VERSION = packageJson.version;
const BUILD_VERSION = getBuildVersion();
const CLARITY_PROJECT_ID = (process.env.CLARITY_PROJECT_ID || '').trim();

function getClarityScript() {
    if (!CLARITY_PROJECT_ID || !/^[a-zA-Z0-9]+$/.test(CLARITY_PROJECT_ID)) {
        return '';
    }

    return `<script type="text/javascript">
(function(c,l,a,r,i,t,y){
    c[a]=c[a]||function(){(c[a].q=c[a].q||[]).push(arguments)};
    t=l.createElement(r);t.async=1;t.src="https://www.clarity.ms/tag/"+i;
    y=l.getElementsByTagName(r)[0];y.parentNode.insertBefore(t,y);
})(window, document, "clarity", "script", "${CLARITY_PROJECT_ID}");
</script>`;
}

const CLARITY_SCRIPT = getClarityScript();

function applyCommonTemplateData(html) {
    return html
        .replace(/\{\{APP_VERSION\}\}/g, APP_VERSION)
        .replace(/\{\{BUILD_VERSION\}\}/g, BUILD_VERSION)
        .replace(/\{\{CLARITY_SCRIPT\}\}/g, CLARITY_SCRIPT);
}

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

    if (user.isSuperAdmin && app_role === 'super_admin') return true;
    if (family_id && user.id === family_id) {
        return app_role === 'admin' || app_role === 'child';
    }
    return false;
}

/**
 * Check if request is authenticated
 */
async function isAuthenticated(req) {
    const cookies = getCookies(req);
    const authenticated = await verifyUserSession(cookies);
    if (!authenticated && (req.url === '/' || req.url === '/index.html')) {
        console.log('🔍 Authentication failed for index request');
    }
    return authenticated;
}

const crypto = require('crypto');

async function serveStatic(req, res) {
    let urlPath = req.url.split('?')[0];
    // Map root style.css etc to public/css/
    if (urlPath === '/style.css') urlPath = '/css/style.css';
    if (urlPath === '/super-admin.css') urlPath = '/css/super-admin.css';

    // All static assets are in public/
    let baseDir = '../../public';
    const isProd = process.env.NODE_ENV === 'production';

    // If in production, try to serve from dist first
    if (isProd) {
        const distPath = path.join(__dirname, '../../public/dist', urlPath);
        if (fs.existsSync(distPath)) {
            baseDir = '../../public/dist';
        }
    }

    let filePath = path.join(__dirname, baseDir, urlPath);

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

    fs.stat(resolvedPath, (err, stats) => {
        if (err) {
            res.writeHead(err.code === 'ENOENT' ? 404 : 500);
            res.end(err.code === 'ENOENT' ? 'Not Found' : 'Server Error');
            return;
        }

        const etag = `W/"${stats.size}-${stats.mtime.getTime()}"`;
        if (req.headers['if-none-match'] === etag) {
            res.writeHead(304);
            return res.end();
        }

        fs.readFile(resolvedPath, (err, content) => {
            if (err) {
                res.writeHead(500);
                res.end('Server Error');
                return;
            }

            const isProd = process.env.NODE_ENV === 'production';
            const cacheControl = isProd ? 'public, max-age=31536000' : 'no-cache';

            res.writeHead(200, {
                'Content-Type': contentType,
                'Cache-Control': cacheControl,
                'ETag': etag
            });
            res.end(content);
        });
    });
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
        content = applyCommonTemplateData(content);
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
        content = applyCommonTemplateData(content);
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
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

    return applyCommonTemplateData(fullHtml);
}

/**
 * Serve the main application index
 */
async function serveIndex(req, res) {
    if (!(await isAuthenticated(req))) return serveLogin(req, res);

    const cookies = getCookies(req);
    if (cookies.app_role === 'super_admin') return serveSuperAdmin(req, res);

    if (cachedIndexHtml && process.env.NODE_ENV === 'production') {
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        return res.end(cachedIndexHtml);
    }

    try {
        const fullHtml = assembleIndexHtml();
        if (process.env.NODE_ENV === 'production') cachedIndexHtml = fullHtml;

        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end(fullHtml);
    } catch (err) {
        console.error('Error assembling index:', err.message);
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
        content = applyCommonTemplateData(content);
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
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
        content = applyCommonTemplateData(content);
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end(content);
    });
}

module.exports = { serveStatic, serveIndex, serveLogin, serveSuperAdmin, serveResetPassword, serveVerify, getCookies };
