const fs = require('fs');
const { BASE_DATA_FILE, DATA_DIR } = require('../config');

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
        console.error('Error loading base data:', err.message);
    }
    return { tasks: [], products: [] };
}

function saveBaseData(data) {
    ensureDataDir();
    try {
        fs.writeFileSync(BASE_DATA_FILE, JSON.stringify(data, null, 2), 'utf8');
        return true;
    } catch (err) {
        console.error('Error saving base data:', err.message);
        return false;
    }
}

module.exports = { loadBaseData, saveBaseData };
