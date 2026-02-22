const WebSocket = require('ws');
const { verifyToken } = require('./authUtils');
const { getCookies } = require('../controllers/viewController');
const logger = require('./logger');

let wss;

function init(server) {
    wss = new WebSocket.Server({ server, path: '/ws' });

    wss.on('connection', (ws, req) => {
        const cookies = getCookies(req);
        const token = cookies.app_auth;
        const decoded = token ? verifyToken(token) : null;

        if (!decoded) {
            logger.debug('WS connection attempt without valid token');
            ws.close(4001, 'Unauthorized');
            return;
        }

        ws.familyId = decoded.familyId;
        ws.role = decoded.role;
        ws.isAlive = true;

        ws.on('pong', () => { ws.isAlive = true; });

        logger.info({ familyId: ws.familyId, role: ws.role }, 'WebSocket connected');

        ws.on('message', (message) => {
            // Echo or handle messages if needed
            logger.debug({ familyId: ws.familyId, message: message.toString() }, 'WS Message received');
        });

        ws.on('error', (err) => {
            logger.error({ familyId: ws.familyId, err: err.message }, 'WS connection error');
        });
    });

    // Keep-alive interval
    const interval = setInterval(() => {
        wss.clients.forEach((ws) => {
            if (ws.isAlive === false) return ws.terminate();
            ws.isAlive = false;
            ws.ping();
        });
    }, 30000);

    wss.on('close', () => {
        clearInterval(interval);
    });

    logger.info('WebSocket system initialized on /ws');
}

/**
 * Notify all family members
 */
function notifyFamily(familyId, type, data) {
    if (!wss) return;

    const message = JSON.stringify({ type, data, timestamp: new Date().toISOString() });
    wss.clients.forEach((client) => {
        if (client.readyState === WebSocket.OPEN && client.familyId === familyId) {
            client.send(message);
        }
    });
}

/**
 * Notify family admins only
 */
function notifyAdmins(familyId, type, data) {
    if (!wss) return;

    const message = JSON.stringify({ type, data, timestamp: new Date().toISOString() });
    wss.clients.forEach((client) => {
        if (client.readyState === WebSocket.OPEN && client.familyId === familyId && client.role === 'admin') {
            client.send(message);
        }
    });
}

function broadcast(type, data) {
    if (!wss) return;

    const message = JSON.stringify({ type, data, timestamp: new Date().toISOString() });
    wss.clients.forEach((client) => {
        if (client.readyState === WebSocket.OPEN) {
            client.send(message);
        }
    });
}

module.exports = {
    init,
    notifyFamily,
    notifyAdmins,
    broadcast
};
