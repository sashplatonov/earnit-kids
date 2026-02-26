/** @file Log retrieval and sanitization for the system dashboard */
const fs = require('fs').promises;
const path = require('path');

const DEFAULT_LOG_PATH = path.join(process.cwd(), 'logs', 'app.log');
const LEVELS = new Set(['info', 'warn', 'error']);
const MIN_LIMIT = 1;
const MAX_LIMIT = 500;

function clamp(value, min, max) {
    if (Number.isNaN(value)) return min;
    return Math.min(Math.max(value, min), max);
}

function normalizeLevel(level) {
    if (!level) return 'error';
    const normalized = String(level).toLowerCase();
    return LEVELS.has(normalized) ? normalized : 'error';
}

function maskSecrets(value) {
    if (typeof value !== 'string') return value;
    let sanitized = value.replace(/(Authorization|token|password|secret|cookie)(["']?\s*[:=]\s*)([^,\s]+)/gi, '$1$2***');
    sanitized = sanitized.replace(/\b[A-Za-z0-9_-]{12,}\b/g, '***');
    return sanitized;
}

function parseLogJson(line) {
    try {
        return JSON.parse(line);
    } catch {
        return null;
    }
}

function fallbackLogEntry(line) {
    return {
        level: 'info',
        msg: maskSecrets(line),
        module: '',
        reqId: null,
        ts: new Date().toISOString()
    };
}

function buildLogEntry(parsed) {
    const tsValue = parsed.ts ?? parsed.time;
    const timestamp = tsValue ? new Date(tsValue).toISOString() : new Date().toISOString();
    const level = normalizeLevel(parsed.level);
    return {
        msg: maskSecrets(parsed.msg || parsed.message || ''),
        module: maskSecrets(parsed.module || parsed.logger || ''),
        level,
        reqId: maskSecrets(parsed.reqId || parsed.req_id || parsed.requestId || ''),
        ts: timestamp
    };
}

function normalizeLogLine(line) {
    const parsed = parseLogJson(line);
    return parsed ? buildLogEntry(parsed) : fallbackLogEntry(line);
}

async function readLogs(options = {}) {
    const level = normalizeLevel(options.level);
    const limit = clamp(parseInt(options.limit, 10) || MIN_LIMIT, MIN_LIMIT, MAX_LIMIT);
    const logPath = process.env.SUPER_ADMIN_LOG_PATH || DEFAULT_LOG_PATH;

    let content;
    try {
        content = await fs.readFile(logPath, 'utf8');
    } catch {
        return [];
    }

    const lines = content.split('\n').filter(Boolean);
    const result = [];

    for (let i = lines.length - 1; i >= 0 && result.length < limit; i--) {
        const entry = normalizeLogLine(lines[i]);
        if (level && entry.level !== level) continue;
        result.push(entry);
    }

    return result;
}

module.exports = {
    readLogs
};
