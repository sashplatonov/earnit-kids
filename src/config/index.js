const path = require('path');

const env = process.env.NODE_ENV || 'development';
const isDev = env === 'development';
const isTest = env === 'test';
const isProd = env === 'production';

const DEFAULT_PORT = process.env.PORT || 3000;
const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, '../../data');
const PUBLIC_BASE_URL = process.env.PUBLIC_BASE_URL || `http://localhost:${DEFAULT_PORT}`;

const config = {
    env,
    isDev,
    isTest,
    isProd,
    PORT: DEFAULT_PORT,
    PUBLIC_BASE_URL,
    DATA_DIR,
    FAMILIES_FILE: path.join(DATA_DIR, 'families.json'),
    FAMILIES_DATA_DIR: path.join(DATA_DIR, 'families'),
    BASE_DATA_FILE: path.join(__dirname, 'baseData.json'),
    MAX_ATTEMPTS: process.env.MAX_ATTEMPTS ? parseInt(process.env.MAX_ATTEMPTS, 10) : 5,
    BLOCK_WINDOW_MS: process.env.BLOCK_WINDOW_MS ? parseInt(process.env.BLOCK_WINDOW_MS, 10) : 15 * 60 * 1000,
    RATE_LIMIT_MS: 60000,
    RATE_LIMIT_MAX: process.env.RATE_LIMIT_MAX ? parseInt(process.env.RATE_LIMIT_MAX, 10) : 100,
    MIME_TYPES: {
        '': 'application/json; charset=utf-8',
        '.html': 'text/html; charset=utf-8',
        '.css': 'text/css; charset=utf-8',
        '.js': 'application/javascript; charset=utf-8',
        '.json': 'application/json; charset=utf-8',
        '.md': 'text/markdown; charset=utf-8',
        '.xml': 'application/xml; charset=utf-8',
        '.png': 'image/png',
        '.jpg': 'image/jpeg',
        '.ico': 'image/x-icon'
    },
    TELEGRAM: {
        TOKEN: process.env.TELEGRAM_BOT_TOKEN,
        CHAT_ID: process.env.TELEGRAM_CHAT_ID,
        ENABLED: process.env.ENABLE_TELEGRAM_ALERTS === 'true',
        BACKUP_INTERVAL_HOURS: parseInt(process.env.BACKUP_INTERVAL_HOURS, 10) || 24
    },
    ENABLE_EMAIL_ALERTS: process.env.ENABLE_EMAIL_ALERTS === 'true'
};

module.exports = config;
