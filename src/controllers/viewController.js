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

async function isAuthenticated(req) {
    const cookies = getCookies(req);
    const { family_id, app_auth, app_role } = cookies;
    if (!app_auth) {
        if (req.url === '/' || req.url === '/index.html') {
            console.log('🔍 Authenticating: No app_auth cookie found');
        }
        return false;
    }

    const user = await findFamilyByEmail(app_auth);
    if (!user) {
        console.log(`🔍 Authenticating: User not found in DB for email: ${app_auth}`);
        return false;
    }

    if (user.isSuperAdmin && app_role === 'super_admin') {
        return true;
    }

    if (family_id && user.id === family_id) {
        return app_role === 'admin' || app_role === 'child';
    }

    console.log(`🔍 Authenticating: Failed for email: ${app_auth}, role: ${app_role}, familyId: ${family_id}`);
    return false;
}

function serveStatic(req, res) {
    let urlPath = req.url.split('?')[0];
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

async function serveIndex(req, res) {
    if (!(await isAuthenticated(req))) {
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

        fullHtml = applyCommonTemplateData(fullHtml);

        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end(fullHtml);
    } catch (err) {
        console.error('Error assembling index:', err.message);
        res.writeHead(500);
        res.end('Server Error: Could not assemble index.html');
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
