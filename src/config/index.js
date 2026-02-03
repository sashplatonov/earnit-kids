const path = require('path');

const DATA_DIR = path.join(__dirname, '../../data');

module.exports = {
    PORT: process.env.PORT || 3000,
    DATA_DIR,
    FAMILIES_FILE: path.join(DATA_DIR, 'families.json'),
    FAMILIES_DATA_DIR: path.join(DATA_DIR, 'families'),
    BASE_DATA_FILE: path.join(__dirname, 'baseData.json'),
    MAX_ATTEMPTS: 5,
    BLOCK_WINDOW_MS: 15 * 60 * 1000,
    MIME_TYPES: {
        '.html': 'text/html; charset=utf-8',
        '.css': 'text/css; charset=utf-8',
        '.js': 'application/javascript; charset=utf-8',
        '.json': 'application/json; charset=utf-8',
        '.md': 'text/markdown; charset=utf-8',
        '.png': 'image/png',
        '.jpg': 'image/jpeg',
        '.ico': 'image/x-icon'
    }
};
