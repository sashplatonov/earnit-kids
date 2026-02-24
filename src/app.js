const http = require('http');
const path = require('path');
const crypto = require('crypto');

// Load environment variables
require('dotenv').config({ path: path.join(__dirname, '../.env') });

const config = require('./config');
const { setSecurityHeaders } = require('./middleware/security');
const apiRoutes = require('./routes/api');
const staticRouter = require('./routes/staticRouter');
const { handleMagicLink } = require('./controllers/authController');
const viewController = require('./controllers/viewController');
const { testConnection } = require('./db/connection');
const { logStartupStats } = require('./utils/stats-logger');


const compression = require('./middleware/compression');
const logger = require('./utils/logger');
const metrics = require('./utils/metrics');
const { sendAlert } = require('./utils/alerts');
const websocket = require('./utils/websocket');
const { initBackupService } = require('./services/backupService');

/**
 * Log request completion and metrics
 */
function setupLogging(req, res, start) {
    const originalEnd = res.end;
    res.end = function (...args) {
        const duration = Date.now() - start;
        const { method, url } = req;
        const { statusCode } = res;

        logger.info({ reqId: req.id, method, url, status: statusCode, duration: `${duration}ms` }, 'Request completed');
        metrics.recordRequest({ method, path: url, status: statusCode, duration });

        if (duration > 500) {
            logger.warn({ reqId: req.id, duration: `${duration}ms`, url }, 'Slow request detected');
        }

        return originalEnd.apply(this, args);
    };
}

function logAndAlertError(err, req) {
    const reqId = req ? req.id : 'unknown';
    logger.error({
        reqId,
        err: err.message,
        stack: err.stack,
        url: req ? req.url : 'unknown',
        method: req ? req.method : 'unknown'
    }, 'Internal Server Error');

    const status = err.status || 500;
    if (status >= 500) {
        sendAlert(err, `ID: ${reqId} | ${req ? req.method : '??'} ${req ? req.url : '??'}`);
    }
}

/**
 * Global error handler
 */
function handleError(err, req, res) {
    logAndAlertError(err, req);

    const status = err.status || 500;
    const isProd = process.env.NODE_ENV === 'production';

    const responseData = {
        error: err.message || 'Internal Server Error',
        code: err.code || 'INTERNAL_ERROR',
        ...(!isProd && status >= 500 ? { stack: err.stack } : {})
    };

    if (!res.headersSent) {
        res.writeHead(status, { 'Content-Type': 'application/json' });
    }
    res.end(JSON.stringify(responseData));
}

const server = http.createServer(async (req, res) => {
    compression(req, res, async () => {
        try {
            req.id = req.headers['x-correlation-id'] || crypto.randomUUID();
            res.setHeader('X-Correlation-ID', req.id);
            setSecurityHeaders(req, res);

            const start = Date.now();
            setupLogging(req, res, start);

            if (staticRouter.handleCors(req, res)) return;

            const { rateLimit } = require('./utils/rateLimiter');
            if (req.url.startsWith('/api/') && rateLimit(req)) {
                res.writeHead(429, { 'Content-Type': 'application/json' });
                return res.end(JSON.stringify({ error: 'Too Many Requests' }));
            }

            const [pathOnly] = req.url.split('?');
            if (pathOnly.startsWith('/login-child/')) return handleMagicLink(req, res);
            if (!pathOnly.startsWith('/api/')) return staticRouter.routeStatic({ pathOnly, req, res, viewController });

            await apiRoutes(req, res);
        } catch (err) {
            handleError(err, req, res);
        }
    });
});

const { validateEnv, initDatabase } = require('./utils/startup-init');

async function startServer() {
    validateEnv();
    await initDatabase();

    server.listen(config.PORT, async () => {
        console.log(`🪙 Coin Shop Server running at http://localhost:${config.PORT}`);
        websocket.init(server);
        initBackupService();
        await logStartupStats();
    });
}

startServer().catch(err => {
    console.error('Failed to start server:', err.message);
    sendAlert(err, 'Startup Failure');
    process.exit(1);
});
