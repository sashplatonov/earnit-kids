import { createServer } from 'node:http';
import process from 'node:process';
import { gzip } from 'node:zlib';
import httpProxy from 'http-proxy';
import { handler } from '../build/handler.js';

function applyCliOverrides(argv) {
    for (let index = 0; index < argv.length; index += 1) {
        const arg = argv[index];

        if (arg === '--host' && argv[index + 1]) {
            process.env.HOST = argv[index + 1];
            index += 1;
            continue;
        }

        if (arg === '--port' && argv[index + 1]) {
            process.env.PORT = argv[index + 1];
            index += 1;
        }
    }
}

applyCliOverrides(process.argv.slice(2));

if (!process.env.HOST) {
    process.env.HOST = '0.0.0.0';
}

if (!process.env.PORT) {
    process.env.PORT = '4174';
}

const backendOrigin = (process.env.BACKEND_ORIGIN || process.env.BACKEND_URL || 'http://localhost:8080')
    .replace(/\/+$/, '');

if (!process.env.BACKEND_ORIGIN) {
    process.env.BACKEND_ORIGIN = backendOrigin;
}

const proxy = httpProxy.createProxyServer({
    changeOrigin: true,
    ws: true,
    xfwd: true,
    ignorePath: false,
    preserveHeaderKeyCase: true,
    secure: false,
});

proxy.on('proxyReq', (proxyReq) => {
    proxyReq.setHeader('Accept-Encoding', 'identity');
});

proxy.on('error', (error, req, res) => {
    console.error('Web edge proxy error', { url: req?.url || 'unknown', error: error.message });

    if (res && !res.headersSent) {
        res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({ error: 'Upstream service unavailable' }));
        return;
    }

    if (res && typeof res.end === 'function') {
        res.end();
    }
});

function isBackendProxyRoute(pathname) {
    return pathname === '/api' || pathname.startsWith('/api/') || pathname.startsWith('/login-child/');
}

function isWebSocketRoute(pathname) {
    return pathname === '/ws';
}

function setSecurityHeaders(res) {
    res.setHeader('Cross-Origin-Resource-Policy', 'same-site');
    res.setHeader('Referrer-Policy', 'no-referrer');
    res.setHeader('X-Frame-Options', 'DENY');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('X-XSS-Protection', '1; mode=block');
    res.setHeader('Strict-Transport-Security', 'max-age=31536000; includeSubDomains');
}

function compression(req, res, next) {
    const acceptEncoding = req.headers['accept-encoding'] || '';
    if (!acceptEncoding.includes('gzip')) {
        next();
        return;
    }

    const originalEnd = res.end.bind(res);
    const originalWriteHead = res.writeHead.bind(res);
    const chunks = [];

    res.write = function write(chunk) {
        if (chunk) {
            chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
        }
        return true;
    };

    res.writeHead = function writeHead(status, headers) {
        res.statusCode = status;
        if (headers) {
            Object.entries(headers).forEach(([key, value]) => {
                res.setHeader(key, value);
            });
        }
        return res;
    };

    res.end = function end(chunk) {
        if (chunk) {
            chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
        }

        const buffer = Buffer.concat(chunks);
        if (buffer.length < 512 || res.hasHeader('Content-Encoding')) {
            if (res.statusCode) {
                originalWriteHead(res.statusCode);
            }
            originalEnd(buffer);
            return;
        }

        gzip(buffer, (error, compressed) => {
            if (error) {
                if (res.statusCode) {
                    originalWriteHead(res.statusCode);
                }
                originalEnd(buffer);
                return;
            }

            res.setHeader('Content-Encoding', 'gzip');
            res.setHeader('Content-Length', compressed.length);

            if (res.statusCode) {
                originalWriteHead(res.statusCode);
            }

            originalEnd(compressed);
        });
    };

    next();
}

function writeJson(res, payload, status = 200) {
    res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify(payload));
}

function proxyRequest(req, res) {
    proxy.web(req, res, { target: backendOrigin });
}

const server = createServer((req, res) => {
    compression(req, res, () => {
        setSecurityHeaders(res);

        try {
            const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
            const pathname = url.pathname;

            if (pathname === '/healthz') {
                writeJson(res, {
                    status: 'ok',
                    service: 'web',
                    backendUrl: backendOrigin,
                });
                return;
            }

            if (isBackendProxyRoute(pathname)) {
                proxyRequest(req, res);
                return;
            }

            handler(req, res);
        } catch (error) {
            console.error('Web edge request failed', { url: req.url, error: error instanceof Error ? error.message : String(error) });
            writeJson(res, { error: 'Internal server error' }, 500);
        }
    });
});

server.on('upgrade', (req, socket, head) => {
    try {
        const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
        if (!isWebSocketRoute(url.pathname)) {
            socket.destroy();
            return;
        }

        proxy.ws(req, socket, head, { target: backendOrigin });
    } catch {
        socket.destroy();
    }
});

server.listen(Number(process.env.PORT), process.env.HOST, () => {
    console.log(`web-svelte edge listening on ${process.env.HOST}:${process.env.PORT}`);
});