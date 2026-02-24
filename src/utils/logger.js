const pino = require('pino');

const logger = pino({
    level: process.env.LOG_LEVEL || 'info',
    formatters: {
        level: (label) => ({ level: label })
    }
});

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
