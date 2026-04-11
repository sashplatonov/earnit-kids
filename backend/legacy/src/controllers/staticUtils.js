/** @file Static Utils REST controller helpers */
const fs = require('fs');
const path = require('path');
const { MIME_TYPES } = require('../config');
const { applyCommonTemplateData, buildSeoReplacements, isTemplatableType } = require('./seoTemplates');

const STYLE_PARTIALS = [
    'tokens.css',
    'reset.css',
    'layout.css',
    'components.css',
    'animations.css',
    'responsive.css'
];

let serveNotFoundHandler = null;

function setServeNotFoundHandler(handler) {
    serveNotFoundHandler = handler;
}

function handleNotFound(req, res) {
    if (serveNotFoundHandler) return serveNotFoundHandler(req, res);
    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('Not Found');
}

function assembleStyleCss() {
    const partialsDir = path.join(__dirname, '../../public/css/partials');
    return STYLE_PARTIALS.map(file => fs.readFileSync(path.join(partialsDir, file), 'utf8')).join('\n\n');
}

function getDistOverride(urlPath) {
    const overrides = {
        '/style.css': '/css/style.css',
        '/css/style.css': '/css/style.css',
        '/super-admin.css': '/css/super-admin.css',
        '/css/super-admin.css': '/css/super-admin.css'
    };
    return overrides[urlPath];
}

function isLocalRequest(req) {
    const hostHeader = (req.headers.host || '').split(':')[0];
    return hostHeader === 'localhost' || hostHeader === '127.0.0.1' || hostHeader === '::1';
}

function getNoStoreHeaders(contentType) {
    return {
        'Content-Type': contentType,
        'Cache-Control': 'no-store, no-cache, must-revalidate, proxy-revalidate',
        'Pragma': 'no-cache',
        'Expires': '0'
    };
}

function getHtmlHeaders(req) {
    const isProd = process.env.NODE_ENV === 'production';
    if (!isProd || isLocalRequest(req)) return getNoStoreHeaders('text/html; charset=utf-8');
    return { 'Content-Type': 'text/html; charset=utf-8' };
}

function shouldDisableLongCache(reqPath) {
    return reqPath === '/sw.js' || reqPath === '/manifest.json';
}

function writeStaticResponse({ req, res, stats, requestPath, contentType, responseContent }) {
    const isProd = process.env.NODE_ENV === 'production';
    const disableBrowserCache = !isProd || isLocalRequest(req);
    const isShortLivedStatic = shouldDisableLongCache(requestPath);
    const disableLongCache = disableBrowserCache || isShortLivedStatic;
    const cacheControl = disableBrowserCache
        ? 'no-store, no-cache, must-revalidate, proxy-revalidate'
        : (isShortLivedStatic ? 'no-cache, max-age=0, must-revalidate' : 'public, max-age=31536000');
    const etag = `W/"${responseContent.length}-${stats.mtime.getTime()}"`;

    if (!disableLongCache && req.headers['if-none-match'] === etag) {
        res.writeHead(304);
        res.end();
        return;
    }

    const headers = {
        'Content-Type': contentType,
        'Cache-Control': cacheControl,
        'ETag': etag
    };
    if (disableLongCache) {
        headers.Pragma = 'no-cache';
        headers.Expires = '0';
    }
    res.writeHead(200, headers);
    res.end(responseContent);
}

function prepareStaticContent({ content, req, contentType, shouldInlineStyle, inlineStyle }) {
    let responseContent = shouldInlineStyle ? Buffer.from(assembleStyleCss(), 'utf8') : content;
    if (!inlineStyle && isTemplatableType(contentType)) {
        const processed = applyCommonTemplateData(responseContent.toString('utf8'), buildSeoReplacements(req), req);
        responseContent = Buffer.from(processed, 'utf8');
    }
    return responseContent;
}

function sendStaticFile({ filePath, req, res, inlineStyle = false }) {
    const contentType = MIME_TYPES[path.extname(filePath)] || 'application/octet-stream';
    const requestPath = normalizeStaticPath(req.url.split('?')[0]);
    const shouldInlineStyle = inlineStyle || requestPath === '/css/style.css';
    fs.stat(filePath, (err, stats) => {
        if (err) {
            if (err.code === 'ENOENT') return handleNotFound(req, res);
            res.writeHead(500);
            return res.end('Server Error');
        }
        function handleFileRead(err, content) {
            if (err) {
                if (err.code === 'ENOENT') return handleNotFound(req, res);
                res.writeHead(500);
                return res.end('Server Error');
            }
            const responseContent = prepareStaticContent({ content, req, contentType, shouldInlineStyle, inlineStyle });
            writeStaticResponse({ req, res, stats, requestPath, contentType, responseContent });
        }
        fs.readFile(filePath, handleFileRead);
    });
}

function normalizeStaticPath(rawUrl) {
    const cleaned = rawUrl.split('?')[0];
    if (cleaned === '/style.css' || cleaned === '/features/css/style.css') return '/css/style.css';
    if (cleaned === '/super-admin.css') return '/css/super-admin.css';
    if (
        cleaned === '/css/public-pages.css' ||
        cleaned === 'css/public-pages.css' ||
        cleaned === '/features/css/public-pages.css'
    ) {
        return '/css/partials/public-pages.css';
    }
    return cleaned;
}

function resolvePublicFilePath(urlPath) {
    const safeUrlPath = (urlPath || '')
        .split('?')[0]
        .replace(/^\/+/, '');

    let baseDir = '../../public';
    if (process.env.NODE_ENV === 'production') {
        const distPath = path.join(__dirname, '../../public/dist', safeUrlPath);
        if (fs.existsSync(distPath)) {
            baseDir = '../../public/dist';
        }
    }

    const filePath = path.join(__dirname, baseDir, safeUrlPath);
    const publicDir = path.resolve(__dirname, '../../public');
    const resolvedPath = path.resolve(filePath);
    return resolvedPath.startsWith(publicDir) ? resolvedPath : null;
}

function tryServeDistOverride(rawUrl, req, res) {
    const distOverride = getDistOverride(rawUrl);
    if (!distOverride) return false;
    const distPath = path.join(__dirname, '../../public/dist', distOverride);
    if (!fs.existsSync(distPath)) return false;
    const inline = distOverride === '/css/style.css';
    sendStaticFile({ filePath: distPath, req, res, inlineStyle: inline });
    return true;
}

module.exports = {
    getHtmlHeaders,
    normalizeStaticPath,
    resolvePublicFilePath,
    sendStaticFile,
    tryServeDistOverride,
    setServeNotFoundHandler
};
