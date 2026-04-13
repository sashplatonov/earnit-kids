/** @file Web edge configuration */
'use strict';

const path = require('path');

const env = process.env.NODE_ENV || 'development';
const isDev = env === 'development';
const isTest = env === 'test';
const isProd = env === 'production';

const DEFAULT_PORT = process.env.PORT || 3000;
const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, '../data');
const PUBLIC_BASE_URL = (process.env.PUBLIC_BASE_URL || `http://localhost:${DEFAULT_PORT}`).replace(/\/+$/, '');

const MIME_TYPES = {
    '': 'application/json; charset=utf-8',
    '.html': 'text/html; charset=utf-8',
    '.css': 'text/css; charset=utf-8',
    '.js': 'application/javascript; charset=utf-8',
    '.json': 'application/json; charset=utf-8',
    '.md': 'text/markdown; charset=utf-8',
    '.xml': 'application/xml; charset=utf-8',
    '.png': 'image/png',
    '.svg': 'image/svg+xml; charset=utf-8',
    '.jpg': 'image/jpeg',
    '.ico': 'image/x-icon'
};

module.exports = {
    env,
    isDev,
    isTest,
    isProd,
    PORT: DEFAULT_PORT,
    PUBLIC_BASE_URL,
    DATA_DIR,
    MIME_TYPES
};
