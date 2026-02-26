/** @file Logger utility helpers */
const fs = require('fs');
const path = require('path');
const pino = require('pino');

const logPath = process.env.SUPER_ADMIN_LOG_PATH
    || path.join(process.cwd(), 'logs', 'app.log');

fs.mkdirSync(path.dirname(logPath), { recursive: true });

const streams = [
    { stream: pino.destination({ dest: logPath }) }
];

if (process.env.NODE_ENV !== 'production') {
    streams.push({ stream: process.stdout });
}

const logger = pino({
    level: process.env.LOG_LEVEL || 'info',
    formatters: {
        level: (label) => ({ level: label })
    }
}, pino.multistream(streams));

/**
 * Create a child logger with module context
 * @param {string} moduleName
 * @returns {pino.Logger}
 */
function createLogger(moduleName) {
    if (!moduleName) return logger;
    return logger.child({ module: moduleName });
}

module.exports = logger;
module.exports.createLogger = createLogger;
