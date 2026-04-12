const fs = require('fs');
const path = require('path');
const viewController = require('./controllers/viewController');
const blogController = require('./controllers/blogController');
const { applyCommonTemplateData, buildSeoReplacements } = require('./controllers/seoTemplates');
const { getHtmlHeaders } = require('./controllers/staticUtils');

const VIEWS_DIR = path.join(__dirname, '../views');
const COMPONENTS_DIR = path.join(VIEWS_DIR, 'components');
const INDEX_COMPONENT_ORDER = [
    'head.html',
    'header.html',
    'nav.html',
    'main_start.html',
    'section_analytics.html',
    'section_tasks.html',
    'section_shop.html',
    'section_requests.html',
    'section_catalog.html',
    'section_history.html',
    'section_rules.html',
    'section_friends.html',
    'section_settings.html',
    'section_limits.html',
    'main_end.html',
    'modals.html',
    'scripts.html'
];

const LOGIN_SEO = {
    title: 'Вход | EarnIt Kids',
    description: 'Войдите в EarnIt Kids — систему мотивации для детей.',
    schema: {
        '@context': 'https://schema.org',
        '@type': 'WebPage',
        name: 'Вход в EarnIt Kids'
    }
};

const NOT_FOUND_SEO = {
    title: 'Страница не найдена | EarnIt Kids',
    description: 'Запрашиваемая страница не найдена.',
    schema: {
        '@context': 'https://schema.org',
        '@type': 'WebPage',
        name: '404 — EarnIt Kids'
    }
};

let cachedIndexHtml = null;

function getNoStoreHtmlHeaders(req) {
    return {
        ...getHtmlHeaders(req),
        'Cache-Control': 'no-store, no-cache, must-revalidate, proxy-revalidate',
        Pragma: 'no-cache',
        Expires: '0'
    };
}

function isStaticAssetPath(pathname) {
    if (!pathname) return false;

    const staticFiles = new Set([
        '/favicon.ico',
        '/manifest.json',
        '/robots.txt',
        '/sitemap.xml',
        '/sw.js',
        '/googlefe5d665f9448c9e2.html'
    ]);

    if (staticFiles.has(pathname)) return true;
    if (pathname.startsWith('/css/')) return true;
    if (pathname.startsWith('/js/')) return true;
    if (pathname.startsWith('/img/')) return true;
    if (pathname.startsWith('/.well-known/')) return true;

    const extension = path.extname(pathname);
    return Boolean(extension) && extension !== '.html';
}

function readTemplate(viewName) {
    return fs.readFileSync(path.join(VIEWS_DIR, viewName), 'utf8');
}

function renderHtml({ viewName, req, res, seoData = {}, extraReplacements = {}, status = 200, headers }) {
    const template = readTemplate(viewName);
    const html = applyCommonTemplateData(template, {
        ...buildSeoReplacements(req, seoData),
        ...extraReplacements
    }, req);

    res.writeHead(status, headers || getHtmlHeaders(req));
    res.end(html);
}

function assembleIndexHtml() {
    return INDEX_COMPONENT_ORDER
        .map(fileName => fs.readFileSync(path.join(COMPONENTS_DIR, fileName), 'utf8'))
        .join('\n');
}

function serveIndexShell(req, res) {
    const template = process.env.NODE_ENV === 'production' && cachedIndexHtml
        ? cachedIndexHtml
        : assembleIndexHtml();

    if (process.env.NODE_ENV === 'production' && !cachedIndexHtml) {
        cachedIndexHtml = template;
    }

    const html = applyCommonTemplateData(template, buildSeoReplacements(req), req);
    res.writeHead(200, getNoStoreHtmlHeaders(req));
    res.end(html);
}

function serveLogin(req, res) {
    renderHtml({
        viewName: 'login.html',
        req,
        res,
        seoData: LOGIN_SEO,
        headers: getNoStoreHtmlHeaders(req)
    });
}

function serveNotFound(req, res) {
    renderHtml({
        viewName: '404.html',
        req,
        res,
        seoData: NOT_FOUND_SEO,
        headers: getNoStoreHtmlHeaders(req),
        status: 404
    });
}

function redirect(res, location, headers = {}) {
    res.writeHead(302, { Location: location, ...headers });
    res.end();
}

async function handleRootRoute({ req, res, session }) {
    if (!session.authenticated) {
        await viewController.serveLanding(req, res);
        return true;
    }

    if (session.role === 'super_admin') {
        await viewController.serveSuperAdmin(req, res);
        return true;
    }

    serveIndexShell(req, res);
    return true;
}

function handleLoginRoute({ req, res, session }) {
    if (session.authenticated) {
        redirect(res, '/', getNoStoreHtmlHeaders(req));
        return true;
    }

    serveLogin(req, res);
    return true;
}

async function handleSuperAdminRoute({ req, res, session }) {
    if (session.role === 'super_admin') {
        await viewController.serveSuperAdmin(req, res);
    } else {
        redirect(res, '/login.html', getNoStoreHtmlHeaders(req));
    }
    return true;
}

async function handleFeatureRoute({ pathname, req, res }) {
    if (pathname === '/features' || pathname === '/features/') {
        redirect(res, '/features/tasks');
        return true;
    }

    if (pathname.startsWith('/features/') && pathname.length > '/features/'.length) {
        const slug = pathname.replace('/features/', '').replace(/\/+$/, '');
        await viewController.serveFeaturePage(req, res, slug);
        return true;
    }

    return false;
}

async function handleInfoRoute({ pathname, req, res }) {
    if (pathname === '/about') {
        await viewController.serveAbout(req, res);
        return true;
    }

    if (pathname === '/faq') {
        await viewController.serveFaq(req, res);
        return true;
    }

    if (pathname === '/reset-password') {
        await viewController.serveResetPassword(req, res);
        return true;
    }

    if (pathname === '/verify') {
        await viewController.serveVerify(req, res);
        return true;
    }

    return false;
}

async function handleBlogRoute({ pathname, req, res }) {
    if (pathname === '/blog') {
        await blogController.serveBlogIndex(req, res);
        return true;
    }

    if (pathname.startsWith('/blog/') && pathname.length > '/blog/'.length) {
        const slug = pathname.replace('/blog/', '').replace(/\/+$/, '');
        await blogController.serveArticle(req, res, slug);
        return true;
    }

    return false;
}

async function handlePageRoute({ pathname, req, res, session }) {
    if (pathname === '/' || pathname === '/index.html') {
        return handleRootRoute({ req, res, session });
    }

    if (pathname === '/login.html') {
        return handleLoginRoute({ req, res, session });
    }

    if (pathname === '/super-admin' || pathname === '/super-admin.html') {
        return handleSuperAdminRoute({ req, res, session });
    }

    if (await handleFeatureRoute({ pathname, req, res })) {
        return true;
    }

    if (await handleInfoRoute({ pathname, req, res })) {
        return true;
    }

    if (await handleBlogRoute({ pathname, req, res })) {
        return true;
    }

    return false;
}

module.exports = {
    handlePageRoute,
    isStaticAssetPath,
    serveNotFound,
    viewController
};