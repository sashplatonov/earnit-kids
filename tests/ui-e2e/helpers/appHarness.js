const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');

const repoRoot = path.resolve(__dirname, '../../..');
const viewsDir = path.join(repoRoot, 'views');
const componentsDir = path.join(viewsDir, 'components');
const publicDir = path.join(repoRoot, 'public');

const COMPONENT_ORDER = [
    'head.html', 'header.html', 'nav.html', 'main_start.html',
    'section_today.html', 'section_tasks.html', 'section_shop.html', 'section_progress.html',
    'section_requests.html', 'section_catalog.html', 'section_analytics.html', 'section_history.html',
    'section_rules.html', 'section_friends.html', 'section_settings.html', 'section_limits.html',
    'main_end.html', 'modals.html', 'scripts.html'
];

const FEATURE_CONTENT = {
    tasks: {
        title: 'EarnIt Kids - Добрые семейные задания',
        heading: 'Задания, которые хочется выполнять',
        subheading: 'Ребенок видит понятную цель, а родители спокойно следят за прогрессом.',
        description: 'Простые задания для детей, понятные шаги для родителей и честные монетки за старание.',
        image: '/img/feature-tasks.svg',
        ctaText: 'Попробовать задания'
    },
    shop: {
        title: 'EarnIt Kids - Семейный магазин наград',
        heading: 'Магазин радостей за монетки',
        subheading: 'Дети учатся копить и выбирать, родители сохраняют контроль и бюджет.',
        description: 'Обменивайте монетки на радости: мультик, прогулка, настольная игра или маленький приз.',
        image: '/img/feature-shop.svg',
        ctaText: 'Открыть магазин наград'
    }
};

function readFile(filePath) {
    return fs.readFileSync(filePath, 'utf8');
}

function normalizeTemplate(html) {
    return html
        .replace(/\{\{APP_VERSION\}\}/g, '1.0.0-test')
        .replace(/\{\{BUILD_VERSION\}\}/g, 'test-build')
        .replace(/\{\{CLARITY_SCRIPT\}\}/g, '')
        .replace(/\{\{SCHEMA_JSON\}\}/g, '{}')
        .replace(/\{\{PAGE_TITLE\}\}/g, 'EarnIt Kids Test')
        .replace(/\{\{PAGE_DESCRIPTION\}\}/g, 'E2E test page')
        .replace(/\{\{CANONICAL_URL\}\}/g, 'http://localhost/test')
        .replace(/\{\{OG_IMAGE_URL\}\}/g, '/img/feature-shop.svg');
}

function withPublicTopNav(html) {
    const nav = readFile(path.join(componentsDir, 'public-top-nav.html'));
    return html.replace(/\{\{PUBLIC_TOP_NAV\}\}/g, nav);
}

function buildFeaturePage(slug) {
    const feature = FEATURE_CONTENT[slug];
    if (!feature) return null;

    let html = readFile(path.join(viewsDir, 'feature-page.html'));
    html = normalizeTemplate(withPublicTopNav(html));

    return html
        .replace(/\{\{PAGE_TITLE\}\}/g, feature.title)
        .replace(/\{\{FEATURE_HEADING\}\}/g, feature.heading)
        .replace(/\{\{FEATURE_SUBTITLE\}\}/g, feature.subheading)
        .replace(/\{\{FEATURE_DESCRIPTION\}\}/g, feature.description)
        .replace(/\{\{FEATURE_IMAGE\}\}/g, feature.image)
        .replace(/\{\{FEATURE_PILL\}\}/g, feature.ctaText)
        .replace(/\{\{FEATURE_LINK\}\}/g, '/login.html')
        .replace(/\{\{FEATURE_BULLETS\}\}/g, '<ul><li>Тестовый пункт 1</li><li>Тестовый пункт 2</li></ul>');
}

function buildBlogPage() {
    let html = readFile(path.join(viewsDir, 'blog-index.html'));
    html = normalizeTemplate(withPublicTopNav(html));
    return html.replace(
        /\{\{BLOG_LIST\}\}/g,
        '<article class="blog-card"><a href="/blog/test"><h2>Тестовая статья</h2><p>Описание</p></a></article>'
    );
}

function buildAppHtml() {
    const parts = COMPONENT_ORDER.map(fileName => readFile(path.join(componentsDir, fileName)));
    return normalizeTemplate(parts.join('\n'));
}

