const fs = require('fs');
const path = require('path');
const { PUBLIC_BASE_URL } = require('../config');
const { getBuildVersion } = require('../utils/buildVersion');

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

function buildSeoReplacements(req) {
    const baseUrl = getBaseUrl(req);
    let canonicalPath = getCanonicalPath(req);
    if (!canonicalPath.startsWith('/')) canonicalPath = `/${canonicalPath}`;
    const canonicalUrl = `${baseUrl}${canonicalPath}`;

    return {
        '{{BASE_URL}}': baseUrl,
        '{{PUBLIC_BASE_URL}}': PUBLIC_BASE_URL,
        '{{CANONICAL_URL}}': canonicalUrl,
        '{{OG_IMAGE_URL}}': `${baseUrl}/img/og-image.png`
    };
}

function applyCommonTemplateData(html, extraReplacements = {}) {
    let result = html
        .replace(/\{\{APP_VERSION\}\}/g, APP_VERSION)
        .replace(/\{\{BUILD_VERSION\}\}/g, BUILD_VERSION)
        .replace(/\{\{CLARITY_SCRIPT\}\}/g, CLARITY_SCRIPT);

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
