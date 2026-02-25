/** @file Seo Templates REST controller helpers */
const fs = require('fs');
const path = require('path');
const { PUBLIC_BASE_URL, isProd } = require('../config');
const { getBuildVersion } = require('../utils/buildVersion');

const packageJsonPath = path.join(__dirname, '../../package.json');
const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, 'utf8'));
const APP_VERSION = packageJson.version;
const BUILD_VERSION = getBuildVersion();
const CLARITY_PROJECT_ID = (process.env.CLARITY_PROJECT_ID || '').trim();
const PUBLIC_TOP_NAV_PATH = path.join(__dirname, '../../views/components/public-top-nav.html');
let PUBLIC_TOP_NAV = '';

try {
    PUBLIC_TOP_NAV = fs.readFileSync(PUBLIC_TOP_NAV_PATH, 'utf8');
} catch (_) {
    PUBLIC_TOP_NAV = '';
}

function getRequestHost(req) {
    if (req) {
        const forwardedHost = req.headers['x-forwarded-host'];
        if (forwardedHost) {
            return forwardedHost.split(',')[0].trim().toLowerCase();
        }
        if (req.headers.host) {
            return req.headers.host.split(':')[0].toLowerCase();
        }
    }
    return PUBLIC_BASE_URL.replace(/^https?:\/\//i, '').split('/')[0].toLowerCase();
}

function isLocalHost(host) {
    return host === 'localhost' || host === '::1' || host.startsWith('127.');
}

function shouldIncludeClarityScript(req) {
    if (!isProd) return false;
    if (!CLARITY_PROJECT_ID || !/^[a-zA-Z0-9]+$/.test(CLARITY_PROJECT_ID)) {
        return false;
    }
    const host = getRequestHost(req);
    if (isLocalHost(host)) return false;
    return true;
}

function getClarityScript(req) {
    if (!shouldIncludeClarityScript(req)) {
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
const TEMPLATE_CONTENT_TYPES = new Set(['text/html', 'text/plain', 'application/xml']);

function normalizeContentType(contentType) {
    return (contentType || '').split(';')[0].trim().toLowerCase();
}

function isTemplatableType(contentType) {
    return TEMPLATE_CONTENT_TYPES.has(normalizeContentType(contentType));
}

function getProtocol(req) {
    if (!req) return 'http';
    const forwardedProto = req.headers['x-forwarded-proto'];
    if (forwardedProto) return forwardedProto.split(',')[0].trim();
    return req.socket && req.socket.encrypted ? 'https' : 'http';
}

function getCanonicalPath(req) {
    if (!req) return '/';
    const [pathOnly] = req.url.split('?');
    if (!pathOnly || pathOnly === '/index.html') return '/';
    return pathOnly.endsWith('/') ? pathOnly : pathOnly;
}

function getBaseUrl(req) {
    if (req && req.headers.host) {
        return `${getProtocol(req)}://${req.headers.host}`;
    }
    return PUBLIC_BASE_URL;
}

const DEFAULT_PAGE_TITLE = 'EarnIt Kids — Семейная система мотивации';
const DEFAULT_PAGE_DESCRIPTION = 'EarnIt Kids помогает семьям внедрять привычки и обучать детей ответственности через виртуальные монеты.';

function buildCanonicalUrl(req, baseUrl, pathOnly) {
    const cleanPath = pathOnly === '' ? '/' : pathOnly;
    return `${baseUrl}${cleanPath}`;
}

function buildSeoReplacements(req, seoData = {}) {
    const baseUrl = getBaseUrl(req);
    let canonicalPath = getCanonicalPath(req);
    if (!canonicalPath.startsWith('/')) canonicalPath = `/${canonicalPath}`;
    const canonicalUrl = buildCanonicalUrl(req, baseUrl, canonicalPath);
    const pageTitle = seoData.title || DEFAULT_PAGE_TITLE;
    const pageDescription = seoData.description || DEFAULT_PAGE_DESCRIPTION;
    const schemaData = {
        '@context': 'https://schema.org',
        '@type': 'WebPage',
        'name': pageTitle,
        'description': pageDescription,
        'url': canonicalUrl,
        ...(seoData.schema || {})
    };

    if (!schemaData.url) {
        schemaData.url = canonicalUrl;
    }

    const schemaJson = JSON.stringify(schemaData, null, 4);

    return {
        '{{BASE_URL}}': baseUrl,
        '{{PUBLIC_BASE_URL}}': PUBLIC_BASE_URL,
        '{{CANONICAL_URL}}': canonicalUrl,
        '{{OG_IMAGE_URL}}': `${baseUrl}/img/og-image.png`,
        '{{PAGE_TITLE}}': pageTitle,
        '{{PAGE_DESCRIPTION}}': pageDescription,
        '{{SCHEMA_JSON}}': schemaJson
    };
}

function applyCommonTemplateData(html, extraReplacements = {}, req = null) {
    let result = html
        .replace(/\{\{APP_VERSION\}\}/g, APP_VERSION)
        .replace(/\{\{BUILD_VERSION\}\}/g, BUILD_VERSION)
        .replace(/\{\{PUBLIC_TOP_NAV\}\}/g, PUBLIC_TOP_NAV)
        .replace(/\{\{CLARITY_SCRIPT\}\}/g, getClarityScript(req));

    Object.entries(extraReplacements).forEach(([key, value]) => {
        if (typeof value !== 'string') return;
        result = result.split(key).join(value);
    });

    return result;
}

module.exports = {
    applyCommonTemplateData,
    buildSeoReplacements,
    isTemplatableType
};