function getContentType(filePath) {
    const ext = path.extname(filePath).toLowerCase();
    if (ext === '.html') return 'text/html; charset=utf-8';
    if (ext === '.css') return 'text/css; charset=utf-8';
    if (ext === '.js') return 'application/javascript; charset=utf-8';
    if (ext === '.json') return 'application/json; charset=utf-8';
    if (ext === '.svg') return 'image/svg+xml';
    if (ext === '.png') return 'image/png';
    if (ext === '.ico') return 'image/x-icon';
    if (ext === '.xml') return 'application/xml; charset=utf-8';
    return 'text/plain; charset=utf-8';
}

function safeResolve(baseDir, requestPath) {
    const decoded = decodeURIComponent(requestPath.split('?')[0]);
    const normalized = path.normalize(decoded).replace(/^([.]{2}[\/])+/, '');
    const full = path.join(baseDir, normalized);
    if (!full.startsWith(baseDir)) return null;
    return full;
}

function serveFile(res, filePath) {
    if (!fs.existsSync(filePath) || !fs.statSync(filePath).isFile()) {
        res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
        res.end('Not Found');
        return;
    }

    res.writeHead(200, { 'Content-Type': getContentType(filePath) });
    fs.createReadStream(filePath).pipe(res);
}

function sendHtml(res, html, status = 200) {
    res.writeHead(status, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(html);
}

function handlePublicPage(urlPath, res) {
    if (urlPath === '/') {
        sendHtml(res, normalizeTemplate(withPublicTopNav(readFile(path.join(viewsDir, 'landing.html')))));
        return true;
    }

    if (urlPath === '/about' || urlPath === '/faq' || urlPath === '/login.html') {
        const fileName = urlPath === '/about' ? 'about.html' : (urlPath === '/faq' ? 'faq.html' : 'login.html');
        sendHtml(res, normalizeTemplate(withPublicTopNav(readFile(path.join(viewsDir, fileName)))));
        return true;
    }

    if (urlPath === '/blog') {
        sendHtml(res, buildBlogPage());
        return true;
    }

    return false;
}

function handleFeaturePage(urlPath, res) {
    if (!urlPath.startsWith('/features/')) return false;
    const slug = urlPath.replace('/features/', '').replace(/\/+$/, '');
    const pageHtml = buildFeaturePage(slug);
    if (pageHtml) {
        sendHtml(res, pageHtml);
        return true;
    }

    sendHtml(res, normalizeTemplate(readFile(path.join(viewsDir, '404.html'))), 404);
    return true;
}

function handleAppPage(urlPath, res) {
    if (urlPath !== '/app') return false;
    sendHtml(res, buildAppHtml());
    return true;
}

function handleAssets(urlPath, res) {
    const isAssetPath = urlPath.startsWith('/css/') || urlPath.startsWith('/js/') ||
        urlPath.startsWith('/img/') || urlPath === '/manifest.json' || urlPath === '/sw.js';
    if (!isAssetPath) return false;

    const filePath = safeResolve(publicDir, urlPath);
    if (!filePath) {
        res.writeHead(403, { 'Content-Type': 'text/plain; charset=utf-8' });
        res.end('Forbidden');
        return true;
    }

    serveFile(res, filePath);
    return true;
}

function createServer() {
    return http.createServer((req, res) => {
        const urlPath = req.url.split('?')[0];

        if (handleAppPage(urlPath, res)) return;
        if (handlePublicPage(urlPath, res)) return;
        if (handleFeaturePage(urlPath, res)) return;
        if (handleAssets(urlPath, res)) return;

        res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
        res.end('Not Found');
    });
}

async function startAppHarness() {
    const server = createServer();
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    const address = server.address();
    const baseUrl = `http://127.0.0.1:${address.port}`;

    return {
        baseUrl,
        stop: async () => {
            await new Promise(resolve => server.close(resolve));
        }
    };
}

async function waitForDataReady(page) {
    await page.waitForSelector('.nav:not(.nav--pending)');
    await page.waitForSelector('#balance');
}

async function gotoAppAsAdmin(page, baseUrl) {
    await page.goto(`${baseUrl}/app`);
    await waitForDataReady(page);
}

async function gotoAppAsChild(page, baseUrl) {
    await page.goto(`${baseUrl}/app`);
    await waitForDataReady(page);
}

async function openTab(page, tabName) {
    const topButton = page.locator(`.nav__btn[data-tab="${tabName}"]`).first();
    if (await topButton.isVisible().catch(() => false)) {
        await topButton.click();
        return;
    }

    await page.click('#nav-more-btn');
    await page.click(`.nav__dropdown-item[data-tab="${tabName}"]`);
}

module.exports = {
    startAppHarness,
    gotoAppAsAdmin,
    gotoAppAsChild,
    openTab,
    waitForDataReady
};
