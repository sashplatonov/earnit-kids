const fs = require('fs');
const { BASE_DATA_FILE, DATA_DIR } = require('../config');
const { createLogger } = require('../utils/logger');
const logger = createLogger('baseDataService');

function ensureDataDir() {
    if (!fs.existsSync(DATA_DIR)) {
        fs.mkdirSync(DATA_DIR, { recursive: true });
    }
}

function loadBaseData() {
    ensureDataDir();
    try {
        if (fs.existsSync(BASE_DATA_FILE)) {
            const content = fs.readFileSync(BASE_DATA_FILE, 'utf8');
            return JSON.parse(content);
        }
    } catch (err) {
        logger.error({ err: err.message }, 'Base data load failed');
    }
    return { tasks: [], products: [] };
}

function saveBaseData(data) {
    ensureDataDir();
    try {
        fs.writeFileSync(BASE_DATA_FILE, JSON.stringify(data, null, 2), 'utf8');
        return true;
    } catch (err) {
        logger.error({ err: err.message }, 'Base data save failed');
        return false;
    }
}

module.exports = { loadBaseData, saveBaseData };
