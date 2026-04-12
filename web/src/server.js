const http = require('http');
const path = require('path');
const httpProxy = require('http-proxy');

require('dotenv').config({ path: path.join(__dirname, '../../.env') });

const compression = require('./middleware/compression');
const { setSecurityHeaders } = require('./middleware/security');
const { createLogger } = require('./utils/logger');
const { BACKEND_URL, fetchSessionSnapshot } = require('./sessionClient');
const { handlePageRoute, isStaticAssetPath, serveNotFound, viewController } = require('./rendering');

const logger = createLogger('webApp');
const PORT = Number(process.env.PORT || 3000);

const proxy = httpProxy.createProxyServer({
    changeOrigin: true,
    ws: true,
    xfwd: true,
    ignorePath: false,
    preserveHeaderKeyCase: true,
    secure: false
});

function writeJson(res, payload, status = 200) {
    res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify(payload));
}

function isBackendProxyRoute(pathname) {
    return pathname === '/api' || pathname.startsWith('/api/') || pathname.startsWith('/login-child/');
}

function isWebSocketRoute(pathname) {
    return pathname === '/ws' || pathname.startsWith('/ws?');
}

function proxyRequest(target, req, res) {
    proxy.web(req, res, { target });
}

async function handleNonPageRequest(pathname, req, res) {
    if (pathname === '/healthz') {
        writeJson(res, {
            status: 'ok',
            service: 'web',
            backendUrl: BACKEND_URL
        });
        return true;
    }

    if (isBackendProxyRoute(pathname)) {
        proxyRequest(BACKEND_URL, req, res);
        return true;
    }

    if (isStaticAssetPath(pathname)) {
        await viewController.serveStatic(req, res);
        return true;
    }

    if (!['GET', 'HEAD'].includes(req.method)) {
        serveNotFound(req, res);
        return true;
    }

    return false;
}

proxy.on('error', (err, req, res) => {
    logger.error({ err: err.message, url: req?.url || 'unknown' }, 'Proxy request failed');

    if (res && !res.headersSent) {
        writeJson(res, { error: 'Upstream service unavailable' }, 502);
        return;
    }

    if (res && typeof res.end === 'function') {
        res.end();
    }
});

async function handleIncomingRequest(req, res) {
    try {
        setSecurityHeaders(req, res);

        const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
        const pathname = url.pathname;

        if (await handleNonPageRequest(pathname, req, res)) {
            return;
        }

        const session = await fetchSessionSnapshot(req);
        const handled = await handlePageRoute({ pathname, req, res, session });
        if (handled) {
            return;
        }

        serveNotFound(req, res);
    } catch (err) {
        logger.error({ err: err.message, url: req.url }, 'Unhandled web edge error');
        writeJson(res, { error: 'Internal server error' }, 500);
    }
}

const server = http.createServer((req, res) => {
    compression(req, res, () => {
        void handleIncomingRequest(req, res);
    });
});

server.on('upgrade', (req, socket, head) => {
    const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    if (!isWebSocketRoute(url.pathname)) {
        socket.destroy();
        return;
    }

    proxy.ws(req, socket, head, { target: BACKEND_URL });
});

server.listen(PORT, () => {
    logger.info({ port: PORT, backendUrl: BACKEND_URL }, 'Web edge service started');
});